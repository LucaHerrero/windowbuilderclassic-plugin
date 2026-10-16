package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.parser.FormParser;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

/** Zeigt den Design-Tab für Java-Dateien des Projekts, die Swing oder AWT importieren. */
public final class WindowBuilderEditorProvider implements FileEditorProvider, DumbAware {
    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        if (!(file.getFileType() instanceof JavaFileType) || !ProjectFileIndex.getInstance(project).isInContent(file)) return false;
        final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        return psiFile != null && FormParser.isCandidate(psiFile);
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new WindowBuilderEditor(project, file);
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return "windowbuilder-classic";
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
    }
}
