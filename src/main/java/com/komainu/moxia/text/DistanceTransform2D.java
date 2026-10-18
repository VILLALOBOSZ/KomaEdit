package com.komainu.moxia.text;

/**
 * Transformada de distancia euclídea exacta en 2D (Felzenszwalb y Huttenlocher): en cada celda queda el cuadrado
 * de la distancia a la celda de valor 0 más cercana. Lineal en el número de celdas.
 */
final class DistanceTransform2D {
    private DistanceTransform2D() {
    }

    /** {@code values}: 0 en las celdas de origen e infinito en las demás; se sobrescribe con las distancias². */
    static void squared(double[] values, int width, int height) {
        int longest = Math.max(width, height);
        double[] line = new double[longest];
        double[] output = new double[longest];
        int[] hulls = new int[longest];
        double[] boundaries = new double[longest + 1];
        for (int column = 0; column < width; column++) {
            for (int row = 0; row < height; row++) {
                line[row] = values[row * width + column];
            }
            pass(line, height, output, hulls, boundaries);
            for (int row = 0; row < height; row++) {
                values[row * width + column] = output[row];
            }
        }
        for (int row = 0; row < height; row++) {
            System.arraycopy(values, row * width, line, 0, width);
            pass(line, width, output, hulls, boundaries);
            System.arraycopy(output, 0, values, row * width, width);
        }
    }

    private static void pass(double[] f, int length, double[] output, int[] hulls, double[] boundaries) {
        int count = -1;
        for (int q = 0; q < length; q++) {
            if (f[q] == Double.POSITIVE_INFINITY) {
                continue;
            }
            while (count >= 0) {
                int p = hulls[count];
                double s = ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * q - 2.0 * p);
                if (s > boundaries[count]) {
                    count++;
                    hulls[count] = q;
                    boundaries[count] = s;
                    boundaries[count + 1] = Double.POSITIVE_INFINITY;
                    break;
                }
                count--;
            }
            if (count < 0) {
                count = 0;
                hulls[0] = q;
                boundaries[0] = Double.NEGATIVE_INFINITY;
                boundaries[1] = Double.POSITIVE_INFINITY;
            }
        }
        if (count < 0) {
            for (int q = 0; q < length; q++) {
                output[q] = Double.POSITIVE_INFINITY;
            }
            return;
        }
        int hull = 0;
        for (int q = 0; q < length; q++) {
            while (boundaries[hull + 1] < q) {
                hull++;
            }
            double offset = q - hulls[hull];
            output[q] = offset * offset + f[hulls[hull]];
        }
    }
}
