package com.komainu.moxia.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Perfil de {@code noisegen heightmap water,sand,grass_block,stone,snow -t} en una selección de 32 de alto: las
 * columnas bajan y suben para que haya un lago en medio. Solo con {@code KOMAEDIT_AUDIT_RENDER=<carpeta>}.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_AUDIT_RENDER", matches = ".+")
class AuditNoiseRenderDump {
    private static final int CELL = 8;

    @Test
    void lakesAreNotCovered() throws IOException {
        PaletteTraits palette = PaletteTraits.of(List.of("water", "sand", "grass_block", "stone", "snow"));
        Color[] colors = {new Color(0x3F6FD8), new Color(0xDCCB8A), new Color(0x6E9A4C), new Color(0x8A8F98),
                new Color(0xF2F4F7)};
        int columns = 60;
        BufferedImage image = new BufferedImage(columns * CELL + 20, 32 * CELL + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x2B2D31));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        Volume volume = Volume.box(0, 0, 0, columns - 1, 31, 0);
        for (int x = 0; x < columns; x++) {
            double t = Math.abs(x - columns / 2.0) / (columns / 2.0);
            double height = 1.5 + t * t * 14.0;
            GeneratedBlocks blocks = new GeneratedBlocks(64);
            NoiseGenerator.column(x, 0, 0, height, 32, true, palette, volume, false, -64, 319, blocks);
            for (int index = 0; index < blocks.size(); index++) {
                graphics.setColor(colors[blocks.material(index)]);
                graphics.fillRect(10 + x * CELL, 10 + (31 - blocks.y(index)) * CELL, CELL, CELL);
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        graphics.drawString("noisegen heightmap water,sand,grass_block,stone,snow -t (32 de alto)", 12,
                32 * CELL + 30);
        graphics.dispose();
        Path folder = Path.of(System.getenv("KOMAEDIT_AUDIT_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve("noisegen_lagos.png").toFile());
    }
}
