package com.herreromarcos.idea.windowbuilderclassic.codegen;

import java.util.Map;
import java.util.Set;

/**
 * Variablennamen nach den Regeln des WindowBuilder: Kürzel plus Text bei beschrifteten Komponenten
 * ({@code btnNewButton}, {@code lblNewLabel}), sonst der Klassenname ohne „J“ ({@code textField}); Dubletten bekommen
 * {@code _1}, {@code _2} …
 */
public final class VariableNames {
    private static final Map<String, String> ACRONYMS = Map.of(
            "JButton", "btn", "JLabel", "lbl", "JCheckBox", "chckbx", "JRadioButton", "rdbtn",
            "JToggleButton", "tglbtn", "JMenuItem", "mntm", "JMenu", "mn", "JCheckBoxMenuItem", "chckbxmntm",
            "JRadioButtonMenuItem", "rdbtnmntm");

    private VariableNames() {
    }

    /** @param text Beschriftung aus dem Konstruktor, z.B. {@code New button}; {@code null} wenn keine */
    public static String base(String className, String text) {
        final String simple = className.substring(className.lastIndexOf('.') + 1).replaceAll("<.*>", "");
        final String acronym = ACRONYMS.get(simple);
        if (acronym != null && text != null && !text.isBlank()) {
            final StringBuilder name = new StringBuilder(acronym);
            for (final String word : text.split("[^\\p{L}\\p{N}]+")) {
                if (!word.isEmpty()) name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
            return name.toString();
        }
        final String stripped = simple.length() > 1 && simple.charAt(0) == 'J' && Character.isUpperCase(simple.charAt(1)) ? simple.substring(1) : simple;
        return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
    }

    public static String unique(String base, Set<String> taken) {
        if (!taken.contains(base)) return base;
        for (int i = 1; ; i++) {
            final String candidate = base + "_" + i;
            if (!taken.contains(candidate)) return candidate;
        }
    }
}
