package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.FormWriter;
import com.herreromarcos.idea.windowbuilderclassic.model.ComponentInfo;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.model.FormScript;
import com.herreromarcos.idea.windowbuilderclassic.parser.FormParser;
import com.herreromarcos.idea.windowbuilderclassic.runtime.FormBuilder;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/** Testbasis mit echtem JDK (das der Test-JVM), damit javax.swing auflösbar ist und Imports gesetzt werden. */
public abstract class WindowBuilderTestCase extends LightJavaCodeInsightFixtureTestCase {
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final LightProjectDescriptor DESCRIPTOR =
            new DefaultLightProjectDescriptor(() -> JavaSdk.getInstance().createJdk("test-jdk", JAVA_HOME, false));

    @Override
    protected void setUp() throws Exception {
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), JAVA_HOME);
        super.setUp();
    }

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return DESCRIPTOR;
    }

    protected FormModel load(String text) {
        myFixture.configureByText("Form.java", text);
        return model();
    }

    protected FormModel model() {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        final FormScript script = FormParser.parse(myFixture.getFile());
        assertNotNull("no script", script);
        final FormModel model = FormBuilder.build(script);
        assertNotNull("no model", model);
        return model;
    }

    protected FormWriter writer(FormModel model) {
        return new FormWriter(model, myFixture.getFile());
    }

    protected static ComponentInfo find(FormModel model, String reference) {
        final ComponentInfo info = model.findByReference(reference);
        assertNotNull("component " + reference, info);
        return info;
    }

    protected String text() {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        return myFixture.getFile().getText();
    }

    /** Prüft, dass die Zeilen (ohne Einrückung) in dieser Reihenfolge direkt hintereinander im Code stehen. */
    protected void assertLines(String... lines) {
        final String normalized = text().lines().map(String::trim).filter(l -> !l.isEmpty()).reduce("", (a, b) -> a + b + "\n");
        final String expected = String.join("\n", lines) + "\n";
        assertTrue("expected\n%s\nin\n%s".formatted(expected, text()), normalized.contains(expected));
    }
}
