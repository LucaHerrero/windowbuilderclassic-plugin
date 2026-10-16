package com.herreromarcos.idea.windowbuilderclassic.parser;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceExpression;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ResourceBundles, wie sie die NLS-Unterstützung des WindowBuilder erzeugt:
 * {@code private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("pkg.messages")} und
 * {@code BUNDLE.getString("Form.btnOk.text")}.
 */
public final class Bundles {
    private Bundles() {
    }

    /** Der Wert hinter {@code BUNDLE.getString("key")} bzw. {@code ResourceBundle.getBundle("x").getString("key")}. */
    public static @Nullable String value(PsiMethodCallExpression call) {
        final IProperty property = property(call);
        return property == null ? null : property.getValue();
    }

    public static @Nullable IProperty property(PsiMethodCallExpression call) {
        if (!"getString".equals(call.getMethodExpression().getReferenceName()) || call.getArgumentList().getExpressionCount() != 1) return null;
        if (!(call.getArgumentList().getExpressions()[0] instanceof final PsiLiteralExpression key) || !(key.getValue() instanceof final String name)) return null;
        final String bundle = bundleName(call.getMethodExpression().getQualifierExpression());
        if (bundle == null) return null;
        for (final PropertiesFile file : files(call, bundle)) {
            final IProperty property = file.findPropertyByKey(name);
            if (property != null) return property;
        }
        return null;
    }

    /** Basisname aus {@code ResourceBundle.getBundle("x")} – direkt oder über ein statisches Feld. */
    private static @Nullable String bundleName(@Nullable PsiExpression qualifier) {
        PsiExpression expression = qualifier;
        if (expression instanceof final PsiReferenceExpression reference && reference.resolve() instanceof final PsiField field
                && field.hasModifierProperty(PsiModifier.STATIC)) {
            expression = field.getInitializer();
        }
        if (expression instanceof final PsiMethodCallExpression getBundle && "getBundle".equals(getBundle.getMethodExpression().getReferenceName())
                && getBundle.getArgumentList().getExpressionCount() >= 1
                && getBundle.getArgumentList().getExpressions()[0] instanceof final PsiLiteralExpression literal
                && literal.getValue() instanceof final String name) {
            return name;
        }
        return null;
    }

    private static List<PropertiesFile> files(PsiElement context, String bundle) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(context);
        if (module == null) return List.of();
        return PropertiesReferenceManager.getInstance(context.getProject()).findPropertiesFiles(module, bundle);
    }

    /** Die Properties-Datei zum Bundle; fehlt sie, entsteht sie im Paket der Formularklasse. */
    public static @Nullable PropertiesFile findOrCreate(PsiFile form, String bundle) {
        final List<PropertiesFile> existing = files(form, bundle);
        if (!existing.isEmpty()) return existing.get(0);
        final PsiDirectory directory = form.getContainingDirectory();
        if (directory == null) return null;
        final String name = bundle.substring(bundle.lastIndexOf('.') + 1) + ".properties";
        final PsiFile file = PsiFileFactory.getInstance(form.getProject()).createFileFromText(name, PropertiesFileType.INSTANCE, "");
        return directory.add(file) instanceof final PropertiesFile created ? created : null;
    }
}
