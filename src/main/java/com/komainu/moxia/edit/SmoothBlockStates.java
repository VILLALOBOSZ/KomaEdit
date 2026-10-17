package com.komainu.moxia.edit;

import com.komainu.moxia.shape.smooth.SmoothMaterials;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.StairShape;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Convierte el bloque que elige el patrón en la losa o escalera de su mismo material.
 *
 * <p>La familia se busca primero por nombre ({@link SmoothMaterials}) y, si el bloque no tiene losas o
 * escaleras propias (hormigón, lana, basalto…), se usa la del color de mapa más parecido siempre que se parezca
 * de verdad. Los bloques que no son cubos opacos (cristal, hojas, entidades de bloque) no se convierten.
 * Las familias se resuelven una vez por bloque y se guardan.</p>
 */
public final class SmoothBlockStates {
    /** Distancia RGB máxima para aceptar una losa o escalera de otro material por su color. */
    private static final int MAX_COLOR_DISTANCE_SQUARED = 48 * 48;
    private static final Map<Block, Family> FAMILIES = new ConcurrentHashMap<>();
    private static volatile Palette palette;

    private SmoothBlockStates() {
    }

    /**
     * @return la pieza en el material de {@code base}, o {@code null} si ese material no admite piezas
     */
    public static com.sk89q.worldedit.world.block.BlockState shape(BaseBlock base, byte piece) {
        if (piece == SmoothPiece.FULL || piece == SmoothPiece.EMPTY) {
            return null;
        }
        BlockState state = FabricAdapter.adapt(base.toImmutableState());
        BlockState shaped = shape(state, piece);
        return shaped == null ? null : FabricAdapter.adapt(shaped);
    }

    static BlockState shape(BlockState state, byte piece) {
        Block block = state.getBlock();
        if (block instanceof SlabBlock || block instanceof StairsBlock) {
            // El patrón ya pide piezas: se respeta el bloque y solo se orienta si coincide el tipo.
            return SmoothPiece.isSlab(piece) == block instanceof SlabBlock ? orient(block, piece) : null;
        }
        if (state.isAir() || state.hasBlockEntity() || !state.isOpaque()
                || !state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
            return null;
        }
        Family family = FAMILIES.computeIfAbsent(block, SmoothBlockStates::resolve);
        Block target = SmoothPiece.isSlab(piece) ? family.slab() : family.stairs();
        return target == null ? null : orient(target, piece);
    }

    private static BlockState orient(Block block, byte piece) {
        BlockState state = block.getDefaultState();
        if (block instanceof SlabBlock) {
            return state.with(SlabBlock.TYPE, SmoothPiece.isTop(piece) ? SlabType.TOP : SlabType.BOTTOM);
        }
        return state
                .with(StairsBlock.FACING, Direction.fromHorizontal(SmoothPiece.facing(piece)))
                .with(StairsBlock.HALF, SmoothPiece.isTop(piece) ? BlockHalf.TOP : BlockHalf.BOTTOM)
                .with(StairsBlock.SHAPE, StairShape.values()[SmoothPiece.shape(piece)]);
    }

    private static Family resolve(Block block) {
        String id = Registries.BLOCK.getId(block).toString();
        int color = color(block);
        Block slab = byName(SmoothMaterials.slabIds(id), SlabBlock.class)
                .orElseGet(() -> palette().nearest(palette().slabs(), color));
        Block stairs = byName(SmoothMaterials.stairsIds(id), StairsBlock.class)
                .orElseGet(() -> palette().nearest(palette().stairs(), color));
        return new Family(slab, stairs);
    }

    private static Optional<Block> byName(List<String> ids, Class<? extends Block> type) {
        for (String id : ids) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null && Registries.BLOCK.containsId(identifier)) {
                Block candidate = Registries.BLOCK.get(identifier);
                if (type.isInstance(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    private static int color(Block block) {
        MapColor mapColor = block.getDefaultState().getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
        return mapColor == null || mapColor == MapColor.CLEAR ? -1 : mapColor.color;
    }

    private static Palette palette() {
        Palette current = palette;
        if (current == null) {
            current = Palette.scan();
            palette = current;
        }
        return current;
    }

    private record Family(Block slab, Block stairs) {
    }

    private record Swatch(Block block, int color) {
    }

    /** Todas las losas y escaleras registradas (vanilla y mods) con su color de mapa. */
    private record Palette(List<Swatch> slabs, List<Swatch> stairs) {
        private static Palette scan() {
            List<Swatch> slabs = new ArrayList<>();
            List<Swatch> stairs = new ArrayList<>();
            for (Block block : Registries.BLOCK) {
                // La losa de roble petrificado es una reliquia que no se puede fabricar.
                if (Registries.BLOCK.getId(block).getPath().startsWith("petrified_")) {
                    continue;
                }
                int color = color(block);
                if (color < 0) {
                    continue;
                }
                if (block instanceof SlabBlock) {
                    slabs.add(new Swatch(block, color));
                } else if (block instanceof StairsBlock) {
                    stairs.add(new Swatch(block, color));
                }
            }
            return new Palette(List.copyOf(slabs), List.copyOf(stairs));
        }

        private Block nearest(List<Swatch> swatches, int color) {
            if (color < 0) {
                return null;
            }
            Block best = null;
            int bestDistance = MAX_COLOR_DISTANCE_SQUARED + 1;
            for (Swatch swatch : swatches) {
                int distance = distanceSquared(color, swatch.color());
                if (distance < bestDistance) {
                    best = swatch.block();
                    bestDistance = distance;
                }
            }
            return best;
        }

        private static int distanceSquared(int first, int second) {
            int red = (first >> 16 & 0xFF) - (second >> 16 & 0xFF);
            int green = (first >> 8 & 0xFF) - (second >> 8 & 0xFF);
            int blue = (first & 0xFF) - (second & 0xFF);
            return red * red + green * green + blue * blue;
        }
    }
}
