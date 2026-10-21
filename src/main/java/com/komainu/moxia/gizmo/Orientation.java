package com.komainu.moxia.gizmo;

import com.komainu.moxia.symmetry.SymmetryTransform;

/**
 * Orientación de un objeto en la rejilla de bloques: una de las 48 matrices enteras que llevan cada eje a otro eje
 * (los 24 giros de un cubo y sus espejos). Es lo único que puede girar un objeto de bloques sin redondear nada:
 * cada posición entera cae en otra posición entera y girar cuatro veces deja el objeto como estaba.
 *
 * <p>Los giros de un cuarto de vuelta ({@link #turn}) siguen esta regla: en Y, de este a sur (el de las agujas del
 * reloj visto desde arriba, el de {@code BlockRotation.CLOCKWISE_90} y el de la simetría); en X, de arriba a sur; en
 * Z, de este a arriba. Las caras se numeran como en la vista previa: oeste, este, abajo, arriba, norte y sur
 * ({@code eje * 2 + (positiva ? 1 : 0)}).</p>
 *
 * <p>Lógica pura: se prueba sin Minecraft.</p>
 */
public final class Orientation {
    public static final int COUNT = 48;
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;
    private static final int[][] PERMUTATIONS = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
    private static final Orientation[] ALL = new Orientation[COUNT];

    static {
        for (int index = 0; index < COUNT; index++) {
            ALL[index] = new Orientation(index);
        }
    }

    public static final Orientation IDENTITY = ALL[0];

    private final int index;
    /** Eje del mundo al que va cada eje local. */
    private final int[] axis = new int[3];
    /** Signo con el que llega (+1 o -1). */
    private final int[] sign = new int[3];

    private Orientation(int index) {
        this.index = index;
        int[] permutation = PERMUTATIONS[index >> 3];
        for (int local = 0; local < 3; local++) {
            axis[local] = permutation[local];
            sign[local] = (index >> local & 1) == 0 ? 1 : -1;
        }
    }

    public static Orientation of(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("Orientación fuera de rango: " + index);
        }
        return ALL[index];
    }

    public int index() {
        return index;
    }

    /** Eje del mundo ({@link #X}, {@link #Y} o {@link #Z}) al que va el eje local {@code local}. */
    public int axisOf(int local) {
        return axis[local];
    }

    /** Coordenada X del mundo del vector local ({@code x}, {@code y}, {@code z}). */
    public int x(int x, int y, int z) {
        return component(0, x, y, z);
    }

    public int y(int x, int y, int z) {
        return component(1, x, y, z);
    }

    public int z(int x, int y, int z) {
        return component(2, x, y, z);
    }

    /** El vector local llevado al mundo, como {x, y, z}. */
    public int[] apply(int x, int y, int z) {
        return new int[]{x(x, y, z), y(x, y, z), z(x, y, z)};
    }

    /** Lo mismo con decimales (para el gizmo y los rayos). */
    public double[] apply(double x, double y, double z) {
        double[] local = {x, y, z};
        double[] world = new double[3];
        for (int index = 0; index < 3; index++) {
            world[axis[index]] += sign[index] * local[index];
        }
        return world;
    }

    private int component(int world, int x, int y, int z) {
        if (axis[0] == world) {
            return sign[0] * x;
        }
        if (axis[1] == world) {
            return sign[1] * y;
        }
        return sign[2] * z;
    }

    /** Primero esta orientación y después {@code next}. */
    public Orientation then(Orientation next) {
        int[] composedAxis = new int[3];
        int[] composedSign = new int[3];
        for (int local = 0; local < 3; local++) {
            composedAxis[local] = next.axis[axis[local]];
            composedSign[local] = next.sign[axis[local]] * sign[local];
        }
        return find(composedAxis, composedSign);
    }

    /** La que deshace esta: {@code o.then(o.inverse())} es la identidad. */
    public Orientation inverse() {
        int[] inverseAxis = new int[3];
        int[] inverseSign = new int[3];
        for (int local = 0; local < 3; local++) {
            inverseAxis[axis[local]] = local;
            inverseSign[axis[local]] = sign[local];
        }
        return find(inverseAxis, inverseSign);
    }

    private static Orientation find(int[] axes, int[] signs) {
        for (int permutation = 0; permutation < PERMUTATIONS.length; permutation++) {
            int[] candidate = PERMUTATIONS[permutation];
            if (candidate[0] == axes[0] && candidate[1] == axes[1] && candidate[2] == axes[2]) {
                int bits = (signs[0] < 0 ? 1 : 0) | (signs[1] < 0 ? 2 : 0) | (signs[2] < 0 ? 4 : 0);
                return ALL[permutation << 3 | bits];
            }
        }
        throw new IllegalStateException("Los ejes no forman una orientación");
    }

    /**
     * {@code quarterTurns} cuartos de vuelta alrededor del eje {@code worldAxis} del mundo (negativos, al revés).
     * Ver el sentido en la clase.
     */
    public static Orientation turn(int worldAxis, int quarterTurns) {
        Orientation step = switch (worldAxis) {
            // y → z, z → -y
            case X -> find(new int[]{0, 2, 1}, new int[]{1, 1, -1});
            // x → z, z → -x
            case Y -> find(new int[]{2, 1, 0}, new int[]{1, 1, -1});
            // x → y, y → -x
            case Z -> find(new int[]{1, 0, 2}, new int[]{1, -1, 1});
            default -> throw new IllegalArgumentException("Eje desconocido: " + worldAxis);
        };
        Orientation result = IDENTITY;
        for (int turn = 0; turn < Math.floorMod(quarterTurns, 4); turn++) {
            result = result.then(step);
        }
        return result;
    }

    /** Espejo que invierte el eje {@code worldAxis}. */
    public static Orientation mirror(int worldAxis) {
        int[] signs = {1, 1, 1};
        signs[worldAxis] = -1;
        return find(new int[]{0, 1, 2}, signs);
    }

    /** ¿Refleja (cambia izquierda por derecha)? Pasa con un número impar de espejos. */
    public boolean mirrored() {
        int parity = (axis[0] == 0 ? 0 : 1) + (axis[1] == 1 ? 0 : 1) + (axis[2] == 2 ? 0 : 1);
        // Una permutación con dos ejes fuera de sitio es un intercambio (impar); con tres, un ciclo (par).
        boolean oddPermutation = parity == 2;
        return oddPermutation ^ (sign[0] * sign[1] * sign[2] < 0);
    }

    /** ¿Sigue lo de arriba arriba o abajo? Si no, el objeto está tumbado. */
    public boolean upright() {
        return axis[1] == 1;
    }

    /** ¿Lo de arriba queda abajo (sin tumbarse)? */
    public boolean upsideDown() {
        return axis[1] == 1 && sign[1] < 0;
    }

    /** La cara local {@code face} ({@code eje * 2 + positiva}) vista en el mundo. */
    public int face(int face) {
        int local = face >> 1;
        boolean positive = (face & 1) != 0;
        return axis[local] << 1 | (positive ^ sign[local] < 0 ? 1 : 0);
    }

    /** Las seis caras de una máscara (bit {@code cara}) llevadas al mundo. */
    public int faces(int mask) {
        int result = 0;
        for (int face = 0; face < 6; face++) {
            if ((mask & 1 << face) != 0) {
                result |= 1 << face(face);
            }
        }
        return result;
    }

    /**
     * La misma orientación como transformación de la simetría (espejo en X, cuartos de vuelta en Y y espejo en Y),
     * para girar los estados de bloque con {@code StateTransform}. Solo existe si el objeto no está tumbado.
     */
    public SymmetryTransform symmetry() {
        if (!upright()) {
            throw new IllegalStateException("Un objeto tumbado no se describe con la simetría");
        }
        int[] east = {x(1, 0, 0), z(1, 0, 0)};
        int[] south = {x(0, 0, 1), z(0, 0, 1)};
        for (int mirror = 0; mirror < 2; mirror++) {
            for (int turn = 0; turn < 4; turn++) {
                SymmetryTransform candidate = new SymmetryTransform(mirror == 1, false, sign[1] < 0, turn, 4);
                int[] turnedEast = candidate.direction(1, 0);
                int[] turnedSouth = candidate.direction(0, 1);
                if (turnedEast[0] == east[0] && turnedEast[1] == east[1] && turnedSouth[0] == south[0]
                        && turnedSouth[1] == south[1]) {
                    return candidate;
                }
            }
        }
        throw new IllegalStateException("Orientación sin equivalente en la simetría: " + index);
    }

    /**
     * Cuartos de vuelta en Y desde la orientación de partida (de 0 a 3), contando los espejos aparte. Un volteo y un
     * giro de 180° son otro volteo, así que volteado solo hay 0 (en X o en Z) o 1 (en diagonal): un Ctrl+F se lee
     * «volteado», no «180° volteado». En un objeto tumbado es el giro de su frente (el eje local Z) visto desde arriba.
     */
    public int yawQuarters() {
        if (upright()) {
            SymmetryTransform transform = symmetry();
            return transform.mirrorX() ? transform.turn() % 2 : transform.turn();
        }
        // Tumbado: hacia dónde mira ahora lo que antes miraba al sur.
        int worldX = x(0, 0, 1);
        int worldZ = z(0, 0, 1);
        if (worldX == 0 && worldZ == 0) {
            worldX = x(1, 0, 0);
            worldZ = z(1, 0, 0);
            return worldX > 0 ? 0 : worldZ > 0 ? 1 : worldX < 0 ? 2 : 3;
        }
        return worldZ > 0 ? 0 : worldX < 0 ? 1 : worldZ < 0 ? 2 : 3;
    }

    /** Cara del mundo hacia la que apunta ahora lo que era arriba ({@link #face}: 3 = arriba). */
    public int upFace() {
        return face(3);
    }

    @Override
    public String toString() {
        return "Orientation[" + index + "]";
    }
}
