package com.komainu.moxia.worldedit;

import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.session.ClipboardHolder;

/**
 * El portapapeles como lo pegaría {@code //paste}: con su {@code //rotate} y {@code //flip} pendientes ya aplicados.
 * Es lo que usan todas las herramientas que leen el portapapeles ({@code place}, {@code scatter}, {@code array}, sus
 * pinceles, {@code bend}, kitbash, el pegado del editor…), igual que {@code paste}, escalar, los planos y la
 * reconstrucción.
 */
public final class PendingClipboard {
    private PendingClipboard() {
    }

    public static Clipboard of(ClipboardHolder holder) {
        Clipboard clipboard = holder.getClipboard();
        Transform transform = holder.getTransform();
        if (transform == null || transform.isIdentity()) {
            return clipboard;
        }
        try {
            return clipboard.transform(transform);
        } catch (WorldEditException exception) {
            throw new IllegalArgumentException("No se pudo girar el portapapeles: " + exception.getMessage(),
                    exception);
        }
    }
}
