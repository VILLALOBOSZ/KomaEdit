package com.komainu.moxia.client.reference;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.image.RgbaImage;
import com.komainu.moxia.reference.ReferenceFiles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Las imágenes de referencia en la tarjeta gráfica: cada archivo se lee y se reduce en segundo plano
 * ({@link ReferenceFiles#decode}) y se sube en el hilo de dibujo. Hay dos tamaños: la imagen para dibujar (hasta
 * 2 048 píxeles de lado) y la miniatura de la lista (96). Un archivo que cambia en disco se vuelve a leer al mirar la
 * carpeta ({@link #refresh}).
 */
public final class ReferenceTextures {
    private static final Map<String, Slot> FULL = new HashMap<>();
    private static final Map<String, Slot> THUMBNAILS = new HashMap<>();
    private static int counter;

    private ReferenceTextures() {
    }

    /** Una imagen subida: su identificador de textura y sus medidas (las del archivo, no las de la textura). */
    public record Texture(Identifier id, NativeImageBackedTexture texture, int width, int height) {
        /** Nítida (cada píxel se ve) o suave al agrandarla. */
        public void filter(boolean smooth) {
            texture.setFilter(smooth, false);
        }
    }

    private static final class Slot {
        final long modified;
        Texture texture;
        String error;

        Slot(long modified) {
            this.modified = modified;
        }
    }

    /** La imagen para dibujar; si aún no está, la pide y devuelve vacío hasta que llegue. */
    public static Optional<Texture> full(String file) {
        return get(FULL, file, ReferenceFiles.TEXTURE_SIDE);
    }

    public static Optional<Texture> thumbnail(String file) {
        return get(THUMBNAILS, file, ReferenceFiles.THUMBNAIL_SIDE);
    }

    /** Por qué no se pudo leer el archivo (falta, no es una imagen…), o {@code null}. */
    public static String error(String file) {
        Slot slot = FULL.get(file);
        if (slot == null || slot.error == null) {
            slot = THUMBNAILS.get(file);
        }
        return slot == null ? null : slot.error;
    }

    /** Si el archivo ya se intentó leer y falló o llegó. */
    public static boolean settled(String file) {
        Slot slot = FULL.get(file);
        return slot != null && (slot.texture != null || slot.error != null);
    }

    private static Optional<Texture> get(Map<String, Slot> cache, String file, int side) {
        Slot slot = cache.get(file);
        if (slot == null) {
            slot = new Slot(modified(file));
            cache.put(file, slot);
            load(cache, file, side, slot);
        }
        return Optional.ofNullable(slot.texture);
    }

    private static void load(Map<String, Slot> cache, String file, int side, Slot slot) {
        Path folder = ReferenceClient.folder();
        CompletableFuture.supplyAsync(() -> ReferenceFiles.decode(ReferenceFiles.read(folder, file), side),
                        Util.getMainWorkerExecutor())
                .whenComplete((decoded, failure) -> MinecraftClient.getInstance().execute(() -> {
                    if (cache.get(file) != slot) {
                        return;
                    }
                    if (failure != null) {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        slot.error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
                        return;
                    }
                    try {
                        slot.texture = upload(decoded);
                    } catch (RuntimeException exception) {
                        KomaEditMod.LOGGER.warn("Could not upload the reference image {}", file, exception);
                        slot.error = exception.getMessage();
                    }
                }));
    }

    private static Texture upload(ReferenceFiles.Decoded decoded) {
        RgbaImage image = decoded.image();
        NativeImage pixels = new NativeImage(NativeImage.Format.RGBA, image.width(), image.height(), false);
        for (int y = 0; y < image.height(); y++) {
            for (int x = 0; x < image.width(); x++) {
                int argb = image.get(x, y);
                // NativeImage guarda ABGR.
                pixels.setColor(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF);
            }
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(pixels);
        Identifier id = Identifier.of(KomaEditMod.MOD_ID, String.format(Locale.ROOT, "reference/%d", counter++));
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        return new Texture(id, texture, decoded.originalWidth(), decoded.originalHeight());
    }

    /** Vuelve a leer lo que cambió en disco desde que se subió (o que no se pudo leer). */
    public static void refresh() {
        refresh(FULL);
        refresh(THUMBNAILS);
    }

    private static void refresh(Map<String, Slot> cache) {
        Iterator<Map.Entry<String, Slot>> entries = cache.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Slot> entry = entries.next();
            Slot slot = entry.getValue();
            boolean pending = slot.texture == null && slot.error == null;
            if (!pending && (slot.error != null || modified(entry.getKey()) != slot.modified)) {
                release(slot);
                entries.remove();
            }
        }
    }

    /** Suelta todo (al salir del mundo). */
    public static void clear() {
        FULL.values().forEach(ReferenceTextures::release);
        THUMBNAILS.values().forEach(ReferenceTextures::release);
        FULL.clear();
        THUMBNAILS.clear();
    }

    private static void release(Slot slot) {
        if (slot.texture != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(slot.texture.id());
            slot.texture = null;
        }
    }

    private static long modified(String file) {
        try {
            return Files.getLastModifiedTime(ReferenceFiles.resolve(ReferenceClient.folder(), file)).toMillis();
        } catch (IOException | RuntimeException exception) {
            return -1L;
        }
    }
}
