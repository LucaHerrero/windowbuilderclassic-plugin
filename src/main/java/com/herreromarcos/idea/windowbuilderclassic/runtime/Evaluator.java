package com.herreromarcos.idea.windowbuilderclassic.runtime;

import com.herreromarcos.idea.windowbuilderclassic.model.Expr;
import com.herreromarcos.idea.windowbuilderclassic.parser.TypeResolver;
import org.jetbrains.annotations.Nullable;

import java.awt.Window;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Wertet {@link Expr} aus. Ausgeführt wird nur JDK-Code: Konstruktoren aus {@code java.awt}/{@code javax.swing},
 * Getter, und statische Methoden einer festen Liste. Was nicht auswertbar ist, ergibt {@link #UNKNOWN}.
 */
public final class Evaluator {
    public static final Object UNKNOWN = new Object() {
        @Override
        public String toString() {
            return "<unknown>";
        }
    };

    private static final Set<String> BLOCKED_CLASSES = Set.of("java.awt.Robot", "java.awt.Desktop", "java.awt.SystemTray",
            "java.awt.TrayIcon", "java.awt.Toolkit", "javax.swing.Timer", "java.awt.PrintJob", "java.awt.SplashScreen");

    /** Statische Methoden ohne Nebenwirkungen; leere Menge = alle öffentlichen Methoden der Klasse. */
    private static final Map<String, Set<String>> STATIC_METHODS = Map.of(
            "javax.swing.BorderFactory", Set.of(),
            "javax.swing.Box", Set.of(),
            "java.awt.Color", Set.of("decode", "getHSBColor", "getColor"),
            "java.awt.Font", Set.of("decode", "getFont"),
            "javax.swing.UIManager", Set.of("getColor", "getFont", "getBorder", "getIcon", "getInt", "getString", "getInsets", "getDimension"),
            "java.lang.Math", Set.of(),
            "java.lang.String", Set.of("valueOf", "format", "join"),
            "java.lang.Integer", Set.of("valueOf", "parseInt", "toString"),
            "javax.swing.Spring", Set.of());

    private final IntFunction<Object> variables;
    private final @Nullable Object thisObject;
    private final @Nullable ProjectClasses project;

    /**
     * @param variables  aktueller Wert einer Variablen ({@link #UNKNOWN}, wenn unbekannt)
     * @param thisObject lebende Instanz von {@code this}, falls die Klasse selbst das Formular ist
     */
    public Evaluator(IntFunction<Object> variables, @Nullable Object thisObject) {
        this(variables, thisObject, null);
    }

    /** @param project Klassen des Projekts; {@code null} = Projektcode wird nicht ausgeführt */
    public Evaluator(IntFunction<Object> variables, @Nullable Object thisObject, @Nullable ProjectClasses project) {
        this.variables = variables;
        this.thisObject = thisObject;
        this.project = project;
    }

    public @Nullable Object eval(Expr expr) {
        try {
            return doEval(expr);
        } catch (final Exception | LinkageError e) {
            return UNKNOWN;
        }
    }

    public Object[] evalAll(List<Expr> exprs) {
        final Object[] values = new Object[exprs.size()];
        for (int i = 0; i < values.length; i++) values[i] = eval(exprs.get(i));
        return values;
    }

    public static boolean known(Object[] values) {
        for (final Object value : values) {
            if (value == UNKNOWN) return false;
        }
        return true;
    }

    private @Nullable Object doEval(Expr expr) throws Exception {
        return switch (expr) {
            case Expr.Literal literal -> literal.value();
            case Expr.This ignored -> thisObject == null ? UNKNOWN : thisObject;
            case Expr.Var var -> variables.apply(var.id());
            case Expr.New create -> instantiate(create);
            case Expr.StaticField field -> staticField(field);
            case Expr.StaticCall call -> staticCall(call);
            case Expr.Call call -> call(call);
            case Expr.ArrayInit array -> array(array);
            case Expr.Binary binary -> binary(binary);
            case Expr.Unary unary -> unary(unary);
            case Expr.Opaque ignored -> UNKNOWN;
        };
    }

    public static boolean isInstantiable(@Nullable String className) {
        return className != null && (className.startsWith("java.awt.") || className.startsWith("javax.swing."))
                && !BLOCKED_CLASSES.contains(className);
    }

    private Object instantiate(Expr.New create) throws Exception {
        if (!isInstantiable(create.className())) return instantiateProjectClass(create);
        final Class<?> cls = TypeResolver.jdkClass(create.className());
        if (cls == null || Modifier.isAbstract(cls.getModifiers())) return UNKNOWN;
        final Object[] args = evalAll(create.args());
        if (Window.class.isAssignableFrom(cls)) {
            final WindowSurrogate window = new WindowSurrogate(create.className());
            for (final Object arg : args) {
                if (arg instanceof final String title) window.setTitle(title);
            }
            return window;
        }
        if (!known(args)) return UNKNOWN;
        if (AwtProxies.isProxied(create.className())) return AwtProxies.create(create.className(), args);
        return Reflection.construct(cls, unwrap(args));
    }

    /** Klassen aus dem Projekt und seinen Bibliotheken (eigene Komponenten, MigLayout …), nur wenn erlaubt. */
    private Object instantiateProjectClass(Expr.New create) throws Exception {
        final Class<?> cls = project == null ? null : project.load(create.className());
        if (cls == null || Modifier.isAbstract(cls.getModifiers()) || Window.class.isAssignableFrom(cls)) return UNKNOWN;
        final Object[] args = evalAll(create.args());
        if (!known(args)) return UNKNOWN;
        return Reflection.construct(cls, unwrap(args));
    }

    private @Nullable Class<?> anyClass(String className) {
        final Class<?> jdk = TypeResolver.jdkClass(className);
        return jdk != null || project == null ? jdk : project.load(className);
    }

    private Object staticField(Expr.StaticField field) throws Exception {
        final Class<?> cls = anyClass(field.className());
        if (cls == null) return UNKNOWN;
        final Field f = cls.getField(field.field());
        return Modifier.isStatic(f.getModifiers()) ? f.get(null) : UNKNOWN;
    }

    private Object staticCall(Expr.StaticCall call) throws Exception {
        final Set<String> allowed = STATIC_METHODS.get(call.className());
        if (allowed == null && project != null && project.load(call.className()) != null) {
            // Projekt- und Bibliotheksklassen (z.B. RowSpec.decode) – nur wenn Projektcode erlaubt ist
            final Object[] args = evalAll(call.args());
            return known(args) ? Reflection.invokeStatic(project.load(call.className()), call.method(), unwrap(args)) : UNKNOWN;
        }
        if (allowed == null || !allowed.isEmpty() && !allowed.contains(call.method())) return UNKNOWN;
        final Class<?> cls = TypeResolver.jdkClass(call.className());
        final Object[] args = evalAll(call.args());
        if (cls == null || !known(args)) return UNKNOWN;
        return Reflection.invokeStatic(cls, call.method(), unwrap(args));
    }

    /** In Ausdrücken nur Getter – Aufrufe mit Nebenwirkung stehen als eigene Anweisung und gehen über den Builder. */
    private Object call(Expr.Call call) throws Exception {
        final String method = call.method();
        final Object target = eval(call.target());
        if (!(method.startsWith("get") || method.startsWith("is") || isLayoutBuilder(target))) return UNKNOWN;
        final Object[] args = evalAll(call.args());
        if (target == null || target == UNKNOWN || !known(args)) return UNKNOWN;
        return Reflection.invoke(target, method, unwrap(args));
    }

    /** Gruppen von GroupLayout und Springs von SpringLayout werden über Methodenketten gebaut – ohne Nebenwirkung. */
    public static boolean isLayoutBuilder(@Nullable Object target) {
        return target instanceof javax.swing.GroupLayout || target instanceof javax.swing.GroupLayout.Group
                || target instanceof javax.swing.SpringLayout || target instanceof javax.swing.Spring
                || target instanceof javax.swing.SpringLayout.Constraints;
    }

    /** Fenster-Ersatz als Argument (z.B. {@code new JDialog(frame)} oder {@code setLocationRelativeTo(frame)}) gibt es nicht. */
    private static Object[] unwrap(Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof WindowSurrogate) args[i] = null;
        }
        return args;
    }

    private Object array(Expr.ArrayInit array) {
        final Class<?> type = switch (array.elementType()) {
            case "int" -> int.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "long" -> long.class;
            case "short" -> short.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "boolean" -> boolean.class;
            case "String", "java.lang.String" -> String.class;
            default -> {
                final Class<?> cls = anyClass(array.elementType());
                yield cls != null ? cls : Object.class;
            }
        };
        final Object result = java.lang.reflect.Array.newInstance(type, array.elements().size());
        for (int i = 0; i < array.elements().size(); i++) {
            final Object value = eval(array.elements().get(i));
            if (value == UNKNOWN) return UNKNOWN;
            // Array.set erweitert int → double wie der Compiler
            java.lang.reflect.Array.set(result, i, value);
        }
        return result;
    }

    private Object binary(Expr.Binary binary) {
        final Object left = eval(binary.left());
        final Object right = eval(binary.right());
        if (left == UNKNOWN || right == UNKNOWN) return UNKNOWN;
        final String op = binary.operator();
        if ("+".equals(op) && (left instanceof String || right instanceof String)) return String.valueOf(left) + right;
        if (!(left instanceof Number || left instanceof Character) || !(right instanceof Number || right instanceof Character)) {
            if (left instanceof final Boolean a && right instanceof final Boolean b) {
                return switch (op) {
                    case "&&", "&" -> a && b;
                    case "||", "|" -> a || b;
                    case "^" -> a ^ b;
                    default -> UNKNOWN;
                };
            }
            return UNKNOWN;
        }
        final boolean floating = left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float;
        if (floating) {
            final double a = number(left), b = number(right);
            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> a / b;
                default -> UNKNOWN;
            };
        }
        final long a = (long) number(left), b = (long) number(right);
        final long result = switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b == 0 ? 0 : a / b;
            case "%" -> b == 0 ? 0 : a % b;
            case "|" -> a | b;
            case "&" -> a & b;
            case "^" -> a ^ b;
            case "<<" -> a << b;
            case ">>" -> a >> b;
            default -> Long.MIN_VALUE;
        };
        if (result == Long.MIN_VALUE) return UNKNOWN;
        return left instanceof Long || right instanceof Long ? (Object) result : (Object) (int) result;
    }

    private Object unary(Expr.Unary unary) {
        final Object value = eval(unary.operand());
        return switch (unary.operator()) {
            case "-" -> value instanceof final Integer i ? -i : value instanceof final Long l ? -l
                    : value instanceof final Double d ? -d : value instanceof final Float f ? -f : UNKNOWN;
            case "+" -> value instanceof Number ? value : UNKNOWN;
            case "!" -> value instanceof final Boolean b ? !b : UNKNOWN;
            case "~" -> value instanceof final Integer i ? ~i : value instanceof final Long l ? ~l : UNKNOWN;
            default -> UNKNOWN;
        };
    }

    private static double number(Object value) {
        return value instanceof final Character c ? c : ((Number) value).doubleValue();
    }
}
