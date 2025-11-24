package com.zachholt.referencelookup.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.parser.JavaConstantParser;
import com.zachholt.referencelookup.parser.PsiConstantParser;
import com.zachholt.referencelookup.settings.ReferenceSettingsState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service(Service.Level.PROJECT)
public final class ReferenceDataService {
    private static final Logger LOG = Logger.getInstance(ReferenceDataService.class);
    
    // Use more memory-efficient collections if possible, but ArrayList is fine for lists.
    private final List<ReferenceItem> references = new ArrayList<>();
    // Cache for the grouped view to avoid rebuilding it
    private final Map<String, List<ReferenceItem>> cachedGroupedReferences = new ConcurrentHashMap<>();
    // Code index for exact matches
    private final Map<String, List<ReferenceItem>> codeIndex = new HashMap<>();
    // Cached lowercase values to avoid repeated allocations during search
    private final Map<ReferenceItem, String> codeLowerCache = new IdentityHashMap<>();
    private final Map<ReferenceItem, String> descriptionLowerCache = new IdentityHashMap<>();
    
    private final Project project;
    
    private volatile boolean isLoaded = false;
    private volatile boolean isLoading = false;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<Runnable> loadListeners = new ArrayList<>();

    public ReferenceDataService(Project project) {
        this.project = project;
    }

    public static ReferenceDataService getInstance(Project project) {
        return project.getService(ReferenceDataService.class);
    }

    public void reload() {
        isLoaded = false;
        isLoading = false;
        loadReferencesAsync();
    }

    /**
     * Loads references asynchronously in the background.
     * This method is thread-safe and ensures references are loaded only once.
     * Use onLoaded() to be notified when loading completes.
     */
    public void loadReferencesAsync() {
        // Fast path: already loaded
        if (isLoaded) return;

        // Prevent multiple threads from starting the load
        synchronized (this) {
            if (isLoading || isLoaded) return;
            isLoading = true;
        }

        // Run file I/O on background thread to avoid blocking UI
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                LOG.info("Starting background load of references...");
                
                List<ReferenceItem> loadedItems = new ArrayList<>();
                ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
                
                boolean loaded = false;

                // 1. Check preferred JSON if enabled
                if (settings.useJsonFile && settings.jsonFilePath != null && !settings.jsonFilePath.isEmpty()) {
                    Path jsonPath = Paths.get(settings.jsonFilePath);
                    if (Files.exists(jsonPath)) {
                        LOG.info("Loading references from JSON: " + jsonPath);
                        List<ReferenceItem> items = loadFromJsonFile(jsonPath);
                        if (!items.isEmpty()) {
                            loadedItems.addAll(items);
                            loaded = true;
                        }
                    }
                }

                // 2. Check Java file (if not loaded or not preferred JSON)
                if (!loaded && settings.referenceFilePath != null && !settings.referenceFilePath.isEmpty()) {
                     Path javaPath = Paths.get(settings.referenceFilePath);
                     if (Files.exists(javaPath)) {
                        LOG.info("Loading references from Java: " + javaPath);
                        List<ReferenceItem> items = loadFromJavaFile(javaPath);
                        if (!items.isEmpty()) {
                            loadedItems.addAll(items);
                            loaded = true;
                        }
                     }
                }
                
                // 3. Check JSON as fallback (if JSON was not preferred/checked or Java failed)
                if (!loaded && !settings.useJsonFile && settings.jsonFilePath != null && !settings.jsonFilePath.isEmpty()) {
                    Path jsonPath = Paths.get(settings.jsonFilePath);
                     if (Files.exists(jsonPath)) {
                        LOG.info("Loading references from JSON (fallback): " + jsonPath);
                        List<ReferenceItem> items = loadFromJsonFile(jsonPath);
                        if (!items.isEmpty()) {
                            loadedItems.addAll(items);
                            loaded = true;
                        }
                     }
                }

                // 4. Resources Fallback
                if (!loaded) {
                    LOG.info("Loading default references from resources");
                    loadedItems.addAll(loadFromResources());
                }

                lock.writeLock().lock();
                try {
                    long indexStart = System.currentTimeMillis();
                    references.clear();
                    references.addAll(loadedItems);
                    buildIndex();
                    long indexDuration = System.currentTimeMillis() - indexStart;
                    isLoaded = true;
                    long totalDuration = System.currentTimeMillis() - startTime;
                    LOG.info("Loaded " + references.size() + " reference items in " + totalDuration + "ms (Indexing: " + indexDuration + "ms)");
                } finally {
                    lock.writeLock().unlock();
                }

            } catch (Exception e) {
                LOG.error("Failed to load references (took " + (System.currentTimeMillis() - startTime) + "ms)", e);
            } finally {
                isLoading = false;
                // Notify listeners regardless of success/failure to prevent them from waiting forever
                notifyLoadListeners();
            }
        });
    }

    /**
     * Register a callback to be notified when references finish loading.
     * If already loaded, the callback runs immediately.
     */
    public void onLoaded(Runnable callback) {
        if (isLoaded) {
            callback.run();
        } else {
            synchronized (loadListeners) {
                if (isLoaded) {
                    callback.run();
                } else {
                    loadListeners.add(callback);
                }
            }
        }
    }

    private void notifyLoadListeners() {
        List<Runnable> listeners;
        synchronized (loadListeners) {
            listeners = new ArrayList<>(loadListeners);
            loadListeners.clear();
        }
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                LOG.error("Error in load listener", e);
            }
        }
    }

    private List<ReferenceItem> loadFromJavaFile(Path path) {
        try {
            // Try PSI parsing first (much more robust)
            final List<ReferenceItem> items = new ArrayList<>();
            
            ApplicationManager.getApplication().runReadAction(() -> {
                VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(path);
                if (virtualFile != null) {
                    // Check if the project is disposed before accessing PSI
                    if (project.isDisposed()) return;
                    
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile != null) {
                        PsiConstantParser parser = new PsiConstantParser();
                        items.addAll(parser.parse(psiFile));
                    }
                }
            });
            
            if (!items.isEmpty()) {
                LOG.info("Successfully parsed via PSI: " + path);
                return items;
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse Java file via PSI: " + path, e);
        }
        
        // Fallback to regex parser
        try {
            LOG.info("Falling back to Regex parser for: " + path);
            JavaConstantParser parser = new JavaConstantParser();
            List<ReferenceItem> loaded = parser.parseJavaFile(path);
            if (loaded != null) {
                return loaded;
            }
        } catch (Exception e) {
            LOG.warn("Failed to parse Java file: " + path, e);
        }
        return Collections.emptyList();
    }

    private List<ReferenceItem> loadFromJsonFile(Path path) {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path))) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<ReferenceItem>>(){}.getType();
            List<ReferenceItem> loaded = gson.fromJson(reader, listType);
            return loaded != null ? loaded : Collections.emptyList();
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON file: " + path, e);
            return Collections.emptyList();
        }
    }

    private List<ReferenceItem> loadFromResources() {
        InputStream resourceStream = getClass().getResourceAsStream("/references.json");
        if (resourceStream == null) {
            LOG.error("Could not find references.json in resources");
            return Collections.emptyList();
        }
        
        try (InputStreamReader reader = new InputStreamReader(resourceStream)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<ReferenceItem>>(){}.getType();
            List<ReferenceItem> loaded = gson.fromJson(reader, listType);
            return loaded != null ? loaded : Collections.emptyList();
        } catch (Exception e) {
            LOG.error("Failed to parse resources JSON", e);
            return Collections.emptyList();
        }
    }

    private void buildIndex() {
        codeIndex.clear();
        cachedGroupedReferences.clear();
        codeLowerCache.clear();
        descriptionLowerCache.clear();
        
        for (ReferenceItem item : references) {
            String code = item.getCode();
            if (code == null) {
                continue;
            }

            String normalizedCode = code.toLowerCase();
            String normalizedDescription = Optional.ofNullable(item.getDescription())
                    .map(String::toLowerCase)
                    .orElse("");
            codeLowerCache.put(item, normalizedCode);
            descriptionLowerCache.put(item, normalizedDescription);
            // Index by full code
            codeIndex.computeIfAbsent(normalizedCode, k -> new ArrayList<>()).add(item);
            
            // Index by code parts (for partial matches)
            String[] parts = normalizedCode.split("[\\s_-]");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    codeIndex.computeIfAbsent(part, k -> new ArrayList<>()).add(item);
                }
            }

            // Build grouped cache
            String category = item.getCategory() != null ? item.getCategory() : "Uncategorized";
            cachedGroupedReferences.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
        }
    }

    public List<ReferenceItem> search(String query) {
        return search(query, -1); // No limit
    }

    public List<ReferenceItem> search(String query, int limit) {
        // Trigger load if not started
        loadReferencesAsync();

        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // If not loaded, we can't search synchronously. Return empty.
        if (!isLoaded) {
            return Collections.emptyList();
        }

        lock.readLock().lock();
        long searchStart = System.currentTimeMillis();
        try {
            String normalizedQuery = query.toLowerCase().trim();
            Set<ReferenceItem> results = new LinkedHashSet<>();

            // 1. Exact match (fastest)
            List<ReferenceItem> exactMatches = codeIndex.get(normalizedQuery);
            if (exactMatches != null) {
                results.addAll(exactMatches);
            }
            
            if (limit > 0 && results.size() >= limit) {
                return new ArrayList<>(results).subList(0, limit);
            }

            // 2. Contains match (fast)
            for (ReferenceItem item : references) {
                String codeLower = codeLowerCache.getOrDefault(item, "");
                String descriptionLower = descriptionLowerCache.getOrDefault(item, "");

                if (codeLower.contains(normalizedQuery) ||
                    descriptionLower.contains(normalizedQuery)) {
                    results.add(item);
                    if (limit > 0 && results.size() >= limit) break;
                }
            }
            
            if (limit > 0 && results.size() >= limit) {
                return new ArrayList<>(results);
            }

            // 3. Fuzzy subsequence match (slower) - only if we need more results
            // Skipping this if we have enough matches to save CPU
            if (results.isEmpty()) {
                 for (ReferenceItem item : references) {
                    if (fuzzyMatch(normalizedQuery, item.getCode().toLowerCase())) {
                        results.add(item);
                        if (limit > 0 && results.size() >= limit) break;
                    }
                }
            }

            return new ArrayList<>(results);
        } finally {
            long duration = System.currentTimeMillis() - searchStart;
            if (duration > 10) {
                LOG.debug("Search for '" + query + "' took " + duration + "ms");
            }
            lock.readLock().unlock();
        }
    }

    /**
     * Checks if all characters of query appear in order in target.
     */
    private boolean fuzzyMatch(String query, String target) {
        int queryIdx = 0;
        int targetIdx = 0;
        int queryLen = query.length();
        int targetLen = target.length();
        
        while (queryIdx < queryLen && targetIdx < targetLen) {
            if (query.charAt(queryIdx) == target.charAt(targetIdx)) {
                queryIdx++;
            }
            targetIdx++;
        }
        return queryIdx == queryLen;
    }

    public List<ReferenceItem> getAllReferences() {
        loadReferencesAsync();
        if (!isLoaded) return Collections.emptyList();

        lock.readLock().lock();
        try {
            return new ArrayList<>(references);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, List<ReferenceItem>> getReferencesGroupedByCategory() {
        loadReferencesAsync();
        if (!isLoaded) return Collections.emptyMap();

        lock.readLock().lock();
        try {
            // Return the cached map
            return new HashMap<>(cachedGroupedReferences);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
