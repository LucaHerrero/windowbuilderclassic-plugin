package com.herreromarcos.idea.windowbuilderclassic.palette;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Die Palette in Aufbau und Reihenfolge des WindowBuilder; erzeugt wird derselbe Code wie dort. */
public final class Palette {
    private static final String S = "javax.swing.";
    private static final String A = "java.awt.";
    private static final String BOX = "javax.swing.Box.";

    public static final PaletteEntry SELECTION = PaletteEntry.tool(PaletteEntry.Kind.SELECTION, "Selection");
    public static final PaletteEntry MARQUEE = PaletteEntry.tool(PaletteEntry.Kind.MARQUEE, "Marquee");
    public static final PaletteEntry CHOOSE_COMPONENT = PaletteEntry.tool(PaletteEntry.Kind.CHOOSE_COMPONENT, "Choose component");

    public static final PaletteEntry ABSOLUTE_LAYOUT = PaletteEntry.layout("Absolute layout", null, "null");
    public static final PaletteEntry FLOW_LAYOUT = PaletteEntry.layout("FlowLayout", A + "FlowLayout", "new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 5, 5)");
    public static final PaletteEntry BORDER_LAYOUT = PaletteEntry.layout("BorderLayout", A + "BorderLayout", "new java.awt.BorderLayout(0, 0)");
    public static final PaletteEntry GRID_LAYOUT = PaletteEntry.layout("GridLayout", A + "GridLayout", "new java.awt.GridLayout(1, 0, 0, 0)");
    /** Der Code dafür entsteht im GridBagWriter (gbl_… mit Arrays), die Vorlage dient nur als Fallback. */
    public static final PaletteEntry GRID_BAG_LAYOUT = PaletteEntry.layout("GridBagLayout", A + "GridBagLayout", "new java.awt.GridBagLayout()");
    public static final PaletteEntry CARD_LAYOUT = PaletteEntry.layout("CardLayout", A + "CardLayout", "new java.awt.CardLayout(0, 0)");
    public static final PaletteEntry BOX_LAYOUT = PaletteEntry.layout("BoxLayout", S + "BoxLayout", "new javax.swing.BoxLayout(%s, javax.swing.BoxLayout.X_AXIS)");
    /** Code entsteht wie bei Absolute Layout aus den Positionen (FreeLayoutWriter). */
    public static final PaletteEntry SPRING_LAYOUT = PaletteEntry.layout("SpringLayout", S + "SpringLayout", "new javax.swing.SpringLayout()");
    public static final PaletteEntry GROUP_LAYOUT = PaletteEntry.layout("GroupLayout", S + "GroupLayout", "new javax.swing.GroupLayout(%s)");

    /** Layouts aus Bibliotheken – in der Palette nur, wenn die Bibliothek im Modul liegt. */
    public static final PaletteEntry MIG_LAYOUT = PaletteEntry.layout("MigLayout", "net.miginfocom.swing.MigLayout",
            "new net.miginfocom.swing.MigLayout(\"\", \"[]\", \"[]\")");
    public static final PaletteEntry FORM_LAYOUT = PaletteEntry.layout("FormLayout", "com.jgoodies.forms.layout.FormLayout",
            "new com.jgoodies.forms.layout.FormLayout(new com.jgoodies.forms.layout.ColumnSpec[]{com.jgoodies.forms.layout.FormSpecs.RELATED_GAP_COLSPEC, "
                    + "com.jgoodies.forms.layout.FormSpecs.DEFAULT_COLSPEC,}, new com.jgoodies.forms.layout.RowSpec[]{com.jgoodies.forms.layout.FormSpecs.RELATED_GAP_ROWSPEC, "
                    + "com.jgoodies.forms.layout.FormSpecs.DEFAULT_ROWSPEC,})");
    public static final String THIRD_PARTY = "Third-party Layouts";

    private static final Map<String, List<PaletteEntry>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("System", List.of(SELECTION, MARQUEE, CHOOSE_COMPONENT));
        CATEGORIES.put("Containers", List.of(
                PaletteEntry.component(S + "JPanel", ""),
                PaletteEntry.component(S + "JScrollPane", ""),
                PaletteEntry.component(S + "JSplitPane", ""),
                PaletteEntry.component(S + "JTabbedPane", "javax.swing.JTabbedPane.TOP"),
                PaletteEntry.component(S + "JToolBar", ""),
                PaletteEntry.component(S + "JLayeredPane", ""),
                PaletteEntry.component(S + "JDesktopPane", ""),
                PaletteEntry.component(S + "JInternalFrame", "", "setVisible(true)")));
        CATEGORIES.put("Layouts", List.of(ABSOLUTE_LAYOUT, FLOW_LAYOUT, BORDER_LAYOUT, GRID_LAYOUT, GRID_BAG_LAYOUT, CARD_LAYOUT,
                BOX_LAYOUT, SPRING_LAYOUT, GROUP_LAYOUT));
        CATEGORIES.put("Struts & Springs", List.of(
                PaletteEntry.factory("Horizontal Box", S + "Box", S + "Box", BOX + "createHorizontalBox()", "horizontalBox"),
                PaletteEntry.factory("Vertical Box", S + "Box", S + "Box", BOX + "createVerticalBox()", "verticalBox"),
                PaletteEntry.factory("Horizontal Strut", S + "Box.Filler", A + "Component", BOX + "createHorizontalStrut(20)", "horizontalStrut"),
                PaletteEntry.factory("Vertical Strut", S + "Box.Filler", A + "Component", BOX + "createVerticalStrut(20)", "verticalStrut"),
                PaletteEntry.factory("Horizontal Glue", S + "Box.Filler", A + "Component", BOX + "createHorizontalGlue()", "horizontalGlue"),
                PaletteEntry.factory("Vertical Glue", S + "Box.Filler", A + "Component", BOX + "createVerticalGlue()", "verticalGlue"),
                PaletteEntry.factory("Rigid Area", S + "Box.Filler", A + "Component", BOX + "createRigidArea(new java.awt.Dimension(20, 20))", "rigidArea"),
                PaletteEntry.factory("Glue", S + "Box.Filler", A + "Component", BOX + "createGlue()", "glue")));
        CATEGORIES.put("Components", List.of(
                PaletteEntry.component(S + "JLabel", "\"New label\""),
                PaletteEntry.field(S + "JTextField", "", "setColumns(10)"),
                PaletteEntry.component(S + "JComboBox", ""),
                PaletteEntry.component(S + "JButton", "\"New button\""),
                PaletteEntry.component(S + "JCheckBox", "\"New check box\""),
                PaletteEntry.component(S + "JRadioButton", "\"New radio button\""),
                PaletteEntry.component(S + "JToggleButton", "\"New toggle button\""),
                PaletteEntry.component(S + "JTextArea", ""),
                PaletteEntry.field(S + "JFormattedTextField", ""),
                PaletteEntry.field(S + "JPasswordField", ""),
                PaletteEntry.component(S + "JTextPane", ""),
                PaletteEntry.component(S + "JEditorPane", ""),
                PaletteEntry.component(S + "JSpinner", ""),
                PaletteEntry.component(S + "JList", ""),
                PaletteEntry.field(S + "JTable", ""),
                PaletteEntry.component(S + "JTree", ""),
                PaletteEntry.component(S + "JProgressBar", ""),
                PaletteEntry.component(S + "JScrollBar", ""),
                PaletteEntry.component(S + "JSeparator", ""),
                PaletteEntry.component(S + "JSlider", "")));
        CATEGORIES.put("Menu", List.of(
                PaletteEntry.component(S + "JMenuBar", ""),
                PaletteEntry.component(S + "JPopupMenu", ""),
                PaletteEntry.component(S + "JMenu", "\"New menu\""),
                PaletteEntry.component(S + "JMenuItem", "\"New menu item\""),
                PaletteEntry.component(S + "JCheckBoxMenuItem", "\"New check item\""),
                PaletteEntry.component(S + "JRadioButtonMenuItem", "\"New radio item\""),
                new PaletteEntry(PaletteEntry.Kind.COMPONENT, "JSeparator (menu)", S + "JSeparator", "", false, List.of(), null, null, null, "separator")));
        CATEGORIES.put("AWT Components", List.of(
                PaletteEntry.component(A + "Panel", ""),
                PaletteEntry.component(A + "Canvas", ""),
                PaletteEntry.component(A + "ScrollPane", ""),
                PaletteEntry.component(A + "Button", "\"New button\""),
                PaletteEntry.component(A + "Label", "\"New label\""),
                PaletteEntry.component(A + "Checkbox", "\"New check box\""),
                PaletteEntry.component(A + "Choice", ""),
                PaletteEntry.component(A + "List", ""),
                PaletteEntry.component(A + "Scrollbar", ""),
                PaletteEntry.component(A + "TextField", ""),
                PaletteEntry.component(A + "TextArea", "")));
    }

    private Palette() {
    }

    public static Map<String, List<PaletteEntry>> categories() {
        return CATEGORIES;
    }

    /** Layout-Eintrag zu einer Layout-Klasse (für die Layout-Auswahl in den Properties). */
    public static @Nullable PaletteEntry layoutFor(@Nullable String layoutClass) {
        for (final PaletteEntry entry : CATEGORIES.get("Layouts")) {
            if (java.util.Objects.equals(entry.className(), layoutClass)) return entry;
        }
        return null;
    }

    public static List<PaletteEntry> layouts() {
        return CATEGORIES.get("Layouts");
    }

    /** Die Layouts aus Bibliotheken, deren Klasse {@code available} findet. */
    public static List<PaletteEntry> thirdPartyLayouts(java.util.function.Predicate<String> available) {
        final List<PaletteEntry> result = new java.util.ArrayList<>();
        for (final PaletteEntry entry : List.of(MIG_LAYOUT, FORM_LAYOUT)) {
            if (available.test(entry.className())) result.add(entry);
        }
        return result;
    }

    /** Eintrag für eine beliebige Klasse (Choose component). */
    public static PaletteEntry custom(String className) {
        return PaletteEntry.component(className, "");
    }

    /** Eintrag zur Klasse, z.B. für Kopien; {@code null}, wenn die Klasse nicht in der Palette steht. */
    public static @Nullable PaletteEntry forClass(String className) {
        for (final List<PaletteEntry> entries : CATEGORIES.values()) {
            for (final PaletteEntry entry : entries) {
                if (className.equals(entry.className()) && entry.factory() == null) return entry;
            }
        }
        return null;
    }
}
