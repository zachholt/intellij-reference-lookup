package com.zachholt.referencelookup.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.parser.JavaConstantParser;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service(Service.Level.PROJECT)
public final class ReferenceDataService {
    private static final Logger LOG = Logger.getInstance(ReferenceDataService.class);
    private final List<ReferenceItem> references = new ArrayList<>();
    private final Map<String, List<ReferenceItem>> codeIndex = new HashMap<>();
    private boolean isLoaded = false;

    public static ReferenceDataService getInstance(Project project) {
        return project.getService(ReferenceDataService.class);
    }

    public void loadReferences() {
        if (isLoaded) return;

        try {
            // Priority order for loading references:
            // 1. Java file in user's home directory
            Path userJavaPath = Paths.get(System.getProperty("user.home"), ".reference-lookup", "Reference.java");
            if (Files.exists(userJavaPath)) {
                loadFromJavaFile(userJavaPath);
            } else {
                // 2. JSON file in user's home directory
                Path userJsonPath = Paths.get(System.getProperty("user.home"), ".reference-lookup", "references.json");
                if (Files.exists(userJsonPath)) {
                    loadFromJsonFile(userJsonPath);
                } else {
                    // 3. Load from resources as fallback
                    loadFromResources();
                }
            }
            buildIndex();
            isLoaded = true;
            LOG.info("Loaded " + references.size() + " reference items");
        } catch (Exception e) {
            LOG.error("Failed to load references", e);
        }
    }

    private void loadFromJavaFile(Path path) throws Exception {
        JavaConstantParser parser = new JavaConstantParser();
        List<ReferenceItem> loaded = parser.parseJavaFile(path);
        if (loaded != null) {
            references.addAll(loaded);
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
        try (InputStreamReader reader = new InputStreamReader(
                getClass().getResourceAsStream("/references.json"))) {
            if (reader != null) {
                Gson gson = new Gson();
                Type listType = new TypeToken<List<ReferenceItem>>(){}.getType();
                List<ReferenceItem> loaded = gson.fromJson(reader, listType);
                if (loaded != null) {
                    references.addAll(loaded);
                }
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
        if (!isLoaded) {
            loadReferences();
        }

        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

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
        if (!isLoaded) {
            loadReferences();
        }
        return new ArrayList<>(references);
    }
}