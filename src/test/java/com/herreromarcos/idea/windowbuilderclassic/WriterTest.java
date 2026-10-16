package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.Placement;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;

import java.awt.Rectangle;

/** Codegenerierung: derselbe Code, den der WindowBuilder an diesen Stellen schreibt. */
public class WriterTest extends WindowBuilderTestCase {
    private static PaletteEntry entry(String simpleName) {
        return Palette.categories().values().stream().flatMap(java.util.List::stream)
                .filter(e -> e.className() != null && e.className().endsWith("." + simpleName)).findFirst().orElseThrow();
    }

    public void testAddButtonAbsolute() {
        final FormModel model = load(Forms.frame("null"));
        writer(model).addComponent(find(model, "contentPane"), entry("JButton"), Placement.absolute(new Rectangle(10, 10, 89, 23)));
        assertLines(
                "contentPane.setLayout(null);",
                "JButton btnNewButton = new JButton(\"New button\");",
                "btnNewButton.setBounds(10, 10, 89, 23);",
                "contentPane.add(btnNewButton);",
                "}");
        // Imports nach dem Codestil des Projekts (Einzelimport oder javax.swing.*)
        assertTrue(text(), text().contains("import javax.swing.JButton;") || text().contains("import javax.swing.*;"));
        assertEquals("New button", ((javax.swing.JButton) find(model(), "btnNewButton").getLive()).getText());
    }

    public void testTextFieldIsFieldAndColumnsAfterAdd() {
        final FormModel model = load(Forms.frame("null"));
        writer(model).addComponent(find(model, "contentPane"), entry("JTextField"), Placement.absolute(new Rectangle(5, 5, 100, 20)));
        assertLines("private JPanel contentPane;", "private JTextField textField;");
        assertLines(
                "textField = new JTextField();",
                "textField.setBounds(5, 5, 100, 20);",
                "contentPane.add(textField);",
                "textField.setColumns(10);");
    }

    public void testBorderLayoutConstraintsAndUniqueNames() {
        FormModel model = load(Forms.frame("new BorderLayout(0, 0)"));
        writer(model).addComponent(find(model, "contentPane"), entry("JButton"), Placement.constraints("java.awt.BorderLayout.NORTH"));
        model = model();
        writer(model).addComponent(find(model, "contentPane"), entry("JButton"), Placement.constraints("java.awt.BorderLayout.SOUTH"));
        assertLines(
                "JButton btnNewButton = new JButton(\"New button\");",
                "contentPane.add(btnNewButton, BorderLayout.NORTH);",
                "JButton btnNewButton_1 = new JButton(\"New button\");",
                "contentPane.add(btnNewButton_1, BorderLayout.SOUTH);");
        assertEquals("BorderLayout.SOUTH", find(model(), "btnNewButton_1").getConstraints());
    }

    public void testImplicitContentPaneOfApplicationWindow() {
        final FormModel model = load(Forms.APPLICATION_WINDOW);
        final ComponentInfo pane = model.getRoot().getChildren().get(0);
        writer(model).addComponent(pane, entry("JCheckBox"), Placement.append());
        assertLines(
                "frame.getContentPane().add(lblHello);",
                "JCheckBox chckbxNewCheckBox = new JCheckBox(\"New check box\");",
                "frame.getContentPane().add(chckbxNewCheckBox);");
    }

    public void testInsertBeforeSiblingInFlowLayout() {
        final FormModel model = load(Forms.PANEL);
        writer(model).addComponent(model.getRoot(), entry("JLabel"), Placement.before(find(model, "btnTwo")));
        assertLines(
                "add(btnOne);",
                "JLabel lblNewLabel = new JLabel(\"New label\");",
                "add(lblNewLabel);",
                "JButton btnTwo = new JButton(\"Two\");");
        assertEquals(java.util.List.of("btnOne", "lblNewLabel", "btnTwo"),
                model().getRoot().getChildren().stream().map(ComponentInfo::getName).toList());
    }

    public void testBlocksAreKeptInDialog() {
        final FormModel model = load(Forms.DIALOG);
        writer(model).addComponent(find(model, "buttonPane"), entry("JButton"), Placement.append());
        assertLines(
                "buttonPane.add(cancelButton);",
                "}",
                "{",
                "JButton btnNewButton = new JButton(\"New button\");",
                "buttonPane.add(btnNewButton);",
                "}",
                "}");
    }

    public void testEmptyPanelWithoutStatements() {
        final FormModel model = load("""
                import javax.swing.JPanel;

                public class Form extends JPanel {
                    public Form() {

                    }
                }
                """);
        writer(model).addComponent(model.getRoot(), entry("JButton"), Placement.append());
        assertLines(
                "public Form() {",
                "JButton btnNewButton = new JButton(\"New button\");",
                "add(btnNewButton);",
                "}");
    }

    public void testMissingConstructorIsCreated() {
        final FormModel model = load("""
                import javax.swing.JPanel;

                public class Form extends JPanel {
                }
                """);
        writer(model).addComponent(model.getRoot(), entry("JLabel"), Placement.append());
        assertLines(
                "public Form() {",
                "JLabel lblNewLabel = new JLabel(\"New label\");",
                "add(lblNewLabel);",
                "}");
    }

    public void testSetPropertyReplacesOrInserts() {
        FormModel model = load(Forms.PANEL);
        writer(model).setProperty(find(model, "btnOne"), "setToolTipText", "\"Tip\"");
        assertLines("JButton btnOne = new JButton(\"One\");", "btnOne.setToolTipText(\"Tip\");", "add(btnOne);");
        model = model();
        writer(model).setProperty(find(model, "btnOne"), "setToolTipText", "\"Other\"");
        assertLines("btnOne.setToolTipText(\"Other\");");
        model = model();
        writer(model).setProperty(find(model, "btnOne"), "setToolTipText", null);
        assertFalse(text().contains("setToolTipText"));
    }

    public void testRootPropertyGoesAfterExistingRootSetters() {
        final FormModel model = load(Forms.frame("new BorderLayout(0, 0)"));
        writer(model).setProperty(model.getRoot(), "setResizable", "false");
        assertLines("setBounds(100, 100, 450, 300);", "setResizable(false);", "contentPane = new JPanel();");
    }

    public void testSwitchToAbsoluteLayoutAddsBounds() {
        final FormModel model = load(Forms.PANEL);
        writer(model).setLayout(model.getRoot(), Palette.ABSOLUTE_LAYOUT);
        final String text = text();
        assertTrue(text, text.contains("setLayout(null);"));
        assertTrue(text, text.contains("btnOne.setBounds("));
        assertTrue(text, text.contains("btnTwo.setBounds("));
    }

    public void testSwitchFromBorderLayoutStripsConstraints() {
        final FormModel model = load(Forms.DIALOG);
        writer(model).setLayout(model.getRoot().getChildren().get(0), Palette.FLOW_LAYOUT);
        assertLines("getContentPane().setLayout(new FlowLayout(FlowLayout.CENTER, 5, 5));");
        assertLines("getContentPane().add(contentPanel);");
        assertLines("getContentPane().add(buttonPane);");
    }

    public void testBoxLayoutReferencesContainer() {
        final FormModel model = load(Forms.PANEL);
        writer(model).setLayout(model.getRoot(), Palette.BOX_LAYOUT);
        assertLines("setLayout(new BoxLayout(this, BoxLayout.X_AXIS));", "JButton btnOne = new JButton(\"One\");");
    }

    public void testSetConstraints() {
        final FormModel model = load(Forms.DIALOG);
        writer(model).setConstraints(find(model, "buttonPane"), "java.awt.BorderLayout.NORTH");
        assertLines("getContentPane().add(buttonPane, BorderLayout.NORTH);");
    }

    public void testDeleteRemovesAllCode() {
        final FormModel model = load(Forms.DIALOG);
        writer(model).delete(find(model, "okButton"));
        final String text = text();
        assertFalse(text, text.contains("okButton"));
        assertTrue(text, text.contains("cancelButton"));
        // Der leere { }-Block verschwindet mit
        assertLines("getContentPane().add(buttonPane, BorderLayout.SOUTH);", "{", "JButton cancelButton = new JButton(\"Cancel\");");
    }

    public void testDeleteContainerRemovesChildren() {
        final FormModel model = load(Forms.DIALOG);
        writer(model).delete(find(model, "buttonPane"));
        final String text = text();
        assertFalse(text, text.contains("buttonPane"));
        assertFalse(text, text.contains("okButton"));
        assertFalse(text, text.contains("cancelButton"));
        assertTrue(text, text.contains("contentPanel"));
    }

    public void testDeleteField() {
        final FormModel model = load(Forms.frame("null"));
        writer(model).addComponent(find(model, "contentPane"), entry("JTextField"), Placement.absolute(new Rectangle(1, 2, 3, 4)));
        final FormModel updated = model();
        writer(updated).delete(find(updated, "textField"));
        assertFalse(text(), text().contains("textField"));
    }

    public void testRename() {
        final FormModel model = load(Forms.PANEL);
        writer(model).rename(find(model, "btnOne"), "btnFirst");
        assertLines("JButton btnFirst = new JButton(\"One\");", "add(btnFirst);");
        assertFalse(writer(model()).isValidNewName(find(model(), "btnFirst"), "btnTwo"));
    }

    public void testMoveIntoOtherContainer() {
        final FormModel model = load("""
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        JButton button = new JButton("B");
                        button.setEnabled(false);
                        add(button);
                        JPanel panel = new JPanel();
                        add(panel);
                    }
                }
                """);
        writer(model).move(find(model, "button"), find(model, "panel"), Placement.append());
        assertLines(
                "JPanel panel = new JPanel();",
                "add(panel);",
                "JButton button = new JButton(\"B\");",
                "button.setEnabled(false);",
                "panel.add(button);");
        final FormModel moved = model();
        assertSame(find(moved, "panel"), find(moved, "button").getParent());
    }

    public void testReorderInFlowLayout() {
        final FormModel model = load(Forms.PANEL);
        writer(model).move(find(model, "btnTwo"), model.getRoot(), Placement.before(find(model, "btnOne")));
        assertEquals(java.util.List.of("btnTwo", "btnOne"), model().getRoot().getChildren().stream().map(ComponentInfo::getName).toList());
    }

    public void testMoveInAbsoluteLayout() {
        final FormModel model = load(Forms.frame("null"));
        writer(model).addComponent(find(model, "contentPane"), entry("JLabel"), Placement.absolute(new Rectangle(10, 10, 60, 14)));
        final FormModel updated = model();
        writer(updated).move(find(updated, "lblNewLabel"), find(updated, "contentPane"), Placement.absolute(new Rectangle(30, 40, 60, 14)));
        assertLines("lblNewLabel.setBounds(30, 40, 60, 14);");
        assertEquals(new Rectangle(30, 40, 60, 14), find(model(), "lblNewLabel").getLive().getBounds());
    }

    public void testScrollPaneViewport() {
        FormModel model = load(Forms.frame("new BorderLayout(0, 0)"));
        writer(model).addComponent(find(model, "contentPane"), entry("JScrollPane"), Placement.constraints("java.awt.BorderLayout.CENTER"));
        model = model();
        writer(model).addComponent(find(model, "scrollPane"), entry("JTable"), Placement.of(ComponentInfo.Association.VIEWPORT));
        assertLines(
                "JScrollPane scrollPane = new JScrollPane();",
                "contentPane.add(scrollPane, BorderLayout.CENTER);",
                "table = new JTable();",
                "scrollPane.setViewportView(table);");
    }

    public void testDesignSizeOfWindow() {
        final FormModel model = load(Forms.frame("null"));
        writer(model).setDesignSize(new java.awt.Dimension(600, 400));
        assertLines("setBounds(100, 100, 600, 400);");
    }

    public void testUndoIsOneStep() {
        final FormModel model = load(Forms.PANEL);
        final String before = text();
        writer(model).addComponent(model.getRoot(), entry("JButton"), Placement.append());
        assertFalse(before.equals(text()));
        final com.intellij.openapi.fileEditor.FileEditor editor =
                com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.getInstanceEx(getProject()).getSelectedEditor(myFixture.getFile().getVirtualFile());
        com.intellij.openapi.command.undo.UndoManager.getInstance(getProject()).undo(editor);
        assertEquals(before, text());
    }
}
