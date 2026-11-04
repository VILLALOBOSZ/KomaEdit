package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.terrain.stamp.StampInfo;

/**
 * Un sello propio tal como lo ve el Studio: lo que dice la galería de él y con qué se pinta su miniatura.
 *
 * @param hash huella del PNG: la miniatura se pide por ella y solo cambia si cambia el sello
 * @param mine si lo capturó quien lo recibe (puede borrarlo o cambiarlo)
 */
public record OwnStampSummary(String name, String author, String description, StampCategory category,
                              float heightRatio, float neutral, String hash, int sizeX, int sizeZ, boolean mine) {
    /** Lo que la galería y el pincel saben de él, como de uno de serie. */
    public StampInfo info() {
        return new StampInfo(name, category, description, neutral, heightRatio, category.palette());
    }

    public OwnStampSummary withName(String value) {
        return new OwnStampSummary(value, author, description, category, heightRatio, neutral, hash + ":" + value,
                sizeX, sizeZ, mine);
    }

    /** El nombre para enseñar: con espacios en lugar de subrayados. */
    public String label() {
        return name.replace('_', ' ');
    }
}
