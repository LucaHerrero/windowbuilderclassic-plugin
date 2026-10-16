package com.herreromarcos.idea.windowbuilderclassic.editor;

import com.herreromarcos.idea.windowbuilderclassic.codegen.GridBagAlignment;
import com.herreromarcos.idea.windowbuilderclassic.palette.Palette;
import com.herreromarcos.idea.windowbuilderclassic.palette.PaletteEntry;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Alle gezeichneten Symbole lassen sich malen; mit -Psnapshots landet eine Übersicht als PNG im Ordner. */
public class IconsTest extends BasePlatformTestCase {
    public void testAllIconsPaint() throws Exception {
        final List<Icon> icons = new ArrayList<>();
        for (final GridBagAlignment.Horizontal h : GridBagAlignment.Horizontal.values()) icons.add(ComponentIcons.alignment(true, h.name()));
        for (final GridBagAlignment.Vertical v : GridBagAlignment.Vertical.values()) icons.add(ComponentIcons.alignment(false, v.name()));
        icons.add(ComponentIcons.grow(true));
        icons.add(ComponentIcons.grow(false));
        for (final List<PaletteEntry> entries : Palette.categories().values()) {
            for (final PaletteEntry entry : entries) icons.add(ComponentIcons.forEntry(entry));
        }
        final int scale = 3, cell = 20 * scale;
        final BufferedImage image = new BufferedImage(cell * 10, cell * ((icons.size() + 9) / 10), BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.scale(scale, scale);
        for (int i = 0; i < icons.size(); i++) {
            icons.get(i).paintIcon(null, g, (i % 10) * 20 + 2, (i / 10) * 20 + 2);
        }
        g.dispose();
        final String dir = System.getProperty("windowbuilder.snapshots");
        if (dir != null) javax.imageio.ImageIO.write(image, "png", new java.io.File(dir, "icons.png"));
    }
}
