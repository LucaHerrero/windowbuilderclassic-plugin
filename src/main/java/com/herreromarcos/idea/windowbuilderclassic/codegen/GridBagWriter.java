package com.herreromarcos.idea.windowbuilderclassic.codegen;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.ObjectInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.PropertyInfo;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiStatement;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * GridBagLayout im Stil des WindowBuilder: ein {@code gbl_…} mit {@code columnWidths}, {@code rowHeights},
 * {@code columnWeights} und {@code rowWeights} (je eine Spalte/Zeile mehr, zuletzt {@code 0} bzw.
 * {@code Double.MIN_VALUE}), pro Komponente ein eigenes {@code gbc_…} und 5 Pixel Abstand über {@code insets} rechts
 * und unten – außer in der letzten Spalte bzw. Zeile. Teilen sich Komponenten ein Constraints-Objekt, bekommt die
 * geänderte Komponente vorher ihr eigenes.
 */
public final class GridBagWriter {
    public static final String GBC = "java.awt.GridBagConstraints";
    static final String GBL = "java.awt.GridBagLayout";
    private static final String[] ARRAYS = {"columnWidths", "rowHeights", "columnWeights", "rowWeights"};
    /** Reihenfolge der Felder im erzeugten Code. */
    private static final List<String> FIELD_ORDER = List.of("gridwidth", "gridheight", "weightx", "weighty", "anchor",
            "insets", "fill", "ipadx", "ipady", "gridx", "gridy");
    private static final Set<String> HORIZONTAL_FILL = Set.of("javax.swing.JTextField", "javax.swing.JPasswordField",
            "javax.swing.JFormattedTextField", "javax.swing.JComboBox", "javax.swing.JSpinner");
    public static final List<String> ANCHORS = List.of("CENTER", "NORTH", "NORTHEAST", "EAST", "SOUTHEAST", "SOUTH", "SOUTHWEST",
            "WEST", "NORTHWEST", "PAGE_START", "PAGE_END", "LINE_START", "LINE_END", "FIRST_LINE_START", "FIRST_LINE_END",
            "LAST_LINE_START", "LAST_LINE_END", "BASELINE", "BASELINE_LEADING", "BASELINE_TRAILING", "ABOVE_BASELINE",
            "ABOVE_BASELINE_LEADING", "ABOVE_BASELINE_TRAILING", "BELOW_BASELINE", "BELOW_BASELINE_LEADING", "BELOW_BASELINE_TRAILING");
    public static final List<String> FILLS = List.of("NONE", "HORIZONTAL", "VERTICAL", "BOTH");

    private final FormWriter writer;

    GridBagWriter(FormWriter writer) {
        this.writer = writer;
    }

    public record Slot(int x, int y, int w, int h) {
        Slot at(int newX, int newY) {
            return new Slot(newX, newY, w, h);
        }
    }

    public static boolean isGridBag(@Nullable ComponentInfo container) {
        return container != null && container.getLive() instanceof final Container c && c.getLayout() instanceof GridBagLayout;
    }

    // ---- Gitter

    /** Das Gitter eines Containers: Zellen der Kinder und die Spalten-/Zeilen-Arrays. Änderungen erst im Speicher, dann {@link #write}. */
    final class Grid {
        final ComponentInfo container;
        final GridBagLayout layout;
        final Map<ComponentInfo, Slot> original = new LinkedHashMap<>();
        final Map<ComponentInfo, Slot> slots = new LinkedHashMap<>();
        final List<Integer> columnWidths = new ArrayList<>();
        final List<Integer> rowHeights = new ArrayList<>();
        final List<Double> columnWeights = new ArrayList<>();
        final List<Double> rowWeights = new ArrayList<>();
        final List<Slot> added = new ArrayList<>();
        boolean arrays;
        boolean arraysChanged;
        /** Ob der Container die Abstände des WindowBuilder benutzt (oder neu ist); sonst bleiben Insets unangetastet. */
        boolean useGaps;
        int gap;
        int columns;
        int rows;

        Grid(ComponentInfo container) {
            this.container = container;
            layout = (GridBagLayout) ((Container) container.getLive()).getLayout();
            for (final ComponentInfo child : container.getChildren()) {
                if (child.getAssociation() != Association.ADD) continue;
                final Slot slot = slotOf(layout, child.getLive());
                original.put(child, slot);
                slots.put(child, slot);
            }
            final ObjectInfo layoutObject = container.getLayoutObject();
            arrays = layoutObject != null && List.of(ARRAYS).stream().anyMatch(layoutObject.getFields()::containsKey);
            final int arrayColumns = arrays ? Math.max(length(layout.columnWidths), length(layout.columnWeights)) - 1 : 0;
            final int arrayRows = arrays ? Math.max(length(layout.rowHeights), length(layout.rowWeights)) - 1 : 0;
            for (int i = 0; i < arrayColumns; i++) {
                columnWidths.add(element(layout.columnWidths, i));
                columnWeights.add(element(layout.columnWeights, i));
            }
            for (int i = 0; i < arrayRows; i++) {
                rowHeights.add(element(layout.rowHeights, i));
                rowWeights.add(element(layout.rowWeights, i));
            }
            columns = arrayColumns;
            rows = arrayRows;
            for (final Slot slot : slots.values()) grow(slot);
            gap = writer.settings().gridBagGap;
            useGaps = slots.isEmpty() && writer.settings().gridBagGaps;
            for (final ComponentInfo child : slots.keySet()) {
                final Insets insets = layout.getConstraints(child.getLive()).insets;
                if (isGapPattern(insets, gap) && (insets.bottom == gap || insets.right == gap) && gap > 0) useGaps = true;
            }
        }

        private void grow(Slot slot) {
            while (columns < slot.x() + slot.w()) addLine(true, columns);
            while (rows < slot.y() + slot.h()) addLine(false, rows);
        }

        private void addLine(boolean column, int index) {
            if (column) {
                columnWidths.add(index, 0);
                columnWeights.add(index, 0.0);
                columns++;
            } else {
                rowHeights.add(index, 0);
                rowWeights.add(index, 0.0);
                rows++;
            }
            arraysChanged = true;
        }

        /** Neue Spalte/Zeile vor {@code index}; Komponenten dahinter rücken nach, überspannende werden breiter. */
        void insert(boolean column, int index) {
            for (final Map.Entry<ComponentInfo, Slot> entry : slots.entrySet()) entry.setValue(shift(entry.getValue(), column, index, 1));
            added.replaceAll(slot -> shift(slot, column, index, 1));
            addLine(column, Math.min(index, column ? columns : rows));
        }

        /** Entfernt Spalte/Zeile {@code index}; liefert die Komponenten, die nur dort lagen (werden mit gelöscht). */
        List<ComponentInfo> delete(boolean column, int index) {
            final List<ComponentInfo> removed = new ArrayList<>();
            for (final Map.Entry<ComponentInfo, Slot> entry : List.copyOf(slots.entrySet())) {
                final Slot slot = entry.getValue();
                final int start = column ? slot.x() : slot.y(), span = column ? slot.w() : slot.h();
                if (start == index && span == 1) {
                    removed.add(entry.getKey());
                    slots.remove(entry.getKey());
                } else if (start > index) {
                    slots.put(entry.getKey(), column ? slot.at(start - 1, slot.y()) : slot.at(slot.x(), start - 1));
                } else if (index < start + span) {
                    slots.put(entry.getKey(), column ? new Slot(start, slot.y(), span - 1, slot.h()) : new Slot(slot.x(), start, slot.w(), span - 1));
                }
            }
            if (column && index < columns) {
                columnWidths.remove(index);
                columnWeights.remove(index);
                columns--;
            } else if (!column && index < rows) {
                rowHeights.remove(index);
                rowWeights.remove(index);
                rows--;
            }
            arraysChanged = true;
            return removed;
        }

        private static Slot shift(Slot slot, boolean column, int index, int delta) {
            final int start = column ? slot.x() : slot.y(), span = column ? slot.w() : slot.h();
            int newStart = start, newSpan = span;
            if (start >= index) {
                newStart = start + delta;
            } else if (start + span > index) {
                newSpan = Math.max(1, span + delta);
            }
            return column ? new Slot(newStart, slot.y(), newSpan, slot.h()) : new Slot(slot.x(), newStart, slot.w(), newSpan);
        }

        void put(ComponentInfo child, Slot slot) {
            slots.put(child, slot);
            grow(slot);
        }

        void remove(ComponentInfo child) {
            slots.remove(child);
        }

        void addNew(Slot slot) {
            added.add(slot);
            grow(slot);
        }

        void setWeight(boolean column, int index, double weight) {
            if (column && index < columns) columnWeights.set(index, weight);
            if (!column && index < rows) rowWeights.set(index, weight);
            arrays = true;
            arraysChanged = true;
        }

        boolean isOccupied(int x, int y, @Nullable ComponentInfo except) {
            for (final Map.Entry<ComponentInfo, Slot> entry : slots.entrySet()) {
                final Slot s = entry.getValue();
                if (entry.getKey() != except && x >= s.x() && x < s.x() + s.w() && y >= s.y() && y < s.y() + s.h()) return true;
            }
            return false;
        }

        Insets gaps(Slot slot) {
            return gapsFor(slot, columns, rows, useGaps, gap);
        }
    }

    Grid read(ComponentInfo container) {
        return new Grid(container);
    }

    public static Slot slotOf(GridBagLayout layout, Component component) {
        final GridBagConstraints c = layout.getConstraints(component);
        int x = c.gridx, y = c.gridy;
        if (x < 0 || y < 0) {
            // RELATIVE: die tatsächliche Zelle aus dem letzten Layout
            final Point location = layout.location(component.getX() + 1, component.getY() + 1);
            if (x < 0) x = Math.max(0, location.x);
            if (y < 0) y = Math.max(0, location.y);
        }
        return new Slot(x, y, c.gridwidth > 0 ? c.gridwidth : 1, c.gridheight > 0 ? c.gridheight : 1);
    }

    private static int length(@Nullable Object array) {
        return array == null ? 0 : java.lang.reflect.Array.getLength(array);
    }

    private static int element(int @Nullable [] array, int index) {
        return array != null && index < array.length ? array[index] : 0;
    }

    private static double element(double @Nullable [] array, int index) {
        final double value = array != null && index < array.length ? array[index] : 0.0;
        return value == Double.MIN_VALUE ? 0.0 : value;
    }

    // ---- Schreiben

    /** Überträgt das Gitter: verschobene Zellen, Abstände und die Arrays des Layouts. */
    void write(Grid grid, Set<ComponentInfo> skip) {
        for (final Map.Entry<ComponentInfo, Slot> entry : grid.slots.entrySet()) {
            final ComponentInfo child = entry.getKey();
            if (skip.contains(child)) continue;
            final Slot now = entry.getValue();
            final Slot before = grid.original.get(child);
            final Map<String, String> changes = new LinkedHashMap<>();
            if (before == null || now.x() != before.x()) changes.put("gridx", String.valueOf(now.x()));
            if (before == null || now.y() != before.y()) changes.put("gridy", String.valueOf(now.y()));
            if (before != null && now.w() != before.w()) changes.put("gridwidth", now.w() == 1 ? null : String.valueOf(now.w()));
            if (before != null && now.h() != before.h()) changes.put("gridheight", now.h() == 1 ? null : String.valueOf(now.h()));
            final Insets current = grid.layout.getConstraints(child.getLive()).insets;
            final boolean own = child.getConstraintsObject() != null && child.getConstraintsObject().getUses() == 1;
            if (grid.useGaps && own && isGapPattern(current, grid.gap)) {
                final Insets wanted = grid.gaps(now);
                if (!wanted.equals(current)) changes.put("insets", insetsText(wanted));
            }
            if (!changes.isEmpty()) applyConstraints(child, grid.layout, changes);
        }
        if (grid.arrays && grid.arraysChanged && grid.container.getLayoutObject() != null) writeArrays(grid.container.getLayoutObject(), grid);
    }

    static Insets gapsFor(Slot slot, int columns, int rows, boolean useGaps, int gap) {
        if (!useGaps) return new Insets(0, 0, 0, 0);
        return new Insets(0, 0, slot.y() + slot.h() < rows ? gap : 0, slot.x() + slot.w() < columns ? gap : 0);
    }

    /** Insets, die der Designer selbst verwaltet: nur Abstände rechts/unten von 0 oder der eingestellten Größe. */
    private static boolean isGapPattern(Insets insets, int gap) {
        return insets.top == 0 && insets.left == 0 && (insets.bottom == 0 || insets.bottom == gap) && (insets.right == 0 || insets.right == gap);
    }

    private static @Nullable String insetsText(Insets insets) {
        return insets.top == 0 && insets.left == 0 && insets.bottom == 0 && insets.right == 0 ? null
                : "new java.awt.Insets(%d, %d, %d, %d)".formatted(insets.top, insets.left, insets.bottom, insets.right);
    }

    private void writeArrays(ObjectInfo layoutObject, Grid grid) {
        setField(layoutObject, "columnWidths", intArray(grid.columnWidths));
        setField(layoutObject, "rowHeights", intArray(grid.rowHeights));
        setField(layoutObject, "columnWeights", doubleArray(grid.columnWeights));
        setField(layoutObject, "rowWeights", doubleArray(grid.rowWeights));
    }

    static String intArray(List<Integer> values) {
        final List<String> texts = new ArrayList<>();
        for (final Integer value : values) texts.add(String.valueOf(value));
        texts.add("0");
        return "new int[]{%s}".formatted(String.join(", ", texts));
    }

    static String doubleArray(List<Double> values) {
        final List<String> texts = new ArrayList<>();
        for (final Double value : values) texts.add(String.valueOf(value));
        texts.add("Double.MIN_VALUE");
        return "new double[]{%s}".formatted(String.join(", ", texts));
    }

    /** Ändert Constraint-Felder einer Komponente ({@code null} = Standardwert, Zuweisung entfällt). */
    void applyConstraints(ComponentInfo child, GridBagLayout layout, Map<String, String> changes) {
        final ObjectInfo own = child.getConstraintsObject();
        if (own != null && own.getUses() == 1) {
            for (final Map.Entry<String, String> change : changes.entrySet()) setField(own, change.getKey(), change.getValue());
        } else {
            materialize(child, layout, changes);
        }
    }

    /** Setzt bzw. entfernt die Zuweisung {@code object.field = value;}. */
    void setField(ObjectInfo object, String field, @Nullable String value) {
        final PropertyInfo property = object.getFields().get(field);
        final PsiElement statement = property == null ? null : FormWriter.element(property.statement());
        if (value == null) {
            if (statement != null && statement.isValid()) statement.delete();
            return;
        }
        if (statement instanceof final PsiExpressionStatement expression
                && expression.getExpression() instanceof final PsiAssignmentExpression assignment && assignment.getRExpression() != null) {
            writer.touch(assignment.getRExpression().replace(writer.factory().createExpressionFromText(value, assignment)));
            return;
        }
        final List<PsiStatement> candidates = new ArrayList<>();
        if (FormWriter.element(object.getCreation()) instanceof final PsiStatement creation) candidates.add(creation);
        for (final PropertyInfo existing : object.getFields().values()) {
            if (FormWriter.element(existing.statement()) instanceof final PsiStatement s && s.isValid()) candidates.add(s);
        }
        if (candidates.isEmpty() || !(candidates.get(0).getParent() instanceof final PsiCodeBlock block)) return;
        final List<String> line = List.of("%s.%s = %s;".formatted(object.getName(), field, value));
        // In der Reihenfolge des WindowBuilder: vor dem ersten Feld, das danach kommt (insets vor gridx …)
        final int order = FIELD_ORDER.indexOf(field);
        if (order >= 0) {
            PsiStatement before = null;
            for (final Map.Entry<String, PropertyInfo> existing : object.getFields().entrySet()) {
                final int existingOrder = FIELD_ORDER.indexOf(existing.getKey());
                if (existingOrder > order && FormWriter.element(existing.getValue().statement()) instanceof final PsiStatement s
                        && s.isValid() && s.getParent() == block && (before == null || s.getTextOffset() < before.getTextOffset())) {
                    before = s;
                }
            }
            if (before != null) {
                writer.insertStatements(block, before, null, line);
                return;
            }
        }
        final PsiStatement after = candidates.stream().filter(s -> s.getParent() == block).max(Comparator.comparingInt(PsiElement::getTextOffset)).orElse(null);
        writer.insertStatements(block, null, after, line);
    }

    /** Gibt der Komponente ein eigenes {@code gbc_…} mit ihren aktuellen Werten plus {@code changes}. */
    private void materialize(ComponentInfo child, GridBagLayout layout, Map<String, String> changes) {
        final PsiElement association = FormWriter.element(child.getAssociationStatement());
        final PsiMethodCallExpression call = FormWriter.callOf(association);
        if (call == null || !(association.getParent() instanceof final PsiCodeBlock block)) return;
        final String name = writer.uniqueName("gbc_" + (child.getName() == null ? "component" : child.getName()));
        final Map<String, String> values = currentValues(layout.getConstraints(child.getLive()));
        values.putAll(changes);
        final List<String> lines = new ArrayList<>();
        lines.add("%s %s = new %s();".formatted(GBC, name, GBC));
        for (final String field : FIELD_ORDER) {
            final String value = values.get(field);
            if (value != null) lines.add("%s.%s = %s;".formatted(name, field, value));
        }
        writer.insertStatements(block, (PsiStatement) association, null, lines);
        replaceConstraintsArgument(child, call, name);
    }

    void replaceConstraintsArgument(ComponentInfo child, PsiMethodCallExpression call, String constraints) {
        final PsiExpressionList list = call.getArgumentList();
        final PsiExpression[] args = list.getExpressions();
        final PsiExpression value = writer.factory().createExpressionFromText(constraints, call);
        if (args.length == 1) {
            writer.touch(list.add(value));
        } else if (args.length == 2) {
            writer.touch((args[0].getText().equals(child.getReference()) ? args[1] : args[0]).replace(value));
        }
    }

    /** Nicht-Standardwerte eines Constraints-Objekts als Quelltext (gridx/gridy immer, wie im WindowBuilder). */
    static Map<String, String> currentValues(GridBagConstraints c) {
        final Map<String, String> values = new LinkedHashMap<>();
        if (c.gridwidth != 1) values.put("gridwidth", span(c.gridwidth));
        if (c.gridheight != 1) values.put("gridheight", span(c.gridheight));
        if (c.weightx != 0) values.put("weightx", String.valueOf(c.weightx));
        if (c.weighty != 0) values.put("weighty", String.valueOf(c.weighty));
        if (c.anchor != GridBagConstraints.CENTER) values.put("anchor", constant(c.anchor, ANCHORS));
        if (c.insets != null && !c.insets.equals(new Insets(0, 0, 0, 0))) values.put("insets", insetsText(c.insets));
        if (c.fill != GridBagConstraints.NONE) values.put("fill", constant(c.fill, FILLS));
        if (c.ipadx != 0) values.put("ipadx", String.valueOf(c.ipadx));
        if (c.ipady != 0) values.put("ipady", String.valueOf(c.ipady));
        values.put("gridx", position(c.gridx));
        values.put("gridy", position(c.gridy));
        return values;
    }

    /** gridx/gridy: nur RELATIVE (-1) ist eine Konstante; 0 ist die erste Spalte. */
    private static String position(int value) {
        return value == GridBagConstraints.RELATIVE ? GBC + ".RELATIVE" : String.valueOf(value);
    }

    /** gridwidth/gridheight: REMAINDER (0) und RELATIVE (-1). */
    private static String span(int value) {
        return value == GridBagConstraints.REMAINDER ? GBC + ".REMAINDER" : value == GridBagConstraints.RELATIVE ? GBC + ".RELATIVE" : String.valueOf(value);
    }

    /** Name einer GridBagConstraints-Konstante (z.B. {@code GridBagConstraints.WEST}) zu ihrem Wert. */
    public static String constant(int value, List<String> names) {
        for (final String name : names) {
            if (constantValue(name) == value) return GBC + "." + name;
        }
        return String.valueOf(value);
    }

    static int constantValue(String name) {
        try {
            return GridBagConstraints.class.getField(name).getInt(null);
        } catch (final ReflectiveOperationException e) {
            return Integer.MIN_VALUE;
        }
    }

    // ---- Code für neue Komponenten und Layouts

    /** Zeilen für {@code gbc_name} einer neuen Komponente; liefert den Variablennamen über {@code lines}. */
    String newConstraints(String component, String className, Slot slot, Insets gaps, List<String> lines) {
        final String name = writer.uniqueName("gbc_" + component);
        lines.add("%s %s = new %s();".formatted(GBC, name, GBC));
        final String insets = insetsText(gaps);
        if (insets != null) lines.add("%s.insets = %s;".formatted(name, insets));
        if (HORIZONTAL_FILL.contains(className)) lines.add("%s.fill = %s.HORIZONTAL;".formatted(name, GBC));
        lines.add("%s.gridx = %d;".formatted(name, slot.x()));
        lines.add("%s.gridy = %d;".formatted(name, slot.y()));
        return name;
    }

    /** Deklaration des Layouts mit Arrays; liefert den Variablennamen. */
    String newLayout(ComponentInfo container, List<Integer> widths, List<Integer> heights, List<Double> columnWeights,
                     List<Double> rowWeights, List<String> lines) {
        final String base = container.getName() == null ? "gridBagLayout" : "gbl_" + container.getName();
        final String name = writer.uniqueName(base);
        lines.add("%s %s = new %s();".formatted(GBL, name, GBL));
        lines.add("%s.columnWidths = %s;".formatted(name, intArray(widths)));
        lines.add("%s.rowHeights = %s;".formatted(name, intArray(heights)));
        lines.add("%s.columnWeights = %s;".formatted(name, doubleArray(columnWeights)));
        lines.add("%s.rowWeights = %s;".formatted(name, doubleArray(rowWeights)));
        return name;
    }

    String newLayout(ComponentInfo container, int columns, int rows, List<String> lines) {
        return newLayout(container, java.util.Collections.nCopies(columns, 0), java.util.Collections.nCopies(rows, 0),
                java.util.Collections.nCopies(columns, 0.0), java.util.Collections.nCopies(rows, 0.0), lines);
    }

    /**
     * Zellen der Kinder beim Wechsel auf GridBagLayout, abgeleitet aus dem bisherigen Layout: BorderLayout als 3×3,
     * Flow/Box in einer Reihe bzw. Spalte, GridLayout zeilenweise, sonst nach Position.
     */
    static Map<ComponentInfo, Slot> convert(ComponentInfo container) {
        final Map<ComponentInfo, Slot> result = new LinkedHashMap<>();
        final List<ComponentInfo> children = new ArrayList<>();
        for (final ComponentInfo child : container.getChildren()) {
            if (child.getAssociation() == Association.ADD || child.getAssociation() == Association.GROUP) children.add(child);
        }
        final java.awt.LayoutManager old = ((Container) container.getLive()).getLayout();
        final boolean positional = old == null || old instanceof javax.swing.GroupLayout || old instanceof javax.swing.SpringLayout;
        for (int i = 0; i < children.size(); i++) {
            final ComponentInfo child = children.get(i);
            final Slot slot;
            if (old instanceof java.awt.BorderLayout) {
                final Object region = child.getConstraintsValue();
                slot = java.awt.BorderLayout.NORTH.equals(region) || "First".equals(region) ? new Slot(1, 0, 1, 1)
                        : java.awt.BorderLayout.SOUTH.equals(region) || "Last".equals(region) ? new Slot(1, 2, 1, 1)
                        : java.awt.BorderLayout.WEST.equals(region) || "Before".equals(region) ? new Slot(0, 1, 1, 1)
                        : java.awt.BorderLayout.EAST.equals(region) || "After".equals(region) ? new Slot(2, 1, 1, 1)
                        : new Slot(1, 1, 1, 1);
            } else if (old instanceof final java.awt.GridLayout grid) {
                final int columns = grid.getColumns() > 0 ? grid.getColumns() : (int) Math.ceil(children.size() / (double) Math.max(1, grid.getRows()));
                slot = new Slot(i % columns, i / columns, 1, 1);
            } else if (old instanceof final javax.swing.BoxLayout box && (box.getAxis() == javax.swing.BoxLayout.Y_AXIS || box.getAxis() == javax.swing.BoxLayout.PAGE_AXIS)) {
                slot = new Slot(0, i, 1, 1);
            } else if (positional) {
                slot = null;
            } else {
                slot = new Slot(i, 0, 1, 1);
            }
            if (slot != null) result.put(child, slot);
        }
        if (positional) byPosition(children, result);
        return compact(result);
    }

    /** Absolute Layout: Spalten und Zeilen aus den Koordinaten (Toleranz 5 Pixel). */
    private static void byPosition(List<ComponentInfo> children, Map<ComponentInfo, Slot> result) {
        final List<Integer> xs = clusters(children.stream().map(c -> c.getLive().getX()).toList());
        final List<Integer> ys = clusters(children.stream().map(c -> c.getLive().getY()).toList());
        for (final ComponentInfo child : children) {
            result.put(child, new Slot(index(xs, child.getLive().getX()), index(ys, child.getLive().getY()), 1, 1));
        }
        // Zwei Komponenten in einer Zelle: die spätere in eine neue Zeile darunter
        final Set<String> taken = new java.util.HashSet<>();
        int extraRow = ys.size();
        for (final Map.Entry<ComponentInfo, Slot> entry : result.entrySet()) {
            if (!taken.add(entry.getValue().x() + "," + entry.getValue().y())) entry.setValue(entry.getValue().at(entry.getValue().x(), extraRow++));
        }
    }

    private static List<Integer> clusters(List<Integer> values) {
        final List<Integer> result = new ArrayList<>();
        for (final int value : new TreeSet<>(values)) {
            if (result.isEmpty() || value - result.get(result.size() - 1) > 5) result.add(value);
        }
        return result;
    }

    private static int index(List<Integer> clusters, int value) {
        int index = 0;
        for (int i = 0; i < clusters.size(); i++) {
            if (value >= clusters.get(i) - 5) index = i;
        }
        return index;
    }

    /** Leere Spalten und Zeilen entfernen. */
    private static Map<ComponentInfo, Slot> compact(Map<ComponentInfo, Slot> slots) {
        final List<Integer> xs = new ArrayList<>(new TreeSet<>(slots.values().stream().map(Slot::x).toList()));
        final List<Integer> ys = new ArrayList<>(new TreeSet<>(slots.values().stream().map(Slot::y).toList()));
        final Map<ComponentInfo, Slot> result = new LinkedHashMap<>();
        for (final Map.Entry<ComponentInfo, Slot> entry : slots.entrySet()) {
            result.put(entry.getKey(), new Slot(xs.indexOf(entry.getValue().x()), ys.indexOf(entry.getValue().y()), 1, 1));
        }
        return result;
    }

}
