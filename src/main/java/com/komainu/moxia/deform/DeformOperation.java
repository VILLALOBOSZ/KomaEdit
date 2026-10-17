package com.komainu.moxia.deform;

import com.komainu.moxia.edit.SmoothBlockStates;
import com.komainu.moxia.region.RegionEdits;
import com.komainu.moxia.region.RegionOperation;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * Puente entre la lógica pura de {@code deform} y el motor de región: arma el {@link Volume}, pide el campo a la
 * operación y lo escribe con {@link FieldWriter}, convirtiendo cada id y pieza en un estado de WorldEdit.
 */
public final class DeformOperation implements RegionOperation {
    private final Planner planner;
    private final SmoothProfile profile;

    /** Calcula el campo y la caja donde escribirlo sobre la rejilla ya leída. */
    @FunctionalInterface
    public interface Planner {
        Plan plan(Volume volume, Runnable checkCancelled);
    }

    /** Campo y caja a recorrer ({@code minX…maxZ}). */
    public record Plan(DeformField field, int[] box) {
    }

    public DeformOperation(Planner planner, SmoothProfile profile) {
        this.planner = planner;
        this.profile = profile == null ? SmoothProfile.NONE : profile;
    }

    /** Recorre la selección (su caja). */
    public static Planner inSelection(java.util.function.BiFunction<Volume, Runnable, DeformField> fields) {
        return (volume, checkCancelled) -> {
            int[] bounds = volume.selectionBounds();
            if (bounds == null) {
                throw new IllegalArgumentException("La selección está vacía");
            }
            return new Plan(fields.apply(volume, checkCancelled), bounds);
        };
    }

    @Override
    public void apply(RegionSnapshot snapshot, RegionEdits edits) {
        Volume volume = Volume.of(snapshot);
        Runnable checkCancelled = edits::checkCancelled;
        Plan plan = planner.plan(volume, checkCancelled);
        Long2ObjectOpenHashMap<BlockState> shapes = new Long2ObjectOpenHashMap<>();
        BlockState air = BlockTypes.AIR.getDefaultState();
        FieldWriter.write(volume, plan.field(), plan.box(), profile, (x, y, z, rawId, piece) -> {
            BlockState state = rawId == DeformField.AIR ? air : snapshot.stateInfo(rawId).state();
            if (state == null) {
                return;
            }
            if (piece != SmoothPiece.FULL) {
                long key = (long) rawId << 8 | (piece & 0xFF);
                BlockState shaped = shapes.get(key);
                if (shaped == null && !shapes.containsKey(key)) {
                    shaped = SmoothBlockStates.shape(state.toBaseBlock(), piece);
                    shapes.put(key, shaped);
                }
                if (shaped != null) {
                    state = shaped;
                }
            }
            edits.setInCapture(x, y, z, state);
        }, checkCancelled);
    }
}
