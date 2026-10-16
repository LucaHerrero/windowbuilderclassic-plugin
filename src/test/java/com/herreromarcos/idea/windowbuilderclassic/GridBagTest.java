package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.GridBagAlignment;
import com.herreromarcos.idea.windowbuilderclassic.codegen.Placement;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;

import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** GridBagLayout: Lesen, Code im WindowBuilder-Stil, Gitter-Operationen. */
public class GridBagTest extends WindowBuilderTestCase {
    private static final String EMPTY = """
            import java.awt.GridBagLayout;
            import javax.swing.JPanel;

            public class Form extends JPanel {
                public Form() {
                    GridBagLayout gridBagLayout = new GridBagLayout();
                    gridBagLayout.columnWidths = new int[]{0};
                    gridBagLayout.rowHeights = new int[]{0};
                    gridBagLayout.columnWeights = new double[]{Double.MIN_VALUE};
                    gridBagLayout.rowWeights = new double[]{Double.MIN_VALUE};
                    setLayout(gridBagLayout);
                }
            }
            """;

    private static final String TWO = """
            import java.awt.GridBagConstraints;
            import java.awt.GridBagLayout;
            import java.awt.Insets;
            import javax.swing.JButton;
            import javax.swing.JPanel;

            public class Form extends JPanel {
                public Form() {
                    GridBagLayout gridBagLayout = new GridBagLayout();
                    gridBagLayout.columnWidths = new int[]{0, 0, 0};
                    gridBagLayout.rowHeights = new int[]{0, 0};
                    gridBagLayout.columnWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
                    gridBagLayout.rowWeights = new double[]{0.0, Double.MIN_VALUE};
                    setLayout(gridBagLayout);
                    JButton btnA = new JButton("A");
                    GridBagConstraints gbc_btnA = new GridBagConstraints();
                    gbc_btnA.insets = new Insets(0, 0, 0, 5);
                    gbc_btnA.gridx = 0;
                    gbc_btnA.gridy = 0;
                    add(btnA, gbc_btnA);
                    JButton btnB = new JButton("B");
                    GridBagConstraints gbc_btnB = new GridBagConstraints();
                    gbc_btnB.gridx = 1;
                    gbc_btnB.gridy = 0;
                    add(btnB, gbc_btnB);
                }
            }
            """;

    private static PaletteEntry entry(String simpleName) {
        return Palette.categories().values().stream().flatMap(java.util.List::stream)
                .filter(e -> e.className() != null && e.className().endsWith("." + simpleName)).findFirst().orElseThrow();
    }

    private static GridBagConstraints constraints(FormModel model, String name) {
        final ComponentInfo info = find(model, name);
        return ((GridBagLayout) ((JPanel) info.getParent().getLive()).getLayout()).getConstraints(info.getLive());
    }

    public void testParse() {
        final FormModel model = load(TWO);
        final ComponentInfo root = model.getRoot();
        assertNotNull(root.getLayoutObject());
        assertEquals("gridBagLayout", root.getLayoutObject().getName());
        assertEquals(java.util.Set.of("columnWidths", "rowHeights", "columnWeights", "rowWeights"), root.getLayoutObject().getFields().keySet());
        final GridBagLayout layout = (GridBagLayout) ((JPanel) root.getLive()).getLayout();
        assertEquals(3, layout.columnWidths.length);
        assertEquals(Double.MIN_VALUE, layout.columnWeights[2]);
        final ComponentInfo a = find(model, "btnA");
        assertEquals("gbc_btnA", a.getConstraintsObject().getName());
        assertEquals(1, a.getConstraintsObject().getUses());
        assertEquals(new Insets(0, 0, 0, 5), constraints(model, "btnA").insets);
        assertEquals(1, constraints(model, "btnB").gridx);
        assertEmpty(model.getWarnings());
    }

    public void testSetGridBagLayout() {
        final FormModel model = load(Forms.frame("null"));
        writer(model).setLayout(find(model, "contentPane"), Palette.GRID_BAG_LAYOUT);
        assertLines(
                "GridBagLayout gbl_contentPane = new GridBagLayout();",
                "gbl_contentPane.columnWidths = new int[]{0};",
                "gbl_contentPane.rowHeights = new int[]{0};",
                "gbl_contentPane.columnWeights = new double[]{Double.MIN_VALUE};",
                "gbl_contentPane.rowWeights = new double[]{Double.MIN_VALUE};",
                "contentPane.setLayout(gbl_contentPane);");
        assertTrue(GridBagWriterAccess.isGridBag(find(model(), "contentPane")));
    }

    public void testAddFirstComponent() {
        final FormModel model = load(EMPTY);
        writer(model).addComponent(model.getRoot(), entry("JButton"), Placement.cell(0, 0, false, false));
        assertLines(
                "gridBagLayout.columnWidths = new int[]{0, 0};",
                "gridBagLayout.rowHeights = new int[]{0, 0};",
                "gridBagLayout.columnWeights = new double[]{0.0, Double.MIN_VALUE};",
                "gridBagLayout.rowWeights = new double[]{0.0, Double.MIN_VALUE};",
                "setLayout(gridBagLayout);",
                "JButton btnNewButton = new JButton(\"New button\");",
                "GridBagConstraints gbc_btnNewButton = new GridBagConstraints();",
                "gbc_btnNewButton.gridx = 0;",
                "gbc_btnNewButton.gridy = 0;",
                "add(btnNewButton, gbc_btnNewButton);");
    }

    public void testTextFieldFillsHorizontally() {
        final FormModel model = load(EMPTY);
        writer(model).addComponent(model.getRoot(), entry("JTextField"), Placement.cell(0, 0, false, false));
        assertLines(
                "GridBagConstraints gbc_textField = new GridBagConstraints();",
                "gbc_textField.fill = GridBagConstraints.HORIZONTAL;",
                "gbc_textField.gridx = 0;",
                "gbc_textField.gridy = 0;",
                "add(textField, gbc_textField);",
                "textField.setColumns(10);");
    }

    public void testNewRowAddsGapsToRowAbove() {
        final FormModel model = load(TWO);
        writer(model).addComponent(model.getRoot(), entry("JLabel"), Placement.cell(0, 1, false, false));
        assertLines(
                "gridBagLayout.rowHeights = new int[]{0, 0, 0};",
                "gridBagLayout.columnWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};",
                "gridBagLayout.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};");
        final FormModel updated = model();
        assertEquals(new Insets(0, 0, 5, 5), constraints(updated, "btnA").insets);
        assertEquals(new Insets(0, 0, 5, 0), constraints(updated, "btnB").insets);
        // Neue Komponente in der letzten Zeile, nicht in der letzten Spalte
        assertEquals(new Insets(0, 0, 0, 5), constraints(updated, "lblNewLabel").insets);
        assertLines("gbc_btnB.insets = new Insets(0, 0, 5, 0);", "gbc_btnB.gridx = 1;");
    }

    public void testInsertColumnShiftsOthers() {
        final FormModel model = load(TWO);
        writer(model).addComponent(model.getRoot(), entry("JLabel"), Placement.cell(1, 0, true, false));
        final FormModel updated = model();
        assertEquals(0, constraints(updated, "btnA").gridx);
        assertEquals(1, constraints(updated, "lblNewLabel").gridx);
        assertEquals(2, constraints(updated, "btnB").gridx);
        assertLines("gridBagLayout.columnWidths = new int[]{0, 0, 0, 0};");
        assertEquals(new Insets(0, 0, 0, 5), constraints(updated, "lblNewLabel").insets);
        assertEquals(new Insets(0, 0, 0, 0), constraints(updated, "btnB").insets);
    }

    public void testMoveToOtherCell() {
        final FormModel model = load(TWO);
        writer(model).move(find(model, "btnB"), model.getRoot(), Placement.cell(0, 1, false, false));
        final FormModel updated = model();
        assertEquals(0, constraints(updated, "btnB").gridx);
        assertEquals(1, constraints(updated, "btnB").gridy);
        // btnA ist jetzt allein in der letzten Spalte, aber nicht mehr in der letzten Zeile
        assertEquals(new Insets(0, 0, 5, 5), constraints(updated, "btnA").insets);
    }

    public void testDeleteRemovesConstraintsVariable() {
        final FormModel model = load(TWO);
        writer(model).delete(find(model, "btnB"));
        final String text = text();
        assertFalse(text, text.contains("gbc_btnB"));
        assertFalse(text, text.contains("btnB"));
        assertTrue(text, text.contains("gbc_btnA"));
    }

    public void testSharedConstraintsAreSplitOnEdit() {
        final FormModel model = load("""
                import java.awt.*;
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        setLayout(new GridBagLayout());
                        GridBagConstraints c = new GridBagConstraints();
                        c.gridx = 0;
                        c.anchor = GridBagConstraints.WEST;
                        JLabel first = new JLabel("1");
                        add(first, c);
                        c.gridx = 1;
                        JLabel second = new JLabel("2");
                        add(second, c);
                    }
                }
                """);
        assertEquals(2, find(model, "first").getConstraintsObject().getUses());
        writer(model).setGridBagConstraint(find(model, "second"), "gridy", "2");
        assertLines(
                "GridBagConstraints gbc_second = new GridBagConstraints();",
                "gbc_second.anchor = GridBagConstraints.WEST;",
                "gbc_second.gridx = 1;",
                "gbc_second.gridy = 2;",
                "add(second, gbc_second);");
        final FormModel updated = model();
        assertEquals(GridBagConstraints.WEST, constraints(updated, "second").anchor);
        assertEquals(0, constraints(updated, "first").gridx);
        assertTrue(text(), text().contains("add(first, c);"));
    }

    public void testConstraintFields() {
        final FormModel model = load(TWO);
        writer(model).setGridBagConstraint(find(model, "btnA"), "anchor", "java.awt.GridBagConstraints.NORTHWEST");
        assertLines("GridBagConstraints gbc_btnA = new GridBagConstraints();", "gbc_btnA.anchor = GridBagConstraints.NORTHWEST;",
                "gbc_btnA.insets = new Insets(0, 0, 0, 5);");
        FormModel updated = model();
        writer(updated).setGridBagConstraint(find(updated, "btnA"), "anchor", null);
        assertFalse(text().contains("anchor"));
        updated = model();
        writer(updated).setGridBagConstraint(find(updated, "btnA"), "gridwidth", "2");
        updated = model();
        assertEquals(2, constraints(updated, "btnA").gridwidth);
    }

    public void testColumnWeight() {
        final FormModel model = load(TWO);
        writer(model).setGridWeight(model.getRoot(), true, 1, 1.0);
        assertLines("gridBagLayout.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};");
    }

    public void testWeightWithoutLayoutVariableCreatesOne() {
        final FormModel model = load("""
                import java.awt.*;
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        setLayout(new GridBagLayout());
                        JLabel label = new JLabel("x");
                        GridBagConstraints gbc_label = new GridBagConstraints();
                        gbc_label.gridx = 0;
                        gbc_label.gridy = 0;
                        add(label, gbc_label);
                    }
                }
                """);
        writer(model).setGridWeight(model.getRoot(), false, 0, 1.0);
        assertLines(
                "GridBagLayout gridBagLayout = new GridBagLayout();",
                "gridBagLayout.columnWidths = new int[]{0, 0};",
                "gridBagLayout.rowHeights = new int[]{0, 0};",
                "gridBagLayout.columnWeights = new double[]{0.0, Double.MIN_VALUE};",
                "gridBagLayout.rowWeights = new double[]{1.0, Double.MIN_VALUE};",
                "setLayout(gridBagLayout);");
    }

    public void testInsertAndDeleteColumn() {
        FormModel model = load(TWO);
        writer(model).insertGridLine(model.getRoot(), true, 0);
        model = model();
        assertEquals(1, constraints(model, "btnA").gridx);
        assertEquals(2, constraints(model, "btnB").gridx);
        writer(model).deleteGridLine(model.getRoot(), true, 2);
        final String text = text();
        assertFalse(text, text.contains("btnB"));
        assertTrue(text, text.contains("gridBagLayout.columnWidths = new int[]{0, 0, 0};"));
    }

    public void testConvertFromBorderLayout() {
        final FormModel model = load(Forms.DIALOG);
        writer(model).setLayout(model.getRoot().getChildren().get(0), Palette.GRID_BAG_LAYOUT);
        assertLines(
                "GridBagLayout gridBagLayout = new GridBagLayout();",
                "gridBagLayout.columnWidths = new int[]{0, 0};",
                "gridBagLayout.rowHeights = new int[]{0, 0, 0};",
                "gridBagLayout.columnWeights = new double[]{0.0, Double.MIN_VALUE};",
                "gridBagLayout.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};",
                "getContentPane().setLayout(gridBagLayout);");
        assertLines(
                "GridBagConstraints gbc_contentPanel = new GridBagConstraints();",
                "gbc_contentPanel.insets = new Insets(0, 0, 5, 0);",
                "gbc_contentPanel.gridx = 0;",
                "gbc_contentPanel.gridy = 0;",
                "getContentPane().add(contentPanel, gbc_contentPanel);");
        final FormModel updated = model();
        assertEquals(1, constraints(updated, "buttonPane").gridy);
    }

    public void testSwitchAwayFromGridBag() {
        final FormModel model = load(TWO);
        writer(model).setLayout(model.getRoot(), Palette.FLOW_LAYOUT);
        final String text = text();
        assertFalse(text, text.contains("gridBagLayout"));
        assertFalse(text, text.contains("gbc_"));
        assertLines("add(btnA);");
        assertLines("add(btnB);");
    }

    public void testMoveOutOfGridIntoPanel() {
        final FormModel model = load(TWO);
        writer(model).addComponent(model.getRoot(), entry("JPanel"), Placement.cell(0, 1, false, false));
        FormModel updated = model();
        writer(updated).move(find(updated, "btnB"), find(updated, "panel"), Placement.append());
        final String text = text();
        assertFalse(text, text.contains("gbc_btnB"));
        assertTrue(text, text.contains("panel.add(btnB);"));
        updated = model();
        assertSame(find(updated, "panel"), find(updated, "btnB").getParent());
    }

    public void testMoveIntoGrid() {
        final FormModel model = load("""
                import java.awt.*;
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        setLayout(new BorderLayout());
                        JButton outside = new JButton("O");
                        add(outside, BorderLayout.NORTH);
                        JPanel grid = new JPanel();
                        add(grid, BorderLayout.CENTER);
                        GridBagLayout gbl_grid = new GridBagLayout();
                        gbl_grid.columnWidths = new int[]{0};
                        gbl_grid.rowHeights = new int[]{0};
                        gbl_grid.columnWeights = new double[]{Double.MIN_VALUE};
                        gbl_grid.rowWeights = new double[]{Double.MIN_VALUE};
                        grid.setLayout(gbl_grid);
                    }
                }
                """);
        writer(model).move(find(model, "outside"), find(model, "grid"), Placement.cell(0, 0, false, false));
        assertLines(
                "JButton outside = new JButton(\"O\");",
                "GridBagConstraints gbc_outside = new GridBagConstraints();",
                "gbc_outside.gridx = 0;",
                "gbc_outside.gridy = 0;",
                "grid.add(outside, gbc_outside);");
        assertLines("gbl_grid.columnWidths = new int[]{0, 0};");
    }

    public void testAlignment() {
        FormModel model = load(TWO);
        writer(model).setGridBagAlignment(find(model, "btnA"), GridBagAlignment.Horizontal.LEFT, null);
        assertLines("gbc_btnA.anchor = GridBagConstraints.WEST;", "gbc_btnA.insets = new Insets(0, 0, 0, 5);");
        model = model();
        writer(model).setGridBagAlignment(find(model, "btnA"), null, GridBagAlignment.Vertical.TOP);
        assertLines("gbc_btnA.anchor = GridBagConstraints.NORTHWEST;");
        model = model();
        writer(model).setGridBagAlignment(find(model, "btnA"), GridBagAlignment.Horizontal.FILL, null);
        assertLines("gbc_btnA.anchor = GridBagConstraints.NORTH;", "gbc_btnA.insets = new Insets(0, 0, 0, 5);",
                "gbc_btnA.fill = GridBagConstraints.HORIZONTAL;");
        model = model();
        assertEquals(GridBagAlignment.Horizontal.FILL, GridBagAlignment.horizontal(constraints(model, "btnA")));
        assertEquals(GridBagAlignment.Vertical.TOP, GridBagAlignment.vertical(constraints(model, "btnA")));
        writer(model).setGridBagAlignment(find(model, "btnA"), GridBagAlignment.Horizontal.CENTER, GridBagAlignment.Vertical.CENTER);
        assertFalse(text(), text().contains("gbc_btnA.anchor"));
        assertFalse(text(), text().contains("gbc_btnA.fill"));
    }

    /** gridx = 0 ist die erste Spalte, nicht GridBagConstraints.REMAINDER (die Konstante hat denselben Wert). */
    public void testFirstColumnIsNotRemainder() {
        final FormModel model = load("""
                import java.awt.*;
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        setLayout(new GridBagLayout());
                        GridBagConstraints c = new GridBagConstraints();
                        c.gridx = 0;
                        c.gridy = 0;
                        JLabel first = new JLabel("1");
                        add(first, c);
                        JLabel second = new JLabel("2");
                        add(second, c);
                    }
                }
                """);
        writer(model).setGridBagConstraint(find(model, "first"), "weightx", "1.0");
        assertLines("gbc_first.weightx = 1.0;", "gbc_first.gridx = 0;", "gbc_first.gridy = 0;");
        assertFalse(text(), text().contains("REMAINDER"));
    }

    public void testColumnAlignment() {
        final FormModel model = load(TWO);
        writer(model).addComponent(model.getRoot(), entry("JLabel"), Placement.cell(0, 1, false, false));
        FormModel updated = model();
        writer(updated).setGridLineAlignment(updated.getRoot(), true, 0, GridBagAlignment.Horizontal.RIGHT, null);
        updated = model();
        assertEquals(GridBagConstraints.EAST, constraints(updated, "btnA").anchor);
        assertEquals(GridBagConstraints.EAST, constraints(updated, "lblNewLabel").anchor);
        assertEquals(GridBagConstraints.CENTER, constraints(updated, "btnB").anchor);
    }
}
