package com.zachholt.referencelookup.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.zachholt.referencelookup.ReferenceBundle;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.service.ReferenceDataService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

public class ReferenceBrowserWithTreePanel extends SimpleToolWindowPanel implements Disposable {
    public static final Key<ReferenceBrowserWithTreePanel> PANEL_KEY = Key.create("ReferenceBrowserPanel");

    private final Project project;
    private final ReferenceDataService dataService;
    private final SearchTextField searchField;
    private final JLabel statusLabel;
    private final JEditorPane detailsArea; // Changed to JEditorPane for HTML
    private final JBTabbedPane tabbedPane;

    // List view components
    private final CollectionListModel<ReferenceItem> listModel;
    private final JBList<ReferenceItem> referenceList;

    // Tree view components
    private final Tree categoryTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    // For proper cleanup
    private final Alarm searchAlarm;
    private final DocumentListener documentListener;
    private final TreeSelectionListener treeSelectionListener;
    private final MouseAdapter listMouseListener;
    private final MouseAdapter treeMouseListener;
    
    private ReferenceItem currentSelectedItem;

    public ReferenceBrowserWithTreePanel(Project project) {
        super(true, true);
        this.project = project;
        this.dataService = project.getService(ReferenceDataService.class);
        this.searchField = new SearchTextField();
        this.statusLabel = new JLabel(" ");
        this.detailsArea = new JEditorPane();
        this.detailsArea.setContentType("text/html");
        this.detailsArea.setEditable(false);
        this.detailsArea.setBackground(JBColor.PanelBackground);
        
        this.tabbedPane = new JBTabbedPane();

        // List components
        this.listModel = new CollectionListModel<>();
        this.referenceList = new JBList<>(listModel);

        // Tree components
        this.rootNode = new DefaultMutableTreeNode(ReferenceBundle.message("tab.all_references"));
        this.treeModel = new DefaultTreeModel(rootNode);
        this.categoryTree = new Tree(treeModel);

        // Initialize alarm for debounced search (300ms delay)
        this.searchAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

        // Create listeners that we'll clean up later
        this.documentListener = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                scheduleFilter();
            }
        };

        this.treeSelectionListener = this::updateDetailsFromTree;

        this.listMouseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    insertSelectedReference(referenceList.getSelectedValue());
                }
            }
        };

        this.treeMouseListener = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = categoryTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        if (node.getUserObject() instanceof ReferenceItem) {
                            insertSelectedReference((ReferenceItem) node.getUserObject());
                        }
                    }
                }
            }
        };

        setupUI();

        // Start loading references in background immediately
        dataService.loadReferencesAsync();

        // Update UI when loading completes
        dataService.onLoaded(() -> SwingUtilities.invokeLater(this::loadData));
    }

    public void setSearchText(String text) {
        searchField.setText(text);
        scheduleFilter();
    }

    private void setupUI() {
        // Main Toolbar
        DefaultActionGroup toolbarGroup = new DefaultActionGroup();
        toolbarGroup.add(new DumbAwareAction("Refresh", "Reload references", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                dataService.reload();
                statusLabel.setText("Reloading...");
                dataService.onLoaded(() -> SwingUtilities.invokeLater(() -> {
                    loadData();
                    filterContent(); // Re-apply filter if any
                }));
            }
        });
        
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("ReferenceBrowserToolbar", toolbarGroup, true);
        toolbar.setTargetComponent(this);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Top panel with Toolbar and Search
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(toolbar.getComponent(), BorderLayout.WEST);
        
        JPanel searchPanel = createSearchPanel();
        topPanel.add(searchPanel, BorderLayout.CENTER);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Split pane for tabbed pane and details
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setDividerLocation(400);
        splitPane.setResizeWeight(0.7);
        
        // Tabbed pane with list and tree views
        setupListView();
        setupTreeView();
        
        splitPane.setTopComponent(tabbedPane);
        
        // Details panel
        JPanel detailsPanel = createDetailsPanel();
        splitPane.setBottomComponent(detailsPanel);
        
        mainPanel.add(splitPane, BorderLayout.CENTER);
        
        // Status bar at the bottom
        JPanel statusPanel = createStatusPanel();
        mainPanel.add(statusPanel, BorderLayout.SOUTH);
        
        setContent(mainPanel);
    }

    private JPanel createSearchPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(2));
        searchField.getTextEditor().getDocument().addDocumentListener(documentListener);
        panel.add(searchField, BorderLayout.CENTER);
        return panel;
    }

    private void setupListView() {
        // Configure list
        referenceList.setCellRenderer(new ColoredListCellRenderer<ReferenceItem>() {
            @Override
            protected void customizeCellRenderer(JList<? extends ReferenceItem> list,
                                                ReferenceItem value,
                                                int index,
                                                boolean selected,
                                                boolean hasFocus) {
                if (value != null) {
                    setIcon(AllIcons.Nodes.Variable);
                    append(value.getCode(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                    append(" - " + value.getDescription(), SimpleTextAttributes.GRAY_ATTRIBUTES);
                    if (value.getCategory() != null) {
                        append(" [" + value.getCategory() + "]", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
                    }
                }
            }
        });
        
        referenceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        referenceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateDetailsFromList();
            }
        });

        // Double-click to insert
        referenceList.addMouseListener(listMouseListener);
        
        // Use ToolbarDecorator for nice border and actions (though we disable most defaults)
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(referenceList)
                .disableAddAction()
                .disableRemoveAction()
                .disableUpDownActions();
        
        tabbedPane.addTab(ReferenceBundle.message("tab.all_references"), decorator.createPanel());
    }

    private void setupTreeView() {
        categoryTree.setRootVisible(false);
        categoryTree.setShowsRootHandles(true);
        categoryTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        
        // Custom renderer for tree nodes
        categoryTree.setCellRenderer(new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(JTree tree, Object value, boolean selected,
                                            boolean expanded, boolean leaf, int row, boolean hasFocus) {
                if (value instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
                    Object userObject = node.getUserObject();
                    
                    if (userObject instanceof String) {
                        // Category node
                        setIcon(AllIcons.Nodes.Folder);
                        append((String) userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                        append(" (" + node.getChildCount() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    } else if (userObject instanceof ReferenceItem) {
                        // Reference item node
                        setIcon(AllIcons.Nodes.Variable);
                        ReferenceItem item = (ReferenceItem) userObject;
                        append(item.getCode(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        append(" - " + item.getDescription(), SimpleTextAttributes.GRAY_ATTRIBUTES);
                    }
                }
            }
        });
        
        categoryTree.addTreeSelectionListener(treeSelectionListener);

        // Double-click to insert
        categoryTree.addMouseListener(treeMouseListener);
        
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(categoryTree)
                .disableAddAction()
                .disableRemoveAction()
                .disableUpDownActions();
        
        tabbedPane.addTab(ReferenceBundle.message("tab.by_category"), decorator.createPanel());
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        
        // Header for details
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.empty(5));
        headerPanel.add(new JLabel("Details"), BorderLayout.WEST);
        
        // Copy button
        JButton copyButton = new JButton("Copy Code");
        copyButton.setIcon(AllIcons.Actions.Copy);
        copyButton.addActionListener(e -> {
            if (currentSelectedItem != null) {
                StringSelection selection = new StringSelection(currentSelectedItem.getCode());
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
            }
        });
        headerPanel.add(copyButton, BorderLayout.EAST);
        
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(new JBScrollPane(detailsArea), BorderLayout.CENTER);
        
        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(2, 5, 2, 5));
        
        statusLabel.setForeground(JBColor.GRAY);
        panel.add(statusLabel, BorderLayout.WEST);
        
        return panel;
    }

    private void loadData() {
        SwingUtilities.invokeLater(() -> {
            if (!dataService.isLoaded()) {
                statusLabel.setText(ReferenceBundle.message("label.loading"));
                return;
            }

            // Load list view efficiently
            List<ReferenceItem> references = dataService.getAllReferences();
            listModel.replaceAll(references);

            // Load tree view
            loadTreeData();

            updateStatus();
        });
    }

    private void loadTreeData() {
        rootNode.removeAllChildren();
        
        Map<String, List<ReferenceItem>> grouped = dataService.getReferencesGroupedByCategory();
        
        for (Map.Entry<String, List<ReferenceItem>> entry : grouped.entrySet()) {
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(entry.getKey());
            
            for (ReferenceItem item : entry.getValue()) {
                DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode(item);
                categoryNode.add(itemNode);
            }
            
            rootNode.add(categoryNode);
        }
        
        treeModel.reload();

        // Only expand if there are a reasonable number of rows (avoid performance hit)
        if (categoryTree.getRowCount() <= 50) {
            for (int i = 0; i < categoryTree.getRowCount(); i++) {
                categoryTree.expandRow(i);
            }
        }
    }

    // Debounced filter - only runs search after user stops typing for 300ms
    private void scheduleFilter() {
        searchAlarm.cancelAllRequests();
        searchAlarm.addRequest(() -> filterContent(), 300);
    }

    private void filterContent() {
        String searchText = searchField.getText().trim();

        if (searchText.isEmpty()) {
            SwingUtilities.invokeLater(this::loadData);
            return;
        }

        // Run search in background to avoid blocking UI
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            List<ReferenceItem> filtered = dataService.search(searchText);

            // Update UI on EDT
            SwingUtilities.invokeLater(() -> {
                // Filter list view efficiently
                listModel.replaceAll(filtered);

                // Filter tree view
                filterTreeData(filtered);

                updateStatus();
            });
        });
    }

    private void filterTreeData(List<ReferenceItem> filtered) {
        rootNode.removeAllChildren();

        Map<String, List<ReferenceItem>> grouped = new java.util.HashMap<>();

        // Group filtered results by category
        for (ReferenceItem item : filtered) {
            String category = item.getCategory() != null ? item.getCategory() : "Uncategorized";
            grouped.computeIfAbsent(category, k -> new java.util.ArrayList<>()).add(item);
        }

        // Build tree
        for (Map.Entry<String, List<ReferenceItem>> entry : grouped.entrySet()) {
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(entry.getKey());

            for (ReferenceItem item : entry.getValue()) {
                DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode(item);
                categoryNode.add(itemNode);
            }

            rootNode.add(categoryNode);
        }

        treeModel.reload();

        // Only expand if there are a reasonable number of rows (avoid performance hit)
        if (categoryTree.getRowCount() <= 50) {
            for (int i = 0; i < categoryTree.getRowCount(); i++) {
                categoryTree.expandRow(i);
            }
        }
    }

    private void updateDetailsFromList() {
        ReferenceItem selected = referenceList.getSelectedValue();
        updateDetails(selected);
    }

    private void updateDetailsFromTree(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        if (path != null) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node.getUserObject() instanceof ReferenceItem) {
                updateDetails((ReferenceItem) node.getUserObject());
            } else {
                detailsArea.setText("");
                currentSelectedItem = null;
            }
        }
    }

    private void updateDetails(ReferenceItem item) {
        currentSelectedItem = item;
        if (item != null) {
            StringBuilder html = new StringBuilder("<html><body style='font-family: sans-serif;'>");
            html.append("<h2>").append(item.getCode()).append("</h2>");
            html.append("<p><b>Description:</b> ").append(item.getDescription()).append("</p>");
            
            if (item.getCategory() != null) {
                html.append("<p><b>Category:</b> ").append(item.getCategory()).append("</p>");
            }
            
            if (item.getTags() != null && !item.getTags().isEmpty()) {
                html.append("<p><b>Tags:</b> ");
                for (String tag : item.getTags()) {
                    html.append("<span style='background-color: #e0e0e0; color: #333; padding: 2px 4px; border-radius: 3px;'>").append(tag).append("</span> ");
                }
                html.append("</p>");
            }
            html.append("</body></html>");
            
            detailsArea.setText(html.toString());
            detailsArea.setCaretPosition(0);
        } else {
            detailsArea.setText("");
        }
    }

    private void updateStatus() {
        int total = dataService.getAllReferences().size();
        int shown = listModel.getSize();
        if (shown < total) {
            statusLabel.setText(ReferenceBundle.message("label.showing_matches", shown, total));
        } else {
            statusLabel.setText(ReferenceBundle.message("label.all_references", total));
        }
    }

    private void insertSelectedReference(ReferenceItem item) {
        if (item != null) {
            // TODO: Insert into editor
            JOptionPane.showMessageDialog(this,
                "Would insert: " + item.getCode(),
                "Reference",
                JOptionPane.INFORMATION_MESSAGE);
        }
    }

    @Override
    public void dispose() {
        // Cancel any pending search requests
        searchAlarm.cancelAllRequests();

        // Remove all listeners to prevent memory leaks
        searchField.getTextEditor().getDocument().removeDocumentListener(documentListener);
        categoryTree.removeTreeSelectionListener(treeSelectionListener);
        referenceList.removeMouseListener(listMouseListener);
        categoryTree.removeMouseListener(treeMouseListener);

        // Clear data structures
        listModel.removeAll();
        rootNode.removeAllChildren();
    }
}
