package com.komainu.moxia.terrain;

import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.RegionSnapshot;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * La foto de {@link com.komainu.moxia.region.RegionOperationService} vista como terreno: se escribe en lo
 * seleccionado y la máscara {@code -m} decide qué columnas se tocan mirando su bloque de superficie. Se usa desde un
 * solo worker.
 */
public final class RegionTerrainView implements TerrainView {
    private final RegionSnapshot snapshot;
    private final RegionMasks.Bound mask;
    private final Int2ObjectOpenHashMap<TerrainCell> cells = new Int2ObjectOpenHashMap<>();

    /** @param mask máscara ya conectada a la foto, o {@code null} */
    public RegionTerrainView(RegionSnapshot snapshot, RegionMasks.Bound mask) {
        this.snapshot = snapshot;
        this.mask = mask;
    }

    @Override
    public int minimumX() {
        return snapshot.minimumX();
    }

    @Override
    public int maximumX() {
        return snapshot.maximumX();
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
        return snapshot.minimumZ();
    }

    @Override
    public int maximumZ() {
        return snapshot.maximumZ();
    }

    @Override
    public String blockId(int x, int y, int z) {
        return snapshot.blockId(x, y, z);
    }

    @Override
    public TerrainCell cell(int x, int y, int z) {
        int rawId = snapshot.rawId(x, y, z);
        TerrainCell cell = cells.get(rawId);
        if (cell == null) {
            RegionSnapshot.StateInfo info = snapshot.stateInfo(rawId);
            cell = TerrainCell.classify(info.blockId(), info.air(), info.liquid(), info.solid());
            cells.put(rawId, cell);
        }
        return cell;
    }

    @Override
    public boolean writable(int x, int y, int z) {
        return snapshot.selected(x, y, z);
    }

    @Override
    public boolean maskAllows(int x, int y, int z) {
        return mask == null || mask.test(x, y, z);
    }
}
