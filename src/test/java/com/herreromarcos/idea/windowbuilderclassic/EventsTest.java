package com.herreromarcos.idea.windowbuilderclassic;

import com.herreromarcos.idea.windowbuilderclassic.codegen.FormWriter;
import com.herreromarcos.idea.windowbuilderclassic.model.FormModel;
import com.herreromarcos.idea.windowbuilderclassic.runtime.EventCatalog;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SmartPsiElementPointer;

import java.util.List;

/** Event-Handler im Stil des WindowBuilder. */
public class EventsTest extends WindowBuilderTestCase {
    private static EventCatalog.EventSet set(Class<?> cls, String name) {
        return EventCatalog.of(cls).stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    private static EventCatalog.EventMethod method(EventCatalog.EventSet set, String name) {
        return set.methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }

    public void testCatalog() {
        final EventCatalog.EventSet action = set(javax.swing.JButton.class, "action");
        assertEquals("addActionListener", action.addMethod());
        assertEquals("java.awt.event.ActionListener", action.listenerType());
        assertNull(action.adapter());
        assertEquals(List.of(new EventCatalog.EventMethod("actionPerformed", "java.awt.event.ActionEvent")), action.methods());
        assertEquals("java.awt.event.MouseAdapter", set(javax.swing.JButton.class, "mouse").adapter());
        assertNotNull(EventCatalog.defaultEvent(javax.swing.JButton.class));
        assertNull(EventCatalog.defaultEvent(javax.swing.JLabel.class));
    }

    public void testActionPerformed() {
        final FormModel model = load(Forms.PANEL);
        final EventCatalog.EventSet action = set(javax.swing.JButton.class, "action");
        final SmartPsiElementPointer<PsiElement> target = writer(model).addEventHandler(find(model, "btnOne"), action, action.methods().get(0));
        assertLines(
                "JButton btnOne = new JButton(\"One\");",
                "btnOne.addActionListener(new ActionListener() {",
                "public void actionPerformed(ActionEvent e) {",
                "}",
                "});",
                "add(btnOne);");
        assertTrue(text().contains("import java.awt.event.ActionListener;") || text().contains("import java.awt.event.*;"));
        assertInstanceOf(target.getElement(), PsiMethod.class);
        assertEquals(List.of("actionPerformed"), FormWriter.handlerMethods(find(model(), "btnOne"), "addActionListener"));
        // Ein zweites Mal: derselbe Handler, kein neuer
        final FormModel updated = model();
        writer(updated).addEventHandler(find(updated, "btnOne"), action, action.methods().get(0));
        assertEquals(1, text().split("addActionListener").length - 1);
    }

    public void testAdapterAndSecondMethod() {
        FormModel model = load(Forms.PANEL);
        final EventCatalog.EventSet mouse = set(javax.swing.JButton.class, "mouse");
        writer(model).addEventHandler(find(model, "btnTwo"), mouse, method(mouse, "mouseClicked"));
        assertLines(
                "btnTwo.addMouseListener(new MouseAdapter() {",
                "@Override",
                "public void mouseClicked(MouseEvent e) {",
                "}",
                "});");
        model = model();
        writer(model).addEventHandler(find(model, "btnTwo"), mouse, method(mouse, "mousePressed"));
        assertEquals(1, text().split("addMouseListener").length - 1);
        assertEquals(List.of("mouseClicked", "mousePressed"), FormWriter.handlerMethods(find(model(), "btnTwo"), "addMouseListener"));
        model = model();
        writer(model).removeEventHandler(find(model, "btnTwo"), mouse, method(mouse, "mouseClicked"));
        model = model();
        writer(model).removeEventHandler(find(model, "btnTwo"), mouse, method(mouse, "mousePressed"));
        assertFalse(text(), text().contains("addMouseListener"));
    }

    public void testWindowListenerOnFrame() {
        final FormModel model = load(Forms.frame("null"));
        final EventCatalog.EventSet window = set(javax.swing.JFrame.class, "window");
        writer(model).addEventHandler(model.getRoot(), window, method(window, "windowClosing"));
        assertLines("addWindowListener(new WindowAdapter() {", "@Override", "public void windowClosing(WindowEvent e) {");
    }

    public void testLambdaIsRecognized() {
        final FormModel model = load("""
                import javax.swing.*;

                public class Form extends JPanel {
                    public Form() {
                        JButton b = new JButton("x");
                        b.addActionListener(e -> System.out.println(e));
                        add(b);
                    }
                }
                """);
        assertEquals(List.of("*"), FormWriter.handlerMethods(find(model, "b"), "addActionListener"));
        final EventCatalog.EventSet action = set(javax.swing.JButton.class, "action");
        final SmartPsiElementPointer<PsiElement> target = writer(model).addEventHandler(find(model, "b"), action, action.methods().get(0));
        assertTrue(target.getElement().getText().contains("e -> System.out.println(e)"));
    }
}
