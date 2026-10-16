package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.Placement;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.model.FormScript;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.parser.FormParser;
import com.herreromarcos.idea.windowbuilderclassic.runtime.FormBuilder;
import com.herreromarcos.idea.windowbuilderclassic.runtime.ProjectClasses;

import javax.swing.JPanel;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Eigene Komponenten und Layouts aus dem Projekt werden geladen und echt dargestellt. */
public class ProjectClassesTest extends WindowBuilderTestCase {
    private static final String FANCY = """
            package com.example;
            public class Fancy extends javax.swing.JPanel {
                public Fancy() {
                    add(new javax.swing.JLabel("inside"));
                }
            }
            """;

    /** Ein Mini-Layout unter dem Namen von MigLayout: "cell x y" setzt die Komponente auf ein 50×30-Raster. */
    private static final String MIG = """
            package net.miginfocom.swing;
            import java.awt.*;
            import java.util.*;
            public class MigLayout implements LayoutManager2 {
                private final Map<Component, int[]> cells = new HashMap<>();
                public MigLayout(String layout, String columns, String rows) {}
                public void addLayoutComponent(Component c, Object constraints) {
                    final String[] parts = String.valueOf(constraints).split(" ");
                    cells.put(c, new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
                }
                public void addLayoutComponent(String name, Component c) {}
                public void removeLayoutComponent(Component c) { cells.remove(c); }
                public Dimension preferredLayoutSize(Container parent) { return new Dimension(200, 100); }
                public Dimension minimumLayoutSize(Container parent) { return new Dimension(0, 0); }
                public Dimension maximumLayoutSize(Container parent) { return new Dimension(1000, 1000); }
                public float getLayoutAlignmentX(Container parent) { return 0; }
                public float getLayoutAlignmentY(Container parent) { return 0; }
                public void invalidateLayout(Container parent) {}
                public void layoutContainer(Container parent) {
                    for (final Component c : parent.getComponents()) {
                        final int[] cell = cells.getOrDefault(c, new int[]{0, 0});
                        c.setBounds(cell[0] * 50, cell[1] * 30, 45, 25);
                    }
                }
            }
            """;

    private Path output;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        output = Files.createTempDirectory("windowbuilder-project");
        final Path sources = Files.createTempDirectory("windowbuilder-sources");
        final Path fancy = sources.resolve("Fancy.java"), mig = sources.resolve("MigLayout.java");
        Files.writeString(fancy, FANCY);
        Files.writeString(mig, MIG);
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler != null) {
            assertEquals(0, compiler.run(null, null, null, "-d", output.toString(), fancy.toString(), mig.toString()));
        } else {
            // JBR ohne jdk.compiler: javac der Gradle-Toolchain als eigener Prozess
            final String javac = System.getProperty("windowbuilder.javac");
            assertNotNull("javac required", javac);
            final Process process = new ProcessBuilder(javac, "-d", output.toString(), fancy.toString(), mig.toString()).inheritIO().start();
            assertEquals(0, process.waitFor());
        }
        myFixture.addClass(FANCY);
        myFixture.addClass(MIG);
    }

    private FormModel build(String text, ProjectClasses classes) {
        myFixture.configureByText("Form.java", text);
        final FormScript script = FormParser.parse(myFixture.getFile());
        assertNotNull(script);
        final FormModel model = FormBuilder.build(script, classes);
        assertNotNull(model);
        return model;
    }

    private ProjectClasses classes() {
        return new ProjectClasses(List.of(output.toString()), 0);
    }

    public void testCustomComponentIsRendered() {
        final String form = """
                import javax.swing.*;
                import com.example.Fancy;

                public class Form extends JPanel {
                    public Form() {
                        Fancy fancy = new Fancy();
                        add(fancy);
                    }
                }
                """;
        final ProjectClasses classes = classes();
        try {
            final ComponentInfo fancy = find(build(form, classes), "fancy");
            assertFalse(fancy.isPlaceholder());
            assertEquals("com.example.Fancy", fancy.getLive().getClass().getName());
            assertEquals(1, ((JPanel) fancy.getLive()).getComponentCount());
        } finally {
            classes.close();
        }
        // Ohne Projektklassen bleibt es ein Platzhalter
        assertTrue(find(load(form), "fancy").isPlaceholder());
    }

    public void testProjectSuperclassBringsItsComponents() {
        final ProjectClasses classes = classes();
        try {
            final FormModel model = build("""
                    import com.example.Fancy;

                    public class Form extends Fancy {
                        public Form() {
                            add(new javax.swing.JButton("own"));
                        }
                    }
                    """, classes);
            assertEquals("com.example.Fancy", model.getRoot().getLive().getClass().getName());
            // JLabel aus dem Konstruktor der Oberklasse plus der eigene Button
            assertEquals(2, ((JPanel) model.getRoot().getLive()).getComponentCount());
        } finally {
            classes.close();
        }
    }

    public void testLibraryLayoutAndCellDrop() {
        final ProjectClasses classes = classes();
        try {
            final FormModel model = build("""
                    import javax.swing.*;
                    import net.miginfocom.swing.MigLayout;

                    public class Form extends JPanel {
                        public Form() {
                            setLayout(new MigLayout("", "[]", "[]"));
                            JButton a = new JButton("A");
                            add(a, "cell 0 0");
                            JButton b = new JButton("B");
                            add(b, "cell 1 1");
                        }
                    }
                    """, classes);
            assertEmpty(model.getWarnings());
            assertEquals(new java.awt.Rectangle(50, 30, 45, 25), find(model, "b").getLive().getBounds());
            assertEquals("\"cell 1 1\"", find(model, "b").getConstraints());
            writer(model).addComponent(model.getRoot(), Palette.categories().get("Components").get(0), Placement.constraints("\"cell 2 0\""));
            assertLines("add(lblNewLabel, \"cell 2 0\");");
        } finally {
            classes.close();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (output != null) {
                try (var files = Files.walk(output)) {
                    files.sorted(java.util.Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } finally {
            super.tearDown();
        }
    }
}
