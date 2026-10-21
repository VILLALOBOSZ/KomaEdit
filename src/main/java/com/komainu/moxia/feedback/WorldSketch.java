package com.komainu.moxia.feedback;

import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Un dibujo de líneas y rótulos en el mundo que arma Java puro (la regla, las notas) y que pintan igual el cliente
 * ({@code client.world.SketchRenderer}) y los renders de las pruebas. Los rótulos miran a la cámara y miden lo mismo
 * en pantalla a cualquier distancia, como las medidas de la selección.
 */
public record WorldSketch(List<Line> lines, List<Label> labels) {
    public static final WorldSketch EMPTY = new WorldSketch(List.of(), List.of());

    public WorldSketch {
        lines = List.copyOf(lines);
        labels = List.copyOf(labels);
    }

    /** Un segmento con su color (ARGB). {@code seeThrough}: se ve a través de los bloques. */
    public record Line(Vec3 from, Vec3 to, int argb, boolean seeThrough) {
    }

    /** Una fila de texto de un rótulo con su color (RGB). */
    public record Row(String text, int rgb) {
    }

    /**
     * Un rótulo: filas de texto centradas sobre {@code at}, con un icono opcional a la izquierda de la primera.
     *
     * @param alpha 0 a 1, para que se apague al acercarse al límite de distancia
     * @param scale tamaño respecto al de las medidas de la selección (1)
     * @param solid fondo casi opaco (las notas): el rótulo de delante tapa al de detrás en lugar de mezclarse
     */
    public record Label(Vec3 at, List<Row> rows, PixelIcon icon, int iconRgb, double alpha, double scale,
                        boolean solid) {
        /** Alfa del fondo de un rótulo normal (como las medidas de la selección) y de uno sólido. */
        public static final int BACKGROUND_ALPHA = 0x66;
        public static final int SOLID_BACKGROUND_ALPHA = 0xC0;

        public Label {
            rows = List.copyOf(rows);
            alpha = Math.clamp(alpha, 0.0, 1.0);
        }

        /** Un rótulo de una fila, sin icono. */
        public static Label of(Vec3 at, String text, int rgb) {
            return new Label(at, List.of(new Row(text, rgb)), null, 0, 1.0, 1.0, false);
        }

        /** El alfa del fondo, ya con el de todo el rótulo. */
        public int backgroundAlpha() {
            return (int) Math.round((solid ? SOLID_BACKGROUND_ALPHA : BACKGROUND_ALPHA) * alpha);
        }
    }

    /** Un icono de píxeles: {@code mask[fila]}, {@code '#'} lleno y cualquier otro carácter vacío. */
    public record PixelIcon(List<String> mask) {
        public PixelIcon {
            List<String> rows = List.copyOf(mask);
            if (rows.isEmpty() || rows.stream().anyMatch(row -> row.length() != rows.getFirst().length())) {
                throw new IllegalArgumentException("Icono sin filas o con filas de distinto ancho");
            }
            mask = rows;
        }

        public int width() {
            return mask.getFirst().length();
        }

        public int height() {
            return mask.size();
        }

        public boolean filled(int x, int y) {
            return mask.get(y).charAt(x) == '#';
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<Line> lines = new ArrayList<>();
        private final List<Label> labels = new ArrayList<>();

        private Builder() {
        }

        public Builder line(Vec3 from, Vec3 to, int argb) {
            if (from.distance(to) > 1.0E-6) {
                lines.add(new Line(from, to, argb, false));
            }
            return this;
        }

        /** Una línea que se ve a través de los bloques (lo que se mide queda detrás de una pared). */
        public Builder ghostLine(Vec3 from, Vec3 to, int argb) {
            if (from.distance(to) > 1.0E-6) {
                lines.add(new Line(from, to, argb, true));
            }
            return this;
        }

        /** Las doce aristas de la caja {@code [min, max]}. */
        public Builder box(Vec3 min, Vec3 max, int argb) {
            double[] xs = {min.x(), max.x()};
            double[] ys = {min.y(), max.y()};
            double[] zs = {min.z(), max.z()};
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    line(new Vec3(xs[0], ys[a], zs[b]), new Vec3(xs[1], ys[a], zs[b]), argb);
                    line(new Vec3(xs[a], ys[0], zs[b]), new Vec3(xs[a], ys[1], zs[b]), argb);
                    line(new Vec3(xs[a], ys[b], zs[0]), new Vec3(xs[a], ys[b], zs[1]), argb);
                }
            }
            return this;
        }

        /** El contorno de un bloque, algo más grande para que no parpadee con sus caras. */
        public Builder block(int x, int y, int z, int argb) {
            double grow = 0.004;
            return box(new Vec3(x - grow, y - grow, z - grow), new Vec3(x + 1 + grow, y + 1 + grow, z + 1 + grow),
                    argb);
        }

        public Builder label(Label label) {
            if (!label.rows().isEmpty() && label.alpha() > 0.0) {
                labels.add(label);
            }
            return this;
        }

        public Builder label(Vec3 at, String text, int rgb) {
            return text.isEmpty() ? this : label(Label.of(at, text, rgb));
        }

        public Builder add(WorldSketch other) {
            lines.addAll(other.lines());
            labels.addAll(other.labels());
            return this;
        }

        public WorldSketch build() {
            return new WorldSketch(lines, labels);
        }
    }
}
