package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.brush.ez.BrushNoise;
import com.komainu.moxia.brush.paint.FootprintLibrary;
import com.komainu.moxia.brush.paint.PaintConfig;
import com.komainu.moxia.brush.paint.PaintFootprint;
import com.komainu.moxia.brush.paint.PaintPalettes;
import com.komainu.moxia.color.BlockColorCatalog;
import com.komainu.moxia.gradient.PaletteSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Miniaturas de la tarjeta «Pintar terreno»: la huella como mancha de luz (lo que pinta más, más claro, con la dureza
 * de serie) y la muestra del degradado de cada paleta, en celdas de bloque con los colores medidos de sus texturas y
 * la misma trama entre tonos que pone el pincel. Java puro, como {@link StampPreview}.
 */
public final class PaintPreview {
    private static final int BACKGROUND = 0x22262C;
    private static final int LIGHT = 0xC5E1A5;
    /** Los bloques que se tiñen con el bioma: su color medido es el de la textura gris. */
    private static final Map<String, Integer> TINTED = Map.of("minecraft:grass_block", 0x7FA843,
            "minecraft:moss_block", 0x596E2D);
    private static final long DITHER_SALT = 0x5357_4154L;

    private PaintPreview() {
    }

    /** La huella en {@code size}×{@code size} píxeles ARGB, por filas. */
    public static int[] footprint(String name, int size) {
        PaintFootprint footprint = FootprintLibrary.builtIn().get(name);
        double hardness = PaintConfig.DEFAULT_HARDNESS / 100.0;
        int[] pixels = new int[size * size];
        // Un poco de margen: la huella llena la miniatura sin tocar el borde.
        double scale = Math.max(1.0, footprint.extent()) * 1.08;
        for (int row = 0; row < size; row++) {
            double v = ((row + 0.5) / size * 2.0 - 1.0) * scale;
            for (int column = 0; column < size; column++) {
                double u = ((column + 0.5) / size * 2.0 - 1.0) * scale;
                double intensity = PaintFootprint.intensity(footprint.shape(u, v), hardness);
                pixels[row * size + column] = 0xFF000000 | mix(BACKGROUND, LIGHT, intensity);
            }
        }
        return pixels;
    }

    /**
     * La muestra de la paleta: celdas de bloque de izquierda (primer tono) a derecha (último), tramadas entre tonos
     * vecinos. La paleta escrita a mano ({@code propia}) sale en grises, de oscuro a claro.
     */
    public static int[] palette(String name, int size) {
        String blocks = PaintPalettes.blocks(name);
        List<Integer> colors = new ArrayList<>();
        if (blocks == null) {
            for (int step = 0; step < 5; step++) {
                int gray = 0x40 + step * 0x28;
                colors.add(gray << 16 | gray << 8 | gray);
            }
        } else {
            for (String entry : PaletteSpec.parse(blocks)) {
                colors.add(color(entry));
            }
        }
        int cells = 12;
        int cell = Math.max(1, size / cells);
        int[] pixels = new int[size * size];
        int last = colors.size() - 1;
        for (int row = 0; row < size; row++) {
            int cellRow = Math.min(cells - 1, row / cell);
            for (int column = 0; column < size; column++) {
                int cellColumn = Math.min(cells - 1, column / cell);
                double t = cellColumn / (cells - 1.0) * last;
                int index = Math.clamp((int) Math.floor(t + BrushNoise.unit(cellColumn, cellRow, 0, DITHER_SALT)),
                        0, last);
                int base = colors.get(index);
                // Un poco de grano dentro de cada celda, como la textura de un bloque.
                double grain = 0.9 + 0.2 * BrushNoise.unit(column, row, 1, DITHER_SALT);
                boolean edge = column % cell == 0 || row % cell == 0;
                pixels[row * size + column] = 0xFF000000 | scale(base, edge ? grain * 0.88 : grain);
            }
        }
        return pixels;
    }

    /** Color de la cara de arriba de un bloque ({@code grass_block}, {@code minecraft:stone[...]}…). */
    static int color(String entry) {
        String id = entry.strip();
        int bracket = id.indexOf('[');
        id = bracket > 0 ? id.substring(0, bracket) : id;
        id = id.contains(":") ? id : "minecraft:" + id;
        Integer tinted = TINTED.get(id);
        if (tinted != null) {
            return tinted;
        }
        BlockColorCatalog.Measured measured = BlockColorCatalog.measured().get(id);
        return measured == null ? 0x8A8A8A : measured.rgb() & 0xFFFFFF;
    }

    private static int mix(int a, int b, double t) {
        double clamped = Math.clamp(t, 0.0, 1.0);
        int red = (int) Math.round(((a >> 16) & 0xFF) * (1 - clamped) + ((b >> 16) & 0xFF) * clamped);
        int green = (int) Math.round(((a >> 8) & 0xFF) * (1 - clamped) + ((b >> 8) & 0xFF) * clamped);
        int blue = (int) Math.round((a & 0xFF) * (1 - clamped) + (b & 0xFF) * clamped);
        return red << 16 | green << 8 | blue;
    }

    private static int scale(int rgb, double factor) {
        int red = Math.clamp((int) Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
        int green = Math.clamp((int) Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
        int blue = Math.clamp((int) Math.round((rgb & 0xFF) * factor), 0, 255);
        return red << 16 | green << 8 | blue;
    }
}
