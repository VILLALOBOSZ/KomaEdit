package com.komainu.moxia.terrain.stamp;

import com.komainu.moxia.terrain.HeightStamp;

/**
 * Cómo cae un sello en el mundo: tamaño, giro, escala, espejo y ruido fino. Es lo que cambia de un clic a otro con la
 * variación ({@link StampVariation}) y lo que usan el pincel de relieve y cualquier otra herramienta que quiera
 * muestrear un sello ({@link #relief}).
 *
 * @param radius     medio lado del cuadrado del sello en bloques, sin escalar
 * @param angle      giro en radianes: 0 deja la parte de arriba de la imagen al norte (-Z) y crece en sentido horario
 *                   visto desde arriba, como la orientación de un jugador
 * @param scale      multiplica el radio (1 = tal cual)
 * @param mirrored   da la vuelta a la imagen de izquierda a derecha antes de girarla
 * @param detailSeed semilla del ruido fino
 * @param detail     fuerza del ruido fino: el relieve se multiplica por {@code 1 ± detail·ruido}, así los bordes siguen
 *                   en cero
 */
public record StampPlacement(double radius, double angle, double scale, boolean mirrored, long detailSeed,
                             double detail) {
    /** Frecuencia del ruido fino: ciclos a lo ancho del sello. */
    private static final double DETAIL_FREQUENCY = 3.0;

    public StampPlacement {
        if (!(radius > 0.0) || !(scale > 0.0)) {
            throw new IllegalArgumentException("El radio y la escala del sello deben ser positivos");
        }
        if (detail < 0.0 || detail > 1.0) {
            throw new IllegalArgumentException("El ruido fino va de 0 a 1");
        }
    }

    /** Tal cual: sin escala, espejo ni ruido. */
    public static StampPlacement of(double radius, double angle) {
        return new StampPlacement(radius, angle, 1.0, false, 0L, 0.0);
    }

    /** Medio lado real del sello en bloques (el radio por la escala). */
    public double extent() {
        return radius * scale;
    }

    /** Hasta dónde llega el sello girado, en bloques desde el centro. */
    public int reach() {
        return (int) Math.ceil(extent() * Math.sqrt(2.0)) + 1;
    }

    /**
     * Relieve del sello en un punto relativo a su centro ({@code dx} hacia el este, {@code dz} hacia el sur): de -1 a 1
     * respecto al gris neutro (lo normal, de 0 a 1). Fuera del sello vale 0.
     */
    public double relief(HeightStamp stamp, double dx, double dz) {
        double half = extent();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        // Del mundo al sello: deshacer el giro. Con giro 0, +X del mundo es la derecha y -Z la parte de arriba.
        double right = dx * cos + dz * sin;
        double down = -dx * sin + dz * cos;
        if (mirrored) {
            right = -right;
        }
        double value = stamp.reliefAt(0.5 + right / (2.0 * half), 0.5 + down / (2.0 * half));
        if (detail > 0.0 && value != 0.0) {
            double noise = StampNoise.fbm(right / half * DETAIL_FREQUENCY, down / half * DETAIL_FREQUENCY, detailSeed,
                    3);
            value *= 1.0 + detail * noise;
        }
        return value;
    }

    /**
     * Huella del sello: 1 dentro y 0 en su círculo, con una transición suave. Aplanar y suavizar la usan para que el
     * borde no deje escalón aunque el sello no llegue a cero.
     */
    public double footprint(double dx, double dz) {
        return StampMath.smoothstep(1.0, 0.75, Math.hypot(dx, dz) / extent());
    }
}
