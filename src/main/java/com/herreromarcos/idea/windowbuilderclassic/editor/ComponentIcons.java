package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

/** Kleine, gezeichnete Symbole für Palette und Components-Baum – eins pro Komponententyp, wie im WindowBuilder. */
final class ComponentIcons {
    private static final Color LINE = new JBColor(0x6C707E, 0xCED0D6);
    private static final Color ACCENT = new JBColor(0x3574F0, 0x548AF7);
    private static final Color FILL = new JBColor(0xFFFFFF, 0x2B2D30);
    private static final Map<String, Icon> CACHE = new HashMap<>();

    private ComponentIcons() {
    }

    static Icon forEntry(PaletteEntry entry) {
        return switch (entry.kind()) {
            case SELECTION -> of("#selection");
            case MARQUEE -> of("#marquee");
            case CHOOSE_COMPONENT -> of("#choose");
            case LAYOUT -> of("#layout:" + entry.title());
            case COMPONENT -> entry.factory() != null ? of("#factory:" + entry.variableBase()) : forClass(entry.className());
        };
    }

    /** Ausrichtung in der Zelle: {@code horizontal} LEFT/CENTER/RIGHT/FILL bzw. vertikal TOP/CENTER/BOTTOM/FILL. */
    static Icon alignment(boolean horizontal, String value) {
        return of("#align:%s:%s".formatted(horizontal ? "h" : "v", value));
    }

    /** Werkzeuge für Absolute Layout ({@link AbsoluteAlignment}). */
    static Icon absolute(String alignment) {
        return of("#abs:" + alignment);
    }

    /** Spalte bzw. Zeile wächst mit (Gewicht &gt; 0). */
    static Icon grow(boolean horizontal) {
        return of(horizontal ? "#grow:h" : "#grow:v");
    }

    /** AWT-Klassen bekommen das Symbol ihres Swing-Gegenstücks. */
    private static final Map<String, String> AWT = Map.ofEntries(Map.entry("Button", "JButton"), Map.entry("Label", "JLabel"),
            Map.entry("Checkbox", "JCheckBox"), Map.entry("Choice", "JComboBox"), Map.entry("List", "JList"),
            Map.entry("TextField", "JTextField"), Map.entry("TextArea", "JTextArea"), Map.entry("Scrollbar", "JScrollBar"),
            Map.entry("Panel", "JPanel"), Map.entry("Canvas", "JPanel"), Map.entry("ScrollPane", "JScrollPane"),
            Map.entry("Frame", "JFrame"), Map.entry("Dialog", "JDialog"));

    static Icon forClass(@Nullable String className) {
        final String simple = className == null ? "" : className.substring(className.lastIndexOf('.') + 1);
        if (className != null && className.startsWith("java.awt.") && AWT.containsKey(simple)) return of(AWT.get(simple));
        if ("Filler".equals(simple)) return of("#factory:horizontalStrut");
        return of(simple);
    }

    private static Icon of(String kind) {
        return CACHE.computeIfAbsent(kind, k -> new Glyph(k));
    }

    private static final class Glyph implements Icon {
        private final String kind;

        Glyph(String kind) {
            this.kind = kind;
        }

        @Override
        public int getIconWidth() {
            return JBUIScale.scale(16);
        }

        @Override
        public int getIconHeight() {
            return JBUIScale.scale(16);
        }

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            final Graphics2D g = (Graphics2D) graphics.create();
            g.translate(x, y);
            final float scale = JBUIScale.scale(1f);
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1f));
            try {
                paint(g);
            } finally {
                g.dispose();
            }
        }

        private void paint(Graphics2D g) {
            if (kind.startsWith("#abs:")) {
                paintAbsolute(g, kind.substring(5));
                return;
            }
            if (kind.startsWith("#factory:")) {
                final String what = kind.substring(9);
                g.setColor(ACCENT);
                if (what.endsWith("Box")) {
                    g.setColor(LINE);
                    g.drawRect(1, 3, 13, 9);
                    g.setColor(ACCENT);
                    if (what.startsWith("horizontal")) {
                        g.fillRect(3, 5, 3, 5);
                        g.fillRect(7, 5, 3, 5);
                    } else {
                        g.fillRect(4, 4, 7, 2);
                        g.fillRect(4, 8, 7, 2);
                    }
                } else if (what.endsWith("Strut")) {
                    final boolean horizontal = what.startsWith("horizontal");
                    if (horizontal) {
                        g.drawLine(2, 5, 2, 11);
                        g.drawLine(13, 5, 13, 11);
                        g.drawLine(2, 8, 13, 8);
                    } else {
                        g.drawLine(5, 2, 11, 2);
                        g.drawLine(5, 13, 11, 13);
                        g.drawLine(8, 2, 8, 13);
                    }
                } else if (what.endsWith("Glue") || "glue".equals(what)) {
                    // Feder
                    final boolean vertical = what.startsWith("vertical");
                    final int[] xs = {1, 3, 5, 7, 9, 11, 13, 15}, ys = {8, 5, 11, 5, 11, 5, 11, 8};
                    if (vertical) g.drawPolyline(ys, xs, xs.length);
                    else g.drawPolyline(xs, ys, xs.length);
                } else {
                    g.setColor(LINE);
                    g.drawRect(3, 3, 9, 9);
                    g.setColor(ACCENT);
                    g.drawLine(3, 3, 12, 12);
                }
                return;
            }
            if (kind.startsWith("#align:")) {
                final boolean horizontal = kind.startsWith("#align:h:");
                final String value = kind.substring(9);
                final int start = switch (value) {
                    case "LEFT", "TOP", "FILL" -> 3;
                    case "CENTER" -> 5;
                    default -> 7;
                };
                final int length = "FILL".equals(value) ? 10 : 6;
                g.setColor(LINE);
                if (horizontal) {
                    g.drawLine(1, 2, 1, 13);
                    g.drawLine(14, 2, 14, 13);
                    g.setColor(ACCENT);
                    g.fillRect(start, 6, length, 4);
                } else {
                    g.drawLine(2, 1, 13, 1);
                    g.drawLine(2, 14, 13, 14);
                    g.setColor(ACCENT);
                    g.fillRect(6, start, 4, length);
                }
                return;
            }
            if (kind.startsWith("#grow:")) {
                g.setColor(ACCENT);
                g.setStroke(new BasicStroke(1.5f));
                if (kind.endsWith("h")) {
                    g.drawLine(2, 8, 14, 8);
                    g.drawPolyline(new int[]{5, 2, 5}, new int[]{5, 8, 11}, 3);
                    g.drawPolyline(new int[]{11, 14, 11}, new int[]{5, 8, 11}, 3);
                } else {
                    g.drawLine(8, 2, 8, 14);
                    g.drawPolyline(new int[]{5, 8, 11}, new int[]{5, 2, 5}, 3);
                    g.drawPolyline(new int[]{5, 8, 11}, new int[]{11, 14, 11}, 3);
                }
                return;
            }
            if (kind.startsWith("#layout:")) {
                g.setColor(LINE);
                g.drawRect(1, 2, 13, 11);
                g.setColor(ACCENT);
                switch (kind.substring(8)) {
                    case "BorderLayout" -> {
                        g.drawLine(1, 5, 14, 5);
                        g.drawLine(1, 10, 14, 10);
                        g.drawLine(5, 5, 5, 10);
                        g.drawLine(10, 5, 10, 10);
                    }
                    case "GridLayout" -> {
                        g.drawLine(5, 2, 5, 13);
                        g.drawLine(10, 2, 10, 13);
                        g.drawLine(1, 7, 14, 7);
                    }
                    case "GridBagLayout" -> {
                        g.drawLine(6, 2, 6, 13);
                        g.drawLine(11, 2, 11, 13);
                        g.drawLine(1, 6, 14, 6);
                        g.fillRect(2, 7, 9, 3);
                    }
                    case "FlowLayout" -> {
                        g.fillRect(3, 5, 3, 2);
                        g.fillRect(7, 5, 4, 2);
                        g.fillRect(3, 9, 5, 2);
                    }
                    case "BoxLayout" -> {
                        g.fillRect(3, 5, 2, 6);
                        g.fillRect(7, 5, 2, 6);
                        g.fillRect(11, 5, 2, 6);
                    }
                    default -> {
                        g.drawRect(3, 4, 5, 3);
                        g.drawRect(6, 8, 6, 3);
                    }
                }
                return;
            }
            switch (kind) {
                case "#selection" -> {
                    g.setColor(LINE);
                    g.fillPolygon(new int[]{4, 4, 7, 9, 11, 9, 12}, new int[]{2, 13, 10, 14, 13, 9, 9}, 7);
                }
                case "#marquee" -> {
                    g.setColor(LINE);
                    g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 2}, 0));
                    g.drawRect(2, 3, 11, 9);
                }
                case "JMenuBar" -> {
                    g.setColor(LINE);
                    g.drawRect(1, 4, 13, 6);
                    g.setColor(ACCENT);
                    g.drawLine(3, 7, 5, 7);
                    g.drawLine(7, 7, 9, 7);
                }
                case "JMenu", "JPopupMenu" -> {
                    g.setColor(FILL);
                    g.fillRect(3, 2, 10, 12);
                    g.setColor(LINE);
                    g.drawRect(3, 2, 9, 11);
                    g.setColor(ACCENT);
                    if (kind.equals("JMenu")) g.fillRect(3, 2, 10, 3);
                    g.drawLine(5, 8, 10, 8);
                    g.drawLine(5, 11, 10, 11);
                }
                case "JMenuItem", "JCheckBoxMenuItem", "JRadioButtonMenuItem" -> {
                    g.setColor(LINE);
                    g.drawRect(1, 4, 13, 7);
                    g.setColor(ACCENT);
                    if (kind.equals("JCheckBoxMenuItem")) g.drawPolyline(new int[]{3, 4, 6}, new int[]{7, 9, 6}, 3);
                    else if (kind.equals("JRadioButtonMenuItem")) g.fillOval(3, 6, 3, 3);
                    g.drawLine(8, 8, 12, 8);
                }
                case "#choose" -> {
                    box(g);
                    g.setColor(ACCENT);
                    g.drawOval(5, 5, 5, 5);
                    g.drawLine(10, 10, 13, 13);
                }
                case "JButton", "JToggleButton" -> {
                    g.setColor(kind.equals("JToggleButton") ? ACCENT : FILL);
                    g.fillRoundRect(1, 4, 14, 8, 4, 4);
                    g.setColor(LINE);
                    g.drawRoundRect(1, 4, 13, 8, 4, 4);
                    g.drawLine(5, 8, 11, 8);
                }
                case "JLabel" -> text(g, "A");
                case "JTextField", "JFormattedTextField", "JPasswordField" -> {
                    g.setColor(FILL);
                    g.fillRect(1, 4, 14, 8);
                    g.setColor(LINE);
                    g.drawRect(1, 4, 13, 8);
                    g.setColor(ACCENT);
                    if (kind.equals("JPasswordField")) {
                        for (int i = 0; i < 3; i++) g.fillOval(3 + 3 * i, 7, 2, 2);
                    } else {
                        g.drawLine(4, 6, 4, 10);
                    }
                }
                case "JTextArea", "JTextPane", "JEditorPane" -> {
                    box(g);
                    g.setColor(ACCENT);
                    for (int i = 0; i < 3; i++) g.drawLine(4, 5 + 3 * i, 11 - (i == 2 ? 3 : 0), 5 + 3 * i);
                }
                case "JCheckBox" -> {
                    g.setColor(FILL);
                    g.fillRect(3, 3, 10, 10);
                    g.setColor(LINE);
                    g.drawRect(3, 3, 9, 9);
                    g.setColor(ACCENT);
                    g.setStroke(new BasicStroke(1.5f));
                    g.drawPolyline(new int[]{5, 7, 11}, new int[]{8, 10, 5}, 3);
                }
                case "JRadioButton" -> {
                    g.setColor(FILL);
                    g.fillOval(3, 3, 10, 10);
                    g.setColor(LINE);
                    g.drawOval(3, 3, 9, 9);
                    g.setColor(ACCENT);
                    g.fillOval(6, 6, 4, 4);
                }
                case "JComboBox" -> {
                    g.setColor(FILL);
                    g.fillRect(1, 4, 14, 8);
                    g.setColor(LINE);
                    g.drawRect(1, 4, 13, 8);
                    g.drawLine(10, 4, 10, 12);
                    g.setColor(ACCENT);
                    g.fillPolygon(new int[]{11, 14, 12}, new int[]{7, 7, 10}, 3);
                }
                case "JList" -> {
                    box(g);
                    g.setColor(ACCENT);
                    g.fillRect(3, 6, 10, 2);
                    g.setColor(LINE);
                    g.drawLine(4, 4, 11, 4);
                    g.drawLine(4, 10, 11, 10);
                }
                case "JTable" -> {
                    box(g);
                    g.setColor(ACCENT);
                    g.drawLine(1, 6, 14, 6);
                    g.drawLine(1, 9, 14, 9);
                    g.drawLine(6, 2, 6, 13);
                    g.drawLine(10, 2, 10, 13);
                }
                case "JTree" -> {
                    g.setColor(LINE);
                    g.drawLine(4, 3, 4, 12);
                    g.drawLine(4, 7, 8, 7);
                    g.drawLine(4, 12, 8, 12);
                    g.setColor(ACCENT);
                    g.fillRect(2, 2, 4, 3);
                    g.fillRect(9, 6, 4, 3);
                    g.fillRect(9, 11, 4, 3);
                }
                case "JSlider" -> {
                    g.setColor(LINE);
                    g.drawLine(1, 8, 14, 8);
                    g.setColor(ACCENT);
                    g.fillRect(6, 4, 3, 8);
                }
                case "JProgressBar" -> {
                    g.setColor(LINE);
                    g.drawRect(1, 5, 13, 6);
                    g.setColor(ACCENT);
                    g.fillRect(2, 6, 7, 5);
                }
                case "JScrollBar" -> {
                    g.setColor(LINE);
                    g.drawRect(5, 1, 6, 13);
                    g.setColor(ACCENT);
                    g.fillRect(6, 4, 5, 5);
                }
                case "JSeparator" -> {
                    g.setColor(LINE);
                    g.drawLine(1, 8, 14, 8);
                }
                case "JSpinner" -> {
                    g.setColor(FILL);
                    g.fillRect(1, 4, 14, 8);
                    g.setColor(LINE);
                    g.drawRect(1, 4, 13, 8);
                    g.drawLine(10, 4, 10, 12);
                    g.drawLine(10, 8, 14, 8);
                }
                case "JScrollPane" -> {
                    box(g);
                    g.setColor(ACCENT);
                    g.drawLine(11, 2, 11, 13);
                    g.fillRect(12, 4, 2, 4);
                }
                case "JSplitPane" -> {
                    box(g);
                    g.setColor(ACCENT);
                    g.fillRect(7, 2, 2, 12);
                }
                case "JTabbedPane" -> {
                    g.setColor(LINE);
                    g.drawRect(1, 5, 13, 9);
                    g.setColor(ACCENT);
                    g.fillRect(1, 2, 5, 3);
                    g.setColor(LINE);
                    g.drawRect(6, 2, 4, 3);
                }
                case "JToolBar" -> {
                    g.setColor(LINE);
                    g.drawRect(1, 4, 13, 7);
                    g.setColor(ACCENT);
                    g.fillRect(3, 6, 3, 3);
                    g.fillRect(7, 6, 3, 3);
                }
                case "JFrame", "JDialog", "JWindow", "JInternalFrame", "WindowSurrogate" -> {
                    box(g);
                    g.setColor(ACCENT);
                    g.fillRect(1, 2, 14, 3);
                }
                default -> {
                    box(g);
                    if (!kind.equals("JPanel") && !kind.isEmpty()) {
                        g.setColor(ACCENT);
                        g.fillRect(4, 5, 8, 6);
                    }
                }
            }
        }

        /** Zwei Balken und eine Bezugslinie, wie die Ausrichtungssymbole des WindowBuilder. */
        private static void paintAbsolute(Graphics2D g, String what) {
            g.setColor(ACCENT);
            switch (what) {
                case "LEFT" -> bars(g, Edge.START, 9, 6, true);
                case "CENTER" -> bars(g, Edge.MIDDLE, 10, 6, true);
                case "RIGHT" -> bars(g, Edge.END, 10, 6, true);
                case "TOP" -> bars(g, Edge.START, 9, 6, false);
                case "MIDDLE" -> bars(g, Edge.MIDDLE, 10, 6, false);
                case "BOTTOM" -> bars(g, Edge.END, 10, 6, false);
                case "SAME_WIDTH", "SAME_HEIGHT" -> {
                    final boolean width = what.equals("SAME_WIDTH");
                    if (width) {
                        g.fillRect(3, 3, 10, 4);
                        g.fillRect(3, 9, 10, 4);
                    } else {
                        g.fillRect(3, 3, 4, 10);
                        g.fillRect(9, 3, 4, 10);
                    }
                }
                case "DISTRIBUTE_HORIZONTALLY" -> {
                    for (int i = 0; i < 3; i++) g.fillRect(1 + 5 * i, 4, 3, 8);
                }
                case "DISTRIBUTE_VERTICALLY" -> {
                    for (int i = 0; i < 3; i++) g.fillRect(4, 1 + 5 * i, 8, 3);
                }
                default -> {
                    g.setColor(LINE);
                    g.drawRect(1, 1, 13, 13);
                    g.setColor(ACCENT);
                    if (what.endsWith("HORIZONTALLY")) g.fillRect(5, 5, 6, 5);
                    else g.fillRect(5, 5, 5, 6);
                    g.setColor(LINE);
                    if (what.endsWith("HORIZONTALLY")) g.drawLine(8, 1, 8, 14);
                    else g.drawLine(1, 8, 14, 8);
                }
            }
        }

        private enum Edge { START, MIDDLE, END }

        /** Bezugslinie am Anfang, in der Mitte oder am Ende; zwei Balken unterschiedlicher Länge daran ausgerichtet. */
        private static void bars(Graphics2D g, Edge edge, int first, int second, boolean vertical) {
            final int line = edge == Edge.START ? 1 : edge == Edge.END ? 14 : 8;
            g.setColor(LINE);
            if (vertical) g.drawLine(line, 1, line, 14);
            else g.drawLine(1, line, 14, line);
            g.setColor(ACCENT);
            final int a = start(edge, first), b = start(edge, second);
            if (vertical) {
                g.fillRect(a, 3, first, 4);
                g.fillRect(b, 9, second, 4);
            } else {
                g.fillRect(3, a, 4, first);
                g.fillRect(9, b, 4, second);
            }
        }

        private static int start(Edge edge, int length) {
            return edge == Edge.START ? 2 : edge == Edge.END ? 14 - length : 8 - length / 2;
        }

        private static void box(Graphics2D g) {
            g.setColor(FILL);
            g.fillRect(1, 2, 14, 12);
            g.setColor(LINE);
            g.drawRect(1, 2, 13, 11);
        }

        private static void text(Graphics2D g, String text) {
            g.setColor(LINE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            g.drawString(text, 4, 13);
        }
    }
}
