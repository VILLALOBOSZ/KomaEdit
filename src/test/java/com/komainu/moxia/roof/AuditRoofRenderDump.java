package com.komainu.moxia.roof;

import com.komainu.moxia.edit.PackedBlockPlan;
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
 * Perfil de cono, agujas y cúpula con {@code -h 12} sobre una torre de 8 × 8: la línea roja es la altura pedida. Solo
 * con {@code KOMAEDIT_AUDIT_RENDER=<carpeta>} (sirve para comparar ramas).
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_AUDIT_RENDER", matches = ".+")
class AuditRoofRenderDump {
    private static final int CELL = 10;
    private static final int BASE = 10;

    @Test
    void conesAndSpiresWithHeight() throws IOException {
        List<String> flags = List.of("cone -h 12", "spire -lados 8 -h 12", "spire -lados 4 -h 12", "dome -h 12");
        Footprint tower = Footprint.rectangle(0, 0, 7, 7, BASE);
        int width = 14 * CELL;
        int height = 22 * CELL;
        BufferedImage image = new BufferedImage(flags.size() * (width + 10) + 10, height + 40,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x1E1F22));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int index = 0; index < flags.size(); index++) {
            RoofResult result = RoofGenerator.generate(tower, RoofSettings.parse(flags.get(index)), RoofWorld.AIR);
            int left = 10 + index * (width + 10);
            int top = 10;
            graphics.setColor(new Color(0x2B2D31));
            graphics.fillRect(left, top, width, height);
            graphics.setColor(new Color(0x8A8F98));
            for (int y = BASE - 4; y < BASE; y++) {
                for (int x = 0; x <= 7; x++) {
                    graphics.fillRect(left + (x + 3) * CELL, top + (BASE + 17 - y) * CELL, CELL - 1, CELL - 1);
                }
            }
            graphics.setColor(new Color(0xB5653C));
            for (long key : result.positions()) {
                int x = PackedBlockPlan.unpackX(key);
                int y = PackedBlockPlan.unpackY(key);
                graphics.fillRect(left + (x + 3) * CELL, top + (BASE + 17 - y) * CELL, CELL - 1, CELL - 1);
            }
            graphics.setColor(new Color(0xE0605A));
            int target = top + (BASE + 17 - (BASE + 12)) * CELL;
            graphics.drawLine(left, target, left + width, target);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            graphics.drawString(flags.get(index) + " → " + (result.roofMaxY() - BASE), left, height + 30);
        }
        graphics.dispose();
        Path folder = Path.of(System.getenv("KOMAEDIT_AUDIT_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve("tejados_altura.png").toFile());
    }
}
