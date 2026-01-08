package com.zachholt.referencelookup.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.zachholt.referencelookup.service.JiraService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class CreateSqlFromJiraAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

        if (project == null || selectedFile == null) {
            return;
        }

        // Get the directory - if a file is selected, use its parent
        VirtualFile directory = selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
        if (directory == null) {
            return;
        }

        // Show dialog to get Jira URL
        String jiraUrl = Messages.showInputDialog(
                project,
                "Enter Jira ticket URL or key (e.g., PROJ-123):",
                "Create SQL from Jira Ticket",
                Messages.getQuestionIcon()
        );

        if (jiraUrl == null || jiraUrl.trim().isEmpty()) {
            return; // User cancelled
        }

        // Fetch ticket info in background
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                JiraService jiraService = new JiraService();
                JiraService.JiraTicket ticket = jiraService.fetchTicket(jiraUrl.trim());

                // Create file on EDT with write action
                ApplicationManager.getApplication().invokeLater(() -> {
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        try {
                            createSqlFile(project, directory, ticket);
                        } catch (IOException ex) {
                            Messages.showErrorDialog(project, "Failed to create file: " + ex.getMessage(), "Error");
                        }
                    });
                });

            } catch (JiraService.JiraException ex) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    Messages.showErrorDialog(project, ex.getMessage(), "Jira Error");
                });
            }
        });
    }

    private void createSqlFile(Project project, VirtualFile directory, JiraService.JiraTicket ticket) throws IOException {
        String fileName = ticket.key + ".sql";
        String content = generateSqlContent(ticket);

        VirtualFile existingFile = directory.findChild(fileName);
        if (existingFile != null) {
            int result = Messages.showYesNoDialog(
                    project,
                    "File " + fileName + " already exists. Overwrite?",
                    "File Exists",
                    Messages.getQuestionIcon()
            );
            if (result != Messages.YES) {
                return;
            }
            existingFile.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
            openFile(project, existingFile);
        } else {
            VirtualFile newFile = directory.createChildData(this, fileName);
            newFile.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
            openFile(project, newFile);
        }
    }

    private void openFile(Project project, VirtualFile file) {
        FileEditorManager.getInstance(project).openFile(file, true);
    }

    private String generateSqlContent(JiraService.JiraTicket ticket) {
        StringBuilder sb = new StringBuilder();
        String date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        sb.append("-- ===========================================================================\n");
        sb.append("-- ").append(ticket.key).append(": ").append(ticket.summary).append("\n");
        sb.append("-- ===========================================================================\n");
        sb.append("-- Jira: ").append(ticket.url).append("\n");
        sb.append("-- Created: ").append(date).append("\n");

        if (!ticket.issueType.isEmpty()) {
            sb.append("-- Type: ").append(ticket.issueType).append("\n");
        }
        if (!ticket.status.isEmpty()) {
            sb.append("-- Status: ").append(ticket.status).append("\n");
        }
        if (!ticket.assignee.isEmpty()) {
            sb.append("-- Assignee: ").append(ticket.assignee).append("\n");
        }
        if (!ticket.reporter.isEmpty()) {
            sb.append("-- Reporter: ").append(ticket.reporter).append("\n");
        }

        if (!ticket.description.isEmpty()) {
            sb.append("-- ---------------------------------------------------------------------------\n");
            sb.append("-- Description:\n");
            for (String line : ticket.description.split("\n")) {
                sb.append("-- ").append(line).append("\n");
            }
        }

        sb.append("-- ===========================================================================\n");
        sb.append("\n");
        sb.append("-- Your SQL here\n");
        sb.append("\n");

        return sb.toString();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // Only show in project view context menu when a file or directory is selected
        Project project = e.getProject();
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(project != null && file != null);
    }
}
