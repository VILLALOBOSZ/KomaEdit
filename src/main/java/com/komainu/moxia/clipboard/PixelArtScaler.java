package com.komainu.moxia.clipboard;

/**
 * Ampliaciones de pixel art llevadas a bloques: una rejilla {@code k} veces más fina en cada eje donde cada bloque
 * se reparte en {@code k³} trocitos según sus vecinos, como Scale2x (EPX/AdvMAME2x) y Scale3x (AdvMAME3x).
 *
 * <p>En 3D cada trocito se decide con la regla 2D en los tres planos que lo contienen (XZ, XY y ZY): si los planos
 * que proponen un vecino están de acuerdo, se queda con él; si no, con el bloque de origen. En una capa de un
 * bloque de grosor los planos verticales nunca proponen nada y sale exactamente el Scale2x o Scale3x 2D de esa capa
 * repetido en cada trocito de altura. Para girar sobre el eje Y se usa solo el plano XZ: cada capa se amplía por
 * separado y la altura no se toca.</p>
 *
 * <p>Las ampliaciones se calculan bajo demanda: {@link Sampler#at} de un nivel pide a su padre el bloque y sus
 * vecinos. El primer nivel se guarda entero si cabe, así RotSprite (tres veces Scale2x, 8×) cuesta unas decenas de
 * lecturas por muestra.</p>
 */
public final class PixelArtScaler {
    /** Celdas máximas que se guardan del primer nivel ampliado. */
    private static final long CACHE_LIMIT = 16_000_000L;

    private PixelArtScaler() {
    }

    /** Una rejilla ampliada leída en coordenadas desde la esquina menor del original por {@code factor}. */
    public interface Sampler {
        int at(int x, int y, int z);

        /** Cuántas veces más fina es que el original. */
        int factor();
    }

    /** Qué planos deciden cada trocito: los tres (escalar en 3D) o solo el horizontal (girar sobre Y). */
    public enum Planes {
        ALL, HORIZONTAL
    }

    /** El original sin ampliar. */
    public static Sampler identity(VoxelVolume volume) {
        return new Sampler() {
            @Override
            public int at(int x, int y, int z) {
                return volume.local(x, y, z);
            }

            @Override
            public int factor() {
                return 1;
            }
        };
    }

    /** Scale2x sobre {@code parent}: el doble de fino. */
    public static Sampler scale2x(Sampler parent, Planes planes, int[] size) {
        return cached(new Scale(parent, 2, planes), size);
    }

    /** Scale3x sobre {@code parent}: el triple de fino. */
    public static Sampler scale3x(Sampler parent, Planes planes, int[] size) {
        return cached(new Scale(parent, 3, planes), size);
    }

    /** Ocho veces más fino con tres Scale2x seguidos: la base de RotSprite. */
    public static Sampler rotSprite(VoxelVolume volume, Planes planes) {
        int[] size = {volume.sizeX(), volume.sizeY(), volume.sizeZ()};
        Sampler first = scale2x(identity(volume), planes, size);
        Sampler second = new Scale(first, 2, planes);
        return new Scale(second, 2, planes);
    }

    /**
     * El primer nivel se guarda en un array si cabe (las demás ampliaciones lo leen muchas veces); {@code size} es
     * el tamaño del original en cada eje.
     */
    private static Sampler cached(Scale scale, int[] size) {
        int factor = scale.factor();
        long sx = (long) size[0] * factor;
        long sy = (long) size[1] * (scale.planes == Planes.HORIZONTAL ? 1 : factor);
        long sz = (long) size[2] * factor;
        if (scale.parent.factor() != 1 || sx * sy * sz > CACHE_LIMIT) {
            return scale;
        }
        int width = (int) sx;
        int height = (int) sy;
        int depth = (int) sz;
        int[] values = new int[width * height * depth];
        java.util.Arrays.fill(values, -1);
        return new Sampler() {
            @Override
            public int at(int x, int y, int z) {
                if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
                    return VoxelVolume.AIR;
                }
                int index = (y * depth + z) * width + x;
                int value = values[index];
                if (value < 0) {
                    value = scale.at(x, y, z);
                    values[index] = value;
                }
                return value;
            }

            @Override
            public int factor() {
                return scale.factor();
            }
        };
    }

    /**
     * Un nivel de ampliación. Con {@link Planes#HORIZONTAL} la altura no se amplía (cada capa va por su lado), así
     * que la coordenada {@code y} es la del padre.
     */
    private static final class Scale implements Sampler {
        /** Los tres planos: ejes {@code u} y {@code v} de cada uno (0 = x, 1 = y, 2 = z). */
        private static final int[][] ALL_PLANES = {{0, 2}, {0, 1}, {2, 1}};
        private static final int[][] HORIZONTAL_PLANES = {{0, 2}};

        private final Sampler parent;
        private final int step;
        private final Planes planes;
        private final int[][] axes;

        private Scale(Sampler parent, int step, Planes planes) {
            this.parent = parent;
            this.step = step;
            this.planes = planes;
            this.axes = planes == Planes.ALL ? ALL_PLANES : HORIZONTAL_PLANES;
        }

        @Override
        public int factor() {
            return parent.factor() * step;
        }

        @Override
        public int at(int x, int y, int z) {
            int[] cell = {Math.floorDiv(x, step), planes == Planes.ALL ? Math.floorDiv(y, step) : y,
                    Math.floorDiv(z, step)};
            int[] sub = {x - cell[0] * step, planes == Planes.ALL ? y - cell[1] * step : 0, z - cell[2] * step};
            int center = parent.at(cell[0], cell[1], cell[2]);
            int chosen = center;
            boolean agreed = false;
            for (int[] plane : axes) {
                int u = plane[0];
                int v = plane[1];
                int candidate = step == 2
                        ? rule2x(cell, u, v, sub[u] == 0 ? -1 : 1, sub[v] == 0 ? -1 : 1, center)
                        : rule3x(cell, u, v, sub[u] - 1, sub[v] - 1, center);
                if (candidate == center) {
                    continue;
                }
                if (!agreed) {
                    chosen = candidate;
                    agreed = true;
                } else if (candidate != chosen) {
                    return center;
                }
            }
            return chosen;
        }

        /** Vecino del bloque {@code cell} desplazado {@code du} en el eje {@code u} y {@code dv} en el {@code v}. */
        private int neighbor(int[] cell, int u, int du, int v, int dv) {
            int[] position = cell.clone();
            position[u] += du;
            position[v] += dv;
            return parent.at(position[0], position[1], position[2]);
        }

        /**
         * Scale2x en el plano {@code (u, v)} para el trocito de la esquina {@code (su, sv)}: si los dos vecinos de esa
         * esquina coinciden y los opuestos no, la esquina se queda con ellos.
         */
        private int rule2x(int[] cell, int u, int v, int su, int sv, int center) {
            int a = neighbor(cell, u, su, v, 0);
            int b = neighbor(cell, u, 0, v, sv);
            int oppositeA = neighbor(cell, u, -su, v, 0);
            int oppositeB = neighbor(cell, u, 0, v, -sv);
            return a == b && b != oppositeA && a != oppositeB ? a : center;
        }

        /**
         * Scale3x (AdvMAME3x) en el plano {@code (u, v)} para el trocito {@code (ou, ov)} de {−1, 0, 1}². Con la
         * notación de siempre, A B C / D E F / G H I con {@code u} a la derecha y {@code v} hacia abajo.
         */
        private int rule3x(int[] cell, int u, int v, int ou, int ov, int center) {
            if (ou == 0 && ov == 0) {
                return center;
            }
            int a = neighbor(cell, u, -1, v, -1);
            int b = neighbor(cell, u, 0, v, -1);
            int c = neighbor(cell, u, 1, v, -1);
            int d = neighbor(cell, u, -1, v, 0);
            int e = center;
            int f = neighbor(cell, u, 1, v, 0);
            int g = neighbor(cell, u, -1, v, 1);
            int h = neighbor(cell, u, 0, v, 1);
            int i = neighbor(cell, u, 1, v, 1);
            if (b == h || d == f) {
                return e;
            }
            return switch ((ov + 1) * 3 + (ou + 1)) {
                case 0 -> d == b ? d : e;
                case 1 -> (d == b && e != c) || (b == f && e != a) ? b : e;
                case 2 -> b == f ? f : e;
                case 3 -> (d == b && e != g) || (d == h && e != a) ? d : e;
                case 5 -> (b == f && e != i) || (h == f && e != c) ? f : e;
                case 6 -> d == h ? d : e;
                case 7 -> (d == h && e != i) || (h == f && e != g) ? h : e;
                case 8 -> h == f ? f : e;
                default -> e;
            };
        }
    }
}
