package com.komainu.moxia.terrain.stamp;

import com.komainu.moxia.terrain.HeightStamp;

/**
 * Miniaturas de los sellos: el relieve pintado con los colores de su categoría (hierba, roca y nieve en una montaña;
 * arena en una duna; estratos en una mesa; agua alrededor de una isla), con luz del noroeste, sombra en lo cóncavo y
 * las proporciones reales del sello. Se leen de un vistazo en lugar de un gris plano.
 */
public final class StampThumbnails {
    private static final int WATER = 0x3A6FD8;
    private static final int[] STRATA = {0xB9622B, 0x985E43, 0xA1531D, 0xBA8524, 0x985E43, 0xD1B2A1, 0x8F3D2E};

    private StampThumbnails() {
    }

    /**
     * Colores de una categoría.
     *
     * @param ground el terreno sin tocar (el gris neutro)
     * @param hollow el fondo de lo que hunde
     * @param snow   altura relativa a partir de la que se pinta {@code peak} (más de 1: nunca)
     */
    private record Look(int ground, int low, int high, int peak, int rock, int hollow, double snow, boolean water,
                        boolean strata) {
    }

    private static Look look(StampCategory category) {
        return switch (category) {
            case MOUNTAINS, RANGES -> new Look(0x6FA34A, 0x6A9A46, 0x587F39, 0xF2F6F8, 0x8C8C8C, 0x4E7A34, 0.68, false,
                    false);
            case HILLS -> new Look(0x6FA34A, 0x74A84C, 0x86B256, 0x93BB5E, 0x80603E, 0x5A8A3C, 2.0, false, false);
            case PLATEAUS, CANYONS -> new Look(0xC87A3E, 0xB9622B, 0xC2703A, 0xB9622B, 0x985E43, 0xB9622B, 2.0, false,
                    true);
            case CLIFFS -> new Look(0x6FA34A, 0xD9C68A, 0x6A9A46, 0x6A9A46, 0x9A9A98, 0xD9C68A, 2.0, false, false);
            case CRATERS -> new Look(0x7E8A5C, 0x857E7B, 0x8C8C8C, 0x9A9A98, 0x6E6E63, 0x6B6762, 2.0, false, false);
            case VOLCANOES -> new Look(0x7A7A6E, 0x6A6870, 0x5C5862, 0x4A4550, 0x3E3944, 0x8E3B1B, 2.0, false,
                    false);
            case DUNES -> new Look(0xDCCB8E, 0xDFCE92, 0xE8D8A2, 0xEEDFAE, 0xC9B57E, 0xC9B57E, 2.0, false, false);
            case ISLANDS -> new Look(WATER, 0xDCCB8E, 0x6FA34A, 0x587F39, 0x8C8C8C, WATER, 2.0, true, false);
            case TERRACES -> new Look(0x6FA34A, 0x6FA34A, 0x7CAD50, 0x86B256, 0x7A7A7A, 0x4E7A34, 2.0, false, false);
            case GLACIAL -> new Look(0xE6EDF2, 0xDCE5EE, 0xF2F6F8, 0xFAFCFD, 0x8C8C8C, 0x9DBEF5, 2.0, false, false);
            case ROCKS -> new Look(0x6FA34A, 0x8C8C8C, 0x9A9A98, 0xA3A3A0, 0x707070, 0x5A8A3C, 2.0, false, false);
            case DETAIL -> new Look(0x7A9A4A, 0x8B6440, 0x86B256, 0x86B256, 0x80603E, 0x5E7A38, 2.0, false, false);
        };
    }

    /** Píxeles ARGB de {@code size}×{@code size}, por filas, de un sello de serie. */
    public static int[] argb(String name, int size) {
        StampInfo info = com.komainu.moxia.terrain.StampLibrary.info(name);
        HeightStamp stamp = com.komainu.moxia.terrain.StampLibrary.get(name);
        return info == null ? argb(stamp, StampCategory.DETAIL, 0.5, size)
                : argb(stamp, info.category(), info.heightRatio(), size);
    }

    /**
     * Píxeles ARGB de cualquier sello (también los PNG propios) con los colores de una categoría.
     *
     * @param heightRatio altura del sello dividida por su radio, para que las pendientes salgan como son
     */
    public static int[] argb(HeightStamp stamp, StampCategory category, double heightRatio, int size) {
        Look look = look(category);
        double[] relief = new double[(size + 2) * (size + 2)];
        int side = size + 2;
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                double u = (column - 0.5) / size;
                double v = (row - 0.5) / size;
                relief[row * side + column] = stamp.reliefAt(u, v);
            }
        }
        // Altura en píxeles de la miniatura, un poco exagerada para que el relieve bajo también se lea.
        double vertical = heightRatio * size / 2.0 * 1.6;
        int[] pixels = new int[size * size];
        double lx = -0.55;
        double ly = -0.55;
        double lz = 0.63;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int index = (row + 1) * side + column + 1;
                double value = relief[index];
                double east = relief[index + 1];
                double west = relief[index - 1];
                double south = relief[index + side];
                double north = relief[index - side];
                double gx = (east - west) * 0.5 * vertical;
                double gy = (south - north) * 0.5 * vertical;
                double length = Math.sqrt(gx * gx + gy * gy + 1.0);
                double light = Math.max(0.0, (-gx * lx - gy * ly + lz) / length);
                double slope = Math.sqrt(gx * gx + gy * gy);
                double cavity = (east + west + south + north - 4.0 * value) * vertical;
                double shade = Math.clamp(0.42 + 0.75 * light - Math.max(0.0, cavity) * 0.05, 0.3, 1.25);
                int color = color(look, value, slope, row, column);
                pixels[row * size + column] = 0xFF000000 | scale(color, shade);
            }
        }
        return pixels;
    }

    private static int color(Look look, double relief, double slope, int row, int column) {
        if (look.water && relief < 0.035) {
            // Más claro cerca de la orilla: se lee la costa.
            return mix(WATER, 0x5C8FE0, Math.clamp(relief / 0.035, 0.0, 1.0));
        }
        double steep = Math.clamp((slope - 0.7) / 0.9, 0.0, 0.85);
        int base;
        if (Math.abs(relief) < 0.01) {
            base = look.ground;
        } else if (relief < 0.0) {
            // Lo que hunde: el fondo más oscuro y las paredes de roca o estratos.
            double depth = -relief;
            base = look.strata ? STRATA[Math.floorMod((int) Math.floor(depth * 9.0), STRATA.length)]
                    : mix(look.ground, look.hollow, Math.min(1.0, depth * 1.2));
        } else if (relief >= look.snow) {
            base = look.peak;
        } else if (look.strata && steep > 0.2) {
            base = STRATA[Math.floorMod((int) Math.floor(relief * 9.0), STRATA.length)];
        } else {
            base = relief < 0.5 ? mix(look.low, look.high, relief / 0.5) : mix(look.high, look.peak,
                    Math.min(1.0, (relief - 0.5) / 0.5));
        }
        int rock = look.strata ? base : mix(base, look.rock, steep);
        // Un poco de grano para que no parezca plástico.
        double grain = ((row * 73 + column * 151) * 0x9E3779B9 >>> 27) / 32.0 - 0.5;
        return scale(rock, 1.0 + grain * 0.06);
    }

    private static int mix(int a, int b, double t) {
        int red = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int green = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int blue = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return red << 16 | green << 8 | blue;
    }

    private static int scale(int rgb, double factor) {
        int red = Math.clamp((int) Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
        int green = Math.clamp((int) Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
        int blue = Math.clamp((int) Math.round((rgb & 0xFF) * factor), 0, 255);
        return red << 16 | green << 8 | blue;
    }
}
