package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushNoise;
import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.noise.NoiseSpec;

import java.util.Locale;

import java.util.Set;

/**
 * Ajustes del pincel de pintura ({@code /komaedit pincel paint}, alias {@code pintar}), leídos sin Minecraft:
 * {@code [paleta] [radio] [huella|altura|pendiente|ruido] [fuerza] [-h huella] [-s semilla] [-k] …}. Todo tiene un
 * valor de serie bueno, así que {@code /komaedit pincel pintar} ya pinta hierba en lo llano y piedra en lo empinado.
 *
 * <p>Los posicionales van en cualquier orden: el primer número es el radio y el segundo la fuerza (en %), una
 * palabra de degradado es el modo y lo demás, la paleta.</p>
 *
 * @param palette    la paleta tal como se escribió: una de serie ({@link PaintPalettes}), {@code ##Nombre},
 *                   {@code #guardada} o una lista
 * @param strength   cuánto se acerca cada toque al tono que toca, de 0 a 1
 * @param footprint  nombre de la huella ({@link FootprintLibrary#normalize})
 * @param seed       semilla escrita, o {@code null} para una al azar
 * @param spacing    distancia entre toques al arrastrar, en radios
 * @param hardness   dureza del borde de la huella, de 0 (difuso) a 1 (cortado)
 * @param jitter     variación con ruido de la frontera entre tonos, de 0 a 1
 * @param layers     cuántos bloques hacia dentro desde el aire se pintan
 * @param surfaceOnly solo lo que toca el aire (hasta {@code layers}); si no, todo el volumen del pincel
 * @param shaped     también escaleras y losas, que conservan su forma
 * @param blocks     solo esos bloques ({@code -b}, lista), o {@code null}
 * @param mask       máscara de WorldEdit ({@code -m}), o {@code null}
 * @param everything cualquier bloque ({@code -x}); sin ella ni máscaras, solo el terreno natural y la paleta
 * @param heightLow  Y del primer tono por altura ({@code NaN}: se mide al empezar el trazo)
 * @param slopeLow   grados de pendiente del primer tono
 * @param noise      ruido de {@code -n}, o {@code null} para el de serie
 * @param noiseSize  tamaño de las manchas del ruido de serie, en bloques
 */
public record PaintConfig(String palette, int radius, PaintGradient gradient, double strength, String footprint,
                          Long seed, boolean seedLocked, double spacing, double hardness, double jitter, int layers,
                          boolean surfaceOnly, boolean shaped, String blocks, String mask, boolean everything,
                          double heightLow, double heightHigh, double slopeLow, double slopeHigh, String noise,
                          int noiseSize, boolean preview) {
    public static final int DEFAULT_RADIUS = 8;
    public static final int MAX_RADIUS = 32;
    public static final int DEFAULT_STRENGTH = 60;
    public static final PaintGradient DEFAULT_GRADIENT = PaintGradient.SLOPE;
    public static final int DEFAULT_SPACING = 30;
    public static final int DEFAULT_HARDNESS = 30;
    public static final int DEFAULT_JITTER = 15;
    /** Hasta 30° la hierba aguanta (una cuesta de un bloque por bloque son 45°); a partir de 65°, roca. */
    public static final int DEFAULT_SLOPE_LOW = 30;
    public static final int DEFAULT_SLOPE_HIGH = 65;
    public static final int DEFAULT_NOISE_SIZE = 12;
    /** Banderas sin valor: bloquear semilla, todo el volumen, solo cubos, cualquier bloque y vista previa. */
    public static final Set<Character> SWITCHES = Set.of('k', 't', 'c', 'x', 'p');
    /** Banderas con valor. */
    public static final Set<Character> VALUED = Set.of('h', 's', 'e', 'u', 'j', 'd', 'b', 'm', 'y', 'a', 'n', 'z');

    /** Las palabras del modo Natural: la superficie por reglas de una paleta de superficie, no un degradado. */
    public static final Set<String> NATURAL_WORDS = Set.of("natural", "superficie", "surface");

    public static PaintConfig defaults() {
        return parse(BrushOptions.parse("", SWITCHES, VALUED));
    }

    /** Si los posicionales piden el modo Natural ({@code pincel pintar natural 10 natural}). */
    public static boolean natural(BrushOptions options) {
        for (int index = 0; index < options.count(); index++) {
            if (NATURAL_WORDS.contains(BrushOptions.normalize(options.positional(index)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * El pincel del modo Natural como pincel de zona ({@code <radio> pintar <paleta> natural [-m máscara]}): el
     * primer número es el radio y la otra palabra, la paleta de superficie (de serie, {@code natural}).
     */
    public static String naturalZone(BrushOptions options) {
        int radius = DEFAULT_RADIUS;
        String palette = null;
        boolean numbered = false;
        for (int index = 0; index < options.count(); index++) {
            String token = options.positional(index);
            if (BrushOptions.isNumber(token) && !numbered) {
                radius = options.integer(index, DEFAULT_RADIUS, 1, MAX_RADIUS, "El radio");
                numbered = true;
            } else if (!NATURAL_WORDS.contains(BrushOptions.normalize(token)) && palette == null) {
                palette = token;
            } else if (!NATURAL_WORDS.contains(BrushOptions.normalize(token))) {
                throw new IllegalArgumentException("No entiendo «" + token + "»: la paleta ya es " + palette);
            }
        }
        StringBuilder command = new StringBuilder().append(radius).append(" pintar ")
                .append(palette == null ? com.komainu.moxia.terrain.SurfacePalette.DEFAULT : palette)
                .append(" natural");
        if (options.has('m')) {
            String mask = options.flag('m').strip();
            command.append(" -m ").append(mask.contains(" ") ? "\"" + mask + "\"" : mask);
        }
        return command.toString();
    }

    public static PaintConfig parse(BrushOptions options) {
        return parse(options, false);
    }

    /**
     * @param region sobre la selección ({@code /komaedit pintar}): no hay radio, así que el único número es la
     *               fuerza
     */
    public static PaintConfig parse(BrushOptions options, boolean region) {
        String palette = null;
        int radius = DEFAULT_RADIUS;
        int strength = DEFAULT_STRENGTH;
        PaintGradient gradient = DEFAULT_GRADIENT;
        int numbers = 0;
        boolean gradientGiven = false;
        for (int index = 0; index < options.count(); index++) {
            String token = options.positional(index);
            PaintGradient mode = PaintGradient.find(token);
            if (BrushOptions.isNumber(token)) {
                if (numbers == 0 && !region) {
                    radius = options.integer(index, DEFAULT_RADIUS, 1, MAX_RADIUS, "El radio");
                } else if (numbers == (region ? 0 : 1)) {
                    strength = options.integer(index, DEFAULT_STRENGTH, 1, 100, "La fuerza (%)");
                } else {
                    throw new IllegalArgumentException("Sobra el número " + token + (region
                            ? " (pintar [paleta] [degradado] [fuerza])"
                            : " (pincel paint [paleta] [radio] [degradado] [fuerza])"));
                }
                numbers++;
            } else if (mode != null && !gradientGiven) {
                gradient = mode;
                gradientGiven = true;
            } else if (palette == null) {
                palette = token;
            } else {
                throw new IllegalArgumentException("No entiendo «" + token + "»: la paleta ya es " + palette);
            }
        }
        String footprint = options.has('h') ? FootprintLibrary.normalize(options.flag('h')) : FootprintLibrary.CIRCLE;
        Long seed = options.has('s')
                ? (long) options.flagDecimal('s', 0, 0, 999_999_999, "La semilla (-s)") : null;
        double spacing = options.flagDecimal('e', DEFAULT_SPACING, 5, 200, "El espaciado (-e, % del radio)") / 100.0;
        double hardness = options.flagDecimal('u', DEFAULT_HARDNESS, 0, 100, "La dureza (-u, %)") / 100.0;
        double jitter = options.flagDecimal('j', DEFAULT_JITTER, 0, 100, "La variación (-j, %)") / 100.0;
        int layers = (int) options.flagDecimal('d', 1, 1, 8, "Las capas (-d)");
        double[] heights = options.has('y') ? range(options.flag('y'), -2_048, 4_096, "Las alturas (-y)")
                : new double[]{Double.NaN, Double.NaN};
        double[] slopes = options.has('a') ? range(options.flag('a'), 0, 90, "Las pendientes (-a, grados)")
                : new double[]{DEFAULT_SLOPE_LOW, DEFAULT_SLOPE_HIGH};
        int noiseSize = (int) options.flagDecimal('z', DEFAULT_NOISE_SIZE, 2, 256, "El tamaño del ruido (-z)");
        return new PaintConfig(palette == null ? PaintPalettes.DEFAULT : palette, radius, gradient, strength / 100.0,
                footprint, seed, options.has('k'), spacing, hardness, jitter, layers, !options.has('t'),
                !options.has('c'), text(options, 'b'), text(options, 'm'), options.has('x'), heights[0], heights[1],
                slopes[0], slopes[1], text(options, 'n'), noiseSize, options.has('p'));
    }

    private static String text(BrushOptions options, char flag) {
        if (!options.has(flag)) {
            return null;
        }
        String value = options.flag(flag).strip();
        return value.isEmpty() ? null : value;
    }

    /** {@code 60..120} (también {@code 60:120}); el orden da igual. */
    static double[] range(String text, double minimum, double maximum, String what) {
        String[] parts = text.strip().split("\\.\\.|:");
        if (parts.length != 2) {
            throw new IllegalArgumentException(what + ": escribe desde..hasta, por ejemplo 60..120");
        }
        double[] values = new double[2];
        for (int index = 0; index < 2; index++) {
            try {
                values[index] = Double.parseDouble(parts[index].strip().replace(',', '.'));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(what + ": «" + parts[index].strip() + "» no es un número");
            }
            if (!Double.isFinite(values[index]) || values[index] < minimum || values[index] > maximum) {
                throw new IllegalArgumentException(what + " van de " + (long) minimum + " a " + (long) maximum);
            }
        }
        if (values[0] == values[1]) {
            throw new IllegalArgumentException(what + ": el principio y el final no pueden coincidir");
        }
        return new double[]{Math.min(values[0], values[1]), Math.max(values[0], values[1])};
    }

    /**
     * Cuánto se estira el ruido de serie: un fBm de Perlin deja el 90 % de sus valores entre 0,37 y 0,63, y sin
     * estirarlo solo saldrían los tonos del medio de la paleta.
     */
    public static final double NOISE_CONTRAST = 3.6;

    /** El ruido de serie estirado para que llegue de un extremo de la paleta al otro. */
    public static BrushNoise contrast(BrushNoise field) {
        return (x, y, z) -> Math.clamp(0.5 + (field.sample(x, y, z) - 0.5) * NOISE_CONTRAST, 0.0, 1.0);
    }

    /** Manchas suaves del tamaño pedido: el ruido de serie del degradado por ruido. */
    public static NoiseSpec defaultNoise(int size) {
        return NoiseSpec.parse(String.format(Locale.ROOT, "Pe(fT:F,fO:4,F:%.5f)", 1.0 / Math.max(2, size)));
    }

    /** Lo que mueve las fronteras entre tonos: detalle de unos pocos bloques, más fino que el pincel. */
    public static NoiseSpec jitterNoise(int radius) {
        double size = Math.clamp(radius * 0.6, 3.0, 10.0);
        return NoiseSpec.parse(String.format(Locale.ROOT, "Pe(fT:F,fO:3,F:%.5f)", 1.0 / size));
    }

    /** Si la altura del degradado se mide al empezar cada trazo. */
    public boolean autoHeights() {
        return Double.isNaN(heightLow);
    }

    /** Resumen corto para el chat: «hierba_piedra por pendiente · círculo · fuerza 40 %». */
    public String describe() {
        return palette + " " + gradient.description() + " · huella " + footprint + " · fuerza "
                + Math.round(strength * 100) + " %";
    }
}
