package com.komainu.moxia.terrain;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Bloques de las herramientas de terreno ya pasados a estados de WorldEdit, y los destinos donde se escriben: el
 * plan condicional de una operación de región o el plan de un clic de pincel.
 */
public final class TerrainBlocks {
    public static final int MAX_BRUSH_CHANGES = 1_000_000;

    private final Map<String, BlockState> states;
    private final Map<String, BlockState> extra = new java.util.concurrent.ConcurrentHashMap<>();

    private TerrainBlocks(Map<String, BlockState> states) {
        this.states = states;
    }

    /**
     * Comprueba y resuelve en el hilo del servidor los bloques de la paleta, el aire y los líquidos. Lanza con un
     * mensaje claro si alguno no existe.
     */
    public static TerrainBlocks resolve(SurfacePalette palette) {
        Set<String> ids = new LinkedHashSet<>(palette.blockIds());
        ids.add("minecraft:air");
        ids.add("minecraft:water");
        ids.add("minecraft:lava");
        return resolve(ids);
    }

    public static TerrainBlocks resolve(Collection<String> ids) {
        Map<String, BlockState> states = new HashMap<>();
        for (String id : ids) {
            BlockType type = BlockTypes.get(id);
            if (type == null) {
                throw new IllegalArgumentException("El bloque " + id + " no existe en esta versión de Minecraft");
            }
            states.put(id, type.getDefaultState());
        }
        return new TerrainBlocks(Map.copyOf(states));
    }

    /**
     * Estado de un id. Los que no venían en la paleta (los que copia {@code conservar}) se buscan en el registro de
     * WorldEdit, que solo se lee.
     */
    BlockState state(String id) {
        BlockState state = states.get(id);
        if (state != null) {
            return state;
        }
        return extra.computeIfAbsent(id, key -> {
            BlockType type = BlockTypes.get(key);
            if (type == null) {
                throw new IllegalArgumentException("El bloque " + key + " no existe");
            }
            return type.getDefaultState();
        });
    }

    /**
     * Destino de una operación de región: escrituras condicionales en lo seleccionado. Si ya hay un bloque de ese
     * tipo no se toca, así conserva sus propiedades.
     */
    public TerrainWriter.BlockSink region(RegionEdits edits, RegionSnapshot snapshot) {
        return (x, y, z, blockId) -> {
            if (!snapshot.blockId(x, y, z).equals(blockId)) {
                edits.set(x, y, z, state(blockId));
            }
        };
    }

    /** Destino de un clic de pincel: el plan del clic, sin escribir lo que ya es igual. */
    public PlanSink brush(WorldSnapshot snapshot, int expected) {
        return new PlanSink(this, snapshot, expected);
    }

    public static final class PlanSink implements TerrainWriter.BlockSink {
        private final TerrainBlocks blocks;
        private final WorldSnapshot snapshot;
        private final PackedBlockPlan.Builder builder;
        private int changes;

        private PlanSink(TerrainBlocks blocks, WorldSnapshot snapshot, int expected) {
            this.blocks = blocks;
            this.snapshot = snapshot;
            this.builder = PackedBlockPlan.builder(Math.max(64, expected));
        }

        @Override
        public void set(int x, int y, int z, String blockId) {
            BlockState existing = snapshot.get(x, y, z);
            if (existing.getBlockType().id().equals(blockId)) {
                return;
            }
            BlockState state = blocks.state(blockId);
            builder.addOverride(x, y, z, state);
            if (++changes > MAX_BRUSH_CHANGES) {
                throw new IllegalArgumentException("El pincel supera el límite de " + MAX_BRUSH_CHANGES + " bloques");
            }
        }

        public PackedBlockPlan build() {
            return builder.build();
        }
    }
}
