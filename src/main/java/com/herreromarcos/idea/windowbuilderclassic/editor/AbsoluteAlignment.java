package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Die Ausrichtungswerkzeuge des WindowBuilder für Absolute Layout. Bezug ist die Hauptauswahl (erste Komponente);
 * Zentrieren bezieht sich auf den Container, Verteilen auf die äußeren Komponenten.
 */
enum AbsoluteAlignment {
    LEFT("Align Left Edges", 2),
    CENTER("Align Horizontal Centers", 2),
    RIGHT("Align Right Edges", 2),
    TOP("Align Top Edges", 2),
    MIDDLE("Align Vertical Centers", 2),
    BOTTOM("Align Bottom Edges", 2),
    SAME_WIDTH("Replicate Width", 2),
    SAME_HEIGHT("Replicate Height", 2),
    DISTRIBUTE_HORIZONTALLY("Space Equally, Horizontal", 3),
    DISTRIBUTE_VERTICALLY("Space Equally, Vertical", 3),
    CENTER_IN_WINDOW_HORIZONTALLY("Center Horizontally in Window", 1),
    CENTER_IN_WINDOW_VERTICALLY("Center Vertically in Window", 1);

    final String title;
    final int minimum;

    AbsoluteAlignment(String title, int minimum) {
        this.title = title;
        this.minimum = minimum;
    }

    /** Neue Bounds je Komponente (Container-Koordinaten); unveränderte bleiben drin, schaden aber nicht. */
    Map<ComponentInfo, Rectangle> apply(List<ComponentInfo> selected, Dimension container) {
        final Map<ComponentInfo, Rectangle> result = new LinkedHashMap<>();
        for (final ComponentInfo info : selected) result.put(info, new Rectangle(info.getLive().getBounds()));
        final Rectangle reference = result.get(selected.get(0));
        switch (this) {
            case LEFT -> result.values().forEach(r -> r.x = reference.x);
            case CENTER -> result.values().forEach(r -> r.x = reference.x + (reference.width - r.width) / 2);
            case RIGHT -> result.values().forEach(r -> r.x = reference.x + reference.width - r.width);
            case TOP -> result.values().forEach(r -> r.y = reference.y);
            case MIDDLE -> result.values().forEach(r -> r.y = reference.y + (reference.height - r.height) / 2);
            case BOTTOM -> result.values().forEach(r -> r.y = reference.y + reference.height - r.height);
            case SAME_WIDTH -> result.values().forEach(r -> r.width = reference.width);
            case SAME_HEIGHT -> result.values().forEach(r -> r.height = reference.height);
            case DISTRIBUTE_HORIZONTALLY -> distribute(new ArrayList<>(result.values()), true);
            case DISTRIBUTE_VERTICALLY -> distribute(new ArrayList<>(result.values()), false);
            case CENTER_IN_WINDOW_HORIZONTALLY -> {
                final Rectangle union = union(result.values());
                final int dx = (container.width - union.width) / 2 - union.x;
                result.values().forEach(r -> r.x += dx);
            }
            case CENTER_IN_WINDOW_VERTICALLY -> {
                final Rectangle union = union(result.values());
                final int dy = (container.height - union.height) / 2 - union.y;
                result.values().forEach(r -> r.y += dy);
            }
        }
        return result;
    }

    /** Gleiche Abstände zwischen den Komponenten; die äußeren bleiben stehen. */
    private static void distribute(List<Rectangle> rectangles, boolean horizontal) {
        rectangles.sort(Comparator.comparingInt(r -> horizontal ? r.x : r.y));
        final Rectangle first = rectangles.get(0), last = rectangles.get(rectangles.size() - 1);
        int total = 0;
        for (final Rectangle r : rectangles) total += horizontal ? r.width : r.height;
        final int span = horizontal ? last.x + last.width - first.x : last.y + last.height - first.y;
        final int gap = (span - total) / (rectangles.size() - 1);
        int position = horizontal ? first.x : first.y;
        for (final Rectangle r : rectangles) {
            if (horizontal) r.x = position;
            else r.y = position;
            position += (horizontal ? r.width : r.height) + gap;
        }
    }

    private static Rectangle union(Iterable<Rectangle> rectangles) {
        Rectangle union = null;
        for (final Rectangle r : rectangles) union = union == null ? new Rectangle(r) : union.union(r);
        return union == null ? new Rectangle() : union;
    }
}
