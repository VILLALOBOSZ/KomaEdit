package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Contorno de una vista previa: las aristas donde la figura dobla, no la rejilla de cada bloque. Una cara plana de
 * 10×10 bloques da sus cuatro bordes, no cien cuadrados.
 *
 * <p>Una arista de la cara {@code d} de un cambio se dibuja salvo que el vecino de ese lado, en el mismo plano, sea
 * un cambio del mismo tipo con la misma cara al descubierto (entonces la superficie sigue). Así salen las esquinas
 * hacia fuera, las de hacia dentro y la frontera entre lo que se pone y lo que se quita. Con solo la superficie del
 * plan basta: las caras al descubierto se calcularon con todos los cambios ({@link PreviewSurface}).</p>
 *
 * <p>Las aristas seguidas se unen y luego se cortan por secciones de 16³ (las del cliente). Cada una va en un
 * entero: posición de su esquina inicial dentro de la sección (4 bits por eje), eje, longitud (1 a 16) y tipo
 * ({@link PreviewDisplays.Kind}). Lógica pura y determinista.</p>
 */
public final class PreviewOutline {
    private static final int SECTION = 16;

    private PreviewOutline() {
    }

    /** Aristas de una sección (coordenadas de sección, como {@code x >> 4}). */
    public record Section(int x, int y, int z, int[] edges) {
    }

    public static int pack(int localX, int localY, int localZ, int axis, int length, int kind) {
        if (length < 1 || length > SECTION) {
            throw new IllegalArgumentException("Longitud fuera de rango: " + length);
        }
        return (localX & 15) | (localY & 15) << 4 | (localZ & 15) << 8 | (axis & 3) << 12 | (length - 1) << 14
                | (kind & 3) << 18;
    }

    public static int localX(int edge) {
        return edge & 15;
    }

    public static int localY(int edge) {
        return edge >> 4 & 15;
    }

    public static int localZ(int edge) {
        return edge >> 8 & 15;
    }

    /** 0 = X, 1 = Y, 2 = Z. */
    public static int axis(int edge) {
        return edge >> 12 & 3;
    }

    public static int length(int edge) {
        return (edge >> 14 & 15) + 1;
    }

    public static int kind(int edge) {
        return edge >> 18 & 3;
    }

    /** Aristas del contorno, por sección y ordenadas. */
    public static List<Section> build(long[] positions, byte[] kinds, byte[] faces) {
        int count = positions.length;
        Long2IntOpenHashMap index = new Long2IntOpenHashMap(count);
        index.defaultReturnValue(-1);
        for (int entry = 0; entry < count; entry++) {
            index.put(positions[entry], entry);
        }
        // Esquina inicial de cada arista unitaria, por eje; el tipo va aparte (gana el primero que la pone).
        LongOpenHashSet[] seen = {new LongOpenHashSet(), new LongOpenHashSet(), new LongOpenHashSet()};
        LongArrayList[] edges = {new LongArrayList(), new LongArrayList(), new LongArrayList()};
        Long2IntOpenHashMap[] edgeKinds = {new Long2IntOpenHashMap(), new Long2IntOpenHashMap(),
                new Long2IntOpenHashMap()};
        int[] voxel = new int[3];
        int[] corner = new int[3];
        for (int entry = 0; entry < count; entry++) {
            int mask = faces[entry] & PreviewSurface.ALL_FACES;
            if (mask == 0) {
                continue;
            }
            long packed = positions[entry];
            voxel[0] = PackedBlockPlan.unpackX(packed);
            voxel[1] = PackedBlockPlan.unpackY(packed);
            voxel[2] = PackedBlockPlan.unpackZ(packed);
            for (int direction = 0; direction < 6; direction++) {
                if ((mask & 1 << direction) == 0) {
                    continue;
                }
                int normal = direction >> 1;
                int plane = voxel[normal] + (direction & 1);
                for (int tangent = 0; tangent < 3; tangent++) {
                    if (tangent == normal) {
                        continue;
                    }
                    int along = 3 - normal - tangent;
                    for (int side = -1; side <= 1; side += 2) {
                        int neighbour = index.get(PackedBlockPlan.pack(
                                voxel[0] + (tangent == 0 ? side : 0),
                                voxel[1] + (tangent == 1 ? side : 0),
                                voxel[2] + (tangent == 2 ? side : 0)));
                        if (neighbour >= 0 && (faces[neighbour] & 1 << direction) != 0
                                && kinds[neighbour] == kinds[entry]) {
                            continue;
                        }
                        corner[normal] = plane;
                        corner[tangent] = voxel[tangent] + (side > 0 ? 1 : 0);
                        corner[along] = voxel[along];
                        if (corner[1] < -2048 || corner[1] > 2047) {
                            continue;
                        }
                        long key = PackedBlockPlan.pack(corner[0], corner[1], corner[2]);
                        if (seen[along].add(key)) {
                            edges[along].add(key);
                            edgeKinds[along].put(key, kinds[entry]);
                        }
                    }
                }
            }
        }
        Map<Long, IntArrayList> sections = new TreeMap<>(Comparator.naturalOrder());
        for (int axis = 0; axis < 3; axis++) {
            merge(axis, edges[axis].toLongArray(), edgeKinds[axis], sections);
        }
        List<Section> result = new ArrayList<>(sections.size());
        for (Map.Entry<Long, IntArrayList> entry : sections.entrySet()) {
            long key = entry.getKey();
            int[] sorted = entry.getValue().toIntArray();
            java.util.Arrays.sort(sorted);
            result.add(new Section(sectionX(key), sectionY(key), sectionZ(key), sorted));
        }
        return result;
    }

    /** Une las aristas unitarias seguidas del mismo tipo y las corta por secciones. */
    private static void merge(int axis, long[] corners, Long2IntOpenHashMap kinds, Map<Long, IntArrayList> sections) {
        int count = corners.length;
        int[] first = new int[count];
        int[] second = new int[count];
        int[] position = new int[count];
        int[] order = new int[count];
        for (int index = 0; index < count; index++) {
            int x = PackedBlockPlan.unpackX(corners[index]);
            int y = PackedBlockPlan.unpackY(corners[index]);
            int z = PackedBlockPlan.unpackZ(corners[index]);
            first[index] = axis == 0 ? y : x;
            second[index] = axis == 2 ? y : z;
            position[index] = axis == 0 ? x : axis == 1 ? y : z;
            order[index] = index;
        }
        IntArrays.quickSort(order, (a, b) -> {
            int compared = Integer.compare(first[a], first[b]);
            if (compared == 0) {
                compared = Integer.compare(second[a], second[b]);
            }
            return compared != 0 ? compared : Integer.compare(position[a], position[b]);
        });
        int slot = 0;
        while (slot < count) {
            int head = order[slot];
            int kind = kinds.get(corners[head]);
            int end = position[head] + 1;
            int next = slot + 1;
            while (next < count) {
                int candidate = order[next];
                if (first[candidate] != first[head] || second[candidate] != second[head]
                        || position[candidate] != end || kinds.get(corners[candidate]) != kind) {
                    break;
                }
                end++;
                next++;
            }
            emit(axis, corners[head], position[head], end, kind, sections);
            slot = next;
        }
    }

    /** Corta el tramo {@code [start, end)} del eje por secciones y lo apunta en cada una. */
    private static void emit(int axis, long corner, int start, int end, int kind, Map<Long, IntArrayList> sections) {
        int x = PackedBlockPlan.unpackX(corner);
        int y = PackedBlockPlan.unpackY(corner);
        int z = PackedBlockPlan.unpackZ(corner);
        int cursor = start;
        while (cursor < end) {
            int sectionStart = Math.floorDiv(cursor, SECTION) * SECTION;
            int stop = Math.min(end, sectionStart + SECTION);
            int cx = axis == 0 ? cursor : x;
            int cy = axis == 1 ? cursor : y;
            int cz = axis == 2 ? cursor : z;
            int sx = Math.floorDiv(cx, SECTION);
            int sy = Math.floorDiv(cy, SECTION);
            int sz = Math.floorDiv(cz, SECTION);
            int edge = pack(cx - sx * SECTION, cy - sy * SECTION, cz - sz * SECTION, axis, stop - cursor, kind);
            sections.computeIfAbsent(sectionKey(sx, sy, sz), ignored -> new IntArrayList()).add(edge);
            cursor = stop;
        }
    }

    private static long sectionKey(int x, int y, int z) {
        return ((long) x + (1L << 21) & 0x3FFFFFL) << 42 | ((long) y + (1L << 7) & 0xFFFFL) << 26
                | (long) z + (1L << 21) & 0x3FFFFFL;
    }

    private static int sectionX(long key) {
        return (int) ((key >>> 42 & 0x3FFFFFL) - (1L << 21));
    }

    private static int sectionY(long key) {
        return (int) ((key >>> 26 & 0xFFFFL) - (1L << 7));
    }

    private static int sectionZ(long key) {
        return (int) ((key & 0x3FFFFFL) - (1L << 21));
    }
}
