package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.VariableNames;
import com.herreromarcos.idea.windowbuilderclassic.model.Expr;
import com.herreromarcos.idea.windowbuilderclassic.runtime.Evaluator;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;
import junit.framework.TestCase;

import java.awt.Color;
import java.awt.Dimension;
import java.util.List;
import java.util.Set;

/** Auswertung und Namensregeln ohne IDE. */
public class RuntimeTest extends TestCase {
    private static final Evaluator EVALUATOR = new Evaluator(id -> Evaluator.UNKNOWN, null);

    private static Object eval(Expr expr) {
        return EVALUATOR.eval(expr);
    }

    public void testConstructorsAndOverloads() {
        final Object color = eval(new Expr.New("java.awt.Color", "Color", List.of(new Expr.Literal(1), new Expr.Literal(2), new Expr.Literal(3)), false, null));
        assertEquals(new Color(1, 2, 3), color);
        // float-Überladung von Color mit int-Literalen darf nicht gewählt werden
        final Object dimension = eval(new Expr.New("java.awt.Dimension", "Dimension", List.of(new Expr.Literal(10), new Expr.Literal(20)), false, null));
        assertEquals(new Dimension(10, 20), dimension);
    }

    public void testStaticFieldsAndCalls() {
        assertEquals("North", eval(new Expr.StaticField("java.awt.BorderLayout", "NORTH")));
        assertNotNull(eval(new Expr.StaticCall("javax.swing.BorderFactory", "createEmptyBorder", List.of(
                new Expr.Literal(1), new Expr.Literal(2), new Expr.Literal(3), new Expr.Literal(4)))));
        // Nicht freigegebene statische Methoden werden nicht ausgeführt
        assertSame(Evaluator.UNKNOWN, eval(new Expr.StaticCall("javax.swing.JOptionPane", "showMessageDialog",
                List.of(new Expr.Literal(null), new Expr.Literal("x")))));
        assertSame(Evaluator.UNKNOWN, eval(new Expr.StaticCall("java.lang.System", "exit", List.of(new Expr.Literal(0)))));
    }

    public void testOnlyJdkGuiClassesAreInstantiated() {
        assertSame(Evaluator.UNKNOWN, eval(new Expr.New("java.io.File", "File", List.of(new Expr.Literal("x")), false, null)));
        assertSame(Evaluator.UNKNOWN, eval(new Expr.New("java.awt.Robot", "Robot", List.of(), false, null)));
        assertTrue(eval(new Expr.New("javax.swing.JFrame", "JFrame", List.of(new Expr.Literal("T")), true, "javax.swing.JFrame")) instanceof WindowSurrogate);
    }

    public void testArithmetic() {
        assertEquals(7, eval(new Expr.Binary("+", new Expr.Literal(3), new Expr.Literal(4))));
        assertEquals("a1", eval(new Expr.Binary("+", new Expr.Literal("a"), new Expr.Literal(1))));
        assertEquals(-5, eval(new Expr.Unary("-", new Expr.Literal(5))));
        assertEquals(3, eval(new Expr.Binary("|", new Expr.Literal(1), new Expr.Literal(2))));
        assertSame(Evaluator.UNKNOWN, eval(new Expr.Binary("+", new Expr.Opaque("x"), new Expr.Literal(1))));
    }

    public void testVariableNames() {
        assertEquals("btnNewButton", VariableNames.base("javax.swing.JButton", "New button"));
        assertEquals("lblNewLabel", VariableNames.base("javax.swing.JLabel", "New label"));
        assertEquals("chckbxNewCheckBox", VariableNames.base("javax.swing.JCheckBox", "New check box"));
        assertEquals("rdbtnNewRadioButton", VariableNames.base("javax.swing.JRadioButton", "New radio button"));
        assertEquals("textField", VariableNames.base("javax.swing.JTextField", null));
        assertEquals("scrollPane", VariableNames.base("javax.swing.JScrollPane", null));
        assertEquals("fancyThing", VariableNames.base("com.example.FancyThing", null));
        assertEquals("btnNewButton_2", VariableNames.unique("btnNewButton", Set.of("btnNewButton", "btnNewButton_1")));
    }
}
