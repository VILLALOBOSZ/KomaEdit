package com.komainu.moxia.blueprint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Las miniaturas viajan al cliente en trozos pequeños, unos pocos por tick, para no atascar la conexión cuando el
 * Studio pide la biblioteca entera. El servidor parte el PNG ({@link #split}) y lo pone en una {@link Queue} con
 * presupuesto de bytes por tick; el cliente lo junta con un {@link Assembler}, que comprueba la huella antes de
 * darlo por bueno. Todo es lógica pura, sin red.
 */
public final class ThumbnailChunks {
    /** Bytes de cada trozo. */
    public static final int CHUNK_BYTES = 4 * 1024;
    /** Trozos de una miniatura como mucho (lo que cabe en {@link BlueprintLibrary#MAX_THUMBNAIL_BYTES}). */
    public static final int MAX_CHUNKS = BlueprintLibrary.MAX_THUMBNAIL_BYTES / CHUNK_BYTES;
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private ThumbnailChunks() {
    }

    /** Un trozo: la huella (SHA-256 del PNG entero), su número, cuántos son y sus bytes. */
    public record Chunk(String hash, int index, int total, byte[] data) {
        public Chunk {
            if (hash == null || !HASH.matcher(hash).matches()) {
                throw new IllegalArgumentException("Huella de miniatura no válida");
            }
            if (total < 1 || total > MAX_CHUNKS || index < 0 || index >= total) {
                throw new IllegalArgumentException("Trozo de miniatura fuera de rango: " + index + "/" + total);
            }
            if (data == null || data.length > CHUNK_BYTES) {
                throw new IllegalArgumentException("Trozo de miniatura demasiado grande");
            }
        }
    }

    public static boolean validHash(String hash) {
        return hash != null && HASH.matcher(hash).matches();
    }

    public static List<Chunk> split(byte[] png) {
        return split(BlueprintLibrary.sha256(png), png, CHUNK_BYTES);
    }

    public static List<Chunk> split(String hash, byte[] data, int chunkBytes) {
        if (chunkBytes < 1 || chunkBytes > CHUNK_BYTES) {
            throw new IllegalArgumentException("Tamaño de trozo no válido");
        }
        int total = Math.max(1, (data.length + chunkBytes - 1) / chunkBytes);
        if (total > MAX_CHUNKS) {
            throw new IllegalArgumentException("La miniatura es demasiado grande para enviarla");
        }
        List<Chunk> chunks = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            int from = index * chunkBytes;
            int to = Math.min(data.length, from + chunkBytes);
            chunks.add(new Chunk(hash, index, total, Arrays.copyOfRange(data, from, to)));
        }
        return chunks;
    }

    /**
     * Junta los trozos que llegan, en cualquier orden y mezclados entre miniaturas. Una miniatura a medias se
     * olvida si llegan demasiadas otras mientras tanto ({@code maxPending}). Si la huella no cuadra, se descarta.
     */
    public static final class Assembler {
        private final int maxPending;
        private final Map<String, Partial> pending = new LinkedHashMap<>();

        public Assembler(int maxPending) {
            this.maxPending = Math.max(1, maxPending);
        }

        private static final class Partial {
            final byte[][] parts;
            int received;

            Partial(int total) {
                parts = new byte[total][];
            }
        }

        /** @return el PNG completo si este trozo era el último que faltaba y la huella cuadra */
        public synchronized Optional<byte[]> accept(Chunk chunk) {
            Partial partial = pending.get(chunk.hash());
            if (partial != null && partial.parts.length != chunk.total()) {
                // Mismo PNG anunciado con otro número de trozos: algo va mal, se empieza de cero.
                pending.remove(chunk.hash());
                partial = null;
            }
            if (partial == null) {
                partial = new Partial(chunk.total());
                pending.put(chunk.hash(), partial);
                trim();
            }
            if (partial.parts[chunk.index()] == null) {
                partial.parts[chunk.index()] = chunk.data();
                partial.received++;
            }
            if (partial.received < partial.parts.length) {
                return Optional.empty();
            }
            pending.remove(chunk.hash());
            int length = 0;
            for (byte[] part : partial.parts) {
                length += part.length;
            }
            byte[] data = new byte[length];
            int offset = 0;
            for (byte[] part : partial.parts) {
                System.arraycopy(part, 0, data, offset, part.length);
                offset += part.length;
            }
            return BlueprintLibrary.sha256(data).equals(chunk.hash()) ? Optional.of(data) : Optional.empty();
        }

        public synchronized int pending() {
            return pending.size();
        }

        public synchronized void clear() {
            pending.clear();
        }

        private void trim() {
            Iterator<String> oldest = pending.keySet().iterator();
            while (pending.size() > maxPending && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
        }
    }

    /** Cola de envío de un jugador: cada tick saca trozos hasta gastar el presupuesto (al menos uno). */
    public static final class Queue {
        private final Deque<Chunk> chunks = new ArrayDeque<>();
        private final java.util.Set<String> queued = new java.util.HashSet<>();

        /** Encola una miniatura entera; si ya estaba en cola, no la repite. */
        public synchronized boolean offer(List<Chunk> thumbnail) {
            if (thumbnail.isEmpty() || !queued.add(thumbnail.getFirst().hash())) {
                return false;
            }
            chunks.addAll(thumbnail);
            return true;
        }

        public synchronized List<Chunk> poll(int budgetBytes) {
            List<Chunk> out = new ArrayList<>();
            int spent = 0;
            while (!chunks.isEmpty() && (out.isEmpty() || spent + chunks.peekFirst().data().length <= budgetBytes)) {
                Chunk chunk = chunks.pollFirst();
                spent += chunk.data().length;
                out.add(chunk);
                if (chunk.index() == chunk.total() - 1) {
                    queued.remove(chunk.hash());
                }
            }
            return out;
        }

        public synchronized boolean isEmpty() {
            return chunks.isEmpty();
        }

        public synchronized void clear() {
            chunks.clear();
            queued.clear();
        }
    }
}
