package com.komainu.moxia.symmetry;

/**
 * Una de las copias de la simetría. Primero refleja en X y en Z, después gira {@code turn} partes de {@code arms} de
 * vuelta alrededor del eje vertical y, aparte, refleja en Y. El giro va de este a sur (el de las agujas del reloj visto
 * desde arriba), el mismo sentido que la propiedad {@code rotation} de carteles y estandartes.
 */
public record SymmetryTransform(boolean mirrorX, boolean mirrorZ, boolean mirrorY, int turn, int arms) {
    public static final SymmetryTransform IDENTITY = new SymmetryTransform(false, false, false, 0, 1);

    public SymmetryTransform {
        if (arms < 1 || turn < 0 || turn >= arms) {
            throw new IllegalArgumentException("Giro de simetría no válido: " + turn + "/" + arms);
        }
    }

    public boolean identity() {
        return !mirrorX && !mirrorZ && !mirrorY && turn == 0;
    }

    /** Ángulo del giro en radianes. */
    public double angle() {
        return 2.0 * Math.PI * turn / arms;
    }

    /** ¿Es el giro un número exacto de cuartos de vuelta? Entonces las posiciones no se redondean. */
    public boolean exactQuarter() {
        return 4 * turn % arms == 0;
    }

    /** Cuartos de vuelta del giro, redondeados al más cercano: es lo que puede girar un bloque con {@code facing}. */
    public int quarterTurns() {
        return Math.floorMod((int) Math.round(4.0 * turn / arms), 4);
    }

    /** ¿Invierte izquierda y derecha? Pasa con un número impar de espejos horizontales. */
    public boolean horizontalFlip() {
        return mirrorX ^ mirrorZ;
    }

    /** Lleva una dirección horizontal entera ({@code dx}, {@code dz}) con los espejos y los cuartos de vuelta. */
    public int[] direction(int dx, int dz) {
        int x = mirrorX ? -dx : dx;
        int z = mirrorZ ? -dz : dz;
        for (int step = 0; step < quarterTurns(); step++) {
            int previous = x;
            x = -z;
            z = previous;
        }
        return new int[]{x, z};
    }

    /** Lleva un vector horizontal con los espejos y el ángulo exacto (sin redondear a cuartos de vuelta). */
    public double[] rotate(double dx, double dz) {
        double x = mirrorX ? -dx : dx;
        double z = mirrorZ ? -dz : dz;
        double angle = angle();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[]{x * cos - z * sin, x * sin + z * cos};
    }
}
