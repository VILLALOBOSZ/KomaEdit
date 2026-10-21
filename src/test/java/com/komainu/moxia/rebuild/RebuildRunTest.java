package com.komainu.moxia.rebuild;

import com.komainu.moxia.edit.PackedBlockPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Una reconstrucción entera con un mundo que guarda lo escrito en un búfer hasta vaciarlo (como WorldEdit) y un reloj
 * de mentira: el ritmo tick a tick, pausar, seguir, acelerar, saltar al final, cancelar, lo que alguien cambia
 * mientras tanto y el presupuesto de cada tick.
 */
class RebuildRunTest {
    private static final long UNLIMITED = Long.MAX_VALUE / 4;

    private static RebuildFixtures.Fixture line(int length) {
        return RebuildFixtures.selection(new RebuildFixtures.Build().fill(0, 0, 0, length - 1, 0, 0, "stone"));
    }

    private static RebuildFixtures.Rig rig(RebuildFixtures.Fixture fixture, RebuildPace pace) {
        return new RebuildFixtures.Rig(fixture, RebuildOrder.LAYERS, pace);
    }

    /** Bloques levantados en cada tick hasta acabar. */
    private static List<Integer> builtPerTick(RebuildFixtures.Rig rig, int ticks) throws Exception {
        List<Integer> built = new ArrayList<>();
        for (int tick = 0; tick < ticks && !rig.run.done(); tick++) {
            int before = rig.run.step();
            rig.tick(UNLIMITED);
            built.add(rig.run.step() - before);
        }
        return built;
    }

    @Test
    void theSelectionIsClearedAndRisesAtThePaceAsked() throws Exception {
        RebuildFixtures.Rig rig = rig(line(20), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(4));
        assertTrue(rig.world.showsTarget(), "la obra está al empezar");
        rig.tick(UNLIMITED);
        assertEquals(RebuildRun.Phase.BUILDING, rig.run.phase());
        assertEquals(1, rig.run.step(), "vaciada, y el primer bloque, que toca ya");
        assertEquals(1, rig.world.blocks.size(), "el resto se ha quitado");
        assertEquals(List.of(4, 4, 4, 4, 3), builtPerTick(rig, 10));
        assertTrue(rig.run.done());
        assertEquals(RebuildRun.Phase.DONE, rig.run.phase());
        assertTrue(rig.world.showsTarget(), "y vuelve a estar entera");
        assertEquals(40, rig.world.writes, "una escritura para quitar y otra para poner cada bloque");
    }

    /**
     * Un cofre que alguien vacía (o que una tolva va llenando) entre la lectura y el vaciado: si se vuelve a poner lo
     * leído al empezar, los objetos que se sacaron entre medias se duplican y los que se metieron se pierden. Cada
     * bloque se anuncia, con su entrada de lo que había, justo antes de quitarlo, para guardar lo que lleva entonces.
     */
    @Test
    void eachBlockIsAnnouncedWithWhatItWasJustBeforeItIsTaken() throws Exception {
        RebuildFixtures.Fixture fixture = RebuildFixtures.paste(
                new RebuildFixtures.Build().fill(0, 0, 0, 3, 0, 0, "stone"),
                new RebuildFixtures.Build().set(0, 0, 0, "chest[facing=north]").set(2, 0, 0, "dirt"));
        RebuildFixtures.Rig rig = rig(fixture, RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(4));
        rig.tick(UNLIMITED);
        assertEquals(0, rig.world.unannounced, "vaciados sin avisar antes");
        assertEquals(2, rig.world.taken.size(), "el cofre y la tierra, lo único que había");
        for (int entry = 0; entry < fixture.model().size(); entry++) {
            long position = fixture.model().position(entry);
            if (fixture.model().before(entry) != RebuildModel.AIR) {
                assertEquals(fixture.model().before(entry), rig.world.taken.get(position));
            }
        }

        RebuildFixtures.Rig selection = rig(line(6), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(4));
        selection.tick(UNLIMITED);
        assertEquals(0, selection.world.unannounced);
        assertEquals(6, selection.world.taken.size(), "en la selección, cada bloque de la obra");
    }

    @Test
    void lessThanOneBlockPerTickWaitsBetweenBlocks() throws Exception {
        RebuildFixtures.Rig rig = rig(line(3), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(0.25));
        rig.tick(UNLIMITED);
        assertEquals(List.of(0, 0, 0, 1, 0, 0, 0, 1), builtPerTick(rig, 20));
    }

    @Test
    void theLeadInShowsTheEmptySiteFirst() throws Exception {
        RebuildFixtures.Rig rig = rig(line(4), RebuildPace.DEFAULT.withBlocksPerTick(4).withLeadIn(10));
        rig.tick(UNLIMITED);
        assertEquals(0, rig.run.step());
        assertTrue(rig.world.blocks.isEmpty(), "vacía");
        List<Integer> built = builtPerTick(rig, 20);
        assertEquals(List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 3), built,
                "diez ticks vacía, el primero justo al acabar la espera y luego los demás a su ritmo");
    }

    @Test
    void pauseStopsTheClockAndResumeCarriesOnWithoutABurst() throws Exception {
        RebuildFixtures.Rig rig = rig(line(40), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(2));
        rig.tick(UNLIMITED);
        builtPerTick(rig, 3);
        int step = rig.run.step();
        long clock = rig.run.animation();
        assertTrue(rig.run.pause(true));
        assertFalse(rig.run.pause(true), "ya estaba en pausa");
        assertFalse(rig.run.hasWorkNow());
        assertEquals(List.of(0, 0, 0, 0, 0), builtPerTick(rig, 5));
        assertEquals(step, rig.run.step());
        assertEquals(clock, rig.run.animation(), "en pausa el reloj no corre");
        assertTrue(rig.run.pause(false));
        assertEquals(List.of(2, 2), builtPerTick(rig, 2), "sigue al mismo ritmo, sin soltar lo de la pausa");
    }

    @Test
    void speedMultipliesThePace() throws Exception {
        RebuildFixtures.Rig rig = rig(line(60), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(2));
        rig.tick(UNLIMITED);
        builtPerTick(rig, 2);
        rig.run.speed(2.0);
        assertEquals(List.of(4, 4), builtPerTick(rig, 2));
        rig.run.speed(0.5);
        assertEquals(List.of(1, 1), builtPerTick(rig, 2));
        rig.run.speed(RebuildRun.MIN_SPEED);
        double remaining = rig.run.remainingTicks();
        rig.run.speed(1.0);
        assertEquals(remaining / 16.0, rig.run.remainingTicks(), 1e-9, "lo que queda se mide a la velocidad actual");
        assertThrows(IllegalArgumentException.class, () -> rig.run.speed(100));
        assertThrows(IllegalArgumentException.class, () -> rig.run.speed(0));
    }

    @Test
    void skipToEndPlacesWhatIsLeftAtOnce() throws Exception {
        RebuildFixtures.Rig rig = rig(line(50), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(1));
        rig.tick(UNLIMITED);
        builtPerTick(rig, 3);
        assertTrue(rig.run.skipToEnd());
        assertEquals(RebuildRun.Phase.FINISHING, rig.run.phase());
        assertFalse(rig.run.skipToEnd(), "ya terminaba");
        rig.tick(UNLIMITED);
        assertTrue(rig.run.done());
        assertTrue(rig.world.showsTarget());
        assertTrue(rig.run.drainPlaced().size() <= 4, "sin animación no hay efectos de lo que se pone de golpe");
    }

    @Test
    void skipToEndWhileClearingClearsFirst() throws Exception {
        RebuildFixtures.Fixture paste = RebuildFixtures.paste(
                new RebuildFixtures.Build().fill(0, 0, 0, 9, 0, 0, "oak_planks"),
                new RebuildFixtures.Build().fill(0, 0, 0, 9, 1, 0, "dirt"));
        RebuildFixtures.Rig rig = rig(paste, RebuildPace.DEFAULT);
        assertTrue(rig.run.skipToEnd());
        rig.tick(UNLIMITED);
        assertTrue(rig.run.done());
        assertTrue(rig.world.showsTarget(), "la tierra de encima vaciada y los tablones puestos");
    }

    @Test
    void cancellingTheSelectionPutsTheWholeBuildBack() throws Exception {
        RebuildFixtures.Fixture house = RebuildFixtures.selection(RebuildFixtures.house());
        RebuildFixtures.Rig rig = new RebuildFixtures.Rig(house, RebuildOrder.BUILDER,
                RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(8));
        rig.tick(UNLIMITED);
        builtPerTick(rig, 20);
        assertFalse(rig.world.showsTarget(), "a medias");
        assertTrue(rig.run.cancel());
        assertFalse(rig.run.cancel(), "ya se deshacía");
        assertFalse(rig.run.pause(true), "deshaciendo no se pausa");
        rig.runToEnd(10);
        assertEquals(RebuildRun.Phase.CANCELLED, rig.run.phase());
        assertTrue(rig.world.showsTarget(), "la casa entera, como estaba");
    }

    @Test
    void cancellingAPasteLeavesTheGroundAsItWas() throws Exception {
        RebuildFixtures.Fixture paste = RebuildFixtures.paste(RebuildFixtures.house(),
                new RebuildFixtures.Build().fill(-2, -1, -2, 16, 2, 16, "dirt"));
        RebuildFixtures.Rig rig = new RebuildFixtures.Rig(paste, RebuildOrder.BUILDER,
                RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(16));
        assertTrue(rig.world.showsBefore());
        rig.tick(UNLIMITED);
        builtPerTick(rig, 15);
        assertFalse(rig.world.showsBefore());
        assertFalse(rig.world.showsTarget());
        rig.run.cancel();
        rig.runToEnd(10);
        assertTrue(rig.world.showsBefore(), "la tierra otra vez, sin rastro de la casa");
    }

    @Test
    void cancellingWhileClearingOnlyUndoesWhatWasCleared() throws Exception {
        RebuildFixtures.Fixture paste = RebuildFixtures.paste(
                new RebuildFixtures.Build().fill(0, 0, 0, 99, 0, 0, "oak_planks"),
                new RebuildFixtures.Build().fill(0, 0, 0, 99, 0, 0, "dirt"));
        RebuildFixtures.Rig rig = rig(paste, RebuildPace.DEFAULT);
        rig.run.place(30, UNLIMITED);
        rig.world.flush();
        assertEquals(RebuildRun.Phase.CLEARING, rig.run.phase());
        assertEquals(70, rig.world.blocks.size(), "30 vaciados");
        rig.run.cancel();
        rig.runToEnd(10);
        assertTrue(rig.world.showsBefore());
        assertEquals(60, rig.world.writes, "30 para vaciar y 30 para volver a poner: lo demás ni se tocó");
    }

    @Test
    void whatSomeoneChangesMeanwhileIsKept() throws Exception {
        RebuildFixtures.Fixture fixture = line(10);
        RebuildFixtures.Rig rig = rig(fixture, RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(1));
        long fifth = PackedBlockPlan.pack(5, 0, 0);
        long eighth = PackedBlockPlan.pack(8, 0, 0);
        // Antes de vaciar alguien cambia uno; ya vacía, alguien pone algo en el sitio de otro.
        rig.world.blocks.put(fifth, 7);
        rig.tick(UNLIMITED);
        rig.world.blocks.put(eighth, 9);
        rig.runToEnd(50);
        assertTrue(rig.run.done());
        assertEquals(7, rig.world.at(fifth));
        assertEquals(9, rig.world.at(eighth));
        assertEquals(2, rig.run.kept());
        for (int x = 0; x < 10; x++) {
            if (x != 5 && x != 8) {
                assertEquals(fixture.model().raw(1), rig.world.at(PackedBlockPlan.pack(x, 0, 0)), "x " + x);
            }
        }
    }

    @Test
    void theClearedSiteReachesTheWorldBeforeTheFirstBlockIsChecked() throws Exception {
        // Con todo en una sola llamada (sin vaciar entre medias) la obra se perdería: la ventana se corta al pasar
        // de vaciar a levantar.
        RebuildFixtures.Rig rig = rig(line(5), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(4096));
        int first = rig.run.place(1_000, UNLIMITED);
        assertEquals(5, first, "solo vaciar");
        assertEquals(RebuildRun.Phase.BUILDING, rig.run.phase());
        rig.world.flush();
        rig.run.tick();
        rig.run.place(1_000, UNLIMITED);
        rig.world.flush();
        assertEquals(0, rig.run.kept());
        assertTrue(rig.world.showsTarget());
    }

    @Test
    void eachTickStaysInsideItsBudgetAndTheAnimationWaitsInsteadOfBursting() throws Exception {
        RebuildFixtures.Rig rig = rig(line(400), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(100));
        rig.world.read = 1_000L;
        rig.world.write = 2_000L;
        long budget = 30_000L;
        long before = rig.clock.now;
        rig.tick(budget);
        assertTrue(rig.clock.now - before <= budget + 3_000L, "vaciar también respeta el plazo");
        for (int tick = 0; tick < 40 && rig.run.phase() == RebuildRun.Phase.CLEARING; tick++) {
            before = rig.clock.now;
            rig.tick(budget);
            assertTrue(rig.clock.now - before <= budget + 3_000L);
        }
        assertEquals(RebuildRun.Phase.BUILDING, rig.run.phase());
        // Caben unos 10 bloques por tick y se piden 100: va más despacio, pero el reloj no se adelanta.
        for (int tick = 0; tick < 10; tick++) {
            before = rig.clock.now;
            rig.tick(budget);
            assertTrue(rig.clock.now - before <= budget + 3_000L, "tick " + tick);
        }
        assertTrue(rig.run.slowTicks() > 0);
        long ahead = rig.run.animation() - rig.run.timeline().due(rig.run.step());
        assertTrue(ahead <= RebuildTimeline.UNIT, "el reloj va como mucho un tick por delante: " + ahead);
        // Con presupuesto otra vez, no suelta de golpe todo lo que no pudo: sigue al ritmo pedido.
        int step = rig.run.step();
        rig.tick(UNLIMITED);
        assertTrue(rig.run.step() - step <= 200, "puso " + (rig.run.step() - step));
    }

    @Test
    void theEffectsListHasWhatTheAnimationPlaced() throws Exception {
        RebuildFixtures.Rig rig = rig(line(8), RebuildPace.DEFAULT.withLeadIn(0).withBlocksPerTick(4));
        rig.tick(UNLIMITED);
        rig.run.drainPlaced();
        rig.tick(UNLIMITED);
        assertEquals(4, rig.run.drainPlaced().size());
        assertEquals(0, rig.run.drainPlaced().size(), "cada bloque una vez");
    }
}
