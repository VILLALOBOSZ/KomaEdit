package com.komainu.moxia.gizmo;

import java.util.function.Consumer;

/**
 * Lo que se dibuja de un fantasma mientras se rehace. La vista previa en vivo manda un fantasma nuevo cada vez que
 * cambia un ajuste, y su malla tarda unos fotogramas en llegar y armarse; hasta entonces sigue a la vista la de antes,
 * quieta donde estaba, para que no parpadee. Si la nueva no llega en {@value #LIMIT_MILLIS} ms, la vieja se quita.
 *
 * <p>Sin Minecraft: {@code M} es la malla y {@code dispose} la libera.</p>
 */
public final class MeshHandover<M> {
    public static final long LIMIT_MILLIS = 3_000L;

    private final Consumer<M> dispose;
    private M current;
    private M previous;
    private long retiredAt;

    public MeshHandover(Consumer<M> dispose) {
        this.dispose = dispose;
    }

    /** El fantasma se va a rehacer: la malla de ahora queda a la vista hasta que llegue la nueva. */
    public void retire(long now) {
        if (current == null) {
            return;
        }
        release(previous);
        previous = current;
        current = null;
        retiredAt = now;
    }

    /** La malla nueva está lista: sustituye a la de ahora y a la que esperaba. */
    public void install(M mesh) {
        release(previous);
        previous = null;
        if (current != mesh) {
            release(current);
        }
        current = mesh;
    }

    /** La malla del fantasma de ahora, o {@code null} si aún no está. */
    public M current() {
        return current;
    }

    /**
     * La de antes, mientras la nueva no está (y no ha pasado el límite), o {@code null}. Al pasar el límite se libera.
     */
    public M previous(long now) {
        if (previous != null && (current != null || now - retiredAt > LIMIT_MILLIS)) {
            release(previous);
            previous = null;
        }
        return previous;
    }

    /** Fuera todo (el fantasma se colocó, se canceló o se cerró sin rehacerse). */
    public void clear() {
        release(previous);
        release(current);
        previous = null;
        current = null;
    }

    private void release(M mesh) {
        if (mesh != null) {
            dispose.accept(mesh);
        }
    }
}
