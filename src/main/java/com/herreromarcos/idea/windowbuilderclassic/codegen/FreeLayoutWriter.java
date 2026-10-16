package com.herreromarcos.idea.windowbuilderclassic.codegen;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.ObjectInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.PropertyInfo;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.GroupLayout;
import javax.swing.SpringLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GroupLayout und SpringLayout als „freies Design“: Komponenten werden wie im Absolute Layout platziert, der Layout-Code
 * entsteht aus den Positionen neu – bei GroupLayout je Komponente eine Gruppe mit Abstand und Größe
 * ({@code gl_contentPane.setHorizontalGroup(...)}), bei SpringLayout {@code putConstraint} an NORTH/WEST des Containers.
 */
final class FreeLayoutWriter {
    private static final String GL = "javax.swing.GroupLayout";
    private static final String SL = "javax.swing.SpringLayout";
    private static final Set<String> GENERATED = Set.of("setHorizontalGroup", "setVerticalGroup", "putConstraint");

    private final FormWriter writer;

    FreeLayoutWriter(FormWriter writer) {
        this.writer = writer;
    }

    static boolean isFree(@Nullable ComponentInfo container) {
        return container != null && container.getLive() instanceof final Container c
                && (c.getLayout() instanceof GroupLayout || c.getLayout() instanceof SpringLayout);
    }

    static boolean isGroup(ComponentInfo container) {
        return container.getLive() instanceof final Container c && c.getLayout() instanceof GroupLayout;
    }

    /** Variablenname für ein neues Layout wie im WindowBuilder: {@code gl_contentPane}, {@code sl_panel}. */
    String newVariable(ComponentInfo container, boolean group) {
        final String prefix = group ? "gl_" : "sl_";
        return writer.uniqueName(container.getName() == null ? (group ? "groupLayout" : "springLayout") : prefix + container.getName());
    }

    static String declaration(boolean group, String variable, String containerReference) {
        return group ? "%s %s = new %s(%s);".formatted(GL, variable, GL, containerReference)
                : "%s %s = new %s();".formatted(SL, variable, SL);
    }

    /** Löscht den bisher erzeugten Layout-Code (Gruppen bzw. putConstraint). */
    void deleteGenerated(ComponentInfo container) {
        final ObjectInfo layout = container.getLayoutObject();
        if (layout == null) return;
        for (final PropertyInfo call : layout.getCalls()) {
            if (!GENERATED.contains(call.method())) continue;
            final PsiElement statement = FormWriter.element(call.statement());
            if (statement != null && statement.isValid()) statement.delete();
        }
    }

    /** Bounds aller Kinder (Container-Koordinaten), außer {@code without}; {@code overrides} ersetzt einzelne. */
    static Map<String, Rectangle> currentBounds(ComponentInfo container, Map<ComponentInfo, Rectangle> overrides, Set<ComponentInfo> without) {
        final Map<String, Rectangle> result = new LinkedHashMap<>();
        for (final ComponentInfo child : container.getChildren()) {
            if (without.contains(child) || child.getName() == null) continue;
            if (child.getAssociation() != Association.ADD && child.getAssociation() != Association.GROUP) continue;
            result.put(child.getName(), overrides.getOrDefault(child, child.getLive().getBounds()));
        }
        return result;
    }

    static Map<String, Dimension> preferredSizes(ComponentInfo container) {
        final Map<String, Dimension> result = new LinkedHashMap<>();
        for (final ComponentInfo child : container.getChildren()) {
            if (child.getName() != null) result.put(child.getName(), child.getLive().getPreferredSize());
        }
        return result;
    }

    /** Der Layout-Code für die Komponenten an ihren Positionen. */
    List<String> lines(ComponentInfo container, boolean group, String variable, Map<String, Rectangle> bounds, Map<String, Dimension> preferred) {
        final Insets insets = ((Container) container.getLive()).getInsets();
        final String reference = container.getReference() == null || container.getReference().isEmpty() ? "this" : container.getReference();
        return group ? groupLines(container, variable, bounds, insets) : springLines(variable, reference, bounds, preferred, insets);
    }

    private static List<String> groupLines(ComponentInfo container, String gl, Map<String, Rectangle> bounds, Insets insets) {
        final StringBuilder horizontal = new StringBuilder("%s.setHorizontalGroup(%s.createParallelGroup(%s.Alignment.LEADING)".formatted(gl, gl, GL));
        final StringBuilder vertical = new StringBuilder("%s.setVerticalGroup(%s.createParallelGroup(%s.Alignment.LEADING)".formatted(gl, gl, GL));
        for (final Map.Entry<String, Rectangle> entry : bounds.entrySet()) {
            final Rectangle r = entry.getValue();
            horizontal.append("\n.addGroup(%s.createSequentialGroup().addGap(%d).addComponent(%s, %s.PREFERRED_SIZE, %d, %s.PREFERRED_SIZE))"
                    .formatted(gl, Math.max(0, r.x - insets.left), entry.getKey(), GL, r.width, GL));
            vertical.append("\n.addGroup(%s.createSequentialGroup().addGap(%d).addComponent(%s, %s.PREFERRED_SIZE, %d, %s.PREFERRED_SIZE))"
                    .formatted(gl, Math.max(0, r.y - insets.top), entry.getKey(), GL, r.height, GL));
        }
        if (bounds.isEmpty()) {
            final java.awt.Dimension size = container.getLive().getSize();
            horizontal.append(".addGap(0, %d, Short.MAX_VALUE)".formatted(Math.max(size.width, 1)));
            vertical.append(".addGap(0, %d, Short.MAX_VALUE)".formatted(Math.max(size.height, 1)));
        }
        return List.of(horizontal.append(");").toString(), vertical.append(");").toString());
    }

    private static List<String> springLines(String sl, String container, Map<String, Rectangle> bounds, Map<String, Dimension> preferred, Insets insets) {
        final List<String> lines = new ArrayList<>();
        for (final Map.Entry<String, Rectangle> entry : bounds.entrySet()) {
            final String name = entry.getKey();
            final Rectangle r = entry.getValue();
            final int x = r.x - insets.left, y = r.y - insets.top;
            lines.add("%s.putConstraint(%s.NORTH, %s, %d, %s.NORTH, %s);".formatted(sl, SL, name, y, SL, container));
            lines.add("%s.putConstraint(%s.WEST, %s, %d, %s.WEST, %s);".formatted(sl, SL, name, x, SL, container));
            final Dimension size = preferred.get(name);
            if (size != null && size.height != r.height) {
                lines.add("%s.putConstraint(%s.SOUTH, %s, %d, %s.NORTH, %s);".formatted(sl, SL, name, y + r.height, SL, container));
            }
            if (size != null && size.width != r.width) {
                lines.add("%s.putConstraint(%s.EAST, %s, %d, %s.WEST, %s);".formatted(sl, SL, name, x + r.width, SL, container));
            }
        }
        return lines;
    }
}
