package com.komainu.moxia.nature;

import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/**
 * Máscara de WorldEdit de {@code -m} para árboles y rocas: dónde se puede poner un bloque, mirando lo que había en la
 * foto. Se parsea en el hilo del servidor y se evalúa en el worker sobre la foto, sin tocar el mundo. La máscara
 * global ({@code //gmask}) la aplica además la sesión de WorldEdit al colocar.
 */
final class NatureMask {
    private final SnapshotExtent extent;
    private final Mask mask;

    private NatureMask(SnapshotExtent extent, Mask mask) {
        this.extent = extent;
        this.mask = mask;
    }

    static NatureMask parse(ServerPlayerEntity player, String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        SnapshotExtent extent = new SnapshotExtent();
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        ParserContext parser = new ParserContext();
        parser.setActor(context.actor());
        parser.setSession(context.session());
        parser.setWorld(context.world());
        parser.setExtent(extent);
        parser.setRestricted(!context.actor().hasPermission("worldedit.anyblock"));
        try {
            return new NatureMask(extent, WorldEdit.getInstance().getMaskFactory().parseFromInput(input.strip(),
                    parser));
        } catch (InputParseException exception) {
            throw new IllegalArgumentException("Máscara inválida «" + input.strip() + "»: " + exception.getMessage(),
                    exception);
        }
    }

    /** Conecta la foto; desde aquí la máscara la lee a ella. */
    void bind(WorldSnapshot snapshot) {
        extent.snapshot = snapshot;
    }

    boolean test(int x, int y, int z) {
        return mask.test(BlockVector3.at(x, y, z));
    }

    /** {@link Extent} de solo lectura sobre la foto; fuera de ella todo es aire y no hay biomas ni entidades. */
    private static final class SnapshotExtent implements Extent {
        private volatile WorldSnapshot snapshot;

        @Override
        public BlockVector3 getMinimumPoint() {
            return BlockVector3.at(-30_000_000, -2048, -30_000_000);
        }

        @Override
        public BlockVector3 getMaximumPoint() {
            return BlockVector3.at(30_000_000, 2047, 30_000_000);
        }

        @Override
        public List<? extends Entity> getEntities(Region region) {
            return List.of();
        }

        @Override
        public List<? extends Entity> getEntities() {
            return List.of();
        }

        @Override
        public Entity createEntity(Location location, BaseEntity entity) {
            return null;
        }

        @Override
        public BlockState getBlock(BlockVector3 position) {
            WorldSnapshot current = snapshot;
            return current == null ? BlockTypes.AIR.getDefaultState()
                    : current.get(position.x(), position.y(), position.z());
        }

        @Override
        public BaseBlock getFullBlock(BlockVector3 position) {
            return getBlock(position).toBaseBlock();
        }

        @Override
        public BiomeType getBiome(BlockVector3 position) {
            throw new IllegalArgumentException("Las máscaras de bioma no sirven aquí: árboles y rocas trabajan sobre "
                    + "una foto de los bloques, sin biomas");
        }

        @Override
        public <T extends BlockStateHolder<T>> boolean setBlock(BlockVector3 position, T block) {
            return false;
        }

        @Override
        public boolean setBiome(BlockVector3 position, BiomeType biome) {
            return false;
        }

        @Override
        public com.sk89q.worldedit.function.operation.Operation commit() {
            return null;
        }
    }
}
