package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.FormWriter;
import com.herreromarcos.idea.windowbuilderclassic.codegen.Placement;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;

import javax.swing.JMenu;
import javax.swing.JPanel;
import java.awt.Rectangle;
import java.util.Map;

/** Menüs, Struts &amp; Springs, AWT, Kopieren/Einfügen und Mehrfachänderungen. */
public class MenusAndToolsTest extends WindowBuilderTestCase {
    private static PaletteEntry entry(String category, String title) {
        return Palette.categories().get(category).stream().filter(e -> e.title().equals(title)).findFirst().orElseThrow();
    }

    public void testStrutsAndSprings() {
        FormModel model = load(Forms.PANEL);
        writer(model).setLayout(model.getRoot(), Palette.BOX_LAYOUT);
        model = model();
        writer(model).addComponent(model.getRoot(), entry("Struts & Springs", "Horizontal Strut"), Placement.before(find(model, "btnTwo")));
        assertLines("add(btnOne);", "Component horizontalStrut = Box.createHorizontalStrut(20);", "add(horizontalStrut);");
        model = model();
        final ComponentInfo strut = find(model, "horizontalStrut");
        assertEquals("javax.swing.Box.Filler", strut.getClassName());
        assertSame(model.getRoot(), strut.getParent());
        assertEquals(20, strut.getLive().getPreferredSize().width);
        writer(model).addComponent(model.getRoot(), entry("Struts & Springs", "Vertical Box"), Placement.append());
        assertLines("Box verticalBox = Box.createVerticalBox();", "add(verticalBox);");
        model = model();
        writer(model).addComponent(find(model, "verticalBox"), entry("Components", "JLabel"), Placement.append());
        assertLines("verticalBox.add(lblNewLabel);");
    }

    public void testMenuBarMenuAndItems() {
        FormModel model = load(Forms.frame("new BorderLayout(0, 0)"));
        writer(model).addComponent(model.getRoot(), entry("Menu", "JMenuBar"), Placement.of(Association.MENU_BAR));
        assertLines("setBounds(100, 100, 450, 300);", "JMenuBar menuBar = new JMenuBar();", "setJMenuBar(menuBar);", "contentPane = new JPanel();");
        model = model();
        final ComponentInfo menuBar = find(model, "menuBar");
        assertEquals(Association.MENU_BAR, menuBar.getAssociation());
        assertSame(menuBar.getLive(), ((WindowSurrogate) model.getRoot().getLive()).getJMenuBar());
        writer(model).addComponent(menuBar, entry("Menu", "JMenu"), Placement.append());
        model = model();
        writer(model).addComponent(find(model, "mnNewMenu"), entry("Menu", "JMenuItem"), Placement.append());
        model = model();
        writer(model).addComponent(find(model, "mnNewMenu"), entry("Menu", "JCheckBoxMenuItem"), Placement.append());
        assertLines(
                "JMenu mnNewMenu = new JMenu(\"New menu\");",
                "menuBar.add(mnNewMenu);",
                "JMenuItem mntmNewMenuItem = new JMenuItem(\"New menu item\");",
                "mnNewMenu.add(mntmNewMenuItem);",
                "JCheckBoxMenuItem chckbxmntmNewCheckItem = new JCheckBoxMenuItem(\"New check item\");",
                "mnNewMenu.add(chckbxmntmNewCheckItem);");
        model = model();
        final JMenu menu = (JMenu) find(model, "mnNewMenu").getLive();
        assertEquals(2, menu.getItemCount());
        assertSame(menu.getPopupMenu(), find(model, "mntmNewMenuItem").getLive().getParent());
    }

    public void testPopupMenu() {
        FormModel model = load(Forms.PANEL);
        writer(model).addComponent(find(model, "btnOne"), entry("Menu", "JPopupMenu"), Placement.of(Association.POPUP));
        assertLines("JPopupMenu popupMenu = new JPopupMenu();", "addPopup(btnOne, popupMenu);");
        assertTrue(text(), text().contains("private static void addPopup(Component component, final JPopupMenu popup) {"));
        model = model();
        final ComponentInfo popup = find(model, "popupMenu");
        assertEquals(Association.POPUP, popup.getAssociation());
        assertSame(find(model, "btnOne"), popup.getParent());
        writer(model).addComponent(popup, entry("Menu", "JMenuItem"), Placement.append());
        assertLines("popupMenu.add(mntmNewMenuItem);");
        // Ein zweites Popup legt die Hilfsmethode nicht noch einmal an
        model = model();
        writer(model).addComponent(find(model, "btnTwo"), entry("Menu", "JPopupMenu"), Placement.of(Association.POPUP));
        assertEquals(1, text().split("void addPopup").length - 1);
    }

    public void testAwtComponents() {
        final FormModel model = load("""
                import java.awt.*;

                public class Form extends Frame {
                    public Form() {
                        setTitle("AWT");
                        setLayout(new FlowLayout());
                        Button button = new Button("Press");
                        add(button);
                        Label label = new Label("Text", Label.RIGHT);
                        add(label);
                        Choice choice = new Choice();
                        choice.add("one");
                        choice.add("two");
                        add(choice);
                        Checkbox box = new Checkbox("Check", true);
                        add(box);
                    }
                }
                """);
        assertEquals("AWT", ((WindowSurrogate) model.getRoot().getLive()).getTitle());
        assertEquals("java.awt.Button", find(model, "button").getClassName());
        assertEquals("Press", ((javax.swing.AbstractButton) find(model, "button").getLive()).getText());
        assertEquals(javax.swing.SwingConstants.RIGHT, ((javax.swing.JLabel) find(model, "label").getLive()).getHorizontalAlignment());
        assertEquals(2, ((javax.swing.JComboBox<?>) find(model, "choice").getLive()).getItemCount());
        assertTrue(((javax.swing.JCheckBox) find(model, "box").getLive()).isSelected());
        assertEmpty(model.getWarnings());
        writer(model).addComponent(model.getRoot().getChildren().get(0), entry("AWT Components", "TextField"), Placement.append());
        assertLines("TextField textField = new TextField();", "add(textField);");
    }

    public void testCopyAndPaste() {
        FormModel model = load(Forms.frame("null"));
        writer(model).addComponent(find(model, "contentPane"), entry("Components", "JTextField"), Placement.absolute(new Rectangle(10, 10, 100, 20)));
        model = model();
        writer(model).setProperty(find(model, "textField"), "setToolTipText", "\"textField tip\"");
        model = model();
        final FormWriter.Clip clip = writer(model).copy(find(model, "textField"));
        assertNotNull(clip);
        final String name = writer(model).paste(find(model, "contentPane"), Placement.absolute(new Rectangle(10, 40, 100, 20)), clip);
        assertEquals("textField_1", name);
        assertLines("private JTextField textField;", "private JTextField textField_1;");
        assertLines("textField_1 = new JTextField();");
        // Text in Strings wird nicht umbenannt
        assertTrue(text(), text().contains("textField_1.setToolTipText(\"textField tip\");"));
        assertLines("textField_1 = new JTextField();", "textField_1.setBounds(10, 40, 100, 20);");
        assertLines("contentPane.add(textField_1);");
        assertFalse(text(), text().contains("textField_1.setBounds(10, 10"));
        assertEquals(2, model().getRoot().getChildren().get(0).getChildren().size());
    }

    public void testCopyIntoGridBag() {
        final FormModel model = load("""
                import java.awt.*;
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        setLayout(new BorderLayout());
                        JButton btnOk = new JButton("OK");
                        add(btnOk, BorderLayout.NORTH);
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
        final FormWriter.Clip clip = writer(model).copy(find(model, "btnOk"));
        writer(model).paste(find(model, "grid"), Placement.cell(0, 0, false, false), clip);
        assertLines("JButton btnOk_1 = new JButton(\"OK\");", "GridBagConstraints gbc_btnOk_1 = new GridBagConstraints();");
        assertLines("grid.add(btnOk_1, gbc_btnOk_1);");
        assertTrue(text().contains("add(btnOk, BorderLayout.NORTH);"));
    }

    public void testSetBoundsAllAndDeleteAll() {
        FormModel model = load(Forms.frame("null"));
        writer(model).addComponent(find(model, "contentPane"), entry("Components", "JButton"), Placement.absolute(new Rectangle(10, 10, 80, 20)));
        model = model();
        writer(model).addComponent(find(model, "contentPane"), entry("Components", "JButton"), Placement.absolute(new Rectangle(50, 60, 80, 20)));
        model = model();
        writer(model).setBoundsAll(Map.of(find(model, "btnNewButton"), new Rectangle(10, 10, 80, 20), find(model, "btnNewButton_1"), new Rectangle(10, 40, 80, 20)));
        assertLines("btnNewButton_1.setBounds(10, 40, 80, 20);");
        model = model();
        writer(model).deleteAll(java.util.List.of(find(model, "btnNewButton"), find(model, "btnNewButton_1")));
        assertFalse(text(), text().contains("btnNewButton"));
    }

    public void testGridBagSpan() {
        final FormModel model = load("""
                import java.awt.*;
                import javax.swing.*;

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
                        gbc_btnA.gridx = 0;
                        gbc_btnA.gridy = 0;
                        add(btnA, gbc_btnA);
                    }
                }
                """);
        writer(model).setGridBagSpan(find(model, "btnA"), 2, 1);
        assertLines("GridBagConstraints gbc_btnA = new GridBagConstraints();", "gbc_btnA.gridwidth = 2;", "gbc_btnA.gridx = 0;");
        final FormModel updated = model();
        assertEquals(2, ((java.awt.GridBagLayout) ((JPanel) updated.getRoot().getLive()).getLayout()).getConstraints(find(updated, "btnA").getLive()).gridwidth);
    }

    public void testCardLayoutNames() {
        FormModel model = load(Forms.PANEL);
        writer(model).setLayout(model.getRoot(), Palette.CARD_LAYOUT);
        assertLines("setLayout(new CardLayout(0, 0));");
        model = model();
        writer(model).addComponent(model.getRoot(), entry("Containers", "JPanel"), Placement.append());
        assertTrue(text(), text().matches("(?s).*add\\(panel, \"name_\\d+\"\\);.*"));
    }
}
