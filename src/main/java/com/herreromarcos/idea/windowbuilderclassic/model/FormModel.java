package com.herreromarcos.idea.windowbuilderclassic.model;

import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Das ausgeführte Formular: Wurzel-Komponente, Zuordnung lebender Komponenten zu ihren Infos und Warnungen. */
public final class FormModel {
    private final FormScript script;
    private final ComponentInfo root;
    private final Map<Component, ComponentInfo> byComponent = new IdentityHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public FormModel(FormScript script, ComponentInfo root) {
        this.script = script;
        this.root = root;
    }

    public FormScript getScript() {
        return script;
    }

    public ComponentInfo getRoot() {
        return root;
    }

    public void register(ComponentInfo info) {
        byComponent.put(info.getLive(), info);
    }

    public @Nullable ComponentInfo find(Component component) {
        return byComponent.get(component);
    }

    /** Sucht über den Code-Verweis (Variablenname/Accessor); dient dazu, die Auswahl über ein Neu-Parsen zu retten. */
    public @Nullable ComponentInfo findByReference(@Nullable String reference) {
        return find(root, reference);
    }

    private static @Nullable ComponentInfo find(ComponentInfo info, @Nullable String reference) {
        if (java.util.Objects.equals(info.getReference(), reference)) return info;
        for (final ComponentInfo child : info.getChildren()) {
            final ComponentInfo found = find(child, reference);
            if (found != null) return found;
        }
        return null;
    }

    public List<ComponentInfo> getAll() {
        final List<ComponentInfo> result = new ArrayList<>();
        collect(root, result);
        return result;
    }

    private static void collect(ComponentInfo info, List<ComponentInfo> result) {
        result.add(info);
        for (final ComponentInfo child : info.getChildren()) collect(child, result);
    }

    public List<String> getWarnings() {
        return warnings;
    }

    /** Größe des Formulars im Designer. */
    public Dimension getDesignSize() {
        final Dimension size = root.getLive().getSize();
        return size.width > 0 && size.height > 0 ? size : new Dimension(450, 300);
    }
}
