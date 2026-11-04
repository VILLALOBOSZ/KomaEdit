package com.komainu.moxia.share;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.komainu.moxia.terrain.HeightStamp;
import com.komainu.moxia.terrain.OwnStamp;
import com.komainu.moxia.terrain.OwnStamps;
import com.komainu.moxia.terrain.StampCapture;
import com.komainu.moxia.terrain.StampImages;
import com.komainu.moxia.terrain.StampLibrary;
import com.komainu.moxia.terrain.StampService;
import com.komainu.moxia.terrain.stamp.StampCategory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Sellos propios del servidor (los capturados del mundo y los PNG puestos a mano). Datos:
 * {@code {"author", "description", "category", "sizeX", "sizeZ", "above", "below", "water"}} y el PNG de 16 bits como
 * adjunto. Al importarlo el PNG se lee entero con sus límites (tamaño, píxeles) y se vuelve a escribir: lo que se guarda
 * es solo el relieve y su gris neutro, nunca los trozos del PNG que llegó.
 */
public final class StampKind implements ShareKind {
    /** Un PNG de sello más grande que esto no se acepta ni se lee. */
    public static final int MAX_PNG_BYTES = 4 * 1024 * 1024;

    private final Supplier<OwnStamps> stamps;

    public StampKind(Supplier<OwnStamps> stamps) {
        this.stamps = stamps;
    }

    @Override
    public String id() {
        return "stamp";
    }

    @Override
    public List<String> aliases() {
        return List.of("sello", "sellos", "stamps", "heightmap");
    }

    @Override
    public String singular() {
        return "sello";
    }

    @Override
    public String plural() {
        return "sellos";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String permission() {
        return StampService.CAPTURE;
    }

    @Override
    public List<String> names(ShareTarget target) {
        return stamps.get().list().stream().map(OwnStamps.Entry::name).toList();
    }

    @Override
    public SharePack.Item export(ShareTarget target, String name) {
        if (!target.can(StampService.USE) && !target.can(StampService.CAPTURE)) {
            throw new IllegalArgumentException("Te falta el permiso " + StampService.USE);
        }
        String key = normalizeName(name);
        OwnStamps.Entry entry = stamps.get().find(key);
        if (entry == null) {
            throw new IllegalArgumentException(StampLibrary.has(key)
                    ? "«" + key + "» es un sello de serie: ya lo tiene cualquier servidor con KomaEdit"
                    : "No hay ningún sello propio «" + key + "»");
        }
        OwnStamp meta = entry.meta();
        JsonObject data = new JsonObject();
        data.addProperty("author", meta.author());
        data.addProperty("description", meta.description());
        data.addProperty("category", meta.category().id());
        data.addProperty("sizeX", meta.sizeX());
        data.addProperty("sizeZ", meta.sizeZ());
        data.addProperty("above", meta.above());
        data.addProperty("below", meta.below());
        data.addProperty("water", meta.water());
        return new SharePack.Item(id(), key, version(), data, stamps.get().imageBytes(entry));
    }

    @Override
    public SharePack.Item check(SharePack.Item item, ShareCheck check) {
        if (item.version() > version()) {
            throw new IllegalArgumentException("Es de una versión más nueva de KomaEdit");
        }
        if (!item.hasBlob()) {
            throw new IllegalArgumentException("Le falta el relieve (el PNG)");
        }
        byte[] blob = item.blob();
        if (blob.length > MAX_PNG_BYTES) {
            throw new IllegalArgumentException("El PNG del sello pasa de " + MAX_PNG_BYTES / 1024 / 1024 + " MB");
        }
        String name = normalizeName(item.name());
        HeightStamp stamp = sanitized(name, blob);
        JsonObject data = item.data() == null ? new JsonObject() : item.data();
        OwnStamp meta = meta(name, data, stamp, "");
        JsonObject result = new JsonObject();
        result.addProperty("author", ShareText.display(meta.author(), OwnStamp.MAX_AUTHOR));
        result.addProperty("description", ShareText.display(meta.description(), OwnStamp.MAX_DESCRIPTION));
        result.addProperty("category", meta.category().id());
        result.addProperty("sizeX", meta.sizeX());
        result.addProperty("sizeZ", meta.sizeZ());
        result.addProperty("above", meta.above());
        result.addProperty("below", meta.below());
        result.addProperty("water", meta.water());
        return new SharePack.Item(id(), name, version(), result, png(stamp));
    }

    /** Lee el PNG con los límites de los sellos y lo deja en un sello limpio (solo relieve y gris neutro). */
    static HeightStamp sanitized(String name, byte[] blob) {
        HeightStamp read;
        try {
            read = StampImages.read(name, new ByteArrayInputStream(blob));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("El relieve no es un PNG que se pueda leer: " + exception.getMessage());
        }
        if (read.width() > StampCapture.MAX_SIDE * 2 || read.height() > StampCapture.MAX_SIDE * 2) {
            throw new IllegalArgumentException("El sello mide " + read.width() + " × " + read.height()
                    + " píxeles; como mucho " + StampCapture.MAX_SIDE * 2);
        }
        return read;
    }

    private static byte[] png(HeightStamp stamp) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            StampImages.write(stamp, output, 16);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo volver a escribir el sello: " + exception.getMessage());
        }
    }

    /** Los datos del paquete, ya comprobados, con lo que no valga cambiado por lo razonable. */
    private static OwnStamp meta(String name, JsonObject data, HeightStamp stamp, String importer) {
        OwnStamp fallback = OwnStamp.plain(name, stamp);
        StampCategory category;
        try {
            category = StampCategory.byId(text(data, "category", fallback.category().id()));
        } catch (IllegalArgumentException exception) {
            category = fallback.category();
        }
        int sizeX = (int) Math.clamp(number(data, "sizeX", stamp.width()), 1, stamp.width());
        int sizeZ = (int) Math.clamp(number(data, "sizeZ", stamp.height()), 1, stamp.height());
        double above = Math.clamp(number(data, "above", fallback.above()), 0.0, 1024.0);
        double below = Math.clamp(number(data, "below", fallback.below()), 0.0, 1024.0);
        boolean water = data.has("water") && data.get("water").isJsonPrimitive()
                && data.get("water").getAsJsonPrimitive().isBoolean() && data.get("water").getAsBoolean();
        return new OwnStamp(name, text(data, "author", ""), importer, text(data, "description", ""), 0L, sizeX, sizeZ,
                above, below, category, water, "");
    }

    @Override
    public String normalizeName(String name) {
        String key = name == null ? "" : name.strip().toLowerCase(Locale.ROOT).replace("ñ", "n");
        if (!key.matches("[a-z0-9_\\-]{1,64}")) {
            throw new IllegalArgumentException("Nombre de sello no válido: " + name);
        }
        if (StampLibrary.has(key)) {
            // Un sello propio no tapa a uno de serie: el que llega con su nombre se guarda con otro.
            return key + "_propio";
        }
        return key;
    }

    @Override
    public boolean exists(ShareTarget target, String name) {
        return stamps.get().find(name) != null;
    }

    @Override
    public String save(ShareTarget target, SharePack.Item item, String name, boolean replace) {
        HeightStamp stamp = sanitized(name, item.blob());
        OwnStamp received = meta(name, item.data(), stamp, target.id().toString());
        String author = received.author().isEmpty() ? target.name() : received.author();
        OwnStamp meta = new OwnStamp(name, author, target.id().toString(), received.description(),
                System.currentTimeMillis(), received.sizeX(), received.sizeZ(), received.above(), received.below(),
                received.category(), received.water(), "importado por " + target.name());
        stamps.get().save(meta, stamp, replace, target.id().toString(), target.can(StampService.MANAGE),
                OwnStamps.Limits.DEFAULT, target.can(StampService.UNLIMITED));
        return "guardado";
    }

    private static String text(JsonObject data, String key, String fallback) {
        JsonElement element = data.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static double number(JsonObject data, String key, double fallback) {
        JsonElement element = data.get(key);
        try {
            double value = element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback;
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
