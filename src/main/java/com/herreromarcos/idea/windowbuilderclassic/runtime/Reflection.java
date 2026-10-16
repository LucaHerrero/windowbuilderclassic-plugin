package com.herreromarcos.idea.windowbuilderclassic.runtime;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/** Aufrufe per Reflection mit Overload-Auswahl ähnlich wie javac (exakte Typen vor Boxing vor Zuweisbarkeit). */
public final class Reflection {
    private static final Map<Class<?>, Class<?>> BOXES = Map.of(
            int.class, Integer.class, long.class, Long.class, double.class, Double.class, float.class, Float.class,
            boolean.class, Boolean.class, char.class, Character.class, short.class, Short.class, byte.class, Byte.class);

    private Reflection() {
    }

    public static final class NoSuchMember extends Exception {
        public NoSuchMember(String message) {
            super(message);
        }
    }

    public static Object construct(Class<?> cls, Object[] args) throws NoSuchMember, ReflectiveOperationException {
        Constructor<?> best = null;
        int bestScore = -1;
        for (final Constructor<?> constructor : cls.getConstructors()) {
            final int score = score(constructor, args);
            if (score > bestScore) {
                best = constructor;
                bestScore = score;
            }
        }
        if (best == null) throw new NoSuchMember("No matching constructor %s(%d arguments)".formatted(cls.getSimpleName(), args.length));
        return best.newInstance(convert(best, args));
    }

    public static @Nullable Object invoke(Object target, String name, Object[] args) throws NoSuchMember, ReflectiveOperationException {
        final Method method = find(target.getClass(), name, args, false);
        if (method == null) throw new NoSuchMember("No method %s(%d arguments) in %s".formatted(name, args.length, target.getClass().getSimpleName()));
        return method.invoke(target, convert(method, args));
    }

    public static @Nullable Object invokeStatic(Class<?> cls, String name, Object[] args) throws NoSuchMember, ReflectiveOperationException {
        final Method method = find(cls, name, args, true);
        if (method == null) throw new NoSuchMember("No static method %s.%s(%d arguments)".formatted(cls.getSimpleName(), name, args.length));
        return method.invoke(null, convert(method, args));
    }

    /** Ursache einer {@link InvocationTargetException} auspacken. */
    public static Throwable cause(Throwable e) {
        return e instanceof final InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : e;
    }

    /** Sucht nur in öffentlichen Klassen – Methoden nicht-öffentlicher Unterklassen (z.B. anonymer Klassen) sind nicht aufrufbar. */
    private static @Nullable Method find(Class<?> cls, String name, Object[] args, boolean wantStatic) {
        Method best = null;
        int bestScore = -1;
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            if (!Modifier.isPublic(c.getModifiers())) continue;
            for (final Method method : c.getMethods()) {
                if (!method.getName().equals(name) || Modifier.isStatic(method.getModifiers()) != wantStatic) continue;
                if (!Modifier.isPublic(method.getDeclaringClass().getModifiers())) continue;
                final int score = score(method, args);
                if (score > bestScore) {
                    best = method;
                    bestScore = score;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static int score(Executable executable, Object[] args) {
        final Class<?>[] types = executable.getParameterTypes();
        if (types.length != args.length) return -1;
        int score = 0;
        for (int i = 0; i < types.length; i++) {
            final int s = score(types[i], args[i]);
            if (s < 0) return -1;
            score += s;
        }
        return score;
    }

    private static int score(Class<?> type, @Nullable Object arg) {
        if (arg == null) return type.isPrimitive() ? -1 : 1;
        if (type.isPrimitive()) {
            if (BOXES.get(type) == arg.getClass()) return 3;
            return widens(type, arg) ? 2 : -1;
        }
        if (type == arg.getClass()) return 3;
        if (type.isInstance(arg)) return type == Object.class ? 0 : 2;
        return -1;
    }

    private static boolean widens(Class<?> type, Object arg) {
        if (!(arg instanceof Number) && !(arg instanceof Character)) return false;
        final int from = rank(arg instanceof Character ? char.class : unbox(arg.getClass()));
        return from >= 0 && rank(type) > from && !(type == char.class);
    }

    private static Class<?> unbox(Class<?> box) {
        for (final Map.Entry<Class<?>, Class<?>> entry : BOXES.entrySet()) {
            if (entry.getValue() == box) return entry.getKey();
        }
        return box;
    }

    private static int rank(Class<?> type) {
        if (type == byte.class) return 0;
        if (type == short.class || type == char.class) return 1;
        if (type == int.class) return 2;
        if (type == long.class) return 3;
        if (type == float.class) return 4;
        if (type == double.class) return 5;
        return -1;
    }

    private static Object[] convert(Executable executable, Object[] args) {
        final Class<?>[] types = executable.getParameterTypes();
        final Object[] converted = args.clone();
        for (int i = 0; i < types.length; i++) {
            if (!types[i].isPrimitive() || converted[i] == null || BOXES.get(types[i]) == converted[i].getClass()) continue;
            final Object arg = converted[i];
            final double number = arg instanceof final Character c ? c : ((Number) arg).doubleValue();
            final long whole = arg instanceof final Character c ? c : ((Number) arg).longValue();
            converted[i] = types[i] == long.class ? (Object) whole
                    : types[i] == int.class ? (Object) (int) whole
                    : types[i] == short.class ? (Object) (short) whole
                    : types[i] == float.class ? (Object) (float) number
                    : (Object) number;
        }
        return converted;
    }
}
