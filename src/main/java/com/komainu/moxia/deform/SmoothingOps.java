package com.komainu.moxia.deform;

/**
 * Suavizado de volumen ({@code /komaedit smooth}, {@code inflate}, {@code deflate} y {@code smoothblocks}).
 *
 * <ul>
 *     <li><b>smooth</b>: la ocupación se convierte en densidad (1 lleno, 0 vacío), se desenfoca con radios por eje
 *     y se vuelve a cortar; cada iteración repite las dos cosas. El sesgo mueve el corte: positivo engorda,
 *     negativo adelgaza.</li>
 *     <li><b>inflate</b>/<b>deflate</b>: crece o encoge exactamente un elipsoide de radios dados (distancia
 *     euclídea ponderada por eje).</li>
 * </ul>
 * Lo que aparece toma el material del bloque ocupado más cercano y todo se escribe solo dentro de la selección.
 */
public final class SmoothingOps {
    private SmoothingOps() {
    }

    /** Radios por eje ({@code x, y, z}) desde {@code r} o {@code rx,ry,rz} (este-oeste, arriba-abajo, norte-sur). */
    public static double[] parseRadii(String input, double maximum) {
        String[] parts = input.trim().split(",");
        if (parts.length != 1 && parts.length != 3) {
            throw new IllegalArgumentException("Los radios son un número o tres separados por comas (EW,UD,NS)");
        }
        double[] radii = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            String part = parts[parts.length == 1 ? 0 : axis].trim();
            try {
                radii[axis] = Double.parseDouble(part);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Radio no válido: " + part);
            }
            if (!(radii[axis] >= 0.0 && radii[axis] <= maximum)) {
                throw new IllegalArgumentException("Cada radio va de 0 a " + (int) maximum);
            }
        }
        if (radii[0] + radii[1] + radii[2] <= 0.0) {
            throw new IllegalArgumentException("Al menos un radio debe ser mayor que 0");
        }
        return radii;
    }

    /** Corte de la densidad según el sesgo: 0 → 0,5; 1 → casi todo lleno; −1 → casi todo vacío. */
    static float threshold(double bias) {
        double clamped = Math.clamp(bias, -1.0, 1.0);
        return (float) Math.clamp(0.5 - 0.5 * clamped, 0.02, 0.98);
    }

    public static DeformField smooth(Volume volume, double[] radii, int iterations, double bias,
                                     Runnable checkCancelled) {
        if (iterations < 1 || iterations > 32) {
            throw new IllegalArgumentException("Las iteraciones van de 1 a 32");
        }
        boolean[] occupancy = volume.occupancy();
        ScalarField density = ScalarField.of(volume, occupancy);
        float cut = threshold(bias);
        int rx = (int) Math.round(radii[0]);
        int ry = (int) Math.round(radii[1]);
        int rz = (int) Math.round(radii[2]);
        for (int iteration = 0; iteration < iterations; iteration++) {
            checkCancelled.run();
            density.boxBlur(rx, ry, rz);
            density.boxBlur(rx, ry, rz);
            if (iteration + 1 < iterations) {
                for (int index = 0; index < density.values.length; index++) {
                    density.values[index] = density.values[index] > cut ? 1.0F : 0.0F;
                }
            }
        }
        boolean[] result = new boolean[occupancy.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = density.values[index] > cut;
        }
        return field(volume, occupancy, result, (x, y, z) -> density.sample(x, y, z) > cut);
    }

    public static DeformField inflate(Volume volume, double[] radii, Runnable checkCancelled) {
        boolean[] occupancy = volume.occupancy();
        checkCancelled.run();
        DistanceTransform.Result outside = DistanceTransform.compute(volume, occupancy,
                weight(radii[0]), weight(radii[1]), weight(radii[2]));
        float[] values = new float[occupancy.length];
        boolean[] result = new boolean[occupancy.length];
        for (int index = 0; index < values.length; index++) {
            double distance = Math.sqrt(Math.min(outside.squared()[index], 1.0E6F));
            values[index] = (float) (1.0 - distance);
            result[index] = occupancy[index] || distance <= 1.0 + 1.0E-6;
        }
        ScalarField field = new ScalarField(volume, values);
        return field(volume, occupancy, result, (x, y, z) -> field.sample(x, y, z) >= 0.0);
    }

    public static DeformField deflate(Volume volume, double[] radii, Runnable checkCancelled) {
        boolean[] occupancy = volume.occupancy();
        boolean[] empty = new boolean[occupancy.length];
        for (int index = 0; index < occupancy.length; index++) {
            empty[index] = !occupancy[index];
        }
        checkCancelled.run();
        DistanceTransform.Result inside = DistanceTransform.compute(volume, empty,
                weight(radii[0]), weight(radii[1]), weight(radii[2]));
        float[] values = new float[occupancy.length];
        boolean[] result = new boolean[occupancy.length];
        for (int index = 0; index < values.length; index++) {
            double distance = occupancy[index] ? Math.sqrt(Math.min(inside.squared()[index], 1.0E6F)) : 0.0;
            values[index] = (float) (distance - 1.0);
            result[index] = occupancy[index] && distance > 1.0 + 1.0E-6;
        }
        ScalarField field = new ScalarField(volume, values);
        return field(volume, occupancy, result, (x, y, z) -> field.sample(x, y, z) > 0.0);
    }

    /** Margen de lectura para ver a los vecinos que influyen en la selección. */
    public static int padding(double[] radii, int iterations) {
        double largest = Math.max(radii[0], Math.max(radii[1], radii[2]));
        return (int) Math.min(32, Math.ceil(largest * 2.0 * Math.max(1, iterations)) + 1);
    }

    private static double weight(double radius) {
        return radius <= 1.0E-3 ? 1.0E6 : 1.0 / (radius * radius);
    }

    @FunctionalInterface
    private interface Occupancy {
        boolean at(double x, double y, double z);
    }

    private static DeformField field(Volume volume, boolean[] before, boolean[] after, Occupancy continuous) {
        int[] nearest = DistanceTransform.compute(volume, before, 1.0, 1.0, 1.0).nearest();
        return new DeformField() {
            @Override
            public int material(int x, int y, int z) {
                if (!volume.selected(x, y, z)) {
                    return KEEP;
                }
                int index = volume.index(x, y, z);
                if (!after[index]) {
                    return AIR;
                }
                if (before[index]) {
                    return volume.material[index];
                }
                return nearest[index] >= 0 ? volume.material[nearest[index]] : AIR;
            }

            @Override
            public boolean solid(double x, double y, double z) {
                return continuous.at(x, y, z);
            }
        };
    }
}
