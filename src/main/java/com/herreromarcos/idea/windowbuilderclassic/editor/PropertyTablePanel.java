package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.codegen.FormWriter;
import com.herreromarcos.idea.windowbuilderclassic.codegen.GridBagWriter;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.model.PropertyInfo;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.herreromarcos.idea.windowbuilderclassic.runtime.EventCatalog;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Die Properties-Ansicht: Variable, Layout, Constraints und die Bean-Eigenschaften der ausgewählten Komponente. */
final class PropertyTablePanel extends JPanel {
    interface Host {
        @Nullable FormModel model();

        @Nullable FormWriter writer();

        /** Handler anlegen bzw. öffnen. */
        void openEvent(ComponentInfo component, EventCatalog.EventSet set, EventCatalog.EventMethod method);

        void removeEvent(ComponentInfo component, EventCatalog.EventSet set, EventCatalog.EventMethod method);
    }

    /** Was ohne „Show advanced properties“ zu sehen ist – angelehnt an die Auswahl des WindowBuilder. */
    private static final Set<String> STANDARD = Set.of("text", "title", "toolTipText", "enabled", "editable", "selected",
            "font", "foreground", "background", "border", "horizontalAlignment", "verticalAlignment", "icon", "columns", "rows",
            "orientation", "value", "minimum", "maximum", "lineWrap", "wrapStyleWord", "resizable", "defaultCloseOperation",
            "bounds", "preferredSize", "visible", "opaque", "alignmentX", "alignmentY");
    private static final Set<Class<?>> EDITABLE = Set.of(String.class, boolean.class, int.class, double.class, float.class,
            long.class, char.class, Color.class, Font.class, Dimension.class, Insets.class, Rectangle.class, Point.class,
            Border.class, Icon.class);
    private static final List<String> GRID_BAG_FIELDS = List.of("gridx", "gridy", "gridwidth", "gridheight", "weightx",
            "weighty", "anchor", "fill", "insets", "ipadx", "ipady");
    private static final Pattern NUMBERS = Pattern.compile("\\s*-?\\d+(\\s*,\\s*-?\\d+)*\\s*");

    private final Host host;
    private final RowModel rows = new RowModel();
    private final JBTable table;
    private boolean advanced;
    private boolean events;
    private @Nullable ComponentInfo component;

    PropertyTablePanel(Host host) {
        super(new BorderLayout());
        this.host = host;
        table = new JBTable(rows) {
            @Override
            public TableCellEditor getCellEditor(int row, int column) {
                return rows.get(row).editor(this);
            }
        };
        table.setShowGrid(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new ColoredTableCellRenderer() {
            @Override
            protected void customizeCellRenderer(@NotNull JTable t, Object value, boolean selected, boolean hasFocus, int row, int column) {
                final Row r = (Row) value;
                if (column == 0) {
                    append(r.name, r.modified() ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
                } else {
                    append(r.display(), r.editable() ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
                }
            }
        });
        table.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE && !table.isEditing()) restoreSelected();
            }
        });
        final DefaultActionGroup popup = new DefaultActionGroup(new AnAction("Restore Default Value", null, AllIcons.Actions.Rollback) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                restoreSelected();
            }
        });
        PopupHandler.installPopupMenu(table, popup, "WindowBuilderProperties");

        final DefaultActionGroup toolbarGroup = new DefaultActionGroup(new ToggleAction("Show Advanced Properties", null, AllIcons.Actions.Show) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return advanced;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                advanced = state;
                show(component);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
        toolbarGroup.add(new ToggleAction("Show Events", null, AllIcons.Actions.Lightning) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return events;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                events = state;
                show(component);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                final int row = table.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0 && rows.get(row) instanceof final EventRow event) event.open();
            }
        });
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("WindowBuilderProperties", toolbarGroup, true);
        toolbar.setTargetComponent(table);
        add(toolbar.getComponent(), BorderLayout.NORTH);
        add(ScrollPaneFactory.createScrollPane(table, true), BorderLayout.CENTER);
    }

    void show(@Nullable ComponentInfo info) {
        if (table.isEditing()) table.getCellEditor().cancelCellEditing();
        component = info;
        final List<Row> list = new ArrayList<>();
        if (info != null && host.model() != null) collectRows(info, host.model(), list);
        rows.set(list);
    }

    private void restoreSelected() {
        final int row = table.getSelectedRow();
        if (row >= 0 && rows.get(row).modified()) rows.get(row).restore();
    }

    // ---- Zeilen

    private void collectRows(ComponentInfo info, FormModel model, List<Row> list) {
        if (events) {
            collectEvents(info, list);
            return;
        }
        if (info.getName() != null) list.add(new VariableRow(info));
        list.add(new FixedRow("Class", info.getLive() instanceof final WindowSurrogate w ? w.getWindowClass() : info.getClassName()));
        final ComponentInfo layoutTarget = DropTargets.isDropContainer(info) ? DropTargets.dropContainer(model, info) : null;
        if (layoutTarget != null && layoutTarget.getLive() instanceof Container && !(layoutTarget.getLive() instanceof javax.swing.JScrollPane)
                && !(layoutTarget.getLive() instanceof javax.swing.JSplitPane) && !(layoutTarget.getLive() instanceof javax.swing.JTabbedPane)) {
            list.add(new LayoutRow(layoutTarget));
        }
        final ComponentInfo parent = info.getParent();
        final boolean absoluteChild = DropTargets.isPositionalChild(info);
        if (parent != null && info.getAssociation() == Association.ADD && parent.getLive() instanceof final Container c
                && c.getLayout() instanceof java.awt.BorderLayout) {
            list.add(new ConstraintsRow(info));
        }
        if (parent != null && info.getAssociation() == Association.ADD && info.getConstraintsValue() instanceof String
                && parent.getLive() instanceof final Container c && !(c.getLayout() instanceof java.awt.BorderLayout)) {
            // MigLayout ("cell 0 1"), FormLayout ("2, 4"), CardLayout (Name der Karte)
            list.add(new TextConstraintsRow(info));
        }
        if (parent != null && info.getAssociation() == Association.ADD && GridBagWriter.isGridBag(parent)) {
            for (final String field : GRID_BAG_FIELDS) list.add(new GridBagRow(info, field));
        }
        final boolean window = info.getLive() instanceof WindowSurrogate || info.getLive() instanceof javax.swing.JInternalFrame;
        if (absoluteChild || parent == null && window) list.add(new BoundsRow(info));

        final Class<?> stop = info.getLive() instanceof WindowSurrogate ? javax.swing.JPanel.class : Object.class;
        final BeanInfo beanInfo;
        try {
            beanInfo = Introspector.getBeanInfo(info.getLive().getClass(), stop);
        } catch (final IntrospectionException e) {
            return;
        }
        final List<PropertyDescriptor> descriptors = new ArrayList<>(List.of(beanInfo.getPropertyDescriptors()));
        descriptors.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (final PropertyDescriptor descriptor : descriptors) {
            if (descriptor.getWriteMethod() == null || descriptor.getReadMethod() == null || descriptor.isHidden()) continue;
            if (!EDITABLE.contains(descriptor.getPropertyType()) || "bounds".equals(descriptor.getName()) || "layout".equals(descriptor.getName())) continue;
            final boolean standard = STANDARD.contains(descriptor.getName());
            final boolean set = info.getProperty(descriptor.getWriteMethod().getName()) != null;
            if (!advanced && !standard && !set) continue;
            list.add(new BeanRow(info, descriptor));
        }
    }

    /** Events-Ansicht: je Listener-Typ eine Überschrift, darunter die Methoden; Doppelklick legt den Handler an. */
    private void collectEvents(ComponentInfo info, List<Row> list) {
        final Class<?> cls = info.getLive() instanceof final WindowSurrogate window
                ? com.herreromarcos.idea.windowbuilderclassic.parser.TypeResolver.jdkClass(window.getWindowClass()) : info.getLive().getClass();
        if (cls == null) return;
        for (final EventCatalog.EventSet set : EventCatalog.of(cls)) {
            final List<String> implemented = FormWriter.handlerMethods(info, set.addMethod());
            list.add(new FixedRow(set.name(), implemented.isEmpty() ? "" : implemented.contains("*") ? "(lambda)" : String.join(", ", implemented)));
            for (final EventCatalog.EventMethod method : set.methods()) {
                list.add(new EventRow(info, set, method, implemented.contains(method.name())));
            }
        }
    }

    private final class EventRow extends Row {
        private final ComponentInfo info;
        private final EventCatalog.EventSet set;
        private final EventCatalog.EventMethod method;
        private final boolean present;

        EventRow(ComponentInfo info, EventCatalog.EventSet set, EventCatalog.EventMethod method, boolean present) {
            super("    " + method.name());
            this.info = info;
            this.set = set;
            this.method = method;
            this.present = present;
        }

        @Override
        String display() {
            return present ? "handler – double-click to open" : "";
        }

        @Override
        boolean modified() {
            return present;
        }

        @Override
        boolean editable() {
            return false;
        }

        void open() {
            host.openEvent(info, set, method);
        }

        @Override
        void set(String value) {
        }

        @Override
        void restore() {
            host.removeEvent(info, set, method);
        }
    }

    private abstract static class Row {
        final String name;

        Row(String name) {
            this.name = name;
        }

        abstract String display();

        /** Text im Editor; bei Auswahllisten der Eintrag. */
        String editText() {
            return display();
        }

        boolean modified() {
            return false;
        }

        boolean editable() {
            return true;
        }

        @Nullable List<String> choices() {
            return null;
        }

        boolean colorChooser() {
            return false;
        }

        abstract void set(String value);

        void restore() {
        }

        TableCellEditor editor(JTable table) {
            final List<String> choices = choices();
            if (choices != null) {
                final ComboBox<String> combo = new ComboBox<>(choices.toArray(String[]::new));
                return new DefaultCellEditor(combo);
            }
            if (colorChooser()) return new ColorCellEditor(table);
            return new DefaultCellEditor(new JBTextField());
        }

        @Override
        public String toString() {
            return editText();
        }
    }

    private final class FixedRow extends Row {
        private final String value;

        FixedRow(String name, String value) {
            super(name);
            this.value = value;
        }

        @Override
        String display() {
            return value;
        }

        @Override
        boolean editable() {
            return false;
        }

        @Override
        void set(String value) {
        }
    }

    private final class VariableRow extends Row {
        private final ComponentInfo info;

        VariableRow(ComponentInfo info) {
            super("Variable");
            this.info = info;
        }

        @Override
        String display() {
            return String.valueOf(info.getName());
        }

        @Override
        void set(String value) {
            final FormWriter writer = host.writer();
            final String name = value.trim();
            if (writer != null && writer.isValidNewName(info, name)) writer.rename(info, name);
        }
    }

    private final class LayoutRow extends Row {
        private final ComponentInfo container;

        LayoutRow(ComponentInfo container) {
            super("Layout");
            this.container = container;
        }

        @Override
        String display() {
            final java.awt.LayoutManager layout = ((Container) container.getLive()).getLayout();
            if (layout == null) return Palette.ABSOLUTE_LAYOUT.title();
            final PaletteEntry entry = Palette.layoutFor(layout.getClass().getName());
            return entry != null ? entry.title() : layout.getClass().getSimpleName();
        }

        @Override
        boolean modified() {
            return container.getProperty("setLayout") != null;
        }

        @Override
        List<String> choices() {
            final List<String> titles = new ArrayList<>();
            for (final PaletteEntry entry : Palette.layouts()) titles.add(entry.title());
            if (!titles.contains(display())) titles.add(0, display());
            return titles;
        }

        @Override
        void set(String value) {
            final FormWriter writer = host.writer();
            for (final PaletteEntry entry : Palette.layouts()) {
                if (entry.title().equals(value) && writer != null) writer.setLayout(container, entry);
            }
        }

        @Override
        void restore() {
            final FormWriter writer = host.writer();
            if (writer != null) writer.setProperty(container, "setLayout", null);
        }
    }

    private final class ConstraintsRow extends Row {
        private final ComponentInfo info;

        ConstraintsRow(ComponentInfo info) {
            super("Constraints");
            this.info = info;
        }

        @Override
        String display() {
            return DropTargets.borderRegion(info);
        }

        @Override
        List<String> choices() {
            return DropTargets.borderRegions();
        }

        @Override
        void set(String value) {
            final FormWriter writer = host.writer();
            if (writer != null && !value.equals(display())) writer.setConstraints(info, "java.awt.BorderLayout." + value);
        }
    }

    private final class TextConstraintsRow extends Row {
        private final ComponentInfo info;

        TextConstraintsRow(ComponentInfo info) {
            super("Constraints");
            this.info = info;
        }

        @Override
        String display() {
            return String.valueOf(info.getConstraintsValue());
        }

        @Override
        boolean modified() {
            return true;
        }

        @Override
        void set(String value) {
            final FormWriter writer = host.writer();
            if (writer != null && !value.equals(display())) writer.setConstraints(info, "\"%s\"".formatted(StringUtil.escapeStringCharacters(value)));
        }
    }

    private final class BoundsRow extends Row {
        private final ComponentInfo info;

        BoundsRow(ComponentInfo info) {
            super("Bounds");
            this.info = info;
        }

        @Override
        String display() {
            final PropertyInfo property = info.getProperty("setBounds");
            if (property != null) return property.argsText();
            final Rectangle r = info.getLive().getBounds();
            return "%d, %d, %d, %d".formatted(r.x, r.y, r.width, r.height);
        }

        @Override
        boolean modified() {
            return info.getProperty("setBounds") != null;
        }

        @Override
        void set(String value) {
            final FormWriter writer = host.writer();
            if (writer != null && !value.isBlank() && !value.equals(display())) writer.setProperty(info, "setBounds", value.trim());
        }

        @Override
        void restore() {
            final FormWriter writer = host.writer();
            if (writer != null) writer.setProperty(info, "setBounds", null);
        }
    }

    private final class BeanRow extends Row {
        private final ComponentInfo info;
        private final PropertyDescriptor descriptor;
        private final String setter;

        BeanRow(ComponentInfo info, PropertyDescriptor descriptor) {
            super(descriptor.getName());
            this.info = info;
            this.descriptor = descriptor;
            this.setter = descriptor.getWriteMethod().getName();
        }

        private @Nullable PropertyInfo property() {
            return info.getProperty(setter);
        }

        private Class<?> type() {
            return descriptor.getPropertyType();
        }

        @Override
        String display() {
            final PropertyInfo property = property();
            if (property != null && property.argTexts().size() == 1) {
                final String text = property.argTexts().get(0);
                if (type() == String.class && text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                    return StringUtil.unescapeStringCharacters(text.substring(1, text.length() - 1));
                }
                if (type() == String.class && property.values().size() == 1 && property.values().get(0) instanceof final String value) {
                    return value;
                }
                final Map<String, String> constants = constants();
                if (constants != null) {
                    for (final String constant : constants.keySet()) {
                        if (text.equals(constant) || text.endsWith("." + constant)) return constant;
                    }
                }
                return text;
            }
            return format(liveValue());
        }

        @Override
        boolean modified() {
            return property() != null;
        }

        private @Nullable Object liveValue() {
            try {
                return descriptor.getReadMethod().invoke(info.getLive());
            } catch (final ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        private String format(@Nullable Object value) {
            if (value == null) return "";
            final Map<String, String> constants = constants();
            if (constants != null && value instanceof final Integer number) {
                for (final Map.Entry<String, String> constant : constants.entrySet()) {
                    if (constantValue(constant.getValue()) == number) return constant.getKey();
                }
            }
            return switch (value) {
                case final Color c -> "%d, %d, %d".formatted(c.getRed(), c.getGreen(), c.getBlue());
                case final Font f -> "%s %s %d".formatted(f.getFamily(), f.isBold() ? f.isItalic() ? "Bold Italic" : "Bold" : f.isItalic() ? "Italic" : "Plain", f.getSize());
                case final Dimension d -> "%d, %d".formatted(d.width, d.height);
                case final Point p -> "%d, %d".formatted(p.x, p.y);
                case final Insets i -> "%d, %d, %d, %d".formatted(i.top, i.left, i.bottom, i.right);
                case final Border b -> b.getClass().getSimpleName();
                case final Icon i -> "(icon)";
                default -> String.valueOf(value);
            };
        }

        /** Konstanten für int-Eigenschaften: Anzeigename → Quelltext. */
        private @Nullable Map<String, String> constants() {
            final Map<String, String> result = new LinkedHashMap<>();
            final String swing = "javax.swing.SwingConstants.";
            switch (name) {
                case "horizontalAlignment", "horizontalTextPosition" -> {
                    for (final String c : List.of("LEFT", "CENTER", "RIGHT", "LEADING", "TRAILING")) result.put(c, swing + c);
                }
                case "verticalAlignment", "verticalTextPosition" -> {
                    for (final String c : List.of("TOP", "CENTER", "BOTTOM")) result.put(c, swing + c);
                }
                case "orientation" -> {
                    for (final String c : List.of("HORIZONTAL", "VERTICAL")) result.put(c, swing + c);
                }
                case "defaultCloseOperation" -> {
                    final String owner = info.getLive() instanceof final WindowSurrogate w ? w.getWindowClass() : "javax.swing.WindowConstants";
                    for (final String c : List.of("DO_NOTHING_ON_CLOSE", "HIDE_ON_CLOSE", "DISPOSE_ON_CLOSE", "EXIT_ON_CLOSE")) result.put(c, owner + "." + c);
                }
                default -> {
                    return null;
                }
            }
            return result;
        }

        private static int constantValue(String qualified) {
            final String field = qualified.substring(qualified.lastIndexOf('.') + 1);
            for (final Class<?> cls : List.of(SwingConstants.class, WindowConstants.class)) {
                try {
                    final Field f = cls.getField(field);
                    return f.getInt(null);
                } catch (final ReflectiveOperationException ignored) {
                    // nächste Klasse
                }
            }
            return Integer.MIN_VALUE;
        }

        @Override
        String editText() {
            final PropertyInfo property = property();
            if (type() == String.class || type() == boolean.class || constants() != null) return display();
            return property != null ? property.argsText() : display();
        }

        @Override
        @Nullable List<String> choices() {
            if (type() == boolean.class) return List.of("true", "false");
            final Map<String, String> constants = constants();
            return constants == null ? null : List.copyOf(constants.keySet());
        }

        @Override
        boolean colorChooser() {
            return type() == Color.class;
        }

        @Override
        void set(String value) {
            final FormWriter writer = host.writer();
            if (writer == null || value.equals(editText())) return;
            final String code = toCode(value);
            if (code == null) {
                restore();
            } else {
                writer.setProperty(info, setter, code);
            }
        }

        private @Nullable String toCode(String value) {
            final Class<?> type = type();
            if (type == String.class) return "\"%s\"".formatted(StringUtil.escapeStringCharacters(value));
            if (value.isBlank()) return null;
            final Map<String, String> constants = constants();
            if (constants != null && constants.containsKey(value)) return constants.get(value);
            if (type == char.class && value.length() == 1) return "'%s'".formatted(StringUtil.escapeCharCharacters(value));
            if (NUMBERS.matcher(value).matches()) {
                final String args = value.trim().replaceAll("\\s*,\\s*", ", ");
                if (type == Color.class) return "new java.awt.Color(%s)".formatted(args);
                if (type == Dimension.class) return "new java.awt.Dimension(%s)".formatted(args);
                if (type == Point.class) return "new java.awt.Point(%s)".formatted(args);
                if (type == Insets.class) return "new java.awt.Insets(%s)".formatted(args);
                if (type == Rectangle.class) return "new java.awt.Rectangle(%s)".formatted(args);
                if (type == float.class && !args.contains(",")) return args + "f";
            }
            return value.trim();
        }

        @Override
        void restore() {
            final FormWriter writer = host.writer();
            if (writer != null) writer.setProperty(info, setter, null);
        }
    }

    /** Ein Feld der GridBagConstraints der Komponente (Gruppe „Constraints“ des WindowBuilder). */
    private final class GridBagRow extends Row {
        private final ComponentInfo info;
        private final String field;

        GridBagRow(ComponentInfo info, String field) {
            super("constraints." + field);
            this.info = info;
            this.field = field;
        }

        private GridBagConstraints constraints() {
            return ((GridBagLayout) ((Container) info.getParent().getLive()).getLayout()).getConstraints(info.getLive());
        }

        @Override
        String display() {
            final GridBagConstraints c = constraints();
            return switch (field) {
                case "gridx" -> positionValue(c.gridx);
                case "gridy" -> positionValue(c.gridy);
                case "gridwidth" -> spanValue(c.gridwidth);
                case "gridheight" -> spanValue(c.gridheight);
                case "weightx" -> String.valueOf(c.weightx);
                case "weighty" -> String.valueOf(c.weighty);
                case "anchor" -> shortName(GridBagWriter.constant(c.anchor, GridBagWriter.ANCHORS));
                case "fill" -> shortName(GridBagWriter.constant(c.fill, GridBagWriter.FILLS));
                case "insets" -> "%d, %d, %d, %d".formatted(c.insets.top, c.insets.left, c.insets.bottom, c.insets.right);
                case "ipadx" -> String.valueOf(c.ipadx);
                default -> String.valueOf(c.ipady);
            };
        }

        /** REMAINDER ist 0 – bei gridx/gridy die erste Spalte, nur bei gridwidth/gridheight eine Konstante. */
        private static String positionValue(int value) {
            return value == GridBagConstraints.RELATIVE ? "RELATIVE" : String.valueOf(value);
        }

        private static String spanValue(int value) {
            return value == GridBagConstraints.RELATIVE ? "RELATIVE" : value == GridBagConstraints.REMAINDER ? "REMAINDER" : String.valueOf(value);
        }

        private static String shortName(String constant) {
            return constant.substring(constant.lastIndexOf('.') + 1);
        }

        @Override
        boolean modified() {
            final var object = info.getConstraintsObject();
            return object != null && object.getFields().containsKey(field);
        }

        @Override
        @Nullable List<String> choices() {
            return switch (field) {
                case "anchor" -> GridBagWriter.ANCHORS;
                case "fill" -> GridBagWriter.FILLS;
                default -> null;
            };
        }

        @Override
        void set(String value) {
            final FormWriter writer = host.writer();
            if (writer == null || value.equals(display())) return;
            final String code;
            if ("anchor".equals(field) || "fill".equals(field)) {
                code = GridBagWriter.GBC + "." + value;
            } else if ("insets".equals(field)) {
                code = NUMBERS.matcher(value).matches() ? "new java.awt.Insets(%s)".formatted(value.trim().replaceAll("\\s*,\\s*", ", ")) : value.trim();
            } else if (List.of("RELATIVE", "REMAINDER").contains(value.trim())) {
                code = GridBagWriter.GBC + "." + value.trim();
            } else {
                code = value.trim();
            }
            writer.setGridBagConstraint(info, field, code.isEmpty() ? null : code);
        }

        @Override
        void restore() {
            final FormWriter writer = host.writer();
            if (writer != null && !"gridx".equals(field) && !"gridy".equals(field)) writer.setGridBagConstraint(info, field, null);
        }
    }

    /** Textfeld mit „…“ für den Farbdialog. */
    private static final class ColorCellEditor extends AbstractCellEditor implements TableCellEditor {
        private final JPanel panel = new JPanel(new BorderLayout());
        private final JBTextField field = new JBTextField();

        ColorCellEditor(JTable table) {
            final JButton button = new JButton("…");
            button.setMargin(new Insets(0, 4, 0, 4));
            button.addActionListener(e -> {
                final Color color = JColorChooser.showDialog(table, "Choose Color", null);
                if (color != null) {
                    field.setText("new java.awt.Color(%d, %d, %d)".formatted(color.getRed(), color.getGreen(), color.getBlue()));
                    stopCellEditing();
                }
            });
            panel.add(field, BorderLayout.CENTER);
            panel.add(button, BorderLayout.EAST);
            field.addActionListener(e -> stopCellEditing());
        }

        @Override
        public Object getCellEditorValue() {
            return field.getText();
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            field.setText(String.valueOf(value));
            return panel;
        }
    }

    private static final class RowModel extends AbstractTableModel {
        private List<Row> rows = List.of();

        void set(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        Row get(int index) {
            return rows.get(index);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Property" : "Value";
        }

        @Override
        public Object getValueAt(int row, int column) {
            return rows.get(row);
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1 && rows.get(row).editable();
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (row < rows.size()) rows.get(row).set(String.valueOf(value));
        }
    }
}
