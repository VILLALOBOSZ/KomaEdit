package com.komainu.moxia.rebuild;

import com.komainu.moxia.persistence.AtomicStorage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Los archivos de una reconstrucción en {@code <mundo>/komaedit/rebuilds/}, para que una caída no pierda ni duplique
 * nada:
 *
 * <ul>
 *     <li>{@code <id>.bin}: lo que tiene que quedar y lo que había ({@link RebuildRecovery}), escrito antes de vaciar
 *     nada;</li>
 *     <li>{@code <id>.taken}: cada bloque con datos (un cofre, un cartel) tal como estaba al quitarlo, apuntado
 *     <b>antes</b> de quitarlo del mundo. El {@code .bin} tiene lo leído al empezar: si entre medias alguien sacó
 *     objetos de un cofre (o una tolva lo llenó), tras una caída volvería con lo de entonces y los objetos se
 *     duplicarían (o se perderían);</li>
 *     <li>{@code <id>.restore}: se estaba cancelando. Se escribe en el momento, antes de deshacer nada: si se cae
 *     deshaciendo, al arrancar se termina de deshacer en vez de levantar la obra.</li>
 * </ul>
 *
 * Si el servidor se para mientras aún se ordena la obra (no se ha tocado el mundo), se abandona: lo que el hilo de
 * la reconstrucción escriba después se borra en vez de quedarse para levantarse en el próximo arranque. Java puro.
 */
public final class RebuildFiles {
    public static final String SNAPSHOT = ".bin";
    public static final String TAKEN = ".taken";
    public static final String RESTORE = ".restore";
    private static final int TAKEN_MAGIC = 0x4B455254;
    private static final int TAKEN_VERSION = 1;
    /** Un bloque con datos apuntado no pasa de esto (lo de un cofre lleno de libros cabe de sobra). */
    private static final int MAX_TAKEN_TEXT = 8 << 20;

    private final Path snapshot;
    private volatile boolean abandoned;
    private DataOutputStream taken;

    /** @param snapshot el {@code <id>.bin}; los demás van a su lado */
    public RebuildFiles(Path snapshot) {
        if (!snapshot.getFileName().toString().endsWith(SNAPSHOT)) {
            throw new IllegalArgumentException("El archivo de una reconstrucción acaba en " + SNAPSHOT);
        }
        this.snapshot = snapshot;
    }

    public Path snapshot() {
        return snapshot;
    }

    public static Path taken(Path snapshot) {
        return sibling(snapshot, TAKEN);
    }

    public static Path restore(Path snapshot) {
        return sibling(snapshot, RESTORE);
    }

    private static Path sibling(Path snapshot, String suffix) {
        String name = snapshot.getFileName().toString();
        return snapshot.resolveSibling(name.substring(0, name.length() - SNAPSHOT.length()) + suffix);
    }

    /**
     * Guarda la foto de la obra. Si entretanto se abandonó (el servidor se paraba mientras se ordenaba), la borra:
     * nada se tocó y la obra no debe levantarse al volver a arrancar.
     *
     * @return {@code false} si quedó borrada por eso
     */
    public boolean write(RebuildRecovery.Snapshot value) throws IOException {
        Files.createDirectories(snapshot.getParent());
        Path temporary = AtomicStorage.temporary(snapshot);
        try (OutputStream output = Files.newOutputStream(temporary)) {
            RebuildRecovery.write(value, output);
        }
        AtomicStorage.replace(temporary, snapshot);
        // Después de escribir: si se abandonó antes, aquí se ve; si se abandona después, quien abandona la borra.
        if (abandoned) {
            deleteAll(snapshot);
            return false;
        }
        return true;
    }

    /**
     * Apunta un bloque con datos tal como está, justo antes de quitarlo. Vuelve con él ya en el disco (no solo en un
     * búfer), así que el mundo nunca se guarda sin el bloque y sin lo apuntado a la vez.
     *
     * @param palette su entrada en la paleta de la obra
     * @param text    el bloque con sus datos, como en la paleta del {@code .bin}
     */
    public synchronized void took(int palette, String text) throws IOException {
        if (palette <= RebuildModel.AIR) {
            throw new IllegalArgumentException("El aire no tiene datos");
        }
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_TAKEN_TEXT) {
            throw new IOException("El bloque tiene demasiados datos para apuntarlo");
        }
        if (taken == null) {
            Path file = taken(snapshot);
            boolean fresh = !Files.isRegularFile(file) || Files.size(file) == 0;
            taken = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)));
            if (fresh) {
                taken.writeInt(TAKEN_MAGIC);
                taken.writeInt(TAKEN_VERSION);
            }
        }
        taken.writeInt(palette);
        taken.writeInt(bytes.length);
        taken.write(bytes);
        taken.flush();
    }

    /** Se está cancelando: a disco ya, antes de deshacer nada. */
    public void markRestore() throws IOException {
        Files.createDirectories(snapshot.getParent());
        Files.writeString(restore(snapshot), "restore", StandardCharsets.UTF_8);
    }

    /** No se tocó el mundo y ya no se va a tocar: se borra lo escrito y lo que se escriba después. */
    public void abandon() {
        abandoned = true;
        delete();
    }

    public boolean abandoned() {
        return abandoned;
    }

    /** Deja de apuntar (cierra el {@code .taken}); los archivos se quedan. */
    public synchronized void close() {
        if (taken == null) {
            return;
        }
        try {
            taken.close();
        } catch (IOException ignored) {
            // Lo apuntado ya estaba en el disco: cada bloque se vuelca al apuntarlo.
        }
        taken = null;
    }

    /** Cierra y borra todos los archivos de la reconstrucción. */
    public synchronized void delete() {
        close();
        deleteAll(snapshot);
    }

    /** Borra el {@code .bin}, su copia y su temporal, el {@code .taken} y la marca de deshacer. */
    public static void deleteAll(Path snapshot) {
        for (Path file : List.of(snapshot, AtomicStorage.backup(snapshot), AtomicStorage.temporary(snapshot),
                taken(snapshot), restore(snapshot))) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // Lo que no se pueda borrar ahora se vuelve a intentar al acabar la recuperación.
            }
        }
    }

    /**
     * Lo de una reconstrucción interrumpida.
     *
     * @param snapshot la foto, con los bloques con datos ya como se quitaron
     * @param restore  si se estaba cancelando
     * @param taken    cuántos bloques con datos se apuntaron al quitarlos
     */
    public record Loaded(RebuildRecovery.Snapshot snapshot, boolean restore, int taken) {
    }

    /** Lee la foto y pone en su paleta los bloques con datos tal como estaban al quitarlos. */
    public static Loaded read(Path snapshot) throws IOException {
        RebuildRecovery.Snapshot read;
        try (InputStream input = Files.newInputStream(snapshot)) {
            read = RebuildRecovery.read(input);
        }
        List<String> palette = new ArrayList<>(read.palette());
        int count = 0;
        Path file = taken(snapshot);
        if (Files.isRegularFile(file)) {
            count = readTaken(file, palette);
        }
        if (count > 0) {
            read = new RebuildRecovery.Snapshot(read.dimension(), read.owner(), read.description(), palette,
                    read.positions(), read.target(), read.sameBefore() ? read.target() : read.before());
        }
        return new Loaded(read, Files.exists(restore(snapshot)), count);
    }

    /**
     * Lo último apuntado de cada entrada gana. Un apunte cortado (la caída llegó mientras se escribía) se ignora: el
     * bloque aún no se había quitado. Lo que no cuadra (otra cosa, una entrada fuera de la paleta) para la lectura:
     * lo de antes vale y lo demás queda como se leyó al empezar.
     */
    private static int readTaken(Path file, List<String> palette) throws IOException {
        int count = 0;
        try (DataInputStream data = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (data.readInt() != TAKEN_MAGIC || data.readInt() != TAKEN_VERSION) {
                return 0;
            }
            while (true) {
                int entry = data.readInt();
                int length = data.readInt();
                if (entry <= RebuildModel.AIR || entry >= palette.size() || length < 0 || length > MAX_TAKEN_TEXT) {
                    return count;
                }
                byte[] bytes = data.readNBytes(length);
                if (bytes.length < length) {
                    return count;
                }
                palette.set(entry, new String(bytes, StandardCharsets.UTF_8));
                count++;
            }
        } catch (EOFException end) {
            return count;
        }
    }
}
