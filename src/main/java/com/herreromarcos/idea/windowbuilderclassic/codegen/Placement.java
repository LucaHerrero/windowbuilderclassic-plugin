package com.herreromarcos.idea.windowbuilderclassic.codegen;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import org.jetbrains.annotations.Nullable;

import java.awt.Rectangle;

/**
 * Wohin eine Komponente kommt.
 *
 * @param constraints Quelltext des zweiten {@code add}-Arguments (z.B. {@code java.awt.BorderLayout.NORTH})
 * @param bounds      bei Absolute Layout die Bounds für {@code setBounds}
 * @param before      Geschwister, vor dem eingefügt wird (Reihenfolge der {@code add}-Aufrufe = Index); {@code null} = ans Ende
 * @param cell        bei GridBagLayout die Zielzelle
 */
public record Placement(Association association, @Nullable String constraints, @Nullable Rectangle bounds,
                        @Nullable ComponentInfo before, @Nullable GridCell cell) {
    /**
     * Zelle im GridBagLayout. {@code insertColumn}/{@code insertRow}: vor Spalte {@code x} bzw. Zeile {@code y} wird eine
     * neue eingefügt (abgelegt auf einer Gitterlinie, wie im WindowBuilder).
     */
    public record GridCell(int x, int y, boolean insertColumn, boolean insertRow) {}

    public static Placement append() {
        return new Placement(Association.ADD, null, null, null, null);
    }

    public static Placement absolute(Rectangle bounds) {
        return new Placement(Association.ADD, null, bounds, null, null);
    }

    public static Placement constraints(String constraints) {
        return new Placement(Association.ADD, constraints, null, null, null);
    }

    public static Placement before(@Nullable ComponentInfo sibling) {
        return new Placement(Association.ADD, null, null, sibling, null);
    }

    public static Placement of(Association association) {
        return new Placement(association, null, null, null, null);
    }

    public static Placement cell(int x, int y, boolean insertColumn, boolean insertRow) {
        return new Placement(Association.ADD, null, null, null, new GridCell(x, y, insertColumn, insertRow));
    }

    public Placement withConstraints(String constraints) {
        return new Placement(association, constraints, bounds, before, cell);
    }
}
