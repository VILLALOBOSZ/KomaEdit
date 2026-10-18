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
 * {@link Extent} de solo lectura respaldado por una foto: las máscaras de WorldEdit se parsean con él en el hilo del
 * servidor y, cuando la foto está lista, se evalúan en el worker sin tocar el mundo. Fuera de la foto todo es aire.
 * No guarda entidades ni biomas (una máscara de bioma da un error claro) y no admite escrituras.
 *
 * <p>Mientras no hay foto conectada, las lecturas van al extent de respaldo (el mundo del jugador): así las máscaras
 * que miran un bloque al parsearse, como {@code #aim}, ven el mundo real. Eso solo pasa en el hilo del servidor; el
 * worker siempre trabaja con la foto ya conectada.</p>
 */
public final class SnapshotExtent implements Extent {
    private final Extent fallback;
    private volatile Source source;

    /** Lo que lee el extent: la foto de una región o la de un pincel. */
    public interface Source {
        /** Estado en una posición; {@code null} es aire. */
        BlockState state(int x, int y, int z);

        BlockVector3 minimum();

        BlockVector3 maximum();
    }

    public SnapshotExtent() {
        this(null);
    }

    /** @param fallback lo que se lee antes de conectar la foto (solo en el hilo del servidor) o {@code null} */
    public SnapshotExtent(Extent fallback) {
        this.fallback = fallback;
    }

    /** Conecta la foto ya capturada; hasta entonces el extent lee el respaldo o, sin él, aire. */
    public void bind(RegionSnapshot snapshot) {
        bind(new Source() {
            @Override
            public BlockState state(int x, int y, int z) {
                return snapshot.state(x, y, z);
            }

            @Override
            public BlockVector3 minimum() {
                return BlockVector3.at(snapshot.minimumX(), snapshot.minimumY(), snapshot.minimumZ());
            }

            @Override
            public BlockVector3 maximum() {
                return BlockVector3.at(snapshot.maximumX(), snapshot.maximumY(), snapshot.maximumZ());
            }
        });
    }

    public void bind(Source source) {
        this.source = source;
    }

    @Override
    public BlockVector3 getMinimumPoint() {
        Source current = source;
        if (current == null) {
            return fallback == null ? BlockVector3.ZERO : fallback.getMinimumPoint();
        }
        return current.minimum();
    }

    @Override
    public BlockVector3 getMaximumPoint() {
        Source current = source;
        if (current == null) {
            return fallback == null ? BlockVector3.ZERO : fallback.getMaximumPoint();
        }
        return current.maximum();
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
        Source current = source;
        if (current == null) {
            return fallback == null ? BlockTypes.AIR.getDefaultState() : fallback.getBlock(position);
        }
        BlockState state = current.state(position.x(), position.y(), position.z());
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
