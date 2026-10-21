package com.komainu.moxia.fluid;

import com.komainu.moxia.utility.TestWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluidBallTest {
    private static final FlowSim.Rules WATER = FlowSim.Rules.of(FluidKind.WATER, false);

    /** Suelo de piedra hasta y=4 con un hoyo de 5×5×3 (x, z de 3 a 7, y de 2 a 4); aire encima. */
    private static TestWorld crater() {
        TestWorld world = new TestWorld(-10, -2, -10, 31, 16, 31);
        world.fill(-10, -2, -10, 20, 4, 20, "stone");
        world.fill(3, 2, 3, 7, 4, 7, "air");
        world.selectInside(0);
        return world;
    }

    private static FluidBall.Result ball(TestWorld world, FluidBall.Settings settings) {
        return FluidBall.plan(world.snapshot(), world.kinds(), 5, 4, 5, settings, WATER, 20_000, FluidScenes.NOTHING);
    }

    @Test
    void closingTheEdgesKeepsOnlyWhatTheGroundHolds() {
        TestWorld world = crater();
        FluidBall.Result result = ball(world, new FluidBall.Settings(FluidKind.WATER, 3, false, true, true));
        assertEquals(75, result.sources(), "solo el hoyo: lo de encima del suelo se derramaría");
        assertTrue(result.trimmed() > 0);
        assertEquals(0, result.flows(), "ningún flujo suelto");
        assertFalse(result.overflow());
        FluidScenes.apply(world, result.writes());
        for (int x = 3; x <= 7; x++) {
            for (int z = 3; z <= 7; z++) {
                for (int y = 2; y <= 4; y++) {
                    assertEquals(TestWorld.WATER, world.at(x, y, z));
                }
                assertEquals(TestWorld.AIR, world.at(x, 5, z), "encima del suelo, nada");
            }
        }
    }

    @Test
    void withoutClosingTheEdgesTheFlowsComeToo() {
        TestWorld world = crater();
        FluidBall.Result open = ball(world, new FluidBall.Settings(FluidKind.WATER, 3, false, true, false));
        assertTrue(open.sources() > 75, "también el aire de encima del suelo");
        assertTrue(open.flows() > 0, "y las corrientes que salen de él, ya puestas");
        FluidBall.Result sources = ball(world, new FluidBall.Settings(FluidKind.WATER, 3, true, true, false));
        assertEquals(open.sources(), sources.sources());
        assertEquals(0, sources.flows(), "solo fuentes: ninguna corriente");
    }

    @Test
    void replacingEverythingButBlocksWithData() {
        TestWorld world = crater();
        world.set(5, 1, 5, "chest");
        FluidBall.Result result = FluidBall.plan(world.snapshot(), world.kinds(), 5, 0, 5,
                new FluidBall.Settings(FluidKind.LAVA, 2, true, false, false), WATER, 20_000, FluidScenes.NOTHING);
        assertEquals(1, result.kept(), "el cofre se respeta");
        assertEquals(81 - 1, result.sources(), "la esfera de radio 2 (81 bloques) menos el cofre");
        FluidScenes.apply(world, result.writes());
        assertEquals("minecraft:lava[level=0]", world.at(5, 0, 5));
        assertEquals("minecraft:chest", world.at(5, 1, 5));
    }

    @Test
    void powderSnowFillsTheAirAndNeverFlows() {
        TestWorld world = crater();
        FluidBall.Result result = ball(world, new FluidBall.Settings(FluidKind.POWDER_SNOW, 3, false, true, true));
        assertEquals(0, result.flows());
        assertEquals(0, result.trimmed(), "la nieve no se derrama: no hay bordes que cerrar");
        FluidScenes.apply(world, result.writes());
        assertEquals("minecraft:powder_snow", world.at(5, 6, 5));
        assertThrows(IllegalArgumentException.class, () -> new FluidBall.Settings(FluidKind.WATER, 40, false, false,
                false));
    }
}
