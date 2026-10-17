package com.komainu.moxia.glass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Colores del cristal tintado y de los bloques de fondo, y el espacio de color OKLab para compararlos.
 *
 * <p>Los colores del cristal son la media de las texturas de Minecraft 1.21.1 (un tinte uniforme con alfa medio
 * 0,46); el cristal sin tinte se trata como transparente, así una combinación puede usar menos capas. Los fondos son
 * la media de los bloques opacos con la misma textura en todas las caras.</p>
 */
public final class GlassColors {
    /** Alfa medio de las texturas de cristal tintado (102–163 sobre 255, sobre todo 102). */
    public static final double ALPHA = 0.46;
    public static final List<Swatch> GLASS = List.of(
            new Swatch("minecraft:glass", 0, 0, 0, 0.0),
            glass("white", 255, 255, 255),
            glass("orange", 216, 127, 51),
            glass("magenta", 178, 76, 216),
            glass("light_blue", 102, 153, 216),
            glass("yellow", 229, 229, 51),
            glass("lime", 127, 204, 25),
            glass("pink", 242, 127, 165),
            glass("gray", 76, 76, 76),
            glass("light_gray", 153, 153, 153),
            glass("cyan", 76, 127, 153),
            glass("purple", 127, 63, 178),
            glass("blue", 51, 76, 178),
            glass("brown", 102, 76, 51),
            glass("green", 102, 127, 51),
            glass("red", 153, 51, 51),
            glass("black", 25, 25, 25)
    );
    private static List<Swatch> backgrounds;

    private GlassColors() {
    }

    private static Swatch glass(String color, int red, int green, int blue) {
        return new Swatch("minecraft:" + color + "_stained_glass", red, green, blue, ALPHA);
    }

    /** Bloques opacos que pueden ir detrás del cristal ({@code -b}), leídos del recurso del mod. */
    public static synchronized List<Swatch> backgrounds() {
        if (backgrounds == null) {
            try (InputStream stream = GlassColors.class.getResourceAsStream("/assets/komaedit/glass/backgrounds.txt")) {
                backgrounds = stream == null ? List.of() : read(stream);
            } catch (IOException exception) {
                throw new IllegalStateException("No se pudo leer la tabla de colores de fondo", exception);
            }
        }
        return backgrounds;
    }

    static List<Swatch> read(InputStream stream) throws IOException {
        List<Swatch> result = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length == 4 && !parts[0].startsWith("#")) {
                result.add(new Swatch(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3]), 1.0));
            }
        }
        return List.copyOf(result);
    }

    /** {@code #RRGGBB}, {@code RRGGBB} o {@code #RGB} → {r, g, b} de 0 a 255. */
    public static double[] hex(String input) {
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("#")) {
            text = text.substring(1);
        } else if (text.startsWith("0x")) {
            text = text.substring(2);
        }
        if (text.length() == 3) {
            text = "" + text.charAt(0) + text.charAt(0) + text.charAt(1) + text.charAt(1) + text.charAt(2)
                    + text.charAt(2);
        }
        if (!text.matches("[0-9a-f]{6}")) {
            throw new IllegalArgumentException("«" + input + "» no es un color hexadecimal como #3A7FD2");
        }
        int value = Integer.parseInt(text, 16);
        return new double[]{(value >> 16) & 255, (value >> 8) & 255, value & 255};
    }

    public static boolean isHex(String input) {
        try {
            hex(input);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static String toHex(double[] rgb) {
        return String.format(Locale.ROOT, "#%02X%02X%02X", channel(rgb[0]), channel(rgb[1]), channel(rgb[2]));
    }

    private static int channel(double value) {
        return (int) Math.round(Math.clamp(value, 0.0, 255.0));
    }

    /** sRGB de 0 a 255 → OKLab (Ottosson, 2020). */
    public static double[] oklab(double[] rgb) {
        double r = linear(rgb[0] / 255.0);
        double g = linear(rgb[1] / 255.0);
        double b = linear(rgb[2] / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        };
    }

    /** OKLab → sRGB de 0 a 255 (sin recortar). */
    public static double[] rgb(double[] lab) {
        double l = lab[0] + 0.3963377774 * lab[1] + 0.2158037573 * lab[2];
        double m = lab[0] - 0.1055613458 * lab[1] - 0.0638541728 * lab[2];
        double s = lab[0] - 0.0894841775 * lab[1] - 1.2914855480 * lab[2];
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return new double[]{
                gamma(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s) * 255.0,
                gamma(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s) * 255.0,
                gamma(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s) * 255.0
        };
    }

    /** Distancia perceptual entre dos colores sRGB (OKLab euclídea; ~0,02 apenas se nota). */
    public static double distance(double[] first, double[] second) {
        double[] a = oklab(first);
        double[] b = oklab(second);
        double dl = a[0] - b[0];
        double da = a[1] - b[1];
        double db = a[2] - b[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static double gamma(double value) {
        double clamped = Math.clamp(value, 0.0, 1.0);
        return clamped <= 0.0031308 ? clamped * 12.92 : 1.055 * Math.pow(clamped, 1.0 / 2.4) - 0.055;
    }

    /** Un bloque con su color medio y su opacidad (1 = opaco, 0 = transparente). */
    public record Swatch(String blockId, double red, double green, double blue, double alpha) {
        public double[] rgb() {
            return new double[]{red, green, blue};
        }
    }
}
