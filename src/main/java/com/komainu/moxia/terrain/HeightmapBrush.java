package com.komainu.moxia.terrain;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.BrushOptions;
import com.sk89q.worldedit.math.BlockVector3;

import java.io.IOException;
import java.util.Locale;

/**
 * Ajustes del pincel de relieve ({@code /komaedit pincel heightmap}):
 * {@code <sello> [radio] [altura] [modo] [paleta] [-i intensidad] [-r giro] [-m máscara] [-c neutro] [-p]}.
 * Se leen en el hilo del servidor (el PNG incluido) y cada clic sale de aquí como una {@link HeightmapOperation}.
 */
public final class HeightmapBrush {
    public static final int DEFAULT_RADIUS = 12;
    public static final int MAX_RADIUS = 32;
    public static final double DEFAULT_HEIGHT = 12.0;

    private HeightmapBrush() {
    }

    public enum Rotation {
        AIM, RANDOM, FIXED
    }

    /**
     * @param angleDegrees giro fijo (con {@link Rotation#FIXED})
     * @param mask         la {@code -m} tal como se escribió (lista o máscara de WorldEdit), o {@code null}; se lee
     *                     en cada clic con {@code BrushMasks}, como en el resto de pinceles
     */
    public record Config(HeightStamp stamp, int radius, double amplitude, StampBrush.Mode mode, double strength,
                         Rotation rotation, double angleDegrees, SurfacePalette palette, String mask,
                         TerrainBlocks blocks) {
    }

    public static Config parse(BrushOptions options) {
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
        int radius = DEFAULT_RADIUS;
        double amplitude = DEFAULT_HEIGHT;
        StampBrush.Mode mode = StampBrush.Mode.RAISE;
        String paletteText = null;
        int numbers = 0;
        for (int index = 1; index < options.count(); index++) {
            String token = options.positional(index);
            if (BrushOptions.isNumber(token)) {
                if (numbers == 0) {
                    radius = options.integer(index, DEFAULT_RADIUS, 1, MAX_RADIUS, "El radio");
                } else if (numbers == 1) {
                    amplitude = options.decimal(index, DEFAULT_HEIGHT, 0.0, 96.0, "La altura");
                } else {
                    throw new IllegalArgumentException("Sobra el número " + token
                            + " (pincel heightmap <sello> [radio] [altura] [modo] [paleta])");
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
        SurfacePalette palette = SurfacePalette.parse(paletteText);
        String mask = options.has('m') ? options.flag('m') : null;
        return new Config(stamp, radius, amplitude, mode, strength, rotation, angle, palette, mask,
                TerrainBlocks.resolve(palette));
    }

    private static boolean isMode(String token) {
        try {
            StampBrush.Mode.byId(token);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * La pasada de un clic.
     *
     * @param yaw    hacia dónde mira el jugador (grados de Minecraft)
     * @param random un número de 0 a 1 para el giro al azar
     * @param mask   la máscara de este clic ya leída, o {@code null} para todas las columnas
     */
    public static HeightmapOperation operation(Config config, StampBrush.Mode mode, BlockVector3 target, float yaw,
                                               double random, BrushMask mask) {
        double angle = switch (config.rotation) {
            // Con giro 0 la parte de arriba del sello mira al norte; un jugador que mira al norte tiene yaw 180.
            case AIM -> Math.toRadians(yaw + 180.0);
            case RANDOM -> random * Math.PI * 2.0;
            case FIXED -> Math.toRadians(config.angleDegrees);
        };
        StampBrush.Settings settings = new StampBrush.Settings(config.stamp, mode, config.radius, config.amplitude,
                config.strength, angle, target.y());
        return new HeightmapOperation(settings, target, config.palette, mask, config.blocks);
    }

    public static String controls(Config config, StampBrush.Mode mode) {
        String rotation = switch (config.rotation) {
            case AIM -> "girado hacia donde miras";
            case RANDOM -> "girado al azar";
            case FIXED -> "girado " + Math.round(config.angleDegrees) + "°";
        };
        return "sello " + config.stamp.name() + ", " + rotation + " · clic derecho: " + mode.displayName().toLowerCase(
                Locale.ROOT) + " · clic izquierdo: " + mode.opposite().displayName().toLowerCase(Locale.ROOT)
                + " · F: cambia de modo";
    }
}
