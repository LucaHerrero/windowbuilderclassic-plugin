package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.Placement;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;

import javax.swing.JPanel;
import java.awt.Rectangle;
import java.util.Map;

/** GroupLayout, SpringLayout und CardLayout. */
public class FreeLayoutsTest extends WindowBuilderTestCase {
    /** So erzeugt NetBeans seine Formulare. */
    private static final String NETBEANS = """
            public class Form extends javax.swing.JFrame {
                public Form() {
                    initComponents();
                }

                private void initComponents() {
                    jLabel1 = new javax.swing.JLabel();
                    jButton1 = new javax.swing.JButton();
                    setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
                    jLabel1.setText("Name");
                    jButton1.setText("OK");
                    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
                    getContentPane().setLayout(layout);
                    layout.setHorizontalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                            .addGap(20, 20, 20)
                            .addComponent(jLabel1)
                            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                            .addComponent(jButton1)
                            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    );
                    layout.setVerticalGroup(
                        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                            .addGap(30, 30, 30)
                            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                .addComponent(jLabel1)
                                .addComponent(jButton1))
                            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    );
                    pack();
                }

                private javax.swing.JButton jButton1;
                private javax.swing.JLabel jLabel1;
            }
            """;

    private static PaletteEntry entry(String title) {
        return Palette.categories().get("Components").stream().filter(e -> e.title().equals(title)).findFirst().orElseThrow();
    }

    public void testNetBeansGroupLayoutRenders() {
        final FormModel model = load(NETBEANS);
        assertEmpty(model.getWarnings());
        final ComponentInfo pane = model.getRoot().getChildren().get(0);
        assertNotNull(pane.getLayoutObject());
        assertEquals(2, pane.getLayoutObject().getCalls().size());
        final ComponentInfo label = find(model, "jLabel1");
        assertEquals(ComponentInfo.Association.GROUP, label.getAssociation());
        assertSame(pane, label.getParent());
        assertEquals(20, label.getLive().getX());
        // BASELINE-Gruppe: das kleinere Label sitzt auf der Grundlinie des Buttons, also etwas tiefer
        assertTrue(label.getLive().getY() >= 30);
        assertEquals(30, find(model, "jButton1").getLive().getY());
        assertTrue(find(model, "jButton1").getLive().getX() > label.getLive().getX() + label.getLive().getWidth());
    }

    public void testMoveInGroupLayoutRewritesGroups() {
        final FormModel model = load(NETBEANS);
        writer(model).setBoundsAll(Map.of(find(model, "jButton1"), new Rectangle(100, 60, 80, 25)));
        final String text = text();
        assertTrue(text, text.contains("addPreferredGap") == false);
        assertTrue(text, text.contains("layout.createSequentialGroup().addGap(100).addComponent(jButton1, GroupLayout.PREFERRED_SIZE, 80, GroupLayout.PREFERRED_SIZE)"));
        final FormModel updated = model();
        assertEquals(new Rectangle(100, 60, 80, 25), find(updated, "jButton1").getLive().getBounds());
        assertEquals(20, find(updated, "jLabel1").getLive().getX());
        assertEmpty(updated.getWarnings());
    }

    public void testAddAndDeleteInGroupLayout() {
        FormModel model = load(NETBEANS);
        final ComponentInfo pane = model.getRoot().getChildren().get(0);
        writer(model).addComponent(pane, entry("JCheckBox"), Placement.absolute(new Rectangle(20, 100, 120, 23)));
        String text = text();
        assertTrue(text, text.contains("JCheckBox chckbxNewCheckBox = new JCheckBox(\"New check box\");"));
        assertFalse(text, text.contains("chckbxNewCheckBox.setBounds"));
        assertFalse(text, text.contains("add(chckbxNewCheckBox"));
        model = model();
        assertEquals(new Rectangle(20, 100, 120, 23), find(model, "chckbxNewCheckBox").getLive().getBounds());
        assertEmpty(model.getWarnings());
        writer(model).delete(find(model, "jLabel1"));
        text = text();
        assertFalse(text, text.contains("jLabel1"));
        model = model();
        assertEmpty(model.getWarnings());
        assertNotNull(find(model, "jButton1"));
    }

    public void testSpringLayout() {
        FormModel model = load(Forms.frame("null"));
        writer(model).addComponent(find(model, "contentPane"), entry("JButton"), Placement.absolute(new Rectangle(10, 20, 89, 23)));
        model = model();
        writer(model).setLayout(find(model, "contentPane"), Palette.SPRING_LAYOUT);
        assertLines("SpringLayout sl_contentPane = new SpringLayout();", "contentPane.setLayout(sl_contentPane);");
        String text = text();
        assertFalse(text, text.contains("btnNewButton.setBounds"));
        assertTrue(text, text.contains("sl_contentPane.putConstraint(SpringLayout.NORTH, btnNewButton, 15, SpringLayout.NORTH, contentPane);"));
        assertTrue(text, text.contains("sl_contentPane.putConstraint(SpringLayout.WEST, btnNewButton, 5, SpringLayout.WEST, contentPane);"));
        model = model();
        assertEquals(new java.awt.Point(10, 20), find(model, "btnNewButton").getLive().getLocation());
        writer(model).setBoundsAll(Map.of(find(model, "btnNewButton"), new Rectangle(50, 60, 150, 23)));
        model = model();
        assertEquals(new Rectangle(50, 60, 150, 23), find(model, "btnNewButton").getLive().getBounds());
        assertEquals(1, text().split("SpringLayout.NORTH, btnNewButton").length - 1);
    }

    public void testFromGroupLayoutToFlowLayout() {
        final FormModel model = load(NETBEANS);
        writer(model).setLayout(model.getRoot().getChildren().get(0), Palette.FLOW_LAYOUT);
        final String text = text();
        assertFalse(text, text.contains("GroupLayout"));
        assertTrue(text, text.contains("getContentPane().add(jLabel1);"));
        assertTrue(text, text.contains("getContentPane().add(jButton1);"));
        assertEquals(2, model().getRoot().getChildren().get(0).getChildren().size());
    }

    public void testCardLayout() {
        final FormModel model = load("""
                import java.awt.CardLayout;
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        setLayout(new CardLayout(0, 0));
                        JPanel first = new JPanel();
                        add(first, "name_1");
                        JPanel second = new JPanel();
                        add(second, "name_2");
                    }
                }
                """);
        assertTrue(find(model, "first").getLive().isVisible());
        assertFalse(find(model, "second").getLive().isVisible());
        ((java.awt.CardLayout) ((JPanel) model.getRoot().getLive()).getLayout()).show((JPanel) model.getRoot().getLive(), "name_2");
        assertTrue(find(model, "second").getLive().isVisible());
    }
}
