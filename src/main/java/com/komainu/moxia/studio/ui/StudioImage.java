package com.komainu.moxia.studio.ui;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Una imagen que el Studio pinta (miniaturas de especies, tejados, sellos, planos). La identifica su {@code key}: el
 * cliente la sube a una textura una sola vez por clave y las pruebas la pintan píxel a píxel. Los píxeles se
 * calculan solo cuando hacen falta.
 *
 * <p>{@code handle} es para el lienzo que ya tiene la imagen en su sitio (las miniaturas de planos llegan del
 * servidor como texturas del cliente): el código común nunca lo mira.</p>
 */
public final class StudioImage {
    private final String key;
    private final int width;
    private final int height;
    private final Supplier<int[]> pixels;
    private final Object handle;
    private int[] cached;

    private StudioImage(String key, int width, int height, Supplier<int[]> pixels, Object handle) {
        this.key = Objects.requireNonNull(key);
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.handle = handle;
    }

    /** Imagen calculada: {@code pixels} da {@code width × height} colores ARGB, fila a fila. */
    public static StudioImage of(String key, int width, int height, Supplier<int[]> pixels) {
        return new StudioImage(key, width, height, Objects.requireNonNull(pixels), null);
    }

    /** Imagen que el lienzo ya tiene (una textura del cliente), con píxeles opcionales para quien no la tenga. */
    public static StudioImage external(String key, int width, int height, Object handle, Supplier<int[]> pixels) {
        return new StudioImage(key, width, height, pixels, handle);
    }

    public String key() {
        return key;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public Object handle() {
        return handle;
    }

    /** Los píxeles ARGB, calculados la primera vez; vacío si la imagen no los tiene. */
    public synchronized int[] pixels() {
        if (cached == null) {
            int[] computed = pixels == null ? null : pixels.get();
            cached = computed == null || computed.length != width * height ? new int[0] : computed;
        }
        return cached;
    }
}
