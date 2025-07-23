package com.zachholt.referencelookup;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.service.ReferenceDataService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class QuickLookupAction extends ActionGroup {
    
    public QuickLookupAction() {
        super("Reference Lookup", true);
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        boolean enabled = project != null && editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabledAndVisible(enabled);
        
        if (enabled) {
            String selectedText = editor.getSelectionModel().getSelectedText();
            if (selectedText != null && selectedText.trim().length() <= 50) {
                e.getPresentation().setText("Lookup '" + selectedText.trim() + "'");
            } else {
                e.getPresentation().setText("Reference Lookup");
            }
        }
    }
    
    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        if (e == null) return EMPTY_ARRAY;
        
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        if (project == null || editor == null) return EMPTY_ARRAY;
        
        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        
        if (selectedText == null || selectedText.trim().isEmpty()) return EMPTY_ARRAY;
        
        List<AnAction> actions = new ArrayList<>();
        
        // Add the original search action
        actions.add(new LookupAction());
        actions.add(Separator.getInstance());
        
        // Search for matches
        ReferenceDataService service = ReferenceDataService.getInstance(project);
        List<ReferenceItem> matches = service.search(selectedText.trim());
        
        if (!matches.isEmpty()) {
            // Add quick results (limit to first 10)
            int count = Math.min(matches.size(), 10);
            for (int i = 0; i < count; i++) {
                ReferenceItem item = matches.get(i);
                actions.add(new QuickValueAction(item));
            }
            
            if (matches.size() > 10) {
                actions.add(Separator.getInstance());
                actions.add(new AnAction("... and " + (matches.size() - 10) + " more") {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent e) {
                        // Open the full search popup
                        new LookupAction().actionPerformed(e);
                    }
                });
            }
        } else {
            actions.add(new AnAction("No matches found") {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    // Do nothing
                }
                
                @Override
                public void update(@NotNull AnActionEvent e) {
                    e.getPresentation().setEnabled(false);
                }
            });
        }
        
        return actions.toArray(new AnAction[0]);
    }
    
    private static class QuickValueAction extends AnAction {
        private final ReferenceItem item;
        
        QuickValueAction(ReferenceItem item) {
            super(formatMenuItem(item));
            this.item = item;
        }
        
        private static String formatMenuItem(ReferenceItem item) {
            String desc = item.getDescription();
            if (desc != null && desc.length() > 60) {
                desc = desc.substring(0, 57) + "...";
            }
            return item.getCode() + " → " + (desc != null ? desc : "No description");
        }
        
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            // Copy the code to clipboard
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(item.getCode()), null);
        }
    }
}