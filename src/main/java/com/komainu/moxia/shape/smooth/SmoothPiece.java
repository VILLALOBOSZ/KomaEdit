package com.komainu.moxia.shape.smooth;

/**
 * Pieza que ocupa un bloque, codificada en un byte para que el plan siga siendo compacto.
 *
 * <p>Cada pieza se describe también como una máscara de 8 octantes (índice {@code x | y << 1 | z << 2}, con
 * x+ hacia el este, y+ hacia arriba y z+ hacia el sur, como en Minecraft). Las orientaciones y formas de las
 * escaleras usan los índices de Minecraft ({@code Direction.getHorizontal()} y el ordinal de
 * {@code StairShape}), así el servidor y el holograma hablan el mismo idioma.</p>
 */
public final class SmoothPiece {
    public static final byte FULL = 0;
    /** Solo como resultado de una elección: la posición queda vacía. Nunca se guarda en un plan. */
    public static final byte EMPTY = -1;
    public static final int FULL_MASK = 0xFF;

    public static final int SOUTH = 0;
    public static final int WEST = 1;
    public static final int NORTH = 2;
    public static final int EAST = 3;

    public static final int STRAIGHT = 0;
    public static final int INNER_LEFT = 1;
    public static final int INNER_RIGHT = 2;
    public static final int OUTER_LEFT = 3;
    public static final int OUTER_RIGHT = 4;

    private static final int SLAB_BOTTOM = 1;
    private static final int SLAB_TOP = 2;
    private static final int STAIRS_BASE = 3;
    private static final int STAIRS_COUNT = 40;
    /** Marca las piezas que ocupan una posición que la figura dejaba vacía. */
    private static final int ADDED = 0x40;
    /** Cuartos levantados de cada escalera, en el orden de Minecraft: 1 = NO, 2 = NE, 4 = SO, 8 = SE. */
    private static final int[] QUARTERS = {12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8};
    private static final int[] MASKS = new int[STAIRS_BASE + STAIRS_COUNT];

    static {
        MASKS[FULL] = FULL_MASK;
        MASKS[SLAB_BOTTOM] = halfMask(false);
        MASKS[SLAB_TOP] = halfMask(true);
        for (int index = 0; index < STAIRS_COUNT; index++) {
            int facing = index & 3;
            boolean top = (index & 4) != 0;
            int shape = index >> 3;
            int mask = halfMask(top);
            int quarters = QUARTERS[shape * 4 + facing];
            int layer = top ? 0 : 1;
            for (int quarter = 0; quarter < 4; quarter++) {
                if ((quarters & 1 << quarter) != 0) {
                    mask |= 1 << octant(quarter & 1, layer, quarter >> 1);
                }
            }
            MASKS[STAIRS_BASE + index] = mask;
        }
    }

    private SmoothPiece() {
    }

    public static byte slab(boolean top) {
        return (byte) (top ? SLAB_TOP : SLAB_BOTTOM);
    }

    public static byte stairs(int facing, boolean top, int shape) {
        if (facing < 0 || facing > 3 || shape < STRAIGHT || shape > OUTER_RIGHT) {
            throw new IllegalArgumentException("Escalera no válida: orientación " + facing + ", forma " + shape);
        }
        return (byte) (STAIRS_BASE + (facing | (top ? 4 : 0) | shape << 3));
    }

    /** La misma pieza, marcada como añadida fuera del volumen original. */
    public static byte added(byte piece) {
        return piece == FULL || piece == EMPTY ? piece : (byte) (piece | ADDED);
    }

    public static boolean isAdded(byte piece) {
        return piece > 0 && (piece & ADDED) != 0;
    }

    public static boolean isValid(byte piece) {
        int base = piece & ~ADDED;
        return piece >= 0 && base < STAIRS_BASE + STAIRS_COUNT && !(base == FULL && piece != FULL);
    }

    public static boolean isSlab(byte piece) {
        int base = base(piece);
        return base == SLAB_BOTTOM || base == SLAB_TOP;
    }

    public static boolean isStairs(byte piece) {
        return base(piece) >= STAIRS_BASE;
    }

    /** Losa superior o escalera colgada (mitad maciza arriba). */
    public static boolean isTop(byte piece) {
        int base = base(piece);
        return base == SLAB_TOP || base >= STAIRS_BASE && ((base - STAIRS_BASE) & 4) != 0;
    }

    public static int facing(byte piece) {
        requireStairs(piece);
        return (base(piece) - STAIRS_BASE) & 3;
    }

    public static int shape(byte piece) {
        requireStairs(piece);
        return (base(piece) - STAIRS_BASE) >> 3;
    }

    public static byte withShape(byte piece, int shape) {
        byte reshaped = stairs(facing(piece), isTop(piece), shape);
        return isAdded(piece) ? added(reshaped) : reshaped;
    }

    public static int mask(byte piece) {
        return piece == EMPTY ? 0 : MASKS[base(piece)];
    }

    public static int octant(int x, int y, int z) {
        return x | y << 1 | z << 2;
    }

    public static int offsetX(int facing) {
        return facing == EAST ? 1 : facing == WEST ? -1 : 0;
    }

    public static int offsetZ(int facing) {
        return facing == SOUTH ? 1 : facing == NORTH ? -1 : 0;
    }

    public static int opposite(int facing) {
        return (facing + 2) & 3;
    }

    /** Equivale a {@code Direction.rotateYCounterclockwise()}: norte → oeste → sur → este → norte. */
    public static int counterclockwise(int facing) {
        return (facing + 3) & 3;
    }

    public static String describe(byte piece) {
        if (piece == EMPTY) {
            return "vacío";
        }
        if (base(piece) == FULL) {
            return "bloque";
        }
        if (isSlab(piece)) {
            return isTop(piece) ? "losa superior" : "losa inferior";
        }
        String[] facings = {"sur", "oeste", "norte", "este"};
        String[] shapes = {"recta", "interior izquierda", "interior derecha", "exterior izquierda",
                "exterior derecha"};
        return "escalera " + facings[facing(piece)] + (isTop(piece) ? " colgada " : " ") + shapes[shape(piece)];
    }

    private static int base(byte piece) {
        return piece == EMPTY ? FULL : piece & ~ADDED;
    }

    private static int halfMask(boolean top) {
        int y = top ? 1 : 0;
        return 1 << octant(0, y, 0) | 1 << octant(1, y, 0) | 1 << octant(0, y, 1) | 1 << octant(1, y, 1);
    }

    private static void requireStairs(byte piece) {
        if (!isStairs(piece)) {
            throw new IllegalArgumentException("La pieza no es una escalera: " + piece);
        }
    }
}
