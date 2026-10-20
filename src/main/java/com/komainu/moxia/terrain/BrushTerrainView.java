package com.komainu.moxia.terrain;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.SnapshotWorld;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * La foto de un clic de pincel ({@link WorldSnapshot}) vista como terreno. Se puede escribir en toda la caja
 * capturada; la máscara {@code -m} del pincel decide qué columnas cambian por su bloque de superficie.
 */
public final class BrushTerrainView implements TerrainView {
    private final WorldSnapshot snapshot;
    private final int minimumX;
    private final int maximumX;
    private final int minimumZ;
    private final int maximumZ;
    private final BrushMask mask;
    private final SnapshotWorld world;
    private final Map<BlockState, TerrainCell> cells = new IdentityHashMap<>();

    /** @param mask la {@code -m} ya conectada a la foto, o {@code null} */
    public BrushTerrainView(WorldSnapshot snapshot, BlockVector3 center, int horizontalRadius, BrushMask mask) {
        this.snapshot = snapshot;
        this.world = new SnapshotWorld(snapshot);
        this.minimumX = center.x() - horizontalRadius;
        this.maximumX = center.x() + horizontalRadius;
        this.minimumZ = center.z() - horizontalRadius;
        this.maximumZ = center.z() + horizontalRadius;
        this.mask = mask;
    }

    public BlockState state(int x, int y, int z) {
        return snapshot.get(x, y, z);
    }

    @Override
    public int minimumX() {
        return minimumX;
    }

    @Override
    public int maximumX() {
        return maximumX;
    }

    @Override
    public int minimumY() {
        return snapshot.minimumY();
    }

    @Override
    public int maximumY() {
        return snapshot.maximumY();
    }

    @Override
    public int minimumZ() {
        return minimumZ;
    }

    @Override
    public int maximumZ() {
        return maximumZ;
    }

    @Override
    public String blockId(int x, int y, int z) {
        return snapshot.get(x, y, z).getBlockType().id();
    }

    @Override
    public TerrainCell cell(int x, int y, int z) {
        BlockState state = snapshot.get(x, y, z);
        return cells.computeIfAbsent(state, key -> {
            var material = key.getBlockType().getMaterial();
            return TerrainCell.classify(key.getBlockType().id(), material.isAir(), material.isLiquid(),
                    material.isFullCube() && material.isSolid());
        });
    }

    @Override
    public boolean writable(int x, int y, int z) {
        return snapshot.contains(x, y, z);
    }

    @Override
    public boolean maskAllows(int x, int y, int z) {
        return mask == null || mask.matches(world, x, y, z);
    }
}
