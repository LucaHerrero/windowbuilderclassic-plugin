package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.DocumentReferenceProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.List;

/**
 * Der Tab „Design“ neben dem Quelltext. Die Oberfläche entsteht erst, wenn der Tab zum ersten Mal gewählt wird – so
 * kostet der Designer nichts, solange man nur im Quelltext arbeitet. Undo/Redo laufen über das Dokument der Datei.
 */
final class WindowBuilderEditor extends UserDataHolderBase implements FileEditor, DocumentReferenceProvider {
    private final Project project;
    private final VirtualFile file;
    private final JPanel component = new JPanel(new BorderLayout());
    private @Nullable DesignerPanel designer;

    WindowBuilderEditor(Project project, VirtualFile file) {
        this.project = project;
        this.file = file;
    }

    @Override
    public @NotNull JComponent getComponent() {
        return component;
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return designer == null ? component : designer.getPreferredFocusedComponent();
    }

    @Override
    public @NotNull String getName() {
        return "Design";
    }

    @Override
    public void selectNotify() {
        if (designer == null) {
            designer = new DesignerPanel(project, file);
            Disposer.register(this, designer);
            component.add(designer, BorderLayout.CENTER);
            component.revalidate();
        }
        designer.setActive(true);
    }

    @Override
    public void deselectNotify() {
        if (designer != null) designer.setActive(false);
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return file.isValid();
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public @NotNull Collection<DocumentReference> getDocumentReferences() {
        final Document document = FileDocumentManager.getInstance().getDocument(file);
        return document == null ? List.of() : List.of(DocumentReferenceManager.getInstance().create(document));
    }

    @Override
    public void dispose() {
    }
}
