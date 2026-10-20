package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Una pasada de árboles o rocas: uno o varios sitios que se calculan en el worker sobre la misma foto y se colocan
 * juntos, en una sola operación de //undo. Cada sitio solo escribe dentro de la foto y respeta la máscara {@code -m};
 * la máscara global la aplica la sesión de WorldEdit.
 */
final class NatureOperation implements BrushOperation {
    /** Genera los bloques de un sitio; todo lo que necesita ya está resuelto. */
    @FunctionalInterface
    interface Generator {
        NatureVoxels generate(NatureTerrain terrain, Bounds bounds, int limit, BooleanSupplier cancelled);
    }

    /** Un árbol o una roca: la caja que puede ocupar y cómo generarlo. */
    record Site(Bounds reach, Generator generator) {
    }

    private final String displayName;
    private final int color;
    private final List<Site> sites;
    private final NatureMaterials materials;
    private final NatureMask mask;
    private final int limit;
    private final Bounds box;

    NatureOperation(String displayName, int color, List<Site> sites, NatureMaterials materials, NatureMask mask,
                    int limit) {
        if (sites.isEmpty()) {
            throw new IllegalArgumentException("No hay ningún sitio donde generar");
        }
        this.displayName = displayName;
        this.color = color;
        this.sites = List.copyOf(sites);
        this.materials = materials;
        this.mask = mask;
        this.limit = limit;
        Bounds union = sites.getFirst().reach();
        for (Site site : sites) {
            union = union.union(site.reach());
        }
        box = union;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public int previewColor() {
        return color;
    }

    @Override
    public Capture capture() {
        int centerX = Math.floorDiv(box.minX() + box.maxX(), 2);
        int centerY = Math.floorDiv(box.minY() + box.maxY(), 2);
        int centerZ = Math.floorDiv(box.minZ() + box.maxZ(), 2);
        int horizontal = Math.max(box.maxX() - centerX, box.maxZ() - centerZ) + 1;
        int vertical = Math.max(box.maxY() - centerY, centerY - box.minY()) + 1;
        return new Capture(BlockVector3.at(centerX, centerY, centerZ), horizontal, vertical);
    }

    @Override
    public PackedBlockPlan plan(WorldSnapshot snapshot) {
        NatureTerrain terrain = new SnapshotNatureTerrain(snapshot);
        BooleanSupplier cancelled = () -> Thread.currentThread().isInterrupted();
        NatureVoxels all = new NatureVoxels(limit);
        for (int index = 0; index < sites.size(); index++) {
            AsyncEngine.progress(index, sites.size() + 1L);
            Site site = sites.get(index);
            Bounds reach = site.reach();
            // La foto recorta la altura al mundo: nada se genera fuera de lo leído.
            Bounds bounds = new Bounds(reach.minX(), Math.max(reach.minY(), snapshot.minimumY()), reach.minZ(),
                    reach.maxX(), Math.min(reach.maxY(), snapshot.maximumY()), reach.maxZ());
            merge(all, site.generator().generate(terrain, bounds, limit, cancelled));
        }
        AsyncEngine.progress(sites.size(), sites.size() + 1L);
        if (mask != null) {
            mask.bind(snapshot);
        }
        long[] keys = all.sortedKeys();
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(keys.length);
        for (long key : keys) {
            BlockState state = materials.state(key, all.info(key));
            if (state == null) {
                continue;
            }
            int x = PackedBlockPlan.unpackX(key);
            int y = PackedBlockPlan.unpackY(key);
            int z = PackedBlockPlan.unpackZ(key);
            if (mask != null && !mask.test(x, y, z)) {
                continue;
            }
            if (!state.equals(snapshot.get(x, y, z))) {
                builder.addOverride(x, y, z, state);
            }
        }
        return builder.build();
    }

    /** Junta un sitio con los anteriores: lo que ya está se queda, salvo hojas que tapa madera o roca nueva. */
    static void merge(NatureVoxels into, NatureVoxels from) {
        for (long key : from.sortedKeys()) {
            int info = from.info(key);
            int existing = into.info(key);
            if (existing < 0 || NatureVoxels.role(existing).foliage() && !NatureVoxels.role(info).foliage()) {
                NatureVoxels.Role role = NatureVoxels.role(info);
                into.set(key, role, NatureVoxels.axis(info), NatureVoxels.distance(info), NatureVoxels.shade(info),
                        NatureVoxels.ground(info));
            }
        }
    }
}
