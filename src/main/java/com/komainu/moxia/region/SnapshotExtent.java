package com.komainu.moxia.region;

import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockStateHolder;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.List;

/**
 * {@link Extent} de solo lectura respaldado por una {@link RegionSnapshot}: las máscaras de WorldEdit se parsean con
 * él en el hilo del servidor y, cuando la foto está lista, se evalúan en el worker sin tocar el mundo. Fuera de la
 * foto todo es aire. No guarda entidades ni biomas (una máscara de bioma da un error claro) y no admite escrituras.
 */
public final class SnapshotExtent implements Extent {
    private volatile RegionSnapshot snapshot;

    /** Conecta la foto ya capturada; hasta entonces el extent está vacío. */
    public void bind(RegionSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        RegionSnapshot current = snapshot;
        return current == null ? BlockVector3.ZERO
                : BlockVector3.at(current.minimumX(), current.minimumY(), current.minimumZ());
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        RegionSnapshot current = snapshot;
        return current == null ? BlockVector3.ZERO
                : BlockVector3.at(current.maximumX(), current.maximumY(), current.maximumZ());
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
        RegionSnapshot current = snapshot;
        BlockState state = current == null ? null : current.state(position.x(), position.y(), position.z());
        return state == null ? BlockTypes.AIR.getDefaultState() : state;
    }

    @Override
    public BaseBlock getFullBlock(BlockVector3 position) {
        return getBlock(position).toBaseBlock();
    }

    @Override
    public BiomeType getBiome(BlockVector3 position) {
        throw new IllegalArgumentException("Las máscaras de bioma no se pueden usar aquí: la operación trabaja sobre "
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
    public Operation commit() {
        return null;
    }
}
