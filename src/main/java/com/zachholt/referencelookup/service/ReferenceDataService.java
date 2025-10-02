package com.zachholt.referencelookup.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.parser.JavaConstantParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service(Service.Level.PROJECT)
public final class ReferenceDataService {
    private static final Logger LOG = Logger.getInstance(ReferenceDataService.class);
    private final List<ReferenceItem> references = new ArrayList<>();
    private final Map<String, List<ReferenceItem>> codeIndex = new HashMap<>();
    private volatile boolean isLoaded = false;
    private volatile boolean isLoading = false;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<Runnable> loadListeners = new ArrayList<>();

    public static ReferenceDataService getInstance(Project project) {
        return project.getService(ReferenceDataService.class);
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
            try {
                LOG.info("Starting background load of references...");

                // Priority order for loading references:
                // 1. Java file in user's home directory
                Path userJavaPath = Paths.get(System.getProperty("user.home"), ".reference-lookup", "Reference.java");
                if (Files.exists(userJavaPath)) {
                    LOG.info("Loading references from: " + userJavaPath);
                    loadFromJavaFile(userJavaPath);
                } else {
                    LOG.info("No Reference.java found at: " + userJavaPath);
                    // 2. JSON file in user's home directory
                    Path userJsonPath = Paths.get(System.getProperty("user.home"), ".reference-lookup", "references.json");
                    if (Files.exists(userJsonPath)) {
                        loadFromJsonFile(userJsonPath);
                    } else {
                        // 3. Load from resources as fallback
                        LOG.info("Loading default references from resources");
                        loadFromResources();
                    }
                }

                lock.writeLock().lock();
                try {
                    buildIndex();
                    isLoaded = true;
                    LOG.info("Loaded " + references.size() + " reference items");
                } finally {
                    lock.writeLock().unlock();
                }

                // Notify listeners
                notifyLoadListeners();
            } catch (Exception e) {
                LOG.error("Failed to load references", e);
            } finally {
                isLoading = false;
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
                loadListeners.add(callback);
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

    private void loadFromJavaFile(Path path) throws Exception {
        JavaConstantParser parser = new JavaConstantParser();
        List<ReferenceItem> loaded = parser.parseJavaFile(path);
        if (loaded != null && !loaded.isEmpty()) {
            references.addAll(loaded);
            LOG.info("Successfully parsed " + loaded.size() + " items from Java file");
        } else {
            LOG.warn("No constants found in Java file or parsing failed");
        }
    }

    private void loadFromJsonFile(Path path) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path))) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<ReferenceItem>>(){}.getType();
            List<ReferenceItem> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                references.addAll(loaded);
            }
        }
    }

    private void loadFromResources() throws Exception {
        InputStream resourceStream = getClass().getResourceAsStream("/references.json");
        if (resourceStream == null) {
            LOG.error("Could not find references.json in resources");
            return;
        }
        
        try (InputStreamReader reader = new InputStreamReader(resourceStream)) {
            Gson gson = new Gson();
            Type listType = new TypeToken<List<ReferenceItem>>(){}.getType();
            List<ReferenceItem> loaded = gson.fromJson(reader, listType);
            if (loaded != null && !loaded.isEmpty()) {
                references.addAll(loaded);
                LOG.info("Loaded " + loaded.size() + " default references from resources");
            }
        }
    }

    private void buildIndex() {
        codeIndex.clear();
        for (ReferenceItem item : references) {
            String code = item.getCode().toLowerCase();
            // Index by full code
            codeIndex.computeIfAbsent(code, k -> new ArrayList<>()).add(item);
            
            // Index by code parts (for partial matches)
            String[] parts = code.split("[\\s-_]");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    codeIndex.computeIfAbsent(part, k -> new ArrayList<>()).add(item);
                }
            }
        }
    }

    public List<ReferenceItem> search(String query) {
        // Start loading if not already started
        loadReferencesAsync();

        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Wait for load to complete if still loading
        if (!isLoaded) {
            return Collections.emptyList();
        }

        lock.readLock().lock();
        try {
            String normalizedQuery = query.toLowerCase().trim();
            Set<ReferenceItem> results = new LinkedHashSet<>();

            // Exact match
            List<ReferenceItem> exactMatches = codeIndex.get(normalizedQuery);
            if (exactMatches != null) {
                results.addAll(exactMatches);
            }

            // Fuzzy search
            for (ReferenceItem item : references) {
                if (fuzzyMatch(normalizedQuery, item)) {
                    results.add(item);
                }
            }

            return new ArrayList<>(results);
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean fuzzyMatch(String query, ReferenceItem item) {
        String code = item.getCode().toLowerCase();
        String description = item.getDescription().toLowerCase();

        // Check if query is contained in code or description
        if (code.contains(query) || description.contains(query)) {
            return true;
        }

        // Check if all characters of query appear in order in code
        int codeIndex = 0;
        for (char c : query.toCharArray()) {
            boolean found = false;
            while (codeIndex < code.length()) {
                if (code.charAt(codeIndex) == c) {
                    found = true;
                    codeIndex++;
                    break;
                }
                codeIndex++;
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public List<ReferenceItem> getAllReferences() {
        // Start loading if not already started
        loadReferencesAsync();

        // Return empty list if still loading
        if (!isLoaded) {
            return Collections.emptyList();
        }

        lock.readLock().lock();
        try {
            return new ArrayList<>(references);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<String, List<ReferenceItem>> getReferencesGroupedByCategory() {
        // Start loading if not already started
        loadReferencesAsync();

        // Return empty map if still loading
        if (!isLoaded) {
            return Collections.emptyMap();
        }

        lock.readLock().lock();
        try {
            Map<String, List<ReferenceItem>> grouped = new HashMap<>();

            for (ReferenceItem item : references) {
                String category = item.getCategory() != null ? item.getCategory() : "Uncategorized";
                grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
            }

            return grouped;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}