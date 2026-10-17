package com.komainu.moxia.brush.ez;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PaletteShiftTest {
    private static final List<String> STONES = List.of("stone", "andesite", "cobblestone", "mossy_cobblestone");

    @Test
    void shiftMovesAlongThePalette() {
        PaletteShift shift = PaletteShift.of(STONES, false);
        assertEquals(2, shift.target("minecraft:stone", 2).index(), "con 2, el primero pasa al tercero");
        assertEquals(2, shift.target("andesite", 1).index());
        assertEquals(0, shift.target("andesite", -1).index(), "clic izquierdo: al revés");
    }

    @Test
    void endsClampUnlessWrapping() {
        assertNull(PaletteShift.of(STONES, false).target("mossy_cobblestone", 1), "el último no tiene adónde ir");
        assertNull(PaletteShift.of(STONES, false).target("stone", -3));
        assertEquals(0, PaletteShift.of(STONES, true).target("mossy_cobblestone", 1).index(), "con vuelta");
        assertEquals(3, PaletteShift.of(STONES, true).target("stone", -1).index());
    }

    @Test
    void blocksOutsideThePaletteStay() {
        assertNull(PaletteShift.of(STONES, false).target("minecraft:dirt", 1));
    }

    @Test
    void repeatedEntriesAlwaysChange() {
        PaletteShift shift = PaletteShift.of(List.of("stone", "stone", "stone", "andesite"), false);
        assertEquals(3, shift.target("stone", 1).index(), "hacia delante cuenta desde la última aparición");
        PaletteShift back = PaletteShift.of(List.of("andesite", "stone", "stone"), false);
        assertEquals(0, back.target("stone", -1).index(), "hacia atrás, desde la primera");
    }

    @Test
    void slabsAndStairsFollowTheirMaterial() {
        PaletteShift shift = new PaletteShift(
                List.of("stone", "cobblestone", "andesite"),
                List.of("stone_slab", "cobblestone_slab", "andesite_slab"),
                Arrays.asList("stone_stairs", null, "andesite_stairs"),
                false
        );
        PaletteShift.Target slab = shift.target("stone_slab", 1);
        assertEquals(PaletteShift.Kind.SLAB, slab.kind());
        assertEquals(1, slab.index());
        assertNull(shift.target("stone_stairs", 1), "el adoquín no tiene escalera en esta paleta");
        assertEquals(2, shift.target("stone_stairs", 2).index());
    }

    @Test
    void paintVisitsTheSphereOnly() {
        FakeWorld world = new FakeWorld(10);
        world.floor(0, "stone");
        world.set(0, 1, 0, "dirt", BrushWorld.Cell.FULL);
        PaletteShift shift = PaletteShift.of(STONES, false);
        List<int[]> changes = new ArrayList<>();
        int count = shift.paint(world, 0, 0, 0, 3, 1, (x, y, z, target) -> changes.add(new int[]{x, z, target.index()}));
        assertEquals(changes.size(), count);
        assertEquals(37, count, "disco de radio 3,5 en el suelo");
        for (int[] change : changes) {
            assertEquals(1, change[2]);
        }
    }
}
