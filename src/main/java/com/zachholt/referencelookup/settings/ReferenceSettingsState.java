package com.zachholt.referencelookup.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
    name = "com.zachholt.referencelookup.settings.ReferenceSettingsState",
    storages = @Storage("ReferenceLookupPlugin.xml")
)
public class ReferenceSettingsState implements PersistentStateComponent<ReferenceSettingsState> {

    public String referenceFilePath = System.getProperty("user.home") + "/.reference-lookup/Reference.java";
    public boolean useJsonFile = false;
    public String jsonFilePath = System.getProperty("user.home") + "/.reference-lookup/references.json";

    public static ReferenceSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(ReferenceSettingsState.class);
    }

    @Nullable
    @Override
    public ReferenceSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ReferenceSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}