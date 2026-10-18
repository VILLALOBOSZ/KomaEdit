package com.komainu.moxia.edit;

import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.shape.smooth.PieceMaterials;
import com.komainu.moxia.shape.smooth.PieceVariant;
import com.komainu.moxia.shape.smooth.SmoothMaterials;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.HorizontalConnectingBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.StairShape;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Convierte el bloque que elige el patrón en la losa, escalera, muro, capa o panel de su mismo material.
 *
 * <p>La pieza se busca con {@link SmoothMaterials}: primero por nombre y, si el bloque no tiene esa pieza
 * (hormigón, lana, basalto…), la del color más parecido. Los bloques que no son cubos opacos (cristal, hojas,
 * entidades de bloque) no se convierten, salvo el cristal en paneles ({@code glass} → {@code glass_pane}). Un perfil con materiales fijos ({@code Slabs(Slab:acacia)}) usa ese
 * material para su variante, sea cual sea el bloque. Todo se resuelve una vez por bloque y se guarda.</p>
 */
public final class SmoothBlockStates {
    private static final Map<Block, Map<PieceVariant, Block>> FAMILIES = new ConcurrentHashMap<>();
    private static final Map<String, Block> OVERRIDES = new ConcurrentHashMap<>();
    private static volatile RegistryCatalog catalog;

    private SmoothBlockStates() {
    }

    /**
     * @return la pieza en el material de {@code base}, o {@code null} si ese material no admite piezas
     */
    public static com.sk89q.worldedit.world.block.BlockState shape(BaseBlock base, byte piece) {
        return shape(base, piece, PieceMaterials.NONE);
    }

    /** Como {@link #shape(BaseBlock, byte)}, con los materiales fijos del perfil. */
    public static com.sk89q.worldedit.world.block.BlockState shape(
            BaseBlock base,
            byte piece,
            PieceMaterials materials
    ) {
        if (piece == SmoothPiece.FULL || piece == SmoothPiece.EMPTY) {
            return null;
        }
        BlockState state = FabricAdapter.adapt(base.toImmutableState());
        BlockState shaped = shape(state, piece, materials);
        return shaped == null ? null : FabricAdapter.adapt(shaped);
    }

    static BlockState shape(BlockState state, byte piece, PieceMaterials materials) {
        if (state.isAir()) {
            return null;
        }
        PieceVariant variant = SmoothPiece.variant(piece);
        String override = materials == null ? null : materials.get(variant);
        if (override != null) {
            return orient(override(variant, override), piece);
        }
        Block block = state.getBlock();
        PieceVariant own = variantOf(block);
        if (own != null) {
            // El patrón ya pide piezas: se respeta el bloque y solo se orienta si coincide el tipo.
            return own == variant ? orient(block, piece) : null;
        }
        if (!convertible(state, variant)) {
            return null;
        }
        Block target = family(block).get(variant);
        return target == null ? null : orient(target, piece);
    }

    /**
     * Losa y escalera del material de un cubo entero, las mismas que usa el modo piezas: por nombre o, si no tiene,
     * del color más parecido. Los pinceles con {@code -w} las usan para texturizar las piezas que ya hay.
     *
     * @return {@code [losa, escalera]}; cualquiera puede ser {@code null}
     */
    public static Block[] pieceFamily(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof SlabBlock) {
            return new Block[]{block, null};
        }
        if (block instanceof StairsBlock) {
            return new Block[]{null, block};
        }
        if (state.isAir() || !convertible(state, null)) {
            return new Block[]{null, null};
        }
        Map<PieceVariant, Block> family = family(block);
        return new Block[]{family.get(PieceVariant.SLAB), family.get(PieceVariant.STAIR)};
    }

    /**
     * Comprueba, en el hilo del comando, que los materiales fijos del perfil existen; así el error sale al
     * escribir la orden y no a media construcción.
     */
    public static void validate(SmoothProfile profile) {
        if (profile != null) {
            validate(profile.materials());
        }
    }

    public static void validate(PieceMaterials materials) {
        if (materials != null) {
            materials.asMap().forEach(SmoothBlockStates::override);
        }
    }

    /** Cubos opacos sin entidad de bloque; para los paneles vale también el cristal (cubo entero translúcido). */
    private static boolean convertible(BlockState state, PieceVariant variant) {
        return !state.hasBlockEntity() && (state.isOpaque() || variant == PieceVariant.PANE)
                && state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }

    private static Map<PieceVariant, Block> family(Block block) {
        return FAMILIES.computeIfAbsent(block, SmoothBlockStates::resolve);
    }

    private static Map<PieceVariant, Block> resolve(Block block) {
        String id = Registries.BLOCK.getId(block).toString();
        Map<PieceVariant, Block> family = new EnumMap<>(PieceVariant.class);
        for (PieceVariant variant : PieceVariant.values()) {
            String target = SmoothMaterials.resolve(id, variant, catalog());
            if (target != null) {
                family.put(variant, Registries.BLOCK.get(Identifier.of(target)));
            }
        }
        return family;
    }

    private static Block override(PieceVariant variant, String text) {
        return OVERRIDES.computeIfAbsent(variant.name() + "|" + text,
                ignored -> Registries.BLOCK.get(Identifier.of(SmoothMaterials.override(text, variant, catalog()))));
    }

    private static PieceVariant variantOf(Block block) {
        if (block instanceof SlabBlock) {
            return PieceVariant.SLAB;
        }
        if (block instanceof StairsBlock) {
            return PieceVariant.STAIR;
        }
        if (block instanceof WallBlock || block instanceof FenceBlock) {
            return PieceVariant.WALL;
        }
        if (block instanceof PaneBlock) {
            return PieceVariant.PANE;
        }
        return block.getDefaultState().contains(Properties.LAYERS) ? PieceVariant.LAYER : null;
    }

    private static BlockState orient(Block block, byte piece) {
        BlockState state = block.getDefaultState();
        if (block instanceof SlabBlock) {
            return state.with(SlabBlock.TYPE, SmoothPiece.isTop(piece) ? SlabType.TOP : SlabType.BOTTOM);
        }
        if (block instanceof StairsBlock) {
            return state
                    .with(StairsBlock.FACING, Direction.fromHorizontal(SmoothPiece.facing(piece)))
                    .with(StairsBlock.HALF, SmoothPiece.isTop(piece) ? BlockHalf.TOP : BlockHalf.BOTTOM)
                    .with(StairsBlock.SHAPE, StairShape.values()[SmoothPiece.shape(piece)]);
        }
        if (state.contains(Properties.LAYERS) && SmoothPiece.isLayers(piece)) {
            return state.with(Properties.LAYERS, SmoothPiece.layerCount(piece));
        }
        if (block instanceof PaneBlock && SmoothPiece.isPane(piece)) {
            // Las conexiones ya vienen resueltas con los vecinos de la figura (PieceSolver).
            int connections = SmoothPiece.paneConnections(piece);
            return state
                    .with(HorizontalConnectingBlock.SOUTH, (connections & 1 << SmoothPiece.SOUTH) != 0)
                    .with(HorizontalConnectingBlock.WEST, (connections & 1 << SmoothPiece.WEST) != 0)
                    .with(HorizontalConnectingBlock.NORTH, (connections & 1 << SmoothPiece.NORTH) != 0)
                    .with(HorizontalConnectingBlock.EAST, (connections & 1 << SmoothPiece.EAST) != 0);
        }
        // Muros y vallas: el estado por defecto es el poste solo; Minecraft los une a sus vecinos al colocarlos.
        return state;
    }

    private static RegistryCatalog catalog() {
        RegistryCatalog current = catalog;
        if (current == null) {
            current = RegistryCatalog.scan();
            catalog = current;
        }
        return current;
    }

    /** Lo registrado (vanilla y mods), con el color de textura de la tabla o, si no está, el de mapa. */
    private record RegistryCatalog(Map<PieceVariant, List<String>> pieces, Map<String, Integer> colors)
            implements SmoothMaterials.Catalog {
        private static RegistryCatalog scan() {
            Map<String, Integer> textures = SmoothMaterials.textureColors();
            Map<PieceVariant, List<String>> pieces = new EnumMap<>(PieceVariant.class);
            for (PieceVariant variant : PieceVariant.values()) {
                pieces.put(variant, new ArrayList<>());
            }
            Map<String, Integer> colors = new java.util.HashMap<>();
            for (Block block : Registries.BLOCK) {
                Identifier identifier = Registries.BLOCK.getId(block);
                String id = identifier.toString();
                int color = textures.getOrDefault(id, mapColor(block));
                if (color >= 0) {
                    colors.put(id, color);
                }
                // La losa de roble petrificado es una reliquia que no se puede fabricar; las vallas, solo por nombre.
                // El cobre encerado tiene el mismo color que el normal: por color se elige siempre el normal.
                if (identifier.getPath().startsWith("petrified_") || identifier.getPath().startsWith("waxed_")
                        || block instanceof FenceBlock) {
                    continue;
                }
                PieceVariant variant = variantOf(block);
                if (variant != null) {
                    pieces.get(variant).add(id);
                }
            }
            pieces.replaceAll((variant, ids) -> List.copyOf(ids));
            return new RegistryCatalog(pieces, Map.copyOf(colors));
        }

        @Override
        public boolean exists(String id) {
            Identifier identifier = Identifier.tryParse(id);
            return identifier != null && Registries.BLOCK.containsId(identifier);
        }

        @Override
        public boolean is(String id, PieceVariant variant) {
            Identifier identifier = Identifier.tryParse(id);
            return identifier != null && Registries.BLOCK.containsId(identifier)
                    && variantOf(Registries.BLOCK.get(identifier)) == variant;
        }

        @Override
        public List<String> pieces(PieceVariant variant) {
            return pieces.get(variant);
        }

        @Override
        public int color(String id) {
            return colors.getOrDefault(id, -1);
        }

        private static int mapColor(Block block) {
            MapColor mapColor = block.getDefaultState().getMapColor(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            return mapColor == null || mapColor == MapColor.CLEAR ? -1 : mapColor.color;
        }
    }
}
