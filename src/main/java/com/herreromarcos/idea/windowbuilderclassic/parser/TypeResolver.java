package com.herreromarcos.idea.windowbuilderclassic.parser;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.util.HashSet;
import java.util.Set;

/**
 * Löst Klassennamen auf. Zuerst über PSI; klappt das nicht (kein JDK im Modul konfiguriert, Index noch nicht fertig),
 * über die Imports der Datei und die JDK-Klassen der laufenden IDE. Geladen werden nur Klassen aus {@code java.*} und
 * {@code javax.*} über den Bootstrap-Loader – niemals Projekt- oder IDE-Code.
 */
public final class TypeResolver {
    private TypeResolver() {
    }

    public static @Nullable Class<?> jdkClass(@Nullable String qualifiedName) {
        if (qualifiedName == null || !(qualifiedName.startsWith("java.") || qualifiedName.startsWith("javax."))) return null;
        String name = qualifiedName;
        while (true) {
            try {
                return Class.forName(name, false, null);
            } catch (final ClassNotFoundException | LinkageError e) {
                // Verschachtelte Klasse? javax.swing.border.TitledBorder bleibt, Map.Entry → Map$Entry
                final int dot = name.lastIndexOf('.');
                if (dot < 0 || !Character.isUpperCase(name.charAt(dot + 1)) || name.indexOf('.') == dot) return null;
                final String outer = name.substring(0, dot);
                if (outer.chars().noneMatch(Character::isUpperCase)) return null;
                name = outer + '$' + name.substring(dot + 1);
            }
        }
    }

    /** Voll qualifizierter Name einer Klassenreferenz, oder {@code null}. */
    public static @Nullable String qualifiedName(@Nullable PsiJavaCodeReferenceElement reference) {
        if (reference == null) return null;
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof final PsiClass cls) return cls.getQualifiedName();
        return byImports(reference.getContainingFile(), reference.getQualifiedName() != null && reference.isQualified()
                ? reference.getQualifiedName() : reference.getReferenceName());
    }

    /** Klassenname aus Quelltext (einfach oder qualifiziert) über die Imports der Datei. */
    public static @Nullable String byImports(@Nullable PsiFile file, @Nullable String name) {
        if (name == null) return null;
        final String text = name.replaceAll("<.*>", "").trim();
        if (text.contains(".") && jdkClass(text) != null) return text;
        final String simple = text.contains(".") ? text.substring(0, text.indexOf('.')) : text;
        final String rest = text.substring(simple.length());
        if (file instanceof final PsiJavaFile javaFile) {
            final PsiImportList imports = javaFile.getImportList();
            if (imports != null) {
                for (final PsiImportStatement statement : imports.getImportStatements()) {
                    final String imported = statement.getQualifiedName();
                    if (imported == null) continue;
                    if (statement.isOnDemand()) {
                        final String candidate = imported + "." + text;
                        if (jdkClass(candidate) != null) return candidate;
                    } else if (imported.endsWith("." + simple)) {
                        return imported + rest;
                    }
                }
            }
        }
        final String lang = "java.lang." + text;
        return jdkClass(lang) != null ? lang : null;
    }

    /** Ob eine JDK-Klasse eine AWT/Swing-Komponente ist. */
    public static boolean isJdkComponent(@Nullable String qualifiedName) {
        final Class<?> cls = jdkClass(qualifiedName);
        return cls != null && Component.class.isAssignableFrom(cls);
    }

    /** Die nächste JDK-Oberklasse von {@code cls}, die eine Komponente ist (bei JDK-Klassen die Klasse selbst). */
    public static @Nullable String jdkComponentAncestor(@Nullable PsiClass cls) {
        final Set<PsiClass> visited = new HashSet<>();
        PsiClass current = cls;
        while (current != null && visited.add(current)) {
            final String name = current.getQualifiedName();
            if (name != null && (name.startsWith("java.") || name.startsWith("javax."))) {
                return isJdkComponent(name) ? name : null;
            }
            final PsiJavaCodeReferenceElement[] supers = current.getExtendsList() == null
                    ? PsiJavaCodeReferenceElement.EMPTY_ARRAY : current.getExtendsList().getReferenceElements();
            if (supers.length == 0) return null;
            final PsiElement resolved = supers[0].resolve();
            if (resolved instanceof final PsiClass superClass) {
                current = superClass;
            } else {
                final String byImport = qualifiedName(supers[0]);
                return isJdkComponent(byImport) ? byImport : null;
            }
        }
        return null;
    }
}
