package com.komainu.moxia.region;

import com.komainu.moxia.brush.ez.BlockFilter;
import com.komainu.moxia.brush.ez.BrushMasks;
import com.komainu.moxia.deform.DeformArguments;
import com.komainu.moxia.generator.SnapshotMask;
import com.komainu.moxia.generator.SnapshotTerrain;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La {@code -m} de generadores, superficie y pinceles: la lista de bloques de antes se queda con su significado y lo
 * demás va a WorldEdit. Lo que necesita WorldEdit arrancado (parsear la máscara) no se prueba aquí.
 */
class RegionMasksTest {
    private static final Set<String> CLASSES = Set.of("#solid", "#existing", "#air", "#liquid");
    private static final RegionSnapshot.StateInfo AIR = new RegionSnapshot.StateInfo(0, null, "minecraft:air", true,
            false, false);
    private static final RegionSnapshot.StateInfo STONE = new RegionSnapshot.StateInfo(1, null, "minecraft:stone",
            false, false, true);
    private static final RegionSnapshot.StateInfo GRASS = new RegionSnapshot.StateInfo(2, null,
            "minecraft:grass_block", false, false, true, "snowy=false");

    @Test
    void plainListsAreTheOldSyntax() {
        assertTrue(RegionMasks.plainList("stone", CLASSES));
        assertTrue(RegionMasks.plainList("minecraft:stone, dirt", CLASSES));
        assertTrue(RegionMasks.plainList("#solid,!stone", CLASSES));
        assertTrue(RegionMasks.plainList("oak_log[axis=y];grass_block", CLASSES));
        assertTrue(RegionMasks.plainList("!air", CLASSES));
        assertTrue(RegionMasks.plainList("mimod:roca_rara", CLASSES));
    }

    @Test
    void everythingElseGoesToWorldEdit() {
        for (String mask : new String[]{"#surface", ">stone", "<air", "%50", "=y>64", "#near[stone][2]",
                "#fullblock", "#solid !sand", "##minecraft:logs", "#existing #fullblock", "#palette[##Magma]",
                "stone,", "", "!", "#region"}) {
            assertFalse(RegionMasks.plainList(mask, CLASSES), mask);
        }
    }

    @Test
    void generatorListsKeepTheirMeaning() {
        assertNull(SnapshotMask.LISTS.apply("#surface"));
        assertNull(SnapshotMask.LISTS.apply("#near[stone][3]"));
        var notStone = SnapshotMask.LISTS.apply("#solid,!stone");
        assertNotNull(notStone);
        assertTrue(notStone.test(GRASS));
        assertFalse(notStone.test(STONE));
        assertFalse(notStone.test(AIR));
    }

    @Test
    void listMasksAreReadFromTheBoundSnapshot() {
        RegionSnapshot snapshot = RegionSnapshot.builder(0, 0, 0, 2, 1, 1)
                .state(STONE).state(GRASS)
                .set(0, 0, 0, STONE.rawId(), true)
                .set(1, 0, 0, GRASS.rawId(), true)
                .build();
        RegionMasks.Bound bound = RegionMasks.list("grass_block", SnapshotMask.parse("grass_block")::test);
        bound.bind(snapshot);
        assertFalse(bound.test(0, 0, 0));
        assertTrue(bound.test(1, 0, 0));
        assertEquals(0, bound.padding(), "una lista no mira a los vecinos");
        assertEquals("grass_block", bound.toString());
        // SnapshotTerrain conecta la máscara él solo.
        SnapshotTerrain terrain = new SnapshotTerrain(snapshot, RegionMasks.list("stone",
                SnapshotMask.parse("stone")::test));
        assertTrue(terrain.source(0, 0, 0));
        assertFalse(terrain.source(1, 0, 0));
    }

    @Test
    void worldEditMasksAskForNeighboursAround() {
        assertEquals(1, RegionMasks.padding("#surface"));
        assertEquals(1, RegionMasks.padding(">stone"));
        assertTrue(RegionMasks.padding("#existing #near[water][4]") > 16);
    }

    @Test
    void currentIsRejectedBeforeTouchingTheWorld() {
        for (String mask : new String[]{"#current", "#c", "!#current", "#existing #c"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> RegionMasks.worldEdit(null, mask), mask);
            assertTrue(error.getMessage().contains("#current"), error.getMessage());
        }
    }

    @Test
    void brushListsStayBlockFilters() {
        assertSame(BlockFilter.FULL_BLOCK, BrushMasks.parse(null, null));
        assertTrue(BrushMasks.parse(null, "#fullblock,!sand") instanceof BlockFilter);
        assertTrue(BrushMasks.parse(null, "grass_block,moss_block") instanceof BlockFilter);
        assertThrows(IllegalArgumentException.class, () -> BrushMasks.parse(null, "#current"));
    }

    @Test
    void quotedValuesKeepTheirSpaces() {
        DeformArguments args = DeformArguments.parse("3 -m \"#solid !sand\" -c", Set.of("m"), "c");
        assertEquals("#solid !sand", args.value("m"));
        assertEquals("3", args.text(0));
        assertTrue(args.has('c'));
        assertEquals("#existing", DeformArguments.parse("-m \"#existing\"", Set.of("m"), "").value("m"));
        assertEquals("stone,dirt", DeformArguments.parse("-m stone,dirt", Set.of("m"), "").value("m"));
        assertThrows(IllegalArgumentException.class, () -> DeformArguments.parse("-m \"#solid !sand", Set.of("m"),
                ""));
    }
}
