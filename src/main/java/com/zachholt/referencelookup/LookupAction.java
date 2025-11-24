package com.zachholt.referencelookup;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.zachholt.referencelookup.ui.ReferenceBrowserWithTreePanel;
import org.jetbrains.annotations.NotNull;

public class LookupAction extends AnAction implements DumbAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // Open the Reference Browser tool window
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
        ToolWindow toolWindow = toolWindowManager.getToolWindow("ReferenceBrowser");
        
        if (toolWindow != null) {
            toolWindow.show();

            Editor editor = e.getData(CommonDataKeys.EDITOR);
            if (editor != null) {
                SelectionModel selectionModel = editor.getSelectionModel();
                String selectedText = selectionModel.getSelectedText();
                if (selectedText != null) {
                    String trimmed = selectedText.trim();
                    if (!trimmed.isEmpty()) {
                        Content content = toolWindow.getContentManager().getSelectedContent();
                        if (content == null && toolWindow.getContentManager().getContentCount() > 0) {
                            content = toolWindow.getContentManager().getContent(0);
                        }
                        if (content != null) {
                            ReferenceBrowserWithTreePanel panel = content.getUserData(ReferenceBrowserWithTreePanel.PANEL_KEY);
                            if (panel != null) {
                                panel.setSearchText(trimmed);
                            }
                        }
                    }
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
