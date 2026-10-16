package com.herreromarcos.idea.windowbuilderclassic.codegen;

import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo.Association;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.model.ObjectInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.PropertyInfo;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.herreromarcos.idea.windowbuilderclassic.parser.Bundles;
import com.herreromarcos.idea.windowbuilderclassic.parser.FormParser;
import com.herreromarcos.idea.windowbuilderclassic.runtime.EventCatalog;
import com.herreromarcos.idea.windowbuilderclassic.settings.WindowBuilderSettings;
import com.herreromarcos.idea.windowbuilderclassic.runtime.WindowSurrogate;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schreibt Änderungen aus dem Designer als Java-Code zurück, im Stil des WindowBuilder: lokale Variablen (Textfelder
 * und Tabellen als Felder), Anweisungen flach hintereinander – Erzeugung, Eigenschaften, {@code add} – und Blöcke
 * {@code { … }}, wo der vorhandene Code schon Blöcke verwendet. Jede öffentliche Methode ist ein Undo-Schritt.
 */
public final class FormWriter {
    private final Project project;
    private final PsiFile file;
    private final PsiClass formClass;
    private final FormModel model;
    private final PsiElementFactory factory;
    private final List<PsiElement> touched = new ArrayList<>();
    private @Nullable PsiCodeBlock createdBody;
    private final GridBagWriter gridBag = new GridBagWriter(this);
    private final FreeLayoutWriter free = new FreeLayoutWriter(this);
    private final WindowBuilderSettings.State settings;

    public FormWriter(FormModel model, PsiFile file) {
        this.model = model;
        this.file = file;
        this.project = file.getProject();
        this.formClass = FormParser.findFormClass(file);
        this.factory = JavaPsiFacade.getElementFactory(project);
        this.settings = WindowBuilderSettings.getInstance(project).getState();
    }

    WindowBuilderSettings.State settings() {
        return settings;
    }

    // ---- Öffentliche Operationen

    /** Neue Komponente aus der Palette; liefert den Variablennamen. */
    public String addComponent(ComponentInfo container, PaletteEntry entry, Placement placement) {
        final String[] result = new String[1];
        command("Add " + entry.simpleName(), () -> result[0] = doAdd(container, entry, placement));
        return result[0];
    }

    /** Setzt (oder entfernt bei {@code args == null}) den Aufruf {@code method(args)} auf der Komponente. */
    public void setProperty(ComponentInfo component, String method, @Nullable String args) {
        command("Set " + StringUtil.decapitalize(StringUtil.trimStart(method, "set")), () -> doSetProperty(component, method, args));
    }

    public void setLayout(ComponentInfo container, PaletteEntry layout) {
        command("Set " + layout.title(), () -> doSetLayout(container, layout));
    }

    public void setConstraints(ComponentInfo component, String constraints) {
        command("Set constraints", () -> doSetConstraints(component, constraints));
    }

    public void move(ComponentInfo component, ComponentInfo target, Placement placement) {
        command("Move " + component.getPresentableName(), () -> doMove(component, target, placement));
    }

    public void delete(ComponentInfo component) {
        command("Delete " + component.getPresentableName(), () -> doDelete(component));
    }

    public void rename(ComponentInfo component, String newName) {
        command("Rename " + component.getName(), () -> doRename(component, newName));
    }

    /** Größe des Fensters (setBounds/setSize) bzw. des Panels (setPreferredSize, nur wenn schon vorhanden). */
    public void setDesignSize(Dimension size) {
        command("Resize form", () -> doSetDesignSize(size));
    }

    /** Ändert ein Feld der GridBagConstraints ({@code gridx}, {@code anchor} …); {@code null} = Standardwert. */
    public void setGridBagConstraint(ComponentInfo child, String field, @Nullable String value) {
        command("Set " + field, () -> doSetGridBagConstraint(child, field, value));
    }

    /**
     * Ausrichtung in der Zelle (Symbolleiste des WindowBuilder); {@code null} lässt die jeweilige Richtung unverändert.
     */
    public void setGridBagAlignment(ComponentInfo child, GridBagAlignment.@Nullable Horizontal horizontal,
                                    GridBagAlignment.@Nullable Vertical vertical) {
        command("Set alignment", () -> doSetGridBagAlignment(child, horizontal, vertical));
    }

    /** Ausrichtung für alle Komponenten einer Spalte ({@code column}) bzw. Zeile – aus dem Kopf des Gitters. */
    public void setGridLineAlignment(ComponentInfo container, boolean column, int index, GridBagAlignment.@Nullable Horizontal horizontal,
                                     GridBagAlignment.@Nullable Vertical vertical) {
        command(column ? "Set column alignment" : "Set row alignment", () -> {
            if (!GridBagWriter.isGridBag(container)) return;
            final java.awt.GridBagLayout layout = (java.awt.GridBagLayout) ((Container) container.getLive()).getLayout();
            for (final ComponentInfo child : List.copyOf(container.getChildren())) {
                if (child.getAssociation() != Association.ADD) continue;
                final GridBagWriter.Slot slot = GridBagWriter.slotOf(layout, child.getLive());
                if ((column ? slot.x() : slot.y()) == index) doSetGridBagAlignment(child, horizontal, vertical);
            }
        });
    }

    private void doSetGridBagAlignment(ComponentInfo child, GridBagAlignment.@Nullable Horizontal horizontal,
                                       GridBagAlignment.@Nullable Vertical vertical) {
        if (!GridBagWriter.isGridBag(child.getParent())) return;
        final java.awt.GridBagLayout layout = (java.awt.GridBagLayout) ((Container) child.getParent().getLive()).getLayout();
        final java.awt.GridBagConstraints current = layout.getConstraints(child.getLive());
        final GridBagAlignment.Horizontal h = horizontal != null ? horizontal : GridBagAlignment.horizontal(current);
        final GridBagAlignment.Vertical v = vertical != null ? vertical : GridBagAlignment.vertical(current);
        final String anchor = GridBagAlignment.anchor(h, v);
        final String fill = GridBagAlignment.fill(h, v);
        final Map<String, String> changes = new java.util.HashMap<>();
        changes.put("anchor", anchor == null ? null : GridBagWriter.GBC + "." + anchor);
        changes.put("fill", fill == null ? null : GridBagWriter.GBC + "." + fill);
        gridBag.applyConstraints(child, layout, changes);
    }

    /** Fügt im GridBagLayout eine Spalte ({@code column}) bzw. Zeile vor {@code index} ein. */
    public void insertGridLine(ComponentInfo container, boolean column, int index) {
        command(column ? "Insert column" : "Insert row", () -> {
            final GridBagWriter.Grid grid = gridBag.read(container);
            grid.insert(column, index);
            gridBag.write(grid, Set.of());
        });
    }

    /** Löscht Spalte bzw. Zeile samt der Komponenten, die nur dort liegen (wie im WindowBuilder). */
    public void deleteGridLine(ComponentInfo container, boolean column, int index) {
        command(column ? "Delete column" : "Delete row", () -> {
            final GridBagWriter.Grid grid = gridBag.read(container);
            final List<ComponentInfo> removed = grid.delete(column, index);
            for (final ComponentInfo component : removed) deleteCode(component);
            gridBag.write(grid, Set.copyOf(removed));
        });
    }

    /** Gewicht einer Spalte/Zeile (1.0 = wächst mit, 0.0 = feste Größe). */
    public void setGridWeight(ComponentInfo container, boolean column, int index, double weight) {
        command(column ? "Set column weight" : "Set row weight", () -> doSetGridWeight(container, column, index, weight));
    }

    /**
     * Legt einen Event-Handler an wie der WindowBuilder (anonyme Klasse, bei mehreren Methoden der Adapter) oder findet
     * den vorhandenen. Liefert die Stelle, an die der Editor springen soll.
     */
    public @Nullable SmartPsiElementPointer<PsiElement> addEventHandler(ComponentInfo component, EventCatalog.EventSet set,
                                                                        EventCatalog.EventMethod method) {
        final List<SmartPsiElementPointer<PsiElement>> result = new ArrayList<>();
        command("Add " + method.name(), () -> {
            final PsiElement target = doAddEventHandler(component, set, method);
            if (target != null) result.add(SmartPointerManager.getInstance(project).createSmartPsiElementPointer(target));
        });
        return result.isEmpty() ? null : result.get(0);
    }

    /** Entfernt die Handler-Methode; ist die anonyme Klasse danach leer, auch die Registrierung. */
    public void removeEventHandler(ComponentInfo component, EventCatalog.EventSet set, EventCatalog.EventMethod method) {
        command("Remove " + method.name(), () -> {
            final PsiElement statement = eventStatement(component, set);
            final PsiAnonymousClass anonymous = anonymousListener(statement);
            if (anonymous == null) {
                if (statement != null) statement.delete();
                return;
            }
            for (final PsiMethod existing : anonymous.findMethodsByName(method.name(), false)) existing.delete();
            if (anonymous.getMethods().length == 0) statement.delete();
        });
    }

    /** Namen der Methoden, die im Listener-Objekt der Registrierung implementiert sind. */
    public static List<String> handlerMethods(ComponentInfo component, String addMethod) {
        final PropertyInfo event = component.getEvents().get(addMethod);
        final PsiAnonymousClass anonymous = event == null ? null : anonymousListener(element(event.statement()));
        final List<String> names = new ArrayList<>();
        if (anonymous != null) {
            for (final PsiMethod method : anonymous.getMethods()) names.add(method.getName());
        } else if (event != null) {
            names.add("*");
        }
        return names;
    }

    // ---- Lokal ↔ Feld (Kontextmenü des WindowBuilder)

    public boolean canConvertToField(ComponentInfo component) {
        return variable(component) instanceof final PsiLocalVariable local && local.getInitializer() != null
                && local.getParent() instanceof final PsiDeclarationStatement declaration && declaration.getDeclaredElements().length == 1;
    }

    /** Ein Feld wird lokal, wenn es nur in der Methode benutzt wird, in der es erzeugt wird. */
    public boolean canConvertToLocal(ComponentInfo component) {
        if (!(variable(component) instanceof final PsiField field) || !(element(component.getCreation()) instanceof final PsiExpressionStatement creation)) {
            return false;
        }
        final PsiMethod method = PsiTreeUtil.getParentOfType(creation, PsiMethod.class);
        for (final PsiReference reference : ReferencesSearch.search(field, new LocalSearchScope(formClass)).findAll()) {
            if (PsiTreeUtil.getParentOfType(reference.getElement(), PsiMethod.class) != method
                    || reference.getElement().getTextOffset() < creation.getTextOffset()) {
                return false;
            }
        }
        return true;
    }

    public void convertToField(ComponentInfo component) {
        command("Convert to field", () -> {
            if (!canConvertToField(component) || !(variable(component) instanceof final PsiLocalVariable local)) return;
            addField("private %s %s;".formatted(local.getTypeElement().getText(), local.getName()));
            final PsiStatement assignment = factory.createStatementFromText("%s = %s;".formatted(local.getName(), local.getInitializer().getText()), local);
            touched.add(local.getParent().replace(assignment));
        });
    }

    public void convertToLocal(ComponentInfo component) {
        command("Convert to local variable", () -> {
            if (!canConvertToLocal(component) || !(variable(component) instanceof final PsiField field)
                    || !(element(component.getCreation()) instanceof final PsiExpressionStatement creation)
                    || !(creation.getExpression() instanceof final PsiAssignmentExpression assignment) || assignment.getRExpression() == null) {
                return;
            }
            final PsiStatement declaration = factory.createStatementFromText("%s %s = %s;".formatted(field.getTypeElement().getText(),
                    field.getName(), assignment.getRExpression().getText()), creation);
            touched.add(creation.replace(declaration));
            field.delete();
        });
    }

    // ---- NLS: Texte in ein ResourceBundle auslagern

    /** Ein String, der ausgelagert werden kann: Literal in setText/setTitle/setToolTipText oder im Konstruktor. */
    public record Externalizable(ComponentInfo component, String property, String key, String value,
                                 SmartPsiElementPointer<PsiElement> literal) {}

    public List<Externalizable> externalizableStrings() {
        final List<Externalizable> result = new ArrayList<>();
        final String owner = String.valueOf(formClass.getName());
        for (final ComponentInfo component : model.getAll()) {
            final String name = component.getName() == null ? "this" : component.getName();
            for (final String method : List.of("setText", "setTitle", "setToolTipText")) {
                final PropertyInfo property = component.getProperty(method);
                final PsiMethodCallExpression call = property == null ? null : callOf(element(property.statement()));
                if (call != null && call.getArgumentList().getExpressionCount() == 1) {
                    addLiteral(result, component, call.getArgumentList().getExpressions()[0],
                            "%s.%s.%s".formatted(owner, name, StringUtil.decapitalize(method.substring(3))));
                }
            }
            final PsiElement creation = element(component.getCreation());
            final PsiNewExpression create = creation == null ? null : PsiTreeUtil.findChildOfType(creation, PsiNewExpression.class);
            if (create != null && create.getArgumentList() != null && create.getArgumentList().getExpressionCount() > 0
                    && component.getProperty("setText") == null) {
                addLiteral(result, component, create.getArgumentList().getExpressions()[0], "%s.%s.text".formatted(owner, name));
            }
        }
        return result;
    }

    private void addLiteral(List<Externalizable> into, ComponentInfo component, PsiExpression expression, String key) {
        if (!(expression instanceof final com.intellij.psi.PsiLiteralExpression literal) || !(literal.getValue() instanceof final String value)) return;
        into.add(new Externalizable(component, key.substring(key.lastIndexOf('.') + 1), key, value,
                SmartPointerManager.getInstance(project).createSmartPsiElementPointer((PsiElement) literal)));
    }

    /**
     * Lagert die Strings aus wie der WindowBuilder: {@code <paket>.messages} bzw. {@code bundle}, ein Feld
     * {@code private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(...)} und
     * {@code BUNDLE.getString("Form.btnOk.text")} anstelle der Literale.
     */
    public void externalize(List<Externalizable> items, String bundle) {
        command("Externalize strings", () -> {
            final com.intellij.lang.properties.psi.PropertiesFile properties = Bundles.findOrCreate(file, bundle);
            if (properties == null) return;
            final String field = ensureBundleField(bundle);
            for (final Externalizable item : items) {
                final PsiElement literal = item.literal().getElement();
                if (literal == null) continue;
                if (properties.findPropertyByKey(item.key()) == null) properties.addProperty(item.key(), item.value());
                touched.add(literal.replace(factory.createExpressionFromText("%s.getString(\"%s\")".formatted(field, item.key()), literal)));
            }
        });
    }

    private String ensureBundleField(String bundle) {
        for (final PsiField field : formClass.getFields()) {
            if (field.getInitializer() != null && field.getInitializer().getText().contains("getBundle(\"%s\")".formatted(bundle))) return field.getName();
        }
        final String name = formClass.findFieldByName("BUNDLE", false) == null ? "BUNDLE" : uniqueName("BUNDLE");
        final PsiField field = factory.createFieldFromText("private static final java.util.ResourceBundle %s = java.util.ResourceBundle.getBundle(\"%s\");"
                .formatted(name, bundle), formClass);
        final PsiField[] fields = formClass.getFields();
        touched.add(fields.length > 0 ? formClass.addBefore(field, fields[0]) : formClass.addAfter(field, formClass.getLBrace()));
        return name;
    }

    /** Ausgelagerter Text: der neue Wert gehört in die Properties-Datei, nicht in den Code. */
    private boolean updateBundleValue(@Nullable PsiMethodCallExpression call, String args) {
        if (call == null || call.getArgumentList().getExpressionCount() != 1 || !(call.getArgumentList().getExpressions()[0] instanceof final PsiMethodCallExpression get)) {
            return false;
        }
        final com.intellij.lang.properties.IProperty property = Bundles.property(get);
        if (property == null) return false;
        final PsiExpression value = factory.createExpressionFromText(args, call);
        if (!(value instanceof final com.intellij.psi.PsiLiteralExpression literal) || !(literal.getValue() instanceof final String text)) return false;
        property.setValue(text);
        return true;
    }

    public boolean isValidNewName(ComponentInfo component, String name) {
        return PsiNameHelper.getInstance(project).isIdentifier(name) && (name.equals(component.getName()) || !takenNames().contains(name));
    }

    private void command(String name, Runnable action) {
        WriteCommandAction.writeCommandAction(project, file).withName(name).run(() -> {
            try {
                action.run();
                finish();
            } finally {
                touched.clear();
            }
        });
    }

    private void finish() {
        final JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);
        final CodeStyleManager style = CodeStyleManager.getInstance(project);
        for (final PsiElement element : touched) {
            if (!element.isValid()) continue;
            style.reformat(javaStyle.shortenClassReferences(element));
        }
    }

    // ---- Hinzufügen

    private String doAdd(ComponentInfo container, PaletteEntry entry, Placement placement) {
        final String className = String.valueOf(entry.className());
        final String text = firstString(entry.creationArgs());
        final String base = entry.variableBase() != null ? entry.variableBase() : VariableNames.base(className, text);
        final String name = VariableNames.unique(base, takenNames());
        final List<String> lines = new ArrayList<>();
        final String creation = entry.creationText();
        if (entry.field() || settings.variablesAsFields) {
            addField("private %s %s;".formatted(entry.variableType(), name));
            lines.add("%s = %s;".formatted(name, creation));
        } else {
            lines.add("%s %s = %s;".formatted(entry.variableType(), name, creation));
        }
        if (placement.bounds() != null) lines.add("%s.setBounds(%s);".formatted(name, bounds(placement.bounds())));
        final List<String> after = new ArrayList<>();
        for (final String call : entry.afterAdd()) after.add("%s.%s;".formatted(name, call));
        placeComponent(name, className, lines, after, container, placement, Set.of());
        return name;
    }

    /**
     * Schreibt den Code einer Komponente samt Zuordnung zum Container: {@code lines} (Erzeugung, Eigenschaften), bei
     * GridBagLayout das {@code gbc_…}, dann {@code add}/{@code setJMenuBar}/… und {@code after}.
     */
    private void placeComponent(String name, String className, List<String> lines, List<String> after, ComponentInfo container,
                                Placement placement, Set<PsiElement> excluded) {
        if (placement.association() == Association.ADD && FreeLayoutWriter.isFree(container)) {
            // GroupLayout/SpringLayout: Position steckt im Layout-Code, nicht in setBounds
            lines.removeIf(line -> line.trim().startsWith(name + ".setBounds("));
            final Rectangle bounds = placement.bounds() != null ? placement.bounds() : new Rectangle(10, 10, 100, 25);
            if (!FreeLayoutWriter.isGroup(container)) lines.add(associationText(container, name, Placement.append()));
            lines.addAll(after);
            regenerateFree(container, Map.of(), Set.of(), lines, Map.of(name, bounds), excluded);
            return;
        }
        GridBagWriter.Grid grid = null;
        Placement target = placement;
        if (placement.association() == Association.ADD && GridBagWriter.isGridBag(container)) {
            grid = gridBag.read(container);
            final GridBagWriter.Slot slot = gridSlot(grid, placement, 1, 1);
            grid.addNew(slot);
            target = new Placement(Association.ADD, gridBag.newConstraints(name, className, slot, grid.gaps(slot), lines), null, null, null);
        } else if (placement.association() == Association.ADD && placement.constraints() == null && isCardLayout(container)) {
            // CardLayout braucht einen Namen je Karte, wie im WindowBuilder: "name_<Zeit>"
            target = placement.withConstraints("\"name_%d\"".formatted(System.nanoTime() / 1000));
        }
        if (placement.association() == Association.POPUP) ensureAddPopupHelper();
        lines.add(associationText(container, name, target));
        lines.addAll(after);
        insertChildCode(container, target, lines, excluded);
        if (grid != null) gridBag.write(grid, Set.of());
    }

    /**
     * Schreibt den Code eines GroupLayout/SpringLayout neu: {@code leading} (Code neuer Komponenten) kommt davor,
     * {@code added} sind deren Positionen, {@code overrides} neue Positionen vorhandener, {@code without} fallen heraus.
     */
    private void regenerateFree(ComponentInfo container, Map<ComponentInfo, Rectangle> overrides, Set<ComponentInfo> without,
                                List<String> leading, Map<String, Rectangle> added, Set<PsiElement> excluded) {
        final boolean group = FreeLayoutWriter.isGroup(container);
        final ObjectInfo layout = container.getLayoutObject();
        String variable = layout == null ? null : layout.getName();
        if (variable == null) {
            // setLayout(new SpringLayout()) ohne Variable: wie im WindowBuilder eine anlegen
            variable = free.newVariable(container, group);
            final PsiStatement setLayout = doSetProperty(container, "setLayout", variable);
            if (setLayout != null && setLayout.getParent() instanceof final PsiCodeBlock block) {
                insertStatements(block, setLayout, null, List.of(FreeLayoutWriter.declaration(group, variable, containerReference(container))));
            }
        }
        free.deleteGenerated(container);
        final Map<String, Rectangle> bounds = FreeLayoutWriter.currentBounds(container, overrides, without);
        bounds.putAll(added);
        final List<String> lines = new ArrayList<>(leading);
        lines.addAll(free.lines(container, group, variable, bounds, FreeLayoutWriter.preferredSizes(container)));
        insertChildCode(container, Placement.append(), lines, excluded);
    }

    private static String containerReference(ComponentInfo container) {
        return container.getReference() == null || container.getReference().isEmpty() ? "this" : container.getReference();
    }

    private void toFree(ComponentInfo container, boolean group, boolean wasAbsolute, List<PsiVariable> obsolete) {
        final String variable = free.newVariable(container, group);
        final PsiStatement setLayout = doSetProperty(container, "setLayout", variable);
        if (setLayout != null && setLayout.getParent() instanceof final PsiCodeBlock block) {
            insertStatements(block, setLayout, null, List.of(FreeLayoutWriter.declaration(group, variable, containerReference(container))));
        }
        for (final ComponentInfo child : container.getChildren()) {
            if (child.getAssociation() != Association.ADD) continue;
            if (child.getConstraints() != null) stripConstraints(child);
            if (wasAbsolute) doSetProperty(child, "setBounds", null);
        }
        final Map<String, Rectangle> bounds = FreeLayoutWriter.currentBounds(container, Map.of(), Set.of());
        insertChildCode(container, Placement.append(), new ArrayList<>(free.lines(container, group, variable, bounds,
                FreeLayoutWriter.preferredSizes(container))), Set.of());
        for (final PsiVariable variableToDelete : obsolete) deleteVariable(variableToDelete);
    }

    private static boolean isCardLayout(ComponentInfo container) {
        return container.getLive() instanceof final Container c && c.getLayout() instanceof java.awt.CardLayout;
    }

    /** Die Hilfsmethode, die der WindowBuilder für Popup-Menüs anlegt. */
    private void ensureAddPopupHelper() {
        if (formClass.findMethodsByName("addPopup", false).length > 0) return;
        final PsiMethod helper = factory.createMethodFromText("""
                private static void addPopup(java.awt.Component component, final javax.swing.JPopupMenu popup) {
                    component.addMouseListener(new java.awt.event.MouseAdapter() {
                        public void mousePressed(java.awt.event.MouseEvent e) {
                            if (e.isPopupTrigger()) {
                                showMenu(e);
                            }
                        }
                        public void mouseReleased(java.awt.event.MouseEvent e) {
                            if (e.isPopupTrigger()) {
                                showMenu(e);
                            }
                        }
                        private void showMenu(java.awt.event.MouseEvent e) {
                            popup.show(e.getComponent(), e.getX(), e.getY());
                        }
                    });
                }""", formClass);
        touched.add(formClass.addBefore(helper, formClass.getRBrace()));
    }

    /** Zielzelle im Gitter; Einfügen auf einer Gitterlinie schiebt die übrigen weiter. Ohne Zelle: neue Zeile unten. */
    private static GridBagWriter.Slot gridSlot(GridBagWriter.Grid grid, Placement placement, int width, int height) {
        final Placement.GridCell cell = placement.cell() != null ? placement.cell() : new Placement.GridCell(0, grid.rows, false, false);
        if (cell.insertColumn()) grid.insert(true, cell.x());
        if (cell.insertRow()) grid.insert(false, cell.y());
        return new GridBagWriter.Slot(cell.x(), cell.y(), width, height);
    }

    private String associationText(ComponentInfo container, String child, Placement placement) {
        // Ein Fenster selbst nimmt keine Kinder auf, sondern seine Content Pane
        if (placement.association() == Association.MENU_BAR) return "%ssetJMenuBar(%s);".formatted(qualifier(container), child);
        if (placement.association() == Association.POPUP) {
            return "addPopup(%s, %s);".formatted(container.getReference() == null ? "this" : container.getReference(), child);
        }
        final String q = container.isThis() && container.getLive() instanceof WindowSurrogate ? "getContentPane()." : qualifier(container);
        return switch (placement.association()) {
            case VIEWPORT -> "%ssetViewportView(%s);".formatted(q, child);
            case LEFT -> "%ssetLeftComponent(%s);".formatted(q, child);
            case RIGHT -> "%ssetRightComponent(%s);".formatted(q, child);
            case TOP -> "%ssetTopComponent(%s);".formatted(q, child);
            case BOTTOM -> "%ssetBottomComponent(%s);".formatted(q, child);
            case TAB -> "%saddTab(\"New tab\", null, %s, null);".formatted(q, child);
            default -> placement.constraints() == null ? "%sadd(%s);".formatted(q, child) : "%sadd(%s, %s);".formatted(q, child, placement.constraints());
        };
    }

    /** Empfänger-Präfix: {@code panel.}, {@code getContentPane().} oder leer für {@code this}. */
    private static String qualifier(ComponentInfo component) {
        final String reference = component.getReference();
        return reference == null || reference.isEmpty() ? "" : reference + ".";
    }

    /** Fügt die Zeilen eines Kindes ein: vor dem Code des Geschwisters {@code placement.before()} oder nach dem Code des Containers. */
    private List<PsiStatement> insertChildCode(ComponentInfo container, Placement placement, List<String> lines, Set<PsiElement> excluded) {
        final PsiCodeBlock block = scope(container);
        PsiStatement before = null;
        if (placement.before() != null) {
            before = first(lifted(subtreeStatements(placement.before()), block, excluded));
        }
        PsiStatement after = null;
        if (placement.association() == Association.MENU_BAR) {
            final List<PsiStatement> statements = new ArrayList<>();
            for (final String line : lines) statements.add(factory.createStatementFromText(line, block));
            return insert(block, null, last(lifted(ownStatements(container, false), block, excluded)), statements, true);
        }
        if (before == null) {
            final List<PsiStatement> candidates = new ArrayList<>(subtreeStatements(container));
            if (container.isImplicit() && container.getParent() != null) candidates.addAll(ownStatements(container.getParent(), true));
            after = last(lifted(candidates, block, excluded));
        }
        final List<PsiStatement> statements = new ArrayList<>();
        if ((usesBlocks(container, block) || settings.useBlocks) && placement.association() != Association.MENU_BAR) {
            statements.add(factory.createStatementFromText("{\n%s\n}".formatted(String.join("\n", lines)), block));
        } else {
            for (final String line : lines) statements.add(factory.createStatementFromText(line, block));
        }
        return insert(block, before, after, statements, false);
    }

    /**
     * Fügt Anweisungen ein: vor {@code before}, nach {@code after}, sonst am Anfang (nach {@code super(...)}) bzw. am Ende
     * des Blocks.
     */
    private List<PsiStatement> insert(PsiCodeBlock block, @Nullable PsiStatement before, @Nullable PsiStatement after,
                                      List<PsiStatement> statements, boolean atStart) {
        final List<PsiStatement> inserted = new ArrayList<>();
        if (before != null) {
            for (final PsiStatement statement : statements) inserted.add((PsiStatement) block.addBefore(statement, before));
        } else {
            PsiElement anchor = after;
            if (anchor == null) {
                final PsiStatement[] existing = block.getStatements();
                if (atStart) {
                    anchor = existing.length > 0 && isConstructorCall(existing[0]) ? existing[0] : block.getLBrace();
                } else {
                    anchor = existing.length > 0 ? existing[existing.length - 1] : block.getLBrace();
                }
            }
            for (final PsiStatement statement : statements) {
                anchor = block.addAfter(statement, anchor);
                inserted.add((PsiStatement) anchor);
            }
        }
        touched.addAll(inserted);
        return inserted;
    }

    /** Für {@link GridBagWriter}: Zeilen als Anweisungen einfügen. */
    List<PsiStatement> insertStatements(PsiCodeBlock block, @Nullable PsiStatement before, @Nullable PsiStatement after, List<String> lines) {
        final List<PsiStatement> statements = new ArrayList<>();
        for (final String line : lines) statements.add(factory.createStatementFromText(line, block));
        return insert(block, before, after, statements, false);
    }

    PsiElementFactory factory() {
        return factory;
    }

    void touch(PsiElement element) {
        touched.add(element);
    }

    String uniqueName(String base) {
        return VariableNames.unique(base, takenNames());
    }

    private static boolean isConstructorCall(PsiStatement statement) {
        return statement instanceof final PsiExpressionStatement expression
                && expression.getExpression() instanceof final PsiMethodCallExpression call
                && ("super".equals(call.getMethodExpression().getReferenceName()) || "this".equals(call.getMethodExpression().getReferenceName()));
    }

    /** Stehen die Kinder des Containers schon in eigenen Blöcken (Dialog-Vorlage des WindowBuilder)? */
    private boolean usesBlocks(ComponentInfo container, PsiCodeBlock block) {
        for (final ComponentInfo child : container.getChildren()) {
            final PsiElement creation = element(child.getCreation());
            if (creation != null && lift(creation, block) instanceof PsiBlockStatement) return true;
        }
        return false;
    }

    private void addField(String text) {
        final PsiField field = factory.createFieldFromText(text, formClass);
        final PsiField[] fields = formClass.getFields();
        final PsiElement added = fields.length > 0 ? formClass.addAfter(field, fields[fields.length - 1])
                : formClass.addAfter(field, formClass.getLBrace());
        touched.add(added);
    }

    // ---- Eigenschaften

    /** @return die Anweisung mit dem Aufruf, {@code null} beim Entfernen */
    private @Nullable PsiStatement doSetProperty(ComponentInfo component, String method, @Nullable String args) {
        final PropertyInfo property = component.getProperty(method);
        final PsiElement existing = property == null ? null : element(property.statement());
        if (args == null) {
            if (existing != null && existing.isValid()) existing.delete();
            return null;
        }
        final PsiMethodCallExpression call = callOf(existing);
        if (updateBundleValue(call, args)) return (PsiStatement) existing;
        if (call != null) {
            final PsiMethodCallExpression replacement = (PsiMethodCallExpression) factory.createExpressionFromText("m(%s)".formatted(args), call);
            touched.add(call.getArgumentList().replace(replacement.getArgumentList()));
            return (PsiStatement) existing;
        }
        final PsiCodeBlock block = scope(component);
        return insertForComponent(component, factory.createStatementFromText("%s%s(%s);".formatted(qualifier(component), method, args), block));
    }

    /** Fügt eine Anweisung hinter den Code der Komponente ein (vor ihrem add, falls sie ein Feld-Initialisierer ist). */
    private PsiStatement insertForComponent(ComponentInfo component, PsiStatement statement) {
        final PsiCodeBlock block = scope(component);
        PsiStatement after = last(lifted(ownStatements(component, false), block, Set.of()));
        PsiStatement before = null;
        if (after == null) {
            final PsiElement association = element(component.getAssociationStatement());
            before = association == null ? null : lift(association, block);
            if (before == null && component.isImplicit() && component.getParent() != null) {
                after = last(lifted(ownStatements(component.getParent(), false), block, Set.of()));
            }
        }
        return insert(block, before, after, List.of(statement), true).get(0);
    }

    // ---- Events

    private @Nullable PsiElement eventStatement(ComponentInfo component, EventCatalog.EventSet set) {
        final PropertyInfo event = component.getEvents().get(set.addMethod());
        return event == null ? null : element(event.statement());
    }

    private static @Nullable PsiAnonymousClass anonymousListener(@Nullable PsiElement statement) {
        final PsiMethodCallExpression call = callOf(statement);
        if (call == null || call.getArgumentList().getExpressionCount() != 1) return null;
        return call.getArgumentList().getExpressions()[0] instanceof final PsiNewExpression create ? create.getAnonymousClass() : null;
    }

    private @Nullable PsiElement doAddEventHandler(ComponentInfo component, EventCatalog.EventSet set, EventCatalog.EventMethod method) {
        final PsiElement statement = eventStatement(component, set);
        final PsiAnonymousClass anonymous = anonymousListener(statement);
        if (anonymous != null) {
            final PsiMethod[] existing = anonymous.findMethodsByName(method.name(), false);
            if (existing.length > 0) return existing[0];
            final PsiMethod added = (PsiMethod) anonymous.addBefore(factory.createMethodFromText(handlerText(method, true), anonymous), anonymous.getRBrace());
            touched.add(added);
            return added;
        }
        // Lambda oder Methodenreferenz: dorthin springen
        if (statement != null) return statement;

        final boolean adapter = set.adapter() != null;
        if (settings.lambdaListeners && !adapter && set.methods().size() == 1) {
            final String receiver = component.isThis() ? "" : qualifier(component);
            final PsiStatement inserted = insertForComponent(component,
                    factory.createStatementFromText("%s%s(e -> {\n});".formatted(receiver, set.addMethod()), scope(component)));
            final PsiMethodCallExpression call = callOf(inserted);
            return call != null && call.getArgumentList().getExpressions()[0] instanceof final com.intellij.psi.PsiLambdaExpression lambda
                    ? lambda.getBody() : inserted;
        }
        final StringBuilder body = new StringBuilder();
        if (adapter) {
            body.append(handlerText(method, true));
        } else {
            // Interface: alle Methoden; bei nur einer wie im WindowBuilder ohne @Override
            final boolean single = set.methods().size() == 1;
            for (final EventCatalog.EventMethod m : set.methods()) body.append(handlerText(m, !single)).append('\n');
        }
        final String receiver = component.isThis() ? "" : qualifier(component);
        final String text = "%s%s(new %s() {\n%s\n});".formatted(receiver, set.addMethod(), adapter ? set.adapter() : set.listenerType(), body);
        final PsiStatement inserted = insertForComponent(component, factory.createStatementFromText(text, scope(component)));
        final PsiAnonymousClass created = anonymousListener(inserted);
        if (created == null) return inserted;
        final PsiMethod[] methods = created.findMethodsByName(method.name(), false);
        return methods.length > 0 ? methods[0] : inserted;
    }

    private static String handlerText(EventCatalog.EventMethod method, boolean override) {
        return "%spublic void %s(%s e) {\n}".formatted(override ? "@Override\n" : "", method.name(), method.parameterType());
    }

    private void doSetLayout(ComponentInfo container, PaletteEntry layout) {
        final boolean wasAbsolute = container.getLive() instanceof final Container c && c.getLayout() == null;
        final boolean wasBorder = container.getLive() instanceof final Container c && c.getLayout() instanceof BorderLayout;
        final boolean wasGridBag = GridBagWriter.isGridBag(container);
        final boolean absolute = layout.isAbsoluteLayout();
        final boolean border = layout == Palette.BORDER_LAYOUT;
        final boolean wasFree = FreeLayoutWriter.isFree(container);
        if (layout == Palette.GROUP_LAYOUT || layout == Palette.SPRING_LAYOUT) {
            final boolean group = layout == Palette.GROUP_LAYOUT;
            if (wasFree && FreeLayoutWriter.isGroup(container) == group) return;
            final List<PsiVariable> obsolete = new ArrayList<>();
            if (wasGridBag || wasFree) obsolete.add(variable(container.getLayoutObject()));
            if (wasGridBag) {
                for (final ComponentInfo child : container.getChildren()) obsolete.add(variable(child.getConstraintsObject()));
            }
            toFree(container, group, wasAbsolute, obsolete);
            return;
        }
        if (wasFree) {
            // Kinder eines GroupLayout hängen nur in den Gruppen – sie brauchen jetzt ein add
            for (final ComponentInfo child : container.getChildren()) {
                if (child.getAssociation() == Association.GROUP && child.getName() != null) {
                    insertChildCode(container, Placement.append(), new ArrayList<>(List.of(associationText(container, child.getName(), Placement.append()))), Set.of());
                }
            }
        }
        if (layout == Palette.GRID_BAG_LAYOUT) {
            if (!wasGridBag) toGridBag(container, wasAbsolute);
            if (wasFree) deleteVariable(variable(container.getLayoutObject()));
            return;
        }
        final String reference = container.getReference() == null || container.getReference().isEmpty() ? "this" : container.getReference();
        if (layout == Palette.MIG_LAYOUT || layout == Palette.FORM_LAYOUT) {
            toCellLayout(container, layout == Palette.FORM_LAYOUT, wasAbsolute, wasGridBag || wasFree);
            return;
        }
        doSetProperty(container, "setLayout", layout.layoutText(reference));
        final List<PsiVariable> obsolete = new ArrayList<>();
        if (wasGridBag || wasFree) obsolete.add(variable(container.getLayoutObject()));
        for (final ComponentInfo child : container.getChildren()) {
            if (child.getAssociation() != Association.ADD && child.getAssociation() != Association.GROUP) continue;
            if (absolute && !wasAbsolute) doSetProperty(child, "setBounds", bounds(child.getLive().getBounds()));
            if (!absolute && wasAbsolute) doSetProperty(child, "setBounds", null);
            if (child.getConstraints() != null && !(border && wasBorder)) stripConstraints(child);
            if (wasGridBag) obsolete.add(variable(child.getConstraintsObject()));
        }
        // gbl_… und gbc_… werden nicht mehr gebraucht
        for (final PsiVariable variable : obsolete) deleteVariable(variable);
    }

    private void toGridBag(ComponentInfo container, boolean wasAbsolute) {
        final Map<ComponentInfo, GridBagWriter.Slot> slots = GridBagWriter.convert(container);
        int columns = 0, rows = 0;
        for (final GridBagWriter.Slot slot : slots.values()) {
            columns = Math.max(columns, slot.x() + 1);
            rows = Math.max(rows, slot.y() + 1);
        }
        final List<String> layoutLines = new ArrayList<>();
        final String layoutName = gridBag.newLayout(container, columns, rows, layoutLines);
        final PsiStatement setLayout = doSetProperty(container, "setLayout", layoutName);
        if (setLayout != null && setLayout.getParent() instanceof final PsiCodeBlock block) insertStatements(block, setLayout, null, layoutLines);
        for (final Map.Entry<ComponentInfo, GridBagWriter.Slot> entry : slots.entrySet()) {
            final ComponentInfo child = entry.getKey();
            if (wasAbsolute) doSetProperty(child, "setBounds", null);
            final PsiElement association = element(child.getAssociationStatement());
            final PsiMethodCallExpression call = callOf(association);
            if (call == null && child.getAssociation() == Association.GROUP && child.getName() != null) {
                final List<String> lines = new ArrayList<>();
                final String constraints = gridBag.newConstraints(child.getName(), child.getClassName(), entry.getValue(),
                        GridBagWriter.gapsFor(entry.getValue(), columns, rows, settings.gridBagGaps, settings.gridBagGap), lines);
                lines.add(associationText(container, child.getName(), Placement.constraints(constraints)));
                insertChildCode(container, Placement.append(), lines, Set.of());
                continue;
            }
            if (call == null || !(association.getParent() instanceof final PsiCodeBlock block)) continue;
            final List<String> lines = new ArrayList<>();
            final String name = gridBag.newConstraints(String.valueOf(child.getName()), child.getClassName(), entry.getValue(),
                    GridBagWriter.gapsFor(entry.getValue(), columns, rows, settings.gridBagGaps, settings.gridBagGap), lines);
            insertStatements(block, (PsiStatement) association, null, lines);
            gridBag.replaceConstraintsArgument(child, call, name);
        }
    }

    private void doSetGridBagConstraint(ComponentInfo child, String field, @Nullable String value) {
        final ComponentInfo container = child.getParent();
        if (!GridBagWriter.isGridBag(container)) return;
        // gridx/gridy schreibt der WindowBuilder immer – ohne Wert gibt es nichts zurückzusetzen
        if (value == null && (field.equals("gridx") || field.equals("gridy"))) return;
        final GridBagWriter.Grid grid = gridBag.read(container);
        final GridBagWriter.Slot slot = grid.slots.get(child);
        final Integer number = value == null ? Integer.valueOf(1) : parseInt(value);
        if (slot != null && number != null && List.of("gridx", "gridy", "gridwidth", "gridheight").contains(field)) {
            final int n = Math.max(field.equals("gridx") || field.equals("gridy") ? 0 : 1, number);
            grid.put(child, switch (field) {
                case "gridx" -> slot.at(n, slot.y());
                case "gridy" -> slot.at(slot.x(), n);
                case "gridwidth" -> new GridBagWriter.Slot(slot.x(), slot.y(), n, slot.h());
                default -> new GridBagWriter.Slot(slot.x(), slot.y(), slot.w(), n);
            });
            gridBag.write(grid, Set.of());
            return;
        }
        final Map<String, String> changes = new java.util.HashMap<>();
        changes.put(field, value);
        gridBag.applyConstraints(child, grid.layout, changes);
    }

    private static @Nullable Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private void doSetGridWeight(ComponentInfo container, boolean column, int index, double weight) {
        final GridBagWriter.Grid grid = gridBag.read(container);
        grid.setWeight(column, index, weight);
        if (container.getLayoutObject() != null) {
            gridBag.write(grid, Set.of());
            return;
        }
        // setLayout(new GridBagLayout()) ohne Variable: wie im WindowBuilder ein gbl_… mit Arrays anlegen
        final List<String> lines = new ArrayList<>();
        final String name = gridBag.newLayout(container, grid.columnWidths, grid.rowHeights, grid.columnWeights, grid.rowWeights, lines);
        final PsiStatement setLayout = doSetProperty(container, "setLayout", name);
        if (setLayout != null && setLayout.getParent() instanceof final PsiCodeBlock block) insertStatements(block, setLayout, null, lines);
    }

    /** Löscht eine Variable mit allen Anweisungen, die sie verwenden. */
    private void deleteVariable(@Nullable PsiVariable variable) {
        if (variable == null || !variable.isValid()) return;
        final List<PsiElement> statements = new ArrayList<>();
        for (final PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(formClass)).findAll()) {
            final PsiStatement statement = PsiTreeUtil.getParentOfType(reference.getElement(), PsiStatement.class, false);
            if (statement != null) statements.add(statement);
        }
        final PsiElement declaration = declarationOf(variable);
        if (declaration != null) statements.add(declaration);
        statements.sort(Comparator.comparingInt(PsiElement::getTextOffset).reversed());
        for (final PsiElement statement : statements) {
            if (statement.isValid()) statement.delete();
        }
    }

    /**
     * MigLayout/FormLayout: die Kinder untereinander, je eine Zelle ({@code "cell 0 1"} bzw. {@code "2, 4"} mit
     * Abstandszeilen wie im WindowBuilder).
     */
    private void toCellLayout(ComponentInfo container, boolean forms, boolean wasAbsolute, boolean replacesVariable) {
        final List<ComponentInfo> children = new ArrayList<>();
        for (final ComponentInfo child : container.getChildren()) {
            if (child.getAssociation() == Association.ADD || child.getAssociation() == Association.GROUP) children.add(child);
        }
        final String text;
        if (forms) {
            final String gap = "com.jgoodies.forms.layout.FormSpecs.RELATED_GAP_ROWSPEC, com.jgoodies.forms.layout.FormSpecs.DEFAULT_ROWSPEC,";
            text = "new com.jgoodies.forms.layout.FormLayout(new com.jgoodies.forms.layout.ColumnSpec[]{com.jgoodies.forms.layout.FormSpecs.RELATED_GAP_COLSPEC, "
                    + "com.jgoodies.forms.layout.FormSpecs.DEFAULT_COLSPEC,}, new com.jgoodies.forms.layout.RowSpec[]{%s})".formatted(gap.repeat(Math.max(1, children.size())));
        } else {
            text = Palette.MIG_LAYOUT.layoutText("");
        }
        final PsiVariable oldLayout = replacesVariable ? variable(container.getLayoutObject()) : null;
        final List<PsiVariable> oldConstraints = new ArrayList<>();
        doSetProperty(container, "setLayout", text);
        for (int i = 0; i < children.size(); i++) {
            final ComponentInfo child = children.get(i);
            if (wasAbsolute) doSetProperty(child, "setBounds", null);
            if (child.getConstraintsObject() != null) oldConstraints.add(variable(child.getConstraintsObject()));
            final String constraints = forms ? "\"2, %d\"".formatted(2 * i + 2) : "\"cell 0 %d\"".formatted(i);
            if (element(child.getAssociationStatement()) == null && child.getName() != null) {
                insertChildCode(container, Placement.append(), new ArrayList<>(List.of(associationText(container, child.getName(), Placement.constraints(constraints)))), Set.of());
            } else {
                doSetConstraints(child, constraints);
            }
        }
        deleteVariable(oldLayout);
        for (final PsiVariable variable : oldConstraints) deleteVariable(variable);
    }

    private void stripConstraints(ComponentInfo child) {
        final PsiMethodCallExpression call = callOf(element(child.getAssociationStatement()));
        if (call == null) return;
        final PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length != 2) return;
        // add(child, constraints) oder das alte add(name, child)
        (args[0].getText().equals(child.getReference()) ? args[1] : args[0]).delete();
    }

    private void doSetConstraints(ComponentInfo child, String constraints) {
        final PsiMethodCallExpression call = callOf(element(child.getAssociationStatement()));
        if (call == null) return;
        final PsiExpressionList list = call.getArgumentList();
        final PsiExpression[] args = list.getExpressions();
        final PsiExpression value = factory.createExpressionFromText(constraints, call);
        if (args.length == 1) {
            touched.add(list.add(value));
        } else if (args.length == 2) {
            touched.add((args[0].getText().equals(child.getReference()) ? args[1] : args[0]).replace(value));
        }
    }

    private void doSetDesignSize(Dimension size) {
        final ComponentInfo root = model.getRoot();
        final PropertyInfo setBounds = root.getProperty("setBounds");
        if (setBounds != null && setBounds.values().size() == 4) {
            doSetProperty(root, "setBounds", "%s, %s, %d, %d".formatted(setBounds.argTexts().get(0), setBounds.argTexts().get(1), size.width, size.height));
        } else if (root.getProperty("setSize") != null) {
            doSetProperty(root, "setSize", "%d, %d".formatted(size.width, size.height));
        } else if (root.getProperty("setPreferredSize") != null) {
            doSetProperty(root, "setPreferredSize", "new java.awt.Dimension(%d, %d)".formatted(size.width, size.height));
        } else if (root.getLive() instanceof WindowSurrogate || root.getLive() instanceof javax.swing.JInternalFrame) {
            doSetProperty(root, "setBounds", "100, 100, %d, %d".formatted(size.width, size.height));
        }
    }

    // ---- Verschieben

    private void doMove(ComponentInfo component, ComponentInfo target, Placement placement) {
        if (target == component || component.isAncestorOf(target)) return;
        if (target == component.getParent() && placement.association() == Association.ADD && placement.bounds() != null) {
            if (FreeLayoutWriter.isFree(target)) regenerateFree(target, Map.of(component, placement.bounds()), Set.of(), List.of(), Map.of(), Set.of());
            else doSetProperty(component, "setBounds", bounds(placement.bounds()));
            return;
        }
        // Aus einem GroupLayout/SpringLayout heraus: dessen Code zuerst ohne die Komponente neu schreiben
        if (FreeLayoutWriter.isFree(component.getParent())) {
            regenerateFree(component.getParent(), Map.of(), Set.of(component), List.of(), Map.of(), Set.of());
        }
        final boolean fromGrid = GridBagWriter.isGridBag(component.getParent()) && component.getAssociation() == Association.ADD;
        final boolean toGrid = GridBagWriter.isGridBag(target);
        if (toGrid && target == component.getParent()) {
            // Innerhalb des Gitters ändern sich nur die Constraints
            final GridBagWriter.Grid grid = gridBag.read(target);
            final GridBagWriter.Slot old = grid.slots.get(component);
            final GridBagWriter.Slot slot = gridSlot(grid, placement, old == null ? 1 : old.w(), old == null ? 1 : old.h());
            grid.put(component, slot);
            gridBag.write(grid, Set.of());
            return;
        }
        GridBagWriter.Grid oldGrid = null;
        if (fromGrid) {
            oldGrid = gridBag.read(component.getParent());
            oldGrid.remove(component);
        }
        final ObjectInfo ownConstraints = component.getConstraintsObject();
        final PsiVariable ownConstraintsVariable = ownConstraints != null && ownConstraints.getUses() == 1 ? variable(ownConstraints) : null;
        final PsiElement association = element(component.getAssociationStatement());
        if (association != null) association.delete();
        deleteVariable(ownConstraintsVariable);
        doSetProperty(component, "setBounds", placement.bounds() == null ? null : bounds(placement.bounds()));

        // Den ganzen Code der Komponente (inkl. Kinder) an die neue Stelle verschieben, damit die Deklaration vor dem add steht
        final List<PsiElement> sorted = ownCode(component, true);
        final Set<PsiElement> moving = new LinkedHashSet<>(sorted);
        final List<String> lines = new ArrayList<>();
        for (final PsiElement statement : sorted) lines.add(statement.getText());
        placeComponent(String.valueOf(component.getReference()), component.getClassName(), lines, List.of(), target, placement, moving);
        for (final PsiElement statement : sorted) {
            if (statement.isValid()) statement.delete();
        }
        if (oldGrid != null) gridBag.write(oldGrid, Set.of());
    }

    /** Der Code einer Komponente ohne ihre Zuordnung zum Container – zum Kopieren und Verschieben. */
    private List<PsiElement> ownCode(ComponentInfo component, boolean withAssociation) {
        final PsiCodeBlock ownBlock = scope(component);
        final ObjectInfo ownConstraints = component.getConstraintsObject();
        final PsiVariable constraintsVariable = ownConstraints != null && ownConstraints.getUses() == 1 ? variable(ownConstraints) : null;
        final PsiElement association = element(component.getAssociationStatement());
        final Set<PsiElement> code = new LinkedHashSet<>();
        for (final PsiVariable variable : variables(component)) {
            if (variable == constraintsVariable && !withAssociation) continue;
            final PsiElement declaration = declarationOf(variable);
            if (declaration instanceof PsiStatement) addLifted(code, declaration, ownBlock);
            for (final PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(formClass)).findAll()) {
                addLifted(code, reference.getElement(), ownBlock);
            }
        }
        if (!withAssociation && association != null) code.remove(lift(association, ownBlock));
        final List<PsiElement> sorted = new ArrayList<>(code);
        sorted.sort(Comparator.comparingInt(PsiElement::getTextOffset));
        return sorted;
    }

    // ---- Kopieren und Einfügen

    /**
     * Eine kopierte Komponente: ihr Code ohne die Zuordnung, Felddeklarationen und die Variablennamen, die beim
     * Einfügen eindeutig gemacht werden.
     */
    public record Clip(String name, String className, List<String> fields, List<String> statements, List<String> names) {}

    public @Nullable Clip copy(ComponentInfo component) {
        if (component.isThis() || component.isImplicit() || component.getName() == null) return null;
        final List<String> statements = new ArrayList<>();
        for (final PsiElement statement : ownCode(component, false)) statements.add(statement.getText());
        final List<String> fields = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        for (final PsiVariable variable : variables(component)) {
            names.add(variable.getName());
            if (variable instanceof final PsiField field) fields.add(field.getText());
        }
        // Constraints zum alten Container kommen nicht mit
        final ObjectInfo constraints = component.getConstraintsObject();
        if (constraints != null) names.remove(constraints.getName());
        return new Clip(component.getName(), component.getClassName(), fields, statements, names);
    }

    /** Fügt eine Kopie ein; Variablen bekommen neue Namen ({@code btnOk} → {@code btnOk_1}). Liefert den neuen Namen. */
    public String paste(ComponentInfo container, Placement placement, Clip clip) {
        final String[] result = new String[1];
        command("Paste " + clip.name(), () -> {
            final Set<String> taken = new HashSet<>(takenNames());
            final Map<String, String> renames = new java.util.LinkedHashMap<>();
            for (final String name : clip.names()) {
                final String base = name.replaceAll("_\\d+$", "");
                final String renamed = VariableNames.unique(base, taken);
                taken.add(renamed);
                renames.put(name, renamed);
            }
            for (final String field : clip.fields()) addField(rename(field, renames));
            final String name = renames.getOrDefault(clip.name(), clip.name());
            final List<String> lines = new ArrayList<>();
            final String setBounds = "%s.setBounds(".formatted(name);
            for (final String statement : clip.statements()) {
                final String renamed = rename(statement, renames);
                // Die alte Position gilt nicht mehr; im Absolute Layout kommt die neue
                if (renamed.trim().startsWith(setBounds)) continue;
                lines.add(renamed);
            }
            if (placement.bounds() != null) lines.add(Math.min(1, lines.size()), "%s%s);".formatted(setBounds, bounds(placement.bounds())));
            placeComponent(name, clip.className(), lines, List.of(), container, placement, Set.of());
            result[0] = name;
        });
        return result[0];
    }

    private static String rename(String code, Map<String, String> renames) {
        String result = code;
        for (final Map.Entry<String, String> entry : renames.entrySet()) {
            result = result.replaceAll("(?<![\\w$.\"])" + java.util.regex.Pattern.quote(entry.getKey()) + "(?![\\w$])", java.util.regex.Matcher.quoteReplacement(entry.getValue()));
        }
        return result;
    }

    // ---- Mehrere Komponenten auf einmal

    /** Neue Bounds für mehrere Komponenten im Absolute Layout (Verschieben, Ausrichten, Verteilen) – ein Undo-Schritt. */
    public void setBoundsAll(Map<ComponentInfo, Rectangle> bounds) {
        command("Change bounds", () -> {
            final Map<ComponentInfo, Map<ComponentInfo, Rectangle>> freeParents = new java.util.LinkedHashMap<>();
            for (final Map.Entry<ComponentInfo, Rectangle> entry : bounds.entrySet()) {
                final ComponentInfo parent = entry.getKey().getParent();
                if (FreeLayoutWriter.isFree(parent)) {
                    freeParents.computeIfAbsent(parent, p -> new java.util.LinkedHashMap<>()).put(entry.getKey(), entry.getValue());
                } else {
                    doSetProperty(entry.getKey(), "setBounds", bounds(entry.getValue()));
                }
            }
            freeParents.forEach((parent, overrides) -> regenerateFree(parent, overrides, Set.of(), List.of(), Map.of(), Set.of()));
        });
    }

    /** Löscht mehrere Komponenten in einem Schritt. */
    public void deleteAll(List<ComponentInfo> components) {
        command("Delete", () -> {
            for (final ComponentInfo component : components) {
                if (components.stream().noneMatch(other -> other != component && other.isAncestorOf(component))) doDelete(component);
            }
        });
    }

    /** gridwidth und gridheight auf einmal (Ziehen am Griff im GridBagLayout). */
    public void setGridBagSpan(ComponentInfo child, int width, int height) {
        command("Change span", () -> {
            if (!GridBagWriter.isGridBag(child.getParent())) return;
            final GridBagWriter.Grid grid = gridBag.read(child.getParent());
            final GridBagWriter.Slot slot = grid.slots.get(child);
            if (slot == null) return;
            grid.put(child, new GridBagWriter.Slot(slot.x(), slot.y(), Math.max(1, width), Math.max(1, height)));
            gridBag.write(grid, Set.of());
        });
    }

    private static void addLifted(Set<PsiElement> into, PsiElement element, PsiCodeBlock block) {
        final PsiStatement statement = lift(element, block);
        if (statement != null) into.add(statement);
    }

    // ---- Löschen und Umbenennen

    private void doDelete(ComponentInfo component) {
        if (component.isThis() || component.isImplicit()) return;
        GridBagWriter.Grid grid = null;
        if (GridBagWriter.isGridBag(component.getParent()) && component.getAssociation() == Association.ADD) {
            grid = gridBag.read(component.getParent());
            grid.remove(component);
        }
        if (FreeLayoutWriter.isFree(component.getParent())) {
            regenerateFree(component.getParent(), Map.of(), Set.of(component), List.of(), Map.of(), Set.of());
        }
        deleteCode(component);
        if (grid != null) gridBag.write(grid, Set.of());
    }

    /** Entfernt allen Code der Komponente und ihrer Kinder, samt eigener gbc_…/gbl_…-Variablen. */
    private void deleteCode(ComponentInfo component) {
        final List<PsiVariable> variables = variables(component);
        final Set<PsiElement> statements = new LinkedHashSet<>();
        final Set<PsiVariable> deleted = new HashSet<>(variables);
        for (final PsiVariable variable : variables) {
            for (final PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(formClass)).findAll()) {
                final PsiStatement statement = PsiTreeUtil.getParentOfType(reference.getElement(), PsiStatement.class, false);
                if (statement == null) continue;
                // Die Deklaration einer anderen Komponente (new JScrollPane(table)) bleibt stehen
                if (statement instanceof final PsiDeclarationStatement declaration && !declaresOnly(declaration, deleted)) continue;
                statements.add(statement);
            }
            final PsiElement declaration = declarationOf(variable);
            if (declaration != null) statements.add(declaration);
        }
        // Zuordnung über einen Accessor (getContentPane().add(...)) ohne Variable
        final PsiElement association = element(component.getAssociationStatement());
        if (association != null) statements.add(association);

        final Set<PsiCodeBlock> blocks = new HashSet<>();
        final List<PsiElement> sorted = new ArrayList<>(statements);
        sorted.sort(Comparator.comparingInt(PsiElement::getTextOffset).reversed());
        for (final PsiElement statement : sorted) {
            if (!statement.isValid()) continue;
            if (statement.getParent() instanceof final PsiCodeBlock block) blocks.add(block);
            statement.delete();
        }
        // Leer gewordene { }-Blöcke des WindowBuilder-Stils entfernen
        for (final PsiCodeBlock block : blocks) {
            if (block.isValid() && block.isEmpty() && block.getParent() instanceof final PsiBlockStatement blockStatement
                    && PsiTreeUtil.getChildrenOfType(block, com.intellij.psi.PsiComment.class) == null) {
                blockStatement.delete();
            }
        }
    }

    private static boolean declaresOnly(PsiDeclarationStatement declaration, Set<PsiVariable> variables) {
        for (final PsiElement element : declaration.getDeclaredElements()) {
            if (!(element instanceof final PsiVariable variable) || !variables.contains(variable)) return false;
        }
        return true;
    }

    private void doRename(ComponentInfo component, String newName) {
        final PsiVariable variable = variable(component);
        if (variable == null || newName.equals(variable.getName()) || !isValidNewName(component, newName)) return;
        for (final PsiReference reference : ReferencesSearch.search(variable, new LocalSearchScope(formClass)).findAll()) {
            reference.handleElementRename(newName);
        }
        variable.setName(newName);
    }

    // ---- Hilfen

    /** Variablen der Komponente und aller Kinder. */
    private List<PsiVariable> variables(ComponentInfo component) {
        final List<PsiVariable> result = new ArrayList<>();
        final PsiVariable variable = variable(component);
        if (variable != null && variable.isValid()) result.add(variable);
        final ObjectInfo constraints = component.getConstraintsObject();
        final PsiVariable constraintsVariable = constraints != null && constraints.getUses() == 1 ? variable(constraints) : null;
        if (constraintsVariable != null && constraintsVariable.isValid()) result.add(constraintsVariable);
        final PsiVariable layoutVariable = variable(component.getLayoutObject());
        if (layoutVariable != null && layoutVariable.isValid()) result.add(layoutVariable);
        for (final ComponentInfo child : component.getChildren()) result.addAll(variables(child));
        return result;
    }

    private static @Nullable PsiVariable variable(@Nullable ObjectInfo object) {
        return object == null ? null : variable(element(object.getCreation()), object.getName());
    }

    private static @Nullable PsiVariable variable(ComponentInfo component) {
        return variable(element(component.getCreation()), component.getName());
    }

    private static @Nullable PsiVariable variable(@Nullable PsiElement creation, @Nullable String name) {
        if (creation instanceof final PsiField field) return field;
        if (creation instanceof final PsiDeclarationStatement declaration) {
            for (final PsiElement element : declaration.getDeclaredElements()) {
                if (element instanceof final PsiLocalVariable local && local.getName().equals(name)) return local;
            }
        }
        if (creation instanceof final PsiExpressionStatement statement
                && statement.getExpression() instanceof final PsiAssignmentExpression assignment
                && assignment.getLExpression() instanceof final PsiReferenceExpression reference
                && reference.resolve() instanceof final PsiVariable resolved) {
            return resolved;
        }
        return null;
    }

    private static @Nullable PsiElement declarationOf(PsiVariable variable) {
        if (variable instanceof PsiField) return variable;
        return variable.getParent() instanceof final PsiDeclarationStatement declaration ? declaration : null;
    }

    /** Der Block, in dem Code für die Komponente stehen darf. */
    private PsiCodeBlock scope(ComponentInfo component) {
        if (component.isImplicit() && component.getParent() != null) return scope(component.getParent());
        final PsiElement creation = element(component.getCreation());
        if (creation instanceof final PsiStatement statement && statement.getParent() instanceof final PsiCodeBlock block) return block;
        return defaultBody();
    }

    private PsiCodeBlock defaultBody() {
        final PsiElement body = element(model.getScript().defaultBody());
        if (body instanceof final PsiCodeBlock block && block.isValid()) return block;
        if (createdBody == null || !createdBody.isValid()) {
            final PsiMethod constructor = factory.createConstructor(String.valueOf(formClass.getName()));
            final PsiField[] fields = formClass.getFields();
            final PsiMethod added = (PsiMethod) (fields.length > 0 ? formClass.addAfter(constructor, fields[fields.length - 1]) : formClass.add(constructor));
            createdBody = added.getBody();
        }
        return createdBody;
    }

    /** Erzeugung und Eigenschaften der Komponente, wahlweise mit der Zuordnung zum Container. */
    private static List<PsiStatement> ownStatements(ComponentInfo component, boolean withAssociation) {
        final List<PsiStatement> result = new ArrayList<>();
        if (element(component.getCreation()) instanceof final PsiStatement creation) result.add(creation);
        for (final PropertyInfo property : component.getProperties().values()) {
            if (element(property.statement()) instanceof final PsiStatement statement) result.add(statement);
        }
        if (withAssociation && element(component.getAssociationStatement()) instanceof final PsiStatement association) result.add(association);
        return result;
    }

    private static List<PsiStatement> subtreeStatements(ComponentInfo component) {
        final List<PsiStatement> result = new ArrayList<>(ownStatements(component, true));
        for (final ComponentInfo child : component.getChildren()) result.addAll(subtreeStatements(child));
        return result;
    }

    /** Die Anweisung direkt in {@code block}, die {@code element} enthält; {@code null}, wenn außerhalb. */
    private static @Nullable PsiStatement lift(PsiElement element, PsiCodeBlock block) {
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile) && current.getParent() != block) current = current.getParent();
        return current instanceof final PsiStatement statement && current.getParent() == block ? statement : null;
    }

    private static List<PsiStatement> lifted(List<PsiStatement> statements, PsiCodeBlock block, Set<PsiElement> excluded) {
        final List<PsiStatement> result = new ArrayList<>();
        for (final PsiStatement statement : statements) {
            final PsiStatement liftedStatement = lift(statement, block);
            if (liftedStatement != null && !excluded.contains(liftedStatement)) result.add(liftedStatement);
        }
        return result;
    }

    private static @Nullable PsiStatement first(List<PsiStatement> statements) {
        return statements.stream().min(Comparator.comparingInt(PsiElement::getTextOffset)).orElse(null);
    }

    private static @Nullable PsiStatement last(List<PsiStatement> statements) {
        return statements.stream().max(Comparator.comparingInt(PsiElement::getTextOffset)).orElse(null);
    }

    static @Nullable PsiMethodCallExpression callOf(@Nullable PsiElement statement) {
        return statement instanceof final PsiExpressionStatement expression
                && expression.getExpression() instanceof final PsiMethodCallExpression call ? call : null;
    }

    static @Nullable PsiElement element(@Nullable SmartPsiElementPointer<PsiElement> pointer) {
        return pointer == null ? null : pointer.getElement();
    }

    private Set<String> takenNames() {
        final Set<String> names = new HashSet<>();
        for (final PsiVariable variable : PsiTreeUtil.findChildrenOfType(file, PsiVariable.class)) names.add(variable.getName());
        return names;
    }

    public static String bounds(Rectangle r) {
        return "%d, %d, %d, %d".formatted(r.x, r.y, r.width, r.height);
    }

    /** Der erste String-Literal-Inhalt in einer Argumentliste ({@code "New button"} → {@code New button}). */
    private static @Nullable String firstString(String args) {
        final int start = args.indexOf('"');
        final int end = start < 0 ? -1 : args.indexOf('"', start + 1);
        return end < 0 ? null : StringUtil.unescapeStringCharacters(args.substring(start + 1, end));
    }
}
