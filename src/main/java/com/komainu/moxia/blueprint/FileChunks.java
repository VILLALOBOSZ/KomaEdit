package com.komainu.moxia.blueprint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Un esquema entero viajando entre el servidor y el cliente, en trozos pequeños para no atascar la conexión: el
 * {@code .litematic} que se exporta baja a la carpeta {@code schematics} del jugador, y el que el jugador elige de esa
 * carpeta sube para importarlo. Como las miniaturas ({@link ThumbnailChunks}), pero con nombre de archivo y más grande.
 * Todo es lógica pura, sin red: quien recibe comprueba el nombre, el tamaño y la huella antes de usar nada.
 */
public final class FileChunks {
    /** Bytes de cada trozo (un paquete del cliente al servidor no puede pasar de 32 KiB). */
    public static final int CHUNK_BYTES = 16 * 1024;
    /** Lo más grande que viaja. */
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    public static final int MAX_CHUNKS = MAX_BYTES / CHUNK_BYTES;
    /** Extensiones que viajan: los esquemas que KomaEdit lee. */
    public static final List<String> EXTENSIONS = List.of(".litematic", ".nbt", ".schem", ".schematic");
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N}_-][\\p{L}\\p{N} ._()+'-]{0,95}");
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    private FileChunks() {
    }

    /**
     * Un trozo.
     *
     * @param transfer número del envío (lo pone quien envía; distingue dos envíos del mismo archivo)
     * @param name     nombre del archivo, sin carpetas ({@link #validName})
     * @param hash     SHA-256 del archivo entero
     */
    public record Chunk(int transfer, String name, String hash, int index, int total, byte[] data) {
        public Chunk {
            if (!validName(name)) {
                throw new IllegalArgumentException("Nombre de archivo no válido");
            }
            if (hash == null || !HASH.matcher(hash).matches()) {
                throw new IllegalArgumentException("Huella de archivo no válida");
            }
            if (total < 1 || total > MAX_CHUNKS || index < 0 || index >= total) {
                throw new IllegalArgumentException("Trozo de archivo fuera de rango: " + index + "/" + total);
            }
            if (data == null || data.length > CHUNK_BYTES) {
                throw new IllegalArgumentException("Trozo de archivo demasiado grande");
            }
        }
    }

    /**
     * Un archivo entero, recibido. La huella se comprueba con {@link #verify()} en el hilo que lo vaya a usar: con
     * 16 MiB, el SHA-256 no se calcula en el del servidor.
     */
    public record Complete(String name, String hash, byte[] data) {
        /** @throws IllegalArgumentException si la huella no cuadra */
        public void verify() {
            if (!BlueprintLibrary.sha256(data).equals(hash)) {
                throw new IllegalArgumentException("El archivo llegó dañado (la huella no cuadra)");
            }
        }
    }

    /**
     * ¿Vale como nombre de un esquema que viaja? Sin barras, sin {@code ..}, con una de las {@link #EXTENSIONS} y sin
     * ser un nombre de dispositivo de Windows ({@code con.nbt}).
     */
    public static boolean validName(String name) {
        if (name == null || !NAME.matcher(name).matches() || name.contains("..") || name.endsWith(" ")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.stream().filter(lower::endsWith).findFirst().orElse(null);
        if (extension == null || lower.length() == extension.length()) {
            return false;
        }
        String base = lower.substring(0, lower.length() - extension.length()).strip();
        int dot = base.indexOf('.');
        String device = dot < 0 ? base : base.substring(0, dot);
        return !device.matches("con|prn|aux|nul|com[0-9]|lpt[0-9]");
    }

    /**
     * Un nombre que valga ({@link #validName}) hecho con el de un archivo cualquiera: lo que no vale pasa a {@code _}.
     * Si ni así vale (un nombre de dispositivo, sin nada antes de la extensión), {@code esquema} con su extensión.
     */
    public static String cleanName(String fileName) {
        StringBuilder clean = new StringBuilder();
        String value = fileName == null ? "" : fileName.strip();
        for (int index = 0; index < value.length() && clean.length() < 96; index++) {
            char character = value.charAt(index);
            clean.append(Character.isLetterOrDigit(character) || " ._()+'-".indexOf(character) >= 0 ? character : '_');
        }
        String name = clean.toString().replace("..", "_.");
        if (!name.isEmpty() && !Character.isLetterOrDigit(name.charAt(0)) && name.charAt(0) != '_'
                && name.charAt(0) != '-') {
            name = "_" + name.substring(1);
        }
        if (validName(name)) {
            return name;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.stream().filter(lower::endsWith).findFirst().orElse(".litematic");
        return "esquema" + extension;
    }

    /**
     * Dónde se guarda un archivo que llega, dentro de {@code folder} y sin pisar otro: {@code casa.litematic},
     * {@code casa-2.litematic}…
     *
     * @throws IOException si el nombre no vale o se sale de la carpeta
     */
    public static Path target(Path folder, String name) throws IOException {
        if (!validName(name)) {
            throw new IOException("nombre de archivo no válido");
        }
        Path root = folder.toAbsolutePath().normalize();
        String lower = name.toLowerCase(Locale.ROOT);
        String extension = EXTENSIONS.stream().filter(lower::endsWith).findFirst().orElseThrow();
        String base = name.substring(0, name.length() - extension.length());
        extension = name.substring(name.length() - extension.length());
        Path target = root.resolve(name).normalize();
        for (int suffix = 2; Files.exists(target, LinkOption.NOFOLLOW_LINKS); suffix++) {
            target = root.resolve(base + "-" + suffix + extension).normalize();
        }
        if (!target.startsWith(root) || !target.getParent().equals(root)) {
            throw new IOException("nombre de archivo no válido");
        }
        return target;
    }

    public static List<Chunk> split(int transfer, String name, byte[] data) {
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("El archivo ocupa " + data.length / 1024 + " KiB; el máximo es "
                    + MAX_BYTES / 1024 + " KiB");
        }
        String hash = BlueprintLibrary.sha256(data);
        int total = Math.max(1, (data.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        List<Chunk> chunks = new ArrayList<>(total);
        for (int index = 0; index < total; index++) {
            int from = index * CHUNK_BYTES;
            int to = Math.min(data.length, from + CHUNK_BYTES);
            chunks.add(new Chunk(transfer, name, hash, index, total, Arrays.copyOfRange(data, from, to)));
        }
        return chunks;
    }

    /**
     * Lo que pueden juntar a la vez varios {@link Assembler}: cuántos envíos a medias y cuántos bytes en vuelo entre
     * todos. En el servidor, uno para las subidas de todos los jugadores: abrir muchas a medias no agota la memoria.
     */
    public static final class Pool {
        private final int maxOpen;
        private final long maxBytes;
        private int open;
        private long bytes;

        public Pool(int maxOpen, long maxBytes) {
            this.maxOpen = Math.max(1, maxOpen);
            this.maxBytes = Math.max(CHUNK_BYTES, maxBytes);
        }

        synchronized boolean tryOpen() {
            if (open >= maxOpen) {
                return false;
            }
            open++;
            return true;
        }

        synchronized boolean tryReserve(long amount) {
            if (bytes + amount > maxBytes) {
                return false;
            }
            bytes += amount;
            return true;
        }

        synchronized void release(long amount) {
            bytes = Math.max(0L, bytes - amount);
            open = Math.max(0, open - 1);
        }

        /** Envíos a medias ahora. */
        public synchronized int openTransfers() {
            return open;
        }

        /** Bytes en vuelo ahora. */
        public synchronized long bytesInFlight() {
            return bytes;
        }
    }

    /**
     * Junta los trozos de un envío. Solo hay uno a medias: si empieza otro, el anterior se olvida. Los trozos que no
     * cuadran (otro número de trozos para el mismo envío, de más) tiran el envío entero. Cada envío a medias ocupa un
     * hueco de su {@link Pool} y sus bytes, hasta que acaba o se tira.
     */
    public static final class Assembler {
        private final int maxBytes;
        private final Pool pool;
        private int transfer = -1;
        private String name;
        private String hash;
        private byte[][] parts;
        private int received;
        private long bytes;
        private long startedAt;

        /** Con su propio {@link Pool}: un envío a la vez. */
        public Assembler(int maxBytes) {
            this(maxBytes, new Pool(1, MAX_BYTES));
        }

        /** @param maxBytes lo que puede ocupar el archivo entero (como mucho {@link #MAX_BYTES}) */
        public Assembler(int maxBytes, Pool pool) {
            this.maxBytes = Math.min(MAX_BYTES, Math.max(CHUNK_BYTES, maxBytes));
            this.pool = pool;
        }

        /**
         * @param now el reloj de quien recibe, para {@link #stale}
         * @return el archivo, si este era el último trozo (falta comprobar su huella, {@link Complete#verify()})
         * @throws IllegalArgumentException si el archivo es demasiado grande, sus trozos no cuadran o no queda sitio
         *                                  en el {@link Pool}
         */
        public synchronized Optional<Complete> accept(Chunk chunk, long now) {
            if (starts(chunk)) {
                clear();
                // Todos los trozos menos el último van llenos: con tantos, no cabe.
                if ((long) (chunk.total() - 1) * CHUNK_BYTES >= maxBytes) {
                    throw new IllegalArgumentException("El archivo es demasiado grande (máximo " + maxBytes / 1024
                            + " KiB)");
                }
                if (!pool.tryOpen()) {
                    throw new IllegalArgumentException("Hay demasiados archivos llegando a la vez: prueba en un rato");
                }
                transfer = chunk.transfer();
                name = chunk.name();
                hash = chunk.hash();
                parts = new byte[chunk.total()][];
                received = 0;
                bytes = 0;
                startedAt = now;
            }
            if (parts.length != chunk.total()) {
                clear();
                throw new IllegalArgumentException("Los trozos del archivo no cuadran");
            }
            if (parts[chunk.index()] == null) {
                if (bytes + chunk.data().length > maxBytes) {
                    clear();
                    throw new IllegalArgumentException("El archivo es demasiado grande (máximo " + maxBytes / 1024
                            + " KiB)");
                }
                if (!pool.tryReserve(chunk.data().length)) {
                    clear();
                    throw new IllegalArgumentException("Hay demasiados archivos llegando a la vez: prueba en un rato");
                }
                bytes += chunk.data().length;
                parts[chunk.index()] = chunk.data();
                received++;
            }
            if (received < parts.length) {
                return Optional.empty();
            }
            byte[] data = new byte[(int) bytes];
            int offset = 0;
            for (byte[] part : parts) {
                System.arraycopy(part, 0, data, offset, part.length);
                offset += part.length;
            }
            Complete complete = new Complete(name, hash, data);
            clear();
            return Optional.of(complete);
        }

        /** ¿Hay un envío a medias empezado antes de {@code before}? */
        public synchronized boolean stale(long before) {
            return parts != null && startedAt < before;
        }

        public synchronized boolean pending() {
            return parts != null;
        }

        /** El envío que se está juntando, o {@code -1}. */
        public synchronized int transfer() {
            return parts == null ? -1 : transfer;
        }

        /** ¿Este trozo empieza un envío nuevo (otro que el que se está juntando)? */
        public synchronized boolean starts(Chunk chunk) {
            return parts == null || chunk.transfer() != transfer || !chunk.name().equals(name)
                    || !chunk.hash().equals(hash);
        }

        /** Tira lo que haya a medias y devuelve su sitio al {@link Pool}. */
        public synchronized void clear() {
            if (parts != null) {
                pool.release(bytes);
            }
            transfer = -1;
            name = null;
            hash = null;
            parts = null;
            received = 0;
            bytes = 0;
        }
    }

    /** Cola de envío: cada tick saca trozos hasta gastar el presupuesto (al menos uno). */
    public static final class Queue {
        /** Archivos en cola como mucho. */
        public static final int MAX_FILES = 4;
        private final Deque<Chunk> chunks = new ArrayDeque<>();
        private int files;

        /** @return {@code false} si ya hay {@link #MAX_FILES} esperando */
        public synchronized boolean offer(List<Chunk> file) {
            if (file.isEmpty() || files >= MAX_FILES) {
                return false;
            }
            chunks.addAll(file);
            files++;
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
                    files--;
                }
            }
            return out;
        }

        public synchronized boolean isEmpty() {
            return chunks.isEmpty();
        }

        public synchronized void clear() {
            chunks.clear();
            files = 0;
        }
    }
}
