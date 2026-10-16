package com.herreromarcos.idea.windowbuilderclassic.palette;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ein Eintrag der Palette.
 *
 * @param className      voll qualifizierte Klasse der Komponente bzw. des Layouts
 * @param creationArgs   Argumente des Konstruktors im erzeugten Code
 * @param field          ob der WindowBuilder die Variable als Feld anlegt (Textfelder)
 * @param afterAdd       Aufrufe, die nach dem {@code add} folgen, ohne Empfänger (z.B. {@code setColumns(10)})
 * @param layoutTemplate für Layouts: Ausdruck für {@code setLayout}; {@code %s} steht für den Container
 * @param factory        statt {@code new}: Fabrikaufruf, z.B. {@code javax.swing.Box.createHorizontalStrut(20)}
 * @param declaredType   Typ der Variablen, falls nicht {@code className} (bei Fabriken {@code java.awt.Component})
 * @param variableBase   Variablenname, falls nicht aus Klasse und Text abgeleitet ({@code horizontalStrut})
 */
public record PaletteEntry(Kind kind, String title, @Nullable String className, String creationArgs, boolean field,
                           List<String> afterAdd, @Nullable String layoutTemplate, @Nullable String factory,
                           @Nullable String declaredType, @Nullable String variableBase) {
    public enum Kind {
        SELECTION, MARQUEE, CHOOSE_COMPONENT, COMPONENT, LAYOUT
    }

    public static PaletteEntry tool(Kind kind, String title) {
        return new PaletteEntry(kind, title, null, "", false, List.of(), null, null, null, null);
    }

    public static PaletteEntry component(String className, String creationArgs) {
        return new PaletteEntry(Kind.COMPONENT, simple(className), className, creationArgs, false, List.of(), null, null, null, null);
    }

    public static PaletteEntry field(String className, String creationArgs, String... afterAdd) {
        return new PaletteEntry(Kind.COMPONENT, simple(className), className, creationArgs, true, List.of(afterAdd), null, null, null, null);
    }

    public static PaletteEntry component(String className, String creationArgs, String... afterAdd) {
        return new PaletteEntry(Kind.COMPONENT, simple(className), className, creationArgs, false, List.of(afterAdd), null, null, null, null);
    }

    /** Komponente aus einer Fabrikmethode, z.B. die Struts &amp; Springs von {@code Box}. */
    public static PaletteEntry factory(String title, String className, String declaredType, String factory, String variableBase) {
        return new PaletteEntry(Kind.COMPONENT, title, className, "", false, List.of(), null, factory, declaredType, variableBase);
    }

    public static PaletteEntry layout(String title, @Nullable String className, String template) {
        return new PaletteEntry(Kind.LAYOUT, title, className, "", false, List.of(), template, null, null, null);
    }

    public String layoutText(String containerReference) {
        return layoutTemplate == null ? "null" : layoutTemplate.formatted(containerReference);
    }

    public boolean isAbsoluteLayout() {
        return kind == Kind.LAYOUT && className == null;
    }

    public String simpleName() {
        return className == null ? title : simple(className);
    }

    /** Ausdruck, der die Komponente erzeugt. */
    public String creationText() {
        return factory != null ? factory : "new %s(%s)".formatted(className, creationArgs);
    }

    public String variableType() {
        return declaredType != null ? declaredType : String.valueOf(className);
    }

    private static String simple(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
