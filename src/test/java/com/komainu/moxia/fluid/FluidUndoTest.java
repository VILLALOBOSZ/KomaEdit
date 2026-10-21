package com.komainu.moxia.fluid;

import com.komainu.moxia.edit.FakeStates;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.PlanApplication;
import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.utility.TestWorld;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.world.block.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que escriben las herramientas de agua va por el plan del motor común: escrituras condicionales que respetan lo
 * que haya cambiado mientras tanto y que el historial de WorldEdit deshace enteras (//undo). El mundo de prueba
 * apunta cada escritura como la sesión de WorldEdit.
 */
class FluidUndoTest {
    /** Estados de mentira con su texto, para saber qué escribió el plan. */
    private static final class Names implements FluidStates.Source {
        final Map<BlockState, String> text = new IdentityHashMap<>();
        private final Map<String, BlockState> states = new HashMap<>();

        private BlockState of(String name) {
            return states.computeIfAbsent(name, key -> {
                BlockState state = FakeStates.fake();
                text.put(state, key);
                return state;
            });
        }

        @Override
        public BlockState air() {
            return of(TestWorld.AIR);
        }

        @Override
        public BlockState fluid(FluidKind fluid, int level) {
            return of(fluid.blockId() + "[level=" + level + "]");
        }

        @Override
        public BlockState with(BlockState state, String property, boolean value) {
            return of(text.get(state) + "+" + property);
        }
    }

    /** El mundo con el historial de una sesión de WorldEdit. */
    private static final class World implements PlanApplication.Target {
        final Map<Long, String> blocks = new HashMap<>();
        final Map<Long, Integer> ids = new HashMap<>();
        final List<Object[]> history = new ArrayList<>();
        final PackedBlockPlan plan;
        final Names names;

        World(TestWorld source, PackedBlockPlan plan, Names names) {
            this.plan = plan;
            this.names = names;
            RegionSnapshot snapshot = source.snapshot();
            for (int y = source.minY; y <= source.maxY(); y++) {
                for (int z = source.minZ; z <= source.maxZ(); z++) {
                    for (int x = source.minX; x <= source.maxX(); x++) {
                        blocks.put(PackedBlockPlan.pack(x, y, z), source.at(x, y, z));
                        ids.put(PackedBlockPlan.pack(x, y, z), snapshot.rawId(x, y, z));
                    }
                }
            }
        }

        @Override
        public int rawIdAt(int x, int y, int z) {
            return ids.get(PackedBlockPlan.pack(x, y, z));
        }

        @Override
        public boolean write(int index, int x, int y, int z) throws MaxChangedBlocksException {
            long key = PackedBlockPlan.pack(x, y, z);
            history.add(new Object[]{key, blocks.get(key), ids.get(key)});
            blocks.put(key, names.text.get(plan.override(index)));
            ids.put(key, 10_000 + index);
            return true;
        }

        void undo() {
            for (int index = history.size() - 1; index >= 0; index--) {
                Object[] change = history.get(index);
                blocks.put((Long) change[0], (String) change[1]);
                ids.put((Long) change[0], (Integer) change[2]);
            }
            history.clear();
        }
    }

    private static PackedBlockPlan plan(TestWorld world, FluidWrites writes, Names names) {
        RegionEdits edits = new RegionEdits(world.snapshot(), 1_000_000);
        new FluidStates(names).write(writes, world.snapshot(), edits);
        return edits.plan();
    }

    @Test
    void aFilledPoolComesBackWithASingleUndo() throws MaxChangedBlocksException {
        TestWorld pool = FluidScenes.pool();
        FloodFill.Result result = FloodFill.plan(pool.snapshot(), pool.kinds(), 3, 3, 3,
                new FloodFill.Settings(FluidKind.WATER, FloodFill.Level.CLICK, FloodFill.Directions.DEFAULT, 50_000,
                        false, -64), FluidScenes.NOTHING);
        Names names = new Names();
        PackedBlockPlan plan = plan(pool, FluidScenes.writes(result, FluidKind.WATER), names);
        assertEquals(75, plan.size());
        World world = new World(pool, plan, names);
        Map<Long, String> before = new HashMap<>(world.blocks);
        // Mientras se calculaba alguien puso un bloque donde iría el agua: se respeta.
        world.blocks.put(PackedBlockPlan.pack(2, 1, 2), "minecraft:glass");
        world.ids.put(PackedBlockPlan.pack(2, 1, 2), 9_999);
        before.put(PackedBlockPlan.pack(2, 1, 2), "minecraft:glass");

        PlanApplication application = new PlanApplication(plan);
        application.step(world, Integer.MAX_VALUE, Long.MAX_VALUE, () -> 0L);
        assertTrue(application.done());
        assertEquals(1, application.protectedBlocks(), "el vidrio no se pisa");
        assertEquals(74, application.written());
        assertEquals("minecraft:water[level=0]", world.blocks.get(PackedBlockPlan.pack(3, 3, 3)));
        assertEquals("minecraft:glass", world.blocks.get(PackedBlockPlan.pack(2, 1, 2)));

        world.undo();
        assertEquals(before, world.blocks, "//undo lo deja todo como estaba");
    }

    @Test
    void aWaterfallWithItsPoolAndFlowsUndoesToo() throws MaxChangedBlocksException {
        TestWorld cliff = FluidScenes.cliff();
        Cascade.Result result = Cascade.fromPoint(cliff.snapshot(), cliff.kinds(), 10, 10, 5,
                new Cascade.Settings(FluidKind.WATER, true, 2, 2, 1, true), FlowSim.Rules.of(FluidKind.WATER, false),
                20_000, FluidScenes.NOTHING);
        Names names = new Names();
        PackedBlockPlan plan = plan(cliff, result.writes(), names);
        World world = new World(cliff, plan, names);
        Map<Long, String> before = new HashMap<>(world.blocks);
        new PlanApplication(plan).step(world, Integer.MAX_VALUE, Long.MAX_VALUE, () -> 0L);
        assertEquals("minecraft:water[level=8]", world.blocks.get(PackedBlockPlan.pack(11, 5, 5)),
                "las corrientes van en el plan");
        assertEquals(plan.size(), world.history.size(), "todo lo escrito queda en el historial");
        world.undo();
        assertEquals(before, world.blocks);
    }
}
