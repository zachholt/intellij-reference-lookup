package com.zachholt.referencelookup;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.service.ReferenceDataService;
import com.zachholt.referencelookup.ui.ReferenceBrowserWithTreePanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

public class QuickLookupAction extends ActionGroup implements DumbAware {

    private static final Key<CachedSearchResult> CACHED_RESULT_KEY = Key.create("QuickLookupAction.CachedResult");
    private static final long CACHE_VALIDITY_MS = 5000;

    private static class CachedSearchResult {
        final String query;
        final List<ReferenceItem> results;
        final long timestamp;

        CachedSearchResult(String query, List<ReferenceItem> results) {
            this.query = query;
            this.results = results;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid(String currentQuery) {
            return query != null && query.equals(currentQuery)
                && (System.currentTimeMillis() - timestamp) < CACHE_VALIDITY_MS;
        }
    }

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
                precomputeSearchResults(project, selectedText.trim());
            } else {
                e.getPresentation().setText("Reference Lookup");
            }
        }
    }

    private void precomputeSearchResults(Project project, String query) {
        ReferenceDataService service = ReferenceDataService.getInstance(project);
        if (!service.isLoaded()) {
            service.loadReferencesAsync();
            return;
        }

        CachedSearchResult cached = project.getUserData(CACHED_RESULT_KEY);
        if (cached != null && cached.isValid(query)) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) return;
            List<ReferenceItem> results = service.search(query, 11);
            project.putUserData(CACHED_RESULT_KEY, new CachedSearchResult(query, results));
        });
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

        String query = selectedText.trim();
        List<AnAction> actions = new ArrayList<>();

        ReferenceDataService service = ReferenceDataService.getInstance(project);

        if (!service.isLoaded()) {
            service.loadReferencesAsync();
            actions.add(createDisabledAction("Loading references..."));
            actions.add(createOpenBrowserAction());
            return actions.toArray(new AnAction[0]);
        }

        CachedSearchResult cached = project.getUserData(CACHED_RESULT_KEY);
        List<ReferenceItem> matches;

        if (cached != null && cached.isValid(query)) {
            matches = cached.results;
        } else {
            precomputeSearchResults(project, query);
            actions.add(createDisabledAction("Searching..."));
            actions.add(createOpenBrowserAction(query));
            return actions.toArray(new AnAction[0]);
        }
        
        if (!matches.isEmpty()) {
            // Add header with clipboard icon
            actions.add(createCopyHeader());
            actions.add(Separator.getInstance());
            
            int count = Math.min(matches.size(), 5);
            for (int i = 0; i < count; i++) {
                actions.add(new QuickValueAction(matches.get(i)));
            }

            if (matches.size() > count) {
                actions.add(Separator.getInstance());
                actions.add(createMoreResultsAction(matches.size() - count, query));
            }

            actions.add(Separator.getInstance());
            actions.add(createOpenBrowserAction(query));
        } else {
            actions.add(createDisabledAction("No matches found"));
            actions.add(Separator.getInstance());
            actions.add(createOpenBrowserAction(query));
        }

        return actions.toArray(new AnAction[0]);
    }

    private AnAction createCopyHeader() {
        AnAction action = new AnAction("Click to copy value", null, AllIcons.Actions.Copy) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // Do nothing - this is just a header
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(false);
            }
        };
        return action;
    }

    private AnAction createDisabledAction(String text) {
        return new AnAction(text) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {}

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(false);
            }
        };
    }

    private AnAction createOpenBrowserAction() {
        return createOpenBrowserAction(null);
    }

    private AnAction createOpenBrowserAction(String searchText) {
        return new AnAction("Open Reference Browser", null, AllIcons.Actions.Search) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openToolWindowWithSelection(e, searchText);
            }
        };
    }

    private AnAction createMoreResultsAction(int moreCount, String searchText) {
        return new AnAction("... and " + moreCount + " more") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openToolWindowWithSelection(e, searchText);
            }
        };
    }

    private void openToolWindowWithSelection(AnActionEvent e, String selection) {
        Project project = e.getProject();
        if (project == null) return;

        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("ReferenceBrowser");
        if (toolWindow != null) {
            toolWindow.show();
            var contentManager = toolWindow.getContentManager();
            Content content = contentManager.getSelectedContent();
            if (content == null && contentManager.getContentCount() > 0) {
                content = contentManager.getContent(0);
            }

            if (content != null) {
                ReferenceBrowserWithTreePanel panel = content.getUserData(ReferenceBrowserWithTreePanel.PANEL_KEY);
                if (panel != null && selection != null && !selection.trim().isEmpty()) {
                    panel.setSearchText(selection.trim());
                }
            }
        }
    }
    
    private static class QuickValueAction extends AnAction {
        private final ReferenceItem item;
        
        QuickValueAction(ReferenceItem item) {
            super(formatMenuItem(item), item.getDescription(), AllIcons.Actions.Copy);
            this.item = item;
        }
        
        private static String formatMenuItem(ReferenceItem item) {
            String value = item.getValue();
            String code = item.getCode();
            
            if (value != null && !value.isEmpty()) {
                return code + " (" + value + ")";
            }
            return code;
        }
        
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            
            String valueToCopy = item.getValue() != null && !item.getValue().isEmpty() 
                    ? item.getValue() 
                    : item.getCode();
            
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(valueToCopy), null);
            
            if (project != null) {
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("Reference Lookup")
                        .createNotification("Copied: " + valueToCopy, NotificationType.INFORMATION)
                        .notify(project);
            }
        }
    }
}
