package com.zachholt.referencelookup;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class LookupAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            Messages.showMessageDialog(project, "No editor found", "Error", Messages.getErrorIcon());
            return;
        }

        SelectionModel selectionModel = editor.getSelectionModel();
        String selectedText = selectionModel.getSelectedText();
        
        if (selectedText == null || selectedText.trim().isEmpty()) {
            Messages.showMessageDialog(project, "Please select some text to look up", "No Selection", Messages.getInformationIcon());
            return;
        }

        // Show the lookup popup
        ReferenceLookupPopup popup = new ReferenceLookupPopup(project, selectedText.trim());
        popup.show();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        
        boolean enabled = project != null && editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}