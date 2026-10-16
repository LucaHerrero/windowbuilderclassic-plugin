package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;

import java.awt.Container;
import java.awt.GridBagLayout;

/** Kleine Testhilfe ohne Zugriff auf Paket-Interna des Codegenerators. */
final class GridBagWriterAccess {
    private GridBagWriterAccess() {
    }

    static boolean isGridBag(ComponentInfo container) {
        return container.getLive() instanceof final Container c && c.getLayout() instanceof GridBagLayout;
    }
}
