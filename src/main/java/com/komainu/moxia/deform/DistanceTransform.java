package com.komainu.moxia.deform;

/**
 * Transformada de distancia euclídea exacta (Felzenszwalb y Huttenlocher) con pesos por eje y transformada de
 * rasgos: para cada posición, la distancia al cuadrado hasta la posición «rasgo» más cercana y cuál es.
 *
 * <p>Los pesos permiten radios distintos por eje: con peso {@code 1/r²} una distancia ponderada de 1 es el borde
 * de un elipsoide de semiejes {@code r}. Es separable (una pasada por eje) y cuesta O(n).</p>
 */
public final class DistanceTransform {
    static final float FAR = 1.0E20F;

    private DistanceTransform() {
    }

    /** Distancia ponderada al cuadrado y el índice del rasgo más cercano (-1 si no hay ninguno). */
    public record Result(float[] squared, int[] nearest) {
        public double distance(int index) {
            return Math.sqrt(squared[index]);
        }
    }

    public static Result compute(Volume volume, boolean[] features, double weightX, double weightY, double weightZ) {
        return compute(volume.sizeX, volume.sizeY, volume.sizeZ, features, weightX, weightY, weightZ);
    }

    /** Índice {@code (y * sizeZ + z) * sizeX + x}, como {@link Volume}. */
    public static Result compute(
            int sizeX,
            int sizeY,
            int sizeZ,
            boolean[] features,
            double weightX,
            double weightY,
            double weightZ
    ) {
        int total = sizeX * sizeY * sizeZ;
        if (features.length != total) {
            throw new IllegalArgumentException("La máscara de rasgos no coincide con la rejilla");
        }
        float[] squared = new float[total];
        int[] nearest = new int[total];
        for (int index = 0; index < total; index++) {
            squared[index] = features[index] ? 0.0F : FAR;
            nearest[index] = features[index] ? index : -1;
        }
        Line line = new Line(Math.max(sizeX, Math.max(sizeY, sizeZ)));
        for (int row = 0; row < sizeY * sizeZ; row++) {
            line.run(squared, nearest, row * sizeX, 1, sizeX, weightX);
        }
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                line.run(squared, nearest, y * sizeZ * sizeX + x, sizeX, sizeZ, weightZ);
            }
        }
        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                line.run(squared, nearest, z * sizeX + x, sizeX * sizeZ, sizeY, weightY);
            }
        }
        return new Result(squared, nearest);
    }

    /** Envolvente inferior de parábolas sobre una línea; los arrays se reutilizan entre líneas. */
    private static final class Line {
        private final double[] values;
        private final int[] owners;
        private final int[] vertices;
        private final double[] boundaries;
        private final double[] outValues;
        private final int[] outOwners;

        private Line(int length) {
            values = new double[length];
            owners = new int[length];
            vertices = new int[length];
            boundaries = new double[length + 1];
            outValues = new double[length];
            outOwners = new int[length];
        }

        void run(float[] squared, int[] nearest, int start, int stride, int length, double weight) {
            int finite = 0;
            boolean flat = true;
            for (int position = 0; position < length; position++) {
                int index = start + position * stride;
                values[position] = squared[index];
                owners[position] = nearest[index];
                if (squared[index] < FAR) {
                    finite++;
                }
                if (squared[index] != 0.0F) {
                    flat = false;
                }
            }
            if (finite == 0 || flat) {
                return;
            }
            int hull = -1;
            for (int position = 0; position < length; position++) {
                if (values[position] >= FAR) {
                    continue;
                }
                double crossing = Double.NEGATIVE_INFINITY;
                while (hull >= 0) {
                    crossing = crossing(position, vertices[hull], weight);
                    if (crossing > boundaries[hull]) {
                        break;
                    }
                    hull--;
                }
                hull++;
                vertices[hull] = position;
                boundaries[hull] = hull == 0 ? Double.NEGATIVE_INFINITY : crossing;
                boundaries[hull + 1] = Double.POSITIVE_INFINITY;
            }
            int segment = 0;
            for (int position = 0; position < length; position++) {
                while (boundaries[segment + 1] < position) {
                    segment++;
                }
                int vertex = vertices[segment];
                double offset = position - vertex;
                outValues[position] = weight * offset * offset + values[vertex];
                outOwners[position] = owners[vertex];
            }
            for (int position = 0; position < length; position++) {
                int index = start + position * stride;
                squared[index] = (float) Math.min(outValues[position], FAR);
                nearest[index] = outOwners[position];
            }
        }

        private double crossing(int position, int vertex, double weight) {
            return ((values[position] + weight * position * position) - (values[vertex] + weight * vertex * vertex))
                    / (2.0 * weight * (position - vertex));
        }
    }
}
