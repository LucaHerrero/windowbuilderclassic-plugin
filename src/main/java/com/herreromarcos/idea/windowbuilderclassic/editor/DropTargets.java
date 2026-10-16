package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.codegen.GridBagWriter;
import com.herreromarcos.idea.windowbuilderclassic.codegen.Placement;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;
import org.jetbrains.annotations.Nullable;

import javax.swing.BoxLayout;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Layout-Unterstützung beim Ablegen (entspricht den LayoutInfos des WindowBuilder): wo landet eine Komponente, wenn sie
 * an einem Punkt losgelassen wird, und welches Feedback zeigt der Designer dafür.
 */
public final class DropTargets {
    private static final String[] REGIONS = {"NORTH", "SOUTH", "WEST", "EAST", "CENTER"};
    static final String MIG_LAYOUT = "net.miginfocom.swing.MigLayout";
    static final String FORM_LAYOUT = "com.jgoodies.forms.layout.FormLayout";

    private DropTargets() {
    }

    /**
     * @param container Ziel-Container (schon auf die Content Pane abgebildet)
     * @param feedback  Rechtecke/Linien in Container-Koordinaten
     * @param label     Hinweis wie im WindowBuilder ({@code NORTH}, Koordinaten …)
     */
    public record Drop(ComponentInfo container, Placement placement, List<Rectangle> feedback, @Nullable String label, Component geometry) {
        public Drop(ComponentInfo container, Placement placement, List<Rectangle> feedback, @Nullable String label) {
            this(container, placement, feedback, label, container.getLive());
        }
    }

    /**
     * Das Koordinatensystem, in dem die Kinder liegen: bei einem {@code JMenu} sein Popup (dort hängen die Einträge),
     * sonst die Komponente selbst.
     */
    public static Component geometry(ComponentInfo container) {
        return container.getLive() instanceof final javax.swing.JMenu menu ? menu.getPopupMenu() : container.getLive();
    }

    /** Kann {@code info} Kinder aufnehmen? */
    public static boolean isDropContainer(ComponentInfo info) {
        final Component live = info.getLive();
        return live instanceof JPanel || live instanceof JScrollPane || live instanceof JSplitPane || live instanceof JTabbedPane
                || live instanceof JToolBar || live instanceof JLayeredPane || live instanceof javax.swing.Box
                || live instanceof WindowSurrogate || live instanceof JInternalFrame || live.getClass() == java.awt.Panel.class
                || live.getClass() == Container.class || live instanceof javax.swing.JMenuBar || live instanceof javax.swing.JMenu
                || live instanceof javax.swing.JPopupMenu;
    }

    /**
     * Ob Kinder frei positioniert werden: Absolute Layout (ohne Menüs, deren Einträge im Popup liegen) sowie
     * GroupLayout und SpringLayout, deren Code der Designer aus den Positionen erzeugt.
     */
    public static boolean isPositional(@Nullable ComponentInfo container) {
        if (container == null || !(container.getLive() instanceof final Container c)) return false;
        if (c instanceof javax.swing.JMenu || c instanceof javax.swing.JMenuBar || c instanceof javax.swing.JPopupMenu) return false;
        final LayoutManager layout = c.getLayout();
        return layout == null || layout instanceof javax.swing.GroupLayout || layout instanceof javax.swing.SpringLayout;
    }

    /** Liegt das Kind frei positioniert in seinem Container? */
    public static boolean isPositionalChild(@Nullable ComponentInfo child) {
        return child != null && (child.getAssociation() == Association.ADD || child.getAssociation() == Association.GROUP)
                && isPositional(child.getParent());
    }

    /** Fenster und JInternalFrame nehmen Kinder über ihre Content Pane auf. */
    public static ComponentInfo dropContainer(FormModel model, ComponentInfo info) {
        final Container pane = info.getLive() instanceof final WindowSurrogate window ? window.getContentPane()
                : info.getLive() instanceof final JInternalFrame frame ? frame.getContentPane() : null;
        final ComponentInfo paneInfo = pane == null ? null : model.find(pane);
        return paneInfo != null ? paneInfo : info;
    }

    /**
     * @param point       Mauspunkt in Container-Koordinaten
     * @param size        Größe der abgelegten Komponente
     * @param grabOffset  Abstand des Mauspunkts zur linken oberen Ecke der Komponente
     * @param moving      verschobene Komponente oder {@code null} bei neuen
     * @return {@code null}, wenn hier nichts abgelegt werden kann
     */
    public static @Nullable Drop compute(ComponentInfo container, Point point, Dimension size, Point grabOffset, @Nullable ComponentInfo moving) {
        final Component live = container.getLive();
        if (live instanceof javax.swing.JMenu || live instanceof javax.swing.JPopupMenu) {
            final Drop drop = flow(container, point, moving, true);
            return new Drop(drop.container(), drop.placement(), drop.feedback(), drop.label(), geometry(container));
        }
        if (live instanceof JScrollPane scrollPane) return scrollPane(container, scrollPane, moving);
        if (live instanceof final JSplitPane splitPane) return splitPane(container, splitPane, point, moving);
        if (live instanceof JTabbedPane) {
            return new Drop(container, Placement.of(Association.TAB), List.of(new Rectangle(live.getSize())), "new tab");
        }
        if (!(live instanceof final Container target)) return null;
        final LayoutManager layout = target.getLayout();
        if (isPositional(container)) {
            final Rectangle bounds = new Rectangle(point.x - grabOffset.x, point.y - grabOffset.y, size.width, size.height);
            return new Drop(container, Placement.absolute(bounds), List.of(bounds), "%d, %d".formatted(bounds.x, bounds.y));
        }
        if (layout instanceof BorderLayout) return border(container, target, point, moving);
        if (layout instanceof final GridBagLayout gridBag) return gridBag(container, gridBag, point, moving);
        final String layoutClass = layout.getClass().getName();
        if (MIG_LAYOUT.equals(layoutClass)) return mig(container, point, moving);
        if (FORM_LAYOUT.equals(layoutClass)) return forms(container, target, layout, point);
        final boolean vertical = layout instanceof final BoxLayout box && (box.getAxis() == BoxLayout.Y_AXIS || box.getAxis() == BoxLayout.PAGE_AXIS)
                || live instanceof final JToolBar toolBar && toolBar.getOrientation() == JToolBar.VERTICAL;
        return flow(container, point, moving, vertical);
    }

    private static @Nullable Drop scrollPane(ComponentInfo container, JScrollPane scrollPane, @Nullable ComponentInfo moving) {
        for (final ComponentInfo child : container.getChildren()) {
            if (child.getAssociation() == Association.VIEWPORT && child != moving) return null;
        }
        final Rectangle viewport = scrollPane.getViewport().getBounds();
        return new Drop(container, Placement.of(Association.VIEWPORT), List.of(viewport), "viewport");
    }

    private static @Nullable Drop splitPane(ComponentInfo container, JSplitPane splitPane, Point point, @Nullable ComponentInfo moving) {
        final boolean horizontal = splitPane.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
        final boolean first = horizontal ? point.x < splitPane.getWidth() / 2 : point.y < splitPane.getHeight() / 2;
        final Association association = horizontal ? first ? Association.LEFT : Association.RIGHT : first ? Association.TOP : Association.BOTTOM;
        final Association alias = switch (association) {
            case LEFT -> Association.TOP;
            case RIGHT -> Association.BOTTOM;
            case TOP -> Association.LEFT;
            default -> Association.RIGHT;
        };
        for (final ComponentInfo child : container.getChildren()) {
            if ((child.getAssociation() == association || child.getAssociation() == alias) && child != moving) return null;
        }
        final Component slot = first ? splitPane.getLeftComponent() : splitPane.getRightComponent();
        final Rectangle area = slot != null ? slot.getBounds()
                : horizontal ? new Rectangle(first ? 0 : splitPane.getWidth() / 2, 0, splitPane.getWidth() / 2, splitPane.getHeight())
                : new Rectangle(0, first ? 0 : splitPane.getHeight() / 2, splitPane.getWidth(), splitPane.getHeight() / 2);
        return new Drop(container, Placement.of(association), List.of(area), association.name().toLowerCase());
    }

    private static @Nullable Drop border(ComponentInfo container, Container target, Point point, @Nullable ComponentInfo moving) {
        final Insets insets = target.getInsets();
        final int x = insets.left, y = insets.top;
        final int w = target.getWidth() - insets.left - insets.right, h = target.getHeight() - insets.top - insets.bottom;
        final int bandX = Math.max(8, Math.min(w / 4, 60)), bandY = Math.max(8, Math.min(h / 4, 60));
        final String region;
        final Rectangle area;
        if (point.y < y + bandY) {
            region = "NORTH";
            area = new Rectangle(x, y, w, bandY);
        } else if (point.y > y + h - bandY) {
            region = "SOUTH";
            area = new Rectangle(x, y + h - bandY, w, bandY);
        } else if (point.x < x + bandX) {
            region = "WEST";
            area = new Rectangle(x, y + bandY, bandX, h - 2 * bandY);
        } else if (point.x > x + w - bandX) {
            region = "EAST";
            area = new Rectangle(x + w - bandX, y + bandY, bandX, h - 2 * bandY);
        } else {
            region = "CENTER";
            area = new Rectangle(x + bandX, y + bandY, w - 2 * bandX, h - 2 * bandY);
        }
        for (final ComponentInfo child : container.getChildren()) {
            if (child == moving || child.getAssociation() != Association.ADD) continue;
            if (region.equals(borderRegion(child))) return null;
        }
        return new Drop(container, Placement.constraints("java.awt.BorderLayout." + region), List.of(area), region);
    }

    /** Region eines Kindes im BorderLayout ({@code null}/fehlend = CENTER). */
    public static String borderRegion(ComponentInfo child) {
        final Object value = child.getConstraintsValue();
        if (value instanceof final String s) {
            for (final String region : REGIONS) {
                if (Objects.equals(s, regionValue(region))) return region;
            }
            return switch (s) {
                case "First" -> "NORTH";
                case "Last" -> "SOUTH";
                case "Before" -> "WEST";
                case "After" -> "EAST";
                default -> s.toUpperCase();
            };
        }
        return child.getConstraints() == null ? "CENTER" : String.valueOf(child.getConstraints());
    }

    private static String regionValue(String region) {
        return switch (region) {
            case "NORTH" -> BorderLayout.NORTH;
            case "SOUTH" -> BorderLayout.SOUTH;
            case "WEST" -> BorderLayout.WEST;
            case "EAST" -> BorderLayout.EAST;
            default -> BorderLayout.CENTER;
        };
    }

    public static List<String> borderRegions() {
        return List.of(REGIONS);
    }

    /** Spalten- und Zeilengrenzen eines GridBagLayout in Container-Koordinaten (n+1 Werte für n Spalten). */
    public record GridLines(int[] xs, int[] ys) {
        public int columns() {
            return xs.length - 1;
        }

        public int rows() {
            return ys.length - 1;
        }
    }

    public static GridLines gridLines(GridBagLayout layout) {
        final int[][] dimensions = layout.getLayoutDimensions();
        final double[][] weights = layout.getLayoutWeights();
        final Point origin = layout.getLayoutOrigin();
        return new GridLines(boundaries(origin.x, withoutSentinel(dimensions[0], weights[0])),
                boundaries(origin.y, withoutSentinel(dimensions[1], weights[1])));
    }

    /**
     * Die letzte Spalte/Zeile des WindowBuilder-Stils (Gewicht {@code Double.MIN_VALUE}) ist keine echte: sie nimmt nur
     * den Restplatz auf, damit das Gitter oben links bleibt.
     */
    private static int[] withoutSentinel(int[] sizes, double[] weights) {
        int count = sizes.length;
        if (count > 0 && count - 1 < weights.length && weights[count - 1] == Double.MIN_VALUE) count--;
        return java.util.Arrays.copyOf(sizes, count);
    }

    private static int[] boundaries(int start, int[] sizes) {
        final int[] result = new int[sizes.length + 1];
        result[0] = start;
        for (int i = 0; i < sizes.length; i++) result[i + 1] = result[i] + sizes[i];
        return result;
    }

    /**
     * GridBagLayout wie im WindowBuilder: in eine freie Zelle, hinter die letzte Spalte/Zeile (neue anhängen) oder auf
     * eine Gitterlinie (dort neue Spalte/Zeile einfügen).
     */
    private static @Nullable Drop gridBag(ComponentInfo container, GridBagLayout layout, Point point, @Nullable ComponentInfo moving) {
        final GridLines lines = gridLines(layout);
        final Component live = container.getLive();
        if (lines.columns() == 0 || lines.rows() == 0) {
            final Rectangle area = new Rectangle(live.getSize());
            return new Drop(container, Placement.cell(0, 0, false, false), List.of(area), "0, 0");
        }
        final int[] column = locate(lines.xs(), point.x);
        final int[] row = locate(lines.ys(), point.y);
        final boolean insertColumn = column[1] == 1, insertRow = row[1] == 1;
        final int x = column[0], y = row[0];
        if (!insertColumn && !insertRow && x < lines.columns() && y < lines.rows()) {
            for (final ComponentInfo child : container.getChildren()) {
                if (child == moving || child.getAssociation() != Association.ADD) continue;
                final GridBagWriter.Slot slot = GridBagWriter.slotOf(layout, child.getLive());
                if (x >= slot.x() && x < slot.x() + slot.w() && y >= slot.y() && y < slot.y() + slot.h()) return null;
            }
        }
        if (moving != null && moving.getParent() == container && !insertColumn && !insertRow) {
            final GridBagWriter.Slot own = GridBagWriter.slotOf(layout, moving.getLive());
            if (own.x() == x && own.y() == y) return new Drop(container, Placement.cell(x, y, false, false), List.of(), null);
        }
        final List<Rectangle> feedback = new ArrayList<>();
        final int top = lines.ys()[0], left = lines.xs()[0];
        final int bottom = Math.max(lines.ys()[lines.rows()], top + 10), right = Math.max(lines.xs()[lines.columns()], left + 10);
        if (insertColumn) feedback.add(new Rectangle(lines.xs()[Math.min(x, lines.columns())] - 1, top, 3, bottom - top));
        if (insertRow) feedback.add(new Rectangle(left, lines.ys()[Math.min(y, lines.rows())] - 1, right - left, 3));
        if (!insertColumn && !insertRow) {
            final int cx = x < lines.columns() ? lines.xs()[x] : lines.xs()[lines.columns()];
            final int cw = x < lines.columns() ? lines.xs()[x + 1] - cx : Math.max(20, Math.min(60, live.getWidth() - cx));
            final int cy = y < lines.rows() ? lines.ys()[y] : lines.ys()[lines.rows()];
            final int ch = y < lines.rows() ? lines.ys()[y + 1] - cy : Math.max(20, Math.min(40, live.getHeight() - cy));
            feedback.add(new Rectangle(cx, cy, Math.max(cw, 6), Math.max(ch, 6)));
        }
        final String label = insertColumn && insertRow ? "insert column %d, row %d".formatted(x, y)
                : insertColumn ? "insert column %d".formatted(x) : insertRow ? "insert row %d".formatted(y) : "%d, %d".formatted(x, y);
        return new Drop(container, Placement.cell(x, y, insertColumn, insertRow), feedback, label);
    }

    /** {index, 1 = auf der Linie davor einfügen}; hinter der letzten Linie: neue Spalte am Ende. */
    private static int[] locate(int[] bounds, int position) {
        final int count = bounds.length - 1;
        if (position >= bounds[count]) return new int[]{count, 0};
        if (position < bounds[0]) return new int[]{0, 1};
        for (int i = 0; i < count; i++) {
            if (position >= bounds[i] && position < bounds[i + 1]) {
                final int size = bounds[i + 1] - bounds[i];
                final int tolerance = Math.min(4, size / 4);
                if (position - bounds[i] < tolerance) return new int[]{i, 1};
                if (bounds[i + 1] - position <= tolerance && i + 1 < count) return new int[]{i + 1, 1};
                return new int[]{i, 0};
            }
        }
        return new int[]{count, 0};
    }

    /**
     * MigLayout: Spalten und Zeilen aus den Positionen der Kinder; rechts bzw. unterhalb von allem entsteht eine neue.
     * Constraints wie im WindowBuilder: {@code "cell 1 0"}.
     */
    private static Drop mig(ComponentInfo container, Point point, @Nullable ComponentInfo moving) {
        final List<Integer> lefts = new ArrayList<>(), tops = new ArrayList<>();
        int right = 0, bottom = 0;
        for (final ComponentInfo child : container.getChildren()) {
            if (child == moving || child.getAssociation() != Association.ADD) continue;
            final Rectangle b = child.getLive().getBounds();
            lefts.add(b.x);
            tops.add(b.y);
            right = Math.max(right, b.x + b.width);
            bottom = Math.max(bottom, b.y + b.height);
        }
        final List<Integer> columns = clusters(lefts), rows = clusters(tops);
        final int column = columns.isEmpty() ? 0 : point.x > right ? columns.size() : Math.max(0, countAtOrBefore(columns, point.x) - 1);
        final int row = rows.isEmpty() ? 0 : point.y > bottom ? rows.size() : Math.max(0, countAtOrBefore(rows, point.y) - 1);
        final int x = column < columns.size() ? columns.get(column) : right + 2;
        final int y = row < rows.size() ? rows.get(row) : bottom + 2;
        final int w = column + 1 < columns.size() ? columns.get(column + 1) - x : 40;
        final int h = row + 1 < rows.size() ? rows.get(row + 1) - y : 20;
        return new Drop(container, Placement.constraints("\"cell %d %d\"".formatted(column, row)), List.of(new Rectangle(x, y, Math.max(w, 6), Math.max(h, 6))),
                "cell %d %d".formatted(column, row));
    }

    private static List<Integer> clusters(List<Integer> values) {
        final List<Integer> result = new ArrayList<>();
        for (final int value : new java.util.TreeSet<>(values)) {
            if (result.isEmpty() || value - result.get(result.size() - 1) > 5) result.add(value);
        }
        return result;
    }

    private static int countAtOrBefore(List<Integer> starts, int position) {
        int count = 0;
        for (final int start : starts) {
            if (start <= position + 2) count++;
        }
        return count;
    }

    /** JGoodies FormLayout: in eine Zelle des Gitters ({@code "2, 4"}, 1-basiert); die Zellen liefert getLayoutInfo. */
    private static @Nullable Drop forms(ComponentInfo container, Container target, LayoutManager layout, Point point) {
        final int[] columns, rows;
        try {
            final Object info = layout.getClass().getMethod("getLayoutInfo", Container.class).invoke(layout, target);
            columns = (int[]) info.getClass().getField("columnOrigins").get(info);
            rows = (int[]) info.getClass().getField("rowOrigins").get(info);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        final int column = cell(columns, point.x), row = cell(rows, point.y);
        if (column < 0 || row < 0) return null;
        final Rectangle area = new Rectangle(columns[column], rows[row], Math.max(columns[column + 1] - columns[column], 6), Math.max(rows[row + 1] - rows[row], 6));
        return new Drop(container, Placement.constraints("\"%d, %d\"".formatted(column + 1, row + 1)), List.of(area),
                "%d, %d".formatted(column + 1, row + 1));
    }

    private static int cell(int[] origins, int position) {
        for (int i = 0; i + 1 < origins.length; i++) {
            if (position >= origins[i] && position < origins[i + 1]) return i;
        }
        return -1;
    }

    /** Flow-, Grid-, Box- und unbekannte Layouts: Einfügen in Lesereihenfolge vor dem Kind hinter dem Mauspunkt. */
    private static Drop flow(ComponentInfo container, Point point, @Nullable ComponentInfo moving, boolean vertical) {
        final List<ComponentInfo> children = new ArrayList<>();
        for (final ComponentInfo child : container.getChildren()) {
            if (child.getAssociation() == Association.ADD && child != moving) children.add(child);
        }
        ComponentInfo before = null;
        for (final ComponentInfo child : children) {
            final Rectangle b = child.getLive().getBounds();
            final boolean isAfterPoint = vertical ? point.y < b.y + b.height / 2
                    : point.y < b.y || point.y <= b.y + b.height && point.x < b.x + b.width / 2;
            if (isAfterPoint) {
                before = child;
                break;
            }
        }
        final Rectangle line;
        if (before != null) {
            final Rectangle b = before.getLive().getBounds();
            line = vertical ? new Rectangle(b.x, b.y - 2, b.width, 3) : new Rectangle(b.x - 2, b.y, 3, b.height);
        } else if (!children.isEmpty()) {
            final Rectangle b = children.get(children.size() - 1).getLive().getBounds();
            line = vertical ? new Rectangle(b.x, b.y + b.height, b.width, 3) : new Rectangle(b.x + b.width, b.y, 3, b.height);
        } else {
            line = new Rectangle(geometry(container).getSize());
        }
        // Verschieben ans eigene Ende/vor sich selbst ist kein Ortswechsel
        if (moving != null && moving.getParent() == container) {
            final int index = container.getChildren().indexOf(moving);
            final List<ComponentInfo> all = container.getChildren();
            final ComponentInfo next = index + 1 < all.size() ? all.get(index + 1) : null;
            if (before == next) return new Drop(container, Placement.before(before), List.of(), null);
        }
        return new Drop(container, Placement.before(before), List.of(line), null);
    }
}
