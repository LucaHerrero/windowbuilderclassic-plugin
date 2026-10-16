package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** Der Components-Baum der Structure-Ansicht. */
final class ComponentTreePanel extends JPanel {
    private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final Tree tree = new Tree(treeModel);
    private boolean updating;

    ComponentTreePanel(Consumer<ComponentInfo> onSelect, Consumer<ComponentInfo> onNavigate, PopupHandler popup) {
        super(new BorderLayout());
        tree.setRootVisible(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(@NotNull JTree t, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                if (!(((DefaultMutableTreeNode) value).getUserObject() instanceof final ComponentInfo info)) return;
                final Component live = info.getLive();
                setIcon(ComponentIcons.forClass(live instanceof final WindowSurrogate window ? window.getWindowClass() : info.getClassName()));
                if (info.isThis()) {
                    append("(%s)".formatted(info.getClassName()));
                } else {
                    append(info.getPresentableName(), info.isImplicit() ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
                if (info.getConstraints() != null) append("  " + info.getConstraints().replace("java.awt.BorderLayout.", "BorderLayout."), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES);
            }
        });
        tree.addTreeSelectionListener(e -> {
            if (updating) return;
            final ComponentInfo info = selected();
            if (info != null) onSelect.accept(info);
        });
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final ComponentInfo info = selected();
                if (e.getClickCount() == 2 && info != null) onNavigate.accept(info);
            }
        });
        tree.addMouseListener(popup);
        add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER);
    }

    Tree getTree() {
        return tree;
    }

    void setModel(@Nullable FormModel model) {
        updating = true;
        try {
            final DefaultMutableTreeNode root = new DefaultMutableTreeNode(model == null ? null : model.getRoot());
            if (model != null) addChildren(root, model.getRoot());
            treeModel.setRoot(root);
            TreeUtil.expandAll(tree);
        } finally {
            updating = false;
        }
    }

    private static void addChildren(DefaultMutableTreeNode node, ComponentInfo info) {
        for (final ComponentInfo child : info.getChildren()) {
            final DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
            node.add(childNode);
            addChildren(childNode, child);
        }
    }

    void select(@Nullable ComponentInfo info) {
        updating = true;
        try {
            if (info == null) {
                tree.clearSelection();
                return;
            }
            final DefaultMutableTreeNode node = TreeUtil.findNodeWithObject((DefaultMutableTreeNode) treeModel.getRoot(), info);
            if (node != null) {
                final TreePath path = new TreePath(node.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
            }
        } finally {
            updating = false;
        }
    }

    private @Nullable ComponentInfo selected() {
        final TreePath path = tree.getSelectionPath();
        return path != null && path.getLastPathComponent() instanceof final DefaultMutableTreeNode node
                && node.getUserObject() instanceof final ComponentInfo info ? info : null;
    }
}
