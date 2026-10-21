package com.komainu.moxia.fluid;

import com.komainu.moxia.utility.TestWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las reglas de Minecraft para el agua y la lava, en mundos de texto. */
class FlowSimTest {
    private static final FlowSim.Rules WATER = FlowSim.Rules.of(FluidKind.WATER, false);

    private static TestWorld flat(int half) {
        TestWorld world = new TestWorld(-half, -2, -half, 2 * half + 1, 6, 2 * half + 1);
        world.fill(-half, -2, -half, half, 0, half, "stone");
        world.selectInside(0);
        return world;
    }

    private static TestWorld run(TestWorld world, FluidKind fluid, FlowSim.Rules rules, int[]... sources) {
        FlowSim sim = new FlowSim(world.snapshot(), world.kinds(), fluid, rules, FluidScenes.NOTHING);
        for (int[] source : sources) {
            sim.source(source[0], source[1], source[2]);
        }
        FlowSim.Result result = sim.run(10_000);
        FluidWrites writes = new FluidWrites(fluid);
        for (int entry = 0; entry < result.size(); entry++) {
            int level = result.level(entry);
            if (level < 0) {
                writes.air(result.x(entry), result.y(entry), result.z(entry));
            } else {
                writes.fluid(result.x(entry), result.y(entry), result.z(entry), level);
            }
        }
        FluidScenes.apply(world, writes);
        return world;
    }

    @Test
    void onFlatGroundWaterSpreadsSevenBlocksInADiamond() {
        TestWorld world = run(flat(10), FluidKind.WATER, WATER, new int[]{0, 1, 0});
        assertEquals("minecraft:water[level=0]", world.at(0, 1, 0));
        assertEquals("minecraft:water[level=3]", world.at(3, 1, 0), "pierde un nivel por bloque");
        assertEquals("minecraft:water[level=7]", world.at(7, 1, 0));
        assertEquals(TestWorld.AIR, world.at(8, 1, 0), "a 8 bloques ya no llega");
        assertEquals("minecraft:water[level=7]", world.at(4, 1, 3), "a 7 bloques en total");
        assertEquals(TestWorld.AIR, world.at(4, 1, 4));
        assertEquals(TestWorld.AIR, world.at(0, 2, 0), "no sube");
    }

    @Test
    void itRunsTowardsTheNearestDropAndFalls() {
        TestWorld world = flat(10);
        world.set(2, 0, 0, "air").set(2, -1, 0, "air");
        run(world, FluidKind.WATER, WATER, new int[]{0, 1, 0});
        assertEquals("minecraft:water[level=1]", world.at(1, 1, 0));
        assertEquals("minecraft:water[level=2]", world.at(2, 1, 0));
        assertEquals("minecraft:water[level=8]", world.at(2, 0, 0), "cae por el hueco");
        assertEquals("minecraft:water[level=8]", world.at(2, -1, 0));
        assertEquals(TestWorld.AIR, world.at(-1, 1, 0), "el lado contrario al hueco se queda seco");
        assertEquals(TestWorld.AIR, world.at(0, 1, 1));
    }

    @Test
    void twoSourcesMakeAThirdAndLavaGoesLessFar() {
        TestWorld world = run(flat(10), FluidKind.WATER, WATER, new int[]{-1, 1, 0}, new int[]{1, 1, 0});
        assertEquals("minecraft:water[level=0]", world.at(0, 1, 0), "agua infinita entre dos fuentes");

        TestWorld lava = run(flat(10), FluidKind.LAVA, FlowSim.Rules.of(FluidKind.LAVA, false), new int[]{0, 1, 0});
        assertEquals("minecraft:lava[level=2]", lava.at(1, 1, 0), "la lava pierde dos niveles por bloque");
        assertEquals("minecraft:lava[level=6]", lava.at(3, 1, 0));
        assertEquals(TestWorld.AIR, lava.at(4, 1, 0));
        TestWorld nether = run(flat(10), FluidKind.LAVA, FlowSim.Rules.of(FluidKind.LAVA, true), new int[]{0, 1, 0});
        assertEquals("minecraft:lava[level=4]", nether.at(4, 1, 0), "en el Nether corre como el agua");
    }

    @Test
    void wallsAndWaterloggedBlocksStopIt() {
        TestWorld world = flat(10);
        world.fill(2, 1, -6, 2, 1, 6, "stone_bricks");
        world.set(-2, 1, 0, "oak_slab[type=bottom,waterlogged=false]");
        run(world, FluidKind.WATER, WATER, new int[]{0, 1, 0});
        assertEquals(TestWorld.AIR, world.at(3, 1, 0), "la pared lo para");
        assertEquals("minecraft:oak_slab[type=bottom,waterlogged=false]", world.at(-2, 1, 0),
                "una corriente no anega");
        assertEquals("minecraft:water[level=5]", world.at(-3, 1, 0), "detrás de la losa llega rodeándola");
        assertTrue(FluidScenes.water(world, 1, 1, 0));
    }
}
