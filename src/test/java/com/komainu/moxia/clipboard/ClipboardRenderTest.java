package com.komainu.moxia.clipboard;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.PieceRender;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders del escalado y el giro del portapapeles, fuera del juego. Solo se ejecuta con
 * {@code KOMAEDIT_CLIPBOARD_RENDER=<carpeta>}; en el CI no hace nada.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_CLIPBOARD_RENDER", matches = ".+")
class ClipboardRenderTest {
    private static final long LIMIT = 20_000_000L;
    private static final Runnable NO_CANCEL = () -> {
    };
    private static final int[] COLORS = {0, 0x9C9C9C, 0x7A5230, 0xC9A26B, 0x5B8C3A, 0x4A6FA5, 0xB04A3A};

    @Test
    void renderScaling() throws IOException {
        VoxelVolume statue = statue();
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        images.add(PieceRender.render(cells(statue), 9));
        labels.add("original (" + statue.blocks() + " bloques)");
        for (ClipboardTransform.Mode mode : ClipboardTransform.Mode.values()) {
            double factor = mode == ClipboardTransform.Mode.SCALE3X ? 3.0 : 2.0;
            VoxelVolume scaled = ClipboardTransform.scale(statue, factor, mode, LIMIT, NO_CANCEL);
            images.add(PieceRender.render(cells(scaled), 9 / (int) factor + 1));
            labels.add("scale " + (int) factor + " " + mode.id() + " (" + scaled.blocks() + ")");
        }
        VoxelVolume odd = ClipboardTransform.scale(statue, 1.5, ClipboardTransform.Mode.ROTSPRITE, LIMIT, NO_CANCEL);
        images.add(PieceRender.render(cells(odd), 6));
        labels.add("scale 1.5 rotsprite (" + odd.blocks() + ")");
        write(PieceRender.sheet(images, labels, 3), "portapapeles_escalar.png");

        // Pixel art plano (una capa): donde mejor se ve Scale2x / Scale3x.
        VoxelVolume sprite = sprite();
        List<BufferedImage> flat = new ArrayList<>();
        List<String> flatLabels = new ArrayList<>();
        flat.add(top(sprite, 24));
        flatLabels.add("original");
        flat.add(top(ClipboardTransform.scale(sprite, 3.0, ClipboardTransform.Mode.NEAREST, LIMIT, NO_CANCEL), 8));
        flatLabels.add("×3 nearest");
        flat.add(top(ClipboardTransform.scale(sprite, 3.0, ClipboardTransform.Mode.SCALE3X, LIMIT, NO_CANCEL), 8));
        flatLabels.add("×3 scale3x");
        flat.add(top(ClipboardTransform.scale(sprite, 4.0, ClipboardTransform.Mode.SCALE2X, LIMIT, NO_CANCEL), 6));
        flatLabels.add("×4 scale2x (dos pasadas)");
        flat.add(top(ClipboardTransform.scale(sprite, 3.0, ClipboardTransform.Mode.ROTSPRITE, LIMIT, NO_CANCEL), 8));
        flatLabels.add("×3 rotsprite");
        write(PieceRender.sheet(flat, flatLabels, 5), "portapapeles_pixelart.png");
    }

    @Test
    void renderRotation() throws IOException {
        VoxelVolume plan = floorPlan();
        List<BufferedImage> images = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        images.add(top(plan, 12));
        labels.add("original");
        for (double angle : new double[]{30.0, 45.0}) {
            images.add(top(ClipboardTransform.rotate(plan, angle, ClipboardTransform.Mode.NEAREST, LIMIT,
                    NO_CANCEL), 12));
            labels.add(angle + "° nearest");
            images.add(top(ClipboardTransform.rotate(plan, angle, ClipboardTransform.Mode.ROTSPRITE, LIMIT,
                    NO_CANCEL), 12));
            labels.add(angle + "° rotsprite");
        }
        write(PieceRender.sheet(images, labels, 5), "portapapeles_girar_planta.png");

        VoxelVolume tower = tower();
        List<BufferedImage> iso = new ArrayList<>();
        List<String> isoLabels = new ArrayList<>();
        iso.add(PieceRender.render(cells(tower), 8));
        isoLabels.add("original");
        iso.add(PieceRender.render(cells(ClipboardTransform.rotate(tower, 30.0, ClipboardTransform.Mode.NEAREST,
                LIMIT, NO_CANCEL)), 8));
        isoLabels.add("30° nearest");
        iso.add(PieceRender.render(cells(ClipboardTransform.rotate(tower, 30.0, ClipboardTransform.Mode.ROTSPRITE,
                LIMIT, NO_CANCEL)), 8));
        isoLabels.add("30° rotsprite");
        write(PieceRender.sheet(iso, isoLabels, 3), "portapapeles_girar_torre.png");
    }

    /** Una figurilla: pedestal, cuerpo redondo y cabeza, con varios materiales. */
    static VoxelVolume statue() {
        VoxelVolume volume = new VoxelVolume(-5, 0, -5, 11, 14, 11);
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                for (int y = 0; y < 14; y++) {
                    double r = Math.sqrt(x * x + z * z);
                    int id = 0;
                    if (y < 2 && Math.max(Math.abs(x), Math.abs(z)) <= 4) {
                        id = 1;
                    } else if (y >= 2 && y < 8 && r <= 3.6 - (y - 2) * 0.25) {
                        id = 6;
                    } else if (y >= 8 && y < 13 && x * x + (y - 10) * (y - 10) + z * z <= 6.5) {
                        id = 3;
                    }
                    if (id != 0) {
                        volume.set(x, y, z, id);
                    }
                }
            }
        }
        return volume;
    }

    /** Un sprite de una capa: un corazón con borde. */
    static VoxelVolume sprite() {
        String[] rows = {
                "..222...222..",
                ".26662.26662.",
                "2666662666662",
                "2666666666662",
                "2666666666662",
                ".26666666662.",
                "..266666662..",
                "...2666662...",
                "....26662....",
                ".....262.....",
                "......2......"};
        VoxelVolume volume = new VoxelVolume(0, 0, 0, rows[0].length(), 1, rows.length);
        for (int z = 0; z < rows.length; z++) {
            for (int x = 0; x < rows[z].length(); x++) {
                char c = rows[z].charAt(x);
                if (c != '.') {
                    volume.set(x, 0, z, c - '0');
                }
            }
        }
        return volume;
    }

    /** Planta de una casa: muros, una puerta y un camino. */
    static VoxelVolume floorPlan() {
        VoxelVolume volume = new VoxelVolume(-10, 0, -7, 21, 1, 15);
        for (int x = -8; x <= 8; x++) {
            for (int z = -5; z <= 5; z++) {
                boolean wall = Math.abs(x) == 8 || Math.abs(z) == 5;
                if (wall && !(z == 5 && Math.abs(x) <= 1)) {
                    volume.set(x, 0, z, 2);
                } else if (!wall) {
                    volume.set(x, 0, z, 3);
                }
            }
        }
        for (int z = 6; z <= 7; z++) {
            for (int x = -1; x <= 1; x++) {
                volume.set(x, 0, z, 1);
            }
        }
        return volume;
    }

    /** Torre de base cuadrada con almenas. */
    static VoxelVolume tower() {
        VoxelVolume volume = new VoxelVolume(-5, 0, -5, 11, 12, 11);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean edge = Math.abs(x) == 4 || Math.abs(z) == 4;
                for (int y = 0; y < 12; y++) {
                    if (y < 10 && edge || y == 0) {
                        volume.set(x, y, z, 1);
                    } else if (y >= 10 && edge && (x + z) % 2 == 0) {
                        volume.set(x, y, z, 1);
                    }
                }
            }
        }
        return volume;
    }

    /** Vista desde arriba: el bloque más alto de cada columna. */
    static BufferedImage top(VoxelVolume volume, int scale) {
        BufferedImage image = new BufferedImage(volume.sizeX() * scale + 8, volume.sizeZ() * scale + 8,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(0xE9EEF2));
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int x = 0; x < volume.sizeX(); x++) {
            for (int z = 0; z < volume.sizeZ(); z++) {
                for (int y = volume.sizeY() - 1; y >= 0; y--) {
                    int id = volume.get(volume.minimumX() + x, volume.minimumY() + y, volume.minimumZ() + z);
                    if (id != VoxelVolume.AIR) {
                        graphics.setColor(new Color(COLORS[Math.min(id, COLORS.length - 1)]));
                        graphics.fillRect(4 + x * scale, 4 + z * scale, scale, scale);
                        graphics.setColor(new Color(0, 0, 0, 40));
                        graphics.drawRect(4 + x * scale, 4 + z * scale, scale - 1, scale - 1);
                        break;
                    }
                }
            }
        }
        graphics.dispose();
        return image;
    }

    static Long2ObjectOpenHashMap<PieceRender.Cell> cells(VoxelVolume volume) {
        Long2ObjectOpenHashMap<PieceRender.Cell> cells = new Long2ObjectOpenHashMap<>();
        volume.forEach((x, y, z, id) -> cells.put(PackedBlockPlan.pack(x, y, z),
                new PieceRender.Cell(SmoothPiece.FULL, COLORS[Math.min(id, COLORS.length - 1)])));
        return cells;
    }

    static void write(BufferedImage image, String name) throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_CLIPBOARD_RENDER"));
        Files.createDirectories(folder);
        ImageIO.write(image, "png", folder.resolve(name).toFile());
    }
}
