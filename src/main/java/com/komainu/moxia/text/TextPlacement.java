package com.komainu.moxia.text;

/**
 * Dónde y cómo queda el plano del texto en el mundo: el bloque de anclaje y tres ejes de bloque (derecha, arriba y
 * grosor). El bloque {@code (u, v, d)} del texto está en {@code ancla + u·derecha + v·arriba + d·grosor}.
 *
 * <p>Según la mirada: de pie, el texto mira al jugador y el grosor crece hacia él (así, apoyado en una pared, no
 * se mete en ella); mirando al suelo, se tumba y se lee desde donde está el jugador; mirando al techo, cuelga de él y
 * se lee mirando hacia arriba.</p>
 */
public record TextPlacement(int[] anchor, int[] right, int[] up, int[] out) {
    public TextPlacement {
        anchor = anchor.clone();
        right = right.clone();
        up = up.clone();
        out = out.clone();
        if (dot(right, up) != 0 || dot(right, out) != 0 || dot(up, out) != 0) {
            throw new IllegalArgumentException("Los ejes del texto tienen que ser perpendiculares");
        }
    }

    public enum Mode {
        /** De pie, mirando al jugador. */
        WALL,
        /** Tumbado en el suelo. */
        FLOOR,
        /** Colgado del techo. */
        CEILING
    }

    /**
     * @param facing dirección horizontal a la que mira el jugador ({@code {dx, 0, dz}}, un eje)
     */
    public static TextPlacement of(Mode mode, int[] anchor, int[] facing) {
        int[] worldUp = {0, 1, 0};
        int[] right = cross(facing, worldUp);
        return switch (mode) {
            case WALL -> new TextPlacement(anchor, right, worldUp, negate(facing));
            case FLOOR -> new TextPlacement(anchor, right, facing, worldUp);
            case CEILING -> new TextPlacement(anchor, right, negate(facing), negate(worldUp));
        };
    }

    /**
     * Modo según la cara pulsada (su componente vertical: 1 arriba, -1 abajo, 0 una pared): sobre el suelo, tumbado
     * en él; bajo un techo, colgado de él; en una pared, pegado a ella.
     */
    public static Mode modeForFace(int offsetY) {
        return offsetY > 0 ? Mode.FLOOR : offsetY < 0 ? Mode.CEILING : Mode.WALL;
    }

    /** Modo según la inclinación de la mirada (positiva hacia abajo, como en Minecraft). */
    public static Mode modeForPitch(double pitch) {
        if (pitch > 50.0) {
            return Mode.FLOOR;
        }
        if (pitch < -50.0) {
            return Mode.CEILING;
        }
        return Mode.WALL;
    }

    /** Posición en el mundo de un bloque del texto. */
    public int[] world(int u, int v, int d) {
        return new int[]{
                anchor[0] + u * right[0] + v * up[0] + d * out[0],
                anchor[1] + u * right[1] + v * up[1] + d * out[1],
                anchor[2] + u * right[2] + v * up[2] + d * out[2]
        };
    }

    /** Bloque del texto ({@code u, v, d}) que corresponde a un bloque del mundo. */
    public int[] local(int x, int y, int z) {
        int dx = x - anchor[0];
        int dy = y - anchor[1];
        int dz = z - anchor[2];
        return new int[]{
                dx * right[0] + dy * right[1] + dz * right[2],
                dx * up[0] + dy * up[1] + dz * up[2],
                dx * out[0] + dy * out[1] + dz * out[2]
        };
    }

    /**
     * Punto continuo del texto que corresponde a un punto del mundo. El bloque {@code u} del texto ocupa
     * {@code [u, u + 1)}, igual que un bloque del mundo, aunque el eje apunte hacia los negativos.
     */
    public double[] local(double x, double y, double z) {
        double dx = x - (anchor[0] + 0.5);
        double dy = y - (anchor[1] + 0.5);
        double dz = z - (anchor[2] + 0.5);
        return new double[]{
                dx * right[0] + dy * right[1] + dz * right[2] + 0.5,
                dx * up[0] + dy * up[1] + dz * up[2] + 0.5,
                dx * out[0] + dy * out[1] + dz * out[2] + 0.5
        };
    }

    /** Caja del mundo ({@code minX, minY, minZ, maxX, maxY, maxZ}) de un rango de bloques del texto. */
    public int[] box(int minU, int minV, int maxU, int maxV, int depth) {
        int[] box = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int u : new int[]{minU, maxU}) {
            for (int v : new int[]{minV, maxV}) {
                for (int d : new int[]{0, depth - 1}) {
                    int[] point = world(u, v, d);
                    for (int axis = 0; axis < 3; axis++) {
                        box[axis] = Math.min(box[axis], point[axis]);
                        box[axis + 3] = Math.max(box[axis + 3], point[axis]);
                    }
                }
            }
        }
        return box;
    }

    /** El mismo plano con el ancla movida para que el bloque {@code (u, v)} del texto caiga en el ancla actual. */
    public TextPlacement shifted(int u, int v) {
        int[] moved = {
                anchor[0] - u * right[0] - v * up[0],
                anchor[1] - u * right[1] - v * up[1],
                anchor[2] - u * right[2] - v * up[2]
        };
        return new TextPlacement(moved, right, up, out);
    }

    private static int dot(int[] a, int[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static int[] cross(int[] a, int[] b) {
        return new int[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static int[] negate(int[] a) {
        return new int[]{-a[0], -a[1], -a[2]};
    }
}
