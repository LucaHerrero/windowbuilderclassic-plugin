package com.herreromarcos.idea.windowbuilderclassic.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Einstellungen der Codegenerierung je Projekt, wie in den Preferences des WindowBuilder
 * (<i>Code Generation</i>, <i>Swing → Layouts</i>, <i>Event handlers</i>).
 */
@Service(Service.Level.PROJECT)
@State(name = "WindowBuilderClassic", storages = @Storage("windowbuilderclassic.xml"))
public final class WindowBuilderSettings implements PersistentStateComponent<WindowBuilderSettings.State> {
    public static final class State {
        /** Neue Komponenten als Felder statt lokale Variablen (WindowBuilder: „Variable generation: Field“). */
        public boolean variablesAsFields;
        /** Code neuer Komponenten in eigene Blöcke {@code { … }} (WindowBuilder: „Statement generation: Block“). */
        public boolean useBlocks;
        /** Listener mit einer Methode als Lambda statt anonymer Klasse. */
        public boolean lambdaListeners;
        /** GridBagLayout: Abstände über insets erzeugen. */
        public boolean gridBagGaps = true;
        /** GridBagLayout: Größe der Abstände in Pixeln. */
        public int gridBagGap = 5;
        /** Projektklassen (eigene Komponenten, MigLayout …) im Designer ausführen, um sie echt darzustellen. */
        public boolean runProjectCode = true;
    }

    private State state = new State();

    public static WindowBuilderSettings getInstance(Project project) {
        return project.getService(WindowBuilderSettings.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }
}
