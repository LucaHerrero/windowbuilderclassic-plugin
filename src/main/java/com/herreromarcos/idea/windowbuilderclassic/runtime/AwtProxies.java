package com.herreromarcos.idea.windowbuilderclassic.runtime;

import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.LayoutManager;
import java.util.Map;
import java.util.function.Function;

/**
 * Swing-Stellvertreter für AWT-Komponenten. Echte AWT-Komponenten sind schwergewichtig: im Designer würden sie als
 * native Fenster über der IDE liegen und die Maus abfangen. Die Stellvertreter sehen ähnlich aus und bieten die
 * AWT-Methoden, die Formularcode aufruft ({@code setAlignment}, {@code setState}, {@code add(String)} …); {@code setLabel}
 * erben Button und Checkbox von {@link javax.swing.AbstractButton}.
 */
public final class AwtProxies {
    private static final Map<String, Function<Object[], Component>> FACTORIES = Map.ofEntries(
            Map.entry("java.awt.Button", args -> new Button(string(args))),
            Map.entry("java.awt.Label", args -> new Label(string(args), args.length > 1 && args[1] instanceof final Integer a ? a : 0)),
            Map.entry("java.awt.Checkbox", args -> new Checkbox(string(args), args.length > 1 && args[1] instanceof final Boolean b && b)),
            Map.entry("java.awt.Choice", args -> new Choice()),
            Map.entry("java.awt.List", args -> new List()),
            Map.entry("java.awt.TextField", args -> new TextField(string(args), args.length > 0 && args[args.length - 1] instanceof final Integer c ? c : 0)),
            Map.entry("java.awt.TextArea", args -> new TextArea(string(args))),
            Map.entry("java.awt.Scrollbar", args -> new Scrollbar(args.length > 0 && args[0] instanceof final Integer o ? o : 1)),
            Map.entry("java.awt.Canvas", args -> new Canvas()),
            Map.entry("java.awt.Panel", args -> new Panel(args.length > 0 && args[0] instanceof final LayoutManager l ? l : new java.awt.FlowLayout())),
            Map.entry("java.awt.ScrollPane", args -> new ScrollPane()));

    private AwtProxies() {
    }

    public static boolean isProxied(@Nullable String className) {
        return className != null && FACTORIES.containsKey(className);
    }

    public static Component create(String className, Object[] args) {
        return FACTORIES.get(className).apply(args);
    }

    private static String string(Object[] args) {
        return args.length > 0 && args[0] instanceof final String s ? s : "";
    }

    public static final class Button extends JButton {
        public Button(String label) {
            super(label);
        }
    }

    public static final class Label extends JLabel {
        public Label(String text, int alignment) {
            super(text);
            setAlignment(alignment);
        }

        /** {@code Label.LEFT/CENTER/RIGHT} = 0/1/2. */
        public void setAlignment(int alignment) {
            setHorizontalAlignment(alignment == 1 ? SwingConstants.CENTER : alignment == 2 ? SwingConstants.RIGHT : SwingConstants.LEFT);
        }
    }

    public static final class Checkbox extends JCheckBox {
        public Checkbox(String label, boolean state) {
            super(label, state);
        }

        public void setState(boolean state) {
            setSelected(state);
        }
    }

    public static final class Choice extends JComboBox<String> {
        private final DefaultComboBoxModel<String> items = new DefaultComboBoxModel<>();

        public Choice() {
            setModel(items);
        }

        public void add(String item) {
            items.addElement(item);
        }

        @Override
        public void addItem(String item) {
            items.addElement(item);
        }

        public void select(int index) {
            setSelectedIndex(index);
        }
    }

    public static final class List extends JList<String> {
        private final DefaultListModel<String> items = new DefaultListModel<>();

        public List() {
            setModel(items);
        }

        public void add(String item) {
            items.addElement(item);
        }

        public void setMultipleMode(boolean multiple) {
            setSelectionMode(multiple ? javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : javax.swing.ListSelectionModel.SINGLE_SELECTION);
        }
    }

    public static final class TextField extends JTextField {
        public TextField(String text, int columns) {
            super(text, columns);
        }

        public void setEchoChar(char c) {
            // nur Darstellung; ein JTextField hat kein Echo-Zeichen
        }
    }

    public static final class TextArea extends JTextArea {
        public TextArea(String text) {
            super(text);
            setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.GRAY));
        }
    }

    public static final class Scrollbar extends JScrollBar {
        public Scrollbar(int orientation) {
            super(orientation == 0 ? HORIZONTAL : VERTICAL);
        }
    }

    public static final class Canvas extends JPanel {
        public Canvas() {
            super(null);
        }
    }

    public static final class Panel extends JPanel {
        public Panel(LayoutManager layout) {
            super(layout);
        }
    }

    public static final class ScrollPane extends JScrollPane {
        /** AWT: das eine Kind kommt in den sichtbaren Bereich. */
        @Override
        public Component add(Component component) {
            setViewportView(component);
            return component;
        }
    }
}
