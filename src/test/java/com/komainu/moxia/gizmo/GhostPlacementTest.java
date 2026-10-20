package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostPlacementTest {
    /** Un mundo de mentira: aire salvo lo que se ponga; con zonas donde no se puede construir. */
    private static final class World implements GhostPlacement.World {
        private final Long2IntOpenHashMap blocks = new Long2IntOpenHashMap();
        private final FakeStates states;
        private final java.util.function.LongPredicate forbidden;

        World(FakeStates states, java.util.function.LongPredicate forbidden) {
            this.states = states;
            this.forbidden = forbidden;
        }

        void set(int x, int y, int z, int state) {
            blocks.put(PackedBlockPlan.pack(x, y, z), state);
        }

        @Override
        public int stateAt(int x, int y, int z) {
            return blocks.get(PackedBlockPlan.pack(x, y, z));
        }

        @Override
        public boolean isAir(int state) {
            return states.isAir(state);
        }

        @Override
        public boolean canBuild(int x, int y, int z) {
            return !forbidden.test(PackedBlockPlan.pack(x, y, z));
        }
    }

    /** Una casita: suelo de piedra, troncos en las esquinas, escaleras y un dispensador, con aire dentro. */
    private static GhostModel house(FakeStates states, int baseX, int baseY, int baseZ, boolean withAir) {
        GhostModel.Builder builder = GhostModel.builder(256);
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 4; z++) {
                builder.add(baseX + x, baseY, baseZ + z, states.state("stone"), false, PackedBlockPlan.NO_CAPTURE);
                boolean corner = (x == 0 || x == 4) && (z == 0 || z == 3);
                for (int y = 1; y <= 3; y++) {
                    if (corner) {
                        builder.add(baseX + x, baseY + y, baseZ + z, states.state("oak_log", "axis", "y"), false,
                                PackedBlockPlan.NO_CAPTURE);
                    } else if (withAir) {
                        builder.add(baseX + x, baseY + y, baseZ + z, 0, true, PackedBlockPlan.NO_CAPTURE);
                    }
                }
            }
        }
        builder.add(baseX + 2, baseY + 1, baseZ - 1, states.state("oak_stairs", "facing", "north"), false,
                PackedBlockPlan.NO_CAPTURE);
        builder.add(baseX + 1, baseY + 4, baseZ + 1, states.state("dispenser", "facing", "up"), false,
                PackedBlockPlan.NO_CAPTURE);
        return builder.build();
    }

    @Test
    void theModelKeepsThePivotInTheMiddleAndTheLastBlockOfARepeatedPosition() {
        FakeStates states = new FakeStates();
        GhostModel.Builder builder = GhostModel.builder(4);
        builder.add(10, 64, 10, states.state("stone"), false, PackedBlockPlan.NO_CAPTURE);
        builder.add(14, 66, 13, states.state("stone"), false, PackedBlockPlan.NO_CAPTURE);
        builder.add(10, 64, 10, states.state("oak_log"), false, 7);
        GhostModel model = builder.build();
        assertEquals(2, model.size());
        assertEquals(12, model.pivotX());
        assertEquals(65, model.pivotY());
        assertEquals(11, model.pivotZ());
        assertEquals(new BlockBounds(-2, -1, -1, 2, 1, 2), model.bounds());
        assertEquals(states.state("oak_log"), model.state(0));
        assertEquals(7, model.capture(0));
        assertTrue(model.hasCaptures());
        // Un punto del mundo de partida viaja con el objeto.
        GhostTransform moved = model.start().moved(5, 0, 0).turned(Orientation.Y, 1);
        assertArrayEquals(new int[]{18, 64, 9}, model.carry(10, 64, 10, moved));
        assertArrayEquals(new int[]{10, 64, 10}, model.carry(10, 64, 10, model.start()));
    }

    @Test
    void confirmingPlacesExactlyWhatWasShown() {
        FakeStates states = new FakeStates();
        GhostModel model = house(states, 100, 64, 200, false);
        SplittableRandom random = new SplittableRandom(7);
        for (int index = 0; index < Orientation.COUNT; index++) {
            Orientation orientation = Orientation.of(index);
            GhostTransform transform = new GhostTransform(orientation, 100 + random.nextInt(-20, 20),
                    70 + random.nextInt(-5, 5), 200 + random.nextInt(-20, 20));
            // Lo que dibuja el cliente: las posiciones locales giradas y desplazadas, con los estados girados.
            long[] oriented = GhostPlacement.orientPositions(model.positions(), orientation);
            int[] turned = GhostPlacement.orientStates(model.states(), orientation, states);
            Map<Long, Integer> drawn = new HashMap<>();
            for (int entry = 0; entry < model.size(); entry++) {
                long local = oriented[entry];
                drawn.put(PackedBlockPlan.pack(transform.x() + PackedBlockPlan.unpackX(local),
                        transform.y() + PackedBlockPlan.unpackY(local),
                        transform.z() + PackedBlockPlan.unpackZ(local)), turned[entry]);
            }
            GhostPlacement.Blocks shown = GhostPlacement.shown(model, transform, states);
            assertEquals(drawn.size(), shown.size());
            for (int entry = 0; entry < shown.size(); entry++) {
                assertEquals(drawn.get(shown.positions()[entry]), shown.states()[entry]);
            }
            // Lo que coloca el servidor al confirmar en un mundo vacío.
            GhostPlacement.Planner planner = new GhostPlacement.Planner(model, transform, GhostOptions.DEFAULT, states,
                    new World(states, packed -> false));
            while (!planner.step(7)) {
                // por tramos, como entre ticks
            }
            GhostPlacement.Writes writes = planner.writes();
            Map<Long, Integer> placed = new HashMap<>();
            for (int entry = 0; entry < writes.size(); entry++) {
                placed.put(writes.positions()[entry], writes.states()[entry]);
                assertEquals(0, writes.expected()[entry]);
            }
            assertEquals(drawn, placed, "orientación " + index);
        }
    }

    @Test
    void theTurnedStatesAreTheTurnedBlocks() {
        FakeStates states = new FakeStates();
        GhostModel model = house(states, 0, 64, 0, false);
        GhostTransform transform = model.start().turned(Orientation.Y, 1);
        GhostPlacement.Blocks shown = GhostPlacement.shown(model, transform, states);
        boolean stairs = false;
        for (int entry = 0; entry < shown.size(); entry++) {
            int state = shown.states()[entry];
            if (states.block(state).equals("oak_stairs")) {
                // Miraba al norte; un cuarto de vuelta de este a sur la deja mirando al este.
                assertEquals("east", states.value(state, "facing"));
                stairs = true;
            }
        }
        assertTrue(stairs);
        GhostTransform tipped = model.start().turned(Orientation.X, 1);
        GhostPlacement.Blocks lying = GhostPlacement.shown(model, tipped, states);
        for (int entry = 0; entry < lying.size(); entry++) {
            int state = lying.states()[entry];
            if (states.block(state).equals("oak_log")) {
                assertEquals("z", states.value(state, "axis"));
            }
            if (states.block(state).equals("dispenser")) {
                assertEquals("south", states.value(state, "facing"));
            }
        }
    }

    @Test
    void keepsWhatIsThereAndOnlyPastesAirWhenAsked() {
        FakeStates states = new FakeStates();
        GhostModel model = house(states, 0, 64, 0, true);
        assertTrue(model.hasAir());
        World world = new World(states, packed -> false);
        GhostTransform transform = model.start();
        // Un bloque de tierra donde iría un tronco y otro donde iría aire.
        int dirt = states.state("oak_slab", "type", "double");
        world.set(0, 65, 0, dirt);
        world.set(2, 66, 1, dirt);
        GhostPlacement.Writes plain = run(model, transform, GhostOptions.DEFAULT.withPasteAir(false), states, world);
        assertFalse(contains(plain, 2, 66, 1));
        assertTrue(contains(plain, 0, 65, 0));
        GhostPlacement.Writes kept = run(model, transform, new GhostOptions(true, false, false), states, world);
        assertFalse(contains(kept, 0, 65, 0));
        assertEquals(1, kept.kept());
        GhostPlacement.Writes air = run(model, transform, new GhostOptions(false, true, false), states, world);
        assertTrue(contains(air, 2, 66, 1));
        int index = indexOf(air, 2, 66, 1);
        assertEquals(0, air.states()[index]);
        assertEquals(dirt, air.expected()[index]);
        // El aire sobre aire no cambia nada: no se escribe.
        assertFalse(contains(air, 1, 66, 1));
        assertTrue(air.unchanged() > 0);
    }

    @Test
    void stopsAtTheFirstPlaceWhereThePlayerCannotBuild() {
        FakeStates states = new FakeStates();
        GhostModel model = house(states, 0, 64, 0, false);
        long protectedPosition = PackedBlockPlan.pack(3, 64, 2);
        World world = new World(states, packed -> packed == protectedPosition);
        GhostPlacement.Planner planner = new GhostPlacement.Planner(model, model.start(), GhostOptions.DEFAULT, states,
                world);
        assertTrue(planner.step(10_000));
        assertTrue(planner.blocked());
        assertEquals(protectedPosition, planner.blockedAt());
        // Movido fuera de la zona, sí.
        GhostPlacement.Planner moved = new GhostPlacement.Planner(model, model.start().moved(20, 0, 0),
                GhostOptions.DEFAULT, states, world);
        assertTrue(moved.step(10_000));
        assertFalse(moved.blocked());
        assertEquals(model.size(), moved.writes().size());
    }

    @Test
    void studioInstancesKeepTheirCaptureIndexEvenWhenNothingChanges() {
        FakeStates states = new FakeStates();
        GhostModel.Builder builder = GhostModel.builder(3);
        builder.add(0, 64, 0, states.state("stone"), false, 0);
        builder.add(1, 64, 0, states.state("stone"), false, 1);
        GhostModel model = builder.build();
        World world = new World(states, packed -> false);
        world.set(0, 64, 0, states.state("stone"));
        GhostPlacement.Writes writes = run(model, model.start(), GhostOptions.DEFAULT, states, world);
        assertEquals(2, writes.size());
        assertEquals(new HashSet<>(java.util.List.of(0, 1)),
                new HashSet<>(java.util.List.of(writes.captures()[0], writes.captures()[1])));
    }

    @Test
    void exposedFacesTurnWithTheBlocks() {
        FakeStates states = new FakeStates();
        GhostModel model = house(states, 0, 64, 0, false);
        com.komainu.moxia.preview.PreviewSurface.Selection local = com.komainu.moxia.preview.PreviewSurface.select(
                model.positions(), model.size(), 100_000);
        for (int index = 0; index < Orientation.COUNT; index++) {
            Orientation orientation = Orientation.of(index);
            long[] oriented = GhostPlacement.orientPositions(model.positions(), orientation);
            com.komainu.moxia.preview.PreviewSurface.Selection turned =
                    com.komainu.moxia.preview.PreviewSurface.select(oriented, oriented.length, 100_000);
            Map<Long, Integer> expected = new HashMap<>();
            for (int slot = 0; slot < turned.shown(); slot++) {
                expected.put(oriented[turned.indices()[slot]],
                        turned.faces()[slot] & com.komainu.moxia.preview.PreviewSurface.ALL_FACES);
            }
            for (int slot = 0; slot < local.shown(); slot++) {
                int entry = local.indices()[slot];
                int faces = orientation.faces(local.faces()[slot] & com.komainu.moxia.preview.PreviewSurface.ALL_FACES);
                assertEquals(expected.get(oriented[entry]), faces, "orientación " + index);
            }
        }
    }

    private static GhostPlacement.Writes run(GhostModel model, GhostTransform transform, GhostOptions options,
                                             FakeStates states, World world) {
        GhostPlacement.Planner planner = new GhostPlacement.Planner(model, transform, options, states, world);
        while (!planner.step(16)) {
            // tramos
        }
        return planner.writes();
    }

    private static boolean contains(GhostPlacement.Writes writes, int x, int y, int z) {
        return indexOf(writes, x, y, z) >= 0;
    }

    private static int indexOf(GhostPlacement.Writes writes, int x, int y, int z) {
        long packed = PackedBlockPlan.pack(x, y, z);
        for (int index = 0; index < writes.size(); index++) {
            if (writes.positions()[index] == packed) {
                return index;
            }
        }
        return -1;
    }
}
