package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.utility.TestWorld;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloodFillTest {
    private static FloodFill.Settings settings(FloodFill.Level level) {
        return new FloodFill.Settings(FluidKind.WATER, level, FloodFill.Directions.DEFAULT, 50_000, false, -64);
    }

    private static FloodFill.Result fill(TestWorld world, int x, int y, int z, FloodFill.Settings settings) {
        return FloodFill.plan(world.snapshot(), world.kinds(), x, y, z, settings, FluidScenes.NOTHING);
    }

    @Test
    void aClosedPoolFillsUpToTheClick() {
        TestWorld world = FluidScenes.pool();
        FloodFill.Result result = fill(world, 3, 3, 3, settings(FloodFill.Level.CLICK));
        assertTrue(result.closed());
        assertEquals(3, result.level());
        assertEquals(5 * 5 * 3, result.filled(), "el hueco de 5×5 de y=1 a y=3");
        FluidScenes.apply(world, FluidScenes.writes(result, FluidKind.WATER));
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                for (int y = 1; y <= 3; y++) {
                    assertTrue(FluidScenes.water(world, x, y, z), "agua en " + x + "," + y + "," + z);
                }
                assertEquals(TestWorld.AIR, world.at(x, 4, z), "por encima del nivel, nada");
            }
        }
        assertEquals(TestWorld.AIR, world.at(8, 1, 3), "fuera de la piscina, nada");
    }

    @Test
    void aGapInTheWallIsFoundPluggedAndTheLevelThatHolds() {
        TestWorld world = FluidScenes.pool();
        world.set(6, 2, 3, "air");
        FloodFill.Result result = fill(world, 3, 4, 3, settings(FloodFill.Level.CLICK));
        assertFalse(result.closed(), "se escaparía por el hueco de la pared");
        assertEquals(0, result.size(), "no se escribe nada");
        FloodFill.Leak leak = result.leak();
        assertEquals(FloodFill.Reason.BOUNDS, leak.reason(), "fuera corre hasta el borde de lo leído");
        assertArrayEquals(new long[]{PackedBlockPlan.pack(6, 2, 3)}, leak.hole(), "el hueco es ese bloque");
        assertTrue(leak.pluggable(), "tapándolo cierra");
        assertEquals(1, leak.highestClosed(), "por debajo del hueco sí cierra");
        assertEquals(2, leak.spillLevel(), "la primera altura que se escapa");
        assertTrue(leak.path().length >= 2, "el camino del clic a la salida");

        // Tapado, el mismo clic cierra.
        world.set(6, 2, 3, "stone_bricks");
        assertTrue(fill(world, 3, 4, 3, settings(FloodFill.Level.CLICK)).closed());
    }

    @Test
    void aHoleInTheFloorIsFoundToo() {
        TestWorld world = FluidScenes.pool();
        world.fill(-6, -5, -6, 12, -1, 12, "air");
        world.set(3, 0, 3, "air");
        FloodFill.Result result = fill(world, 3, 3, 3, settings(FloodFill.Level.CLICK));
        FloodFill.Leak leak = result.leak();
        assertArrayEquals(new long[]{PackedBlockPlan.pack(3, 0, 3)}, leak.hole(), "el agujero del suelo");
        assertTrue(leak.pluggable());
        assertFalse(leak.closesSomewhere(), "se va por abajo a cualquier altura");
    }

    @Test
    void levelsFromTheClickFixedOrTheBrim() {
        TestWorld world = FluidScenes.pool();
        assertEquals(50, fill(world, 3, 1, 3, settings(FloodFill.Level.fixed(2))).filled(), "hasta y=2");
        FloodFill.Result brim = fill(world, 3, 1, 3, settings(FloodFill.Level.BRIM));
        assertEquals(4, brim.level(), "lo más alto que aguanta: lo alto de las paredes");
        assertEquals(100, brim.filled());

        world.set(6, 2, 3, "air");
        assertEquals(1, fill(world, 3, 1, 3, settings(FloodFill.Level.BRIM)).level(), "con el hueco, solo y=1");

        world.set(3, 6, 3, "stone");
        IllegalArgumentException ceiling = assertThrows(IllegalArgumentException.class,
                () -> fill(world, 3, 3, 3, settings(FloodFill.Level.fixed(7))));
        assertTrue(ceiling.getMessage().contains("y=6"), ceiling.getMessage());
        assertThrows(IllegalArgumentException.class, () -> fill(world, 0, 2, 0, settings(FloodFill.Level.CLICK)),
                "en un bloque no se puede empezar");
    }

    @Test
    void theLimitStopsBigHoles() {
        TestWorld world = FluidScenes.pool();
        FloodFill.Settings small = new FloodFill.Settings(FluidKind.WATER, FloodFill.Level.fixed(4),
                FloodFill.Directions.DEFAULT, 60, false, -64);
        FloodFill.Result result = fill(world, 3, 3, 3, small);
        assertEquals(FloodFill.Reason.LIMIT, result.leak().reason());
        assertEquals(2, result.leak().highestClosed(), "hasta y=2 son 50 bloques: caben en 60");
        FloodFill.Settings enough = new FloodFill.Settings(FluidKind.WATER, FloodFill.Level.fixed(4),
                FloodFill.Directions.DEFAULT, 100, false, -64);
        assertTrue(fill(world, 3, 3, 3, enough).closed(), "100 bloques justos caben");
    }

    @Test
    void directionsDecideWhereItSpreadsButNotWhereItMustBeClosed() {
        TestWorld world = FluidScenes.pool();
        FloodFill.Directions layer = new FloodFill.Directions(true, false, false);
        FloodFill.Settings flat = new FloodFill.Settings(FluidKind.WATER, FloodFill.Level.CLICK, layer, 50_000, false,
                -64);
        assertEquals(25, fill(world, 3, 1, 3, flat).filled(), "una capa con suelo debajo");
        FloodFill.Result floating = fill(world, 3, 3, 3, flat);
        assertEquals(FloodFill.Reason.BELOW, floating.leak().reason(), "debajo hay hueco y no baja");
        assertEquals(PackedBlockPlan.pack(3, 2, 3), floating.leak().exit());

        // Un corral con la esquina abierta: el agua no pasa en diagonal, salvo que se pida.
        TestWorld pen = new TestWorld(-4, 0, -4, 13, 4, 13);
        pen.fill(-4, 0, -4, 8, 0, 8, "stone");
        pen.fill(0, 1, 0, 4, 1, 4, "stone_bricks");
        pen.fill(1, 1, 1, 3, 1, 3, "air");
        pen.set(4, 1, 4, "air");
        FloodFill.Result square = FloodFill.plan(pen.snapshot(), pen.kinds(), 2, 1, 2,
                settings(FloodFill.Level.CLICK), FluidScenes.NOTHING);
        assertTrue(square.closed(), "sin esquinas, la diagonal no es un hueco");
        assertEquals(9, square.filled());
        FloodFill.Settings corners = new FloodFill.Settings(FluidKind.WATER, FloodFill.Level.CLICK,
                FloodFill.Directions.parse("hbe"), 50_000, false, -64);
        assertFalse(FloodFill.plan(pen.snapshot(), pen.kinds(), 2, 1, 2, corners, FluidScenes.NOTHING).closed(),
                "con esquinas se escapa por la diagonal");
        assertEquals("hb", FloodFill.Directions.parse("horizontal,abajo").letters());
        assertThrows(IllegalArgumentException.class, () -> FloodFill.Directions.parse("e"));
    }

    @Test
    void waterloggingOnRequest() {
        TestWorld world = FluidScenes.pool();
        world.set(3, 1, 3, "oak_slab[type=bottom,waterlogged=false]");
        FloodFill.Result dry = fill(world, 3, 3, 3, settings(FloodFill.Level.CLICK));
        assertEquals(74, dry.filled(), "sin -w la losa hace de pared");
        assertEquals(0, dry.waterlogged());
        FloodFill.Settings wet = new FloodFill.Settings(FluidKind.WATER, FloodFill.Level.CLICK,
                FloodFill.Directions.DEFAULT, 50_000, true, -64);
        FloodFill.Result logged = fill(world, 3, 3, 3, wet);
        assertEquals(1, logged.waterlogged());
        FluidScenes.apply(world, FluidScenes.writes(logged, FluidKind.WATER));
        assertEquals("minecraft:oak_slab[type=bottom,waterlogged=true]", world.at(3, 1, 3));

        // Una valla en el hueco de la pared: cierra sin anegar, pero anegada dejaría salir el agua.
        TestWorld fence = FluidScenes.pool();
        fence.set(6, 2, 3, "oak_fence[east=false,north=false,south=false,waterlogged=false,west=false]");
        assertTrue(fill(fence, 3, 4, 3, settings(FloodFill.Level.CLICK)).closed());
        assertFalse(fill(fence, 3, 4, 3, wet).closed());
    }

    @Test
    void lavaTreatsWaterAsAWallAndCountsTheContacts() {
        TestWorld world = FluidScenes.pool();
        world.set(3, 1, 3, TestWorld.WATER);
        FloodFill.Settings lava = new FloodFill.Settings(FluidKind.LAVA, FloodFill.Level.CLICK,
                FloodFill.Directions.DEFAULT, 50_000, true, -64);
        FloodFill.Result result = fill(world, 3, 2, 3, lava);
        assertTrue(result.closed());
        assertEquals(49, result.filled(), "todo menos el agua");
        assertEquals(1, result.contacts(), "toca el agua una vez");
        assertNull(result.leak());
    }
}
