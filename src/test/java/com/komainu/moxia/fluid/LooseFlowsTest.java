package com.komainu.moxia.fluid;

import com.komainu.moxia.utility.TestWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LooseFlowsTest {
    private static LooseFlows.Result clean(TestWorld world, LooseFlows.Target target) {
        return LooseFlows.clean(world.snapshot(), world.kinds(), target, false, null, FluidScenes.NOTHING);
    }

    /** Suelo en y=0 de x, z de -8 a 8; todo seleccionado menos un borde de un bloque. */
    private static TestWorld ground() {
        TestWorld world = new TestWorld(-8, -1, -8, 17, 8, 17);
        world.fill(-8, -1, -8, 8, 0, 8, "stone");
        world.selectInside(1);
        return world;
    }

    @Test
    void flowsFedByASourceStayAndTheOrphansGo() {
        TestWorld world = ground();
        // Una fuente con su corriente (de verdad la alimenta) …
        world.set(-4, 1, 0, TestWorld.WATER).set(-3, 1, 0, "water[level=1]").set(-2, 1, 0, "water[level=2]");
        // … y un charco suelto: corrientes sin ninguna fuente.
        world.set(3, 1, 3, "water[level=3]").set(4, 1, 3, "water[level=4]").set(3, 1, 4, "water[level=4]");
        // Una corriente con más nivel del que le puede llegar sigue viva (el juego solo la rebaja).
        world.set(-4, 1, 2, TestWorld.WATER).set(-4, 1, 3, "water[level=1]").set(-4, 1, 4, "water[level=6]");
        LooseFlows.Result result = clean(world, LooseFlows.Target.WATER);
        assertEquals(3, result.water(), "el charco suelto");
        FluidScenes.apply(world, result.writes());
        assertEquals(TestWorld.AIR, world.at(3, 1, 3));
        assertEquals(TestWorld.AIR, world.at(4, 1, 3));
        assertEquals("minecraft:water[level=2]", world.at(-2, 1, 0), "la alimentada se queda");
        assertEquals("minecraft:water[level=6]", world.at(-4, 1, 4));
    }

    @Test
    void aFallUnderASourceIsFedAndTooFarIsNot() {
        TestWorld world = ground();
        world.set(0, 6, 0, TestWorld.WATER);
        for (int y = 1; y <= 5; y++) {
            world.set(0, y, 0, "water[level=8]");
        }
        // Al pie de la cascada corre de lado; a ocho bloques del agua ya no le llega nada.
        world.set(1, 1, 0, "water[level=1]").set(2, 1, 0, "water[level=2]");
        world.set(0, 1, 7, "water[level=7]");
        LooseFlows.Result result = clean(world, LooseFlows.Target.ALL);
        assertEquals(1, result.removed());
        FluidScenes.apply(world, result.writes());
        assertEquals("minecraft:water[level=8]", world.at(0, 3, 0), "la cascada sale de la fuente");
        assertEquals("minecraft:water[level=2]", world.at(2, 1, 0));
        assertEquals(TestWorld.AIR, world.at(0, 1, 7));
    }

    @Test
    void lavaOnlyWhenAskedAndTheEdgeCountsAsFed() {
        TestWorld world = ground();
        world.set(2, 1, -2, "lava[level=2]");
        world.set(-8, 1, -5, "water[level=3]");
        world.set(-7, 1, -5, "water[level=4]");
        assertEquals(0, clean(world, LooseFlows.Target.WATER).lava(), "sin pedirlo, la lava no se toca");
        LooseFlows.Result result = clean(world, LooseFlows.Target.ALL);
        assertEquals(1, result.lava());
        assertEquals(0, result.water(), "la del borde puede venir de fuera; la de al lado, de ella");
        // Los bloques anegados también son fuentes.
        TestWorld logged = ground();
        logged.set(0, 1, 0, "oak_slab[type=bottom,waterlogged=true]").set(1, 1, 0, "water[level=1]");
        assertEquals(0, clean(logged, LooseFlows.Target.WATER).removed());
    }
}
