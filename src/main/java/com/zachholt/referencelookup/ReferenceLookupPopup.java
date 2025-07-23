package com.zachholt.referencelookup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.zachholt.referencelookup.model.ReferenceItem;
import com.zachholt.referencelookup.service.ReferenceDataService;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

public class ReferenceLookupPopup {
    private final Project project;
    private final String initialQuery;
    private JBPopup popup;
    private JBList<ReferenceItem> resultsList;
    private DefaultListModel<ReferenceItem> listModel;
    private SearchTextField searchField;

    public ReferenceLookupPopup(Project project, String initialQuery) {
        this.project = project;
        this.initialQuery = initialQuery;
    }

    public void show() {
        JPanel mainPanel = createMainPanel();
        
        popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(mainPanel, searchField)
                .setTitle("Reference Lookup")
                .setFocusable(true)
                .setRequestFocus(true)
                .setMovable(true)
                .setResizable(true)
                .setMinSize(new Dimension(600, 400))
                .createPopup();

        popup.showCenteredInCurrentWindow(project);
        
        // Perform initial search
        performSearch(initialQuery);
        searchField.setText(initialQuery);
        searchField.getTextEditor().selectAll();
    }

    private JPanel createMainPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.empty(10));

        // Search field
        searchField = new SearchTextField();
        searchField.getTextEditor().setColumns(40);
        searchField.getTextEditor().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                performSearch(searchField.getText());
            }
        });

        // Results list
        listModel = new DefaultListModel<>();
        resultsList = new JBList<>(listModel);
        resultsList.setCellRenderer(new ReferenceListCellRenderer());
        resultsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Double-click to copy
        resultsList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    copySelectedToClipboard();
                }
            }
        });

        // Enter key to copy and close
        resultsList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    copySelectedToClipboard();
                    popup.cancel();
                }
            }
        });

        // Escape key handler for search field
        searchField.getTextEditor().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    popup.cancel();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    resultsList.requestFocus();
                    if (resultsList.getSelectedIndex() == -1 && listModel.size() > 0) {
                        resultsList.setSelectedIndex(0);
                    }
                }
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(resultsList);
        
        // Info label
        JLabel infoLabel = new JLabel("Double-click or press Enter to copy code");
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC));
        infoLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        panel.add(searchField, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(infoLabel, BorderLayout.SOUTH);

        return panel;
    }

    private void performSearch(String query) {
        listModel.clear();
        
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        ReferenceDataService service = ReferenceDataService.getInstance(project);
        List<ReferenceItem> results = service.search(query);
        
        for (ReferenceItem item : results) {
            listModel.addElement(item);
        }

        if (listModel.size() > 0) {
            resultsList.setSelectedIndex(0);
        }
    }

    private void copySelectedToClipboard() {
        ReferenceItem selected = resultsList.getSelectedValue();
        if (selected != null) {
            String textToCopy = selected.getCode();
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(textToCopy), null);
        }
    }

    private static class ReferenceListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof ReferenceItem) {
                ReferenceItem item = (ReferenceItem) value;
                String html = String.format(
                    "<html><b>%s</b><br/><span style='color:gray;'>%s</span></html>",
                    escapeHtml(item.getCode()),
                    escapeHtml(item.getDescription())
                );
                label.setText(html);
                label.setBorder(JBUI.Borders.empty(5));
            }
            
            return label;
        }

        private String escapeHtml(String text) {
            if (text == null) return "";
            return text.replace("&", "&amp;")
                      .replace("<", "&lt;")
                      .replace(">", "&gt;")
                      .replace("\"", "&quot;")
                      .replace("'", "&#39;");
        }
    }
}