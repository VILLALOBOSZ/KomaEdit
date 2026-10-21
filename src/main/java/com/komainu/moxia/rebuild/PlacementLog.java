package com.komainu.moxia.rebuild;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * El orden en que se colocó cada bloque de un mundo, para el orden «historial» de la reconstrucción. Guarda las
 * últimas {@code capacity} colocaciones: al llenarse, se olvida la más antigua. Volver a colocar en una posición la
 * pone al final; quitar el bloque la olvida.
 *
 * <p>Cada colocación cuesta unos 30 bytes (la posición en un anillo y su puesto en una tabla). Java puro; lo usa el
 * servidor desde su hilo.</p>
 */
public final class PlacementLog {
    private static final int MAGIC = 0x4B45504C;
    private static final int VERSION = 1;

    private final int capacity;
    /** Se reserva al apuntar la primera colocación: un mundo sin historial no ocupa nada. */
    private long[] ring;
    private final Long2LongOpenHashMap ranks = new Long2LongOpenHashMap();
    private long nextRank;
    private boolean dirty;

    public PlacementLog(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("La capacidad no puede ser negativa");
        }
        this.capacity = capacity;
        ranks.defaultReturnValue(RebuildModel.NO_HISTORY);
    }

    /** Apunta que se acaba de colocar un bloque en {@code position} (empaquetada con {@code PackedBlockPlan.pack}). */
    public void record(long position) {
        if (capacity == 0) {
            return;
        }
        if (ring == null) {
            ring = new long[capacity];
        }
        int slot = (int) (nextRank % capacity);
        if (nextRank >= capacity) {
            long evicted = ring[slot];
            if (ranks.get(evicted) == nextRank - capacity) {
                ranks.remove(evicted);
            }
        }
        ring[slot] = position;
        ranks.put(position, nextRank);
        nextRank++;
        dirty = true;
    }

    /** El bloque de {@code position} ya no está (se rompió o se vació). */
    public void forget(long position) {
        if (ranks.remove(position) != RebuildModel.NO_HISTORY) {
            dirty = true;
        }
    }

    /** Puesto de la última colocación en {@code position} (mayor = más reciente), o {@link RebuildModel#NO_HISTORY}. */
    public long rank(long position) {
        return ranks.get(position);
    }

    public int size() {
        return ranks.size();
    }

    public int capacity() {
        return capacity;
    }

    /** Si cambió desde la última vez que se guardó. */
    public boolean dirty() {
        return dirty;
    }

    /** Las posiciones apuntadas, de la más antigua a la más reciente (una vez cada una). */
    public long[] positions() {
        long[] positions = new long[ranks.size()];
        if (ring == null) {
            return positions;
        }
        int count = 0;
        long first = Math.max(0L, nextRank - capacity);
        for (long rank = first; rank < nextRank; rank++) {
            long position = ring[(int) (rank % capacity)];
            if (ranks.get(position) == rank) {
                positions[count++] = position;
            }
        }
        return count == positions.length ? positions : Arrays.copyOf(positions, count);
    }

    /** Guarda las posiciones en orden; {@link #read} las vuelve a apuntar en el mismo orden. */
    public void write(OutputStream output) throws IOException {
        writePositions(positions(), output);
        dirty = false;
    }

    /** Lo de {@link #write} con las posiciones ya copiadas (para escribirlas en otro hilo). */
    public static void writePositions(long[] positions, OutputStream output) throws IOException {
        GZIPOutputStream gzip = new GZIPOutputStream(output);
        DataOutputStream data = new DataOutputStream(new BufferedOutputStream(gzip, 1 << 16));
        data.writeInt(MAGIC);
        data.writeInt(VERSION);
        data.writeInt(positions.length);
        for (long position : positions) {
            data.writeLong(position);
        }
        data.flush();
        gzip.finish();
    }

    /** Lee lo guardado; si hay más de lo que cabe, se quedan las más recientes. */
    public static PlacementLog read(InputStream input, int capacity) throws IOException {
        DataInputStream data = new DataInputStream(new BufferedInputStream(new GZIPInputStream(input), 1 << 16));
        if (data.readInt() != MAGIC) {
            throw new IOException("No es un historial de colocación de KomaEdit");
        }
        int version = data.readInt();
        if (version != VERSION) {
            throw new IOException("Versión de historial de colocación desconocida: " + version);
        }
        int count = data.readInt();
        if (count < 0) {
            throw new IOException("Historial de colocación dañado");
        }
        PlacementLog log = new PlacementLog(capacity);
        for (int index = 0; index < count; index++) {
            log.record(data.readLong());
        }
        log.dirty = false;
        return log;
    }

    /** Marca como guardado (tras escribir una copia de {@link #positions} en otro hilo). */
    public void saved() {
        dirty = false;
    }
}
