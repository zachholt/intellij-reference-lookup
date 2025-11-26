package com.zachholt.referencelookup.service;

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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;

@Service(Service.Level.PROJECT)
public final class ReferenceDataService {
    private static final Logger LOG = Logger.getInstance(ReferenceDataService.class);

    // Pre-compiled pattern for code splitting
    private static final Pattern CODE_SPLIT_PATTERN = Pattern.compile("[\\s_-]");

    private final List<ReferenceItem> references = new ArrayList<>();
    private final Map<String, List<ReferenceItem>> codeIndex = new HashMap<>();

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
        if (isLoaded) return;

        synchronized (this) {
            if (isLoading || isLoaded) return;
            isLoading = true;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                LOG.info("Starting background load of references...");

                List<ReferenceItem> loadedItems = new ArrayList<>();
                ReferenceSettingsState settings = ReferenceSettingsState.getInstance();

                if (settings.referenceFilePath != null && !settings.referenceFilePath.isEmpty()) {
                    Path javaPath = Paths.get(settings.referenceFilePath);
                    if (Files.exists(javaPath)) {
                        LOG.info("Loading references from Java: " + javaPath);
                        loadedItems.addAll(loadFromJavaFile(javaPath));
                    } else {
                        LOG.warn("Reference file not found: " + javaPath);
                    }
                } else {
                    LOG.info("No reference file configured in settings");
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

    private void buildIndex() {
        codeIndex.clear();

        for (ReferenceItem item : references) {
            String codeLower = item.getCodeLower();
            if (codeLower == null) {
                continue;
            }

            codeIndex.computeIfAbsent(codeLower, k -> new ArrayList<>()).add(item);

            String valueLower = item.getValueLower();
            if (valueLower != null && !valueLower.isEmpty()) {
                codeIndex.computeIfAbsent(valueLower, k -> new ArrayList<>()).add(item);
            }

            String[] parts = CODE_SPLIT_PATTERN.split(codeLower);
            for (String part : parts) {
                if (!part.isEmpty()) {
                    codeIndex.computeIfAbsent(part, k -> new ArrayList<>()).add(item);
                }
            }
        }
    }

    public List<ReferenceItem> search(String query) {
        return search(query, -1);
    }

    public List<ReferenceItem> search(String query, int limit) {
        loadReferencesAsync();

        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

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

            // 2. Contains match
            for (ReferenceItem item : references) {
                String codeLower = item.getCodeLower();
                String descriptionLower = item.getDescriptionLower();
                String valueLower = item.getValueLower();

                if ((codeLower != null && codeLower.contains(normalizedQuery)) ||
                    (descriptionLower != null && descriptionLower.contains(normalizedQuery)) ||
                    (valueLower != null && valueLower.contains(normalizedQuery))) {
                    results.add(item);
                    if (limit > 0 && results.size() >= limit) break;
                }
            }

            if (limit > 0 && results.size() >= limit) {
                return new ArrayList<>(results);
            }

            // 3. Fuzzy match (only if no results yet)
            if (results.isEmpty()) {
                for (ReferenceItem item : references) {
                    String codeLower = item.getCodeLower();
                    if (codeLower != null && fuzzyMatch(normalizedQuery, codeLower)) {
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
            return Collections.unmodifiableList(references);
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean isLoaded() {
        return isLoaded;
    }
}
