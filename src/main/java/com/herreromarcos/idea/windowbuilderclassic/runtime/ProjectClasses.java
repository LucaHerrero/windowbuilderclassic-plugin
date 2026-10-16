package com.herreromarcos.idea.windowbuilderclassic.runtime;

import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lädt Klassen des Projekts (kompilierte Ausgabe und Bibliotheken des Moduls), um eigene Komponenten und Layouts wie
 * MigLayout echt darzustellen – wie der WindowBuilder, der den Code des Projekts ebenfalls ausführt. Parent ist der
 * Plattform-Loader: Projektcode sieht das JDK, aber keine Klassen der IDE.
 */
public final class ProjectClasses {
    private final List<String> classPath;
    private final long generation;
    private final URLClassLoader loader;

    public ProjectClasses(List<String> classPath, long generation) {
        this.classPath = List.copyOf(classPath);
        this.generation = generation;
        final List<URL> urls = new ArrayList<>();
        for (final String path : classPath) {
            try {
                urls.add(new File(path).toURI().toURL());
            } catch (final MalformedURLException ignored) {
                // Pfad ohne gültige URL: auslassen
            }
        }
        loader = new URLClassLoader("windowbuilder-project", urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader());
    }

    /** Wiederverwendbar, solange sich weder Classpath noch Kompilierstand geändert haben. */
    public boolean matches(List<String> classPath, long generation) {
        return this.generation == generation && this.classPath.equals(classPath);
    }

    /** Eine Klasse aus dem Projekt bzw. seinen Bibliotheken; JDK-Klassen gehen über {@link com.herreromarcos.idea.windowbuilderclassic.parser.TypeResolver}. */
    public @Nullable Class<?> load(@Nullable String className) {
        if (className == null || className.startsWith("java.") || className.startsWith("javax.")) return null;
        String name = className;
        while (true) {
            try {
                return Class.forName(name, true, loader);
            } catch (final ClassNotFoundException | LinkageError e) {
                // Verschachtelte Klasse: a.b.Outer.Inner → a.b.Outer$Inner
                final int dot = name.lastIndexOf('.');
                if (dot < 0 || name.substring(0, dot).chars().noneMatch(Character::isUpperCase)) return null;
                name = name.substring(0, dot) + '$' + name.substring(dot + 1);
            }
        }
    }

    public void close() {
        try {
            loader.close();
        } catch (final java.io.IOException ignored) {
            // nichts zu tun
        }
    }
}
