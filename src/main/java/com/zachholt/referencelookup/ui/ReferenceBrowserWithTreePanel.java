package com.zachholt.referencelookup.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.JBUI;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.service.ReferenceDataService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

public class ReferenceBrowserWithTreePanel extends SimpleToolWindowPanel {
    private final Project project;
    private final ReferenceDataService dataService;
    private final SearchTextField searchField;
    private final JLabel statusLabel;
    private final JTextArea detailsArea;
    private final JBTabbedPane tabbedPane;
    
    // List view components
    private final DefaultListModel<ReferenceItem> listModel;
    private final JBList<ReferenceItem> referenceList;
    
    // Tree view components
    private final Tree categoryTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;

    public ReferenceBrowserWithTreePanel(Project project) {
        super(true, true);
        this.project = project;
        this.dataService = project.getService(ReferenceDataService.class);
        this.searchField = new SearchTextField();
        this.statusLabel = new JLabel(" ");
        this.detailsArea = new JTextArea();
        this.tabbedPane = new JBTabbedPane();
        
        // List components
        this.listModel = new DefaultListModel<>();
        this.referenceList = new JBList<>(listModel);
        
        // Tree components
        this.rootNode = new DefaultMutableTreeNode("All References");
        this.treeModel = new DefaultTreeModel(rootNode);
        this.categoryTree = new Tree(treeModel);
        
        setupUI();
        loadData();
    }

    private void setupUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // Search panel at the top
        JPanel searchPanel = createSearchPanel();
        mainPanel.add(searchPanel, BorderLayout.NORTH);
        
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
        panel.setBorder(JBUI.Borders.empty(5));
        
        searchField.getTextEditor().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                filterContent();
            }
        });
        
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
        referenceList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    insertSelectedReference(referenceList.getSelectedValue());
                }
            }
        });
        
        JBScrollPane scrollPane = new JBScrollPane(referenceList);
        tabbedPane.addTab("All References", scrollPane);
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
                        append((String) userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                        append(" (" + node.getChildCount() + ")", SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    } else if (userObject instanceof ReferenceItem) {
                        // Reference item node
                        ReferenceItem item = (ReferenceItem) userObject;
                        append(item.getCode(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                        append(" - " + item.getDescription(), SimpleTextAttributes.GRAY_ATTRIBUTES);
                    }
                }
            }
        });
        
        categoryTree.addTreeSelectionListener(this::updateDetailsFromTree);
        
        // Double-click to insert
        categoryTree.addMouseListener(new MouseAdapter() {
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
        });
        
        JBScrollPane scrollPane = new JBScrollPane(categoryTree);
        tabbedPane.addTab("By Category", scrollPane);
    }

    private JPanel createDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(5));
        
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        
        JBScrollPane scrollPane = new JBScrollPane(detailsArea);
        panel.add(scrollPane, BorderLayout.CENTER);
        
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
            // Load list view
            List<ReferenceItem> references = dataService.getAllReferences();
            listModel.clear();
            for (ReferenceItem ref : references) {
                listModel.addElement(ref);
            }
            
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
        
        // Expand all categories
        for (int i = 0; i < categoryTree.getRowCount(); i++) {
            categoryTree.expandRow(i);
        }
    }

    private void filterContent() {
        String searchText = searchField.getText().trim();
        
        SwingUtilities.invokeLater(() -> {
            if (searchText.isEmpty()) {
                loadData();
            } else {
                // Filter list view
                List<ReferenceItem> filtered = dataService.search(searchText);
                listModel.clear();
                for (ReferenceItem ref : filtered) {
                    listModel.addElement(ref);
                }
                
                // Filter tree view
                filterTreeData(searchText);
                
                updateStatus();
            }
        });
    }

    private void filterTreeData(String searchText) {
        rootNode.removeAllChildren();
        
        List<ReferenceItem> filtered = dataService.search(searchText);
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
        
        // Expand all categories
        for (int i = 0; i < categoryTree.getRowCount(); i++) {
            categoryTree.expandRow(i);
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
            }
        }
    }

    private void updateDetails(ReferenceItem item) {
        if (item != null) {
            StringBuilder details = new StringBuilder();
            details.append("Code: ").append(item.getCode()).append("\n\n");
            details.append("Description: ").append(item.getDescription()).append("\n\n");
            if (item.getCategory() != null) {
                details.append("Category: ").append(item.getCategory()).append("\n\n");
            }
            if (item.getTags() != null && !item.getTags().isEmpty()) {
                details.append("Tags: ").append(String.join(", ", item.getTags())).append("\n");
            }
            detailsArea.setText(details.toString());
            detailsArea.setCaretPosition(0);
        } else {
            detailsArea.setText("");
        }
    }

    private void updateStatus() {
        int total = dataService.getAllReferences().size();
        int shown = listModel.getSize();
        if (shown < total) {
            statusLabel.setText(String.format("Showing %d of %d references", shown, total));
        } else {
            statusLabel.setText(String.format("%d references", total));
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
}