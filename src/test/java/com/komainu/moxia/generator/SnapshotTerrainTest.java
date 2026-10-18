package com.komainu.moxia.generator;

import com.komainu.moxia.region.RegionSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotTerrainTest {
    private static final RegionSnapshot.StateInfo AIR = new RegionSnapshot.StateInfo(0, null, "minecraft:air", true,
            false, false);
    private static final RegionSnapshot.StateInfo STONE = new RegionSnapshot.StateInfo(1, null, "minecraft:stone",
            false, false, true);
    private static final RegionSnapshot.StateInfo GRASS = new RegionSnapshot.StateInfo(2, null,
            "minecraft:grass_block", false, false, true, "snowy=false");
    private static final RegionSnapshot.StateInfo WATER = new RegionSnapshot.StateInfo(3, null, "minecraft:water",
            false, true, false, "level=0");
    private static final RegionSnapshot.StateInfo LOG = new RegionSnapshot.StateInfo(4, null, "minecraft:oak_log",
            false, false, true, "axis=y");

    /** 8×8 columnas: piedra hasta y=2, hierba en y=3; en x=0 agua en y=3; todo seleccionado salvo x=7. */
    private static RegionSnapshot hills() {
        RegionSnapshot.Builder builder = RegionSnapshot.builder(0, 0, 0, 8, 8, 8)
                .state(AIR).state(STONE).state(GRASS).state(WATER).state(LOG);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                for (int y = 0; y < 8; y++) {
                    int id = y < 3 ? 1 : y == 3 ? (x == 0 ? 3 : 2) : 0;
                    builder.set(x, y, z, id, x < 7);
                }
            }
        }
        return builder.build();
    }

    @Test
    void maskParsing() {
        SnapshotMask grass = SnapshotMask.parse("grass_block");
        assertTrue(grass.test(GRASS));
        assertFalse(grass.test(STONE));
        assertTrue(SnapshotMask.parse("!air").test(STONE));
        assertFalse(SnapshotMask.parse("!air").test(AIR));
        assertTrue(SnapshotMask.parse("#solid").test(LOG));
        assertTrue(SnapshotMask.parse("#liquid").test(WATER));
        assertTrue(SnapshotMask.parse("#existing").test(WATER));
        assertTrue(SnapshotMask.parse("oak_log[axis=y],stone").test(LOG));
        assertFalse(SnapshotMask.parse("oak_log[axis=x]").test(LOG));
        assertFalse(SnapshotMask.parse("#solid,!stone").test(STONE));
        assertTrue(SnapshotMask.parse("minecraft:stone").test(STONE));
        assertThrows(IllegalArgumentException.class, () -> SnapshotMask.parse("%50"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotMask.parse(""));
        assertThrows(IllegalArgumentException.class, () -> SnapshotMask.parse("stone,"));
    }

    @Test
    void surfaceAndMaskTops() {
        SnapshotTerrain terrain = new SnapshotTerrain(hills(), SnapshotMask.parse("stone"));
        assertEquals(6, terrain.maximumX(), "x=7 está fuera de la selección");
        assertEquals(3, terrain.surface(3, 3));
        assertEquals(3, terrain.surface(0, 3), "el agua cuenta como superficie");
        assertEquals(FlowField.Terrain.NONE, terrain.surface(7, 3));
        assertEquals(2, terrain.top(3, 3), "la máscara busca la piedra más alta");
        assertTrue(terrain.source(1, 1, 1));
        assertFalse(terrain.source(1, 3, 1));
        SnapshotTerrain plain = new SnapshotTerrain(hills(), (SnapshotMask) null);
        assertEquals(3, plain.top(4, 4));
    }

    @Test
    void noisegenOnTopOfTheMask() {
        SnapshotTerrain terrain = new SnapshotTerrain(hills(), SnapshotMask.parse("grass_block"));
        GeneratedBlocks blocks = new GeneratedBlocks(10_000);
        NoiseGenerator.generate(GeneratorRequests.noise(NoiseGenerator.Mode.HEIGHTMAP, "x Perlin(F:0.2) 4",
                        () -> 1L).settings(), terrain, terrain, PaletteTraits.of(List.of("dirt")), -64, 319, blocks);
        assertTrue(blocks.size() > 0);
        for (int index = 0; index < blocks.size(); index++) {
            assertTrue(blocks.y(index) >= 4, "encima de la hierba");
            assertTrue(blocks.x(index) >= 1 && blocks.x(index) <= 6, "ni sobre el agua ni fuera de la selección");
        }
    }
}
