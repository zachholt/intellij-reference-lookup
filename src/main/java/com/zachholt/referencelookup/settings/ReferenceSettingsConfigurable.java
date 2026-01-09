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

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Reference Lookup";
    }

    @SuppressWarnings("removal")
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
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        return !javaFileField.getText().equals(settings.referenceFilePath);
    }

    @Override
    public void apply() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        settings.referenceFilePath = javaFileField.getText();

        // Trigger reload on all open projects
        for (com.intellij.openapi.project.Project project : com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()) {
            com.zachholt.referencelookup.service.ReferenceDataService.getInstance(project).reload();
        }
    }

    @Override
    public void reset() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        javaFileField.setText(settings.referenceFilePath);
    }
}
