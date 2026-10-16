package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.codegen.FormWriter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** „Externalize Strings“ wie im WindowBuilder: welche Texte, unter welchem Schlüssel, in welches Bundle. */
final class ExternalizeDialog extends DialogWrapper {
    private final List<FormWriter.Externalizable> items;
    private final boolean[] selected;
    private final String[] keys;
    private final JBTextField bundle;

    ExternalizeDialog(Project project, List<FormWriter.Externalizable> items, String defaultBundle) {
        super(project);
        this.items = items;
        selected = new boolean[items.size()];
        keys = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            selected[i] = true;
            keys[i] = items.get(i).key();
        }
        bundle = new JBTextField(defaultBundle);
        setTitle("Externalize Strings");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        final JBTable table = new JBTable(new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return items.size();
            }

            @Override
            public int getColumnCount() {
                return 3;
            }

            @Override
            public String getColumnName(int column) {
                return switch (column) {
                    case 0 -> "";
                    case 1 -> "Key";
                    default -> "Value";
                };
            }

            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public Object getValueAt(int row, int column) {
                return switch (column) {
                    case 0 -> selected[row];
                    case 1 -> keys[row];
                    default -> items.get(row).value();
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column < 2;
            }

            @Override
            public void setValueAt(Object value, int row, int column) {
                if (column == 0) selected[row] = (Boolean) value;
                else if (column == 1) keys[row] = String.valueOf(value).trim();
            }
        });
        table.getColumnModel().getColumn(0).setMaxWidth(JBUI.scale(30));
        table.setPreferredScrollableViewportSize(JBUI.size(560, 260));
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Bundle:", bundle)
                .addComponent(new JBLabel("The literals are replaced by BUNDLE.getString(\"key\"); the values go into the properties file."))
                .addComponentFillVertically(ScrollPaneFactory.createScrollPane(table), 4)
                .getPanel();
    }

    String bundle() {
        return bundle.getText().trim();
    }

    List<FormWriter.Externalizable> chosen() {
        final List<FormWriter.Externalizable> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!selected[i] || keys[i].isEmpty()) continue;
            final FormWriter.Externalizable item = items.get(i);
            result.add(new FormWriter.Externalizable(item.component(), item.property(), keys[i], item.value(), item.literal()));
        }
        return result;
    }
}
