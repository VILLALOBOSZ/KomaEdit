package com.komainu.moxia.deform;

import java.util.Locale;

/**
 * {@code cylindrify} y {@code spherify}: proyectan la selección sobre un cilindro o una esfera nuevos, construidos
 * en una caja aparte (encima de la selección o alrededor del jugador). La selección original no se toca.
 *
 * <p>La selección es un «mapa»: el eje que va a {@code EW} da la vuelta (longitud), el que va a {@code NS} recorre
 * el cilindro a lo largo (o la latitud de la esfera) y el que va a {@code UD} es la profundidad: lo más alto del
 * mapa queda en la superficie exterior y lo más bajo a {@code depth} bloques hacia dentro. Cada bloque del
 * resultado se calcula al revés (de la figura al mapa), así no quedan agujeros.</p>
 */
public final class Projection {
    private final Settings settings;
    private final double[] center;
    private final double[] primary;
    private final double[] secondary;
    private final double[] tertiary;
    private final double[] radii;
    private final double halfLength;
    private final double depth;
    private final int[] box;
    private final int[] selection;
    private final double[] lengths = new double[3];

    /**
     * Lo que escribe el jugador.
     *
     * @param radii   {@code null} para calcularlos; cilindro: {@code r}, {@code r,mitad} o {@code r1,r2,mitad};
     *                esfera: {@code r} o {@code r1,r2,r3} (en los ejes -j, -i y el tercero)
     * @param depth   {@code NaN} para calcularla
     * @param anchor  centro pedido ({@code -o}: la posición del jugador) o {@code null} para ponerla encima
     */
    public record Settings(
            boolean sphere,
            boolean laea,
            double[] radii,
            double depth,
            AxisMap axisMap,
            Deformations.Axis primary,
            Deformations.Axis secondary,
            boolean keepSurroundings,
            boolean fill,
            boolean seam,
            boolean twoBlockCenter,
            double[] anchor
    ) {
        public Settings {
            if (primary.index() == secondary.index()) {
                throw new IllegalArgumentException("Los ejes -i y -j no pueden ser el mismo");
            }
            if (twoBlockCenter && radii == null) {
                throw new IllegalArgumentException("-t necesita los radios con -r");
            }
            if (laea && !sphere) {
                throw new IllegalArgumentException("-l solo vale para spherify");
            }
        }
    }

    /**
     * Qué eje de la selección va a cada eje de la figura, en el orden {@code EW, UD, NS}: {@code xyz} (por
     * defecto), {@code zyx}, {@code -xyz}… Un signo menos da la vuelta a ese eje.
     */
    public record AxisMap(int[] axes, boolean[] flipped) {
        public static final AxisMap DEFAULT = new AxisMap(new int[]{0, 1, 2}, new boolean[3]);

        public static AxisMap parse(String input) {
            String text = input.trim().toLowerCase(Locale.ROOT);
            int[] axes = new int[3];
            boolean[] flipped = new boolean[3];
            int slot = 0;
            boolean negative = false;
            boolean[] used = new boolean[3];
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                if (character == '-') {
                    negative = true;
                    continue;
                }
                if (character == '+' || character == ',') {
                    continue;
                }
                int axis = "xyz".indexOf(character);
                if (axis < 0 || slot >= 3 || used[axis]) {
                    throw new IllegalArgumentException("Mapa de ejes inválido: " + input
                            + " (tres ejes distintos para EW, UD y NS, por ejemplo xyz, zyx o -xyz)");
                }
                used[axis] = true;
                axes[slot] = axis;
                flipped[slot] = negative;
                negative = false;
                slot++;
            }
            if (slot != 3) {
                throw new IllegalArgumentException("Mapa de ejes inválido: " + input + " (por ejemplo xyz)");
            }
            return new AxisMap(axes, flipped);
        }
    }

    /**
     * @param selection caja de la selección ({@code minX…maxZ})
     * @param worldTop  primera altura fuera del mundo; si la figura no cabe encima, va al lado (este)
     */
    public Projection(Settings settings, int[] selection, int worldTop) {
        this.settings = settings;
        this.selection = selection.clone();
        for (int slot = 0; slot < 3; slot++) {
            int axis = settings.axisMap().axes()[slot];
            lengths[slot] = selection[axis + 3] - selection[axis] + 1;
        }
        if (settings.seam()) {
            lengths[0] = Math.max(1.0, lengths[0] - 1.0);
        }
        primary = settings.primary().vector();
        secondary = settings.secondary().vector();
        tertiary = cross(primary, secondary);

        double equator;
        if (settings.sphere()) {
            equator = settings.laea()
                    ? Math.sqrt(lengths[0] * lengths[2] / (4.0 * Math.PI))
                    : lengths[0] / (2.0 * Math.PI);
        } else {
            equator = lengths[0] / (2.0 * Math.PI);
        }
        equator = Math.max(1.0, equator);
        double scale = 1.0;
        double[] given = settings.radii();
        if (settings.sphere()) {
            if (given == null) {
                radii = new double[]{equator, equator, equator};
            } else if (given.length == 1) {
                radii = new double[]{given[0], given[0], given[0]};
            } else if (given.length == 3) {
                radii = given.clone();
            } else {
                throw new IllegalArgumentException("La esfera admite -r r o -r r1,r2,r3");
            }
            halfLength = 0.0;
            scale = (radii[0] + radii[1] + radii[2]) / 3.0 / equator;
        } else {
            double half = lengths[2] / 2.0;
            if (given == null) {
                radii = new double[]{equator, 0.0, equator};
            } else if (given.length == 1) {
                radii = new double[]{given[0], 0.0, given[0]};
                half *= given[0] / equator;
            } else if (given.length == 2) {
                radii = new double[]{given[0], 0.0, given[0]};
                half = given[1];
            } else if (given.length == 3) {
                radii = new double[]{given[0], 0.0, given[1]};
                half = given[2];
            } else {
                throw new IllegalArgumentException("El cilindro admite -r r, -r r,mitad o -r r1,r2,mitad");
            }
            halfLength = half;
            scale = (radii[0] + radii[2]) / 2.0 / equator;
            if (!(halfLength >= 0.5)) {
                throw new IllegalArgumentException("La mitad de la longitud del cilindro debe ser al menos 0.5");
            }
        }
        for (double radius : radii) {
            if (radius != 0.0 && !(radius >= 1.0 && radius <= 1_024.0)) {
                throw new IllegalArgumentException("Los radios van de 1 a 1024");
            }
        }
        double meanRadius = meanRadius();
        double wantedDepth = Double.isNaN(settings.depth()) ? lengths[1] * scale : settings.depth();
        if (!(wantedDepth > 0.0)) {
            throw new IllegalArgumentException("La profundidad debe ser mayor que 0");
        }
        depth = Math.min(wantedDepth, meanRadius);

        double[] extent = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            extent[axis] = Math.abs(secondary[axis]) * radii[0] + Math.abs(tertiary[axis]) * radii[2]
                    + Math.abs(primary[axis]) * (settings.sphere() ? radii[1] : halfLength);
        }
        double offset = settings.twoBlockCenter() ? 0.0 : 0.5;
        center = new double[3];
        if (settings.anchor() != null) {
            for (int axis = 0; axis < 3; axis++) {
                center[axis] = Math.floor(settings.anchor()[axis]) + offset;
            }
        } else {
            center[0] = Math.floor((selection[0] + selection[3] + 1) / 2.0) + offset;
            center[2] = Math.floor((selection[2] + selection[5] + 1) / 2.0) + offset;
            center[1] = selection[4] + 2 + Math.ceil(extent[1]) + offset;
            if (center[1] + extent[1] >= worldTop) {
                center[0] = selection[3] + 2 + Math.ceil(extent[0]) + offset;
                center[1] = Math.floor((selection[1] + selection[4] + 1) / 2.0) + offset;
            }
        }
        box = new int[]{
                (int) Math.floor(center[0] - extent[0]) - 1,
                (int) Math.floor(center[1] - extent[1]) - 1,
                (int) Math.floor(center[2] - extent[2]) - 1,
                (int) Math.ceil(center[0] + extent[0]),
                (int) Math.ceil(center[1] + extent[1]),
                (int) Math.ceil(center[2] + extent[2])
        };
    }

    /** Caja donde se construye la figura ({@code minX…maxZ}). */
    public int[] box() {
        return box.clone();
    }

    public long boxVolume() {
        return (long) (box[3] - box[0] + 1) * (box[4] - box[1] + 1) * (box[5] - box[2] + 1);
    }

    public double[] center() {
        return center.clone();
    }

    public double depth() {
        return depth;
    }

    /** Radios en los ejes {@code -j}, {@code -i} y el tercero (en el cilindro el del eje es 0). */
    public double[] radii() {
        return radii.clone();
    }

    public double halfLength() {
        return halfLength;
    }

    private double meanRadius() {
        return settings.sphere() ? (radii[0] + radii[1] + radii[2]) / 3.0 : (radii[0] + radii[2]) / 2.0;
    }

    /** La figura sobre una foto que cubre la selección y la caja. */
    public DeformField field(Volume volume) {
        return new DeformField() {
            @Override
            public int material(int x, int y, int z) {
                if (x < box[0] || y < box[1] || z < box[2] || x > box[3] || y > box[4] || z > box[5]
                        || !volume.contains(x, y, z)) {
                    return KEEP;
                }
                return sample(volume, x + 0.5, y + 0.5, z + 0.5);
            }

            @Override
            public boolean solid(double x, double y, double z) {
                return sample(volume, x, y, z) > 0;
            }
        };
    }

    /**
     * Coordenadas del mapa {@code {EW, UD, NS}} en [0, 1] que corresponden a un punto, o {@code null} fuera de la
     * figura. UD por debajo de 0 es el interior: con relleno se pega a 0, sin él cuenta como fuera.
     */
    double[] mapCoordinates(double x, double y, double z) {
        double dx = x - center[0];
        double dy = y - center[1];
        double dz = z - center[2];
        double along = dx * primary[0] + dy * primary[1] + dz * primary[2];
        double first = dx * secondary[0] + dy * secondary[1] + dz * secondary[2];
        double second = dx * tertiary[0] + dy * tertiary[1] + dz * tertiary[2];
        double meanRadius = meanRadius();
        double ew;
        double ns;
        double normalized;
        if (settings.sphere()) {
            double qs = first / radii[0];
            double qp = along / radii[1];
            double qt = second / radii[2];
            normalized = Math.sqrt(qs * qs + qp * qp + qt * qt);
            if (normalized < 1.0E-9) {
                qp = 1.0;
                normalized = 1.0E-9;
            }
            double vs = qs / Math.max(normalized, 1.0E-9);
            double vp = Math.clamp(qp / Math.max(normalized, 1.0E-9), -1.0, 1.0);
            double vt = qt / Math.max(normalized, 1.0E-9);
            double longitude = Math.atan2(vt, vs);
            if (settings.laea()) {
                double disk = Math.sqrt(Math.max(0.0, (1.0 - vp) / 2.0));
                double[] square = diskToSquare(disk, longitude);
                ew = (square[0] + 1.0) / 2.0;
                ns = (square[1] + 1.0) / 2.0;
            } else {
                ew = (longitude + Math.PI) / (2.0 * Math.PI);
                double latitude = Math.asin(vp);
                double mercator = Math.log(Math.tan(Math.PI / 4.0 + latitude / 2.0));
                if (Double.isNaN(mercator)) {
                    mercator = latitude > 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
                }
                ns = Math.clamp(0.5 + mercator / (2.0 * Math.PI) * lengths[0] / lengths[2], 0.0, 1.0);
            }
        } else {
            double qs = first / radii[0];
            double qt = second / radii[2];
            normalized = Math.sqrt(qs * qs + qt * qt);
            ew = (Math.atan2(qt, qs) + Math.PI) / (2.0 * Math.PI);
            ns = (along / halfLength + 1.0) / 2.0;
            if (ns < 0.0 || ns >= 1.0) {
                return null;
            }
        }
        double ud = (normalized * meanRadius - (meanRadius - depth)) / depth;
        if (ud >= 1.0) {
            return null;
        }
        if (ud < 0.0) {
            if (!settings.fill()) {
                return null;
            }
            ud = 0.0;
        }
        return new double[]{Math.clamp(ew, 0.0, 1.0 - 1.0E-9), ud, Math.clamp(ns, 0.0, 1.0 - 1.0E-9)};
    }

    private int sample(Volume volume, double x, double y, double z) {
        double[] map = mapCoordinates(x, y, z);
        if (map == null) {
            return settings.keepSurroundings() ? DeformField.KEEP : DeformField.AIR;
        }
        double[] input = new double[3];
        for (int slot = 0; slot < 3; slot++) {
            int axis = settings.axisMap().axes()[slot];
            double fraction = settings.axisMap().flipped()[slot] ? 1.0 - map[slot] : map[slot];
            if (fraction >= 1.0) {
                fraction = 1.0 - 1.0E-9;
            }
            double start = selection[axis] + (slot == 0 && settings.seam() ? 0.5 : 0.0);
            input[axis] = start + fraction * lengths[slot];
        }
        int material = volume.sourceAt(input[0], input[1], input[2]);
        if (material == 0 && settings.keepSurroundings() && !volume.selected(
                (int) Math.floor(input[0]), (int) Math.floor(input[1]), (int) Math.floor(input[2]))) {
            return DeformField.KEEP;
        }
        return material;
    }

    /** Inversa de la proyección concéntrica de Shirley y Chiu: del disco unidad al cuadrado [-1, 1]². */
    static double[] diskToSquare(double radius, double angle) {
        double phi = angle;
        if (phi < -Math.PI / 4.0) {
            phi += 2.0 * Math.PI;
        }
        double quarter = Math.PI / 4.0;
        if (phi < quarter) {
            return new double[]{radius, radius * phi / quarter};
        }
        if (phi < 3.0 * quarter) {
            return new double[]{-radius * (phi - Math.PI / 2.0) / quarter, radius};
        }
        if (phi < 5.0 * quarter) {
            return new double[]{-radius, -radius * (phi - Math.PI) / quarter};
        }
        return new double[]{radius * (phi - 3.0 * Math.PI / 2.0) / quarter, -radius};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }
}
