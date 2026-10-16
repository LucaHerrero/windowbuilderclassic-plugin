package com.herreromarcos.idea.windowbuilderclassic.codegen;

import org.jetbrains.annotations.Nullable;

import java.awt.GridBagConstraints;

/**
 * Ausrichtung in der Zelle, wie sie der WindowBuilder für GridBagLayout anbietet. Horizontal und vertikal ergeben
 * zusammen {@code anchor} und {@code fill}: links + oben = {@code NORTHWEST}, horizontal füllen = {@code HORIZONTAL} …
 */
public final class GridBagAlignment {
    public enum Horizontal { LEFT, CENTER, RIGHT, FILL }

    public enum Vertical { TOP, CENTER, BOTTOM, FILL }

    private GridBagAlignment() {
    }

    public static Horizontal horizontal(GridBagConstraints c) {
        if (c.fill == GridBagConstraints.HORIZONTAL || c.fill == GridBagConstraints.BOTH) return Horizontal.FILL;
        return switch (c.anchor) {
            case GridBagConstraints.NORTHWEST, GridBagConstraints.WEST, GridBagConstraints.SOUTHWEST,
                 GridBagConstraints.FIRST_LINE_START, GridBagConstraints.LINE_START, GridBagConstraints.LAST_LINE_START,
                 GridBagConstraints.BASELINE_LEADING, GridBagConstraints.ABOVE_BASELINE_LEADING,
                 GridBagConstraints.BELOW_BASELINE_LEADING -> Horizontal.LEFT;
            case GridBagConstraints.NORTHEAST, GridBagConstraints.EAST, GridBagConstraints.SOUTHEAST,
                 GridBagConstraints.FIRST_LINE_END, GridBagConstraints.LINE_END, GridBagConstraints.LAST_LINE_END,
                 GridBagConstraints.BASELINE_TRAILING, GridBagConstraints.ABOVE_BASELINE_TRAILING,
                 GridBagConstraints.BELOW_BASELINE_TRAILING -> Horizontal.RIGHT;
            default -> Horizontal.CENTER;
        };
    }

    public static Vertical vertical(GridBagConstraints c) {
        if (c.fill == GridBagConstraints.VERTICAL || c.fill == GridBagConstraints.BOTH) return Vertical.FILL;
        return switch (c.anchor) {
            case GridBagConstraints.NORTHWEST, GridBagConstraints.NORTH, GridBagConstraints.NORTHEAST,
                 GridBagConstraints.FIRST_LINE_START, GridBagConstraints.PAGE_START, GridBagConstraints.FIRST_LINE_END,
                 GridBagConstraints.ABOVE_BASELINE, GridBagConstraints.ABOVE_BASELINE_LEADING,
                 GridBagConstraints.ABOVE_BASELINE_TRAILING -> Vertical.TOP;
            case GridBagConstraints.SOUTHWEST, GridBagConstraints.SOUTH, GridBagConstraints.SOUTHEAST,
                 GridBagConstraints.LAST_LINE_START, GridBagConstraints.PAGE_END, GridBagConstraints.LAST_LINE_END,
                 GridBagConstraints.BELOW_BASELINE, GridBagConstraints.BELOW_BASELINE_LEADING,
                 GridBagConstraints.BELOW_BASELINE_TRAILING -> Vertical.BOTTOM;
            default -> Vertical.CENTER;
        };
    }

    /** {@code anchor}-Konstante (ohne Klasse) oder {@code null} für den Standard CENTER. */
    static @Nullable String anchor(Horizontal h, Vertical v) {
        final String row = v == Vertical.TOP ? "NORTH" : v == Vertical.BOTTOM ? "SOUTH" : "";
        final String column = h == Horizontal.LEFT ? "WEST" : h == Horizontal.RIGHT ? "EAST" : "";
        final String anchor = row + column;
        return anchor.isEmpty() ? null : anchor;
    }

    /** {@code fill}-Konstante (ohne Klasse) oder {@code null} für den Standard NONE. */
    static @Nullable String fill(Horizontal h, Vertical v) {
        if (h == Horizontal.FILL && v == Vertical.FILL) return "BOTH";
        if (h == Horizontal.FILL) return "HORIZONTAL";
        if (v == Vertical.FILL) return "VERTICAL";
        return null;
    }
}
