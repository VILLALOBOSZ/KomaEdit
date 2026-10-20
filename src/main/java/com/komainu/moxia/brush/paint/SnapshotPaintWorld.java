package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.SnapshotWorld;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.terrain.TerrainCell;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * {@link PaintWorld} sobre la foto de un clic. La clase de cada estado y su tono se calculan una vez por estado.
 * Hojas, troncos y plantas cuentan como abiertos: la hierba bajo un árbol sigue siendo superficie y el árbol no se
 * pinta. Una pasada la usa un solo hilo.
 */
public final class SnapshotPaintWorld implements PaintWorld {
    private final WorldSnapshot snapshot;
    private final SnapshotWorld brushWorld;
    private final PaintPalette palette;
    private final BrushMask blocks;
    private final BrushMask mask;
    private final boolean naturalOnly;
    private final Map<BlockState, Kind> kinds = new IdentityHashMap<>();
    private final Map<BlockState, Boolean> natural = new IdentityHashMap<>();

    /**
     * @param blocks      la {@code -b} ya leída, o {@code null}
     * @param mask        la {@code -m} ya conectada a la foto, o {@code null}
     * @param naturalOnly sin {@code -b} ni {@code -m}: solo el terreno natural ({@link PaintTerrain}) y la paleta
     */
    public SnapshotPaintWorld(WorldSnapshot snapshot, PaintPalette palette, BrushMask blocks, BrushMask mask,
                              boolean naturalOnly) {
        this.snapshot = snapshot;
        this.brushWorld = new SnapshotWorld(snapshot);
        this.palette = palette;
        this.blocks = blocks;
        this.mask = mask;
        this.naturalOnly = naturalOnly;
    }

    public BlockState state(int x, int y, int z) {
        return snapshot.get(x, y, z);
    }

    @Override
    public Kind kind(int x, int y, int z) {
        if (!snapshot.contains(x, y, z)) {
            return Kind.BLOCKED;
        }
        BlockState state = snapshot.get(x, y, z);
        Kind kind = kinds.get(state);
        if (kind == null) {
            kind = classify(state);
            kinds.put(state, kind);
        }
        return kind;
    }

    static Kind classify(BlockState state) {
        var material = state.getBlockType().getMaterial();
        if (material.isAir() || material.isLiquid()) {
            return Kind.OPEN;
        }
        String id = state.getBlockType().id();
        if (id.endsWith("_slab") || id.endsWith("_stairs")) {
            return Kind.SHAPED;
        }
        boolean full = material.isFullCube();
        TerrainCell cell = TerrainCell.classify(id, false, false, full && material.isSolid());
        if (cell == TerrainCell.PLANT) {
            return Kind.OPEN;
        }
        // Suelos que no son cubo entero (camino, tierra de cultivo): tapan lo de debajo, pero no se repintan.
        return full ? Kind.GROUND : Kind.BLOCKED;
    }

    @Override
    public int paletteIndex(int x, int y, int z) {
        return palette.indexOf(snapshot.get(x, y, z));
    }

    @Override
    public boolean allowed(int x, int y, int z) {
        if (blocks != null && !blocks.matches(brushWorld, x, y, z)) {
            return false;
        }
        if (mask != null && !mask.matches(brushWorld, x, y, z)) {
            return false;
        }
        if (!naturalOnly) {
            return true;
        }
        BlockState state = snapshot.get(x, y, z);
        Boolean known = natural.get(state);
        if (known == null) {
            known = PaintTerrain.natural(state.getBlockType().id()) || palette.indexOf(state) >= 0;
            natural.put(state, known);
        }
        return known;
    }

    @Override
    public int minimumY() {
        return snapshot.minimumY();
    }

    @Override
    public int maximumY() {
        return snapshot.maximumY();
    }
}
