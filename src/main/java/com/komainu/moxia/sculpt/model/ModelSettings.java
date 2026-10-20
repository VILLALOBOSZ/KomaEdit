package com.komainu.moxia.sculpt.model;

import com.komainu.moxia.sculpt.BrushFalloff;

/**
 * Una pasada de un pincel de modelado: dónde, cuánto y cómo. Cada campo lo usan solo algunos modos; los demás lo
 * ignoran. Es inmutable y no toca Minecraft.
 *
 * @param strength   esculpir: bloques que sube o baja el centro; rugosidad: amplitud en bloques
 * @param invert     esculpir: hundir en vez de empujar; roca: tallar en vez de añadir
 * @param smoothing  esculpir: pasadas de suavizado tras desplazar (0 a 4)
 * @param threshold  soldar y fundir: corte de la densidad desenfocada (0 a 1)
 * @param sigma      soldar y fundir: desviación del desenfoque gaussiano, en bloques
 * @param roughness  roca: cuánto deforma el ruido el radio (0 a 1)
 * @param scale      roca y rugosidad: tamaño de los rasgos del ruido; grietas: tamaño de las celdas
 * @param blend      roca: radio de la unión suave con lo que hay
 * @param width      grietas: ancho de la grieta en bloques
 * @param axis       grietas: columnas a lo largo de un eje o celdas en 3D
 * @param depth      grietas: profundidad máxima desde la superficie (0 = sin límite)
 * @param hint       normal de respaldo (la cara pulsada) si no hay superficie bajo el pincel
 */
public record ModelSettings(
        ModelMode mode,
        int centerX,
        int centerY,
        int centerZ,
        int radius,
        BrushFalloff falloff,
        double strength,
        boolean invert,
        int smoothing,
        double threshold,
        double sigma,
        double roughness,
        double scale,
        double blend,
        double width,
        ModelMode.Axis axis,
        int depth,
        long seed,
        double[] hint
) {
    public static final int MAX_RADIUS = 32;

    public ModelSettings {
        if (radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("El radio debe estar entre 1 y " + MAX_RADIUS);
        }
        check(strength, 0.0, 16.0, "La fuerza");
        if (smoothing < 0 || smoothing > 4) {
            throw new IllegalArgumentException("El suavizado va de 0 a 4 pasadas");
        }
        check(threshold, 0.0, 1.0, "El umbral");
        check(sigma, 0.3, 8.0, "El desenfoque");
        check(roughness, 0.0, 1.0, "La rugosidad");
        check(scale, 0.5, 64.0, "La escala");
        check(blend, 0.0, 16.0, "El fundido");
        check(width, 0.1, 8.0, "El ancho");
        if (depth < 0 || depth > 64) {
            throw new IllegalArgumentException("La profundidad va de 0 (sin límite) a 64");
        }
        hint = hint == null || hint.length != 3 ? new double[]{0.0, 1.0, 0.0} : hint.clone();
    }

    private static void check(double value, double minimum, double maximum, String what) {
        if (!(value >= minimum && value <= maximum)) {
            throw new IllegalArgumentException(what + " debe estar entre " + minimum + " y " + maximum);
        }
    }

    /** Lo de serie de cada modo, centrado en {@code (x, y, z)}. */
    public static ModelSettings defaults(ModelMode mode, int x, int y, int z, int radius) {
        double strength = switch (mode) {
            case DRAW -> 2.0;
            case ROUGHEN -> 1.5;
            default -> 1.0;
        };
        // Las grietas no se afinan hacia el borde salvo que se pida otra caída.
        BrushFalloff falloff = mode == ModelMode.SHATTER ? BrushFalloff.CONSTANT : BrushFalloff.SMOOTH;
        double threshold = mode == ModelMode.MELT ? 0.6 : 0.4;
        double scale = switch (mode) {
            case ROCK -> Math.max(2.0, radius * 0.7);
            case ROUGHEN -> 1.5;
            case SHATTER -> Math.max(3.0, radius * 0.6);
            default -> 4.0;
        };
        return new ModelSettings(mode, x, y, z, radius, falloff, strength, false, 0, threshold, 1.5,
                0.4, scale, Math.max(1.0, radius * 0.35), 1.0, ModelMode.Axis.FREE, 3, 0L, null);
    }

    public ModelSettings withFalloff(BrushFalloff value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, value, strength, invert, smoothing,
                threshold, sigma, roughness, scale, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withStrength(double value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, value, invert, smoothing,
                threshold, sigma, roughness, scale, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withInvert(boolean value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, value, smoothing,
                threshold, sigma, roughness, scale, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withSmoothing(int value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, value,
                threshold, sigma, roughness, scale, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withThreshold(double value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                value, sigma, roughness, scale, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withSigma(double value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, value, roughness, scale, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withRoughness(double value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, value, scale, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withScale(double value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, roughness, value, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withBlend(double value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, roughness, scale, value, width, axis, depth, seed, hint);
    }

    public ModelSettings withWidth(double value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, roughness, scale, blend, value, axis, depth, seed, hint);
    }

    public ModelSettings withAxis(ModelMode.Axis value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, roughness, scale, blend, width, value, depth, seed, hint);
    }

    public ModelSettings withDepth(int value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, roughness, scale, blend, width, axis, value, seed, hint);
    }

    public ModelSettings withSeed(long value) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, roughness, scale, blend, width, axis, depth, value, hint);
    }

    public ModelSettings withHint(double x, double y, double z) {
        return new ModelSettings(mode, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, roughness, scale, blend, width, axis, depth, seed, new double[]{x, y, z});
    }

    /** La misma pasada en otro sitio (cada clic del pincel). */
    public ModelSettings at(int x, int y, int z) {
        return new ModelSettings(mode, x, y, z, radius, falloff, strength, invert, smoothing, threshold, sigma,
                roughness, scale, blend, width, axis, depth, seed, hint);
    }

    public ModelSettings withMode(ModelMode value) {
        return new ModelSettings(value, centerX, centerY, centerZ, radius, falloff, strength, invert, smoothing,
                threshold, sigma, roughness, scale, blend, width, axis, depth, seed, hint);
    }

    /**
     * Margen que hay que leer alrededor del radio para ver todo lo que influye en el resultado: el desplazamiento
     * de esculpir, el ruido y el fundido de la roca, la ventana del desenfoque o la profundidad de las grietas.
     */
    public int reach() {
        int extra = switch (mode) {
            case DRAW -> (int) Math.ceil(strength) + 3 + smoothing;
            case ROCK -> (int) Math.ceil(radius * (0.3 + roughness) + blend) + 3;
            case WELD, MELT -> (int) Math.ceil(sigma * 2.5) + 2;
            case ROUGHEN -> (int) Math.ceil(strength) + 3;
            case SHATTER -> depth + 2;
        };
        return radius + Math.min(extra, 32);
    }
}
