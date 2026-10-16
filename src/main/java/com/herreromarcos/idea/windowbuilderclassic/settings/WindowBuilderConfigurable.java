package com.herreromarcos.idea.windowbuilderclassic.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/** <i>Settings → Tools → WindowBuilder Classic</i>. */
public final class WindowBuilderConfigurable implements Configurable {
    private final Project project;
    private final JBCheckBox fields = new JBCheckBox("Create new components as fields (instead of local variables)");
    private final JBCheckBox blocks = new JBCheckBox("Put the code of new components into blocks { … }");
    private final JBCheckBox lambdas = new JBCheckBox("Create single-method listeners as lambdas (instead of anonymous classes)");
    private final JBCheckBox gaps = new JBCheckBox("GridBagLayout: create gaps between cells via insets");
    private final JSpinner gap = new JSpinner(new SpinnerNumberModel(5, 0, 50, 1));
    private final JBCheckBox projectCode = new JBCheckBox("Run project code to render custom components and third-party layouts");

    public WindowBuilderConfigurable(Project project) {
        this.project = project;
    }

    @Override
    public String getDisplayName() {
        return "WindowBuilder Classic";
    }

    @Override
    public @Nullable JComponent createComponent() {
        final JPanel panel = FormBuilder.createFormBuilder()
                .addComponent(new JBLabel("Code generation"))
                .addComponent(fields)
                .addComponent(blocks)
                .addComponent(lambdas)
                .addVerticalGap(8)
                .addComponent(new JBLabel("GridBagLayout"))
                .addComponent(gaps)
                .addLabeledComponent("Gap size (pixels):", gap)
                .addVerticalGap(8)
                .addComponent(new JBLabel("Rendering"))
                .addComponent(projectCode)
                .addComponent(new JBLabel("<html>Classes are loaded from the compiled output and the libraries of the module. "
                        + "Their constructors run inside the IDE.</html>").withBorder(JBUI.Borders.emptyLeft(24)))
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        reset();
        return panel;
    }

    private WindowBuilderSettings.State state() {
        return WindowBuilderSettings.getInstance(project).getState();
    }

    @Override
    public boolean isModified() {
        final WindowBuilderSettings.State s = state();
        return fields.isSelected() != s.variablesAsFields || blocks.isSelected() != s.useBlocks || lambdas.isSelected() != s.lambdaListeners
                || gaps.isSelected() != s.gridBagGaps || (int) gap.getValue() != s.gridBagGap || projectCode.isSelected() != s.runProjectCode;
    }

    @Override
    public void apply() {
        final WindowBuilderSettings.State s = state();
        s.variablesAsFields = fields.isSelected();
        s.useBlocks = blocks.isSelected();
        s.lambdaListeners = lambdas.isSelected();
        s.gridBagGaps = gaps.isSelected();
        s.gridBagGap = (int) gap.getValue();
        s.runProjectCode = projectCode.isSelected();
    }

    @Override
    public void reset() {
        final WindowBuilderSettings.State s = state();
        fields.setSelected(s.variablesAsFields);
        blocks.setSelected(s.useBlocks);
        lambdas.setSelected(s.lambdaListeners);
        gaps.setSelected(s.gridBagGaps);
        gap.setValue(s.gridBagGap);
        projectCode.setSelected(s.runProjectCode);
    }
}
