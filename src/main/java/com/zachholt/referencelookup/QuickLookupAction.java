package com.zachholt.referencelookup;

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

    // Cache for pre-computed search results to avoid EDT blocking
    private static final Key<CachedSearchResult> CACHED_RESULT_KEY = Key.create("QuickLookupAction.CachedResult");

    // Cache validity period (5 seconds)
    private static final long CACHE_VALIDITY_MS = 5000;

    /**
     * Holds cached search results with query and timestamp for validation.
     */
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

                // Pre-compute search results in background during update()
                // This keeps the cache warm so getChildren() doesn't block
                precomputeSearchResults(project, selectedText.trim());
            } else {
                e.getPresentation().setText("Reference Lookup");
            }
        }
    }

    /**
     * Pre-computes search results on a background thread and stores in project cache.
     * Called from update() to keep the cache warm before getChildren() is invoked.
     */
    private void precomputeSearchResults(Project project, String query) {
        ReferenceDataService service = ReferenceDataService.getInstance(project);
        if (!service.isLoaded()) {
            service.loadReferencesAsync();
            return;
        }

        CachedSearchResult cached = project.getUserData(CACHED_RESULT_KEY);
        if (cached != null && cached.isValid(query)) {
            return; // Already have valid cached results
        }

        // Run search in background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            if (project.isDisposed()) return;

            List<ReferenceItem> results = service.search(query, 11);

            // Store in project-level cache (thread-safe via Key mechanism)
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

        // Use cached results - NO blocking search on EDT!
        CachedSearchResult cached = project.getUserData(CACHED_RESULT_KEY);
        List<ReferenceItem> matches;

        if (cached != null && cached.isValid(query)) {
            matches = cached.results;
        } else {
            // Cache miss - show "Searching..." and trigger background search
            precomputeSearchResults(project, query);
            actions.add(createDisabledAction("Searching..."));
            actions.add(createOpenBrowserAction(query));
            return actions.toArray(new AnAction[0]);
        }
        
        // Build menu from cached results (fast, no blocking)
        if (!matches.isEmpty()) {
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

    /**
     * Creates a disabled action with the given text.
     */
    private AnAction createDisabledAction(String text) {
        return new AnAction(text) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                // Do nothing
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(false);
            }
        };
    }

    /**
     * Creates an action to open the Reference Browser tool window.
     */
    private AnAction createOpenBrowserAction() {
        return createOpenBrowserAction(null);
    }

    /**
     * Creates an action to open the Reference Browser tool window with optional search text.
     */
    private AnAction createOpenBrowserAction(String searchText) {
        return new AnAction("Open Reference Browser") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openToolWindowWithSelection(e, searchText);
            }
        };
    }

    /**
     * Creates an action showing "... and N more" that opens the browser.
     */
    private AnAction createMoreResultsAction(int moreCount, String searchText) {
        return new AnAction("... and " + moreCount + " more (Open Reference Browser)") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                openToolWindowWithSelection(e, searchText);
            }
        };
    }

    private void openToolWindowWithSelection(AnActionEvent e, String selection) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

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
            super(formatMenuItem(item));
            this.item = item;
            
            // Set tooltip to show full description
            String tooltip = item.getCode() + "\n" + 
                           (item.getDescription() != null ? item.getDescription() : "No description");
            getTemplatePresentation().setDescription(tooltip);
        }
        
        private static String formatMenuItem(ReferenceItem item) {
            String desc = item.getDescription();
            if (desc == null) {
                desc = "No description";
            }
            
            String display = item.getCode();
            if (item.getValue() != null && !item.getValue().isEmpty()) {
                display += " = " + item.getValue();
            }
            
            // Truncate description if too long
            if (desc.length() > 50) {
                desc = desc.substring(0, 47) + "...";
            }
            
            return display + "  [Click to copy]";
        }
        
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            
            // Copy the code to clipboard
            String codeToCopy = item.getValue() != null && !item.getValue().isEmpty() ? item.getValue() : item.getCode();
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(codeToCopy), null);
            
            // Show notification
            if (project != null) {
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("Reference Lookup")
                        .createNotification(
                                "Copied: " + codeToCopy,
                                NotificationType.INFORMATION)
                        .notify(project);
            }
        }
    }
}
