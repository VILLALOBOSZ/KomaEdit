package com.komainu.moxia.rebuild;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.block.AbstractPlantPartBlock;
import net.minecraft.block.BambooBlock;
import net.minecraft.block.BambooShootBlock;
import net.minecraft.block.BigDripleafBlock;
import net.minecraft.block.BigDripleafStemBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.ChorusFlowerBlock;
import net.minecraft.block.ChorusPlantBlock;
import net.minecraft.block.CocoaBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.HangingRootsBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.SporeBlossomBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;
import org.enginehub.linbus.format.snbt.LinStringIO;
import org.enginehub.linbus.tree.LinCompoundTag;

/**
 * Los bloques de Minecraft y WorldEdit vistos por la reconstrucción: sus {@link BlockTraits} de verdad (forma,
 * etiquetas y clases del juego), su id crudo y el texto con el que se guardan para recuperarlos tras un reinicio
 * (el estado y sus datos en SNBT, como los escribe y lee WorldEdit).
 */
public final class RebuildBlocks {
    private RebuildBlocks() {
    }

    public static BlockTraits traits(BlockState state) {
        Block block = state.getBlock();
        String type = Registries.BLOCK.getId(block).toString();
        if (state.isAir()) {
            return new BlockTraits(type, BlockTraits.Shape.OTHER, true, false, false, false);
        }
        if (block instanceof FluidBlock || state.isOf(Blocks.BUBBLE_COLUMN)) {
            return new BlockTraits(type, BlockTraits.Shape.OTHER, false, false, false, true);
        }
        boolean full = state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        if (vegetation(state)) {
            return new BlockTraits(type, full ? BlockTraits.Shape.FULL : BlockTraits.Shape.OTHER, false, false, true,
                    false);
        }
        BlockTraits.Shape shape;
        if (block instanceof StairsBlock) {
            shape = BlockTraits.Shape.STAIRS;
        } else if (block instanceof SlabBlock) {
            shape = BlockTraits.Shape.SLAB;
        } else if (block instanceof PaneBlock) {
            shape = BlockTraits.Shape.PANE;
        } else if (full && !state.hasBlockEntity()) {
            // Los muebles enteros (hornos, barriles, dispensadores) van con los detalles.
            shape = BlockTraits.Shape.FULL;
        } else {
            shape = BlockTraits.Shape.OTHER;
        }
        boolean pillar = shape == BlockTraits.Shape.FULL && (state.isIn(BlockTags.LOGS)
                || state.contains(Properties.AXIS) && pillarName(type));
        return new BlockTraits(type, shape, false, pillar, false, false);
    }

    private static boolean vegetation(BlockState state) {
        Block block = state.getBlock();
        if (state.isIn(BlockTags.LEAVES) || state.isIn(BlockTags.FLOWERS) || state.isIn(BlockTags.SAPLINGS)
                || state.isIn(BlockTags.CROPS) || state.isIn(BlockTags.CAVE_VINES)
                || state.isIn(BlockTags.REPLACEABLE_BY_TREES)) {
            return true;
        }
        return block instanceof LeavesBlock || block instanceof PlantBlock || block instanceof AbstractPlantPartBlock
                || block instanceof VineBlock || block instanceof MultifaceGrowthBlock || block instanceof CactusBlock
                || block instanceof SugarCaneBlock || block instanceof BambooBlock || block instanceof BambooShootBlock
                || block instanceof CocoaBlock || block instanceof ChorusPlantBlock
                || block instanceof ChorusFlowerBlock || block instanceof HangingRootsBlock
                || block instanceof SporeBlossomBlock || block instanceof BigDripleafBlock
                || block instanceof BigDripleafStemBlock || state.isOf(Blocks.MOSS_CARPET);
    }

    private static boolean pillarName(String type) {
        return type.contains("pillar") || type.contains("basalt") || type.endsWith("bamboo_block");
    }

    public static int raw(BlockState state) {
        return Block.getRawIdFromState(state);
    }

    /** Id crudo del estado de un bloque de WorldEdit. */
    public static int raw(BaseBlock block) {
        return Block.getRawIdFromState(FabricAdapter.adapt(block.toImmutableState()));
    }

    public static BlockState state(BaseBlock block) {
        return FabricAdapter.adapt(block.toImmutableState());
    }

    /** {@code minecraft:chest[facing=north,…]{Items:[…]}}: el estado y, si los tiene, sus datos en SNBT. */
    public static String text(BaseBlock block) {
        LinCompoundTag data = block.getNbt();
        String state = block.toImmutableState().getAsString();
        return data == null ? state : state + LinStringIO.writeToString(data);
    }

    /**
     * El bloque de {@link #text}, o {@code null} si ya no existe en el juego (un mod quitado). El estado lo lee
     * WorldEdit y los datos se leen aparte: su lector de bloques parte el texto por {@code |}, que puede salir en el
     * texto de un cartel o de un libro.
     */
    public static BaseBlock parse(String text) {
        int data = text.indexOf('{');
        String state = data < 0 ? text : text.substring(0, data);
        ParserContext context = new ParserContext();
        context.setRestricted(false);
        context.setTryLegacy(false);
        context.setPreferringWildcard(false);
        try {
            BaseBlock block = WorldEdit.getInstance().getBlockFactory().parseFromInput(state, context);
            if (data < 0) {
                return block.toImmutableState().toBaseBlock();
            }
            LinCompoundTag nbt = LinStringIO.readFromStringUsing(text.substring(data), LinCompoundTag::readFrom);
            return block.toImmutableState().toBaseBlock(nbt);
        } catch (InputParseException | RuntimeException exception) {
            return null;
        }
    }
}
