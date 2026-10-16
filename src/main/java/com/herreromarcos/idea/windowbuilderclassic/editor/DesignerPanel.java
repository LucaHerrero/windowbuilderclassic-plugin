package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.codegen.FormWriter;
import com.herreromarcos.idea.windowbuilderclassic.codegen.GridBagAlignment;
import com.herreromarcos.idea.windowbuilderclassic.codegen.GridBagWriter;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.model.FormScript;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.herreromarcos.idea.windowbuilderclassic.parser.FormParser;
import com.herreromarcos.idea.windowbuilderclassic.runtime.EventCatalog;
import com.herreromarcos.idea.windowbuilderclassic.runtime.FormBuilder;
import com.herreromarcos.idea.windowbuilderclassic.runtime.ProjectClasses;
import com.herreromarcos.idea.windowbuilderclassic.settings.WindowBuilderSettings;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.TreeClassChooser;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Die Design-Seite: links Structure (Components und Properties), daneben die Palette, rechts die Zeichenfläche – die
 * Aufteilung des WindowBuilder. Hält das aktuelle {@link FormModel} und baut es nach jeder PSI-Änderung neu.
 */
final class DesignerPanel extends JPanel implements Disposable, DesignCanvas.Host, PropertyTablePanel.Host {
    private final Project project;
    private final VirtualFile file;
    private final Alarm alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
    private final DesignCanvas canvas = new DesignCanvas(this);
    private final ComponentTreePanel tree;
    private final PropertyTablePanel properties = new PropertyTablePanel(this);
    private final PalettePanel palette;
    private final JBLabel status = new JBLabel();
    private @Nullable FormScript script;
    private @Nullable FormModel model;
    private @Nullable ComponentInfo selection;
    private @Nullable String selectedReference;
    private boolean selectionIsRoot = true;
    /** Alle ausgewählten Komponenten (Hauptauswahl zuerst) und ihre Verweise, um sie über ein Neu-Parsen zu retten. */
    private final java.util.List<ComponentInfo> selectionList = new java.util.ArrayList<>();
    private java.util.List<String> otherReferences = java.util.List.of();
    /** Zwischenablage für Komponenten (gilt für alle Design-Tabs der Sitzung). */
    private static FormWriter.@Nullable Clip clipboard;
    private @Nullable PaletteEntry tool;
    /** Größe von Panels, die ihre Größe nicht im Code tragen (nur für die Sitzung, wie im WindowBuilder). */
    private @Nullable Dimension panelSize;
    private boolean dirty = true;
    private boolean active;
    /** Klassen des Projekts für eigene Komponenten; neu nach jedem Build. */
    private @Nullable ProjectClasses projectClasses;
    private long buildGeneration;

    /** Ergebnis des Hintergrund-Parsens: Skript, Classpath des Moduls und verfügbare Layouts aus Bibliotheken. */
    private record Parsed(@Nullable FormScript script, java.util.List<String> classPath, java.util.List<PaletteEntry> thirdParty) {}

    DesignerPanel(Project project, VirtualFile file) {
        super(new BorderLayout());
        this.project = project;
        this.file = file;
        final PopupHandler popup = new PopupHandler() {
            @Override
            public void invokePopup(Component comp, int x, int y) {
                showPopup(comp, x, y);
            }
        };
        tree = new ComponentTreePanel(this::select, this::navigate, popup);
        palette = new PalettePanel(this::paletteSelected);

        final OnePixelSplitter structure = new OnePixelSplitter(true, "WindowBuilder.Structure", 0.45f);
        structure.setFirstComponent(titled("Components", tree));
        structure.setSecondComponent(titled("Properties", properties));

        final JBScrollPane canvasScroll = new JBScrollPane(canvas);
        canvasScroll.setBorder(JBUI.Borders.empty());
        final JPanel canvasArea = new JPanel(new BorderLayout());
        canvasArea.add(createToolbar(canvasArea), BorderLayout.NORTH);
        canvasArea.add(canvasScroll, BorderLayout.CENTER);

        final OnePixelSplitter paletteAndCanvas = new OnePixelSplitter(false, "WindowBuilder.Palette", 0.18f);
        paletteAndCanvas.setFirstComponent(titled("Palette", palette));
        paletteAndCanvas.setSecondComponent(canvasArea);

        final OnePixelSplitter main = new OnePixelSplitter(false, "WindowBuilder.Main", 0.25f);
        main.setFirstComponent(structure);
        main.setSecondComponent(paletteAndCanvas);
        add(main, BorderLayout.CENTER);

        registerShortcuts();
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeChangeAdapter() {
            @Override
            public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
                changed(event);
            }

            @Override
            public void childAdded(@NotNull PsiTreeChangeEvent event) {
                changed(event);
            }

            @Override
            public void childRemoved(@NotNull PsiTreeChangeEvent event) {
                changed(event);
            }

            @Override
            public void childReplaced(@NotNull PsiTreeChangeEvent event) {
                changed(event);
            }
        }, this);
        project.getMessageBus().connect(this).subscribe(com.intellij.task.ProjectTaskListener.TOPIC, new com.intellij.task.ProjectTaskListener() {
            @Override
            public void finished(com.intellij.task.ProjectTaskManager.@NotNull Result result) {
                // Neu kompilierte Klassen: neuer Loader beim nächsten Aufbau
                buildGeneration++;
                if (active) refresh();
                else dirty = true;
            }
        });
    }

    private static JComponent titled(String title, JComponent content) {
        final JPanel panel = new JPanel(new BorderLayout());
        final JLabel label = new JLabel(title);
        label.setBorder(JBUI.Borders.empty(4, 6));
        label.setFont(JBUI.Fonts.label().asBold());
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createToolbar(JComponent target) {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(action("Test/Preview", AllIcons.Actions.Execute, this::preview, () -> model != null));
        group.add(action("Refresh", AllIcons.Actions.Refresh, this::refresh, () -> true));
        group.add(action("Externalize Strings…", AllIcons.Nodes.ResourceBundle, this::externalizeStrings, () -> model != null));
        group.add(Separator.getInstance());
        group.add(action("Delete", AllIcons.General.Remove, this::deleteSelection, this::canDeleteAll));
        group.add(action("Go to Source", AllIcons.Actions.EditSource, () -> {
            if (selection != null) navigate(selection);
        }, () -> selection != null));
        group.add(Separator.getInstance());
        addAlignmentActions(group);
        addAbsoluteActions(group);
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("WindowBuilder", group, true);
        toolbar.setTargetComponent(target);
        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(toolbar.getComponent(), BorderLayout.WEST);
        status.setBorder(JBUI.Borders.emptyRight(8));
        panel.add(status, BorderLayout.EAST);
        return panel;
    }

    private static AnAction action(String text, javax.swing.Icon icon, Runnable run, java.util.function.BooleanSupplier enabled) {
        return new AnAction(text, null, icon) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                run.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(enabled.getAsBoolean());
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    private void registerShortcuts() {
        final JComponent canvasLayer = canvas.getEventLayer();
        for (final JComponent target : new JComponent[]{canvasLayer, tree.getTree()}) {
            action("Delete", AllIcons.General.Remove, this::deleteSelection, this::canDelete)
                    .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0)), target);
        }
        action("Selection", null, () -> paletteSelected(Palette.SELECTION), () -> true)
                .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0)), canvasLayer);
        action("Edit Text", null, this::editTextInPlace, () -> selection != null)
                .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), canvasLayer);
        final int menu = com.intellij.openapi.util.SystemInfo.isMac ? java.awt.event.InputEvent.META_DOWN_MASK : java.awt.event.InputEvent.CTRL_DOWN_MASK;
        for (final JComponent target : new JComponent[]{canvasLayer, tree.getTree()}) {
            action("Copy", null, this::copySelection, this::canDelete)
                    .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_C, menu)), target);
            action("Cut", null, this::cutSelection, this::canDelete)
                    .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_X, menu)), target);
            action("Paste", null, this::pasteClipboard, () -> clipboard != null)
                    .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_V, menu)), target);
        }
        // Pfeiltasten verschieben im Absolute Layout um 1 Pixel, mit Shift um 10
        final int[][] moves = {{KeyEvent.VK_LEFT, -1, 0}, {KeyEvent.VK_RIGHT, 1, 0}, {KeyEvent.VK_UP, 0, -1}, {KeyEvent.VK_DOWN, 0, 1}};
        for (final int[] move : moves) {
            action("Move", null, () -> nudge(move[1], move[2]), () -> true)
                    .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(move[0], 0)), canvasLayer);
            action("Move", null, () -> nudge(move[1] * 10, move[2] * 10), () -> true)
                    .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(move[0], java.awt.event.InputEvent.SHIFT_DOWN_MASK)), canvasLayer);
        }
    }

    // ---- Aktualisieren

    private void changed(PsiTreeChangeEvent event) {
        final PsiFile changed = event.getFile();
        if (changed == null || !file.equals(changed.getVirtualFile())) return;
        if (!active) {
            dirty = true;
            return;
        }
        alarm.cancelAllRequests();
        alarm.addRequest(this::refresh, 300);
    }

    void setActive(boolean active) {
        this.active = active;
        if (active && dirty) refresh();
    }

    void refresh() {
        dirty = false;
        ReadAction.nonBlocking(() -> {
                    final PsiFile psiFile = psiFile();
                    final FormScript parsed = psiFile == null ? null : FormParser.parse(psiFile);
                    final com.intellij.openapi.module.Module module = com.intellij.openapi.module.ModuleUtilCore.findModuleForFile(file, project);
                    final boolean run = WindowBuilderSettings.getInstance(project).getState().runProjectCode;
                    final java.util.List<String> classPath = module == null || !run ? java.util.List.of()
                            : com.intellij.openapi.roots.OrderEnumerator.orderEntries(module).recursively().withoutSdk().getPathsList().getPathList();
                    return new Parsed(parsed, classPath, module == null ? java.util.List.of() : Palette.thirdPartyLayouts(name ->
                            JavaPsiFacade.getInstance(project).findClass(name, GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)) != null));
                })
                .inSmartMode(project)
                .expireWith(this)
                .coalesceBy(this)
                .finishOnUiThread(ModalityState.any(), this::apply)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private void apply(Parsed parsed) {
        final FormScript newScript = parsed.script();
        script = newScript;
        palette.setThirdParty(parsed.thirdParty());
        if (parsed.classPath().isEmpty()) {
            if (projectClasses != null) projectClasses.close();
            projectClasses = null;
        } else if (projectClasses == null || !projectClasses.matches(parsed.classPath(), buildGeneration)) {
            if (projectClasses != null) projectClasses.close();
            projectClasses = new ProjectClasses(parsed.classPath(), buildGeneration);
        }
        model = newScript == null ? null : FormBuilder.build(newScript, projectClasses);
        if (model != null && panelSize != null && !(model.getRoot().getLive() instanceof WindowSurrogate)
                && model.getRoot().getProperty("setPreferredSize") == null && model.getRoot().getProperty("setSize") == null) {
            model.getRoot().getLive().setSize(panelSize);
            FormBuilder.layoutTree(model.getRoot().getLive());
        }
        canvas.setModel(model, model == null ? "No Swing GUI found: the class neither extends a Swing component nor creates a window in its constructor." : null);
        tree.setModel(model);
        ComponentInfo restored = null;
        if (model != null) restored = selectionIsRoot ? model.getRoot() : model.findByReference(selectedReference);
        final java.util.List<ComponentInfo> list = new java.util.ArrayList<>();
        list.add(restored != null ? restored : model == null ? null : model.getRoot());
        if (model != null && restored != null) {
            for (final String reference : otherReferences) {
                final ComponentInfo other = model.findByReference(reference);
                if (other != null && !list.contains(other)) list.add(other);
            }
        }
        if (list.get(0) == null) list.clear();
        setSelection(list);
        updateStatus();
    }

    private void updateStatus() {
        if (model == null || model.getWarnings().isEmpty() && model.getScript().problems().isEmpty()) {
            status.setText("");
            status.setIcon(null);
            status.setToolTipText(null);
            return;
        }
        final java.util.List<String> all = new java.util.ArrayList<>(model.getScript().problems());
        all.addAll(model.getWarnings());
        status.setIcon(AllIcons.General.Warning);
        status.setText(all.size() == 1 ? "1 problem" : "%d problems".formatted(all.size()));
        status.setToolTipText("<html>" + StringUtil.join(all, StringUtil::escapeXmlEntities, "<br>") + "</html>");
    }

    private @Nullable PsiFile psiFile() {
        return file.isValid() ? PsiManager.getInstance(project).findFile(file) : null;
    }

    // ---- Host

    @Override
    public @Nullable FormModel model() {
        return model;
    }

    @Override
    public @Nullable ComponentInfo selection() {
        return selection;
    }

    @Override
    public void select(@Nullable ComponentInfo info) {
        setSelection(info == null ? java.util.List.of() : java.util.List.of(info));
    }

    @Override
    public java.util.List<ComponentInfo> selectionList() {
        return java.util.Collections.unmodifiableList(selectionList);
    }

    @Override
    public void setSelection(java.util.List<ComponentInfo> infos) {
        selectionList.clear();
        selectionList.addAll(infos);
        selection = infos.isEmpty() ? null : infos.get(0);
        if (selection != null) {
            selectedReference = selection.getReference();
            selectionIsRoot = model != null && selection == model.getRoot();
        }
        final java.util.List<String> others = new java.util.ArrayList<>();
        for (int i = 1; i < infos.size(); i++) others.add(infos.get(i).getReference());
        otherReferences = others;
        showCards(selection);
        tree.select(selection);
        properties.show(selection);
        canvas.repaint();
    }

    /** Im CardLayout die Karte der Auswahl (bzw. ihres Vorfahren) zeigen, wie im WindowBuilder. */
    private void showCards(@Nullable ComponentInfo info) {
        boolean changed = false;
        for (ComponentInfo c = info; c != null && c.getParent() != null; c = c.getParent()) {
            if (c.getParent().getLive() instanceof final Container parent && parent.getLayout() instanceof final java.awt.CardLayout cards
                    && c.getConstraintsValue() instanceof final String name && !c.getLive().isVisible()) {
                cards.show(parent, name);
                changed = true;
            }
        }
        if (changed) canvas.updateSize();
    }

    @Override
    public void toggleSelection(ComponentInfo info) {
        final java.util.List<ComponentInfo> list = new java.util.ArrayList<>(selectionList);
        if (!list.remove(info)) list.add(info);
        setSelection(list);
    }

    @Override
    public void moveAll(java.util.Map<ComponentInfo, Rectangle> bounds) {
        final FormWriter writer = writer();
        if (writer != null) writer.setBoundsAll(bounds);
    }

    @Override
    public void resizeSpan(ComponentInfo component, int width, int height) {
        final FormWriter writer = writer();
        if (writer != null) writer.setGridBagSpan(component, width, height);
    }

    // ---- Kopieren, Ausschneiden, Einfügen

    private void copySelection() {
        if (!canDelete() || selection == null) return;
        final FormWriter writer = writer();
        final FormWriter.Clip clip = writer == null ? null : writer.copy(selection);
        if (clip == null) return;
        clipboard = clip;
        // Der Code landet zusätzlich als Text in der Zwischenablage
        com.intellij.openapi.ide.CopyPasteManager.getInstance().setContents(new java.awt.datatransfer.StringSelection(String.join("\n", clip.statements())));
    }

    private void cutSelection() {
        copySelection();
        if (clipboard != null) deleteSelection();
    }

    /** Einfügen in den ausgewählten Container bzw. den Container der Auswahl; im Absolute Layout leicht versetzt. */
    private void pasteClipboard() {
        final FormWriter.Clip clip = clipboard;
        if (clip == null || model == null) return;
        ComponentInfo target = selection == null ? model.getRoot() : selection;
        while (target != null && !DropTargets.isDropContainer(target)) target = target.getParent();
        if (target == null) return;
        final ComponentInfo container = DropTargets.dropContainer(model, target);
        final com.herreromarcos.idea.windowbuilderclassic.codegen.Placement placement;
        if (container.getLive() instanceof final Container c && c.getLayout() == null) {
            final ComponentInfo original = model.findByReference(clip.name());
            final Rectangle bounds = original != null ? new Rectangle(original.getLive().getBounds()) : new Rectangle(10, 10, 100, 25);
            bounds.translate(10, 10);
            placement = com.herreromarcos.idea.windowbuilderclassic.codegen.Placement.absolute(bounds);
        } else {
            placement = com.herreromarcos.idea.windowbuilderclassic.codegen.Placement.append();
        }
        final FormWriter writer = writer();
        if (writer == null) return;
        selectedReference = writer.paste(container, placement, clip);
        selectionIsRoot = false;
        otherReferences = java.util.List.of();
    }

    // ---- Absolute Layout: Pfeiltasten und Ausrichtungswerkzeuge des WindowBuilder

    /** Die ausgewählten Komponenten, wenn alle im selben Absolute Layout liegen. */
    private java.util.List<ComponentInfo> absoluteSelection() {
        if (selectionList.isEmpty()) return java.util.List.of();
        final ComponentInfo parent = selectionList.get(0).getParent();
        for (final ComponentInfo info : selectionList) {
            if (info.getParent() != parent || !DropTargets.isPositionalChild(info)) return java.util.List.of();
        }
        return selectionList;
    }

    private void nudge(int dx, int dy) {
        final java.util.List<ComponentInfo> selected = absoluteSelection();
        if (selected.isEmpty()) return;
        final java.util.Map<ComponentInfo, Rectangle> bounds = new java.util.LinkedHashMap<>();
        for (final ComponentInfo info : selected) {
            final Rectangle r = new Rectangle(info.getLive().getBounds());
            r.translate(dx, dy);
            bounds.put(info, r);
        }
        moveAll(bounds);
    }

    private void addAbsoluteActions(DefaultActionGroup group) {
        group.add(Separator.getInstance());
        for (final AbsoluteAlignment alignment : AbsoluteAlignment.values()) {
            group.add(new AnAction(alignment.title, null, ComponentIcons.absolute(alignment.name())) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    final java.util.List<ComponentInfo> selected = absoluteSelection();
                    final Dimension container = selected.isEmpty() ? new Dimension() : selected.get(0).getParent().getLive().getSize();
                    if (!selected.isEmpty()) moveAll(alignment.apply(selected, container));
                }

                @Override
                public void update(@NotNull AnActionEvent e) {
                    final int count = absoluteSelection().size();
                    e.getPresentation().setVisible(count >= 1);
                    e.getPresentation().setEnabled(count >= alignment.minimum);
                }

                @Override
                public @NotNull ActionUpdateThread getActionUpdateThread() {
                    return ActionUpdateThread.EDT;
                }
            });
        }
    }

    @Override
    public @Nullable PaletteEntry tool() {
        return tool;
    }

    @Override
    public void toolUsed(boolean keep) {
        if (!keep) paletteSelected(Palette.SELECTION);
    }

    private void paletteSelected(PaletteEntry entry) {
        switch (entry.kind()) {
            case SELECTION -> tool = null;
            case CHOOSE_COMPONENT -> tool = chooseComponent();
            default -> tool = entry;
        }
        final boolean custom = tool != null && Palette.categories().values().stream().noneMatch(entries -> entries.contains(tool));
        palette.select(custom ? Palette.CHOOSE_COMPONENT : tool);
        canvas.repaint();
    }

    private @Nullable PaletteEntry chooseComponent() {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiClass base = facade.findClass("java.awt.Component", GlobalSearchScope.allScope(project));
        final TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(project);
        final TreeClassChooser chooser = base != null
                ? factory.createInheritanceClassChooser("Choose Component", GlobalSearchScope.allScope(project), base, null)
                : factory.createProjectScopeChooser("Choose Component");
        chooser.showDialog();
        final PsiClass selected = chooser.getSelected();
        return selected == null || selected.getQualifiedName() == null ? null : Palette.custom(selected.getQualifiedName());
    }

    @Override
    public @Nullable FormWriter writer() {
        final PsiFile psiFile = psiFile();
        return model == null || psiFile == null || !psiFile.isWritable() && !ensureWritable() ? null : new FormWriter(model, psiFile);
    }

    private boolean ensureWritable() {
        return !com.intellij.openapi.vfs.ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(java.util.List.of(file)).hasReadonlyFiles();
    }

    @Override
    public void create(PaletteEntry entry, DropTargets.Drop drop) {
        final FormWriter writer = writer();
        if (writer == null) return;
        final String name = writer.addComponent(drop.container(), entry, drop.placement());
        selectedReference = name;
        selectionIsRoot = false;
    }

    @Override
    public void move(ComponentInfo component, DropTargets.Drop drop) {
        final FormWriter writer = writer();
        if (writer != null) writer.move(component, drop.container(), drop.placement());
    }

    @Override
    public void resize(ComponentInfo component, Rectangle bounds) {
        final FormWriter writer = writer();
        if (writer != null) writer.setBoundsAll(java.util.Map.of(component, bounds));
    }

    @Override
    public void resizeForm(Dimension size) {
        if (model == null) return;
        final ComponentInfo root = model.getRoot();
        final boolean inCode = root.getLive() instanceof WindowSurrogate || root.getLive() instanceof javax.swing.JInternalFrame
                || root.getProperty("setPreferredSize") != null || root.getProperty("setSize") != null || root.getProperty("setBounds") != null;
        if (inCode) {
            final FormWriter writer = writer();
            if (writer != null) writer.setDesignSize(size);
        } else {
            panelSize = size;
            root.getLive().setSize(size);
            FormBuilder.layoutTree(root.getLive());
            canvas.updateSize();
        }
    }

    @Override
    public void applyLayout(ComponentInfo container, PaletteEntry layout) {
        final FormWriter writer = writer();
        if (writer != null) writer.setLayout(container, layout);
    }

    /** Doppelklick auf der Zeichenfläche: Standard-Event (actionPerformed) wie im WindowBuilder, sonst zum Quelltext. */
    @Override
    public void activate(ComponentInfo component) {
        final Class<?> cls = eventClass(component);
        final EventCatalog.EventSet set = cls == null || component.isImplicit() || component.isThis() ? null : EventCatalog.defaultEvent(cls);
        if (set != null && !set.methods().isEmpty()) {
            openEvent(component, set, set.methods().get(0));
        } else {
            navigate(component);
        }
    }

    @Override
    public void navigate(ComponentInfo component) {
        PsiElement element = component.getCreation() != null ? component.getCreation().getElement() : null;
        if (element == null && component.getAssociationStatement() != null) element = component.getAssociationStatement().getElement();
        if (element == null) {
            final PsiFile psiFile = psiFile();
            final PsiClass cls = psiFile == null ? null : FormParser.findFormClass(psiFile);
            element = cls == null ? null : cls.getNameIdentifier();
        }
        if (element != null) new OpenFileDescriptor(project, file, element.getTextOffset()).navigate(true);
    }

    @Override
    public void showPopup(MouseEvent event) {
        showPopup(event.getComponent(), event.getX(), event.getY());
    }

    // ---- Ausrichtung im GridBagLayout (Symbolleiste des WindowBuilder)

    /** Constraints der Auswahl, wenn sie in einem GridBagLayout liegt. */
    private java.awt.@Nullable GridBagConstraints gridConstraints() {
        if (selection == null || selection.getParent() == null || selection.getAssociation() != ComponentInfo.Association.ADD
                || !(selection.getParent().getLive() instanceof final Container parent)
                || !(parent.getLayout() instanceof final java.awt.GridBagLayout layout)) {
            return null;
        }
        return layout.getConstraints(selection.getLive());
    }

    private void addAlignmentActions(DefaultActionGroup group) {
        for (final GridBagAlignment.Horizontal h : GridBagAlignment.Horizontal.values()) {
            group.add(alignmentAction("Horizontal: " + StringUtil.capitalize(h.name().toLowerCase()), ComponentIcons.alignment(true, h.name()),
                    c -> GridBagAlignment.horizontal(c) == h, () -> gridEdit(w -> w.setGridBagAlignment(selection, h, null))));
        }
        group.add(Separator.getInstance());
        for (final GridBagAlignment.Vertical v : GridBagAlignment.Vertical.values()) {
            group.add(alignmentAction("Vertical: " + StringUtil.capitalize(v.name().toLowerCase()), ComponentIcons.alignment(false, v.name()),
                    c -> GridBagAlignment.vertical(c) == v, () -> gridEdit(w -> w.setGridBagAlignment(selection, null, v))));
        }
        group.add(Separator.getInstance());
        group.add(alignmentAction("Grow Column", ComponentIcons.grow(true), c -> grows(true), () -> toggleGrow(true)));
        group.add(alignmentAction("Grow Row", ComponentIcons.grow(false), c -> grows(false), () -> toggleGrow(false)));
    }

    /** Nur sichtbar, solange eine Komponente im GridBagLayout ausgewählt ist. */
    private AnAction alignmentAction(String text, javax.swing.Icon icon, java.util.function.Predicate<java.awt.GridBagConstraints> selected,
                                     Runnable run) {
        return new com.intellij.openapi.actionSystem.ToggleAction(text, null, icon) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                final java.awt.GridBagConstraints c = gridConstraints();
                return c != null && selected.test(c);
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                run.run();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                super.update(e);
                e.getPresentation().setVisible(gridConstraints() != null);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
    }

    /** Wächst die Spalte bzw. Zeile der Auswahl mit? */
    private boolean grows(boolean column) {
        if (gridConstraints() == null) return false;
        final java.awt.GridBagLayout layout = (java.awt.GridBagLayout) ((Container) selection.getParent().getLive()).getLayout();
        final double[] weights = layout.getLayoutWeights()[column ? 0 : 1];
        final GridBagWriter.Slot slot = GridBagWriter.slotOf(layout, selection.getLive());
        final int index = column ? slot.x() : slot.y();
        return index < weights.length && weights[index] > 0 && weights[index] != Double.MIN_VALUE;
    }

    private void toggleGrow(boolean column) {
        if (gridConstraints() == null) return;
        final ComponentInfo container = selection.getParent();
        final java.awt.GridBagLayout layout = (java.awt.GridBagLayout) ((Container) container.getLive()).getLayout();
        final GridBagWriter.Slot slot = GridBagWriter.slotOf(layout, selection.getLive());
        final boolean grows = grows(column);
        gridEdit(w -> w.setGridWeight(container, column, column ? slot.x() : slot.y(), grows ? 0.0 : 1.0));
    }

    // ---- NLS

    private void externalizeStrings() {
        final FormWriter writer = writer();
        if (writer == null) return;
        final java.util.List<FormWriter.Externalizable> items = writer.externalizableStrings();
        if (items.isEmpty()) {
            Messages.showInfoMessage(project, "There are no string literals left to externalize.", "Externalize Strings");
            return;
        }
        final PsiFile psiFile = psiFile();
        final String packageName = psiFile instanceof final com.intellij.psi.PsiJavaFile java ? java.getPackageName() : "";
        final ExternalizeDialog dialog = new ExternalizeDialog(project, items, packageName.isEmpty() ? "messages" : packageName + ".messages");
        if (dialog.showAndGet() && !dialog.chosen().isEmpty() && !dialog.bundle().isEmpty()) writer.externalize(dialog.chosen(), dialog.bundle());
    }

    // ---- Events

    @Override
    public void openEvent(ComponentInfo component, EventCatalog.EventSet set, EventCatalog.EventMethod method) {
        final FormWriter writer = writer();
        if (writer == null) return;
        final com.intellij.psi.SmartPsiElementPointer<PsiElement> target = writer.addEventHandler(component, set, method);
        final PsiElement element = target == null ? null : target.getElement();
        if (element == null) return;
        // In den Rumpf der Handler-Methode springen
        int offset = element.getTextOffset();
        if (element instanceof final com.intellij.psi.PsiMethod handler && handler.getBody() != null && handler.getBody().getLBrace() != null) {
            offset = handler.getBody().getLBrace().getTextRange().getEndOffset();
        } else if (element instanceof final com.intellij.psi.PsiCodeBlock block && block.getLBrace() != null) {
            offset = block.getLBrace().getTextRange().getEndOffset();
        }
        new OpenFileDescriptor(project, file, offset).navigate(true);
    }

    @Override
    public void removeEvent(ComponentInfo component, EventCatalog.EventSet set, EventCatalog.EventMethod method) {
        final FormWriter writer = writer();
        if (writer != null) writer.removeEventHandler(component, set, method);
    }

    private static @Nullable Class<?> eventClass(ComponentInfo info) {
        return info.getLive() instanceof final WindowSurrogate window
                ? com.herreromarcos.idea.windowbuilderclassic.parser.TypeResolver.jdkClass(window.getWindowClass()) : info.getLive().getClass();
    }

    /** Untermenü „Add Event Handler“ wie im WindowBuilder: Listener-Typ → Methode. */
    private @Nullable DefaultActionGroup eventMenu(ComponentInfo info) {
        final Class<?> cls = eventClass(info);
        if (cls == null || info.isImplicit()) return null;
        final DefaultActionGroup menu = DefaultActionGroup.createPopupGroup(() -> "Add Event Handler");
        for (final EventCatalog.EventSet set : EventCatalog.of(cls)) {
            final DefaultActionGroup sub = DefaultActionGroup.createPopupGroup(set::name);
            final java.util.List<String> implemented = FormWriter.handlerMethods(info, set.addMethod());
            for (final EventCatalog.EventMethod method : set.methods()) {
                final String label = implemented.contains(method.name()) ? method.name() + " ✓" : method.name();
                sub.add(action(label, null, () -> openEvent(info, set, method), () -> true));
            }
            menu.add(sub);
        }
        return menu;
    }

    @Override
    public void gridHeader(ComponentInfo container, boolean column, int index, MouseEvent event) {
        final String kind = column ? "Column" : "Row";
        final java.awt.GridBagLayout layout = (java.awt.GridBagLayout) ((Container) container.getLive()).getLayout();
        final double[] weights = layout.getLayoutWeights()[column ? 0 : 1];
        final boolean grows = index < weights.length && weights[index] > 0;
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(action("Insert %s Before".formatted(kind), null, () -> gridEdit(w -> w.insertGridLine(container, column, index)), () -> true));
        group.add(action("Insert %s After".formatted(kind), null, () -> gridEdit(w -> w.insertGridLine(container, column, index + 1)), () -> true));
        group.add(action("Delete %s".formatted(kind), AllIcons.General.Remove, () -> gridEdit(w -> w.deleteGridLine(container, column, index)), () -> true));
        final DefaultActionGroup alignment = DefaultActionGroup.createPopupGroup(() -> "Alignment");
        if (column) {
            for (final GridBagAlignment.Horizontal h : GridBagAlignment.Horizontal.values()) {
                alignment.add(action(StringUtil.capitalize(h.name().toLowerCase()), ComponentIcons.alignment(true, h.name()),
                        () -> gridEdit(w -> w.setGridLineAlignment(container, true, index, h, null)), () -> true));
            }
        } else {
            for (final GridBagAlignment.Vertical v : GridBagAlignment.Vertical.values()) {
                alignment.add(action(StringUtil.capitalize(v.name().toLowerCase()), ComponentIcons.alignment(false, v.name()),
                        () -> gridEdit(w -> w.setGridLineAlignment(container, false, index, null, v)), () -> true));
            }
        }
        group.add(Separator.getInstance());
        group.add(alignment);
        group.add(new com.intellij.openapi.actionSystem.ToggleAction("Grow", "Weight 1.0: the %s takes up extra space".formatted(kind.toLowerCase()), null) {
            @Override
            public boolean isSelected(@NotNull AnActionEvent e) {
                return grows;
            }

            @Override
            public void setSelected(@NotNull AnActionEvent e, boolean state) {
                gridEdit(w -> w.setGridWeight(container, column, index, state ? 1.0 : 0.0));
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });
        ActionManager.getInstance().createActionPopupMenu("WindowBuilder", group).getComponent().show(event.getComponent(), event.getX(), event.getY());
    }

    private void gridEdit(java.util.function.Consumer<FormWriter> edit) {
        final FormWriter writer = writer();
        if (writer != null) edit.accept(writer);
    }

    private void showPopup(Component component, int x, int y) {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(action("Go to Source", AllIcons.Actions.EditSource, () -> {
            if (selection != null) navigate(selection);
        }, () -> selection != null));
        group.add(action("Rename…", null, this::renameSelection, () -> selection != null && selection.getName() != null));
        group.add(action("Edit Text", null, this::editTextInPlace, () -> textOf(selection) != null));
        group.add(action("Cut", AllIcons.Actions.MenuCut, this::cutSelection, this::canDelete));
        group.add(action("Copy", AllIcons.Actions.Copy, this::copySelection, this::canDelete));
        group.add(action("Paste", AllIcons.Actions.MenuPaste, this::pasteClipboard, () -> clipboard != null));
        group.add(action("Delete", AllIcons.General.Remove, this::deleteSelection, this::canDeleteAll));
        final FormWriter conversions = selection == null || selection.getName() == null ? null : writer();
        if (conversions != null && conversions.canConvertToField(selection)) {
            final ComponentInfo target = selection;
            group.add(action("Convert Local to Field", null, () -> conversions.convertToField(target), () -> true));
        } else if (conversions != null && conversions.canConvertToLocal(selection)) {
            final ComponentInfo target = selection;
            group.add(action("Convert Field to Local", null, () -> conversions.convertToLocal(target), () -> true));
        }
        final DefaultActionGroup events = selection == null ? null : eventMenu(selection);
        if (events != null) {
            group.add(Separator.getInstance());
            group.add(events);
        }
        if (gridConstraints() != null) {
            final DefaultActionGroup alignment = DefaultActionGroup.createPopupGroup(() -> "Alignment");
            addAlignmentActions(alignment);
            group.add(Separator.getInstance());
            group.add(alignment);
        }
        if (selection != null && model != null && DropTargets.isDropContainer(selection)) {
            final ComponentInfo container = DropTargets.dropContainer(model, selection);
            final DefaultActionGroup layouts = DefaultActionGroup.createPopupGroup(() -> "Set Layout");
            for (final PaletteEntry layout : Palette.layouts()) {
                layouts.add(action(layout.title(), ComponentIcons.forEntry(layout), () -> applyLayout(container, layout), () -> true));
            }
            group.add(Separator.getInstance());
            group.add(layouts);
        }
        group.add(Separator.getInstance());
        group.add(action("Test/Preview", AllIcons.Actions.Execute, this::preview, () -> model != null));
        final ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu("WindowBuilder", group);
        menu.getComponent().show(component, x, y);
    }

    // ---- Befehle

    private boolean canDeleteAll() {
        for (final ComponentInfo info : selectionList) {
            if (info.isThis() || info.isImplicit() || model != null && info == model.getRoot()) return false;
        }
        return !selectionList.isEmpty();
    }

    private boolean canDelete() {
        return selection != null && !selection.isThis() && !selection.isImplicit() && model != null && selection != model.getRoot();
    }

    private void deleteSelection() {
        if (!canDeleteAll()) return;
        final FormWriter writer = writer();
        if (writer == null) return;
        final ComponentInfo parent = selectionList.get(0).getParent();
        final java.util.List<ComponentInfo> doomed = java.util.List.copyOf(selectionList);
        selectedReference = parent == null ? null : parent.getReference();
        selectionIsRoot = parent == null || parent == (model == null ? null : model.getRoot());
        otherReferences = java.util.List.of();
        if (doomed.size() == 1) writer.delete(doomed.get(0));
        else writer.deleteAll(doomed);
    }


    private void renameSelection() {
        if (selection == null || selection.getName() == null) return;
        final ComponentInfo target = selection;
        final FormWriter writer = writer();
        if (writer == null) return;
        final String name = Messages.showInputDialog(project, "New name:", "Rename Variable", null, target.getName(),
                new com.intellij.openapi.ui.InputValidator() {
                    @Override
                    public boolean checkInput(String input) {
                        return writer.isValidNewName(target, input.trim());
                    }

                    @Override
                    public boolean canClose(String input) {
                        return checkInput(input);
                    }
                });
        if (name == null) return;
        selectedReference = name.trim();
        writer.rename(target, name.trim());
    }

    private static @Nullable String textOf(@Nullable ComponentInfo info) {
        if (info == null) return null;
        final Component live = info.getLive();
        if (live instanceof final AbstractButton button) return button.getText();
        if (live instanceof final JLabel label) return label.getText();
        if (live instanceof final javax.swing.text.JTextComponent text) return text.getText();
        if (live instanceof final WindowSurrogate window) return window.getTitle();
        return null;
    }

    /** Direktes Bearbeiten des Texts wie im WindowBuilder (Leertaste). */
    private void editTextInPlace() {
        final ComponentInfo target = selection;
        final String text = textOf(target);
        if (target == null || text == null) return;
        final String setter = target.getLive() instanceof WindowSurrogate ? "setTitle" : "setText";
        final JBTextField field = new JBTextField(text);
        final Rectangle bounds = canvas.canvasBounds(target);
        field.setPreferredSize(new Dimension(Math.max(bounds.width, 120), field.getPreferredSize().height));
        final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(field, field)
                .setRequestFocus(true).setCancelOnClickOutside(true).createPopup();
        field.addActionListener(e -> {
            popup.closeOk(null);
            final FormWriter writer = writer();
            if (writer != null && !field.getText().equals(text)) {
                writer.setProperty(target, setter, "\"%s\"".formatted(StringUtil.escapeStringCharacters(field.getText())));
            }
        });
        popup.show(new RelativePoint(canvas, bounds.getLocation()));
    }

    /** Test/Preview: das Formular frisch erzeugt in einem echten Fenster, bedienbar. */
    private void preview() {
        if (script == null) return;
        final FormModel fresh = FormBuilder.build(script, projectClasses);
        if (fresh == null) return;
        final JDialog dialog = new JDialog(WindowManager.getInstance().getFrame(project), false);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        final Component root = fresh.getRoot().getLive();
        if (root instanceof final WindowSurrogate window) {
            final JRootPane rootPane = window.getRootPane();
            dialog.setTitle(window.getTitle());
            dialog.setJMenuBar(rootPane.getJMenuBar());
            final Container content = rootPane.getContentPane();
            rootPane.setContentPane(new JPanel());
            dialog.setContentPane(content);
            dialog.setSize(root.getSize());
        } else {
            dialog.setTitle("Preview – " + fresh.getRoot().getShortClassName());
            final JPanel content = new JPanel(new BorderLayout());
            content.add(root, BorderLayout.CENTER);
            content.setPreferredSize(root.getSize());
            dialog.setContentPane(content);
            dialog.pack();
        }
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    JComponent getPreferredFocusedComponent() {
        return canvas.getEventLayer();
    }

    @Override
    public void dispose() {
        if (projectClasses != null) projectClasses.close();
    }
}
