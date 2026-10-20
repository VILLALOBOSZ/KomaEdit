package com.komainu.moxia.nature;

import com.komainu.moxia.brush.ez.PaletteShift;
import com.komainu.moxia.brush.ez.ResolvedPalette;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bloques de un árbol o una roca, resueltos en el hilo del servidor (bloques sueltos, listas, {@code ##paletas} y
 * {@code #guardadas}) y con todas sus variantes preparadas para el worker: cada tronco con sus tres ejes y cada hoja
 * con {@code persistent=true} y su {@code distance}, para que nunca se caigan solas.
 */
final class NatureMaterials {
    /** Por entrada de la paleta: el estado con eje x, y, z. */
    private final BlockState[][] wood;
    private final BlockState[][] roots;
    /** Por entrada: el estado con distancia 0 a 7 (persistent siempre a true). */
    private final BlockState[][] leaves;
    private final BlockState[] rock;
    private final BlockState[] moss;
    private final long seed;

    private NatureMaterials(BlockState[][] wood, BlockState[][] roots, BlockState[][] leaves, BlockState[] rock,
                            BlockState[] moss, long seed) {
        this.wood = wood;
        this.roots = roots;
        this.leaves = leaves;
        this.rock = rock;
        this.moss = moss;
        this.seed = seed;
    }

    static NatureMaterials tree(ServerPlayerEntity player, TreeSettings settings, long seed) {
        BlockState[][] wood = oriented(resolve(player, settings.trunkMaterial(), "el tronco (-t)"));
        String rootText = settings.rootMaterial();
        BlockState[][] roots = rootText == null ? wood : oriented(resolve(player, rootText, "las raíces (-r)"));
        String leafText = settings.leafMaterial();
        BlockState[][] leaves = leafText == null ? new BlockState[0][]
                : persistent(resolve(player, leafText, "las hojas (-h)"));
        return new NatureMaterials(wood, roots, leaves, new BlockState[0], new BlockState[0], seed);
    }

    static NatureMaterials rock(ServerPlayerEntity player, RockSettings settings, long seed) {
        BlockState[] rock = resolve(player, settings.paletteMaterial(), "los bloques (-b)").toArray(BlockState[]::new);
        BlockState[] moss = settings.moss() == null ? new BlockState[0]
                : resolve(player, settings.moss(), "el musgo (-u)").toArray(BlockState[]::new);
        return new NatureMaterials(new BlockState[0][], new BlockState[0][], new BlockState[0][], rock, moss, seed);
    }

    private static List<BlockState> resolve(ServerPlayerEntity player, String input, String what) {
        ResolvedPalette palette;
        try {
            palette = ResolvedPalette.resolve(player, input, 1);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Material de " + what + ": " + exception.getMessage(), exception);
        }
        List<BlockState> states = new ArrayList<>(palette.size());
        for (int index = 0; index < palette.size(); index++) {
            states.add(palette.place(index, null, PaletteShift.Kind.FULL));
        }
        return states;
    }

    private static BlockState[][] oriented(List<BlockState> states) {
        BlockState[][] result = new BlockState[states.size()][];
        for (int index = 0; index < result.length; index++) {
            BlockState state = states.get(index);
            result[index] = new BlockState[3];
            for (int axis = 0; axis < 3; axis++) {
                result[index][axis] = with(state, "axis", NatureStates.axis(axis));
            }
        }
        return result;
    }

    private static BlockState[][] persistent(List<BlockState> states) {
        BlockState[][] result = new BlockState[states.size()][];
        for (int index = 0; index < result.length; index++) {
            result[index] = new BlockState[8];
            for (int distance = 0; distance < 8; distance++) {
                BlockState state = states.get(index);
                for (Map.Entry<String, Object> property : NatureStates.leaf(distance).entrySet()) {
                    state = with(state, property.getKey(), property.getValue());
                }
                result[index][distance] = state;
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static BlockState with(BlockState state, String name, Object value) {
        Property<Object> property = (Property<Object>) state.getBlockType().getPropertyMap().get(name);
        if (property == null || !property.getValues().contains(value)) {
            return state;
        }
        return state.with(property, value);
    }

    /** El estado de una celda; {@code null} si su papel no tiene material (sin hojas, sin musgo). */
    BlockState state(long key, int info) {
        return switch (NatureVoxels.role(info)) {
            case WOOD -> pick(wood, key, true)[NatureVoxels.axis(info)];
            case ROOT -> pick(roots, key, true)[NatureVoxels.axis(info)];
            case LEAF, HANGING -> leaves.length == 0 ? null
                    : pick(leaves, key, false)[Math.min(7, NatureVoxels.distance(info))];
            case ROCK -> rock[Math.min(rock.length - 1, NatureVoxels.shade(info) * rock.length / 256)];
            case MOSS -> moss.length == 0
                    ? rock[Math.min(rock.length - 1, NatureVoxels.shade(info) * rock.length / 256)]
                    : moss[(int) (TreeBuilder.unit(key, seed + 3) * moss.length)];
        };
    }

    /**
     * Entrada de la paleta para la celda. En la madera va por manchas (corteza con vetas), en las hojas mezclada.
     */
    private BlockState[] pick(BlockState[][] palette, long key, boolean patches) {
        if (palette.length == 1) {
            return palette[0];
        }
        double value;
        if (patches) {
            int x = PackedBlockPlan.unpackX(key) >> 1;
            int y = PackedBlockPlan.unpackY(key) / 3;
            int z = PackedBlockPlan.unpackZ(key) >> 1;
            value = TreeBuilder.unit(PackedBlockPlan.pack(x, y, z), seed + 11);
        } else {
            value = TreeBuilder.unit(key, seed + 7);
        }
        return palette[(int) Math.min(palette.length - 1, value * palette.length)];
    }
}
