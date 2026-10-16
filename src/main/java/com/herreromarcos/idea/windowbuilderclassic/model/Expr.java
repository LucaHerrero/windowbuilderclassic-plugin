package com.herreromarcos.idea.windowbuilderclassic.model;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ein Java-Ausdruck, so weit der Designer ihn auswerten kann. Der Parser übersetzt PSI in diese Form (im Hintergrund),
 * der {@link com.herreromarcos.idea.windowbuilderclassic.runtime.Evaluator} wertet sie anschließend auf dem EDT aus – ohne PSI.
 */
public sealed interface Expr {
    /** String, Zahl, char, boolean oder {@code null}. */
    record Literal(@Nullable Object value) implements Expr {}

    /** {@code this} bzw. ein unqualifizierter Aufruf auf der Formularklasse. */
    record This() implements Expr {}

    /** Verweis auf eine vom Parser verfolgte Variable (lokal oder Feld). */
    record Var(int id, String name) implements Expr {}

    /**
     * {@code new X(...)}. {@code className} ist der voll qualifizierte Name, falls auflösbar. {@code component} sagt, ob
     * die Klasse (auch eine Projektklasse) von {@code java.awt.Component} erbt; {@code jdkSuperclass} ist dann die
     * nächste JDK-Oberklasse, mit der eine Projektklasse ersatzweise gerendert wird.
     */
    record New(@Nullable String className, String classText, List<Expr> args, boolean component,
               @Nullable String jdkSuperclass) implements Expr {}

    record StaticField(String className, String field) implements Expr {}

    record StaticCall(String className, String method, List<Expr> args) implements Expr {}

    /** Aufruf auf einem Objekt, z.B. {@code getContentPane()} oder {@code frame.getContentPane()}. */
    record Call(Expr target, String method, List<Expr> args) implements Expr {}

    /** {@code new int[]{0, 0}} bzw. {@code {"a", "b"}}; {@code elementType} ist primitiv oder voll qualifiziert. */
    record ArrayInit(String elementType, List<Expr> elements) implements Expr {}

    record Binary(String operator, Expr left, Expr right) implements Expr {}

    record Unary(String operator, Expr operand) implements Expr {}

    /** Alles andere: Lambdas, Klassen-Literale, Methoden des Projekts … – wird nicht ausgewertet. */
    record Opaque(String text) implements Expr {}
}
