package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.terrain.StampLibrary;

/**
 * Miniatura de un sello de relieve en la galería: la de la biblioteca ({@link StampLibrary#thumbnail}), con los
 * colores de su categoría, luz del noroeste y sombra en lo cóncavo, para que se lea la forma de un vistazo.
 */
public final class StampPreview {
    private StampPreview() {
    }

    /** Píxeles ARGB de {@code size}×{@code size}, por filas. */
    public static int[] argb(String name, int size) {
        return StampLibrary.thumbnail(name, size);
    }
}
