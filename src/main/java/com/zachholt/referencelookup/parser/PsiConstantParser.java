package com.zachholt.referencelookup.parser;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.zachholt.referencelookup.model.ReferenceItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PsiConstantParser {

    public List<ReferenceItem> parse(PsiFile psiFile) {
        if (!(psiFile instanceof PsiJavaFile)) {
            return Collections.emptyList();
        }

        PsiJavaFile javaFile = (PsiJavaFile) psiFile;
        List<ReferenceItem> references = new ArrayList<>();

        // Iterate over all classes in the file
        for (PsiClass psiClass : javaFile.getClasses()) {
            parseClass(psiClass, references);
        }

        return references;
    }

        private void parseClass(PsiClass psiClass, List<ReferenceItem> references) {
            for (PsiField field : psiClass.getFields()) {
                boolean isConstant = false;
    
                if (field instanceof PsiEnumConstant) {
                    isConstant = true;
                } else {
                    PsiModifierList modifiers = field.getModifierList();
                    if (modifiers != null &&
                        modifiers.hasModifierProperty(PsiModifier.PUBLIC) &&
                        modifiers.hasModifierProperty(PsiModifier.STATIC) &&
                        modifiers.hasModifierProperty(PsiModifier.FINAL)) {
                        isConstant = true;
                    }
                }
    
                if (isConstant) {
                    parseField(field, references);
                }
            }
    
            // Also parse inner classes
            for (PsiClass innerClass : psiClass.getInnerClasses()) {
                parseClass(innerClass, references);
            }
        }
    
        private void parseField(PsiField field, List<ReferenceItem> references) {
            String name = field.getName();
            if (name == null) return;
    
            String value = null;
            
            if (field instanceof PsiEnumConstant) {
                // For Enums, the name is the primary "value"
                value = name;
                // Optionally, we could check for arguments: MY_ENUM("Some Description")
                PsiExpressionList argList = ((PsiEnumConstant) field).getArgumentList();
                if (argList != null) {
                     PsiExpression[] args = argList.getExpressions();
                     if (args.length > 0) {
                         // If the first arg is a string literal, it might be a better description/value
                         if (args[0] instanceof PsiLiteralExpression) {
                             Object val = ((PsiLiteralExpression) args[0]).getValue();
                             if (val != null) {
                                 // We'll use this as the description later if needed, 
                                 // or we could treat it as the "value". 
                                 // For now, let's stick to the name as code, but extract this for description.
                             }
                         }
                     }
                }
            } else {
                PsiExpression initializer = field.getInitializer();
                if (initializer instanceof PsiLiteralExpression) {
                    Object literalValue = ((PsiLiteralExpression) initializer).getValue();
                    if (literalValue != null) {
                        value = literalValue.toString();
                    }
                } else if (initializer != null) {
                    value = initializer.getText();
                }
            }
    
            if (value == null) return; // Could not determine value
    
            String description = extractDescription(field);
            
            // If no description, use value (cleaned up)
            if (description.isEmpty()) {
                 // For enums, if we didn't find a doc comment, maybe the constructor arg is the description?
                 if (field instanceof PsiEnumConstant) {
                     PsiExpressionList argList = ((PsiEnumConstant) field).getArgumentList();
                     if (argList != null && argList.getExpressions().length > 0) {
                         PsiExpression firstArg = argList.getExpressions()[0];
                         if (firstArg instanceof PsiLiteralExpression) {
                             Object val = ((PsiLiteralExpression) firstArg).getValue();
                             if (val != null) {
                                 description = val.toString();
                             }
                         }
                     }
                 }
                 
                 if (description.isEmpty()) {
                     description = cleanValue(value);
                 }
            }
    
            references.add(new ReferenceItem(
                name,
                description,
                extractCategory(name),
                extractTags(name, description)
            ));
        }
        
            private String cleanValue(String value) {
                // Simple cleanup for "quoted strings"
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    return value.substring(1, value.length() - 1);
                }
                return value;
            }
        
            private String extractDescription(PsiField field) {
                // 1. Try JavaDoc
                PsiDocComment docComment = field.getDocComment();
                if (docComment != null) {
                    return cleanJavaDoc(docComment.getText());
                }
        
                // 2. Try End-of-line comment (// ...)
                PsiElement next = field.getNextSibling();
                while (next != null) {
                    if (next instanceof PsiComment) {
                        String text = next.getText().trim();
                        if (text.startsWith("//")) {
                            return text.substring(2).trim();
                        }
                        break; 
                    }
                    if (next instanceof PsiWhiteSpace) {
                        if (next.getText().contains("\n")) {
                            break; 
                        }
                    } else {
                        // found something else
                    }
                    next = next.getNextSibling();
                }
        
                // 3. Try Comment *above* the field (if not JavaDoc)
                PsiElement prev = field.getPrevSibling();
                while (prev != null) {
                     if (prev instanceof PsiComment) {
                         if (prev instanceof PsiDocComment) {
                             prev = prev.getPrevSibling();
                             continue;
                         }
                         String text = prev.getText().trim();
                         if (text.startsWith("//")) {
                             return text.substring(2).trim();
                         } else if (text.startsWith("/*")) {
                             return text.replace("/*", "").replace("*/", "").trim();
                         }
                         break;
                     }
                     if (prev instanceof PsiWhiteSpace) {
                         int newLines = prev.getText().split("\n", -1).length - 1;
                         if (newLines > 1) break; 
                     } else {
                         break; 
                     }
                     prev = prev.getPrevSibling();
                }
        
                return "";
            }
        
            private String cleanJavaDoc(String text) {            if (text == null) return "";
            return text.replaceAll("/\\*\\*", "")
                       .replaceAll("\\*/", "")
                       .replaceAll("(?m)^\\s*\\*\\s?", "") // Remove leading * on each line (multiline mode)
                       .replaceAll("\n", " ") // Merge lines
                       .trim();
        }
    // --- Copied helper methods ---

    private String extractCategory(String constantName) {
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
        
        if (lowerName.contains("error") || lowerDesc.contains("error")) tags.add("error");
        if (lowerName.contains("http") || lowerDesc.contains("http")) tags.add("http");
        if (lowerName.contains("sql") || lowerDesc.contains("sql") || lowerDesc.contains("database")) tags.add("database");
        if (lowerName.contains("auth") || lowerDesc.contains("auth")) tags.add("authentication");
        if (lowerName.contains("api") || lowerDesc.contains("api")) tags.add("api");
        
        return tags;
    }
    
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
