package com.herreromarcos.idea.windowbuilderclassic.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Eine Komponente des Formulars (entspricht dem JavaInfo des WindowBuilder): Code-Herkunft plus lebende Swing-Instanz. */
public final class ComponentInfo {
    /** Wie die Komponente an ihren Container gehängt ist. */
    public enum Association {
        ROOT, IMPLICIT, ADD, CONTENT_PANE, MENU_BAR, VIEWPORT, LEFT, RIGHT, TOP, BOTTOM, TAB, POPUP, GROUP
    }

    private final int varId;
    private final @Nullable String name;
    private final boolean field;
    private final String className;
    private final Component live;
    private final boolean placeholder;
    private final @Nullable SmartPsiElementPointer<PsiElement> creation;
    private final List<String> creationArgs;
    private final @Nullable String accessor;
    private final Map<String, PropertyInfo> properties = new LinkedHashMap<>();
    private final Map<String, PropertyInfo> events = new LinkedHashMap<>();
    private final List<ComponentInfo> children = new ArrayList<>();
    private @Nullable ComponentInfo parent;
    private Association association = Association.ROOT;
    private @Nullable SmartPsiElementPointer<PsiElement> associationStatement;
    private @Nullable String constraints;
    private @Nullable Object constraintsValue;
    private @Nullable ObjectInfo constraintsObject;
    private @Nullable ObjectInfo layoutObject;

    /**
     * @param accessor Ausdruck für implizite Komponenten ohne Variable, z.B. {@code getContentPane()}
     */
    public ComponentInfo(int varId, @Nullable String name, boolean field, String className, Component live, boolean placeholder,
                         @Nullable SmartPsiElementPointer<PsiElement> creation, List<String> creationArgs, @Nullable String accessor) {
        this.varId = varId;
        this.name = name;
        this.field = field;
        this.className = className;
        this.live = live;
        this.placeholder = placeholder;
        this.creation = creation;
        this.creationArgs = creationArgs;
        this.accessor = accessor;
    }

    public int getVarId() {
        return varId;
    }

    public @Nullable String getName() {
        return name;
    }

    public boolean isField() {
        return field;
    }

    public String getClassName() {
        return className;
    }

    public String getShortClassName() {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    public Component getLive() {
        return live;
    }

    /** Projektklasse, die mit ihrer JDK-Oberklasse dargestellt wird. */
    public boolean isPlaceholder() {
        return placeholder;
    }

    public @Nullable SmartPsiElementPointer<PsiElement> getCreation() {
        return creation;
    }

    public List<String> getCreationArgs() {
        return creationArgs;
    }

    /** Wie im Code auf die Komponente verwiesen wird: Variablenname, {@code getContentPane()} oder {@code null} für {@code this}. */
    public @Nullable String getReference() {
        return name != null ? name : accessor;
    }

    public boolean isThis() {
        return name == null && accessor == null;
    }

    public boolean isImplicit() {
        return association == Association.IMPLICIT;
    }

    public Map<String, PropertyInfo> getProperties() {
        return properties;
    }

    public @Nullable PropertyInfo getProperty(String method) {
        return properties.get(method);
    }

    /** Registrierte Listener: {@code addActionListener} → Anweisung (erste Registrierung je Methode). */
    public Map<String, PropertyInfo> getEvents() {
        return events;
    }

    public List<ComponentInfo> getChildren() {
        return children;
    }

    public @Nullable ComponentInfo getParent() {
        return parent;
    }

    public Association getAssociation() {
        return association;
    }

    public @Nullable SmartPsiElementPointer<PsiElement> getAssociationStatement() {
        return associationStatement;
    }

    /** Quelltext der Constraints im {@code add}-Aufruf (z.B. {@code BorderLayout.NORTH}) bzw. Tab-Titel. */
    public @Nullable String getConstraints() {
        return constraints;
    }

    public @Nullable Object getConstraintsValue() {
        return constraintsValue;
    }

    /** Die Constraints-Variable aus {@code add(child, gbc)}, falls es eine ist. */
    public @Nullable ObjectInfo getConstraintsObject() {
        return constraintsObject;
    }

    public void setConstraintsObject(@Nullable ObjectInfo constraintsObject) {
        this.constraintsObject = constraintsObject;
    }

    /** Die Layout-Variable aus {@code setLayout(gbl)}, falls es eine ist. */
    public @Nullable ObjectInfo getLayoutObject() {
        return layoutObject;
    }

    public void setLayoutObject(@Nullable ObjectInfo layoutObject) {
        this.layoutObject = layoutObject;
    }

    public void attach(ComponentInfo child, Association association, @Nullable SmartPsiElementPointer<PsiElement> statement,
                       @Nullable String constraints, @Nullable Object constraintsValue) {
        if (child.parent != null) child.parent.children.remove(child);
        child.parent = this;
        child.association = association;
        child.associationStatement = statement;
        child.constraints = constraints;
        child.constraintsValue = constraintsValue;
        children.add(child);
    }

    /** Der Container, in den Kinder tatsächlich gelegt werden (bei Fenstern und JInternalFrame die Content Pane). */
    public boolean isContainer() {
        return live instanceof Container && !placeholder;
    }

    public boolean isAncestorOf(ComponentInfo other) {
        for (ComponentInfo c = other.parent; c != null; c = c.parent) {
            if (c == this) return true;
        }
        return false;
    }

    /** Anzeigename wie im Components-Baum des WindowBuilder: {@code btnOk - "OK"}. */
    public String getPresentableName() {
        final String base = name != null ? name : accessor != null && !accessor.isEmpty() ? accessor
                : accessor != null ? "(content)" : "(%s)".formatted(className);
        final PropertyInfo text = properties.get("setText");
        if (text != null && !text.argTexts().isEmpty()) return "%s - %s".formatted(base, text.argTexts().get(0));
        if (creationArgs.size() == 1 && creationArgs.get(0).startsWith("\"")) return "%s - %s".formatted(base, creationArgs.get(0));
        return base;
    }

    @Override
    public String toString() {
        return getPresentableName();
    }
}
