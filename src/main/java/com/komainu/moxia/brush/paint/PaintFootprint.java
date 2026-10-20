package com.komainu.moxia.brush.paint;

import com.komainu.moxia.terrain.HeightStamp;

/**
 * La forma del pincel de pintura vista desde arriba: cuánto pinta cada punto, de 0 (nada) a 1 (todo). Se mide en
 * coordenadas de la huella, con el centro del clic en (0, 0) y el radio del pincel en 1: {@code u} hacia la derecha
 * y {@code v} hacia abajo de la imagen (el sur con giro 0).
 *
 * <p>Un círculo o un cuadrado sencillos, o un sello de la biblioteca de relieve usado como máscara: lo que se aparta
 * del gris neutro (hacia arriba o hacia abajo) pinta más. La dureza ({@link #intensity}) decide si el borde se
 * difumina o corta.</p>
 */
public interface PaintFootprint {
    /** Nombre con el que se elige ({@code circulo}, {@code cuadrado}, {@code montana}…). */
    String name();

    /** Forma en bruto de 0 a 1 en el punto {@code (u, v)} de la huella; 0 fuera. */
    double shape(double u, double v);

    /** Hasta dónde llega la forma desde el centro, en radios: 1 un círculo, √2 las esquinas de un cuadrado. */
    double extent();

    /**
     * La intensidad que pinta una forma {@code shape} con la dureza {@code hardness} (de 0 a 1): con 0 el borde se
     * difumina desde el centro; con 1 la huella corta en seco. Siempre de 0 a 1 y creciente con la forma.
     */
    static double intensity(double shape, double hardness) {
        if (!(shape > 0.0)) {
            return 0.0;
        }
        double t = Math.min(1.0, shape / Math.max(0.02, 1.0 - Math.clamp(hardness, 0.0, 1.0)));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Círculo: pinta más en el centro y nada a partir del radio. */
    static PaintFootprint circle() {
        return Simple.CIRCLE;
    }

    /** Cuadrado de lado {@code 2·radio}, con el borde según la dureza. */
    static PaintFootprint square() {
        return Simple.SQUARE;
    }

    /** Un sello de relieve como máscara: el blanco (o el negro, en los que hunden) pinta del todo. */
    static PaintFootprint stamp(HeightStamp stamp) {
        return new Stamp(stamp);
    }

    /** Las huellas sencillas, sin imagen. */
    record Simple(String name, double extent, boolean round) implements PaintFootprint {
        static final Simple CIRCLE = new Simple(FootprintLibrary.CIRCLE, 1.0, true);
        static final Simple SQUARE = new Simple(FootprintLibrary.SQUARE, Math.sqrt(2.0), false);

        @Override
        public double shape(double u, double v) {
            double distance = round ? Math.sqrt(u * u + v * v) : Math.max(Math.abs(u), Math.abs(v));
            return Math.max(0.0, 1.0 - distance);
        }
    }

    /**
     * Un sello: lo que se aparta del gris neutro, normalizado para que lo más fuerte del sello valga 1. El alcance
     * se mide una vez sobre la imagen (los de serie llegan al gris neutro en el círculo inscrito).
     */
    final class Stamp implements PaintFootprint {
        private final HeightStamp stamp;
        private final double peak;
        private final double extent;

        private Stamp(HeightStamp stamp) {
            this.stamp = stamp;
            double neutral = stamp.neutral();
            double strongest = 0.0;
            double reach = 0.0;
            for (int row = 0; row < stamp.height(); row++) {
                double v = row / (stamp.height() - 1.0) * 2.0 - 1.0;
                for (int column = 0; column < stamp.width(); column++) {
                    double away = away(stamp.pixel(column, row), neutral);
                    if (away > 1.0E-3) {
                        double u = column / (stamp.width() - 1.0) * 2.0 - 1.0;
                        strongest = Math.max(strongest, away);
                        // Medio píxel más, para no recortar el último anillo al girar.
                        reach = Math.max(reach, Math.sqrt(u * u + v * v) + 1.0 / stamp.width());
                    }
                }
            }
            this.peak = strongest;
            this.extent = Math.min(Math.sqrt(2.0), Math.max(0.05, reach));
        }

        private static double away(double value, double neutral) {
            double span = value >= neutral ? 1.0 - neutral : neutral;
            return span <= 0.0 ? 0.0 : Math.abs(value - neutral) / span;
        }

        @Override
        public String name() {
            return stamp.name();
        }

        @Override
        public double shape(double u, double v) {
            if (peak <= 0.0 || Math.abs(u) > 1.0 || Math.abs(v) > 1.0) {
                return 0.0;
            }
            return Math.min(1.0, away(stamp.sample(0.5 + u * 0.5, 0.5 + v * 0.5), stamp.neutral()) / peak);
        }

        @Override
        public double extent() {
            return extent;
        }
    }
}
