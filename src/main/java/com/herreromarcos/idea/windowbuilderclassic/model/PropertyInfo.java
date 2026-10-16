package com.herreromarcos.idea.windowbuilderclassic.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Ein im Code gesetzter Wert: der letzte Aufruf von {@code method} auf der Komponente. */
public record PropertyInfo(String method, List<String> argTexts, List<Object> values,
                           @Nullable SmartPsiElementPointer<PsiElement> statement) {
    public String argsText() {
        return String.join(", ", argTexts);
    }
}
