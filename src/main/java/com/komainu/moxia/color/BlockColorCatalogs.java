package com.komainu.moxia.color;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Monta el {@link BlockColorCatalog} del juego con todos los bloques registrados: el color de su textura si está en
 * la tabla vanilla y, si no (bloques de mods), su color de mapa. Las banderas de forma salen del estado por defecto
 * de cada bloque; un bloque no es liso si la tabla lo marca, si da luz o si tiene entidad de bloque (cofres,
 * hornos, colmenas… también los de mods). El registro no cambia con el servidor en marcha, así que se monta una vez
 * y vale para el servidor y para el cliente.
 */
public final class BlockColorCatalogs {
    private static volatile BlockColorCatalog catalog;

    private BlockColorCatalogs() {
    }

    public static BlockColorCatalog get() {
        BlockColorCatalog current = catalog;
        if (current == null) {
            synchronized (BlockColorCatalogs.class) {
                current = catalog;
                if (current == null) {
                    current = build();
                    catalog = current;
                }
            }
        }
        return current;
    }

    private static BlockColorCatalog build() {
        Map<String, BlockColorCatalog.Measured> measured = BlockColorCatalog.measured();
        List<BlockColor> blocks = new ArrayList<>();
        for (Identifier identifier : Registries.BLOCK.getIds()) {
            Block block = Registries.BLOCK.get(identifier);
            String id = identifier.toString();
            BlockState state = block.getDefaultState();
            if (state.isAir()) {
                continue;
            }
            BlockColorCatalog.Measured texture = measured.get(id);
            int rgb;
            if (texture != null) {
                rgb = texture.rgb();
            } else {
                MapColor mapColor = block.getDefaultMapColor();
                if (mapColor == null || mapColor == MapColor.CLEAR) {
                    continue;
                }
                rgb = mapColor.color;
            }
            int flags = 0;
            try {
                if (state.isFullCube(EmptyBlockView.INSTANCE, BlockPos.ORIGIN)) {
                    flags |= BlockColor.CUBE;
                }
                if (!state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN).isEmpty()) {
                    flags |= BlockColor.SOLID;
                }
            } catch (RuntimeException exception) {
                // Algún bloque de mod mira el mundo para su forma: sin mundo no se sabe, se queda sin banderas.
            }
            if (state.isOpaque()) {
                flags |= BlockColor.OPAQUE;
            }
            if (texture != null && texture.uniform()) {
                flags |= BlockColor.UNIFORM;
            }
            if (block.asItem() != Items.AIR) {
                flags |= BlockColor.ITEM;
            }
            boolean patterned = texture != null && texture.patterned();
            if (!patterned && state.getLuminance() == 0 && !(block instanceof BlockEntityProvider)) {
                flags |= BlockColor.PLAIN;
            }
            blocks.add(BlockColor.of(id, rgb, flags, texture != null));
        }
        return BlockColorCatalog.of(blocks);
    }
}
