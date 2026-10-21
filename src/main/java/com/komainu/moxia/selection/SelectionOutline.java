package com.komainu.moxia.selection;

import com.komainu.moxia.preview.PreviewOutline;
import com.komainu.moxia.preview.PreviewSurface;

import java.util.List;

/**
 * El contorno de una selección de bloques sueltos para dibujarlo en el cliente: las aristas donde la selección dobla,
 * como las guías de la vista previa ({@link PreviewOutline}), no la rejilla de cada bloque. Una selección muy grande
 * solo se cuenta y se enseña con su caja ({@link #MAX_BLOCKS}).
 */
public final class SelectionOutline {
    /** Bloques como mucho para dibujar el contorno; más, solo la caja y el número. */
    public static final int MAX_BLOCKS = 200_000;
    /** Aristas como mucho que se mandan; más, solo la caja y el número. */
    public static final int MAX_EDGES = 60_000;

    private SelectionOutline() {
    }

    /** Las aristas por sección de 16³, o {@code null} si la selección es demasiado grande para dibujarla entera. */
    public static List<PreviewOutline.Section> build(BlockSet set) {
        if (set.isEmpty() || set.size() > MAX_BLOCKS) {
            return set.isEmpty() ? List.of() : null;
        }
        long[] positions = set.toSortedArray();
        byte[] kinds = new byte[positions.length];
        byte[] faces = new byte[positions.length];
        for (int index = 0; index < positions.length; index++) {
            long block = positions[index];
            int x = BlockSet.x(block);
            int y = BlockSet.y(block);
            int z = BlockSet.z(block);
            int mask = 0;
            for (int direction = 0; direction < 6; direction++) {
                if (!set.contains(x + PreviewSurface.OFFSET_X[direction], y + PreviewSurface.OFFSET_Y[direction],
                        z + PreviewSurface.OFFSET_Z[direction])) {
                    mask |= 1 << direction;
                }
            }
            faces[index] = (byte) mask;
        }
        List<PreviewOutline.Section> sections = PreviewOutline.build(positions, kinds, faces);
        int edges = 0;
        for (PreviewOutline.Section section : sections) {
            edges += section.edges().length;
        }
        return edges > MAX_EDGES ? null : sections;
    }
}
