package com.komainu.moxia.terrain.stamp;

import com.komainu.moxia.terrain.StampLibrary;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Hoja de contacto de los sellos de serie: una fila por categoría con su nombre y cada sello con la miniatura del
 * Studio y su nombre. Para revisarlos a ojo y para la documentación.
 */
public final class StampSheet {
    private static final Color BACKGROUND = new Color(0x1C2026);
    private static final Color TITLE = new Color(0xF2C14B);
    private static final Color NAME = new Color(0xE6E8EB);
    private static final Color DETAIL = new Color(0x98A2AD);

    private StampSheet() {
    }

    /** La hoja entera: {@code columns} miniaturas de {@code thumb} píxeles por fila como mucho. */
    public static BufferedImage render(int thumb, int columns) {
        int gap = 10;
        int label = 30;
        int header = 26;
        int height = gap;
        for (StampCategory category : StampCategory.values()) {
            int count = StampLibrary.of(category).size();
            int rows = (count + columns - 1) / columns;
            height += header + rows * (thumb + label + gap);
        }
        int width = gap + columns * (thumb + gap);
        BufferedImage sheet = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, width, height);
        Font titleFont = new Font(Font.SANS_SERIF, Font.BOLD, 15);
        Font nameFont = new Font(Font.SANS_SERIF, Font.BOLD, 12);
        Font detailFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
        int y = gap;
        for (StampCategory category : StampCategory.values()) {
            List<StampInfo> stamps = StampLibrary.of(category);
            graphics.setFont(titleFont);
            graphics.setColor(TITLE);
            graphics.drawString(category.displayName() + "  (" + stamps.size() + ", paleta " + category.palette()
                    + ")", gap, y + 17);
            y += header;
            for (int index = 0; index < stamps.size(); index++) {
                StampInfo info = stamps.get(index);
                int x = gap + (index % columns) * (thumb + gap);
                int top = y + (index / columns) * (thumb + label + gap);
                int[] pixels = StampLibrary.thumbnail(info.name(), thumb);
                BufferedImage image = new BufferedImage(thumb, thumb, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, thumb, thumb, pixels, 0, thumb);
                graphics.drawImage(image, x, top, null);
                graphics.setFont(nameFont);
                graphics.setColor(NAME);
                graphics.drawString(info.name(), x, top + thumb + 14);
                graphics.setFont(detailFont);
                graphics.setColor(DETAIL);
                String kind = info.neutral() >= 1.0 ? "hunde" : info.neutral() > 0.0 ? "sube y baja" : "levanta";
                graphics.drawString(kind + " · alto " + Math.round(info.heightRatio() * 100) + " % del radio", x,
                        top + thumb + 26);
            }
            y += ((stamps.size() + columns - 1) / columns) * (thumb + label + gap);
        }
        graphics.dispose();
        return sheet;
    }
}
