package com.herreromarcos.idea.windowbuilderclassic.runtime;

import com.herreromarcos.idea.windowbuilderclassic.parser.TypeResolver;
import org.jetbrains.annotations.Nullable;

import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Die Events einer Komponentenklasse, wie sie die Events-Ansicht des WindowBuilder auflistet: aus den
 * EventSetDescriptors der JavaBeans-Introspection, mit Adapter-Klasse, falls es eine gibt.
 */
public final class EventCatalog {
    /** Ein Listener-Typ, z.B. {@code action} mit {@code addActionListener} und {@code actionPerformed}. */
    public record EventSet(String name, String addMethod, String listenerType, @Nullable String adapter, List<EventMethod> methods) {}

    /** Eine Listener-Methode mit dem Typ ihres Parameters. */
    public record EventMethod(String name, String parameterType) {}

    private EventCatalog() {
    }

    public static List<EventSet> of(Class<?> componentClass) {
        final List<EventSet> result = new ArrayList<>();
        final BeanInfo info;
        try {
            info = Introspector.getBeanInfo(componentClass);
        } catch (final IntrospectionException e) {
            return result;
        }
        for (final EventSetDescriptor descriptor : info.getEventSetDescriptors()) {
            final Method add = descriptor.getAddListenerMethod();
            if (add == null || descriptor.isHidden()) continue;
            final Class<?> listener = descriptor.getListenerType();
            final List<EventMethod> methods = new ArrayList<>();
            for (final Method method : descriptor.getListenerMethods()) {
                final Class<?>[] parameters = method.getParameterTypes();
                if (parameters.length != 1) continue;
                methods.add(new EventMethod(method.getName(), parameters[0].getName().replace('$', '.')));
            }
            if (methods.isEmpty()) continue;
            methods.sort(Comparator.comparing(EventMethod::name));
            result.add(new EventSet(descriptor.getName(), add.getName(), listener.getName().replace('$', '.'), adapterFor(listener), methods));
        }
        result.sort(Comparator.comparing(EventSet::name));
        return result;
    }

    /** {@code MouseListener} → {@code MouseAdapter} im selben Paket, falls vorhanden. */
    static @Nullable String adapterFor(Class<?> listener) {
        final String name = listener.getName();
        if (!name.endsWith("Listener")) return null;
        final String adapter = name.substring(0, name.length() - "Listener".length()) + "Adapter";
        final Class<?> cls = TypeResolver.jdkClass(adapter);
        return cls != null && listener.isAssignableFrom(cls) ? adapter : null;
    }

    /** Das Standard-Event bei Doppelklick: {@code actionPerformed} für Buttons, Textfelder, Comboboxen. */
    public static @Nullable EventSet defaultEvent(Class<?> componentClass) {
        for (final EventSet set : of(componentClass)) {
            if ("action".equals(set.name())) return set;
        }
        return null;
    }
}
