package com.zachholt.referencelookup.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.parser.PsiConstantParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper class that isolates PSI-dependent code.
 * This class is only loaded when the Java plugin is available.
 */
public class PsiParserHelper {
    
    public static List<ReferenceItem> parse(Project project, Path path) {
        final List<ReferenceItem> items = new ArrayList<>();
        
        try {
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
        } catch (Exception e) {
            // If anything goes wrong with PSI, return empty list
            // The caller will fall back to regex parser
            return Collections.emptyList();
        }
        
        return items;
    }
}
