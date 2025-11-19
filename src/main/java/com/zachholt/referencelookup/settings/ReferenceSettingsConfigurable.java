package com.zachholt.referencelookup.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ReferenceSettingsConfigurable implements Configurable {

    private JPanel mainPanel;
    private TextFieldWithBrowseButton javaFileField = new TextFieldWithBrowseButton();
    private TextFieldWithBrowseButton jsonFileField = new TextFieldWithBrowseButton();
    private JBCheckBox useJsonCheckBox = new JBCheckBox("Prefer JSON file over Java file");

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Reference Lookup";
    }

    @Override
    public @Nullable JComponent createComponent() {
        javaFileField.addBrowseFolderListener(
            "Select Java Reference File",
            "Choose the Java file containing constants",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("java")
        );

        jsonFileField.addBrowseFolderListener(
            "Select JSON Reference File",
            "Choose the JSON file containing references",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("json")
        );

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JLabel("Java Reference File:"), javaFileField, 1, false)
                .addSeparator()
                .addComponent(useJsonCheckBox)
                .addLabeledComponent(new JLabel("JSON Reference File:"), jsonFileField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        return mainPanel;
    }

    @Override
    public boolean isModified() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        boolean modified = !javaFileField.getText().equals(settings.referenceFilePath);
        modified |= useJsonCheckBox.isSelected() != settings.useJsonFile;
        modified |= !jsonFileField.getText().equals(settings.jsonFilePath);
        return modified;
    }

    @Override
    public void apply() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        settings.referenceFilePath = javaFileField.getText();
        settings.useJsonFile = useJsonCheckBox.isSelected();
        settings.jsonFilePath = jsonFileField.getText();
        
        // Trigger reload on all open projects
        for (com.intellij.openapi.project.Project project : com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()) {
            com.zachholt.referencelookup.service.ReferenceDataService.getInstance(project).reload();
        }
    }

    @Override
    public void reset() {
        ReferenceSettingsState settings = ReferenceSettingsState.getInstance();
        javaFileField.setText(settings.referenceFilePath);
        useJsonCheckBox.setSelected(settings.useJsonFile);
        jsonFileField.setText(settings.jsonFilePath);
    }
}