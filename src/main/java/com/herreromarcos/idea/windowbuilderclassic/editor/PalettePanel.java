package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Die Palette: Kategorien als Überschriften, darunter die Einträge. Ein Klick wählt das Werkzeug, wie im WindowBuilder. */
final class PalettePanel extends JPanel {
    private final DefaultListModel<Object> items = new DefaultListModel<>();
    private final JBList<Object> list = new JBList<>(items);
    private boolean updating;

    PalettePanel(Consumer<PaletteEntry> onSelect) {
        super(new BorderLayout());
        for (final Map.Entry<String, List<PaletteEntry>> category : Palette.categories().entrySet()) {
            items.addElement(category.getKey());
            for (final PaletteEntry entry : category.getValue()) items.addElement(entry);
        }
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<?> l, Object value, int index, boolean selected, boolean hasFocus) {
                if (value instanceof final String category) {
                    append(category, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES);
                    setBorder(JBUI.Borders.empty(index == 0 ? 2 : 8, 4, 2, 4));
                } else if (value instanceof final PaletteEntry entry) {
                    setIcon(ComponentIcons.forEntry(entry));
                    append(entry.title());
                    setBorder(JBUI.Borders.empty(1, 12, 1, 4));
                }
            }
        });
        list.addListSelectionListener(e -> {
            if (updating || e.getValueIsAdjusting()) return;
            final Object value = list.getSelectedValue();
            if (value instanceof final PaletteEntry entry) {
                onSelect.accept(entry);
            } else if (value instanceof String) {
                select(null);
            }
        });
        list.setBackground(UIUtil.getListBackground());
        add(ScrollPaneFactory.createScrollPane(list, true), BorderLayout.CENTER);
        select(null);
    }

    /** Layouts aus Bibliotheken des Moduls (MigLayout, FormLayout) als eigene Kategorie hinter den Layouts. */
    void setThirdParty(List<PaletteEntry> entries) {
        final int existing = items.indexOf(Palette.THIRD_PARTY);
        if (existing >= 0) {
            int end = existing + 1;
            while (end < items.size() && items.get(end) instanceof PaletteEntry) end++;
            if (List.copyOf(java.util.Collections.list(items.elements())).subList(existing + 1, end).equals(entries)) return;
            items.removeRange(existing, end - 1);
        }
        if (entries.isEmpty()) return;
        int index = items.indexOf(Palette.categories().keySet().stream().toList().get(3));
        if (index < 0) index = items.size();
        updating = true;
        try {
            items.add(index++, Palette.THIRD_PARTY);
            for (final PaletteEntry entry : entries) items.add(index++, entry);
        } finally {
            updating = false;
        }
    }

    /** Markiert den Eintrag ohne Rückmeldung; {@code null} = Selection. */
    void select(@Nullable PaletteEntry entry) {
        updating = true;
        try {
            list.setSelectedValue(entry == null ? Palette.SELECTION : entry, true);
        } finally {
            updating = false;
        }
    }
}
