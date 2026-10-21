package com.komainu.moxia.selection;

import com.komainu.moxia.preview.PreviewOutline;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sumar, restar e intersecar; ampliar, reducir e invertir; el pincel; el historial de trazos y el contorno. */
class SelectionOperationsTest {
    private static final int GROUND = 63;

    @Test
    void strokesCombineWithTheSelection() {
        BlockSet current = BlockSet.box(0, 0, 0, 3, 0, 0);
        BlockSet stroke = BlockSet.box(2, 0, 0, 5, 0, 0);
        assertEquals(stroke, SelectionCombine.REPLACE.apply(current, stroke));
        assertEquals(BlockSet.box(0, 0, 0, 5, 0, 0), SelectionCombine.ADD.apply(current, stroke));
        assertEquals(BlockSet.box(0, 0, 0, 1, 0, 0), SelectionCombine.SUBTRACT.apply(current, stroke));
        assertEquals(BlockSet.box(2, 0, 0, 3, 0, 0), SelectionCombine.INTERSECT.apply(current, stroke));
        assertEquals(BlockSet.box(2, 0, 0, 3, 0, 0), SelectionCombine.INTERSECT.apply(stroke, current));
        assertEquals(4, current.size(), "la selección de antes no se toca");

        assertEquals(SelectionCombine.REPLACE, SelectionCombine.of(false, false, false));
        assertEquals(SelectionCombine.ADD, SelectionCombine.of(true, false, false));
        assertEquals(SelectionCombine.SUBTRACT, SelectionCombine.of(false, true, false));
        assertEquals(SelectionCombine.INTERSECT, SelectionCombine.of(false, false, true));
        assertEquals(SelectionCombine.SUBTRACT, SelectionCombine.of(true, true, true), "gana Ctrl");
        assertThrows(IllegalArgumentException.class, () -> SelectionCombine.byId(9));
    }

    @Test
    void blockSetsKeepTheirBounds() {
        BlockSet set = new BlockSet();
        assertNull(set.bounds());
        set.add(3, -60, 9);
        set.add(-4, 200, 1);
        assertEquals(List.of(-4, -60, 1, 3, 200, 9), java.util.Arrays.stream(set.bounds()).boxed().toList());
        set.remove(-4, 200, 1);
        assertEquals(List.of(3, -60, 9, 3, -60, 9), java.util.Arrays.stream(set.bounds()).boxed().toList());
        assertFalse(set.contains(0, 5000, 0), "una altura imposible no está");
        assertEquals(set, set.shifted(2, 0, 0).shifted(-2, 0, 0));
        assertEquals(BlockSet.ofPacked(set.toSortedArray()), set);
    }

    @Test
    void growAndShrinkInVolume() {
        BlockSet one = BlockSet.box(0, 0, 0, 0, 0, 0);
        assertEquals(27, SelectionMorphology.grow(one, 1, BrushShape.CUBE, 1_000).size());
        assertEquals(7, SelectionMorphology.grow(one, 1, BrushShape.OCTAHEDRON, 1_000).size());
        assertEquals(19, SelectionMorphology.grow(one, 1, BrushShape.SPHERE, 1_000).size());
        assertEquals(125, SelectionMorphology.grow(one, 2, BrushShape.CUBE, 1_000).size());
        assertEquals(25, SelectionMorphology.grow(one, 2, BrushShape.OCTAHEDRON, 1_000).size());

        BlockSet cube = BlockSet.box(0, 0, 0, 4, 4, 4);
        assertEquals(BlockSet.box(1, 1, 1, 3, 3, 3), SelectionMorphology.shrink(cube, 1, BrushShape.CUBE));
        assertEquals(BlockSet.box(2, 2, 2, 2, 2, 2), SelectionMorphology.shrink(cube, 2, BrushShape.OCTAHEDRON));
        assertTrue(SelectionMorphology.shrink(cube, 3, BrushShape.CUBE).isEmpty());
        // Una L: crecer y encoger con el cubo devuelve lo mismo en lo que es grueso.
        BlockSet box = BlockSet.box(0, 0, 0, 6, 6, 6);
        assertEquals(box, SelectionMorphology.shrink(SelectionMorphology.grow(box, 2, BrushShape.CUBE, 10_000), 2,
                BrushShape.CUBE));
        assertThrows(IllegalArgumentException.class, () -> SelectionMorphology.grow(box, 3, BrushShape.CUBE, 500));
        assertThrows(IllegalArgumentException.class, () -> SelectionMorphology.grow(box, 0, BrushShape.CUBE, 500));
        assertThrows(IllegalArgumentException.class, () -> SelectionMorphology.shrink(box,
                SelectionMorphology.MAX_STEPS + 1, BrushShape.CUBE));
    }

    @Test
    void invertKeepsTheRestOfTheBox() {
        BlockSet shell = BlockSet.box(0, 0, 0, 4, 4, 4);
        shell.removeAll(BlockSet.box(1, 1, 1, 3, 3, 3));
        assertEquals(BlockSet.box(1, 1, 1, 3, 3, 3), SelectionMorphology.invert(shell, 1_000));
        assertThrows(IllegalArgumentException.class, () -> SelectionMorphology.invert(shell, 10));
        assertThrows(IllegalArgumentException.class, () -> SelectionMorphology.invert(new BlockSet(), 10));
    }

    @Test
    void surfaceOperationsFollowTheTerrain() {
        // Suelo a 63 con un escalón: de x = 4 en adelante está un bloque más alto.
        TestTerrain terrain = TestTerrain.flat(GROUND).fill(4, GROUND + 1, -10, 10, GROUND + 1, 10, Voxels.SOLID);
        BlockSet patch = BlockSet.box(-2, GROUND, -2, 2, GROUND, 2);
        assertEquals(3, SelectionMorphology.facing(patch, terrain, false), "mira hacia arriba");

        BlockSet smaller = SelectionMorphology.shrinkSurface(patch, 1, BrushShape.CUBE, terrain, false);
        assertEquals(BlockSet.box(-1, GROUND, -1, 1, GROUND, 1), smaller);

        BlockSet bigger = SelectionMorphology.growSurface(patch, 2, BrushShape.CUBE, terrain, false, 10_000);
        assertEquals(81, bigger.size(), "9×9 columnas, una por columna");
        assertTrue(bigger.contains(-4, GROUND, -4));
        assertTrue(bigger.contains(4, GROUND + 1, 0), "sube al escalón");
        assertFalse(bigger.contains(4, GROUND, 0), "y no mete lo de debajo");
        assertFalse(bigger.contains(-4, GROUND + 1, -4), "ni el aire");
        // Bajo la copa de un árbol sigue el suelo, que está más cerca que las hojas.
        TestTerrain tree = TestTerrain.flat(GROUND).fill(-6, GROUND + 4, -6, -3, GROUND + 5, -3, Voxels.SOLID);
        BlockSet shaded = SelectionMorphology.growSurface(patch, 2, BrushShape.CUBE, tree, false, 10_000);
        assertTrue(shaded.contains(-4, GROUND, -4));
        assertFalse(shaded.contains(-4, GROUND + 5, -4), "la copa no");

        // Una selección de tres de grueso crece con tres de grueso y encoge columnas enteras.
        BlockSet thick = BlockSet.box(-2, GROUND - 2, -2, 2, GROUND, 2);
        BlockSet thicker = SelectionMorphology.growSurface(thick, 1, BrushShape.OCTAHEDRON, terrain, false, 10_000);
        assertTrue(thicker.contains(-3, GROUND - 2, 0));
        assertFalse(thicker.contains(-3, GROUND - 3, 0));
        assertFalse(thicker.contains(-3, GROUND, -3), "el rombo no llega a las esquinas");
        assertEquals(BlockSet.box(-1, GROUND - 2, -1, 1, GROUND, 1),
                SelectionMorphology.shrinkSurface(thick, 1, BrushShape.CUBE, terrain, false));

        // Invertir un anillo de suelo: el agujero de en medio.
        BlockSet ring = BlockSet.box(-2, GROUND, -2, 2, GROUND, 2);
        ring.remove(0, GROUND, 0);
        assertEquals(BlockSet.box(0, GROUND, 0, 0, GROUND, 0),
                SelectionMorphology.invertSurface(ring, terrain, false, 1_000));
    }

    @Test
    void surfaceOperationsWorkOnWallsToo() {
        TestTerrain terrain = TestTerrain.flat(GROUND).fill(-20, GROUND + 1, 10, 20, GROUND + 20, 15, Voxels.SOLID);
        BlockSet facade = BlockSet.box(-2, GROUND + 5, 10, 2, GROUND + 9, 10);
        assertEquals(4, SelectionMorphology.facing(facade, terrain, false), "mira hacia −Z");
        BlockSet grown = SelectionMorphology.growSurface(facade, 1, BrushShape.CUBE, terrain, false, 10_000);
        assertEquals(49, grown.size());
        grown.forEach(block -> assertEquals(10, BlockSet.z(block), "por la fachada, sin meterse"));
    }

    @Test
    void theBrushOnlyTakesMatter() {
        TestTerrain terrain = TestTerrain.flat(GROUND).set(1, GROUND + 1, 0, Voxels.NON_SOLID);
        BlockSet sphere = BrushShape.SPHERE.stamp(0, GROUND, 0, 2, terrain, false);
        int expected = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 0; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= 6) {
                        expected++;
                    }
                }
            }
        }
        assertEquals(expected, sphere.size(), "media esfera: lo de arriba es aire");
        assertFalse(sphere.contains(1, GROUND + 1, 0), "la flor no, sin «no sólido»");
        assertTrue(BrushShape.SPHERE.stamp(0, GROUND, 0, 2, terrain, true).contains(1, GROUND + 1, 0));
        assertEquals(18, BrushShape.CUBE.stamp(0, GROUND, 0, 1, terrain, false).size(),
                "cubo de 3×3×3 con la capa de arriba en el aire");
        assertEquals(6, BrushShape.OCTAHEDRON.stamp(0, GROUND, 0, 1, terrain, false).size());
        assertThrows(IllegalArgumentException.class, () -> BrushShape.CUBE.stamp(0, 0, 0, BrushShape.MAX_RADIUS + 1,
                terrain, false));
        assertEquals(BrushShape.OCTAHEDRON, BrushShape.byId("octaedro"));
        assertThrows(IllegalArgumentException.class, () -> BrushShape.byId("pirámide"));
    }

    @Test
    void historyForgetsTheOldestFirst() {
        SelectionHistory<BlockSet> history = new SelectionHistory<>(BlockSet::size);
        for (int index = 0; index < SelectionHistory.MAX_ENTRIES + 3; index++) {
            history.push(BlockSet.box(0, 0, 0, index, 0, 0));
        }
        assertEquals(SelectionHistory.MAX_ENTRIES, history.size());
        assertEquals(SelectionHistory.MAX_ENTRIES + 2 + 1, history.pop().size(), "deshace el último");
        history.push(new BlockSet());
        assertEquals(0, history.pop().size());

        SelectionHistory<Long> heavy = new SelectionHistory<>(Long::longValue);
        heavy.push(3_000_000L);
        heavy.push(3_000_000L);
        assertEquals(1, heavy.size(), "no caben dos de tres millones");
        assertEquals(3_000_000L, heavy.weight());
        heavy.push(SelectionHistory.MAX_WEIGHT + 1);
        assertEquals(0, heavy.size(), "una que no cabe vacía el historial: no se puede deshacer a medias");
        assertNull(heavy.pop());
    }

    @Test
    void theOutlineFollowsTheFolds() {
        List<PreviewOutline.Section> single = SelectionOutline.build(BlockSet.box(0, 64, 0, 0, 64, 0));
        assertEquals(12, edges(single), "un bloque: sus doce aristas");
        assertEquals(12, edges(SelectionOutline.build(BlockSet.box(0, 64, 0, 9, 64, 9))),
                "una losa plana: sus doce aristas, no la rejilla");
        BlockSet step = BlockSet.box(0, 64, 0, 9, 64, 9);
        step.addAll(BlockSet.box(5, 65, 0, 9, 65, 9));
        assertTrue(edges(SelectionOutline.build(step)) > 12, "el escalón tiene sus aristas");
        assertTrue(SelectionOutline.build(new BlockSet()).isEmpty());
        assertNull(SelectionOutline.build(BlockSet.box(0, 0, 0, 99, 20, 99)), "muy grande: solo la caja");
    }

    private static int edges(List<PreviewOutline.Section> sections) {
        int count = 0;
        for (PreviewOutline.Section section : sections) {
            count += section.edges().length;
        }
        return count;
    }
}
