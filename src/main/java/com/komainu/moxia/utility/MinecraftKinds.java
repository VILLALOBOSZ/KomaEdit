package com.komainu.moxia.utility;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.FluidFillable;
import net.minecraft.block.SnowBlock;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.EmptyBlockView;

/**
 * {@link BlockKinds} con las reglas de Minecraft, a partir del id crudo de cada estado. Solo lee estados, formas y
 * etiquetas ya cargados, así que vale desde el worker; cada operación usa su propia instancia (la caché no es
 * compartida entre hilos).
 */
public final class MinecraftKinds implements BlockKinds {
    private static final int WATER = 1;
    private static final int LAVA = 1 << 1;
    private static final int SOURCE = 1 << 2;
    private static final int WATER_ONLY = 1 << 3;
    private static final int WATERLOGGABLE = 1 << 4;
    private static final int WATERLOGGED = 1 << 5;
    private static final int SUPPORTS_SNOW = 1 << 6;
    private static final int SNOWY = 1 << 7;
    private static final int SMALL_PLANT = 1 << 8;
    private static final int BLOCK_ENTITY = 1 << 9;
    private static final int WASHABLE = 1 << 10;
    private static final int LAYERS_SHIFT = 12;
    /** El nivel del líquido más uno (0: no es agua ni lava), en cinco bits. */
    private static final int LEVEL_SHIFT = 16;

    private final Int2IntOpenHashMap flags = new Int2IntOpenHashMap();

    @Override
    public boolean water(int rawId) {
        return has(rawId, WATER);
    }

    @Override
    public boolean lava(int rawId) {
        return has(rawId, LAVA);
    }

    @Override
    public boolean source(int rawId) {
        return has(rawId, SOURCE);
    }

    @Override
    public boolean waterOnly(int rawId) {
        return has(rawId, WATER_ONLY);
    }

    @Override
    public boolean waterloggable(int rawId) {
        return has(rawId, WATERLOGGABLE);
    }

    @Override
    public boolean waterlogged(int rawId) {
        return has(rawId, WATERLOGGED);
    }

    @Override
    public boolean supportsSnow(int rawId) {
        return has(rawId, SUPPORTS_SNOW);
    }

    @Override
    public int snowLayers(int rawId) {
        return flags(rawId) >>> LAYERS_SHIFT;
    }

    @Override
    public boolean snowy(int rawId) {
        return has(rawId, SNOWY);
    }

    @Override
    public boolean smallPlant(int rawId) {
        return has(rawId, SMALL_PLANT);
    }

    @Override
    public boolean blockEntity(int rawId) {
        return has(rawId, BLOCK_ENTITY);
    }

    @Override
    public boolean washable(int rawId) {
        return has(rawId, WASHABLE);
    }

    @Override
    public int fluidLevel(int rawId) {
        return (flags(rawId) >>> LEVEL_SHIFT & 31) - 1;
    }

    private boolean has(int rawId, int flag) {
        return (flags(rawId) & flag) != 0;
    }

    private int flags(int rawId) {
        int cached = flags.getOrDefault(rawId, -1);
        if (cached >= 0) {
            return cached;
        }
        int computed = compute(Block.getStateFromRawId(rawId));
        flags.put(rawId, computed);
        return computed;
    }

    private static int compute(BlockState state) {
        int result = 0;
        if (state.isOf(Blocks.WATER) || state.isOf(Blocks.BUBBLE_COLUMN)) {
            result |= WATER;
        }
        if (state.isOf(Blocks.LAVA)) {
            result |= LAVA;
        }
        if ((state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA) || state.isOf(Blocks.BUBBLE_COLUMN))
                && state.getFluidState().isStill()) {
            result |= SOURCE;
        }
        if (state.isOf(Blocks.KELP) || state.isOf(Blocks.KELP_PLANT) || state.isOf(Blocks.SEAGRASS)
                || state.isOf(Blocks.TALL_SEAGRASS)) {
            result |= WATER_ONLY;
        }
        if (state.contains(Properties.WATERLOGGED)) {
            result |= WATERLOGGABLE;
            if (state.get(Properties.WATERLOGGED)) {
                result |= WATERLOGGED;
            }
        }
        if (supportsSnow(state)) {
            result |= SUPPORTS_SNOW;
        }
        if (state.contains(Properties.SNOWY)) {
            result |= SNOWY;
        }
        if (state.isReplaceable() && state.getFluidState().isEmpty() && !state.isAir() && !state.isOf(Blocks.SNOW)
                && !state.contains(Properties.DOUBLE_BLOCK_HALF)) {
            result |= SMALL_PLANT;
        }
        if (state.hasBlockEntity()) {
            result |= BLOCK_ENTITY;
        }
        if (state.isOf(Blocks.SNOW)) {
            result |= state.get(SnowBlock.LAYERS) << LAYERS_SHIFT;
        }
        if (washable(state)) {
            result |= WASHABLE;
        }
        if (state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA)) {
            result |= (state.get(FluidBlock.LEVEL) + 1) << LEVEL_SHIFT;
        }
        return result;
    }

    /** {@code FlowableFluid.canFill} para un bloque sin líquido que no se anega. */
    private static boolean washable(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        Block block = state.getBlock();
        if (!state.getFluidState().isEmpty() || block instanceof FluidFillable || block instanceof DoorBlock
                || state.isIn(BlockTags.SIGNS) || state.isOf(Blocks.LADDER) || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.BUBBLE_COLUMN) || state.isOf(Blocks.NETHER_PORTAL)
                || state.isOf(Blocks.END_PORTAL) || state.isOf(Blocks.END_GATEWAY)
                || state.isOf(Blocks.STRUCTURE_VOID)) {
            return false;
        }
        return !state.blocksMovement();
    }

    /** La misma regla que {@code SnowBlock.canPlaceAt} sobre el bloque de debajo. */
    private static boolean supportsSnow(BlockState state) {
        if (state.isIn(BlockTags.SNOW_LAYER_CANNOT_SURVIVE_ON)) {
            return false;
        }
        if (state.isIn(BlockTags.SNOW_LAYER_CAN_SURVIVE_ON)) {
            return true;
        }
        if (state.isOf(Blocks.SNOW)) {
            return state.get(SnowBlock.LAYERS) == 8;
        }
        return Block.isFaceFullSquare(state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN), Direction.UP);
    }
}
