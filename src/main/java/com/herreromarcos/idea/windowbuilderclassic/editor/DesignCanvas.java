package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.codegen.GridBagWriter;
import com.herreromarcos.idea.windowbuilderclassic.codegen.Placement;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.herreromarcos.idea.windowbuilderclassic.parser.TypeResolver;
import com.herreromarcos.idea.windowbuilderclassic.runtime.AwtProxies;
import com.herreromarcos.idea.windowbuilderclassic.runtime.Evaluator;
import com.herreromarcos.idea.windowbuilderclassic.runtime.FormBuilder;
import com.herreromarcos.idea.windowbuilderclassic.runtime.Reflection;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die Zeichenfläche: das lebende Formular plus eine durchsichtige Ebene darüber, die alle Mausereignisse abfängt und
 * Auswahl, Griffe, Gitter, aufgeklappte Menüs und Ablage-Feedback zeichnet – die lebenden Komponenten selbst reagieren
 * nie auf den Benutzer.
 */
final class DesignCanvas extends JPanel {
    static final int MARGIN = 20;
    private static final int HANDLE = 7;
    private static final int HEADER = 14;
    private static final int SNAP = 5;
    private static final Color SELECTION = new JBColor(0x3574F0, 0x548AF7);
    private static final Color FEEDBACK = new JBColor(new Color(0x2E, 0xA0, 0x43), new Color(0x57, 0xC0, 0x6A));
    private static final Color FORBIDDEN = new JBColor(0xE55765, 0xDB5C5C);
    private static final Color CANVAS = new JBColor(0xFFFFFF, 0x1E1F22);
    private static final Color OUTLINE = new JBColor(new Color(0, 0, 0, 60), new Color(255, 255, 255, 60));
    private static final Color GRID = new JBColor(new Color(0x35, 0x74, 0xF0, 0x60), new Color(0x54, 0x8A, 0xF7, 0x60));
    private static final Color GUIDE = new JBColor(new Color(0xE5, 0x57, 0x65), new Color(0xDB, 0x5C, 0x5C));
    private static final Color HEADER_BACKGROUND = new JBColor(0xEBECF0, 0x393B40);
    private static final Color HEADER_GROWS = new JBColor(0xC2D6FC, 0x2E436E);

    interface Host {
        @Nullable FormModel model();

        /** Die Hauptauswahl (Properties, Griffe). */
        @Nullable ComponentInfo selection();

        /** Alle ausgewählten Komponenten, die Hauptauswahl zuerst. */
        List<ComponentInfo> selectionList();

        void select(@Nullable ComponentInfo info);

        void setSelection(List<ComponentInfo> infos);

        void toggleSelection(ComponentInfo info);

        /** Aktives Werkzeug der Palette, {@code null} = Auswahl. */
        @Nullable PaletteEntry tool();

        void toolUsed(boolean keep);

        void create(PaletteEntry entry, DropTargets.Drop drop);

        void move(ComponentInfo component, DropTargets.Drop drop);

        /** Mehrere Komponenten im Absolute Layout auf einmal (ein Undo-Schritt). */
        void moveAll(Map<ComponentInfo, Rectangle> bounds);

        void resize(ComponentInfo component, Rectangle bounds);

        /** Spannweite im GridBagLayout. */
        void resizeSpan(ComponentInfo component, int width, int height);

        void resizeForm(Dimension size);

        void applyLayout(ComponentInfo container, PaletteEntry layout);

        void navigate(ComponentInfo component);

        /** Doppelklick auf eine Komponente. */
        void activate(ComponentInfo component);

        void showPopup(MouseEvent event);

        /** Klick auf einen Spalten-/Zeilenkopf eines GridBagLayout. */
        void gridHeader(ComponentInfo container, boolean column, int index, MouseEvent event);
    }

    /** Ein Spalten- oder Zeilenkopf in Overlay-Koordinaten. */
    private record Header(ComponentInfo container, boolean column, int index, Rectangle bounds, boolean grows) {}

    /** Ein aufgeklapptes Menü bzw. Popup-Menü: gezeichnet von der Overlay-Ebene an {@code location}. */
    private record OpenPopup(JPopupMenu popup, Point location) {
        Rectangle bounds() {
            return new Rectangle(location, popup.getSize());
        }
    }

    /** Griffe in der Reihenfolge NW, N, NE, E, SE, S, SW, W. */
    private static final int[][] HANDLES = {{0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {0, 1}};
    private static final int[] CURSORS = {Cursor.NW_RESIZE_CURSOR, Cursor.N_RESIZE_CURSOR, Cursor.NE_RESIZE_CURSOR,
            Cursor.E_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR};
    /** Formular-Wurzel und GridBag-Kinder: nur O, SO, S. */
    private static final Set<Integer> EAST_SOUTH = Set.of(3, 4, 5);

    private final Host host;
    private final Overlay overlay = new Overlay();
    private final JPanel holder = new JPanel(null);
    private final Map<String, Dimension> prototypeSizes = new HashMap<>();
    private @Nullable Component root;
    private @Nullable String message;
    /** Menü, über dem gerade abgelegt wird – wird zusätzlich zur Auswahl aufgeklappt. */
    private @Nullable ComponentInfo hoverMenu;
    private List<OpenPopup> popups = List.of();

    DesignCanvas(Host host) {
        super(null);
        this.host = host;
        holder.setOpaque(false);
        add(overlay);
        add(holder);
        setBackground(CANVAS);
        setOpaque(true);
    }

    void setModel(@Nullable FormModel model, @Nullable String message) {
        this.message = message;
        holder.removeAll();
        hoverMenu = null;
        root = model == null ? null : model.getRoot().getLive();
        if (root != null) {
            root.setLocation(0, 0);
            holder.add(root);
        }
        updateSize();
    }

    void updateSize() {
        final Dimension size = root == null ? new Dimension(0, 0) : root.getSize();
        holder.setBounds(MARGIN, MARGIN, size.width, size.height);
        if (root instanceof final Container container) {
            container.invalidate();
            container.validate();
        }
        revalidate();
        repaint();
    }

    @Override
    public void doLayout() {
        overlay.setBounds(0, 0, getWidth(), getHeight());
    }

    @Override
    public Dimension getPreferredSize() {
        final Dimension size = root == null ? new Dimension(0, 0) : root.getSize();
        // Platz für aufgeklappte Menüs unterhalb des Formulars
        int width = size.width + 2 * MARGIN + HANDLE, height = size.height + 2 * MARGIN + HANDLE;
        for (final OpenPopup popup : popups) {
            width = Math.max(width, popup.bounds().x + popup.bounds().width + MARGIN);
            height = Math.max(height, popup.bounds().y + popup.bounds().height + MARGIN);
        }
        return new Dimension(width, height);
    }

    @Override
    public boolean isOptimizedDrawingEnabled() {
        // Die Overlay-Ebene liegt über dem Formular
        return false;
    }

    JComponent getEventLayer() {
        return overlay;
    }

    /** Für Inline-Bearbeitung: Bounds einer Komponente in Canvas-Koordinaten. */
    Rectangle canvasBounds(ComponentInfo info) {
        refreshPopups();
        return SwingUtilities.convertRectangle(overlay, boundsOf(info), this);
    }

    // ---- Aufgeklappte Menüs

    /**
     * Welche Menüs aufgeklappt sind: die der Auswahl und ihrer Vorfahren (wie im WindowBuilder) plus das Menü, über dem
     * gerade abgelegt wird. Popup-Menüs ({@code addPopup}) erscheinen unter ihrer Komponente.
     */
    private void refreshPopups() {
        final List<ComponentInfo> chain = new ArrayList<>();
        addChain(chain, host.selection());
        addChain(chain, hoverMenu);
        final List<OpenPopup> result = new ArrayList<>();
        popups = result;
        for (final ComponentInfo info : chain) {
            final JPopupMenu popup;
            final Point location;
            if (info.getLive() instanceof final JMenu menu) {
                popup = menu.getPopupMenu();
                final Rectangle bounds = boundsOf(info);
                location = menu.getParent() instanceof JMenuBar || info.getParent() == null || info.getParent().getLive() instanceof JMenuBar
                        ? new Point(bounds.x, bounds.y + bounds.height) : new Point(bounds.x + bounds.width, bounds.y);
            } else if (info.getLive() instanceof final JPopupMenu menu && info.getParent() != null) {
                popup = menu;
                final Rectangle owner = boundsOf(info.getParent());
                location = new Point(owner.x + Math.min(10, owner.width / 2), owner.y + owner.height);
            } else {
                continue;
            }
            final Dimension preferred = popup.getPreferredSize();
            popup.setSize(Math.max(preferred.width, 90), Math.max(preferred.height, 20));
            FormBuilder.layoutTree(popup);
            result.add(new OpenPopup(popup, location));
        }
    }

    private static void addChain(List<ComponentInfo> chain, @Nullable ComponentInfo info) {
        final List<ComponentInfo> ancestors = new ArrayList<>();
        for (ComponentInfo c = info; c != null; c = c.getParent()) {
            if (c.getLive() instanceof JMenu || c.getLive() instanceof JPopupMenu) ancestors.add(0, c);
        }
        for (final ComponentInfo c : ancestors) {
            if (!chain.contains(c)) chain.add(c);
        }
    }

    private @Nullable OpenPopup popupContaining(Component component) {
        for (final OpenPopup popup : popups) {
            if (component == popup.popup() || SwingUtilities.isDescendingFrom(component, popup.popup())) return popup;
        }
        return null;
    }

    // ---- Geometrie

    /** Rechteck aus dem Koordinatensystem von {@code component} in Overlay-Koordinaten (auch in aufgeklappten Menüs). */
    private Rectangle toOverlay(Component component, Rectangle r) {
        final OpenPopup popup = popupContaining(component);
        if (popup != null) {
            final Rectangle inPopup = SwingUtilities.convertRectangle(component, r, popup.popup());
            inPopup.translate(popup.location().x, popup.location().y);
            return inPopup;
        }
        if (component == root) return new Rectangle(r.x + MARGIN, r.y + MARGIN, r.width, r.height);
        return SwingUtilities.convertRectangle(component, r, overlay);
    }

    private Point fromOverlay(Point point, Component component) {
        final OpenPopup popup = popupContaining(component);
        if (popup != null) {
            return SwingUtilities.convertPoint(popup.popup(), new Point(point.x - popup.location().x, point.y - popup.location().y), component);
        }
        return SwingUtilities.convertPoint(overlay, point, component);
    }

    private Rectangle boundsOf(ComponentInfo info) {
        final Component live = info.getLive();
        if (live == root) return new Rectangle(MARGIN, MARGIN, live.getWidth(), live.getHeight());
        final OpenPopup popup = popupContaining(live);
        if (popup != null && popup.popup() == live) return popup.bounds();
        if (live.getParent() == null) return new Rectangle(-1000, -1000, 0, 0);
        return toOverlay(live.getParent(), live.getBounds());
    }

    /** Im Formular oder in einem aufgeklappten Menü sichtbar – unabhängig davon, ob es schon einen Bildschirm gibt. */
    private boolean isInForm(ComponentInfo info) {
        if (root == null) return false;
        if (popupContaining(info.getLive()) != null) return true;
        for (Component c = info.getLive(); c != null; c = c.getParent()) {
            if (!c.isVisible()) return false;
            if (c == root) return true;
        }
        return false;
    }

    /** Die Komponente mit Info unter dem Punkt; {@code exclude} und seine Kinder werden übersprungen. */
    private @Nullable ComponentInfo infoAt(Point point, @Nullable ComponentInfo exclude) {
        final FormModel model = host.model();
        if (model == null || root == null) return null;
        // Aufgeklappte Menüs liegen über dem Formular, das zuletzt geöffnete ganz oben
        for (int i = popups.size() - 1; i >= 0; i--) {
            final OpenPopup popup = popups.get(i);
            if (!popup.bounds().contains(point)) continue;
            Component component = SwingUtilities.getDeepestComponentAt(popup.popup(), point.x - popup.location().x, point.y - popup.location().y);
            while (component != null) {
                final ComponentInfo info = model.find(component);
                if (info != null && accept(info, exclude)) return info;
                if (component == popup.popup()) {
                    final ComponentInfo owner = popup.popup().getInvoker() == null ? null : model.find(popup.popup().getInvoker());
                    if (owner != null && accept(owner, exclude)) return owner;
                    break;
                }
                component = component.getParent();
            }
        }
        final Point inRoot = new Point(point.x - MARGIN, point.y - MARGIN);
        if (!new Rectangle(root.getSize()).contains(inRoot)) return null;
        Component component = SwingUtilities.getDeepestComponentAt(root, inRoot.x, inRoot.y);
        while (component != null) {
            final ComponentInfo info = model.find(component);
            if (info != null && accept(info, exclude)) return info;
            if (component == root) break;
            component = component.getParent();
        }
        return model.getRoot() == exclude ? null : model.getRoot();
    }

    private static boolean accept(ComponentInfo info, @Nullable ComponentInfo exclude) {
        return exclude == null || info != exclude && !exclude.isAncestorOf(info);
    }

    /** Ablage-Ziel am Punkt; ist der innerste Container belegt, wird der nächst äußere versucht. */
    private @Nullable DropTargets.Drop dropAt(Point point, Dimension size, Point grabOffset, @Nullable ComponentInfo moving,
                                              @Nullable PaletteEntry entry) {
        final FormModel model = host.model();
        if (model == null) return null;
        final String className = entry != null ? entry.className() : moving != null ? moving.getClassName() : null;
        if ("javax.swing.JMenuBar".equals(className)) return menuBarDrop(model, moving);
        if ("javax.swing.JPopupMenu".equals(className)) return popupDrop(point, moving);
        ComponentInfo info = infoAt(point, moving);
        while (info != null) {
            if (DropTargets.isDropContainer(info)) {
                final ComponentInfo container = DropTargets.dropContainer(model, info);
                final Component geometry = DropTargets.geometry(container);
                if (!(geometry instanceof Container)) return null;
                final DropTargets.Drop drop = DropTargets.compute(container, fromOverlay(point, geometry), size, grabOffset, moving);
                if (drop != null) return drop;
            }
            info = info.getParent();
            while (info != null && info.isImplicit()) info = info.getParent();
        }
        return null;
    }

    /** Die Menüleiste gehört ans Fenster (setJMenuBar) – nur eine je Fenster. */
    private @Nullable DropTargets.Drop menuBarDrop(FormModel model, @Nullable ComponentInfo moving) {
        final ComponentInfo window = model.getRoot();
        if (!(window.getLive() instanceof final WindowSurrogate surrogate) && !(window.getLive() instanceof JInternalFrame)) return null;
        for (final ComponentInfo child : window.getChildren()) {
            if (child.getAssociation() == Association.MENU_BAR && child != moving) return null;
        }
        final javax.swing.JRootPane rootPane = window.getLive() instanceof final WindowSurrogate s ? s.getRootPane() : ((JInternalFrame) window.getLive()).getRootPane();
        final Rectangle area = SwingUtilities.convertRectangle(rootPane, new Rectangle(0, 0, rootPane.getWidth(), 22), window.getLive());
        return new DropTargets.Drop(window, Placement.of(Association.MENU_BAR), List.of(area), "setJMenuBar");
    }

    /** Ein Popup-Menü hängt an der Komponente unter der Maus (addPopup). */
    private @Nullable DropTargets.Drop popupDrop(Point point, @Nullable ComponentInfo moving) {
        ComponentInfo info = infoAt(point, moving);
        while (info != null && info.isImplicit()) info = info.getParent();
        if (info == null || info.getLive() instanceof JPopupMenu) return null;
        return new DropTargets.Drop(info, Placement.of(Association.POPUP), List.of(new Rectangle(info.getLive().getSize())), "addPopup");
    }

    /** Das GridBagLayout, dessen Gitter gezeigt wird: der ausgewählte Container oder der Container der Auswahl. */
    private @Nullable ComponentInfo gridContainer() {
        final FormModel model = host.model();
        final ComponentInfo selection = host.selection();
        if (model == null || selection == null) return null;
        final ComponentInfo own = DropTargets.isDropContainer(selection) ? DropTargets.dropContainer(model, selection) : null;
        if (isGridBag(own)) return own;
        return isGridBag(selection.getParent()) && selection.getAssociation() == Association.ADD ? selection.getParent() : null;
    }

    private boolean isGridBag(@Nullable ComponentInfo info) {
        return info != null && info.getLive() instanceof final Container c && c.getLayout() instanceof GridBagLayout && isInForm(info);
    }

    private List<Header> headers() {
        final ComponentInfo container = gridContainer();
        if (container == null) return List.of();
        final GridBagLayout layout = (GridBagLayout) ((Container) container.getLive()).getLayout();
        final DropTargets.GridLines lines = DropTargets.gridLines(layout);
        final double[][] weights = layout.getLayoutWeights();
        final Rectangle bounds = boundsOf(container);
        final List<Header> result = new ArrayList<>();
        for (int i = 0; i < lines.columns(); i++) {
            final int x = bounds.x + lines.xs()[i];
            result.add(new Header(container, true, i, new Rectangle(x, bounds.y - HEADER - 1, Math.max(lines.xs()[i + 1] - lines.xs()[i], 4), HEADER),
                    i < weights[0].length && weights[0][i] > 0));
        }
        for (int i = 0; i < lines.rows(); i++) {
            final int y = bounds.y + lines.ys()[i];
            result.add(new Header(container, false, i, new Rectangle(bounds.x - HEADER - 1, y, HEADER, Math.max(lines.ys()[i + 1] - lines.ys()[i], 4)),
                    i < weights[1].length && weights[1][i] > 0));
        }
        return result;
    }

    private @Nullable Header headerAt(Point point) {
        for (final Header header : headers()) {
            if (header.bounds().contains(point)) return header;
        }
        return null;
    }

    private static boolean isAbsoluteChild(@Nullable ComponentInfo info) {
        return DropTargets.isPositionalChild(info);
    }

    private static boolean isGridBagChild(@Nullable ComponentInfo info) {
        return info != null && info.getAssociation() == Association.ADD && GridBagWriter.isGridBag(info.getParent());
    }

    private Dimension prototypeSize(PaletteEntry entry) {
        return prototypeSizes.computeIfAbsent(String.valueOf(entry.className()) + entry.creationArgs() + entry.factory(), key -> {
            if (entry.factory() != null) return entry.factory().contains("Strut") || entry.factory().contains("Rigid") ? new Dimension(20, 20) : new Dimension(40, 20);
            if (AwtProxies.isProxied(entry.className())) {
                final String args = entry.creationArgs();
                final Object[] values = args.startsWith("\"") ? new Object[]{args.substring(1, args.length() - 1)} : new Object[0];
                return AwtProxies.create(String.valueOf(entry.className()), values).getPreferredSize();
            }
            final Class<?> cls = TypeResolver.jdkClass(entry.className());
            if (cls == null || !Evaluator.isInstantiable(entry.className())) return new Dimension(100, 25);
            try {
                final String args = entry.creationArgs();
                final Object instance = args.startsWith("\"") && args.endsWith("\"") && args.indexOf('"', 1) == args.length() - 1
                        ? Reflection.construct(cls, new Object[]{args.substring(1, args.length() - 1)})
                        : Reflection.construct(cls, new Object[0]);
                final Dimension preferred = ((Component) instance).getPreferredSize();
                return new Dimension(Math.max(preferred.width, 10), Math.max(preferred.height, 10));
            } catch (final Exception | LinkageError e) {
                return new Dimension(100, 25);
            }
        });
    }

    private static Rectangle handle(Rectangle bounds, int index) {
        final int x = bounds.x + HANDLES[index][0] * bounds.width / 2 - HANDLE / 2;
        final int y = bounds.y + HANDLES[index][1] * bounds.height / 2 - HANDLE / 2;
        return new Rectangle(x, y, HANDLE, HANDLE);
    }

    /** Welche Griffe eine Komponente hat: alle im Absolute Layout, O/SO/S für Formular und GridBag-Kinder. */
    private @Nullable Set<Integer> activeHandles(ComponentInfo info) {
        if (info.getLive() == root || isGridBagChild(info)) return EAST_SOUTH;
        if (isAbsoluteChild(info)) return Set.of(0, 1, 2, 3, 4, 5, 6, 7);
        return null;
    }

    private int handleAt(ComponentInfo info, Point point) {
        final Set<Integer> active = activeHandles(info);
        if (active == null) return -1;
        final Rectangle bounds = boundsOf(info);
        for (final int i : active) {
            final Rectangle h = handle(bounds, i);
            h.grow(2, 2);
            if (h.contains(point)) return i;
        }
        return -1;
    }

    private static Rectangle resized(Rectangle start, int handle, int dx, int dy) {
        final Rectangle r = new Rectangle(start);
        final int hx = HANDLES[handle][0], hy = HANDLES[handle][1];
        if (hx == 0) {
            r.x += dx;
            r.width -= dx;
        } else if (hx == 2) {
            r.width += dx;
        }
        if (hy == 0) {
            r.y += dy;
            r.height -= dy;
        } else if (hy == 2) {
            r.height += dy;
        }
        r.width = Math.max(r.width, 1);
        r.height = Math.max(r.height, 1);
        return r;
    }

    // ---- Snapping im Absolute Layout

    /** Ergebnis des Einrastens: verschobenes Rechteck plus Hilfslinien (Overlay-Koordinaten). */
    private record Snapped(Rectangle bounds, List<Rectangle> guides) {}

    /**
     * Rastet Kanten an denen der Geschwister und des Containers ein (Abstand ≤ 5 Pixel), wie das „free snapping“ des
     * WindowBuilder. {@code moveX}/{@code moveY}: ob die linke/obere Kante mitwandert (Verschieben) oder nur die rechte/untere.
     */
    private Snapped snap(Rectangle r, ComponentInfo parent, Set<ComponentInfo> exclude, boolean resizeLeft, boolean resizeTop, boolean move) {
        final Rectangle container = boundsOf(parent);
        final List<Integer> xs = new ArrayList<>(List.of(container.x, container.x + container.width));
        final List<Integer> ys = new ArrayList<>(List.of(container.y, container.y + container.height));
        for (final ComponentInfo sibling : parent.getChildren()) {
            if (exclude.contains(sibling) || sibling.getAssociation() != Association.ADD) continue;
            final Rectangle b = boundsOf(sibling);
            xs.add(b.x);
            xs.add(b.x + b.width);
            ys.add(b.y);
            ys.add(b.y + b.height);
        }
        final Rectangle result = new Rectangle(r);
        final List<Rectangle> guides = new ArrayList<>();
        final Integer dx = move ? best(xs, r.x, r.x + r.width) : resizeLeft ? best(xs, r.x) : best(xs, r.x + r.width);
        if (dx != null) {
            if (move) result.x += dx;
            else if (resizeLeft) {
                result.x += dx;
                result.width -= dx;
            } else {
                result.width += dx;
            }
        }
        final Integer dy = move ? best(ys, r.y, r.y + r.height) : resizeTop ? best(ys, r.y) : best(ys, r.y + r.height);
        if (dy != null) {
            if (move) result.y += dy;
            else if (resizeTop) {
                result.y += dy;
                result.height -= dy;
            } else {
                result.height += dy;
            }
        }
        for (final int x : xs) {
            if (x == result.x || x == result.x + result.width) guides.add(new Rectangle(x, container.y, 1, container.height));
        }
        for (final int y : ys) {
            if (y == result.y || y == result.y + result.height) guides.add(new Rectangle(container.x, y, container.width, 1));
        }
        return new Snapped(result, guides);
    }

    /** Kleinste Verschiebung, mit der eine der Kanten auf einer Linie liegt. */
    private static @Nullable Integer best(List<Integer> lines, int... edges) {
        Integer best = null;
        for (final int edge : edges) {
            for (final int line : lines) {
                final int delta = line - edge;
                if (Math.abs(delta) <= SNAP && (best == null || Math.abs(delta) < Math.abs(best))) best = delta;
            }
        }
        return best;
    }

    // ---- Maus

    private final class Overlay extends JComponent {
        private @Nullable ComponentInfo hover;
        private @Nullable DropTargets.Drop drop;
        private @Nullable Rectangle dragRect;
        private @Nullable Point pressPoint;
        private @Nullable ComponentInfo pressed;
        private Point grabOffset = new Point();
        private int resizeHandle = -1;
        private boolean moving;
        private @Nullable Rectangle marquee;
        /** Gemeinsames Verschieben im Absolute Layout: Ziel-Bounds je Komponente (Overlay-Koordinaten). */
        private Map<ComponentInfo, Rectangle> group = Map.of();
        private List<Rectangle> guides = List.of();
        private @Nullable String spanLabel;

        Overlay() {
            setFocusable(true);
            final MouseAdapter adapter = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    onMove(e.getPoint());
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (e.isPopupTrigger()) {
                        popup(e);
                        return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) onPress(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e)) onDrag(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        popup(e);
                        return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) onRelease(e);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = null;
                    if (pressPoint == null) {
                        drop = null;
                        hoverMenu = null;
                    }
                    repaint();
                }
            };
            addMouseListener(adapter);
            addMouseMotionListener(adapter);
        }

        private void popup(MouseEvent e) {
            refreshPopups();
            final Header header = headerAt(e.getPoint());
            if (header != null) {
                host.gridHeader(header.container(), header.column(), header.index(), e);
                return;
            }
            final ComponentInfo info = infoAt(e.getPoint(), null);
            if (info != null && !host.selectionList().contains(info)) host.select(info);
            host.showPopup(e);
        }

        private void onMove(Point point) {
            refreshPopups();
            final PaletteEntry tool = host.tool();
            if (tool != null && tool.kind() == PaletteEntry.Kind.COMPONENT) {
                drop = dropAt(point, prototypeSize(tool), new Point(0, 0), null, tool);
                updateHoverMenu(drop);
                setCursor(Cursor.getPredefinedCursor(drop == null ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
            } else if (tool != null && tool.kind() == PaletteEntry.Kind.LAYOUT) {
                drop = null;
                hover = layoutTarget(point);
            } else if (tool != null && tool.kind() == PaletteEntry.Kind.MARQUEE) {
                drop = null;
                setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            } else {
                drop = null;
                hover = infoAt(point, null);
                final ComponentInfo selection = host.selection();
                final int h = selection == null ? -1 : handleAt(selection, point);
                setCursor(Cursor.getPredefinedCursor(h < 0 ? Cursor.DEFAULT_CURSOR : CURSORS[h]));
            }
            repaint();
        }

        /** Beim Ablegen über einem Menü klappt es auf, damit man die Einfügestelle sieht. */
        private void updateHoverMenu(@Nullable DropTargets.Drop target) {
            final ComponentInfo menu = target != null && target.container().getLive() instanceof JMenu ? target.container() : null;
            if (menu != hoverMenu) {
                hoverMenu = menu;
                refreshPopups();
                revalidate();
            }
        }

        private @Nullable ComponentInfo layoutTarget(Point point) {
            final FormModel model = host.model();
            ComponentInfo info = infoAt(point, null);
            while (info != null && !DropTargets.isDropContainer(info)) info = info.getParent();
            return info == null || model == null ? null : DropTargets.dropContainer(model, info);
        }

        private void onPress(MouseEvent e) {
            refreshPopups();
            final Point point = e.getPoint();
            pressPoint = point;
            moving = false;
            final PaletteEntry tool = host.tool();
            if (tool != null && tool.kind() == PaletteEntry.Kind.MARQUEE) {
                marquee = new Rectangle(point);
                return;
            }
            if (tool != null) return;
            final Header header = headerAt(point);
            if (header != null) {
                pressPoint = null;
                host.gridHeader(header.container(), header.column(), header.index(), e);
                return;
            }
            if (e.getClickCount() == 2) {
                final ComponentInfo info = infoAt(point, null);
                if (info != null) host.activate(info);
                pressPoint = null;
                return;
            }
            final ComponentInfo selection = host.selection();
            resizeHandle = selection == null ? -1 : handleAt(selection, point);
            if (resizeHandle >= 0) {
                pressed = selection;
                return;
            }
            pressed = infoAt(point, null);
            final boolean toggle = (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0;
            if (pressed != null && toggle) {
                host.toggleSelection(pressed);
                pressPoint = null;
            } else if (pressed != null && !host.selectionList().contains(pressed)) {
                host.select(pressed);
            } else if (pressed != null) {
                // Klick in eine Mehrfachauswahl: die angeklickte wird Hauptauswahl, die übrigen bleiben
                final List<ComponentInfo> list = new ArrayList<>(host.selectionList());
                list.remove(pressed);
                list.add(0, pressed);
                host.setSelection(list);
            } else {
                host.select(null);
            }
            if (pressed != null) {
                final Rectangle bounds = boundsOf(pressed);
                grabOffset = new Point(point.x - bounds.x, point.y - bounds.y);
            }
            repaint();
        }

        private void onDrag(MouseEvent e) {
            final Point point = e.getPoint();
            final boolean snapping = (e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) == 0;
            if (marquee != null && pressPoint != null) {
                marquee = new Rectangle(Math.min(pressPoint.x, point.x), Math.min(pressPoint.y, point.y),
                        Math.abs(point.x - pressPoint.x), Math.abs(point.y - pressPoint.y));
                repaint();
                return;
            }
            if (pressPoint == null || pressed == null || host.tool() != null) return;
            final int dx = point.x - pressPoint.x, dy = point.y - pressPoint.y;
            if (resizeHandle >= 0) {
                Rectangle r = resized(boundsOf(pressed), resizeHandle, dx, dy);
                guides = List.of();
                spanLabel = null;
                if (isGridBagChild(pressed)) {
                    r = spanRect(pressed, r);
                } else if (snapping && isAbsoluteChild(pressed)) {
                    final Snapped snapped = snap(r, pressed.getParent(), Set.of(pressed), HANDLES[resizeHandle][0] == 0, HANDLES[resizeHandle][1] == 0, false);
                    r = snapped.bounds();
                    guides = snapped.guides();
                }
                dragRect = r;
                repaint();
                return;
            }
            if (!moving && Math.abs(dx) + Math.abs(dy) < 4) return;
            if (pressed.getLive() == root || pressed.isImplicit()) return;
            moving = true;
            final List<ComponentInfo> selected = host.selectionList();
            if (selected.size() > 1 && selected.contains(pressed) && sameAbsoluteParent(selected)) {
                moveGroup(selected, dx, dy, snapping);
                return;
            }
            group = Map.of();
            drop = dropAt(point, pressed.getLive().getSize(), grabOffset, pressed, null);
            updateHoverMenu(drop);
            dragRect = new Rectangle(point.x - grabOffset.x, point.y - grabOffset.y, pressed.getLive().getWidth(), pressed.getLive().getHeight());
            guides = List.of();
            if (drop != null && drop.placement().bounds() != null && snapping) {
                final Rectangle inOverlay = toOverlay(drop.geometry(), drop.placement().bounds());
                final Snapped snapped = snap(inOverlay, drop.container(), Set.of(pressed), false, false, true);
                guides = snapped.guides();
                final Point local = fromOverlay(snapped.bounds().getLocation(), drop.geometry());
                final Rectangle bounds = new Rectangle(local, drop.placement().bounds().getSize());
                drop = new DropTargets.Drop(drop.container(), Placement.absolute(bounds), List.of(bounds), "%d, %d".formatted(bounds.x, bounds.y), drop.geometry());
            }
            setCursor(Cursor.getPredefinedCursor(drop == null ? Cursor.DEFAULT_CURSOR : Cursor.MOVE_CURSOR));
            repaint();
        }

        private boolean sameAbsoluteParent(List<ComponentInfo> selected) {
            final ComponentInfo parent = selected.get(0).getParent();
            for (final ComponentInfo info : selected) {
                if (info.getParent() != parent || !isAbsoluteChild(info)) return false;
            }
            return true;
        }

        private void moveGroup(List<ComponentInfo> selected, int dx, int dy, boolean snapping) {
            final Rectangle union = new Rectangle(boundsOf(selected.get(0)));
            for (final ComponentInfo info : selected) union.add(boundsOf(info));
            int sx = dx, sy = dy;
            guides = List.of();
            if (snapping) {
                final Rectangle moved = new Rectangle(union);
                moved.translate(dx, dy);
                final Snapped snapped = snap(moved, selected.get(0).getParent(), Set.copyOf(selected), false, false, true);
                sx += snapped.bounds().x - moved.x;
                sy += snapped.bounds().y - moved.y;
                guides = snapped.guides();
            }
            final Map<ComponentInfo, Rectangle> result = new LinkedHashMap<>();
            for (final ComponentInfo info : selected) {
                final Rectangle r = boundsOf(info);
                r.translate(sx, sy);
                result.put(info, r);
            }
            group = result;
            drop = null;
            repaint();
        }

        /** GridBag: das Rechteck rastet auf Zellgrenzen ein; die Spannweite steht im Label. */
        private Rectangle spanRect(ComponentInfo child, Rectangle dragged) {
            final GridBagLayout layout = (GridBagLayout) ((Container) child.getParent().getLive()).getLayout();
            final DropTargets.GridLines lines = DropTargets.gridLines(layout);
            final GridBagWriter.Slot slot = GridBagWriter.slotOf(layout, child.getLive());
            final Rectangle container = boundsOf(child.getParent());
            final int right = dragged.x + dragged.width - container.x, bottom = dragged.y + dragged.height - container.y;
            final int columns = Math.max(1, nearest(lines.xs(), right) - slot.x());
            final int rows = Math.max(1, nearest(lines.ys(), bottom) - slot.y());
            final int x = lines.xs()[Math.min(slot.x(), lines.columns())], y = lines.ys()[Math.min(slot.y(), lines.rows())];
            final int x2 = lines.xs()[Math.min(slot.x() + columns, lines.columns())], y2 = lines.ys()[Math.min(slot.y() + rows, lines.rows())];
            spanLabel = "%d × %d".formatted(columns, rows);
            return new Rectangle(container.x + x, container.y + y, Math.max(x2 - x, 4), Math.max(y2 - y, 4));
        }

        private static int nearest(int[] bounds, int position) {
            int best = 0;
            for (int i = 0; i < bounds.length; i++) {
                if (Math.abs(bounds[i] - position) < Math.abs(bounds[best] - position)) best = i;
            }
            return best;
        }

        private void onRelease(MouseEvent e) {
            final Point point = e.getPoint();
            final PaletteEntry tool = host.tool();
            final boolean keep = (e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0;
            try {
                if (marquee != null) {
                    selectInside(marquee);
                } else if (tool != null && tool.kind() == PaletteEntry.Kind.COMPONENT) {
                    final DropTargets.Drop target = dropAt(point, prototypeSize(tool), new Point(0, 0), null, tool);
                    if (target != null) {
                        host.create(tool, target);
                        host.toolUsed(keep);
                    }
                } else if (tool != null && tool.kind() == PaletteEntry.Kind.LAYOUT) {
                    final ComponentInfo target = layoutTarget(point);
                    if (target != null) {
                        host.applyLayout(target, tool);
                        host.toolUsed(keep);
                    }
                } else if (pressed != null && resizeHandle >= 0 && dragRect != null) {
                    applyResize(pressed, dragRect);
                } else if (moving && !group.isEmpty()) {
                    final Map<ComponentInfo, Rectangle> local = new LinkedHashMap<>();
                    for (final Map.Entry<ComponentInfo, Rectangle> entry : group.entrySet()) {
                        final Component parent = entry.getKey().getLive().getParent();
                        local.put(entry.getKey(), new Rectangle(fromOverlay(entry.getValue().getLocation(), parent), entry.getValue().getSize()));
                    }
                    host.moveAll(local);
                } else if (pressed != null && moving && drop != null && !drop.feedback().isEmpty()) {
                    host.move(pressed, drop);
                }
            } finally {
                pressPoint = null;
                pressed = null;
                dragRect = null;
                drop = null;
                marquee = null;
                group = Map.of();
                guides = List.of();
                spanLabel = null;
                resizeHandle = -1;
                moving = false;
                hoverMenu = null;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }
        }

        private void applyResize(ComponentInfo component, Rectangle r) {
            if (component.getLive() == root) {
                host.resizeForm(r.getSize());
            } else if (isGridBagChild(component)) {
                final GridBagLayout layout = (GridBagLayout) ((Container) component.getParent().getLive()).getLayout();
                final GridBagWriter.Slot slot = GridBagWriter.slotOf(layout, component.getLive());
                final String[] parts = String.valueOf(spanLabel).split(" × ");
                if (parts.length == 2) {
                    final int w = Integer.parseInt(parts[0]), h = Integer.parseInt(parts[1]);
                    if (w != slot.w() || h != slot.h()) host.resizeSpan(component, w, h);
                }
            } else {
                final Component parent = component.getLive().getParent();
                host.resize(component, new Rectangle(fromOverlay(r.getLocation(), parent), r.getSize()));
            }
        }

        /** Marquee: alle Komponenten, die ganz im Rahmen liegen (ohne deren Kinder). */
        private void selectInside(Rectangle area) {
            final FormModel model = host.model();
            if (model == null) return;
            final List<ComponentInfo> inside = new ArrayList<>();
            for (final ComponentInfo info : model.getAll()) {
                if (info.getLive() == root || info.isImplicit() || !isInForm(info)) continue;
                if (area.contains(boundsOf(info)) && inside.stream().noneMatch(other -> other.isAncestorOf(info))) inside.add(info);
            }
            host.setSelection(inside);
            host.toolUsed(false);
        }

        // ---- Zeichnen

        @Override
        protected void paintComponent(Graphics graphics) {
            final Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            try {
                if (message != null) {
                    g.setColor(UIUtil.getContextHelpForeground());
                    g.drawString(message, MARGIN, MARGIN + (root == null ? 0 : root.getHeight()) + 30);
                }
                final FormModel model = host.model();
                if (model == null) return;
                refreshPopups();
                paintPopups(g);
                paintOutlines(g, model);
                if (hover != null && !host.selectionList().contains(hover) && isInForm(hover)) {
                    g.setColor(SELECTION);
                    g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 3}, 0));
                    final Rectangle r = boundsOf(hover);
                    g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                }
                final ComponentInfo grid = drop != null && isGridBag(drop.container()) ? drop.container() : gridContainer();
                if (grid != null) paintGrid(g, grid);
                final List<ComponentInfo> selected = host.selectionList();
                for (int i = selected.size() - 1; i >= 0; i--) {
                    if (isInForm(selected.get(i))) paintSelection(g, selected.get(i), i == 0);
                }
                paintHeaders(g);
                paintFeedback(g);
                paintGuides(g);
                if (marquee != null) {
                    g.setColor(SELECTION);
                    g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{4, 2}, 0));
                    g.drawRect(marquee.x, marquee.y, marquee.width, marquee.height);
                }
            } finally {
                g.dispose();
            }
        }

        /** Aufgeklappte Menüs: die lebenden Popups, an ihre Position verschoben. */
        private void paintPopups(Graphics2D g) {
            for (final OpenPopup popup : popups) {
                final Graphics2D g2 = (Graphics2D) g.create(popup.location().x, popup.location().y, popup.popup().getWidth(), popup.popup().getHeight());
                try {
                    popup.popup().paint(g2);
                    if (popup.popup().getComponentCount() == 0) {
                        g2.setColor(UIUtil.getContextHelpForeground());
                        g2.setFont(JBUI.Fonts.smallFont());
                        g2.drawString("(empty)", 6, 14);
                    }
                } finally {
                    g2.dispose();
                }
                g.setColor(OUTLINE);
                final Rectangle r = popup.bounds();
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            }
        }

        /** Leere Panels, Struts und Projektklassen wären sonst unsichtbar bzw. namenlos. */
        private void paintOutlines(Graphics2D g, FormModel model) {
            for (final ComponentInfo info : model.getAll()) {
                if (info.getLive() == root || !isInForm(info)) continue;
                final Rectangle r = boundsOf(info);
                if (info.isPlaceholder()) {
                    g.setColor(OUTLINE);
                    g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                    g.setColor(UIUtil.getContextHelpForeground());
                    g.setFont(JBUI.Fonts.smallFont());
                    g.drawString(info.getShortClassName(), r.x + 4, r.y + g.getFontMetrics().getAscent() + 2);
                } else if (info.getLive() instanceof javax.swing.Box.Filler) {
                    g.setColor(GRID);
                    g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 2}, 0));
                    g.drawRect(r.x, r.y, Math.max(r.width - 1, 2), Math.max(r.height - 1, 2));
                    g.setStroke(new BasicStroke(1));
                } else if ((info.getLive() instanceof JPanel || info.getLive() instanceof javax.swing.Box) && !info.isImplicit() && info.getChildren().isEmpty()) {
                    g.setColor(OUTLINE);
                    g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 2}, 0));
                    g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                    g.setStroke(new BasicStroke(1));
                }
            }
        }

        /** Gestrichelte Gitterlinien eines GridBagLayout. */
        private void paintGrid(Graphics2D g, ComponentInfo container) {
            final DropTargets.GridLines lines = DropTargets.gridLines((GridBagLayout) ((Container) container.getLive()).getLayout());
            if (lines.columns() == 0 || lines.rows() == 0) return;
            final Rectangle bounds = boundsOf(container);
            final Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(GRID);
            g2.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 3}, 0));
            final int top = bounds.y + lines.ys()[0], bottom = bounds.y + lines.ys()[lines.rows()];
            final int left = bounds.x + lines.xs()[0], right = bounds.x + lines.xs()[lines.columns()];
            for (final int x : lines.xs()) g2.drawLine(bounds.x + x, top, bounds.x + x, bottom);
            for (final int y : lines.ys()) g2.drawLine(left, bounds.y + y, right, bounds.y + y);
            g2.dispose();
        }

        /** Spalten- und Zeilenköpfe mit Nummer; wachsende (Gewicht &gt; 0) sind hinterlegt. */
        private void paintHeaders(Graphics2D g) {
            g.setFont(JBUI.Fonts.miniFont());
            for (final Header header : headers()) {
                final Rectangle r = header.bounds();
                g.setColor(header.grows() ? HEADER_GROWS : HEADER_BACKGROUND);
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(GRID);
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                final String text = String.valueOf(header.index());
                final int w = g.getFontMetrics().stringWidth(text);
                if (w + 2 > r.width && header.column() || g.getFontMetrics().getHeight() > r.height + 2 && !header.column()) continue;
                g.setColor(UIUtil.getLabelForeground());
                g.drawString(text, r.x + (r.width - w) / 2, r.y + (r.height + g.getFontMetrics().getAscent()) / 2 - 1);
            }
        }

        private void paintSelection(Graphics2D g, ComponentInfo selection, boolean primary) {
            final Rectangle r = boundsOf(selection);
            g.setColor(SELECTION);
            g.setStroke(new BasicStroke(primary ? 2 : 1));
            g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            final Set<Integer> active = primary ? activeHandles(selection) : null;
            for (int i = 0; i < HANDLES.length; i++) {
                if (selection.getLive() == root && !EAST_SOUTH.contains(i)) continue;
                final Rectangle h = handle(r, i);
                final boolean resizable = active != null && active.contains(i);
                g.setStroke(new BasicStroke(1));
                g.setColor(resizable ? SELECTION : JBColor.WHITE);
                g.fillRect(h.x, h.y, h.width, h.height);
                g.setColor(resizable ? JBColor.WHITE : SELECTION);
                g.drawRect(h.x, h.y, h.width - 1, h.height - 1);
            }
        }

        private void paintFeedback(Graphics2D g) {
            if (!group.isEmpty()) {
                g.setColor(FEEDBACK);
                g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{4, 2}, 0));
                for (final Rectangle r : group.values()) g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                return;
            }
            if (dragRect != null && (resizeHandle >= 0 || drop != null && drop.placement().bounds() != null)) {
                final Rectangle r = resizeHandle >= 0 ? dragRect : toOverlay(drop.geometry(), drop.feedback().get(0));
                g.setColor(FEEDBACK);
                g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{4, 2}, 0));
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                label(g, resizeHandle >= 0 ? spanLabel != null ? spanLabel : "%d × %d".formatted(r.width, r.height) : drop.label(), r);
                return;
            }
            if (moving && drop == null && dragRect != null) {
                g.setColor(FORBIDDEN);
                g.drawRect(dragRect.x, dragRect.y, dragRect.width - 1, dragRect.height - 1);
                return;
            }
            if (drop == null) return;
            final Rectangle container = toOverlay(drop.geometry(), new Rectangle(drop.geometry().getSize()));
            g.setColor(FEEDBACK);
            g.setStroke(new BasicStroke(1));
            g.drawRect(container.x, container.y, container.width - 1, container.height - 1);
            for (final Rectangle area : drop.feedback()) {
                final Rectangle r = toOverlay(drop.geometry(), area);
                final Graphics2D fill = (Graphics2D) g.create();
                fill.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, r.width <= 3 || r.height <= 3 ? 1f : 0.25f));
                fill.fillRect(r.x, r.y, r.width, r.height);
                fill.dispose();
                if (drop.label() != null) label(g, drop.label(), r);
            }
        }

        private void paintGuides(Graphics2D g) {
            g.setColor(GUIDE);
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 2}, 0));
            for (final Rectangle guide : guides) g.drawLine(guide.x, guide.y, guide.x + guide.width - 1, guide.y + guide.height - 1);
        }

        private void label(Graphics2D g, @Nullable String text, Rectangle near) {
            if (text == null) return;
            g.setFont(JBUI.Fonts.smallFont());
            final int w = g.getFontMetrics().stringWidth(text) + 8, h = g.getFontMetrics().getHeight() + 2;
            final int x = near.x, y = Math.max(0, near.y - h - 2);
            g.setColor(new JBColor(new Color(0xFFFFE1), new Color(0x3C3F41)));
            g.fillRect(x, y, w, h);
            g.setColor(FEEDBACK);
            g.drawRect(x, y, w - 1, h - 1);
            g.setColor(UIUtil.getLabelForeground());
            g.drawString(text, x + 4, y + g.getFontMetrics().getAscent() + 1);
        }
    }
}
