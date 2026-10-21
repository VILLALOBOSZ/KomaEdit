package com.komainu.moxia.edit;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Archivo temporal con las páginas de un plan que no caben en memoria ({@link PlanPages}). Se escribe una vez al
 * montar el plan y luego solo se lee, desde cualquier hilo (lecturas por posición). El archivo se borra al cerrarse,
 * y se cierra solo cuando nadie usa ya el plan; si el servidor se para de golpe, {@link #sweep} borra los restos al
 * arrancar.
 */
final class PlanSpill {
    static final String PREFIX = "komaedit-plan-";
    private static final Cleaner CLEANER = Cleaner.create();

    private final Path file;
    private final FileChannel channel;
    private final AtomicBoolean closed;
    private long length;

    record Slot(long offset, int length) {
    }

    private PlanSpill(Path file, FileChannel channel, AtomicBoolean closed) {
        this.file = file;
        this.channel = channel;
        this.closed = closed;
    }

    static PlanSpill create(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, PREFIX, ".bin");
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE,
                StandardOpenOption.DELETE_ON_CLOSE);
        AtomicBoolean closed = new AtomicBoolean();
        PlanSpill spill = new PlanSpill(file, channel, closed);
        // La acción no puede tocar el objeto (si no, nunca sería inalcanzable): solo el canal.
        CLEANER.register(spill, () -> close(channel, file, closed));
        return spill;
    }

    Slot append(byte[] data) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        long offset = length;
        while (buffer.hasRemaining()) {
            offset += channel.write(buffer, offset);
        }
        Slot slot = new Slot(length, data.length);
        length = offset;
        return slot;
    }

    /** Acaba la escritura; desde aquí solo se lee. */
    void finish() {
        try {
            channel.force(false);
        } catch (IOException ignored) {
            // Solo es para que el sistema no tenga que reordenar escrituras; la lectura funciona igual.
        }
    }

    PlanPages.Page read(Slot slot) {
        ByteBuffer buffer = ByteBuffer.allocate(slot.length());
        try {
            long position = slot.offset();
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read < 0) {
                    throw new IOException("El archivo temporal del plan está incompleto: " + file);
                }
                position += read;
            }
            return PlanPages.Page.read(new DataInputStream(new ByteArrayInputStream(buffer.array())));
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo leer el plan volcado a disco", exception);
        }
    }

    Path file() {
        return file;
    }

    long length() {
        return length;
    }

    private static void close(FileChannel channel, Path file, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // DELETE_ON_CLOSE ya lo intentó; lo que quede lo borra sweep al arrancar.
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Igual que arriba.
        }
    }

    /**
     * Borra los archivos de planes que dejó un servidor parado de golpe. En Linux un archivo abierto por otro
     * servidor que comparta la carpeta sigue legible para él aunque se borre; en Windows no se puede borrar y se deja.
     */
    static int sweep(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        int removed = 0;
        try (var files = Files.newDirectoryStream(directory, PREFIX + "*.bin")) {
            for (Path file : files) {
                try {
                    if (Files.deleteIfExists(file)) {
                        removed++;
                    }
                } catch (IOException ignored) {
                    // En uso (Windows): es de otro servidor vivo.
                }
            }
        } catch (IOException ignored) {
            return removed;
        }
        return removed;
    }
}
