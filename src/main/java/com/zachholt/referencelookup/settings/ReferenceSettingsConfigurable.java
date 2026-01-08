package com.zachholt.referencelookup.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ReferenceSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private TextFieldWithBrowseButton javaFileField = new TextFieldWithBrowseButton();

    // Jira settings fields
    private JTextField jiraBaseUrlField = new JTextField();
    private JTextField jiraEmailField = new JTextField();
    private JPasswordField jiraApiTokenField = new JPasswordField();

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Reference Lookup";
    }

    @Override
    public @Nullable JComponent createComponent() {
        javaFileField.addBrowseFolderListener(
            "Select Java Reference File",
            "Choose the Java file containing reference constants",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("java")
        );

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JLabel("Java Reference File:"), javaFileField, 1, false)
                .addSeparator()
                .addComponent(new JLabel("<html><b>Jira Integration</b></html>"))
                .addLabeledComponent(new JLabel("Jira Base URL:"), jiraBaseUrlField, 1, false)
                .addTooltip("e.g., https://yourcompany.atlassian.net")
                .addLabeledComponent(new JLabel("Email:"), jiraEmailField, 1, false)
                .addLabeledComponent(new JLabel("API Token:"), jiraApiTokenField, 1, false)
                .addTooltip("Create at: https://id.atlassian.com/manage-profile/security/api-tokens")
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        return !javaFileField.getText().equals(settings.referenceFilePath)
                || !jiraBaseUrlField.getText().equals(settings.jiraBaseUrl)
                || !jiraEmailField.getText().equals(settings.jiraEmail)
                || !new String(jiraApiTokenField.getPassword()).equals(settings.jiraApiToken);
    }

    @Override
    public void apply() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        settings.referenceFilePath = javaFileField.getText();

        // Jira settings
        settings.jiraBaseUrl = jiraBaseUrlField.getText().replaceAll("/+$", ""); // Remove trailing slashes
        settings.jiraEmail = jiraEmailField.getText();
        settings.jiraApiToken = new String(jiraApiTokenField.getPassword());

        // Trigger reload on all open projects
        for (com.intellij.openapi.project.Project project : com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()) {
            com.zachholt.referencelookup.service.ReferenceDataService.getInstance(project).reload();
        }
    }

    @Override
    public void reset() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        javaFileField.setText(settings.referenceFilePath);
        jiraBaseUrlField.setText(settings.jiraBaseUrl);
        jiraEmailField.setText(settings.jiraEmail);
        jiraApiTokenField.setText(settings.jiraApiToken);
    }
}
