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

    /** Lado más largo de una miniatura que el cliente acepta: las de KomaEdit miden {@link BlueprintThumbnail#SIZE}. */
    public static final int MAX_SIDE = 512;

    /**
     * ¿Es un PNG de como mucho {@code maxSide} de lado? Se mira la cabecera ({@code IHDR}) antes de decodificarlo: un
     * PNG de 256 KiB puede decir que mide 30000 × 30000, y el cliente reservaba esa imagen entera en memoria.
     */
    public static boolean fits(byte[] png, int maxSide) {
        if (png == null || png.length < 24 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N'
                || png[3] != 'G' || png[12] != 'I' || png[13] != 'H' || png[14] != 'D' || png[15] != 'R') {
            return false;
        }
        long width = readInt(png, 16);
        long height = readInt(png, 20);
        return width >= 1 && height >= 1 && width <= maxSide && height <= maxSide;
    }

    private static long readInt(byte[] data, int at) {
        return (data[at] & 0xFFL) << 24 | (data[at + 1] & 0xFF) << 16 | (data[at + 2] & 0xFF) << 8
                | data[at + 3] & 0xFF;
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
        /**
         * Miniaturas pedidas al disco que aún no han llegado a la cola, como mucho: más que una biblioteca de serie
         * entera (64 propios y 512 compartidos), que el cliente pide de una vez y no repite.
         */
        public static final int MAX_PENDING = 1_024;

        private final Deque<Chunk> chunks = new ArrayDeque<>();
        private final java.util.Set<String> queued = new java.util.HashSet<>();
        /** Pedidas al disco que aún no han llegado. */
        private final java.util.Set<String> requested = new java.util.HashSet<>();

        /** Encola una miniatura entera; si ya estaba en cola, no la repite. */
        public synchronized boolean offer(List<Chunk> thumbnail) {
            if (!thumbnail.isEmpty()) {
                requested.remove(thumbnail.getFirst().hash());
            }
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

        /**
         * ¿Hay que leer del disco la miniatura que pide el cliente? No si ya se está leyendo o enviando, ni si hay
         * {@link #MAX_PENDING} lecturas pendientes: así pedirla una y otra vez no cuesta nada.
         */
        public synchronized boolean request(String hash) {
            if (queued.contains(hash) || requested.contains(hash) || requested.size() >= MAX_PENDING) {
                return false;
            }
            requested.add(hash);
            return true;
        }

        /** Terminó la lectura de esa miniatura, llegara o no (no existe, o falló). */
        public synchronized void finished(String hash) {
            requested.remove(hash);
        }

        /** Sin nada que enviar ni lecturas pendientes. */
        public synchronized boolean isEmpty() {
            return chunks.isEmpty() && requested.isEmpty();
        }

        public synchronized void clear() {
            chunks.clear();
            queued.clear();
            requested.clear();
        }
    }
}
