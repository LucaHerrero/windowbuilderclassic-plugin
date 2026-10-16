package com.herreromarcos.idea.windowbuilderclassic.runtime;

import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Steht im Designer für ein JFrame, JDialog oder JWindow: echte Fenster lassen sich weder ohne Bildschirm erzeugen noch
 * in einen Editor einbetten. Wie bei einem Fenster schließen die Bounds die Titelleiste ein; darunter liegt eine echte
 * {@link JRootPane} mit Content Pane und Menüleiste. Die öffentlichen Methoden spiegeln die der Fensterklassen, damit
 * Aufrufe wie {@code setTitle} per Reflection ankommen.
 */
public final class WindowSurrogate extends JPanel {
    private final String windowClass;
    private final JRootPane rootPane = new JRootPane();
    private final TitleBar titleBar;
    private String title = "";
    private boolean resizable = true;
    private int defaultCloseOperation = 1;

    public WindowSurrogate(String windowClass) {
        super(new BorderLayout());
        this.windowClass = windowClass;
        titleBar = new TitleBar();
        if (!windowClass.endsWith("JWindow") && !windowClass.equals("java.awt.Window")) add(titleBar, BorderLayout.NORTH);
        add(rootPane, BorderLayout.CENTER);
        setBorder(BorderFactory.createLineBorder(new Color(0x7a7a7a)));
    }

    public String getWindowClass() {
        return windowClass;
    }

    public Container getContentPane() {
        return rootPane.getContentPane();
    }

    public void setContentPane(Container contentPane) {
        rootPane.setContentPane(contentPane);
    }

    @Override
    public JRootPane getRootPane() {
        return rootPane;
    }

    public Component getGlassPane() {
        return rootPane.getGlassPane();
    }

    public javax.swing.JLayeredPane getLayeredPane() {
        return rootPane.getLayeredPane();
    }

    public void setJMenuBar(@Nullable JMenuBar menuBar) {
        rootPane.setJMenuBar(menuBar);
    }

    public @Nullable JMenuBar getJMenuBar() {
        return rootPane.getJMenuBar();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(@Nullable String title) {
        this.title = title == null ? "" : title;
        titleBar.label.setText(this.title);
    }

    public boolean isResizable() {
        return resizable;
    }

    public void setResizable(boolean resizable) {
        this.resizable = resizable;
    }

    public int getDefaultCloseOperation() {
        return defaultCloseOperation;
    }

    public void setDefaultCloseOperation(int operation) {
        defaultCloseOperation = operation;
    }

    /** Titelleiste im neutralen Stil; die Fensterknöpfe sind nur gezeichnet. */
    private static final class TitleBar extends JComponent {
        private final JLabel label = new JLabel();

        TitleBar() {
            setLayout(new BorderLayout());
            label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
            label.setFont(label.getFont().deriveFont(Font.PLAIN));
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(100, 26);
        }

        @Override
        protected void paintComponent(Graphics g) {
            final Color base = UIManager.getColor("Panel.background");
            g.setColor(base == null ? new Color(0xe8e8e8) : base.darker());
            g.fillRect(0, 0, getWidth(), getHeight());
            final Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(label.getForeground());
            final int y = getHeight() / 2;
            int x = getWidth() - 18;
            g2.drawLine(x - 4, y - 4, x + 4, y + 4);
            g2.drawLine(x - 4, y + 4, x + 4, y - 4);
            x -= 26;
            g2.drawRect(x - 4, y - 4, 8, 8);
            x -= 26;
            g2.drawLine(x - 4, y + 3, x + 4, y + 3);
            g2.dispose();
        }
    }
}
