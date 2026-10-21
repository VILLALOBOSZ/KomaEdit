package com.komainu.moxia.editor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;

/**
 * Las pasadas que esperan su turno en un trazo de la pintura rápida: cada centro que llega se une al anterior con
 * pasadas intermedias (una cada medio radio, como mucho 128) y un centro ya puesto no se repite. Java puro.
 */
final class DabTrail {
    /** Pasadas en espera como mucho; las que llegan de más se descartan. */
    static final int MAX_PENDING = 1_024;
    private final int radius;
    private final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
    private final LongOpenHashSet queued = new LongOpenHashSet();
    private BlockPos last;

    DabTrail(int radius) {
        this.radius = radius;
    }

    /** Une {@code center} con el centro anterior del trazo. */
    void add(BlockPos center) {
        BlockPos next = center.toImmutable();
        if (last == null) {
            enqueue(next);
        } else {
            double distance = Math.sqrt(last.getSquaredDistance(next));
            int spacing = Math.max(1, radius / 2);
            int steps = Math.clamp((int) Math.ceil(distance / spacing), 1, 128);
            for (int step = 1; step <= steps; step++) {
                double progress = step / (double) steps;
                enqueue(new BlockPos(
                        (int) Math.round(last.getX() + (next.getX() - last.getX()) * progress),
                        (int) Math.round(last.getY() + (next.getY() - last.getY()) * progress),
                        (int) Math.round(last.getZ() + (next.getZ() - last.getZ()) * progress)
                ));
            }
        }
        last = next;
    }

    /** El trazo sigue, pero el próximo centro no se une al último (se levantó el pincel). */
    void restart() {
        last = null;
    }

    /** La siguiente pasada, o {@code null} si no queda ninguna. */
    BlockPos poll() {
        return pending.pollFirst();
    }

    boolean isEmpty() {
        return pending.isEmpty();
    }

    int pending() {
        return pending.size();
    }

    private void enqueue(BlockPos center) {
        if (pending.size() < MAX_PENDING && queued.add(center.asLong())) {
            pending.addLast(center);
        }
    }
}
