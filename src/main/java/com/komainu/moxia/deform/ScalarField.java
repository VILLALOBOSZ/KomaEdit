package com.komainu.moxia.deform;

/**
 * Valor continuo por bloque sobre la rejilla de un {@link Volume} (densidad, distancia con signo…), medido en el
 * centro de cada bloque. Se puede desenfocar y leer en cualquier punto con interpolación trilineal, que es lo que
 * usan las losas y escaleras para medir la ocupación de cada octante.
 */
public final class ScalarField {
    private final Volume volume;
    final float[] values;

    ScalarField(Volume volume, float[] values) {
        if (values.length != volume.size()) {
            throw new IllegalArgumentException("El campo no coincide con la rejilla");
        }
        this.volume = volume;
        this.values = values;
    }

    static ScalarField of(Volume volume, boolean[] occupancy) {
        float[] values = new float[occupancy.length];
        for (int index = 0; index < values.length; index++) {
            values[index] = occupancy[index] ? 1.0F : 0.0F;
        }
        return new ScalarField(volume, values);
    }

    float at(int x, int y, int z) {
        int cx = Math.clamp(x, volume.minimumX, volume.minimumX + volume.sizeX - 1);
        int cy = Math.clamp(y, volume.minimumY, volume.minimumY + volume.sizeY - 1);
        int cz = Math.clamp(z, volume.minimumZ, volume.minimumZ + volume.sizeZ - 1);
        return values[volume.index(cx, cy, cz)];
    }

    /** Interpolación trilineal en coordenadas de mundo (el centro del bloque {@code x} está en {@code x + 0.5}). */
    double sample(double x, double y, double z) {
        double u = x - 0.5;
        double v = y - 0.5;
        double w = z - 0.5;
        int x0 = (int) Math.floor(u);
        int y0 = (int) Math.floor(v);
        int z0 = (int) Math.floor(w);
        double fx = u - x0;
        double fy = v - y0;
        double fz = w - z0;
        double c00 = lerp(at(x0, y0, z0), at(x0 + 1, y0, z0), fx);
        double c10 = lerp(at(x0, y0 + 1, z0), at(x0 + 1, y0 + 1, z0), fx);
        double c01 = lerp(at(x0, y0, z0 + 1), at(x0 + 1, y0, z0 + 1), fx);
        double c11 = lerp(at(x0, y0 + 1, z0 + 1), at(x0 + 1, y0 + 1, z0 + 1), fx);
        return lerp(lerp(c00, c10, fy), lerp(c01, c11, fy), fz);
    }

    /**
     * Media en una caja de radios {@code (rx, ry, rz)} bloques, separable y con la ventana recortada en los bordes
     * de la captura. Dos pasadas seguidas se parecen a un desenfoque gaussiano y no dejan marcas de la caja.
     */
    void boxBlur(int radiusX, int radiusY, int radiusZ) {
        int sizeX = volume.sizeX;
        int sizeY = volume.sizeY;
        int sizeZ = volume.sizeZ;
        double[] line = new double[Math.max(sizeX, Math.max(sizeY, sizeZ))];
        double[] prefix = new double[line.length + 1];
        if (radiusX > 0) {
            for (int row = 0; row < sizeY * sizeZ; row++) {
                blurLine(row * sizeX, 1, sizeX, radiusX, line, prefix);
            }
        }
        if (radiusZ > 0) {
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    blurLine(y * sizeZ * sizeX + x, sizeX, sizeZ, radiusZ, line, prefix);
                }
            }
        }
        if (radiusY > 0) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    blurLine(z * sizeX + x, sizeX * sizeZ, sizeY, radiusY, line, prefix);
                }
            }
        }
    }

    private void blurLine(int start, int stride, int length, int radius, double[] line, double[] prefix) {
        prefix[0] = 0.0;
        for (int position = 0; position < length; position++) {
            line[position] = values[start + position * stride];
            prefix[position + 1] = prefix[position] + line[position];
        }
        for (int position = 0; position < length; position++) {
            int low = Math.max(0, position - radius);
            int high = Math.min(length - 1, position + radius);
            values[start + position * stride] = (float) ((prefix[high + 1] - prefix[low]) / (high - low + 1));
        }
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
