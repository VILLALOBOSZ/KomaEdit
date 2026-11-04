package com.komainu.moxia.blueprint;

import java.util.Optional;

/**
 * Lo que llega al cliente para su carpeta {@code schematics}: solo los envíos que el jugador pidió
 * ({@link DownloadPermits}), uno a la vez y con tope de tamaño ({@link FileChunks.Assembler}). Un trozo de un envío
 * que nadie pidió se tira sin juntarlo, sin escribir nada y sin avisar. Lógica pura, con el reloj de fuera.
 */
public final class DownloadInbox {
    private final DownloadPermits permits = new DownloadPermits();
    private final FileChunks.Assembler assembler = new FileChunks.Assembler(FileChunks.MAX_BYTES,
            new FileChunks.Pool(1, FileChunks.MAX_BYTES));

    /** El jugador mandó esta orden (escrita o desde el Studio): si exporta, deja pasar un archivo. */
    public void expect(String command, long now) {
        permits.expect(command, now);
    }

    /**
     * @return el archivo, si llegó entero (falta comprobar su huella, {@link FileChunks.Complete#verify()}); vacío si
     * falta algún trozo o no se pidió
     * @throws IllegalArgumentException si un envío pedido no cuadra o es demasiado grande (para decírselo al jugador)
     */
    public synchronized Optional<FileChunks.Complete> receive(FileChunks.Chunk chunk, long now) {
        if (!permits.admit(chunk.transfer(), now)) {
            return Optional.empty();
        }
        int previous = assembler.transfer();
        if (previous >= 0 && previous != chunk.transfer()) {
            // Empieza otro envío pedido: el que estaba a medias no va a acabar.
            permits.finished(previous);
        }
        try {
            Optional<FileChunks.Complete> complete = assembler.accept(chunk, now);
            complete.ifPresent(ignored -> permits.finished(chunk.transfer()));
            return complete;
        } catch (IllegalArgumentException exception) {
            permits.finished(chunk.transfer());
            throw exception;
        }
    }

    /** Peticiones que aún esperan archivo. */
    public int waiting(long now) {
        return permits.waiting(now);
    }

    public synchronized void clear() {
        permits.clear();
        assembler.clear();
    }
}
