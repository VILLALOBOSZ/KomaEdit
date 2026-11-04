package com.komainu.moxia.terrain;

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

/**
 * Corte por el centro de un cráter y de un cañón en un mundo plano con bedrock en −64, y de un aplanado al pie de un
 * cortado más alto que la caja leída. Solo con {@code KOMAEDIT_AUDIT_RENDER=<carpeta>}.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_AUDIT_RENDER", matches = ".+")
class AuditTerrainRenderDump {
    private static final int CELL = 6;

    @Test
    void bedrockAndTallCliffs() throws IOException {
        FakeTerrain crater = flat();
        TerrainJobs.stamp(crater, new StampBrush.Settings(StampLibrary.get("crater"), StampBrush.Mode.RAISE, 24, 12,
                1.0, 0.0, -61), 32, 32, SurfacePalette.natural(), crater, FakeTerrain.NO_CANCEL);
        FakeTerrain canyon = flat();
        TerrainJobs.river(canyon, TerrainScenes.curve(new double[][]{{2, 0, 32}, {62, 0, 32}}),
                RiverPlanner.preset("canon"), SurfacePalette.natural(), canyon, FakeTerrain.NO_CANCEL);
        FakeTerrain cliff = FakeTerrain.columns(64, 40, 64, (x, z) -> x >= 40 ? 80 : 10);
        TerrainJobs.stamp(cliff, new StampBrush.Settings(StampLibrary.get("mesa"), StampBrush.Mode.FLATTEN, 30, 0,
                1.0, 0.0, 10), 40, 32, SurfacePalette.natural(), cliff, FakeTerrain.NO_CANCEL);
        FakeTerrain[] scenes = {crater, canyon, cliff};
        String[] names = {"cráter, z = 32", "cañón, x = 32 (corte transversal)",
                "aplanar al pie de un cortado más alto que la caja"};
        BufferedImage image = new BufferedImage(64 * CELL + 20, 3 * (40 * CELL + 30) + 10,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0x1E1F22));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int index = 0; index < scenes.length; index++) {
            FakeTerrain terrain = scenes[index];
            int top = 10 + index * (40 * CELL + 30);
            for (int u = 0; u < 64; u++) {
                for (int y = terrain.minimumY(); y <= terrain.maximumY(); y++) {
                    String id = index == 1 ? terrain.blockId(32, y, u) : terrain.blockId(u, y, 32);
                    graphics.setColor(color(id));
                    graphics.fillRect(10 + u * CELL, top + (terrain.maximumY() - y) * CELL, CELL, CELL);
                }
            }
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            graphics.drawString(names[index], 12, top + 14);
        }
        graphics.dispose();
        Path folder = Path.of(System.getenv("KOMAEDIT_AUDIT_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve("terreno_bedrock_cortado.png").toFile());
    }

    private static FakeTerrain flat() {
        FakeTerrain terrain = new FakeTerrain(0, -64, 0, 64, 40, 64);
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                terrain.put(x, -64, z, "minecraft:bedrock");
                terrain.put(x, -63, z, "minecraft:dirt");
                terrain.put(x, -62, z, "minecraft:dirt");
                terrain.put(x, -61, z, "minecraft:grass_block");
            }
        }
        return terrain;
    }

    private static Color color(String id) {
        return switch (id) {
            case "minecraft:air" -> new Color(0x2B2D31);
            case "minecraft:bedrock" -> new Color(0x111111);
            case "minecraft:grass_block" -> new Color(0x6E9A4C);
            case "minecraft:dirt", "minecraft:coarse_dirt" -> new Color(0x8B6A47);
            case "minecraft:water" -> new Color(0x3F6FD8);
            case "minecraft:gravel" -> new Color(0x9A9490);
            case "minecraft:sand" -> new Color(0xDCCB8A);
            default -> new Color(0x8A8F98);
        };
    }
}
