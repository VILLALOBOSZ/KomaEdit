package com.komainu.moxia.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders de comprobación fuera del juego. Solo se ejecuta con {@code KOMAEDIT_IMAGE_RENDER=<carpeta>}: convierte
 * un atardecer generado por código (con las esquinas transparentes) en vidriera y en hormigón, y un icono en lana,
 * y dibuja cada bloque con su color medio de textura. Los huecos transparentes salen como un damero.
 */
@EnabledIfEnvironmentVariable(named = "KOMAEDIT_IMAGE_RENDER", matches = ".+")
class ImageRenderTest {
    private static final int SCALE = 5;
    private static final int WIDTH = 96;
    private static final int HEIGHT = 72;
    private static final int HEADER = 22;
    /** Lo que se ve detrás de una vidriera a contraluz: cielo claro. */
    private static final int SKY = 0xEAF2FF;

    @Test
    void rendersGlassAndConcrete() throws IOException {
        Path folder = Path.of(System.getenv("KOMAEDIT_IMAGE_RENDER"));
        Files.createDirectories(folder);
        byte[] scene = TestImages.encode(TestImages.scene(320, 240), "png");
        byte[] icon = TestImages.encode(TestImages.icon(23), "png");
        Files.write(folder.resolve("prueba_atardecer.png"), scene);

        List<BufferedImage> tiles = new ArrayList<>();
        ImageConverter.Converted original = convert(scene, ImagePalettes.Preset.GLASS, Dithering.NONE);
        tiles.add(panel(original.scaled(), null, null, Style.ORIGINAL, "Original reducido a " + original.size()
                + " (de 320×240)", 0));
        Object[][] rows = {
                {ImagePalettes.Preset.GLASS, Dithering.FLOYD_STEINBERG, Style.LEAD, "vidrio_fs"},
                {ImagePalettes.Preset.GLASS, Dithering.FLOYD_STEINBERG, Style.BACKLIT, "vidrio_contraluz"},
                {ImagePalettes.Preset.GLASS, Dithering.NONE, Style.LEAD, "vidrio_sin_tramado"},
                {ImagePalettes.Preset.CONCRETE, Dithering.FLOYD_STEINBERG, Style.FLAT, "hormigon_fs"},
                {ImagePalettes.Preset.CONCRETE, Dithering.BAYER, Style.FLAT, "hormigon_bayer"},
                {ImagePalettes.Preset.SOLID, Dithering.FLOYD_STEINBERG, Style.FLAT, "solidos_fs"}
        };
        for (Object[] row : rows) {
            ImagePalettes.Preset preset = (ImagePalettes.Preset) row[0];
            Dithering dithering = (Dithering) row[1];
            Style style = (Style) row[2];
            long start = System.nanoTime();
            ImageConverter.Converted converted = convert(scene, preset, dithering);
            long millis = (System.nanoTime() - start) / 1_000_000;
            BlockPalette palette = ImagePalettes.preset(preset);
            String text = String.format(Locale.ROOT, "%s · %s%s · %d bloques · ΔE %.1f · %d ms",
                    capitalized(preset.displayName()), dithering.displayName(),
                    style == Style.BACKLIT ? " · a contraluz" : "", converted.blocks().distinct(),
                    converted.blocks().meanError(), millis);
            BufferedImage tile = panel(converted.scaled(), converted.blocks(), palette, style, text, 0);
            tiles.add(tile);
            ImageIO.write(tile, "png", folder.resolve("imagen_" + row[3] + ".png").toFile());
        }
        ImageConverter.Converted wool = ImageConverter.convert(icon, 10_000,
                header -> ImageSize.of(header.width(), header.height(), null, null, 128, 512), 0, 0,
                ImagePalettes.preset(ImagePalettes.Preset.WOOL), Dithering.NONE, 128, () -> false);
        BufferedImage woolTile = panel(wool.scaled(), wool.blocks(), ImagePalettes.preset(ImagePalettes.Preset.WOOL),
                Style.FLAT, "Icono de 23×23 en lana, sin tramado · " + wool.blocks().distinct() + " bloques", 3);
        tiles.add(woolTile);
        ImageIO.write(woolTile, "png", folder.resolve("imagen_icono_lana.png").toFile());
        ImageIO.write(sheet(tiles, 2), "png", folder.resolve("imagenes.png").toFile());
        Files.writeString(folder.resolve("tiempos_imagen.txt"), timings());
    }

    /** Tiempo del cálculo entero (decodificar, reescalar y elegir bloques) de una foto de 2048×1536. */
    private static String timings() {
        byte[] photo = TestImages.encode(TestImages.scene(2048, 1536), "png");
        StringBuilder text = new StringBuilder("Foto de 2048×1536 (PNG de " + photo.length / 1024 + " KiB)%n".formatted());
        for (int width : new int[]{128, 256, 512}) {
            for (ImagePalettes.Preset preset : List.of(ImagePalettes.Preset.GLASS, ImagePalettes.Preset.CONCRETE,
                    ImagePalettes.Preset.SOLID)) {
                for (Dithering dithering : Dithering.values()) {
                    ImageConverter.convert(photo, 4_000_000, header -> ImageSize.of(header.width(), header.height(),
                            width, null, 128, 512), 0, 0, ImagePalettes.preset(preset), dithering, 128, () -> false);
                    long start = System.nanoTime();
                    ImageConverter.Converted converted = ImageConverter.convert(photo, 4_000_000,
                            header -> ImageSize.of(header.width(), header.height(), width, null, 128, 512), 0, 0,
                            ImagePalettes.preset(preset), dithering, 128, () -> false);
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    text.append(String.format(Locale.ROOT, "%s · %-10s · %-16s · %5d ms · ΔE %.1f%n",
                            converted.size(), preset.id(), dithering.displayName(), millis,
                            converted.blocks().meanError()));
                }
            }
        }
        return text.toString();
    }

    private static ImageConverter.Converted convert(byte[] data, ImagePalettes.Preset preset, Dithering dithering) {
        return ImageConverter.convert(data, 1_000_000,
                header -> ImageSize.of(header.width(), header.height(), WIDTH, null, 128, 512), 0, 0,
                ImagePalettes.preset(preset), dithering, 128, () -> false);
    }

    private enum Style {
        ORIGINAL,
        FLAT,
        /** Vidrio con su color de bloque y una junta oscura, como el plomo de una vidriera. */
        LEAD,
        /** Lo que se ve a través del vidrio con cielo claro detrás (alfa medio 0,46). */
        BACKLIT
    }

    /** @param zoom bloques más grandes para imágenes pequeñas (0 = escala normal) */
    private static BufferedImage panel(RgbaImage scaled, ImageQuantizer.Result blocks, BlockPalette palette,
                                       Style style, String text, int zoom) {
        int scale = zoom > 0 ? SCALE * zoom : SCALE;
        int width = WIDTH * SCALE + 20;
        int height = HEIGHT * SCALE + HEADER + 10;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(0xF4F4F4));
        graphics.fillRect(0, 0, width, height);
        int offsetX = (width - scaled.width() * scale) / 2;
        int offsetY = HEADER + (height - HEADER - scaled.height() * scale) / 2;
        for (int y = 0; y < scaled.height(); y++) {
            for (int x = 0; x < scaled.width(); x++) {
                int left = offsetX + x * scale;
                int top = offsetY + y * scale;
                int color;
                boolean empty;
                if (blocks == null) {
                    int pixel = scaled.get(x, y);
                    empty = RgbaImage.alpha(pixel) < 128;
                    color = RgbaImage.rgb(pixel);
                } else {
                    int index = blocks.index(x, y);
                    empty = index < 0;
                    color = empty ? 0 : palette.rgb(index);
                }
                if (empty) {
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            image.setRGB(left + dx, top + dy, ((left + dx) / 4 + (top + dy) / 4) % 2 == 0
                                    ? 0xFFFFFF : 0xD6D6D6);
                        }
                    }
                    continue;
                }
                if (style == Style.BACKLIT) {
                    color = over(color, SKY, 0.46);
                }
                graphics.setColor(new Color(color));
                graphics.fillRect(left, top, scale, scale);
                if (style == Style.LEAD) {
                    graphics.setColor(new Color(0x2A2A2E));
                    graphics.drawLine(left + scale - 1, top, left + scale - 1, top + scale - 1);
                    graphics.drawLine(left, top + scale - 1, left + scale - 1, top + scale - 1);
                }
            }
        }
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        graphics.drawString(text, 10, 16);
        graphics.dispose();
        return image;
    }

    private static int over(int tint, int background, double alpha) {
        int red = (int) Math.round((tint >> 16 & 0xFF) * alpha + (background >> 16 & 0xFF) * (1 - alpha));
        int green = (int) Math.round((tint >> 8 & 0xFF) * alpha + (background >> 8 & 0xFF) * (1 - alpha));
        int blue = (int) Math.round((tint & 0xFF) * alpha + (background & 0xFF) * (1 - alpha));
        return red << 16 | green << 8 | blue;
    }

    private static BufferedImage sheet(List<BufferedImage> tiles, int columns) {
        int width = tiles.getFirst().getWidth();
        int height = tiles.getFirst().getHeight();
        int rows = (tiles.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(width * columns, height * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int index = 0; index < tiles.size(); index++) {
            graphics.drawImage(tiles.get(index), (index % columns) * width, (index / columns) * height, null);
        }
        graphics.dispose();
        return sheet;
    }

    private static String capitalized(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
