package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.WindowBuilderTestCase;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.herreromarcos.idea.windowbuilderclassic.runtime.FormBuilder;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;

/** Der Design-Tab als Ganzes: öffnen, zeichnen, ablegen, Auswahl nach dem Neu-Parsen. */
public class EditorTest extends WindowBuilderTestCase {
    private static final String FRAME = """
            import java.awt.BorderLayout;
            import javax.swing.JFrame;
            import javax.swing.JPanel;
            import javax.swing.border.EmptyBorder;

            public class Form extends JFrame {
                private JPanel contentPane;

                public Form() {
                    setBounds(100, 100, 450, 300);
                    contentPane = new JPanel();
                    contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
                    setContentPane(contentPane);
                    contentPane.setLayout(new BorderLayout(0, 0));
                }
            }
            """;

    private DesignerPanel open(String text) {
        myFixture.configureByText("Form.java", text);
        final VirtualFile file = myFixture.getFile().getVirtualFile();
        final WindowBuilderEditorProvider provider = new WindowBuilderEditorProvider();
        assertTrue(provider.accept(getProject(), file));
        final WindowBuilderEditor editor = (WindowBuilderEditor) provider.createEditor(getProject(), file);
        Disposer.register(getTestRootDisposable(), editor);
        editor.selectNotify();
        waitForRefresh();
        return (DesignerPanel) editor.getComponent().getComponent(0);
    }

    private static void waitForRefresh() {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    }

    private static BufferedImage paint(DesignerPanel panel) {
        panel.setSize(1200, 800);
        FormBuilder.layoutTree(panel);
        final BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        try {
            panel.paint(g);
        } finally {
            g.dispose();
        }
        // Zum Nachsehen: -Dwindowbuilder.snapshots=<Ordner>
        final String dir = System.getProperty("windowbuilder.snapshots");
        if (dir != null) {
            try {
                javax.imageio.ImageIO.write(image, "png", new java.io.File(dir, "snapshot-%d.png".formatted(System.nanoTime())));
            } catch (final java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
        return image;
    }

    public void testNoDesignTabForNonSwingFiles() {
        myFixture.configureByText("Plain.java", "public class Plain {}");
        assertFalse(new WindowBuilderEditorProvider().accept(getProject(), myFixture.getFile().getVirtualFile()));
    }

    public void testOpenPaintAndDrop() {
        final DesignerPanel panel = open(FRAME);
        assertNotNull(panel.model());
        assertSame(panel.model().getRoot(), panel.selection());
        paint(panel);

        final ComponentInfo contentPane = panel.model().findByReference("contentPane");
        assertNotNull(contentPane);
        final PaletteEntry button = Palette.categories().get("Components").stream()
                .filter(e -> "javax.swing.JButton".equals(e.className())).findFirst().orElseThrow();
        final DropTargets.Drop drop = DropTargets.compute(contentPane, new Point(200, 10), new Dimension(80, 25), new Point(), null);
        assertNotNull(drop);
        assertEquals("NORTH", drop.label());
        panel.create(button, drop);
        assertTrue(text(), text().contains("contentPane.add(btnNewButton, BorderLayout.NORTH);"));

        panel.refresh();
        waitForRefresh();
        assertNotNull(panel.selection());
        assertEquals("btnNewButton", panel.selection().getName());
        paint(panel);

        // NORTH ist jetzt belegt
        assertNull(DropTargets.compute(panel.model().findByReference("contentPane"), new Point(200, 10), new Dimension(80, 25), new Point(), null));
    }

    public void testLayoutFromPaletteAndResize() {
        final DesignerPanel panel = open(FRAME);
        panel.applyLayout(panel.model().findByReference("contentPane"), Palette.ABSOLUTE_LAYOUT);
        assertTrue(text(), text().contains("contentPane.setLayout(null);"));
        panel.refresh();
        waitForRefresh();
        panel.resizeForm(new Dimension(500, 350));
        assertTrue(text(), text().contains("setBounds(100, 100, 500, 350);"));
    }

    public void testNoGuiMessage() {
        final DesignerPanel panel = open("import javax.swing.*; public class Form { public Form() { } }");
        assertNull(panel.model());
        paint(panel);
    }

    private static final String GRID = """
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

    public void testGridBagDropTargets() {
        final DesignerPanel panel = open(GRID);
        panel.select(panel.model().findByReference("btnA"));
        paint(panel);
        final ComponentInfo root = panel.model().getRoot();
        final java.awt.GridBagLayout layout = (java.awt.GridBagLayout) ((javax.swing.JPanel) root.getLive()).getLayout();
        final DropTargets.GridLines lines = DropTargets.gridLines(layout);
        assertEquals(2, lines.columns());
        assertEquals(1, lines.rows());
        final int midY = (lines.ys()[0] + lines.ys()[1]) / 2;
        final Dimension size = new Dimension(50, 20);

        // Auf eine belegte Zelle: nicht erlaubt
        assertNull(DropTargets.compute(root, new Point((lines.xs()[0] + lines.xs()[1]) / 2, midY), size, new Point(), null));
        // Auf die Linie zwischen A und B: Spalte 1 einfügen
        final DropTargets.Drop insert = DropTargets.compute(root, new Point(lines.xs()[1], midY), size, new Point(), null);
        assertNotNull(insert);
        assertEquals(new com.herreromarcos.idea.windowbuilderclassic.codegen.Placement.GridCell(1, 0, true, false), insert.placement().cell());
        // Rechts neben dem Gitter: neue Spalte am Ende
        final DropTargets.Drop append = DropTargets.compute(root, new Point(lines.xs()[2] + 10, midY), size, new Point(), null);
        assertNotNull(append);
        assertEquals(new com.herreromarcos.idea.windowbuilderclassic.codegen.Placement.GridCell(2, 0, false, false), append.placement().cell());
        // Unter dem Gitter: neue Zeile
        final DropTargets.Drop below = DropTargets.compute(root, new Point((lines.xs()[0] + lines.xs()[1]) / 2, lines.ys()[1] + 10), size, new Point(), null);
        assertNotNull(below);
        assertEquals(new com.herreromarcos.idea.windowbuilderclassic.codegen.Placement.GridCell(0, 1, false, false), below.placement().cell());

        final PaletteEntry label = Palette.categories().get("Components").stream()
                .filter(e -> "javax.swing.JLabel".equals(e.className())).findFirst().orElseThrow();
        panel.create(label, below);
        assertTrue(text(), text().contains("add(lblNewLabel, gbc_lblNewLabel);"));
        panel.refresh();
        waitForRefresh();
        assertEquals("lblNewLabel", panel.selection().getName());
        paint(panel);
    }

    private static final String MENU = """
            import javax.swing.*;

            public class Form extends JFrame {
                public Form() {
                    setBounds(100, 100, 450, 300);
                    JMenuBar menuBar = new JMenuBar();
                    setJMenuBar(menuBar);
                    JMenu mnFile = new JMenu("File");
                    menuBar.add(mnFile);
                    JMenuItem mntmOpen = new JMenuItem("Open");
                    mnFile.add(mntmOpen);
                    mnFile.addSeparator();
                    JMenuItem mntmExit = new JMenuItem("Exit");
                    mnFile.add(mntmExit);
                    JPanel panel = new JPanel();
                    getContentPane().add(panel);
                    JButton btnContext = new JButton("Context");
                    panel.add(btnContext);
                }
            }
            """;

    public void testMenuDropdown() {
        final DesignerPanel panel = open(MENU);
        final ComponentInfo item = panel.model().findByReference("mntmOpen");
        panel.select(item);
        paint(panel);
        final ComponentInfo menu = panel.model().findByReference("mnFile");
        // Die Einträge liegen im Popup des Menüs – dort wird abgelegt
        assertSame(((javax.swing.JMenu) menu.getLive()).getPopupMenu(), DropTargets.geometry(menu));
        final javax.swing.JPopupMenu popup = ((javax.swing.JMenu) menu.getLive()).getPopupMenu();
        final DropTargets.Drop drop = DropTargets.compute(menu, new Point(10, popup.getHeight() - 2), new Dimension(80, 20), new Point(), null);
        assertNotNull(drop);
        assertSame(popup, drop.geometry());
        assertNull(drop.placement().before());
        final PaletteEntry entry = Palette.categories().get("Menu").stream()
                .filter(e -> "javax.swing.JMenuItem".equals(e.className())).findFirst().orElseThrow();
        panel.create(entry, drop);
        assertTrue(text(), text().contains("mnFile.add(mntmNewMenuItem);"));
    }

    public void testMultiSelectionAndAlignment() {
        final DesignerPanel panel = open("""
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        setLayout(null);
                        JButton a = new JButton("A");
                        a.setBounds(10, 10, 80, 20);
                        add(a);
                        JButton b = new JButton("B");
                        b.setBounds(40, 50, 120, 20);
                        add(b);
                        JButton c = new JButton("C");
                        c.setBounds(25, 120, 60, 20);
                        add(c);
                    }
                }
                """);
        final ComponentInfo a = panel.model().findByReference("a"), b = panel.model().findByReference("b"), c = panel.model().findByReference("c");
        panel.setSelection(java.util.List.of(a, b, c));
        paint(panel);
        final java.util.Map<ComponentInfo, java.awt.Rectangle> left = AbsoluteAlignment.LEFT.apply(panel.selectionList(), new Dimension(450, 300));
        assertEquals(10, left.get(b).x);
        assertEquals(10, left.get(c).x);
        final java.util.Map<ComponentInfo, java.awt.Rectangle> spaced = AbsoluteAlignment.DISTRIBUTE_VERTICALLY.apply(panel.selectionList(), new Dimension(450, 300));
        assertEquals(10, spaced.get(a).y);
        assertEquals(65, spaced.get(b).y);
        assertEquals(120, spaced.get(c).y);
        final java.util.Map<ComponentInfo, java.awt.Rectangle> centered = AbsoluteAlignment.CENTER_IN_WINDOW_HORIZONTALLY.apply(java.util.List.of(b), new Dimension(450, 300));
        assertEquals(165, centered.get(b).x);
        panel.moveAll(left);
        assertTrue(text(), text().contains("b.setBounds(10, 50, 120, 20);"));
        assertTrue(text(), text().contains("c.setBounds(10, 120, 60, 20);"));
        // Die Auswahl übersteht das Neu-Parsen
        panel.refresh();
        waitForRefresh();
        assertEquals(java.util.List.of("a", "b", "c"), panel.selectionList().stream().map(ComponentInfo::getName).toList());
    }
}
