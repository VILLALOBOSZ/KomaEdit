package com.komainu.moxia.shape.smooth;

/**
 * Pieza que ocupa un bloque, codificada en un byte para que el plan siga siendo compacto: bloque entero, losa,
 * escalera (40 estados), capa de nieve (1 a 7 capas), muro o panel (16 juegos de conexiones). El bit de signo marca
 * las piezas añadidas donde la figura no tenía bloque; {@link #EMPTY} (-1) queda fuera de los valores de pieza.
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
    private static final int LAYERS_BASE = STAIRS_BASE + STAIRS_COUNT;
    /** Capas de 1 a 7; la de 8 es un bloque entero. */
    public static final int MAX_LAYERS = 7;
    private static final int WALL_BASE = LAYERS_BASE + MAX_LAYERS;
    /** Paneles de vidrio o barrotes: un bit de conexión por lado horizontal ({@code 1 << SOUTH…EAST}). */
    private static final int PANE_BASE = WALL_BASE + 1;
    private static final int PANE_COUNT = 16;
    private static final int COUNT = PANE_BASE + PANE_COUNT;
    /** Fracción de cada octante que ocupa la columna central de un muro (8 de 16 píxeles de ancho). */
    public static final double WALL_FILL = 0.25;
    /**
     * Fracción de cada octante que se supone que ocupa un panel al elegirlo: la lámina central, de 2 de 16 píxeles,
     * de lado a lado. Las conexiones se deciden después, con los vecinos.
     */
    public static final double PANE_FILL = 0.125;
    /** Marca las piezas que ocupan una posición que la figura dejaba vacía (el bit de signo del byte). */
    private static final int ADDED = 0x80;
    private static final int BASE_BITS = 0x7F;
    /** Partes de un panel en dieciseisavos: el poste central y los brazos hacia sur, oeste, norte y este. */
    private static final int[] PANE_POST = {7, 0, 7, 9, 16, 9};
    private static final int[][] PANE_ARMS = {
            {7, 0, 9, 9, 16, 16},
            {0, 0, 7, 7, 16, 9},
            {7, 0, 0, 9, 16, 7},
            {9, 0, 7, 16, 16, 9}
    };
    /** Cuartos levantados de cada escalera, en el orden de Minecraft: 1 = NO, 2 = NE, 4 = SO, 8 = SE. */
    private static final int[] QUARTERS = {12, 5, 3, 10, 14, 13, 7, 11, 13, 7, 11, 14, 8, 4, 1, 2, 4, 1, 2, 8};
    private static final int[] MASKS = new int[COUNT];
    /** Fracción ocupada de cada octante, para medir el encaje de piezas que no llenan octantes enteros. */
    private static final double[][] FILLS = new double[COUNT][8];

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
        for (int layers = 1; layers <= MAX_LAYERS; layers++) {
            double bottom = Math.min(1.0, layers / 4.0);
            double top = Math.max(0.0, (layers - 4) / 4.0);
            for (int octant = 0; octant < 8; octant++) {
                FILLS[LAYERS_BASE + layers - 1][octant] = (octant >> 1 & 1) == 0 ? bottom : top;
            }
            MASKS[LAYERS_BASE + layers - 1] = layers >= 4 ? halfMask(false) : 0;
        }
        java.util.Arrays.fill(FILLS[WALL_BASE], WALL_FILL);
        for (int connections = 0; connections < PANE_COUNT; connections++) {
            FILLS[PANE_BASE + connections] = paneFill(connections);
        }
        for (int base = 0; base < LAYERS_BASE; base++) {
            for (int octant = 0; octant < 8; octant++) {
                FILLS[base][octant] = (MASKS[base] & 1 << octant) != 0 ? 1.0 : 0.0;
            }
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

    /** Capa de nieve con {@code count} capas (de 1 a 7). */
    public static byte layers(int count) {
        if (count < 1 || count > MAX_LAYERS) {
            throw new IllegalArgumentException("Las capas van de 1 a " + MAX_LAYERS + ": " + count);
        }
        return (byte) (LAYERS_BASE + count - 1);
    }

    /** Muro (o valla): la columna central del bloque; Minecraft lo une a sus vecinos al colocarlo. */
    public static byte wall() {
        return (byte) WALL_BASE;
    }

    /**
     * Panel de vidrio o barrotes con sus conexiones ({@code 1 << SOUTH | 1 << NORTH}…). Al elegir piezas se usa
     * sin conexiones; {@link PieceSolver#finish} se las pone mirando a los vecinos.
     */
    public static byte pane(int connections) {
        if (connections < 0 || connections >= PANE_COUNT) {
            throw new IllegalArgumentException("Conexiones de panel no válidas: " + connections);
        }
        return (byte) (PANE_BASE + connections);
    }

    /** La misma pieza, marcada como añadida fuera del volumen original. */
    public static byte added(byte piece) {
        return piece == FULL || piece == EMPTY ? piece : (byte) (piece | ADDED);
    }

    public static boolean isAdded(byte piece) {
        return piece != EMPTY && (piece & ADDED) != 0;
    }

    public static boolean isValid(byte piece) {
        if (piece == EMPTY) {
            return false;
        }
        int base = piece & BASE_BITS;
        return base < COUNT && !(base == FULL && piece != FULL);
    }

    public static boolean isSlab(byte piece) {
        int base = base(piece);
        return base == SLAB_BOTTOM || base == SLAB_TOP;
    }

    public static boolean isStairs(byte piece) {
        int base = base(piece);
        return base >= STAIRS_BASE && base < LAYERS_BASE;
    }

    public static boolean isLayers(byte piece) {
        int base = base(piece);
        return base >= LAYERS_BASE && base < WALL_BASE;
    }

    public static boolean isWall(byte piece) {
        return base(piece) == WALL_BASE;
    }

    public static boolean isPane(byte piece) {
        int base = base(piece);
        return base >= PANE_BASE && base < COUNT;
    }

    /** Lados a los que se une un panel: un bit por orientación ({@code 1 << SOUTH}, {@code 1 << WEST}…). */
    public static int paneConnections(byte piece) {
        if (!isPane(piece)) {
            throw new IllegalArgumentException("La pieza no es un panel: " + piece);
        }
        return base(piece) - PANE_BASE;
    }

    /** El mismo panel con otras conexiones; conserva la marca de añadido. */
    public static byte withConnections(byte piece, int connections) {
        byte pane = pane(connections);
        return isAdded(piece) ? added(pane) : pane;
    }

    /** Número de capas de una capa de nieve. */
    public static int layerCount(byte piece) {
        if (!isLayers(piece)) {
            throw new IllegalArgumentException("La pieza no es una capa: " + piece);
        }
        return base(piece) - LAYERS_BASE + 1;
    }

    /** Variante de la pieza, o {@code null} para el bloque entero y el vacío. */
    public static PieceVariant variant(byte piece) {
        if (piece == FULL || piece == EMPTY) {
            return null;
        }
        if (isSlab(piece)) {
            return PieceVariant.SLAB;
        }
        if (isStairs(piece)) {
            return PieceVariant.STAIR;
        }
        if (isPane(piece)) {
            return PieceVariant.PANE;
        }
        return isLayers(piece) ? PieceVariant.LAYER : PieceVariant.WALL;
    }

    /** Losa superior o escalera colgada (mitad maciza arriba). */
    public static boolean isTop(byte piece) {
        int base = base(piece);
        return base == SLAB_TOP || base >= STAIRS_BASE && base < LAYERS_BASE && ((base - STAIRS_BASE) & 4) != 0;
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

    /** Octantes que la pieza llena por completo (los que tapan la cara del vecino en el holograma). */
    public static int mask(byte piece) {
        return piece == EMPTY ? 0 : MASKS[base(piece)];
    }

    /**
     * Fracción de cada octante que ocupa la pieza: 0 o 1 en losas y escaleras, intermedia en capas, muros y paneles.
     * Un panel sin conexiones (el candidato al elegir) cuenta como la lámina entera, {@link #PANE_FILL}.
     */
    public static double fill(byte piece, int octant) {
        return piece == EMPTY ? 0.0 : FILLS[base(piece)][octant];
    }

    /**
     * Caja de las piezas que no se dibujan por octantes (capas y muros), en dieciseisavos de bloque:
     * {@code {minX, minY, minZ, maxX, maxY, maxZ}}; {@code null} para el resto. Los paneles tienen varias:
     * {@link #boxes}.
     */
    public static int[] box(byte piece) {
        if (isLayers(piece)) {
            return new int[]{0, 0, 0, 16, 2 * layerCount(piece), 16};
        }
        return isWall(piece) ? new int[]{4, 0, 4, 12, 16, 12} : null;
    }

    /** Todas las cajas de una pieza que no se dibuja por octantes; vacío para losas, escaleras y bloques. */
    public static int[][] boxes(byte piece) {
        if (isPane(piece)) {
            int connections = paneConnections(piece);
            int[][] result = new int[1 + Integer.bitCount(connections)][];
            int index = 0;
            result[index++] = paneBox(0);
            for (int side = 0; side < 4; side++) {
                if ((connections & 1 << side) != 0) {
                    result[index++] = paneBox(1 + side);
                }
            }
            return result;
        }
        int[] box = box(piece);
        return box == null ? new int[0][] : new int[][]{box};
    }

    /** Parte de un panel: 0 el poste, 1 a 4 los brazos hacia sur, oeste, norte y este. */
    public static int[] paneBox(int part) {
        return (part == 0 ? PANE_POST : PANE_ARMS[part - 1]).clone();
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
        if (isLayers(piece)) {
            int count = layerCount(piece);
            return count == 1 ? "1 capa" : count + " capas";
        }
        if (isWall(piece)) {
            return "muro";
        }
        if (isPane(piece)) {
            int connections = paneConnections(piece);
            if (connections == 0) {
                return "panel suelto";
            }
            String[] sides = {"sur", "oeste", "norte", "este"};
            StringBuilder text = new StringBuilder("panel");
            for (int side = 0; side < 4; side++) {
                if ((connections & 1 << side) != 0) {
                    text.append(' ').append(sides[side]);
                }
            }
            return text.toString();
        }
        String[] facings = {"sur", "oeste", "norte", "este"};
        String[] shapes = {"recta", "interior izquierda", "interior derecha", "exterior izquierda",
                "exterior derecha"};
        return "escalera " + facings[facing(piece)] + (isTop(piece) ? " colgada " : " ") + shapes[shape(piece)];
    }

    private static int base(byte piece) {
        return piece == EMPTY ? FULL : piece & BASE_BITS;
    }

    /**
     * Ocupación de un panel por octante: sin conexiones, la lámina entera (la que se supone al elegirlo); con ellas,
     * el poste y sus brazos medidos en dieciseisavos.
     */
    private static double[] paneFill(int connections) {
        double[] fill = new double[8];
        if (connections == 0) {
            java.util.Arrays.fill(fill, PANE_FILL);
            return fill;
        }
        for (int octant = 0; octant < 8; octant++) {
            int ox = (octant & 1) * 8;
            int oy = (octant >> 1 & 1) * 8;
            int oz = (octant >> 2 & 1) * 8;
            // El poste y los brazos no se solapan: se suman sus volúmenes dentro del octante.
            double volume = overlap(PANE_POST, ox, oy, oz);
            for (int side = 0; side < 4; side++) {
                if ((connections & 1 << side) != 0) {
                    volume += overlap(PANE_ARMS[side], ox, oy, oz);
                }
            }
            fill[octant] = volume / 512.0;
        }
        return fill;
    }

    private static double overlap(int[] box, int ox, int oy, int oz) {
        int x = Math.max(0, Math.min(box[3], ox + 8) - Math.max(box[0], ox));
        int y = Math.max(0, Math.min(box[4], oy + 8) - Math.max(box[1], oy));
        int z = Math.max(0, Math.min(box[5], oz + 8) - Math.max(box[2], oz));
        return x * y * z;
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
