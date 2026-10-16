package com.herreromarcos.idea.windowbuilderclassic.runtime;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.Expr;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.model.FormScript;
import com.herreromarcos.idea.windowbuilderclassic.model.ObjectInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.PropertyInfo;
import com.herreromarcos.idea.windowbuilderclassic.parser.TypeResolver;
import org.jetbrains.annotations.Nullable;

import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Führt ein {@link FormScript} aus und baut daraus das {@link FormModel} mit lebenden Swing-Komponenten. Läuft auf dem
 * EDT und ohne PSI. Wie im WindowBuilder werden Aufrufe auf Komponenten nachgespielt; was nicht ausführbar ist, wird
 * übersprungen und als Warnung gemeldet.
 */
public final class FormBuilder {
    /** Werden aufgezeichnet, aber im Designer nicht ausgeführt. */
    private static final Set<String> NOT_EXECUTED = Set.of("setVisible", "setLocationRelativeTo", "setLocation",
            "setDefaultCloseOperation", "setModal", "setModalityType", "setUndecorated", "setExtendedState", "setIconImage",
            "setAlwaysOnTop", "setType", "setOpacity", "setShape", "setFocusTraversalPolicy");

    /** Aufrufe außer Settern, die das Aussehen bestimmen und ohne Nebenwirkung nachgespielt werden. */
    private static final Set<String> EXECUTED_CALLS = Set.of("add", "addItem", "addSeparator", "insertItemAt", "select",
            "addElement", "append", "insert");

    /** Methoden, die eine Komponente in einen Container hängen, und wie. */
    private static final Map<String, Association> CHILD_SETTERS = Map.of(
            "setViewportView", Association.VIEWPORT, "setLeftComponent", Association.LEFT,
            "setRightComponent", Association.RIGHT, "setTopComponent", Association.TOP,
            "setBottomComponent", Association.BOTTOM, "setContentPane", Association.CONTENT_PANE,
            "setJMenuBar", Association.MENU_BAR);

    /** Klassen, deren öffentliche Felder der Designer setzen darf. */
    private static final Set<Class<?>> FIELD_WRITABLE = Set.of(java.awt.GridBagConstraints.class, java.awt.GridBagLayout.class,
            java.awt.Insets.class, Dimension.class, java.awt.Point.class, java.awt.Rectangle.class);

    /** Layouts, deren Aufrufe ({@code setHorizontalGroup}, {@code putConstraint}, {@code show}) verfolgt werden. */
    private static final Set<Class<?>> TRACKED_LAYOUTS = Set.of(javax.swing.GroupLayout.class, javax.swing.SpringLayout.class,
            java.awt.CardLayout.class);

    private static final Dimension DEFAULT_SIZE = new Dimension(450, 300);

    private final FormScript script;
    private final Map<Integer, Object> values = new HashMap<>();
    private final Map<Component, ComponentInfo> infos = new IdentityHashMap<>();
    private final List<ComponentInfo> created = new ArrayList<>();
    private final Map<Object, ObjectInfo> objects = new IdentityHashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private @Nullable ComponentInfo thisInfo;
    private final Evaluator evaluator;

    private final @Nullable ProjectClasses project;

    private FormBuilder(FormScript script, @Nullable ProjectClasses project) {
        this.script = script;
        this.project = project;
        evaluator = new Evaluator(id -> values.getOrDefault(id, Evaluator.UNKNOWN), null, project);
    }

    /** @return das Modell oder {@code null}, wenn die Klasse keine Oberfläche erzeugt */
    public static @Nullable FormModel build(FormScript script) {
        return build(script, null);
    }

    /** @param project Klassen des Projekts, um eigene Komponenten echt darzustellen; {@code null} = Platzhalter */
    public static @Nullable FormModel build(FormScript script, @Nullable ProjectClasses project) {
        return new FormBuilder(script, project).run();
    }

    private @Nullable FormModel run() {
        final Evaluator rootEvaluator;
        if (script.rootClass() != null) {
            final Component live = createRoot();
            thisInfo = new ComponentInfo(0, null, false, script.rootClass(), live, false, null, List.of(), null);
            register(thisInfo);
            addImplicitContentPane(thisInfo);
        }
        rootEvaluator = new Evaluator(id -> values.getOrDefault(id, Evaluator.UNKNOWN), thisInfo == null ? null : thisInfo.getLive(), project);
        for (final FormScript.Op op : script.ops()) {
            try {
                switch (op) {
                    case FormScript.Assign assign -> assign(assign, rootEvaluator);
                    case FormScript.Invoke invoke -> invoke(invoke, rootEvaluator);
                    case FormScript.FieldAssign fieldAssign -> assignField(fieldAssign, rootEvaluator);
                }
            } catch (final RuntimeException | LinkageError e) {
                warnings.add("%s: %s".formatted(describe(op), e));
            }
        }
        for (final ComponentInfo info : created) {
            final Container parent = info.getLive().getParent();
            final ComponentInfo parentInfo = parent == null ? null : infos.get(parent);
            if (info.getParent() == null && parentInfo != null && parent.getLayout() instanceof javax.swing.GroupLayout) {
                parentInfo.attach(info, Association.GROUP, null, null, null);
            }
        }
        final ComponentInfo root = thisInfo != null ? thisInfo : findWindowRoot();
        if (root == null) return null;
        final FormModel model = new FormModel(script, root);
        for (final ComponentInfo info : infos.values()) model.register(info);
        model.getWarnings().addAll(warnings);
        layout(root);
        return model;
    }

    private void assign(FormScript.Assign op, Evaluator evaluator) {
        if (op.value() instanceof final Expr.New create && create.component()) {
            Object value = evaluator.eval(create);
            final boolean jdk = Evaluator.isInstantiable(create.className());
            if (!(value instanceof Component)) {
                if (jdk) warnings.add("%s: constructor arguments could not be evaluated, using the default constructor".formatted(op.name()));
                value = createInstance(jdk ? create.className() : create.jdkSuperclass(), List.of());
            }
            final Component live = (Component) value;
            final String className = create.className() != null ? create.className() : create.classText();
            // Projektklassen sind nur Platzhalter, wenn sie nicht geladen werden konnten
            final Class<?> projectClass = jdk || project == null ? null : project.load(create.className());
            final boolean placeholder = !jdk && (projectClass == null || !projectClass.isInstance(live));
            final ComponentInfo info = new ComponentInfo(op.varId(), op.name(), op.field(), className, live, placeholder,
                    op.statement(), op.argTexts(), null);
            register(info);
            created.add(info);
            addImplicitContentPane(info);
            values.put(op.varId(), live);
        } else {
            final Object value = evaluator.eval(op.value());
            values.put(op.varId(), value);
            if (value instanceof final Component component && !infos.containsKey(component) && !(op.value() instanceof Expr.Var)) {
                final ComponentInfo info = new ComponentInfo(op.varId(), op.name(), op.field(), component.getClass().getName().replace('$', '.'),
                        component, false, op.statement(), op.argTexts(), null);
                register(info);
                created.add(info);
                return;
            }
            if (value != null && value != Evaluator.UNKNOWN && !(value instanceof Component)
                    && (FIELD_WRITABLE.contains(value.getClass()) || TRACKED_LAYOUTS.contains(value.getClass()))) {
                objects.put(value, new ObjectInfo(op.varId(), op.name(), value.getClass().getName(), value, op.statement()));
            }
        }
    }

    private void assignField(FormScript.FieldAssign op, Evaluator evaluator) {
        final Object target = evaluator.eval(op.target());
        if (target == null || target == Evaluator.UNKNOWN) return;
        final Object value = evaluator.eval(op.value());
        final ObjectInfo info = objects.get(target);
        if (info != null) {
            info.getFields().remove(op.field());
            info.getFields().put(op.field(), new PropertyInfo(op.field(), List.of(op.valueText()), Arrays.asList(value), op.statement()));
        }
        if (value == Evaluator.UNKNOWN) {
            warnings.add("%s: value could not be evaluated".formatted(describe(op)));
            return;
        }
        if (!FIELD_WRITABLE.contains(target.getClass())) return;
        try {
            final java.lang.reflect.Field field = target.getClass().getField(op.field());
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers()) || java.lang.reflect.Modifier.isStatic(field.getModifiers())) return;
            field.set(target, value);
        } catch (final ReflectiveOperationException | IllegalArgumentException e) {
            warnings.add("%s: %s".formatted(describe(op), e));
        }
    }

    private void invoke(FormScript.Invoke op, Evaluator evaluator) {
        if ("addPopup".equals(op.method()) && op.args().size() == 2) {
            final Object[] args = evaluator.evalAll(op.args());
            final ComponentInfo owner = args[0] instanceof final Component c ? infos.get(c) : null;
            final ComponentInfo popup = args[1] instanceof final Component c ? infos.get(c) : null;
            if (owner != null && popup != null) owner.attach(popup, Association.POPUP, op.statement(), null, null);
            return;
        }
        final Object target = evaluator.eval(op.target());
        if (target != null && !(target instanceof Component) && objects.containsKey(target)) {
            // gl.setHorizontalGroup(...), springLayout.putConstraint(...): merken und nachspielen
            final Object[] args = evaluator.evalAll(op.args());
            objects.get(target).getCalls().add(new PropertyInfo(op.method(), op.argTexts(), Arrays.asList(args), op.statement()));
            if (Evaluator.known(args) && Evaluator.isLayoutBuilder(target)) {
                try {
                    Reflection.invoke(target, op.method(), args);
                } catch (final ReflectiveOperationException | Reflection.NoSuchMember | RuntimeException e) {
                    warnings.add("%s: %s".formatted(describe(op), Reflection.cause(e)));
                }
            }
            return;
        }
        if (!(target instanceof final Component targetComponent)) return;
        final ComponentInfo info = infos.get(targetComponent);
        if (info == null) return;
        final Object[] args = evaluator.evalAll(op.args());
        final String method = op.method();

        if ("add".equals(method) && addChild(info, op, args)) return;
        if (("addTab".equals(method) || "insertTab".equals(method)) && addTab(info, op, args)) return;
        final Association association = CHILD_SETTERS.get(method);
        if (association != null && args.length == 1 && args[0] instanceof final Component child && infos.containsKey(child)) {
            setChild(info, op, infos.get(child), association);
            return;
        }
        if (method.startsWith("add") && method.endsWith("Listener") && args.length == 1) {
            info.getEvents().putIfAbsent(method, new PropertyInfo(method, op.argTexts(), Arrays.asList(args), op.statement()));
            return;
        }
        if (EXECUTED_CALLS.contains(method)) {
            // choice.add("one"), menu.addSeparator(), comboBox.addItem("x") …
            if (Evaluator.known(args)) apply(info.getLive(), method, args, op);
            return;
        }
        if (!method.startsWith("set")) return;

        // JFrame.setLayout & Co. gehen an die Content Pane – wie zur Laufzeit, und so zeigt es auch der WindowBuilder
        final ComponentInfo owner = "setLayout".equals(method) ? contentPaneOf(info) : info;
        owner.getProperties().remove(method);
        owner.getProperties().put(method, new PropertyInfo(method, op.argTexts(), Arrays.asList(args), op.statement()));
        if ("setLayout".equals(method)) owner.setLayoutObject(args.length == 1 && args[0] != null ? objects.get(args[0]) : null);
        if (NOT_EXECUTED.contains(method)) return;
        if (!Evaluator.known(args)) {
            warnings.add("%s: arguments could not be evaluated".formatted(describe(op)));
            return;
        }
        apply(owner.getLive(), method, args, op);
    }

    private boolean addChild(ComponentInfo container, FormScript.Invoke op, Object[] args) {
        int childIndex = -1;
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof final Component c && infos.containsKey(c)) {
                childIndex = i;
                break;
            }
        }
        final ComponentInfo target = contentPaneOf(container);
        if (childIndex < 0) {
            // Komponenten ohne Variable (Box.createHorizontalStrut …) werden gezeichnet, sind aber nicht auswählbar
            if (args.length > 0 && args[0] instanceof Component && Evaluator.known(args)) apply(target.getLive(), "add", args, op);
            return args.length > 0 && args[0] instanceof Component;
        }
        final ComponentInfo child = infos.get((Component) args[childIndex]);
        if (child == target || child.isAncestorOf(target)) return true;
        final int constraintsIndex = args.length == 2 ? 1 - childIndex : -1;
        final String constraints = constraintsIndex >= 0 ? op.argTexts().get(constraintsIndex) : null;
        final Object constraintsValue = constraintsIndex >= 0 ? args[constraintsIndex] : null;
        target.attach(child, Association.ADD, op.statement(), constraints, constraintsValue);
        final ObjectInfo constraintsObject = constraintsValue == null ? null : objects.get(constraintsValue);
        child.setConstraintsObject(constraintsObject);
        if (constraintsObject != null) constraintsObject.used();
        if (Evaluator.known(args)) apply(target.getLive(), "add", args, op);
        else ((Container) target.getLive()).add(child.getLive());
        return true;
    }

    private boolean addTab(ComponentInfo container, FormScript.Invoke op, Object[] args) {
        for (final Object arg : args) {
            if (arg instanceof final Component c && infos.containsKey(c)) {
                container.attach(infos.get(c), Association.TAB, op.statement(), op.argTexts().isEmpty() ? null : op.argTexts().get(0),
                        args.length > 0 ? args[0] : null);
                if (Evaluator.known(args)) apply(container.getLive(), op.method(), args, op);
                return true;
            }
        }
        return false;
    }

    private void setChild(ComponentInfo container, FormScript.Invoke op, ComponentInfo child, Association association) {
        if (association == Association.CONTENT_PANE) {
            // Die implizite Content Pane wird durch die gesetzte ersetzt
            for (final ComponentInfo existing : List.copyOf(container.getChildren())) {
                if (existing.getAssociation() == Association.IMPLICIT) container.getChildren().remove(existing);
            }
        }
        container.attach(child, association, op.statement(), null, null);
        apply(container.getLive(), op.method(), new Object[]{child.getLive()}, op);
    }

    private void apply(Component target, String method, Object[] args, FormScript.Op op) {
        Component receiver = target;
        if (target instanceof final WindowSurrogate window && ("add".equals(method) || "setLayout".equals(method) || "remove".equals(method))) {
            receiver = window.getContentPane();
        }
        try {
            Reflection.invoke(receiver, method, args);
        } catch (final Reflection.NoSuchMember e) {
            // Fenster-Methoden ohne Entsprechung im Ersatz (setIconImage, pack …) stören nicht
            if (!(receiver instanceof WindowSurrogate)) warnings.add("%s: %s".formatted(describe(op), e.getMessage()));
        } catch (final ReflectiveOperationException | RuntimeException e) {
            warnings.add("%s: %s".formatted(describe(op), Reflection.cause(e)));
        }
    }

    /** Für Fenster und JInternalFrame die Info der aktuellen Content Pane, sonst die Komponente selbst. */
    private ComponentInfo contentPaneOf(ComponentInfo info) {
        final Container pane = info.getLive() instanceof final WindowSurrogate window ? window.getContentPane()
                : info.getLive() instanceof final JInternalFrame frame ? frame.getContentPane() : null;
        final ComponentInfo paneInfo = pane == null ? null : infos.get(pane);
        return paneInfo != null ? paneInfo : info;
    }

    private void addImplicitContentPane(ComponentInfo owner) {
        final Container pane = owner.getLive() instanceof final WindowSurrogate window ? window.getContentPane()
                : owner.getLive() instanceof final JInternalFrame frame ? frame.getContentPane() : null;
        if (pane == null) return;
        // AWT-Fenster haben keine Content Pane: Kinder und Layout gehen direkt an das Fenster ("" = this)
        final boolean awt = owner.getLive() instanceof final WindowSurrogate window && window.getWindowClass().startsWith("java.awt.");
        final String accessor = awt ? (owner.getName() == null ? "" : owner.getName())
                : owner.getName() == null ? "getContentPane()" : owner.getName() + ".getContentPane()";
        final ComponentInfo info = new ComponentInfo(-1, null, false, pane.getClass().getName(), pane, false, null, List.of(), accessor);
        owner.attach(info, Association.IMPLICIT, null, null, null);
        register(info);
    }

    private @Nullable ComponentInfo findWindowRoot() {
        for (final ComponentInfo info : created) {
            if (info.getParent() == null && (info.getLive() instanceof WindowSurrogate || info.getLive() instanceof JInternalFrame)) return info;
        }
        for (final ComponentInfo info : created) {
            if (info.getParent() == null) return info;
        }
        return null;
    }

    private void register(ComponentInfo info) {
        infos.put(info.getLive(), info);
    }

    /**
     * Die Wurzel: erbt die Klasse von einer Projektklasse (z.B. {@code BasePanel}), wird diese erzeugt – mit ihren
     * Komponenten, wie im WindowBuilder. Fenster bleiben beim Ersatz.
     */
    private Component createRoot() {
        final String superClass = script.superClass();
        if (project != null && superClass != null && !superClass.equals(script.rootClass())) {
            final Class<?> cls = project.load(superClass);
            if (cls != null && Component.class.isAssignableFrom(cls) && !java.awt.Window.class.isAssignableFrom(cls)) {
                try {
                    final Object[] args = evaluator.evalAll(script.superArgs());
                    return (Component) Reflection.construct(cls, Evaluator.known(args) ? args : new Object[0]);
                } catch (final Exception | LinkageError e) {
                    warnings.add("%s cannot be instantiated: %s".formatted(superClass, Reflection.cause(e)));
                }
            }
        }
        return createInstance(script.rootClass(), script.superArgs());
    }

    /** Instanz einer JDK-Komponente; Fenster werden durch {@link WindowSurrogate} ersetzt, Unbekanntes durch ein JPanel. */
    private Component createInstance(@Nullable String className, List<Expr> args) {
        final Class<?> cls = TypeResolver.jdkClass(className);
        if (cls != null && java.awt.Window.class.isAssignableFrom(cls)) {
            final WindowSurrogate window = new WindowSurrogate(className);
            for (final Object arg : evaluator.evalAll(args)) {
                if (arg instanceof final String title) window.setTitle(title);
            }
            return window;
        }
        if (AwtProxies.isProxied(className)) {
            final Object[] argValues = evaluator.evalAll(args);
            return AwtProxies.create(className, Evaluator.known(argValues) ? argValues : new Object[0]);
        }
        if (cls != null && Component.class.isAssignableFrom(cls) && Evaluator.isInstantiable(className)) {
            try {
                final Object[] argValues = evaluator.evalAll(args);
                return (Component) Reflection.construct(cls, Evaluator.known(argValues) ? argValues : new Object[0]);
            } catch (final Exception | LinkageError e) {
                try {
                    return (Component) Reflection.construct(cls, new Object[0]);
                } catch (final Exception | LinkageError ignored) {
                    warnings.add("%s cannot be instantiated: %s".formatted(className, Reflection.cause(e)));
                }
            }
        }
        return new JPanel();
    }

    /** Fenster bekommen ihre Größe aus setBounds/setSize; Panels ohne Größe wie im WindowBuilder 450×300 oder ihre Preferred Size. */
    private static void layout(ComponentInfo root) {
        final Component live = root.getLive();
        if (live.getWidth() <= 0 || live.getHeight() <= 0) {
            live.setSize(root.getProperty("setPreferredSize") != null ? live.getPreferredSize() : DEFAULT_SIZE);
        }
        live.setLocation(0, 0);
        layoutTree(live);
    }

    public static void layoutTree(Component component) {
        if (component instanceof final Container container) {
            container.doLayout();
            for (final Component child : container.getComponents()) layoutTree(child);
        }
    }

    private static String describe(FormScript.Op op) {
        return switch (op) {
            case FormScript.Assign assign -> assign.name();
            case FormScript.Invoke invoke -> "%s(%s)".formatted(invoke.method(), String.join(", ", invoke.argTexts()));
            case FormScript.FieldAssign assign -> "%s = %s".formatted(assign.field(), assign.valueText());
        };
    }
}
