package com.komainu.moxia.terrain;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.terrain.stamp.StampInfo;
import com.komainu.moxia.terrain.stamp.StampPlacement;
import com.komainu.moxia.terrain.stamp.StampVariation;
import com.sk89q.worldedit.math.BlockVector3;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ajustes del pincel de relieve ({@code /komaedit pincel heightmap}):
 * {@code <sello> [radio] [altura] [modo] [paleta] [-i intensidad] [-r giro] [-x variación] [-s semilla] [-k]
 * [-m máscara] [-c neutro] [-p]}. Se leen en el hilo del servidor (el PNG incluido) y cada clic sale de aquí como una
 * {@link HeightmapOperation}.
 *
 * <p>Sin altura, la natural del sello para ese radio (una colina sale baja y un pico alto); sin paleta o con
 * {@code auto}, la de su categoría. Cada clic varía a la vez giro, escala, espejo y ruido fino según la semilla y el
 * número de clic ({@link StampVariation}); {@code -k} bloquea la semilla para repetir exacto.</p>
 */
public final class HeightmapBrush {
    public static final int DEFAULT_RADIUS = 12;
    public static final int MAX_RADIUS = 32;
    public static final int MAX_HEIGHT = 96;
    /** Altura natural de un PNG propio, por bloque de radio. */
    public static final double CUSTOM_RATIO = 0.5;
    public static final String AUTO_PALETTE = "auto";

    private HeightmapBrush() {
    }

    public enum Rotation {
        AIM, RANDOM, FIXED
    }

    /**
     * @param angleDegrees giro fijo (con {@link Rotation#FIXED})
     * @param mask         la {@code -m} tal como se escribió (lista o máscara de WorldEdit), o {@code null}; se lee
     *                     en cada clic con {@code BrushMasks}, como en el resto de pinceles
     * @param seed         semilla con la que empieza el pincel
     * @param locked       con la semilla bloqueada todos los clics salen iguales
     */
    public record Config(HeightStamp stamp, int radius, double amplitude, StampBrush.Mode mode, double strength,
                         Rotation rotation, double angleDegrees, SurfacePalette palette, String mask,
                         TerrainBlocks blocks, StampVariation variation, long seed, boolean locked) {
    }

    public static Config parse(BrushOptions options) {
        return parse(options, 0);
    }

    /**
     * @param fixedRadius sobre la selección ({@code /komaedit relieve}), el radio que la cubre; entonces el único
     *                    número es la altura. 0: el pincel, con el radio escrito
     */
    public static Config parse(BrushOptions options, int fixedRadius) {
        String name = options.required(0, "el sello (montana, crater, duna… o el nombre de un PNG)");
        HeightStamp stamp;
        try {
            stamp = TerrainService.catalog().load(name);
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el sello " + name + ": " + exception.getMessage(),
                    exception);
        }
        if (options.has('c')) {
            stamp = stamp.withNeutral(options.flagDecimal('c', stamp.neutral(), 0.0, 1.0, "El gris neutro"));
        }
        int radius = fixedRadius > 0 ? fixedRadius : DEFAULT_RADIUS;
        Double amplitude = null;
        StampBrush.Mode mode = StampBrush.Mode.RAISE;
        String paletteText = null;
        int numbers = fixedRadius > 0 ? 1 : 0;
        for (int index = 1; index < options.count(); index++) {
            String token = options.positional(index);
            if (BrushOptions.isNumber(token)) {
                if (numbers == 0) {
                    radius = options.integer(index, DEFAULT_RADIUS, 1, MAX_RADIUS, "El radio");
                } else if (numbers == 1) {
                    amplitude = options.decimal(index, 0.0, 0.0, MAX_HEIGHT, "La altura");
                } else {
                    throw new IllegalArgumentException("Sobra el número " + token + (fixedRadius > 0
                            ? " (relieve <sello> [altura] [modo] [paleta])"
                            : " (pincel heightmap <sello> [radio] [altura] [modo] [paleta])"));
                }
                numbers++;
            } else if (isMode(token)) {
                mode = StampBrush.Mode.byId(token);
            } else if (paletteText == null) {
                paletteText = token;
            } else {
                throw new IllegalArgumentException("No entiendo «" + token + "»");
            }
        }
        double height = amplitude != null ? amplitude : naturalHeight(stamp.name(), radius);
        double strength = options.flagDecimal('i', 100.0, 1.0, 100.0, "La intensidad") / 100.0;
        Rotation rotation = Rotation.AIM;
        double angle = 0.0;
        if (options.has('r')) {
            String text = options.flag('r').toLowerCase(Locale.ROOT);
            switch (text) {
                case "mirada", "aim", "look", "vista" -> rotation = Rotation.AIM;
                case "azar", "random", "aleatorio" -> rotation = Rotation.RANDOM;
                default -> {
                    rotation = Rotation.FIXED;
                    angle = options.flagDecimal('r', 0.0, -360.0, 360.0, "El giro");
                }
            }
        }
        SurfacePalette palette = SurfacePalette.parse(paletteFor(stamp.name(), paletteText));
        String mask = options.has('m') ? options.flag('m') : null;
        StampVariation variation = options.has('x')
                ? StampVariation.percent(options.flagDecimal('x', 0.0, 0.0, 100.0, "La variación"))
                : StampVariation.DEFAULT;
        long seed = options.has('s') ? parseSeed(options.flag('s')) : ThreadLocalRandom.current().nextInt(1_000_000);
        return new Config(stamp, radius, height, mode, strength, rotation, angle, palette, mask,
                TerrainBlocks.resolve(palette), variation, seed, options.has('k'));
    }

    /** La altura natural del sello para un radio: la de su receta, o la mitad del radio en un PNG propio. */
    public static int naturalHeight(String stamp, int radius) {
        StampInfo info = StampLibrary.info(stamp);
        return info != null ? info.naturalHeight(radius, MAX_HEIGHT)
                : (int) Math.clamp(Math.round(radius * CUSTOM_RATIO), 1L, MAX_HEIGHT);
    }

    /** La paleta escrita, o con {@code auto} (o sin escribir) la de la categoría del sello. */
    public static String paletteFor(String stamp, String written) {
        if (written != null && !written.isBlank() && !written.equalsIgnoreCase(AUTO_PALETTE)) {
            return written;
        }
        StampInfo info = StampLibrary.info(stamp);
        return info == null ? SurfacePalette.DEFAULT : info.palette();
    }

    private static long parseSeed(String text) {
        try {
            long seed = Long.parseLong(text.strip());
            if (seed < 0) {
                throw new NumberFormatException();
            }
            return seed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La semilla debe ser un número entero positivo: " + text);
        }
    }

    private static boolean isMode(String token) {
        try {
            StampBrush.Mode.byId(token);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** Dónde cae el sello en un clic: giro pedido más la variación de la semilla y el número de clic. */
    public static StampPlacement placement(Config config, float yaw, long seed, int click) {
        double base = switch (config.rotation) {
            // Con giro 0 la parte de arriba del sello mira al norte; un jugador que mira al norte tiene yaw 180.
            case AIM -> Math.toRadians(yaw + 180.0);
            case RANDOM -> 0.0;
            case FIXED -> Math.toRadians(config.angleDegrees);
        };
        return config.variation.place(config.radius, base, config.rotation == Rotation.RANDOM, seed,
                config.locked ? 0 : click);
    }

    /**
     * La pasada de un clic.
     *
     * @param yaw   hacia dónde mira el jugador (grados de Minecraft)
     * @param seed  semilla del pincel ahora mismo
     * @param click número de clic desde que se eligió el pincel o se cambió la semilla
     * @param mask  la máscara de este clic ya leída, o {@code null} para todas las columnas
     */
    public static HeightmapOperation operation(Config config, StampBrush.Mode mode, BlockVector3 target, float yaw,
                                               long seed, int click, BrushMask mask) {
        StampBrush.Settings settings = new StampBrush.Settings(config.stamp, mode, placement(config, yaw, seed, click),
                config.amplitude, config.strength, target.y());
        return new HeightmapOperation(settings, target, config.palette, mask, config.blocks);
    }

    public static String controls(Config config, StampBrush.Mode mode, long seed) {
        String rotation = switch (config.rotation) {
            case AIM -> "girado hacia donde miras";
            case RANDOM -> "girado al azar";
            case FIXED -> "girado " + Math.round(config.angleDegrees) + "°";
        };
        String variation = config.variation.amount() <= 0.0 ? "sin variación"
                : "variación " + Math.round(config.variation.amount() * 100.0) + " %";
        return "sello " + config.stamp.name() + ", " + rotation + ", " + variation + ", semilla " + seed
                + (config.locked ? " bloqueada" : "") + " · clic derecho: " + mode.displayName().toLowerCase(
                Locale.ROOT) + " · clic izquierdo: " + mode.opposite().displayName().toLowerCase(Locale.ROOT)
                + " · F: cambia de modo · Mayús+F: semilla nueva";
    }
}
