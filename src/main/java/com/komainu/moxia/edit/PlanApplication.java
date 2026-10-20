package com.komainu.moxia.edit;

import com.sk89q.worldedit.MaxChangedBlocksException;

import java.util.function.LongSupplier;

/**
 * Recorrido de un {@link PackedBlockPlan} por tramos, el que usa {@link BatchEditService} tick a tick: comprueba el
 * estado esperado de cada entrada (si el bloque cambió desde el cálculo se deja como está y cuenta como protegido),
 * la escribe y avanza.
 *
 * <p>Parar a medias ({@link #stop}) o chocar con el límite de WorldEdit deja el recorrido en una frontera de
 * entrada: todo lo anterior está escrito y nada de lo posterior, así que el historial de la sesión (y //undo)
 * cubre exactamente lo colocado. No depende de Minecraft: el mundo y la escritura llegan por {@link Target}.
 */
public final class PlanApplication {
    /** Mundo y escritura de una aplicación. */
    public interface Target {
        int rawIdAt(int x, int y, int z);

        /**
         * Escribe la entrada {@code index} del plan.
         *
         * @return {@code false} si la posición se dejó sin tocar
         */
        boolean write(int index, int x, int y, int z) throws MaxChangedBlocksException;
    }

    private final PackedBlockPlan plan;
    private int next;
    private int written;
    private int protectedBlocks;
    private boolean stopped;

    public PlanApplication(PackedBlockPlan plan) {
        this.plan = plan;
    }

    /**
     * Avanza hasta {@code maxEntries} entradas o hasta {@code deadline} (en el reloj de {@code clock}). Si la
     * escritura lanza, la entrada que falló no cuenta como hecha.
     *
     * @return entradas recorridas en este tramo
     */
    public int step(Target target, int maxEntries, long deadline, LongSupplier clock) throws MaxChangedBlocksException {
        int processed = 0;
        while (!done() && processed < maxEntries && clock.getAsLong() < deadline) {
            long packed = plan.packedPosition(next);
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            int expected = plan.expectedStateId(next);
            if (expected != PackedBlockPlan.NO_EXPECTED_STATE && target.rawIdAt(x, y, z) != expected) {
                protectedBlocks++;
            } else if (target.write(next, x, y, z)) {
                written++;
            }
            next++;
            processed++;
        }
        return processed;
    }

    /** Detiene el recorrido en la entrada actual; lo ya escrito se queda. */
    public void stop() {
        stopped = true;
    }

    public boolean done() {
        return stopped || next >= plan.size();
    }

    public boolean stopped() {
        return stopped;
    }

    /** Entradas recorridas (escritas, protegidas o dejadas sin tocar). */
    public int processed() {
        return next;
    }

    public int written() {
        return written;
    }

    public int protectedBlocks() {
        return protectedBlocks;
    }

    public int total() {
        return plan.size();
    }
}
