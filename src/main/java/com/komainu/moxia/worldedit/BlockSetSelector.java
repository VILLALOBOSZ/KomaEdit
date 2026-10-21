package com.komainu.moxia.worldedit;

import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.selection.BlockSet;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.limit.SelectorLimits;
import com.sk89q.worldedit.util.formatting.text.Component;
import com.sk89q.worldedit.util.formatting.text.TextComponent;
import com.sk89q.worldedit.util.formatting.text.format.TextColor;
import com.sk89q.worldedit.world.World;

import java.util.List;

/**
 * El selector de WorldEdit con la selección de bloques sueltos de KomaEdit ({@link BlockSetRegion}). Para WorldEdit
 * es una selección más: //size la cuenta, //sel la quita y las órdenes de región la usan.
 *
 * <p>Si con ella puesta se marca un punto con la varita, //pos1 o //pos2, pasa a ser una caja de WorldEdit normal
 * desde ese punto ({@link #fallback}); el servidor la cambia por un selector de caja de verdad en cuanto la ve.</p>
 *
 * <p>{@link #version} cambia con cada selección nueva: así se sabe cuándo hay que volver a mandar el contorno.</p>
 */
public final class BlockSetSelector implements RegionSelector {
    private static long versions;

    private World world;
    private BlockSetRegion region;
    private CuboidRegionSelector fallback;
    private final long version;

    public BlockSetSelector(World world, BlockSet blocks, int maxBlocks) {
        this.world = world;
        this.region = new BlockSetRegion(world, blocks, maxBlocks);
        this.version = nextVersion();
    }

    private static synchronized long nextVersion() {
        return ++versions;
    }

    /** Lo seleccionado ahora, o {@code null} si se quitó o se pasó a caja. */
    public BlockSet blocks() {
        return fallback == null && region != null ? region.blocks() : null;
    }

    /** La caja de WorldEdit en que se convirtió al marcar un punto, o {@code null}. */
    public CuboidRegionSelector fallback() {
        return fallback;
    }

    /**
     * Cambia cuando cambia lo seleccionado: con cada selector nuevo, al mover o estirar la región con órdenes de
     * WorldEdit (que cambian sus bloques sin cambiar de selector) y al quitarla.
     */
    public long version() {
        return blocks() == null ? -version : version * 1_000_003L + region.revision();
    }

    @Override
    public World getWorld() {
        return fallback != null ? fallback.getWorld() : world;
    }

    @Override
    public void setWorld(World world) {
        this.world = world;
        if (region != null) {
            region.setWorld(world);
        }
        if (fallback != null) {
            fallback.setWorld(world);
        }
    }

    @Override
    public boolean selectPrimary(BlockVector3 position, SelectorLimits limits) {
        fallback = new CuboidRegionSelector(world);
        region = null;
        return fallback.selectPrimary(position, limits);
    }

    @Override
    public boolean selectSecondary(BlockVector3 position, SelectorLimits limits) {
        if (fallback == null) {
            fallback = new CuboidRegionSelector(world);
            region = null;
        }
        return fallback.selectSecondary(position, limits);
    }

    @Override
    public void explainPrimarySelection(Actor actor, LocalSession session, BlockVector3 position) {
        if (fallback != null) {
            fallback.explainPrimarySelection(actor, session, position);
        }
    }

    @Override
    public void explainSecondarySelection(Actor actor, LocalSession session, BlockVector3 position) {
        if (fallback != null) {
            fallback.explainSecondarySelection(actor, session, position);
        }
    }

    @Override
    public void explainRegionAdjust(Actor actor, LocalSession session) {
        if (fallback != null) {
            fallback.explainRegionAdjust(actor, session);
        } else if (region != null) {
            actor.printInfo(TextComponent.of("Selección de KomaEdit: " + ProgressLine.group(region.getVolume())
                    + " bloques"));
        }
    }

    @Override
    public BlockVector3 getPrimaryPosition() throws IncompleteRegionException {
        if (fallback != null) {
            return fallback.getPrimaryPosition();
        }
        if (region == null) {
            throw new IncompleteRegionException();
        }
        return region.getMinimumPoint();
    }

    @Override
    public Region getRegion() throws IncompleteRegionException {
        if (fallback != null) {
            return fallback.getRegion();
        }
        if (region == null) {
            throw new IncompleteRegionException();
        }
        return region;
    }

    @Override
    public Region getIncompleteRegion() {
        if (fallback != null) {
            return fallback.getIncompleteRegion();
        }
        return region != null ? region : new CuboidRegion(world, BlockVector3.ZERO, BlockVector3.ZERO);
    }

    @Override
    public boolean isDefined() {
        return fallback != null ? fallback.isDefined() : region != null;
    }

    @Override
    public long getVolume() {
        if (fallback != null) {
            return fallback.getVolume();
        }
        return region == null ? 0 : region.getVolume();
    }

    @Override
    public void learnChanges() {
        if (fallback != null) {
            fallback.learnChanges();
        }
    }

    @Override
    public void clear() {
        region = null;
        if (fallback != null) {
            fallback.clear();
        }
    }

    @Override
    public String getTypeName() {
        return fallback != null ? fallback.getTypeName() : "KomaEdit (bloques sueltos)";
    }

    @Override
    public List<Component> getSelectionInfoLines() {
        if (fallback != null) {
            return fallback.getSelectionInfoLines();
        }
        if (region == null) {
            return List.of();
        }
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        return List.of(
                TextComponent.of("Bloques: " + ProgressLine.group(region.getVolume()), TextColor.LIGHT_PURPLE),
                TextComponent.of("Caja: " + min + " → " + max, TextColor.LIGHT_PURPLE));
    }
}
