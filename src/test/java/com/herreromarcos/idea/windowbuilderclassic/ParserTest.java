package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/** Lesen und Ausführen: die Vorlagen des WindowBuilder und typischer handgeschriebener Code. */
public class ParserTest extends WindowBuilderTestCase {

    public void testFrameTemplate() {
        final FormModel model = load(Forms.frame("new BorderLayout(0, 0)"));
        final ComponentInfo root = model.getRoot();
        assertTrue(root.isThis());
        final WindowSurrogate window = assertInstanceOf(root.getLive(), WindowSurrogate.class);
        assertEquals("Hello", window.getTitle());
        assertEquals(450, window.getWidth());
        assertEquals(300, window.getHeight());
        assertNotNull(root.getProperty("setBounds"));
        assertNotNull(root.getProperty("setDefaultCloseOperation"));

        // setContentPane ersetzt die implizite Content Pane
        assertEquals(1, root.getChildren().size());
        final ComponentInfo contentPane = root.getChildren().get(0);
        assertEquals("contentPane", contentPane.getName());
        assertEquals(Association.CONTENT_PANE, contentPane.getAssociation());
        assertTrue(contentPane.isField());
        assertInstanceOf(((JPanel) contentPane.getLive()).getLayout(), BorderLayout.class);
        assertInstanceOf(((JPanel) contentPane.getLive()).getBorder(), EmptyBorder.class);
        assertSame(contentPane.getLive(), window.getContentPane());
        assertEmpty(model.getWarnings());
    }

    public void testDialogTemplateWithBlocks() {
        final FormModel model = load(Forms.DIALOG);
        final ComponentInfo pane = model.getRoot().getChildren().get(0);
        assertTrue(pane.isImplicit());
        assertEquals("getContentPane()", pane.getReference());
        assertNotNull(pane.getProperty("setLayout"));
        assertEquals(2, pane.getChildren().size());

        final ComponentInfo contentPanel = find(model, "contentPanel");
        assertEquals("BorderLayout.CENTER", contentPanel.getConstraints());
        assertEquals(BorderLayout.CENTER, contentPanel.getConstraintsValue());

        final ComponentInfo buttonPane = find(model, "buttonPane");
        assertEquals(FlowLayout.RIGHT, ((FlowLayout) ((JPanel) buttonPane.getLive()).getLayout()).getAlignment());
        assertEquals(2, buttonPane.getChildren().size());
        final ComponentInfo ok = find(model, "okButton");
        assertEquals("OK", ((JButton) ok.getLive()).getText());
        assertEquals("okButton - \"OK\"", ok.getPresentableName());
        assertSame(buttonPane, ok.getParent());
    }

    public void testApplicationWindow() {
        final FormModel model = load(Forms.APPLICATION_WINDOW);
        final ComponentInfo root = model.getRoot();
        assertEquals("frame", root.getName());
        assertInstanceOf(root.getLive(), WindowSurrogate.class);
        final ComponentInfo pane = root.getChildren().get(0);
        assertEquals("frame.getContentPane()", pane.getReference());
        assertEquals("lblHello", pane.getChildren().get(0).getName());
    }

    public void testPanelDefaultsAndOrder() {
        final FormModel model = load(Forms.PANEL);
        final ComponentInfo root = model.getRoot();
        assertEquals("javax.swing.JPanel", root.getClassName());
        assertEquals(450, root.getLive().getWidth());
        assertEquals(java.util.List.of("btnOne", "btnTwo"), root.getChildren().stream().map(ComponentInfo::getName).toList());
        assertInstanceOf(((JPanel) root.getLive()).getLayout(), FlowLayout.class);
    }

    public void testExpressionsAndConstants() {
        final FormModel model = load("""
                import java.awt.*;
                import javax.swing.*;

                public class Form extends JPanel {
                    private static final int GAP = 4;
                    public Form() {
                        setLayout(new GridLayout(0, 2, GAP * 2, GAP));
                        JLabel label = new JLabel("Name" + ":");
                        label.setForeground(Color.RED);
                        label.setHorizontalAlignment(SwingConstants.RIGHT);
                        label.setFont(new Font("Tahoma", Font.BOLD | Font.ITALIC, 14));
                        label.setBorder(BorderFactory.createTitledBorder("Title"));
                        add(label);
                    }
                }
                """);
        final GridLayout grid = (GridLayout) ((JPanel) model.getRoot().getLive()).getLayout();
        assertEquals(8, grid.getHgap());
        assertEquals(4, grid.getVgap());
        final JLabel label = (JLabel) find(model, "label").getLive();
        assertEquals("Name:", label.getText());
        assertEquals(Color.RED, label.getForeground());
        assertEquals(SwingConstants.RIGHT, label.getHorizontalAlignment());
        assertEquals(Font.BOLD | Font.ITALIC, label.getFont().getStyle());
        assertInstanceOf(label.getBorder(), TitledBorder.class);
        assertEmpty(model.getWarnings());
    }

    public void testSpecialContainers() {
        final FormModel model = load("""
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        JScrollPane scrollPane = new JScrollPane();
                        add(scrollPane);
                        JTable table = new JTable();
                        scrollPane.setViewportView(table);
                        JSplitPane splitPane = new JSplitPane();
                        add(splitPane);
                        JTree tree = new JTree();
                        splitPane.setLeftComponent(tree);
                        JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
                        add(tabbedPane);
                        JPanel panel = new JPanel();
                        tabbedPane.addTab("First", null, panel, null);
                    }
                }
                """);
        assertEquals(Association.VIEWPORT, find(model, "table").getAssociation());
        assertSame(find(model, "scrollPane"), find(model, "table").getParent());
        assertEquals(Association.LEFT, find(model, "tree").getAssociation());
        final ComponentInfo panel = find(model, "panel");
        assertEquals(Association.TAB, panel.getAssociation());
        assertEquals("\"First\"", panel.getConstraints());
        assertEquals("First", ((JTabbedPane) find(model, "tabbedPane").getLive()).getTitleAt(0));
    }

    public void testUnknownCodeIsSkipped() {
        final FormModel model = load("""
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form(String text) {
                        JButton button = new JButton(text);
                        button.setIcon(new ImageIcon(Form.class.getResource("/missing.png")));
                        button.addActionListener(e -> System.exit(0));
                        add(button);
                        if (text == null) add(new JLabel("never"));
                    }
                }
                """);
        final ComponentInfo button = find(model, "button");
        assertSame(model.getRoot(), button.getParent());
        assertEquals(1, model.getRoot().getChildren().size());
        assertFalse(model.getWarnings().isEmpty());
    }

    public void testProjectComponentIsPlaceholder() {
        myFixture.addClass("""
                package com.example;
                public class Fancy extends javax.swing.JPanel {
                }
                """);
        final FormModel model = load("""
                import javax.swing.*;
                import com.example.Fancy;

                public class Form extends JPanel {
                    public Form() {
                        Fancy fancy = new Fancy();
                        add(fancy);
                    }
                }
                """);
        final ComponentInfo fancy = find(model, "fancy");
        assertTrue(fancy.isPlaceholder());
        assertEquals("com.example.Fancy", fancy.getClassName());
        assertInstanceOf(fancy.getLive(), JPanel.class);
    }

    public void testNoGui() {
        myFixture.configureByText("Form.java", "import javax.swing.*; public class Form { public Form() { int x = 1; } }");
        final var script = com.herreromarcos.idea.windowbuilderclassic.parser.FormParser.parse(myFixture.getFile());
        assertNotNull(script);
        assertNull(com.herreromarcos.idea.windowbuilderclassic.runtime.FormBuilder.build(script));
    }
}
