package org.graphify.viz.ui;

import org.graphify.core.model.Entity;
import org.graphify.core.model.Relationship;
import org.graphify.viz.search.HybridSearcher;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main application window for the Graphify Visualizer.
 */
public class MainFrame extends JFrame {
    private final GraphCanvas canvas;
    private final JTree entityTree;
    private final DefaultTreeModel treeModel;
    private final DefaultMutableTreeNode rootNode;
    private final JTextArea propertiesArea;
    private final JCheckBox hierarchicalToggle;
    
    private HybridSearcher searcher;
    private List<Entity> allEntities;
    private final JTextField searchField;
    private final JPanel filterPanel;
    private final Map<String, JCheckBox> typeFilters = new HashMap<>();

    public MainFrame() {
        setTitle("Graphify - OpenCL Graph Visualizer");
        setSize(1200, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        canvas = new GraphCanvas();

        // Initialize Tree
        rootNode = new DefaultMutableTreeNode("Entities");
        treeModel = new DefaultTreeModel(rootNode);
        entityTree = new JTree(treeModel) {
            @Override
            public String convertValueToText(Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                if (value instanceof DefaultMutableTreeNode node) {
                    if (node.getUserObject() instanceof Entity entity) {
                        return entity.name();
                    }
                }
                return super.convertValueToText(value, selected, expanded, leaf, row, hasFocus);
            }
        };

        // Properties Area
        propertiesArea = new JTextArea();
        propertiesArea.setEditable(false);
        propertiesArea.setLineWrap(true);
        propertiesArea.setWrapStyleWord(true);
        propertiesArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        // Sidebar Layout
        JPanel sidebar = new JPanel(new BorderLayout());
        
        // Controls at the top of sidebar
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFiltersAndNotify(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFiltersAndNotify(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFiltersAndNotify(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        controls.add(searchPanel);

        filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(filterPanel);

        hierarchicalToggle = new JCheckBox("Hierarchical Layout");
        controls.add(hierarchicalToggle);
        
        sidebar.add(controls, BorderLayout.NORTH);

        // Nested split pane for tree and properties
        JSplitPane sidebarSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(entityTree),
                new JScrollPane(propertiesArea));
        sidebarSplit.setDividerLocation(600);
        sidebar.add(sidebarSplit, BorderLayout.CENTER);

        // Main split pane
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, canvas);
        mainSplit.setDividerLocation(300);

        setLayout(new BorderLayout());
        add(mainSplit, BorderLayout.CENTER);

        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        infoPanel.add(new JLabel("Force-Directed Layout (OpenCL Accelerated)"));
        infoPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY));
        add(infoPanel, BorderLayout.SOUTH);
    }

    public void updateGraph(List<Entity> entities, List<Relationship> relationships, 
                            float[] positions, Map<String, Integer> entityIdToIndex) {
        this.allEntities = entities;
        
        if (this.allEntities == null || this.searcher == null || this.allEntities.size() != entities.size()) {
            this.searcher = new HybridSearcher(entities);
        }

        updateFilterCheckboxes(entities);
        canvas.updateData(entities, relationships, positions, entityIdToIndex);
        applyFiltersAndNotify();
    }

    private void updateFilterCheckboxes(List<Entity> entities) {
        Set<String> types = entities.stream().map(e -> e.type().name()).collect(Collectors.toSet());
        boolean changed = false;
        for (String type : types) {
            if (!typeFilters.containsKey(type)) {
                JCheckBox cb = new JCheckBox(type, true);
                cb.addActionListener(e -> applyFiltersAndNotify());
                typeFilters.put(type, cb);
                filterPanel.add(cb);
                changed = true;
            }
        }
        if (changed) {
            filterPanel.revalidate();
            filterPanel.repaint();
        }
    }

    private void applyFiltersAndNotify() {
        if (allEntities == null) return;

        String query = searchField.getText().trim();
        List<Entity> filtered;
        
        if (query.isEmpty()) {
            filtered = allEntities;
        } else {
            filtered = searcher.search(query);
        }

        // Apply type filters
        List<Entity> finalFiltered = filtered.stream()
                .filter(e -> {
                    JCheckBox cb = typeFilters.get(e.type().name());
                    return cb == null || cb.isSelected();
                })
                .collect(Collectors.toList());

        updateTree(finalFiltered);
    }

    public void updateTree(List<Entity> entities) {
        // ... (keep current updateTree implementation, but handle selection preservation) ...
        // Save selection
        Object selectedObj = null;
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) entityTree.getLastSelectedPathComponent();
        if (selectedNode != null) selectedObj = selectedNode.getUserObject();

        rootNode.removeAllChildren();
        
        // Group entities by type
        Map<String, List<Entity>> groupedEntities = entities.stream()
                .collect(Collectors.groupingBy(e -> e.type().name()));

        for (Map.Entry<String, List<Entity>> entry : groupedEntities.entrySet()) {
            DefaultMutableTreeNode typeNode = new DefaultMutableTreeNode(entry.getKey());
            for (Entity entity : entry.getValue()) {
                typeNode.add(new DefaultMutableTreeNode(entity));
            }
            rootNode.add(typeNode);
        }
        
        treeModel.reload();
        // Expand first level
        for (int i = 0; i < entityTree.getRowCount(); i++) {
            entityTree.expandRow(i);
        }
    }

    public void updateProperties(Entity entity) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(entity.id()).append("\n");
        sb.append("Type: ").append(entity.type()).append("\n");
        sb.append("Name: ").append(entity.name()).append("\n");
        sb.append("\nProperties:\n");
        if (entity.metadata() != null) {
            entity.metadata().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }
        propertiesArea.setText(sb.toString());
        propertiesArea.setCaretPosition(0);
    }

    public void clearProperties() {
        propertiesArea.setText("");
    }

    public JCheckBox getHierarchicalToggle() {
        return hierarchicalToggle;
    }

    public JTree getEntityTree() {
        return entityTree;
    }

    public GraphCanvas getCanvas() {
        return canvas;
    }

    public boolean isHierarchicalLayoutEnabled() {
        return hierarchicalToggle.isSelected();
    }
}
