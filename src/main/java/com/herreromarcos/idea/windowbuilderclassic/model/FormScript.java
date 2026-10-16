package com.herreromarcos.idea.windowbuilderclassic.model;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ergebnis des Parsers: die Anweisungen der Formularklasse in Ausführungsreihenfolge (Feld-Initialisierer, Konstruktor
 * und die von ihm aufgerufenen Methoden – der „Execution Flow“ des WindowBuilder).
 *
 * @param rootClass     JDK-Oberklasse, wenn die Klasse selbst das Formular ist ({@code extends JFrame}); sonst {@code null}
 *                      und das Formular ist das erste erzeugte Fenster (Application Window)
 * @param superClass    direkte Oberklasse; ist sie eine Projektklasse, zeigt der Designer auch deren Komponenten
 * @param superArgs     Argumente von {@code super(...)}
 * @param defaultBody   Methodenrumpf, in den neue Anweisungen geschrieben werden, wenn es keinen besseren Ort gibt
 */
public record FormScript(String className, @Nullable String rootClass, @Nullable String superClass, List<Expr> superArgs, List<Op> ops,
                         @Nullable SmartPsiElementPointer<PsiElement> defaultBody, List<String> problems) {

    /** Eine ausgeführte Anweisung. {@code statement} zeigt auf die Anweisung bzw. das Feld (bei Initialisierern). */
    public sealed interface Op {
        @Nullable SmartPsiElementPointer<PsiElement> statement();
    }

    /** {@code T name = init;}, {@code name = init;} oder ein Feld mit Initialisierer. */
    public record Assign(int varId, String name, boolean field, Expr value, List<String> argTexts,
                         @Nullable SmartPsiElementPointer<PsiElement> statement) implements Op {}

    /** {@code target.field = value;} – z.B. {@code gbc.gridx = 0;} oder {@code gbl.columnWidths = new int[]{0};}. */
    public record FieldAssign(Expr target, String field, Expr value, String valueText,
                              @Nullable SmartPsiElementPointer<PsiElement> statement) implements Op {}

    /** {@code target.method(args);} als eigene Anweisung. */
    public record Invoke(Expr target, String method, List<Expr> args, List<String> argTexts,
                         @Nullable SmartPsiElementPointer<PsiElement> statement) implements Op {}
}
