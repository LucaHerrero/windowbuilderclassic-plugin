package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.FormWriter;
import com.herreromarcos.idea.windowbuilderclassic.codegen.Placement;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.herreromarcos.idea.windowbuilderclassic.runtime.EventCatalog;
import com.herreromarcos.idea.windowbuilderclassic.settings.WindowBuilderSettings;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;

import java.util.List;

/** Einstellungen der Codegenerierung, lokal ↔ Feld und NLS. */
public class SettingsAndNlsTest extends WindowBuilderTestCase {
    private WindowBuilderSettings.State settings() {
        return WindowBuilderSettings.getInstance(getProject()).getState();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            WindowBuilderSettings.getInstance(getProject()).loadState(new WindowBuilderSettings.State());
        } finally {
            super.tearDown();
        }
    }

    private static PaletteEntry entry(String title) {
        return Palette.categories().get("Components").stream().filter(e -> e.title().equals(title)).findFirst().orElseThrow();
    }

    public void testVariablesAsFields() {
        settings().variablesAsFields = true;
        final FormModel model = load(Forms.PANEL);
        writer(model).addComponent(model.getRoot(), entry("JLabel"), Placement.append());
        assertLines("public class Form extends JPanel {", "private JLabel lblNewLabel;");
        assertLines("lblNewLabel = new JLabel(\"New label\");", "add(lblNewLabel);");
    }

    public void testBlocks() {
        settings().useBlocks = true;
        final FormModel model = load(Forms.PANEL);
        writer(model).addComponent(model.getRoot(), entry("JLabel"), Placement.append());
        assertLines("add(btnTwo);", "{", "JLabel lblNewLabel = new JLabel(\"New label\");", "add(lblNewLabel);", "}");
    }

    public void testLambdaListeners() {
        settings().lambdaListeners = true;
        final FormModel model = load(Forms.PANEL);
        final EventCatalog.EventSet action = EventCatalog.defaultEvent(javax.swing.JButton.class);
        writer(model).addEventHandler(find(model, "btnOne"), action, action.methods().get(0));
        assertLines("btnOne.addActionListener(e -> {", "});");
        assertEquals(List.of("*"), FormWriter.handlerMethods(find(model(), "btnOne"), "addActionListener"));
    }

    public void testGapSize() {
        settings().gridBagGap = 10;
        final FormModel model = load(Forms.PANEL);
        writer(model).setLayout(model.getRoot(), Palette.GRID_BAG_LAYOUT);
        assertLines("gbc_btnOne.insets = new Insets(0, 0, 0, 10);");
    }

    public void testNoGaps() {
        settings().gridBagGaps = false;
        final FormModel model = load(Forms.PANEL);
        writer(model).setLayout(model.getRoot(), Palette.GRID_BAG_LAYOUT);
        assertFalse(text(), text().contains("insets"));
    }

    public void testConvertLocalAndField() {
        FormModel model = load(Forms.PANEL);
        assertTrue(writer(model).canConvertToField(find(model, "btnOne")));
        assertFalse(writer(model).canConvertToLocal(find(model, "btnOne")));
        writer(model).convertToField(find(model, "btnOne"));
        assertLines("public class Form extends JPanel {", "private JButton btnOne;");
        assertLines("btnOne = new JButton(\"One\");", "add(btnOne);");
        model = model();
        assertTrue(find(model, "btnOne").isField());
        assertTrue(writer(model).canConvertToLocal(find(model, "btnOne")));
        writer(model).convertToLocal(find(model, "btnOne"));
        assertLines("JButton btnOne = new JButton(\"One\");", "add(btnOne);");
        assertFalse(text(), text().contains("private JButton btnOne;"));
    }

    public void testFieldUsedElsewhereStaysField() {
        final FormModel model = load("""
                import javax.swing.*;

                public class Form extends JPanel {
                    private JTextField name;

                    public Form() {
                        name = new JTextField();
                        add(name);
                    }

                    public String getName() {
                        return name.getText();
                    }
                }
                """);
        assertFalse(writer(model).canConvertToLocal(find(model, "name")));
    }

    public void testExternalizeStrings() {
        FormModel model = load(Forms.frame("null"));
        writer(model).addComponent(find(model, "contentPane"), entry("JButton"), Placement.absolute(new java.awt.Rectangle(10, 10, 89, 23)));
        model = model();
        final List<FormWriter.Externalizable> items = writer(model).externalizableStrings();
        assertEquals(List.of("Form.this.title", "Form.btnNewButton.text"), items.stream().map(FormWriter.Externalizable::key).toList());
        writer(model).externalize(items, "messages");
        assertLines("private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(\"messages\");");
        assertTrue(text(), text().contains("setTitle(BUNDLE.getString(\"Form.this.title\"));"));
        assertTrue(text(), text().contains("new JButton(BUNDLE.getString(\"Form.btnNewButton.text\"));"));
        final PsiFile properties = myFixture.getFile().getContainingDirectory().findFile("messages.properties");
        assertNotNull(properties);
        assertEquals("New button", ((PropertiesFile) properties).findPropertyByKey("Form.btnNewButton.text").getValue());

        // Der Designer zeigt die Texte aus dem Bundle
        model = model();
        assertEquals("New button", ((javax.swing.JButton) find(model, "btnNewButton").getLive()).getText());
        assertEquals("Hello", ((com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate) model.getRoot().getLive()).getTitle());
        // Ein geänderter Text landet in der Properties-Datei, der Code bleibt
        writer(model).setProperty(model.getRoot(), "setTitle", "\"Hallo\"");
        assertEquals("Hallo", ((PropertiesFile) properties).findPropertyByKey("Form.this.title").getValue());
        assertTrue(text(), text().contains("setTitle(BUNDLE.getString(\"Form.this.title\"));"));
        assertEmpty(writer(model()).externalizableStrings());
    }
}
