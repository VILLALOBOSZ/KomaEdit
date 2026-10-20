package com.komainu.moxia.preview;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewOutlineTest {
    /** Una arista en coordenadas de mundo: esquina inicial, eje y longitud. */
    private record Edge(int x, int y, int z, int axis, int length, int kind) {
    }

    private static List<Edge> outline(long[] positions, byte[] kinds) {
        PreviewSurface.Selection selection = PreviewSurface.select(positions, positions.length, 1_000_000);
        long[] shown = new long[selection.shown()];
        byte[] shownKinds = new byte[selection.shown()];
        for (int slot = 0; slot < shown.length; slot++) {
            shown[slot] = positions[selection.indices()[slot]];
            shownKinds[slot] = kinds[selection.indices()[slot]];
        }
        List<Edge> edges = new ArrayList<>();
        for (PreviewOutline.Section section : PreviewOutline.build(shown, shownKinds, selection.faces())) {
            for (int edge : section.edges()) {
                edges.add(new Edge(section.x() * 16 + PreviewOutline.localX(edge),
                        section.y() * 16 + PreviewOutline.localY(edge),
                        section.z() * 16 + PreviewOutline.localZ(edge),
                        PreviewOutline.axis(edge), PreviewOutline.length(edge), PreviewOutline.kind(edge)));
            }
        }
        return edges;
    }

    private static int totalLength(List<Edge> edges) {
        return edges.stream().mapToInt(Edge::length).sum();
    }

    private static byte[] kinds(int count, int kind) {
        byte[] kinds = new byte[count];
        java.util.Arrays.fill(kinds, (byte) kind);
        return kinds;
    }

    @Test
    void aCubeIsItsTwelveEdgesWithoutTheGrid() {
        long[] positions = PreviewSurfaceTest.cube(5, 1, 65, 1);
        List<Edge> edges = outline(positions, kinds(positions.length, 0));
        assertEquals(12, edges.size(), "doce aristas, sin la rejilla de cada cara: " + edges);
        for (Edge edge : edges) {
            assertEquals(5, edge.length());
        }
    }

    @Test
    void aFlatSheetIsTwoRectanglesAndFourPosts() {
        LongArrayList positions = new LongArrayList();
        for (int z = 0; z < 8; z++) {
            for (int x = 0; x < 6; x++) {
                positions.add(PackedBlockPlan.pack(x, 70, z));
            }
        }
        List<Edge> edges = outline(positions.toLongArray(), kinds(positions.size(), 0));
        assertEquals(12, edges.size());
        assertEquals(2 * (2 * 6 + 2 * 8) + 4, totalLength(edges));
    }

    @Test
    void aConcaveCornerIsDrawnToo() {
        // Una «L» de dos bloques de ancho en planta, un bloque de alto.
        LongArrayList positions = new LongArrayList();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                if (x < 2 || z < 2) {
                    positions.add(PackedBlockPlan.pack(x, 64, z));
                }
            }
        }
        List<Edge> edges = outline(positions.toLongArray(), kinds(positions.size(), 0));
        Set<String> vertical = new HashSet<>();
        for (Edge edge : edges) {
            if (edge.axis() == 1) {
                vertical.add(edge.x() + "," + edge.z());
            }
        }
        assertEquals(Set.of("0,0", "4,0", "4,2", "2,2", "2,4", "0,4"), vertical,
                "un poste en cada esquina, también en la de dentro (2,2)");
    }

    @Test
    void theBorderBetweenAddedAndRemovedIsDrawn() {
        LongArrayList positions = new LongArrayList();
        for (int x = 0; x < 4; x++) {
            positions.add(PackedBlockPlan.pack(x, 64, 0));
        }
        byte[] kinds = {0, 0, (byte) PreviewDisplays.Kind.REMOVE.ordinal(),
                (byte) PreviewDisplays.Kind.REMOVE.ordinal()};
        List<Edge> edges = outline(positions.toLongArray(), kinds);
        long middle = edges.stream().filter(edge -> edge.axis() != 0 && edge.x() == 2).count();
        assertEquals(4, middle, "la raya entre lo verde y lo rojo, en las cuatro caras");
        assertTrue(edges.stream().anyMatch(edge -> edge.kind() == PreviewDisplays.Kind.REMOVE.ordinal()));
    }

    @Test
    void longEdgesAreCutAtSectionBorders() {
        LongArrayList positions = new LongArrayList();
        for (int x = 10; x < 40; x++) {
            positions.add(PackedBlockPlan.pack(x, 64, 3));
        }
        List<Edge> edges = outline(positions.toLongArray(), kinds(positions.size(), 0));
        List<Edge> alongX = edges.stream().filter(edge -> edge.axis() == 0).toList();
        assertEquals(4 * 3, alongX.size(), "cuatro aristas largas, cada una en tres secciones (10-16, 16-32, 32-40)");
        assertEquals(4 * 30, totalLength(alongX));
        for (Edge edge : alongX) {
            assertTrue(Math.floorDiv(edge.x(), 16) == Math.floorDiv(edge.x() + edge.length() - 1, 16),
                    "dentro de una sección: " + edge);
        }
    }
}
