package com.herreromarcos.idea.windowbuilderclassic.parser;

import com.herreromarcos.idea.windowbuilderclassic.model.Expr;
import com.herreromarcos.idea.windowbuilderclassic.model.FormScript;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiThisExpression;
import com.intellij.psi.PsiTryStatement;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.JavaTokenType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liest eine Formularklasse wie der WindowBuilder: Feld-Initialisierer, dann der Konstruktor und alle Methoden der
 * Klasse, die er (direkt oder indirekt) aufruft. Kontrollfluss wird nicht ausgewertet; Blöcke und try-Rümpfe werden
 * durchlaufen. Muss in einer Read-Action laufen.
 */
public final class FormParser {
    private final PsiClass formClass;
    private final SmartPointerManager pointers;
    private final Map<PsiVariable, Integer> varIds = new HashMap<>();
    private final Set<PsiMethod> visiting = new HashSet<>();
    private final List<FormScript.Op> ops = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final List<Expr> superArgs = new ArrayList<>();

    private FormParser(PsiClass formClass) {
        this.formClass = formClass;
        this.pointers = SmartPointerManager.getInstance(formClass.getProject());
    }

    /** Die Klasse der Datei, die der Designer bearbeitet: die erste Top-Level-Klasse. */
    public static @Nullable PsiClass findFormClass(PsiFile file) {
        if (!(file instanceof final PsiJavaFile javaFile)) return null;
        final PsiClass[] classes = javaFile.getClasses();
        return classes.length == 0 || classes[0].isInterface() || classes[0].isEnum() || classes[0].isRecord() ? null : classes[0];
    }

    /** Grobe, schnelle Prüfung ohne Auflösung: sieht die Datei nach Swing/AWT-Oberfläche aus? */
    public static boolean isCandidate(PsiFile file) {
        final PsiClass cls = findFormClass(file);
        if (cls == null || !(file instanceof final PsiJavaFile javaFile) || javaFile.getImportList() == null) return false;
        final String text = javaFile.getImportList().getText();
        return text.contains("javax.swing") || text.contains("java.awt");
    }

    public static @Nullable FormScript parse(PsiFile file) {
        final PsiClass cls = findFormClass(file);
        return cls == null ? null : new FormParser(cls).run();
    }

    private FormScript run() {
        final String rootClass = TypeResolver.jdkComponentAncestor(formClass);
        for (final PsiField field : formClass.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC) || field.getInitializer() == null) continue;
            ops.add(new FormScript.Assign(id(field), field.getName(), true, convert(field.getInitializer()),
                    argTexts(field.getInitializer()), pointer(field)));
        }
        final PsiMethod constructor = findConstructor();
        PsiCodeBlock defaultBody = null;
        if (constructor != null && constructor.getBody() != null) {
            defaultBody = constructor.getBody();
            final PsiMethod init = singleInitMethod(constructor.getBody());
            if (init != null) defaultBody = init.getBody();
            runMethod(constructor);
        } else if (constructor == null) {
            problems.add("No constructor – a default constructor is created on the first change.");
        }
        final PsiJavaCodeReferenceElement[] extendsList = formClass.getExtendsList() == null
                ? PsiJavaCodeReferenceElement.EMPTY_ARRAY : formClass.getExtendsList().getReferenceElements();
        final String superClass = extendsList.length == 0 ? null : TypeResolver.qualifiedName(extendsList[0]);
        return new FormScript(formClass.getQualifiedName() != null ? formClass.getQualifiedName() : String.valueOf(formClass.getName()),
                rootClass, superClass, superArgs, ops, defaultBody == null ? null : pointer(defaultBody), problems);
    }

    private @Nullable PsiMethod findConstructor() {
        final PsiMethod[] constructors = formClass.getConstructors();
        for (final PsiMethod constructor : constructors) {
            if (constructor.getParameterList().isEmpty()) return constructor;
        }
        return constructors.length > 0 ? constructors[0] : null;
    }

    /** Ruft der Konstruktor genau eine eigene Methode auf (WindowBuilder: {@code initialize()}), landet neuer Code dort. */
    private @Nullable PsiMethod singleInitMethod(PsiCodeBlock body) {
        PsiMethod found = null;
        for (final PsiStatement statement : body.getStatements()) {
            if (statement instanceof final PsiExpressionStatement expression
                    && expression.getExpression() instanceof final PsiMethodCallExpression call) {
                final PsiMethod own = ownMethod(call);
                if (own != null) {
                    if (found != null) return null;
                    found = own;
                }
            }
        }
        return found;
    }

    private void runMethod(PsiMethod method) {
        if (method.getBody() == null || !visiting.add(method)) return;
        try {
            runBlock(method.getBody());
        } finally {
            visiting.remove(method);
        }
    }

    private void runBlock(PsiCodeBlock block) {
        for (final PsiStatement statement : block.getStatements()) runStatement(statement);
    }

    private void runStatement(PsiStatement statement) {
        if (statement instanceof final PsiBlockStatement block) {
            runBlock(block.getCodeBlock());
        } else if (statement instanceof final PsiTryStatement tryStatement) {
            if (tryStatement.getTryBlock() != null) runBlock(tryStatement.getTryBlock());
        } else if (statement instanceof final PsiDeclarationStatement declaration) {
            for (final PsiElement element : declaration.getDeclaredElements()) {
                if (element instanceof final PsiLocalVariable local && local.getInitializer() != null) {
                    ops.add(new FormScript.Assign(id(local), local.getName(), false, convert(local.getInitializer()),
                            argTexts(local.getInitializer()), pointer(statement)));
                }
            }
        } else if (statement instanceof final PsiExpressionStatement expressionStatement) {
            final PsiExpression expression = expressionStatement.getExpression();
            if (expression instanceof final PsiAssignmentExpression fieldAssignment
                    && fieldAssignment.getOperationTokenType() == JavaTokenType.EQ
                    && fieldAssignment.getLExpression() instanceof final PsiReferenceExpression target
                    && target.getQualifierExpression() != null && !(target.getQualifierExpression() instanceof PsiThisExpression)
                    && target.getReferenceName() != null && fieldAssignment.getRExpression() != null) {
                // gbc.gridx = 0; – Feld eines anderen Objekts
                ops.add(new FormScript.FieldAssign(convert(target.getQualifierExpression()), target.getReferenceName(),
                        convert(fieldAssignment.getRExpression()), fieldAssignment.getRExpression().getText(), pointer(statement)));
            } else if (expression instanceof final PsiAssignmentExpression assignment
                    && assignment.getOperationTokenType() == JavaTokenType.EQ
                    && assignment.getLExpression() instanceof final PsiReferenceExpression target
                    && target.resolve() instanceof final PsiVariable variable
                    && !(variable instanceof PsiParameter)
                    && assignment.getRExpression() != null) {
                ops.add(new FormScript.Assign(id(variable), variable.getName(), variable instanceof PsiField,
                        convert(assignment.getRExpression()), argTexts(assignment.getRExpression()), pointer(statement)));
            } else if (expression instanceof final PsiMethodCallExpression call) {
                runCall(call, statement);
            }
        }
    }

    private void runCall(PsiMethodCallExpression call, PsiStatement statement) {
        final PsiReferenceExpression method = call.getMethodExpression();
        final String name = method.getReferenceName();
        if (name == null) return;
        if ("super".equals(name) || "this".equals(name)) {
            if ("super".equals(name)) {
                for (final PsiExpression argument : call.getArgumentList().getExpressions()) superArgs.add(convert(argument));
            } else if (call.resolveMethod() != null) {
                runMethod(call.resolveMethod());
            }
            return;
        }
        final PsiMethod own = ownMethod(call);
        if (own != null && !("addPopup".equals(name) && call.getArgumentList().getExpressionCount() == 2)) {
            runMethod(own);
            return;
        }
        final PsiExpression qualifier = method.getQualifierExpression();
        final Expr target = qualifier == null || qualifier instanceof PsiThisExpression ? new Expr.This() : convert(qualifier);
        final List<Expr> args = new ArrayList<>();
        final List<String> texts = new ArrayList<>();
        for (final PsiExpression argument : call.getArgumentList().getExpressions()) {
            args.add(convert(argument));
            texts.add(argument.getText());
        }
        ops.add(new FormScript.Invoke(target, name, args, texts, pointer(statement)));
    }

    /** Eine Methode dieser Klasse (unqualifiziert oder über {@code this}) mit Rumpf. */
    private @Nullable PsiMethod ownMethod(PsiMethodCallExpression call) {
        final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier != null && !(qualifier instanceof PsiThisExpression)) return null;
        final String name = call.getMethodExpression().getReferenceName();
        final PsiMethod resolved = call.resolveMethod();
        if (resolved != null) return resolved.getContainingClass() == formClass && resolved.getBody() != null ? resolved : null;
        // Ohne JDK lassen sich Aufrufe mit Swing-Argumenten nicht immer auflösen: per Name und Parameterzahl suchen
        for (final PsiMethod candidate : formClass.findMethodsByName(name, false)) {
            if (candidate.getBody() != null && candidate.getParameterList().getParametersCount() == call.getArgumentList().getExpressionCount()) {
                return candidate;
            }
        }
        return null;
    }

    // ---- Ausdrücke

    private Expr convert(@Nullable PsiExpression expression) {
        if (expression == null) return new Expr.Opaque("");
        if (expression instanceof final PsiParenthesizedExpression parenthesized) return convert(parenthesized.getExpression());
        if (expression instanceof final PsiTypeCastExpression cast) return convert(cast.getOperand());
        if (expression instanceof final PsiLiteralExpression literal) return new Expr.Literal(literal.getValue());
        if (expression instanceof PsiThisExpression) return new Expr.This();
        if (expression instanceof final PsiNewExpression newExpression) return convertNew(newExpression);
        if (expression instanceof final PsiArrayInitializerExpression initializer) return convertArray(initializer.getType(), initializer);
        if (expression instanceof final PsiMethodCallExpression call) return convertCall(call);
        if (expression instanceof final PsiReferenceExpression reference) return convertReference(reference);
        if (expression instanceof final PsiPrefixExpression prefix) {
            return new Expr.Unary(prefix.getOperationSign().getText(), convert(prefix.getOperand()));
        }
        if (expression instanceof final PsiPolyadicExpression polyadic) {
            final Object constant = constant(expression);
            if (constant != null) return new Expr.Literal(constant);
            final PsiExpression[] operands = polyadic.getOperands();
            Expr result = convert(operands[0]);
            for (int i = 1; i < operands.length; i++) {
                final PsiJavaToken sign = polyadic.getTokenBeforeOperand(operands[i]);
                result = new Expr.Binary(sign == null ? "+" : sign.getText(), result, convert(operands[i]));
            }
            return result;
        }
        return new Expr.Opaque(expression.getText());
    }

    private Expr convertNew(PsiNewExpression expression) {
        if (expression.getArrayInitializer() != null) return convertArray(expression.getType(), expression.getArrayInitializer());
        if (expression.isArrayCreation()) return new Expr.Opaque(expression.getText());
        final PsiAnonymousClass anonymous = expression.getAnonymousClass();
        final PsiJavaCodeReferenceElement reference = anonymous != null ? anonymous.getBaseClassReference() : expression.getClassReference();
        if (reference == null) return new Expr.Opaque(expression.getText());
        final String className = TypeResolver.qualifiedName(reference);
        boolean component = TypeResolver.isJdkComponent(className);
        String jdkSuperclass = component ? className : null;
        if (!component && reference.resolve() instanceof final PsiClass projectClass) {
            jdkSuperclass = TypeResolver.jdkComponentAncestor(projectClass);
            component = jdkSuperclass != null;
        }
        final List<Expr> args = new ArrayList<>();
        if (expression.getArgumentList() != null) {
            for (final PsiExpression argument : expression.getArgumentList().getExpressions()) args.add(convert(argument));
        }
        return new Expr.New(className, reference.getText(), args, component, jdkSuperclass);
    }

    private Expr convertArray(@Nullable PsiType type, PsiArrayInitializerExpression initializer) {
        if (!(type instanceof final PsiArrayType arrayType)) return new Expr.Opaque(initializer.getText());
        final List<Expr> elements = new ArrayList<>();
        for (final PsiExpression element : initializer.getInitializers()) elements.add(convert(element));
        return new Expr.ArrayInit(arrayType.getComponentType().getCanonicalText(), elements);
    }

    private Expr convertCall(PsiMethodCallExpression call) {
        final PsiReferenceExpression method = call.getMethodExpression();
        final String name = method.getReferenceName();
        final String bundleValue = Bundles.value(call);
        if (bundleValue != null) return new Expr.Literal(bundleValue);
        final List<Expr> args = new ArrayList<>();
        for (final PsiExpression argument : call.getArgumentList().getExpressions()) args.add(convert(argument));
        final PsiExpression qualifier = method.getQualifierExpression();
        if (qualifier == null || qualifier instanceof PsiThisExpression) {
            return ownMethod(call) != null ? new Expr.Opaque(call.getText()) : new Expr.Call(new Expr.This(), name, args);
        }
        final String staticClass = classOf(qualifier);
        if (staticClass != null) return new Expr.StaticCall(staticClass, name, args);
        return new Expr.Call(convert(qualifier), name, args);
    }

    private Expr convertReference(PsiReferenceExpression reference) {
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof final PsiVariable variable && !(variable instanceof PsiParameter)
                && !(variable instanceof final PsiField field && field.hasModifierProperty(PsiModifier.STATIC))) {
            return new Expr.Var(id(variable), String.valueOf(variable.getName()));
        }
        final Object constant = constant(reference);
        if (constant != null) return new Expr.Literal(constant);
        if (resolved instanceof final PsiField field && field.getContainingClass() != null && field.getContainingClass().getQualifiedName() != null) {
            return new Expr.StaticField(field.getContainingClass().getQualifiedName(), field.getName());
        }
        final PsiExpression qualifier = reference.getQualifierExpression();
        if (resolved == null && qualifier != null) {
            final String cls = classOf(qualifier);
            if (cls != null) return new Expr.StaticField(cls, reference.getReferenceName());
        }
        return new Expr.Opaque(reference.getText());
    }

    /** Ist der Qualifier ein Klassenname ({@code BorderLayout} in {@code BorderLayout.NORTH})? */
    private @Nullable String classOf(PsiExpression qualifier) {
        if (!(qualifier instanceof final PsiReferenceExpression reference)) return null;
        final PsiElement resolved = reference.resolve();
        if (resolved instanceof final PsiClass cls) return cls.getQualifiedName();
        if (resolved != null) return null;
        final String name = reference.getText();
        // Variablen schreibt man klein; Klassen groß (oder qualifiziert: javax.swing.SwingConstants)
        final String last = name.substring(name.lastIndexOf('.') + 1);
        if (last.isEmpty() || !Character.isUpperCase(last.charAt(0))) return null;
        return TypeResolver.byImports(qualifier.getContainingFile(), name);
    }

    private static @Nullable Object constant(PsiExpression expression) {
        try {
            return JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper().computeConstantExpression(expression);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static List<String> argTexts(PsiExpression expression) {
        final List<String> texts = new ArrayList<>();
        if (expression instanceof final PsiNewExpression newExpression && newExpression.getArgumentList() != null) {
            for (final PsiExpression argument : newExpression.getArgumentList().getExpressions()) texts.add(argument.getText());
        }
        return texts;
    }

    private int id(PsiVariable variable) {
        return varIds.computeIfAbsent(variable, v -> varIds.size() + 1);
    }

    @SuppressWarnings("unchecked")
    private SmartPsiElementPointer<PsiElement> pointer(PsiElement element) {
        return (SmartPsiElementPointer<PsiElement>) (SmartPsiElementPointer<?>) pointers.createSmartPsiElementPointer(element);
    }
}
