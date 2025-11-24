package com.zachholt.referencelookup.parser;

import com.zachholt.referencelookup.model.ReferenceItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JavaConstantParser {
    // Simplified pattern to match any type (including primitives and generics)
    private static final Pattern CONSTANT_PATTERN = Pattern.compile(
        "public\\s+static\\s+final\\s+[\\w<>\\[\\]]+\\s+(\\w+)\\s*=\\s*(.+?);"
    );
    
    // Pattern to match JavaDoc comments
    private static final Pattern JAVADOC_PATTERN = Pattern.compile(
        "/\\*\\*\\s*\\n?\\s*\\*?\\s*(.+?)\\s*\\*/",
        Pattern.DOTALL
    );
    
    // Pattern to match single-line comments
    private static final Pattern SINGLE_LINE_COMMENT_PATTERN = Pattern.compile(
        "//\\s*(.+)"
    );

    public List<ReferenceItem> parseJavaFile(Path javaFile) throws IOException {
        if (!Files.exists(javaFile)) {
            return Collections.emptyList();
        }

        List<ReferenceItem> references = new ArrayList<>();
        List<String> lines = Files.readAllLines(javaFile);
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            
            // Skip empty lines and package/import statements
            if (line.isEmpty() || line.startsWith("package") || line.startsWith("import")) {
                continue;
            }
            
            // Check for constants
            Matcher constantMatcher = CONSTANT_PATTERN.matcher(line);
            if (constantMatcher.find()) {
                String constantName = constantMatcher.group(1);
                String constantValue = constantMatcher.group(2).trim();
                
                // Clean up the value
                if (constantValue.startsWith("\"") && constantValue.endsWith("\"")) {
                    // String value - remove quotes
                    constantValue = constantValue.substring(1, constantValue.length() - 1);
                } else if (constantValue.contains(".valueOf(")) {
                    // Extract value from Integer.valueOf(123) -> "123"
                    int start = constantValue.indexOf('(');
                    int end = constantValue.lastIndexOf(')');
                    if (start != -1 && end != -1 && start < end) {
                        constantValue = constantValue.substring(start + 1, end).trim();
                    }
                }
                
                // Look for associated comments
                String description = findDescription(lines, i);
                
                // If no comment found, use the constant value as description
                if (description.isEmpty() && !constantValue.equals(constantName)) {
                    description = constantValue;
                }
                
                // Create the reference item
                ReferenceItem item = new ReferenceItem(
                    constantName,
                    constantValue,
                    description,
                    extractCategory(constantName),
                    extractTags(constantName, description)
                );
                
                references.add(item);
            }
        }
        
        return references;
    }
    
    private String findDescription(List<String> lines, int currentIndex) {
        StringBuilder description = new StringBuilder();
        
        // Check for JavaDoc above the constant
        if (currentIndex > 0) {
            int searchIndex = currentIndex - 1;
            List<String> commentLines = new ArrayList<>();
            
            // Collect comment lines going backwards
            while (searchIndex >= 0) {
                String line = lines.get(searchIndex).trim();
                
                if (line.endsWith("*/")) {
                    // Found end of JavaDoc, now collect until start
                    commentLines.add(line);
                    
                    // If the same line also starts the comment, we are done
                    if (!line.startsWith("/**")) {
                        searchIndex--;
                        
                        while (searchIndex >= 0) {
                            line = lines.get(searchIndex).trim();
                            commentLines.add(line);
                            if (line.startsWith("/**")) {
                                break;
                            }
                            searchIndex--;
                        }
                    }
                    
                    // Reverse and parse JavaDoc
                    Collections.reverse(commentLines);
                    String javadoc = String.join("\n", commentLines);
                    description.append(parseJavaDoc(javadoc));
                    break;
                    
                } else if (line.startsWith("//")) {
                    // Single line comment
                    Matcher matcher = SINGLE_LINE_COMMENT_PATTERN.matcher(line);
                    if (matcher.find()) {
                        description.append(matcher.group(1).trim());
                    }
                    break;
                } else if (!line.isEmpty() && !line.startsWith("@")) {
                    // Hit non-comment, non-annotation line
                    break;
                }
                
                searchIndex--;
            }
        }
        
        // Check for inline comment on the same line
        if (description.length() == 0 && currentIndex < lines.size()) {
            String line = lines.get(currentIndex);
            int commentIndex = line.indexOf("//");
            if (commentIndex > 0) {
                String inlineComment = line.substring(commentIndex + 2).trim();
                description.append(inlineComment);
            }
        }
        
        return description.toString();
    }
    
    private String parseJavaDoc(String javadoc) {
        // Remove /** and */
        javadoc = javadoc.replaceAll("/\\*\\*", "").replaceAll("\\*/", "");
        
        // Remove * from beginning of lines
        String[] lines = javadoc.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("*")) {
                line = line.substring(1).trim();
            }
            if (!line.isEmpty() && !line.startsWith("@")) { // Skip @param, @return etc
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(line);
            }
        }
        
        return result.toString();
    }
    
    private String extractCategory(String constantName) {
        // Try to extract category from naming convention
        // e.g., ERROR_CODE_XXX -> "Error Codes"
        // HTTP_STATUS_XXX -> "HTTP Status"
        
        if (constantName.startsWith("ERROR_") || constantName.contains("_ERROR_")) {
            return "Error Codes";
        } else if (constantName.startsWith("HTTP_")) {
            return "HTTP";
        } else if (constantName.startsWith("STATUS_")) {
            return "Status Codes";
        } else if (constantName.startsWith("SQL_")) {
            return "SQL";
        } else if (constantName.contains("_CODE")) {
            return "Codes";
        }
        
        // Extract first part before underscore as category
        int firstUnderscore = constantName.indexOf('_');
        if (firstUnderscore > 0) {
            String prefix = constantName.substring(0, firstUnderscore);
            return capitalize(prefix);
        }
        
        return "General";
    }
    
    private List<String> extractTags(String constantName, String description) {
        List<String> tags = new ArrayList<>();
        String lowerName = constantName.toLowerCase();
        String lowerDesc = description.toLowerCase();
        
        // Add tags based on common keywords
        if (lowerName.contains("error") || lowerDesc.contains("error")) {
            tags.add("error");
        }
        if (lowerName.contains("http") || lowerDesc.contains("http")) {
            tags.add("http");
        }
        if (lowerName.contains("sql") || lowerDesc.contains("sql") || lowerDesc.contains("database")) {
            tags.add("database");
        }
        if (lowerName.contains("auth") || lowerDesc.contains("auth")) {
            tags.add("authentication");
        }
        if (lowerName.contains("api") || lowerDesc.contains("api")) {
            tags.add("api");
        }
        if (lowerName.contains("timeout") || lowerDesc.contains("timeout")) {
            tags.add("timeout");
        }
        
        return tags;
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}