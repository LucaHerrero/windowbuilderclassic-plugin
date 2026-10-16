package com.herreromarcos.idea.windowbuilderclassic.actions;

import com.herreromarcos.idea.windowbuilderclassic.WindowBuilderIcons;
import com.intellij.ide.actions.CreateFileFromTemplateDialog;
import com.intellij.ide.actions.JavaCreateTemplateInPackageAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

/** <i>New → WindowBuilder Class</i>: dieselben Vorlagen wie die Swing-Wizards des WindowBuilder. Text und Icon stehen in plugin.xml. */
public class NewWindowBuilderClassAction extends JavaCreateTemplateInPackageAction<PsiClass> {
    public NewWindowBuilderClassAction() {
        super(() -> "WindowBuilder Class", () -> "Create a Swing class to edit with WindowBuilder Classic",
                () -> WindowBuilderIcons.DESIGNER, JavaModuleSourceRootTypes.SOURCES);
    }

    @Override
    protected void buildDialog(@NotNull Project project, @NotNull PsiDirectory directory, CreateFileFromTemplateDialog.@NotNull Builder builder) {
        builder.setTitle("New WindowBuilder Class")
                .addKind("JFrame", WindowBuilderIcons.DESIGNER, "Swing JFrame")
                .addKind("JPanel", WindowBuilderIcons.DESIGNER, "Swing JPanel")
                .addKind("JDialog", WindowBuilderIcons.DESIGNER, "Swing JDialog")
                .addKind("Application Window", WindowBuilderIcons.DESIGNER, "Swing Application Window")
                .addKind("JInternalFrame", WindowBuilderIcons.DESIGNER, "Swing JInternalFrame");
    }

    @Override
    protected String getActionName(PsiDirectory directory, @NotNull String newName, String templateName) {
        return "Create Swing class %s".formatted(newName);
    }

    @Override
    protected @Nullable PsiClass doCreate(PsiDirectory dir, String className, String templateName) throws IncorrectOperationException {
        return JavaDirectoryService.getInstance().createClass(dir, className, templateName, true);
    }

    @Override
    protected PsiElement getNavigationElement(@NotNull PsiClass createdElement) {
        return createdElement.getLBrace() != null ? createdElement.getLBrace() : createdElement;
    }
}
