package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.utility.TestWorld;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CascadeTest {
    private static final FlowSim.Rules WATER = FlowSim.Rules.of(FluidKind.WATER, false);
    private static final Cascade.Settings DEFAULT = new Cascade.Settings(FluidKind.WATER, true, 2, 2, 1, true);

    private static Cascade.Result fall(TestWorld world, int x, int y, int z, Cascade.Settings settings) {
        return Cascade.fromPoint(world.snapshot(), world.kinds(), x, y, z, settings, WATER, 20_000,
                FluidScenes.NOTHING);
    }

    @Test
    void fromTheEdgeItFallsIntoAPoolWithoutFloodingTheBottom() {
        TestWorld world = FluidScenes.cliff();
        Cascade.Result result = fall(world, 10, 10, 5, DEFAULT);
        assertNull(result.flood(), "no inunda");
        assertEquals(9, result.fall(), "cae de y=9 a y=1");
        assertEquals(21, result.pools(), "una poza de radio 2");
        FluidScenes.apply(world, result.writes());
        assertEquals("minecraft:water[level=0]", world.at(10, 10, 5), "la fuente, metida en el borde");
        assertEquals("minecraft:water[level=1]", world.at(11, 10, 5), "sale por el lado abierto");
        for (int y = 1; y <= 9; y++) {
            assertEquals("minecraft:water[level=8]", world.at(11, y, 5), "cae en " + y);
        }
        assertEquals("minecraft:water[level=0]", world.at(11, 0, 5), "y entra en la poza");
        assertEquals(TestWorld.AIR, world.at(12, 1, 5), "abajo no se extiende");
        assertEquals(TestWorld.AIR, world.at(11, 1, 7));
    }

    @Test
    void withoutThePoolItWouldFloodAndSaysWhere() {
        TestWorld world = FluidScenes.cliff();
        Cascade.Result result = fall(world, 10, 10, 5, new Cascade.Settings(FluidKind.WATER, false, 2, 2, 1, true));
        assertTrue(result.floods(), "abajo el agua se extiende siete bloques");
        assertTrue(result.flood().outside() > 0);
        int x = PackedBlockPlan.unpackX(result.flood().at());
        assertTrue(x > 13 || Math.abs(PackedBlockPlan.unpackZ(result.flood().at()) - 5) > 2,
                "lo señalado está fuera del margen de la caída");
    }

    @Test
    void onASlopeThePoolSinksUntilItHolds() {
        TestWorld world = FluidScenes.cliff();
        // El suelo de abajo baja hacia el este: en x=13 ya no hay suelo a y=0.
        world.fill(13, -1, -10, 30, 0, 20, "air");
        Cascade.Result result = fall(world, 10, 10, 5, DEFAULT);
        assertNull(result.flood(), "la poza se hunde y se traga la caída");
        assertTrue(result.cuts() > 0 || result.pools() < 21, "más honda o más pequeña");
    }

    @Test
    void theSourceNeedsAWayOut() {
        TestWorld world = FluidScenes.cliff();
        IllegalArgumentException middle = assertThrows(IllegalArgumentException.class,
                () -> fall(world, 5, 10, 5, DEFAULT));
        assertTrue(middle.getMessage().contains("no tiene por dónde salir"), middle.getMessage());
        world.set(11, 10, 5, TestWorld.WATER);
        assertThrows(IllegalArgumentException.class, () -> fall(world, 11, 10, 5, DEFAULT), "ahí ya hay agua");
    }

    /** Una ladera que baja hacia el este un bloque cada dos, igual en todo z, con un bulto y un hoyo junto al cauce. */
    private static TestWorld hillside() {
        TestWorld world = new TestWorld(-5, -2, -5, 31, 20, 21);
        for (int x = -5; x <= 25; x++) {
            int top = 12 - Math.max(0, x) / 2;
            world.fill(x, -2, -5, x, top, 15, "dirt");
        }
        world.fill(10, 8, 5, 10, 9, 5, "dirt");
        world.set(6, 9, 4, "air");
        world.selectInside(0);
        return world;
    }

    @Test
    void alongThePathTheWaterNeverClimbsAndStaysInItsBed() {
        TestWorld world = hillside();
        Cascade.Result result = Cascade.alongPath(world.snapshot(), world.kinds(),
                List.of(new int[]{20, 2, 5}, new int[]{0, 12, 5}), DEFAULT, WATER, 20_000, FluidScenes.NOTHING);
        assertNull(result.flood(), "no se sale del cauce");
        assertTrue(result.cuts() >= 1, "el bulto se corta");
        assertTrue(result.banks() >= 1, "junto al hoyo hace falta orilla");
        FluidScenes.apply(world, result.writes());
        int previous = Integer.MAX_VALUE;
        for (int x = 0; x <= 20; x++) {
            int surface = Integer.MIN_VALUE;
            for (int y = 14; y >= -2; y--) {
                if (world.at(x, y, 5).equals(TestWorld.WATER)) {
                    surface = y;
                    break;
                }
            }
            assertTrue(surface != Integer.MIN_VALUE, "hay agua quieta en x=" + x);
            assertTrue(surface <= previous, "el agua nunca sube: x=" + x);
            previous = surface;
        }
        assertEquals(TestWorld.AIR, world.at(10, 9, 5), "el bulto, cortado");
        assertEquals("minecraft:dirt", world.at(6, 9, 4), "el hoyo de al lado, tapado con tierra");
        for (int x = -5; x <= 25; x++) {
            for (int y = -2; y <= 14; y++) {
                for (int z : new int[]{3, 4, 6, 7}) {
                    assertTrue(!FluidScenes.water(world, x, y, z), "agua fuera del cauce en " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void theCourseHasNoDiagonalSteps() {
        List<int[]> columns = Cascade.rasterize(List.of(new int[]{0, 10, 0}, new int[]{5, 0, 3}));
        assertEquals(9, columns.size(), "5 + 3 pasos más el primero");
        for (int index = 1; index < columns.size(); index++) {
            int dx = Math.abs(columns.get(index)[0] - columns.get(index - 1)[0]);
            int dz = Math.abs(columns.get(index)[2] - columns.get(index - 1)[2]);
            assertEquals(1, dx + dz, "un paso de un bloque, de lado");
        }
        assertEquals(0, columns.getLast()[1]);
    }
}
