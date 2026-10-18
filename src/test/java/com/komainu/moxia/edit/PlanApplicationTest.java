package com.komainu.moxia.edit;

import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanApplicationTest {
    private static final long FAR = Long.MAX_VALUE;

    /**
     * Mundo de prueba con el historial de una sesión de WorldEdit: cada escritura apunta el bloque anterior, y
     * deshacer recorre el historial al revés. Puede fallar al llegar a un límite de cambios, como //limit.
     */
    private static final class World implements PlanApplication.Target {
        private final PackedBlockPlan plan;
        private final Map<Long, Integer> blocks = new HashMap<>();
        private final List<long[]> history = new ArrayList<>();
        private final int limit;

        World(PackedBlockPlan plan, int limit) {
            this.plan = plan;
            this.limit = limit;
        }

        @Override
        public int rawIdAt(int x, int y, int z) {
            return blocks.getOrDefault(PackedBlockPlan.pack(x, y, z), 0);
        }

        @Override
        public boolean write(int index, int x, int y, int z) throws MaxChangedBlocksException {
            if (history.size() >= limit) {
                throw new MaxChangedBlocksException(limit);
            }
            long packed = PackedBlockPlan.pack(x, y, z);
            history.add(new long[]{packed, rawIdAt(x, y, z)});
            blocks.put(packed, 100 + index);
            return true;
        }

        void undo() {
            for (int index = history.size() - 1; index >= 0; index--) {
                long[] change = history.get(index);
                if (change[1] == 0) {
                    blocks.remove(change[0]);
                } else {
                    blocks.put(change[0], (int) change[1]);
                }
            }
            history.clear();
        }
    }

    /** 50 escrituras en fila; las múltiplos de 10 esperan piedra (id 1) y las de 7 esperan aire. */
    private static PackedBlockPlan plan() {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(50);
        for (int index = 0; index < 50; index++) {
            BlockVector3 position = BlockVector3.at(index, 64, 0);
            if (index % 10 == 0) {
                builder.addConditionalPattern(position, 1);
            } else if (index % 7 == 0) {
                builder.addConditionalPattern(position, 0);
            } else {
                builder.addPattern(position);
            }
        }
        return builder.build();
    }

    private static Map<Long, Integer> initial(World world) {
        // Piedra en 0, 10 y 20 (se respetan las escrituras que la esperan); 30 y 40 tienen otra cosa (protegidas).
        world.blocks.put(PackedBlockPlan.pack(0, 64, 0), 1);
        world.blocks.put(PackedBlockPlan.pack(10, 64, 0), 1);
        world.blocks.put(PackedBlockPlan.pack(20, 64, 0), 1);
        world.blocks.put(PackedBlockPlan.pack(30, 64, 0), 5);
        world.blocks.put(PackedBlockPlan.pack(40, 64, 0), 5);
        world.blocks.put(PackedBlockPlan.pack(14, 64, 0), 9);
        return new HashMap<>(world.blocks);
    }

    @Test
    void cancellingHalfwayLeavesAnUndoThatCoversExactlyWhatWasPlaced() throws MaxChangedBlocksException {
        PackedBlockPlan plan = plan();
        World world = new World(plan, Integer.MAX_VALUE);
        Map<Long, Integer> before = initial(world);
        PlanApplication application = new PlanApplication(plan);

        for (int slice = 0; slice < 3; slice++) {
            assertEquals(7, application.step(world, 7, FAR, () -> 0L), "tramos de 7 entradas");
        }
        application.stop();
        assertTrue(application.done());
        assertEquals(0, application.step(world, 7, FAR, () -> 0L), "parado no sigue");

        assertEquals(21, application.processed());
        assertEquals(1, application.protectedBlocks(), "la 14 esperaba aire y había otra cosa");
        assertEquals(20, application.written());
        assertEquals(application.written(), world.history.size(), "el historial tiene justo lo escrito");
        for (int index = 21; index < 50; index++) {
            assertEquals(before.getOrDefault(PackedBlockPlan.pack(index, 64, 0), 0),
                    world.rawIdAt(index, 64, 0), "lo de después del corte sigue como estaba: " + index);
        }
        assertEquals(9, world.rawIdAt(14, 64, 0), "lo protegido no se toca");

        world.undo();
        assertEquals(before, world.blocks, "//undo deja el mundo como antes de empezar");
    }

    @Test
    void hittingTheWorldEditLimitStopsAtAnEntryBoundary() {
        PackedBlockPlan plan = plan();
        World world = new World(plan, 14);
        Map<Long, Integer> before = initial(world);
        PlanApplication application = new PlanApplication(plan);

        assertThrows(MaxChangedBlocksException.class, () -> application.step(world, 1_000, FAR, () -> 0L));
        assertEquals(14, world.history.size());
        assertEquals(14, application.written(), "las entradas 0 a 13");
        assertEquals(1, application.protectedBlocks(), "la 14 esperaba aire y había otra cosa");
        assertEquals(15, application.processed(), "la 15, la que chocó con el límite, no cuenta como hecha");
        assertFalse(application.done(), "queda pendiente; quien la lleva decide cerrarla");

        world.undo();
        assertEquals(before, world.blocks);
    }

    @Test
    void timeBudgetAndCompletion() throws MaxChangedBlocksException {
        PackedBlockPlan plan = plan();
        World world = new World(plan, Integer.MAX_VALUE);
        initial(world);
        PlanApplication application = new PlanApplication(plan);
        long[] clock = {0L};
        int done = application.step(world, 1_000, 5L, () -> clock[0]++);
        assertEquals(5, done, "corta al acabarse el tiempo del tick");

        while (!application.done()) {
            application.step(world, 1_000, FAR, () -> 0L);
        }
        assertEquals(50, application.processed());
        assertEquals(50, application.written() + application.protectedBlocks());
        assertEquals(3, application.protectedBlocks(), "14 (había otra cosa), 30 y 40 (no había piedra)");
    }
}
