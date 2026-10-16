import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

// Gradle-Version (gradle/wrapper): wie bei den anderen Plugins vorerst 9.7.1 – IntelliJ 2026.2 übernimmt mit
// Gradle 9.8.x die Abhängigkeiten des main-Source-Sets nicht ins IDE-Modell.
group = "com.herreromarcos.idea"
version = "0.3.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.3.5")
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.properties")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

intellijPlatform {
    // Keine GUI-Designer-Formulare → Bytecode-Instrumentierung unnötig
    instrumentCode = false

    pluginConfiguration {
        id = "com.herreromarcos.idea.windowbuilderclassic"
        name = "WindowBuilder Classic"
        version = project.version.toString()
        changeNotes = """
            <b>0.3.0</b>
            <ul>
              <li>Events: event view in the property table, "Add Event Handler" menu, double-click opens actionPerformed;
                  anonymous classes and adapters as in WindowBuilder, optionally lambdas</li>
              <li>Menus: JMenuBar, JMenu, menu items, JPopupMenu with the addPopup helper; menus open in the designer</li>
              <li>Struts &amp; Springs (Box, struts, glue, rigid area) and AWT components</li>
              <li>Multiple selection, marquee, moving several components, snapping with guides, arrow keys,
                  alignment tools for absolute layout, cut/copy/paste</li>
              <li>GridBagLayout: span via handles, alignment per column/row</li>
              <li>CardLayout, SpringLayout and GroupLayout (also renders NetBeans forms); MigLayout and FormLayout from libraries</li>
              <li>Custom components and project superclasses are rendered by running the compiled project code</li>
              <li>Code generation settings (fields/local variables, blocks, lambdas, GridBag gaps); convert local ↔ field</li>
              <li>Externalize strings into a ResourceBundle; externalized texts are shown and edited in the properties file</li>
            </ul>
            <b>0.2.1</b>
            <ul>
              <li>GridBagLayout: alignment buttons in the toolbar and context menu as in WindowBuilder
                  (left/center/right/fill, top/center/bottom/fill, grow column/row)</li>
              <li>Fixed: <code>gridx = 0</code> was shown and written as <code>REMAINDER</code></li>
              <li>Fixed: selection, grid lines and headers were only drawn for components already on screen</li>
              <li>The canvas background now sets the form apart</li>
            </ul>
            <b>0.2.0</b>
            <ul>
              <li>GridBagLayout in the WindowBuilder style: <code>gbl_…</code> with column/row arrays, one
                  <code>gbc_…</code> per component, automatic 5&nbsp;px gaps via <code>insets</code></li>
              <li>Drop into a cell, behind the last column/row or onto a grid line to insert a column/row</li>
              <li>Column and row headers: insert, delete, grow</li>
              <li>Constraints in the property table (gridx … ipady, anchor, fill, insets)</li>
              <li>Converting to GridBagLayout keeps the arrangement of Border, Flow, Grid, Box and absolute layouts</li>
              <li>Shared or inline <code>GridBagConstraints</code> are split into an own variable when edited</li>
              <li>Field assignments (<code>gbc.gridx = 0;</code>) and array literals are parsed</li>
            </ul>
            <b>0.1.0</b>
            <ul>
              <li>First release: Design tab for Swing classes, palette, component tree, property table,
                  round-trip parsing of plain Java code, absolute, Border, Flow, Grid and Box layout,
                  wizards for JFrame, JPanel, JDialog, JInternalFrame and Application Window</li>
            </ul>
        """.trimIndent()
        ideaVersion {
            sinceBuild = "243"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides {
            create("IC", "2024.3.5")
        }
    }
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
    processResources {
        from(files("LICENSE")) {
            into("META-INF")
        }
    }
    test {
        // Swing-Komponenten werden in den Tests ohne Bildschirm erzeugt
        systemProperty("java.awt.headless", "true")
        providers.gradleProperty("snapshots").orNull?.let { systemProperty("windowbuilder.snapshots", it) }
        // Die Test-JVM (JBR) hat keinen Compiler – für ProjectClassesTest den javac der Toolchain mitgeben
        val javac = project.extensions.getByType<JavaToolchainService>().compilerFor { languageVersion.set(JavaLanguageVersion.of(21)) }
        systemProperty("windowbuilder.javac", javac.get().executablePath.asFile.absolutePath)
    }
}
