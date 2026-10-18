package com.komainu.moxia.utility;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidEngineTest {
    private static FluidEngine.Sink sink(TestWorld world, Map<String, String> writes) {
        return new FluidEngine.Sink() {
            @Override
            public void air(int x, int y, int z) {
                writes.put(x + "," + y + "," + z, TestWorld.AIR);
            }

            @Override
            public void water(int x, int y, int z) {
                writes.put(x + "," + y + "," + z, TestWorld.WATER);
            }

            @Override
            public void waterlogged(int x, int y, int z, boolean value) {
                writes.put(x + "," + y + "," + z, TestWorld.withProperty(world.at(x, y, z), "waterlogged",
                        Boolean.toString(value)));
            }
        };
    }

    private static void apply(TestWorld world, Map<String, String> writes) {
        writes.forEach((key, block) -> {
            String[] parts = key.split(",");
            world.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), block);
        });
    }

    /** Un estanque con de todo: agua quieta y corriente, bloques anegados, plantas de agua y algo de lava. */
    private static TestWorld pond() {
        TestWorld world = new TestWorld(0, 0, 0, 8, 6, 8);
        world.fill(0, 0, 0, 7, 0, 7, "stone");
        world.fill(1, 1, 1, 6, 3, 6, TestWorld.WATER);
        world.set(2, 3, 2, "water[level=3]");
        world.set(3, 1, 3, "oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=true]");
        world.set(4, 1, 4, "kelp[age=3]").set(4, 2, 4, "kelp_plant");
        world.set(5, 1, 5, "seagrass").set(2, 1, 5, "bubble_column[drag=true]");
        world.set(6, 2, 1, "oak_fence[east=false,north=false,south=false,waterlogged=true,west=false]");
        world.set(1, 4, 6, TestWorld.LAVA);
        world.set(6, 4, 6, "oak_slab[type=bottom,waterlogged=false]");
        world.selectInside(0);
        return world;
    }

    @Test
    void drainingRemovesEveryDropOfWater() {
        TestWorld world = pond();
        Map<String, String> writes = new HashMap<>();
        FluidEngine.Result result = FluidEngine.drain(world.snapshot(), world.kinds(), FluidEngine.Drain.WATER, false,
                null, sink(world, writes), () -> {
                });
        apply(world, writes);
        BlockKinds kinds = world.kinds();
        var snapshot = world.snapshot();
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 6; y++) {
                for (int z = 0; z < 8; z++) {
                    int rawId = snapshot.rawId(x, y, z);
                    assertFalse(kinds.water(rawId) || kinds.waterlogged(rawId) || kinds.waterOnly(rawId),
                            "queda agua en " + x + "," + y + "," + z + ": " + world.at(x, y, z));
                }
            }
        }
        assertEquals(2, result.unwaterlogged(), "la escalera y la valla se secan");
        assertEquals("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]",
                world.at(3, 1, 3));
        assertEquals(TestWorld.LAVA, world.at(1, 4, 6), "drenar agua no toca la lava");
    }

    @Test
    void lavaAndPlantsOnRequest() {
        TestWorld world = pond();
        Map<String, String> writes = new HashMap<>();
        FluidEngine.drain(world.snapshot(), world.kinds(), FluidEngine.Drain.LAVA, false, null,
                sink(world, writes), () -> {
                });
        apply(world, writes);
        assertEquals(TestWorld.AIR, world.at(1, 4, 6));
        assertEquals(TestWorld.WATER, world.at(1, 1, 1), "drenar lava no toca el agua");

        TestWorld keep = pond();
        Map<String, String> kept = new HashMap<>();
        FluidEngine.drain(keep.snapshot(), keep.kinds(), FluidEngine.Drain.ALL, true, null, sink(keep, kept), () -> {
        });
        apply(keep, kept);
        assertEquals("minecraft:kelp[age=3]", keep.at(4, 1, 4), "-k conserva las algas");
        assertEquals(TestWorld.AIR, keep.at(2, 1, 5), "las burbujas son agua y se van igual");
        assertEquals(TestWorld.AIR, keep.at(1, 4, 6));
    }

    @Test
    void floodingFillsBelowTheLevelOnly() {
        TestWorld world = new TestWorld(0, 0, 0, 6, 8, 6);
        world.fill(0, 0, 0, 5, 0, 5, "stone");
        world.set(1, 1, 1, "oak_slab[type=bottom,waterlogged=false]");
        world.set(2, 3, 2, "oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]");
        world.set(3, 5, 3, "oak_slab[type=bottom,waterlogged=false]");
        world.set(4, 2, 4, "water[level=5]");
        world.set(1, 2, 4, "glass");
        world.select(0, 1, 0, 5, 7, 5);
        Map<String, String> writes = new HashMap<>();
        FluidEngine.Result result = FluidEngine.flood(world.snapshot(), world.kinds(), 3, false, null,
                sink(world, writes), () -> {
                });
        apply(world, writes);
        assertEquals("minecraft:oak_slab[type=bottom,waterlogged=true]", world.at(1, 1, 1));
        assertTrue(world.at(2, 3, 2).contains("waterlogged=true"), "el nivel se incluye");
        assertEquals("minecraft:oak_slab[type=bottom,waterlogged=false]", world.at(3, 5, 3), "por encima, no");
        assertEquals(TestWorld.WATER, world.at(4, 2, 4), "el agua corriente pasa a quieta");
        assertEquals(TestWorld.WATER, world.at(0, 3, 0));
        assertEquals(TestWorld.AIR, world.at(0, 4, 0));
        assertEquals("minecraft:glass", world.at(1, 2, 4), "lo que no admite agua se queda");
        assertEquals("minecraft:stone", world.at(0, 0, 0), "fuera de la selección no se toca");
        assertEquals(2, result.waterlogged());
        assertEquals(6 * 6 * 3 - 3, result.filled(), "todo el aire y el agua corriente hasta el nivel");

        TestWorld blocks = new TestWorld(0, 0, 0, 3, 3, 1);
        blocks.set(0, 0, 0, "oak_slab[type=bottom,waterlogged=false]");
        blocks.selectInside(0);
        Map<String, String> onlyBlocks = new HashMap<>();
        FluidEngine.flood(blocks.snapshot(), blocks.kinds(), 2, true, null, sink(blocks, onlyBlocks), () -> {
        });
        apply(blocks, onlyBlocks);
        assertEquals(1, onlyBlocks.size(), "-b solo anega bloques");
        assertEquals(TestWorld.AIR, blocks.at(1, 1, 0));
    }
}
