package com.komainu.moxia.terrain.stamp;

/**
 * Variación de un clic a otro, como la semilla de Axiom: de la semilla del pincel y el número de clic salen a la vez un
 * giro, una escala, un espejo y un ruido fino, para que estampar diez veces el mismo sello no deje diez copias
 * iguales. Con la misma semilla y el mismo clic sale siempre lo mismo; con la semilla bloqueada se repite el clic 0.
 *
 * @param amount de 0 (sin variación: el sello tal cual) a 1 (giro de ±{@value #MAX_TURN_DEGREES}°, escala de
 *               ±{@value #MAX_SCALE} y ruido fino de {@value #MAX_DETAIL})
 */
public record StampVariation(double amount) {
    public static final StampVariation NONE = new StampVariation(0.0);
    /** La de serie del pincel. */
    public static final StampVariation DEFAULT = new StampVariation(0.6);
    public static final double MAX_TURN_DEGREES = 25.0;
    public static final double MAX_SCALE = 0.2;
    public static final double MAX_DETAIL = 0.3;

    public StampVariation {
        if (!(amount >= 0.0 && amount <= 1.0)) {
            throw new IllegalArgumentException("La variación va de 0 a 100 %");
        }
    }

    /** De un porcentaje (0 a 100). */
    public static StampVariation percent(double value) {
        return new StampVariation(Math.clamp(value, 0.0, 100.0) / 100.0);
    }

    /**
     * La colocación de un clic.
     *
     * @param radius      radio del pincel en bloques
     * @param baseAngle   giro pedido (hacia donde mira el jugador o fijo), en radianes
     * @param randomAngle si el giro es al azar: entonces lo pone la semilla entero, sin depender de {@code amount}
     * @param seed        semilla del pincel
     * @param click       número de clic (0 el primero); con la semilla bloqueada siempre 0
     */
    public StampPlacement place(double radius, double baseAngle, boolean randomAngle, long seed, int click) {
        long mixed = mix(seed * 0x9E3779B97F4A7C15L + click * 0xC2B2AE3D27D4EB4FL + 0x632BE59BD9B4E019L);
        double turn = unit(mixed, 1);
        double angle = randomAngle ? turn * 2.0 * Math.PI
                : baseAngle + (turn - 0.5) * 2.0 * amount * Math.toRadians(MAX_TURN_DEGREES);
        double scale = 1.0 + (unit(mixed, 2) - 0.5) * 2.0 * amount * MAX_SCALE;
        boolean mirrored = amount > 0.0 && unit(mixed, 3) < 0.5;
        return new StampPlacement(radius, angle, scale, mirrored, mixed, amount * MAX_DETAIL);
    }

    private static double unit(long seed, int index) {
        return (mix(seed + index * 0xD1B54A32D192ED03L) >>> 11) * 0x1.0p-53;
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
