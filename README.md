# WindowBuilder Classic für IntelliJ IDEA

Ein visueller Designer für Java Swing, gebaut nach dem Vorbild des Eclipse WindowBuilder. Anders als der GUI Designer von IntelliJ gibt es hier keine `.form`-Dateien und keinen generierten Bereich, den man nicht anfassen darf. Der Designer liest den normalen Java-Code, zeigt das Fenster an und schreibt jede Änderung als ganz gewöhnlichen Code zurück. Diesen Code kann man danach von Hand weiterbearbeiten, und der Designer liest ihn beim nächsten Mal genauso wieder ein.

Das Plugin ist ein eigenständiger Nachbau des Verhaltens. Code aus dem Eclipse-Projekt steckt nicht darin. Es läuft ab IntelliJ IDEA 2024.3, in der Community- wie in der Ultimate-Edition.

## Installation

```
gradlew buildPlugin
```

Die ZIP-Datei liegt danach in `build/distributions/`. In IntelliJ unter *Settings → Plugins → ⚙ → Install Plugin from Disk…* auswählen, fertig.

Zum Ausprobieren startet `gradlew runIde` eine Sandbox-IDE mit dem Plugin.

## Was das Plugin kann

### Design-Tab
Jede Java-Datei, die Swing oder AWT importiert, bekommt unten neben *Text* einen Tab *Design*. Der Aufbau ist derselbe wie im WindowBuilder:

- **Components**: der Komponentenbaum, z.B. `btnOk - "OK"`, inklusive `getContentPane()`.
- **Properties**: Variable, Klasse, Layout, Constraints, Bounds und die Bean-Eigenschaften. Gesetzte Werte sind fett, *Entf* stellt den Standardwert wieder her. Mit dem Auge-Symbol werden alle Eigenschaften sichtbar.
- **Palette**: System, Containers, Layouts, Struts & Springs, Components, Menu und AWT Components in der Reihenfolge des WindowBuilder. Liegen MigLayout oder JGoodies Forms im Modul, kommt die Kategorie *Third-party Layouts* dazu.
- **Zeichenfläche**: das echte, lebende Formular. Fenster bekommen eine Titelleiste.

### Bedienung
- Ein Eintrag aus der Palette wird angeklickt, dann wird in den Container geklickt. Mit gedrückter *Strg*-Taste bleibt das Werkzeug aktiv.
- Beim Ablegen zeigt der Designer passendes Feedback: im BorderLayout die Region, in Flow-, Grid- und BoxLayout die Einfügeposition, im GridBagLayout Zelle oder Gitterlinie, im Absolute Layout Rahmen und Koordinaten.
- Komponenten lassen sich verschieben, auch in andere Container. Im Absolute Layout kann man sie außerdem über die Griffe in der Größe ändern, im GridBagLayout ändert man so die Spannweite. Fenster werden über die Griffe rechts und unten angepasst.
- **Mehrfachauswahl** mit *Strg*- oder *Shift*-Klick, ein Rahmen aufziehen geht mit dem Werkzeug *Marquee*. Mehrere Komponenten im Absolute Layout lassen sich gemeinsam verschieben, die Pfeiltasten schieben sie um 1 Pixel, mit *Shift* um 10.
- **Snapping** im Absolute Layout: Beim Verschieben und Größe ändern rasten Kanten an den Kanten der Geschwister und des Containers ein, rote Hilfslinien zeigen das an. Mit gedrückter *Alt*-Taste ist das aus.
- **Ausrichtungswerkzeuge** für Absolute Layout in der Symbolleiste: linke, rechte, obere und untere Kanten sowie Mitten bündig ausrichten, Breite oder Höhe übernehmen, gleichmäßig verteilen, im Fenster zentrieren. Bezug ist die zuerst ausgewählte Komponente.
- **Ausschneiden, Kopieren, Einfügen** (*Strg+X/C/V*): Eingefügt wird der ganze Code der Komponente samt Kindern. Variablen bekommen neue Namen (`btnOk_1`), im Absolute Layout landet die Kopie leicht versetzt.
- *Leertaste* bearbeitet den Text direkt, *Entf* löscht.
- Ein **Doppelklick** auf einen Button, ein Textfeld oder eine Combobox öffnet wie im WindowBuilder den `actionPerformed`-Handler und legt ihn bei Bedarf an. Bei anderen Komponenten springt er zur Stelle im Quelltext.
- *Test/Preview* öffnet das Formular in einem echten, bedienbaren Fenster.
- Jede Aktion ist ein einzelner Undo-Schritt im Dokument. *Strg+Z* funktioniert deshalb in beiden Tabs.

### Events
Das Blitz-Symbol in den Properties schaltet auf die Events der Komponente um: je Listener-Typ (`action`, `mouse`, `key` …) die Methoden. Ein Doppelklick legt den Handler an oder springt hinein, *Entf* entfernt ihn. Dasselbe steht im Kontextmenü unter *Add Event Handler*. Erzeugt wird der Code wie im WindowBuilder:

```java
btnNewButton.addActionListener(new ActionListener() {
    public void actionPerformed(ActionEvent e) {
    }
});
btnNewButton.addMouseListener(new MouseAdapter() {
    @Override
    public void mouseClicked(MouseEvent e) {
    }
});
```

Gibt es einen Adapter, wird er verwendet. Weitere Methoden kommen in dieselbe anonyme Klasse. Vorhandene Lambdas und Methodenreferenzen werden erkannt. Auf Wunsch entstehen Listener mit nur einer Methode als Lambda (siehe Einstellungen).

### Menüs
- Eine `JMenuBar` legt man auf das Fenster (`setJMenuBar(menuBar)`), `JMenu` auf die Menüleiste, Einträge (`JMenuItem`, `JCheckBoxMenuItem`, `JRadioButtonMenuItem`, Trenner) auf ein Menü. Ist ein Menü oder einer seiner Einträge ausgewählt, klappt der Designer es auf. Beim Ablegen klappt das Menü unter der Maus auf.
- Ein `JPopupMenu` legt man auf eine Komponente. Wie im WindowBuilder entsteht dabei `addPopup(component, popupMenu)` samt Hilfsmethode `addPopup`. Popup-Menüs öffnen sich unter ihrer Komponente, sobald sie ausgewählt sind.

### Struts & Springs und AWT
- `Box.createHorizontalBox()`, `Box.createVerticalStrut(20)`, `Box.createGlue()`, `Box.createRigidArea(…)` und Co. erscheinen als eigene Komponenten. Unsichtbare Füller zeichnet der Designer gestrichelt.
- AWT-Komponenten (`Button`, `Label`, `Checkbox`, `Choice`, `List`, `TextField`, `TextArea`, `Scrollbar`, `Panel`, `Canvas`, `ScrollPane`) sowie `Frame` und `Dialog` als Wurzel werden gelesen und geschrieben. Dargestellt werden sie durch Swing-Gegenstücke, weil echte AWT-Komponenten als native Fenster über der IDE lägen.

### Erzeugter Code
Der Code entspricht dem, was der WindowBuilder schreibt:

```java
JButton btnNewButton = new JButton("New button");
btnNewButton.setBounds(10, 10, 89, 23);
contentPane.add(btnNewButton);
```

- Lokale Variablen mit WindowBuilder-Namen (`btnNewButton`, `lblNewLabel`, `chckbxNewCheckBox`, `mntmNewMenuItem`), Dubletten werden zu `btnNewButton_1`.
- Textfelder und Tabellen werden Felder. Bei `JTextField` steht `setColumns(10)` nach dem `add`.
- Neue Eigenschaften kommen hinter die vorhandenen der Komponente, neue Kinder hinter den Code ihres Containers. Benutzt der Code schon `{ … }`-Blöcke wie die Dialog-Vorlage, bekommen neue Kinder ebenfalls einen eigenen Block.
- Wer die Reihenfolge in Flow-, Grid- oder BoxLayout ändert, verschiebt die Anweisungen, nicht einen Index.
- Beim Wechsel auf Absolute Layout bekommen alle Kinder `setBounds`. Beim Wechsel weg vom BorderLayout fallen die Constraints weg.
- Imports folgen dem Codestil des Projekts.
- Über das Kontextmenü wird aus einer lokalen Variable ein Feld (*Convert Local to Field*) und umgekehrt, solange das Feld nur in der Methode benutzt wird, in der es entsteht.

### Einstellungen
Unter *Settings → Tools → WindowBuilder Classic* (je Projekt), angelehnt an die Preferences des WindowBuilder:
- neue Komponenten als Felder statt lokale Variablen
- Code neuer Komponenten in Blöcke `{ … }`
- Listener mit einer Methode als Lambda
- GridBagLayout: Abstände ja oder nein, Größe in Pixeln
- Projektcode ausführen, um eigene Komponenten darzustellen (siehe unten)

### NLS
*Externalize Strings…* in der Symbolleiste lagert Texte aus `setText`, `setTitle`, `setToolTipText` und Konstruktoren in ein ResourceBundle aus. Schlüssel und Bundle sind wählbar, Standard ist `<paket>.messages`:

```java
private static final ResourceBundle BUNDLE = ResourceBundle.getBundle("com.example.messages");
…
JButton btnOk = new JButton(BUNDLE.getString("LoginFrame.btnOk.text"));
```

Der Designer zeigt die Texte aus der Properties-Datei an. Wer einen ausgelagerten Text in den Properties oder direkt per *Leertaste* ändert, ändert den Wert in der Properties-Datei, der Code bleibt gleich.

### Layouts und Container
Absolute Layout (`null`), `BorderLayout`, `FlowLayout`, `GridLayout`, `GridBagLayout`, `CardLayout`, `BoxLayout`, `SpringLayout` und `GroupLayout`, aus Bibliotheken außerdem MigLayout und JGoodies `FormLayout`. Dazu kommen `JScrollPane` (`setViewportView`), `JSplitPane` (links/rechts bzw. oben/unten) und `JTabbedPane` (`addTab`).

- **CardLayout**: Neue Karten bekommen wie im WindowBuilder einen Namen `"name_…"`. Wer im Baum eine Komponente auswählt, sieht ihre Karte.
- **GroupLayout und SpringLayout** funktionieren wie ein freies Design: Ablegen, Verschieben und Größe ändern wie im Absolute Layout. Den Layout-Code erzeugt der Designer aus den Positionen, bei GroupLayout je Komponente eine Gruppe mit Abstand und Größe, bei SpringLayout `putConstraint` an NORTH/WEST (und SOUTH/EAST bei geänderter Größe). Vorhandener GroupLayout-Code, etwa aus dem NetBeans-Designer mit `initComponents()`, wird korrekt dargestellt. Ändert man dort etwas, schreibt der Designer die Gruppen in seinem Stil neu.
- **MigLayout** (`"cell 1 0"`) und **FormLayout** (`"2, 4"`): Abgelegt wird in die Zelle unter der Maus, bei MigLayout auch rechts bzw. unterhalb von allem in eine neue. Die Constraints lassen sich in den Properties als Text bearbeiten. Dargestellt werden beide über die Bibliothek im Classpath des Moduls.
- Andere Layouts werden korrekt angezeigt. Neue Komponenten kommen dort ans Ende.

### GridBagLayout
So wie im WindowBuilder:

```java
GridBagLayout gbl_contentPane = new GridBagLayout();
gbl_contentPane.columnWidths = new int[]{0, 0, 0};
gbl_contentPane.rowHeights = new int[]{0, 0};
gbl_contentPane.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
gbl_contentPane.rowWeights = new double[]{0.0, Double.MIN_VALUE};
contentPane.setLayout(gbl_contentPane);

JLabel lblName = new JLabel("Name");
GridBagConstraints gbc_lblName = new GridBagConstraints();
gbc_lblName.insets = new Insets(0, 0, 0, 5);
gbc_lblName.gridx = 0;
gbc_lblName.gridy = 0;
contentPane.add(lblName, gbc_lblName);
```

- Die Arrays haben eine Spalte bzw. Zeile mehr als das Gitter. Die letzte (`0` bzw. `Double.MIN_VALUE`) nimmt nur den Restplatz auf, damit das Gitter oben links bleibt.
- Abgelegt wird in eine freie Zelle oder hinter die letzte Spalte bzw. Zeile, dann entsteht eine neue. Wer auf eine Gitterlinie ablegt, fügt dort eine Spalte bzw. Zeile ein, die übrigen Komponenten rücken nach.
- Ist der Container oder eine Komponente darin ausgewählt, zeigt der Designer das Gitter mit Spalten- und Zeilenköpfen. Über einen Klick auf einen Kopf lässt sich die Spalte bzw. Zeile einfügen, löschen oder mitwachsen lassen (Gewicht 1.0). Wachsende Köpfe sind farbig hinterlegt.
- Die Constraints (`gridx` … `ipady`, `anchor`, `fill`, `insets`) stehen in den Properties. Gesetzte Felder sind fett.
- Ausrichtung wie im WindowBuilder: Ist eine Komponente im GridBagLayout ausgewählt, zeigt die Symbolleiste (und das Kontextmenü unter *Alignment*) horizontal links, mittig, rechts, füllen und vertikal oben, mittig, unten, füllen. Daraus entstehen `anchor` (z.B. `NORTHWEST`) und `fill`. *Grow Column*/*Grow Row* lassen die Spalte bzw. Zeile der Komponente mitwachsen. Über den Kopf einer Spalte bzw. Zeile richtet *Alignment* alle Komponenten darin auf einmal aus.
- Die Griffe rechts und unten ändern `gridwidth`/`gridheight`. Das Rechteck rastet dabei auf die Zellgrenzen ein.
- Abstände: Wie im WindowBuilder bekommt jede Komponente 5 Pixel Abstand rechts und unten, außer in der letzten Spalte bzw. Zeile. Das passt der Designer bei jeder Änderung an, allerdings nur bei Komponenten mit eigenem `gbc_…` und nur, wenn der Container die Abstände schon nutzt oder neu ist. Handgeschriebene Insets bleiben, wie sie sind.
- Handgeschriebener Code mit einem gemeinsamen `GridBagConstraints c` für mehrere Komponenten oder mit `new GridBagConstraints(…)` direkt im `add` wird korrekt gelesen. Ändert man eine dieser Komponenten, bekommt sie vorher ein eigenes `gbc_…` mit ihren bisherigen Werten. Die anderen bleiben unverändert.
- Beim Wechsel auf GridBagLayout bleibt die Anordnung erhalten: BorderLayout wird zum 3×3-Gitter, Flow/Box zu einer Reihe bzw. Spalte, GridLayout zeilenweise, Absolute Layout nach Position. Leere Spalten und Zeilen fallen weg. Beim Wechsel weg vom GridBagLayout verschwinden `gbl_…` und alle `gbc_…`.

### Wizards
Unter *New → WindowBuilder Class* gibt es die Vorlagen des WindowBuilder: JFrame, JPanel, JDialog (mit OK/Cancel), Application Window und JInternalFrame.

## Wie der Code gelesen wird

Das Verfahren ist das des WindowBuilder:

1. Gelesen wird die erste Klasse der Datei. Erbt sie von einer Swing-Klasse, ist sie selbst das Formular. Sonst ist es das erste Fenster, das sie erzeugt, wie bei der Vorlage „Application Window“ mit `frame = new JFrame()`.
2. Ausgeführt werden die Feld-Initialisierer, dann der Konstruktor (bevorzugt der ohne Parameter) und jede eigene Methode, die er aufruft, also z.B. `initialize()`. Blöcke und `try`-Rümpfe werden durchlaufen, `if` und Schleifen nicht.
3. Konstruktoren, Setter und `add`-Aufrufe werden mit den echten Swing-Klassen nachgespielt. Fenster ersetzt der Designer durch eine Nachbildung mit echter `JRootPane`, sodass Content Pane und Menüleiste wie zur Laufzeit funktionieren.
4. Ausgeführt wird JDK-Code aus `java.awt` und `javax.swing`. Statische Methoden kommen nur aus einer festen Liste (`BorderFactory`, `Box`, `Color`, `Font`, `UIManager`, `Spring` …), `System.exit` und `JOptionPane` laufen nie. Neben Settern spielt der Designer nur harmlose Aufrufe nach (`add`, `addItem`, `addSeparator`), dazu die Gruppen-Ketten von GroupLayout.
5. **Projektcode**: Eigene Komponenten (`new MyPanel()`), Layouts aus Bibliotheken (MigLayout, FormLayout) und eine Projekt-Oberklasse des Formulars (`extends BasePanel`, mit deren Komponenten) lädt der Designer wie der WindowBuilder aus der kompilierten Ausgabe und den Bibliotheken des Moduls und führt ihre Konstruktoren aus. Nach jedem Build lädt er sie neu. Gibt es noch keine kompilierte Klasse, erscheint ein beschrifteter Platzhalter. Wer das nicht möchte, schaltet es in den Einstellungen ab.

Was sich nicht auswerten lässt, wird übersprungen und oben rechts als Problem gemeldet.

## Grenzen

- **Look and Feel**: Die Zeichenfläche verwendet das Look and Feel der IDE, eine Auswahl wie im WindowBuilder gibt es nicht. Swing hält das Look and Feel global für die ganze JVM. Ein Umschalten würde die IDE mit umstellen, und ein Umschalten nur für einzelne Komponenten liefert falsche Farben und Schriften, weil die UI-Klassen ihre Werte trotzdem aus der globalen Tabelle holen. *Test/Preview* zeigt das Formular ebenfalls im Look and Feel der IDE.
- `JApplet` wird nicht unterstützt (seit Java 9 veraltet und zur Entfernung vorgesehen).
- AWT-Komponenten werden durch Swing-Gegenstücke dargestellt. Die Properties zeigen deshalb deren Eigenschaften.
- GroupLayout und SpringLayout bearbeitet der Designer als freies Design. Ausrichtungen und Abhängigkeiten zwischen Komponenten aus fremdem Code gehen beim ersten Ändern verloren.
- Die Größe eines `JPanel` ohne `setPreferredSize`/`setSize` gilt nur für die Sitzung.

## Entwicklung

- Gebaut wird mit Java 21. Fehlt das JDK, lädt Gradle es selbst herunter.
- Der Gradle-Wrapper bleibt wie bei den anderen Plugins auf 9.7.1.
- `gradlew test` startet die Tests, `gradlew verifyPlugin` prüft die Kompatibilität. Die Tests verwenden das JDK der Test-JVM, damit `javax.swing` aufgelöst wird. Für die Tests mit Projektklassen kompiliert der `javac` der Gradle-Toolchain kleine Testklassen.
- `gradlew test -Psnapshots=<Ordner>` legt Bilder des Design-Tabs aus den Editor-Tests als PNG ab. So lässt sich die Darstellung ohne IDE prüfen.

Der Ablauf im Code: `FormParser` übersetzt PSI im Hintergrund in ein `FormScript`, eine Liste von Anweisungen mit PSI-freien Ausdrücken. `FormBuilder` führt das Skript auf dem EDT aus und baut das `FormModel` mit lebenden Komponenten. `FormWriter` schreibt Änderungen per PSI zurück, für GridBagLayout mit `GridBagWriter`, für GroupLayout und SpringLayout mit `FreeLayoutWriter`. Die PSI-Änderung löst danach automatisch ein neues Parsen aus.

Zum Code-Stil: `final` an Feldern und lokalen Variablen, nicht an Parametern und Klassen. Meldungen schreibe ich als Format-Strings.

## Releases

Jeder Push auf `master` baut das Plugin, lässt Tests und Verifier laufen und veröffentlicht die ZIP als GitHub Release. Die Version steht in `build.gradle.kts`.

## Lizenz

Apache License 2.0, siehe [LICENSE](LICENSE). „WindowBuilder“ ist ein Projekt der Eclipse Foundation, dieses Plugin ist davon unabhängig.
