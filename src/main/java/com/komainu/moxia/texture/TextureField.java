package com.komainu.moxia.texture;

/**
 * Un modo de textura: da a cada bloque a texturizar un valor de 0 (primer bloque de la paleta) a 1 (último), o
 * {@code NaN} para dejarlo como está. Las texturas se pueden combinar ({@link TextureFields}) como en
 * {@code //eztexture advanced} de ezEdits.
 */
public interface TextureField {
    double[] evaluate(TextureContext context);

    /** Bloques que hay que leer alrededor de la selección para que la textura vea a sus vecinos. */
    default int padding() {
        return 1;
    }

    /** Si necesita los niveles de luz del mundo (captura más lenta). */
    default boolean needsLight() {
        return false;
    }

    /**
     * Brillo y contraste al estilo de ezEdits: más brillo acerca al principio de la paleta y el contraste separa los
     * valores del centro.
     */
    static double adjust(double value, double brightness, double contrast) {
        if (Double.isNaN(value)) {
            return value;
        }
        return Math.clamp((value - 0.5) * (1.0 + contrast) + 0.5 - brightness, 0.0, 1.0);
    }

    static double[] adjust(double[] values, double brightness, double contrast) {
        if (brightness == 0.0 && contrast == 0.0) {
            return values;
        }
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = adjust(values[index], brightness, contrast);
        }
        return result;
    }

    /**
     * Estira los valores para que el 2 % más bajo y el 2 % más alto lleguen a los extremos: así la paleta entera
     * aparece aunque la medida se concentre en un tramo estrecho.
     */
    static double[] stretch(double[] values) {
        int count = 0;
        for (double value : values) {
            if (!Double.isNaN(value)) {
                count++;
            }
        }
        if (count < 2) {
            return values;
        }
        double[] sorted = new double[count];
        int position = 0;
        for (double value : values) {
            if (!Double.isNaN(value)) {
                sorted[position++] = value;
            }
        }
        java.util.Arrays.sort(sorted);
        double low = sorted[(int) Math.floor((count - 1) * 0.02)];
        double high = sorted[(int) Math.ceil((count - 1) * 0.98)];
        if (high - low < 1.0E-9) {
            return values;
        }
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            double value = values[index];
            result[index] = Double.isNaN(value) ? value : Math.clamp((value - low) / (high - low), 0.0, 1.0);
        }
        return result;
    }
}
