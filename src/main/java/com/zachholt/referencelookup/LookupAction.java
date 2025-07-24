package com.zachholt.referencelookup;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class LookupAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // Open the Reference Browser tool window
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("ReferenceBrowser");
        
        if (toolWindow != null) {
            toolWindow.show();
            
            // TODO: If we have selected text, we could set it in the search field
            Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
                SelectionModel selectionModel = editor.getSelectionModel();
                String selectedText = selectionModel.getSelectedText();
                if (selectedText != null && !selectedText.trim().isEmpty()) {
                    // In the future, we could pass the selected text to the tool window
                    // to automatically search for it
                }
            }
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        boolean enabled = project != null && editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}