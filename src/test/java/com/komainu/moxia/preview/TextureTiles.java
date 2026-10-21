package com.komainu.moxia.preview;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Las texturas de bloque de los renders de comprobación, en píxeles ARGB de 16×16: del jar de cliente si
 * {@code KOMAEDIT_MC_JAR} lo indica y, si no, un color liso con un poco de grano para que se lean las caras. Las
 * comparten {@link SoftRaster} (vista previa) y el rasterizador en perspectiva del gizmo.
 */
public final class TextureTiles implements AutoCloseable {
    private final ZipFile jar;
    private final Map<String, int[]> tiles = new HashMap<>();

    public TextureTiles() throws IOException {
        String path = System.getenv("KOMAEDIT_MC_JAR");
        jar = path == null || path.isBlank() ? null : new ZipFile(Path.of(path).toFile());
    }

    /** Si las texturas son las del juego. */
    public boolean real() {
        return jar != null;
    }

    /** {@code assets/minecraft/textures/block/<name>.png} multiplicada por {@code tint}, o {@code fallback} liso. */
    public int[] tile(String name, int fallback, int tint) {
        return tiles.computeIfAbsent(name + "#" + Integer.toHexString(tint), key -> {
            int[] pixels = new int[256];
            BufferedImage image = jar == null ? null : load("assets/minecraft/textures/block/" + name + ".png");
            for (int index = 0; index < 256; index++) {
                int argb;
                if (image == null) {
                    int noise = (int) ((index * 73 + 17) * 2654435761L >>> 28 & 7) - 3;
                    argb = 0xFF000000 | add(fallback, noise * 4);
                } else {
                    argb = image.getRGB(index & 15, (index >> 4) % image.getHeight());
                }
                pixels[index] = multiply(argb, tint);
            }
            return pixels;
        });
    }

    private BufferedImage load(String entryName) {
        try {
            ZipEntry entry = jar.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            try (InputStream input = jar.getInputStream(entry)) {
                return ImageIO.read(input);
            }
        } catch (IOException exception) {
            return null;
        }
    }

    /** Cada canal de {@code argb} por el de {@code rgb}; el alfa se queda. */
    public static int multiply(int argb, int rgb) {
        int red = (argb >> 16 & 0xFF) * (rgb >> 16 & 0xFF) / 255;
        int green = (argb >> 8 & 0xFF) * (rgb >> 8 & 0xFF) / 255;
        int blue = (argb & 0xFF) * (rgb & 0xFF) / 255;
        return argb & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int add(int rgb, int amount) {
        int red = Math.clamp((rgb >> 16 & 0xFF) + amount, 0, 255);
        int green = Math.clamp((rgb >> 8 & 0xFF) + amount, 0, 255);
        int blue = Math.clamp((rgb & 0xFF) + amount, 0, 255);
        return red << 16 | green << 8 | blue;
    }

    @Override
    public void close() throws IOException {
        if (jar != null) {
            jar.close();
        }
    }
}
