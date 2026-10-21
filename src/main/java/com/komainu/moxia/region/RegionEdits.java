package com.komainu.moxia.region;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import com.sk89q.worldedit.world.block.BlockState;

import java.util.concurrent.CancellationException;

/**
 * Cambios que produce una operación de región. Cada escritura es condicional: solo se aplica si el bloque sigue
 * siendo el de la foto, para no pisar lo que alguien haya construido mientras se calculaba.
 */
public final class RegionEdits {
    private final RegionSnapshot snapshot;
    private final int limit;
    private final PackedBlockPlan.Builder builder;
    private int changes;
    private int checks;
    private PackedBlockPlan sealed;

    public RegionEdits(RegionSnapshot snapshot, int limit) {
        this.snapshot = snapshot;
        this.limit = limit;
        this.builder = PackedBlockPlan.builder((int) Math.min(1 << 16, Math.max(16L, snapshot.volume() / 8)));
    }

    /** Escribe dentro de la selección; fuera de ella no hace nada. */
    public boolean set(int x, int y, int z, BlockState state) {
        return snapshot.selected(x, y, z) && write(x, y, z, state);
    }

    /** Escribe en cualquier punto capturado, también en el margen (por ejemplo escombros al pie de un muro). */
    public boolean setInCapture(int x, int y, int z, BlockState state) {
        return snapshot.contains(x, y, z) && write(x, y, z, state);
    }

    private boolean write(int x, int y, int z, BlockState state) {
        if (sealed != null) {
            throw new IllegalStateException("El plan ya está cerrado");
        }
        if (state == null) {
            throw new IllegalArgumentException("Estado de bloque vacío");
        }
        BlockState current = snapshot.state(x, y, z);
        if (state.equals(current)) {
            return false;
        }
        builder.addConditionalOverride(x, y, z, snapshot.rawId(x, y, z), state);
        if (++changes > limit) {
            throw new GeometryLimitException(limit);
        }
        return true;
    }

    /** Llamar dentro de bucles largos: corta el trabajo si el jugador cancela o se desconecta. */
    public void checkCancelled() {
        if ((checks++ & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
            throw new CancellationException();
        }
    }

    public int changes() {
        return changes;
    }

    /**
     * Cierra el plan: lo monta (y lo comprime, si es grande) una vez. Se llama al final del cálculo, en el worker,
     * para no hacerlo en el hilo del servidor; {@link #plan} lo devuelve después sin más.
     */
    public void seal() {
        if (sealed == null) {
            sealed = builder.build();
        }
    }

    public PackedBlockPlan plan() {
        seal();
        return sealed;
    }
}
