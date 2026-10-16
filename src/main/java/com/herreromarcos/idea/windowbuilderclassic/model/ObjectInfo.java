package com.herreromarcos.idea.windowbuilderclassic.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ein nicht sichtbares Objekt in einer Variablen, dessen öffentliche Felder der Code setzt – {@code GridBagLayout}
 * und {@code GridBagConstraints}. Die Feldzuweisungen merkt sich der Designer, um sie gezielt zu ändern.
 */
public final class ObjectInfo {
    private final int varId;
    private final String name;
    private final String className;
    private final Object live;
    private final @Nullable SmartPsiElementPointer<PsiElement> creation;
    private final Map<String, PropertyInfo> fields = new LinkedHashMap<>();
    private final java.util.List<PropertyInfo> calls = new java.util.ArrayList<>();
    private int uses;

    public ObjectInfo(int varId, String name, String className, Object live, @Nullable SmartPsiElementPointer<PsiElement> creation) {
        this.varId = varId;
        this.name = name;
        this.className = className;
        this.live = live;
        this.creation = creation;
    }

    public int getVarId() {
        return varId;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }

    public Object getLive() {
        return live;
    }

    public @Nullable SmartPsiElementPointer<PsiElement> getCreation() {
        return creation;
    }

    /** Letzte Zuweisung je Feld. */
    public Map<String, PropertyInfo> getFields() {
        return fields;
    }

    /** Aufrufe auf dem Objekt, z.B. {@code setHorizontalGroup} oder {@code putConstraint}, in Reihenfolge. */
    public java.util.List<PropertyInfo> getCalls() {
        return calls;
    }

    /** Wie oft das Objekt als Constraints an {@code add} übergeben wird – mehr als einmal heißt geteilt. */
    public int getUses() {
        return uses;
    }

    public void used() {
        uses++;
    }
}
