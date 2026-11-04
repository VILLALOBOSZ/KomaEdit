package com.komainu.moxia.terrain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.terrain.stamp.StampInfo;

import java.util.Locale;

/**
 * Lo que se sabe de un sello propio del servidor, en el {@code <nombre>.json} junto a su PNG en
 * {@code config/komaedit/heightmaps/}. Un PNG dejado a mano, sin JSON, también es propio: sin autor y con la altura
 * natural de serie de los PNG propios.
 *
 * @param author      nombre de quien lo capturó (o lo importó), para enseñarlo
 * @param authorId    su UUID, o vacío; decide quién puede borrarlo
 * @param created     cuándo se guardó, en milisegundos
 * @param sizeX       columnas de oeste a este que se leyeron
 * @param sizeZ       columnas de norte a sur
 * @param above       bloques que sube por encima del nivel base, a tamaño real
 * @param below       bloques que hunde por debajo
 * @param category    la de su miniatura y su paleta automática
 * @param water       si había agua en lo que se leyó
 * @param source      de dónde salió («minecraft:overworld 120 64 -40»), o vacío
 */
public record OwnStamp(String name, String author, String authorId, String description, long created, int sizeX,
                       int sizeZ, double above, double below, StampCategory category, boolean water, String source) {
    public static final int FORMAT = 1;
    public static final int MAX_DESCRIPTION = 160;
    public static final int MAX_AUTHOR = 32;

    public OwnStamp {
        author = clean(author, MAX_AUTHOR);
        authorId = authorId == null ? "" : authorId.strip();
        description = clean(description, MAX_DESCRIPTION);
        source = clean(source, 96);
        category = category == null ? StampCategory.HILLS : category;
        if (!(above >= 0.0) || !(below >= 0.0) || above > 4096 || below > 4096) {
            throw new IllegalArgumentException("Alturas del sello no válidas");
        }
        if (sizeX < 1 || sizeZ < 1 || sizeX > HeightStamp.MAX_SIZE || sizeZ > HeightStamp.MAX_SIZE) {
            throw new IllegalArgumentException("Tamaño del sello no válido");
        }
    }

    /** Un PNG propio sin JSON: su tamaño y la altura natural de serie de los PNG propios. */
    public static OwnStamp plain(String name, HeightStamp stamp) {
        double span = HeightmapBrush.CUSTOM_RATIO * Math.max(stamp.width(), stamp.height()) / 2.0;
        return new OwnStamp(name, "", "", "", 0L, stamp.width(), stamp.height(), stamp.neutral() >= 1.0 ? 0.0 : span,
                stamp.neutral() <= 0.0 ? 0.0 : span, StampCategory.HILLS, false, "");
    }

    /** Lado del cuadrado del sello en columnas (lo capturado se centra en un cuadrado). */
    public int side() {
        return Math.max(sizeX, sizeZ);
    }

    /** Altura natural dividida por el radio: la de verdad, para que salga con las proporciones de lo capturado. */
    public double heightRatio() {
        return Math.max(above, below) / (side() / 2.0);
    }

    /** Lo que usan la galería y el pincel como de cualquier sello. */
    public StampInfo info(double neutral) {
        return new StampInfo(name, category, description, neutral, heightRatio(), category.palette());
    }

    public boolean ownedBy(String id) {
        return !authorId.isEmpty() && authorId.equals(id);
    }

    public OwnStamp withDescription(String text) {
        return new OwnStamp(name, author, authorId, text, created, sizeX, sizeZ, above, below, category, water,
                source);
    }

    public OwnStamp withName(String value) {
        return new OwnStamp(value, author, authorId, description, created, sizeX, sizeZ, above, below, category,
                water, source);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT);
        json.addProperty("author", author);
        json.addProperty("authorId", authorId);
        json.addProperty("description", description);
        json.addProperty("created", created);
        json.addProperty("sizeX", sizeX);
        json.addProperty("sizeZ", sizeZ);
        json.addProperty("above", round(above));
        json.addProperty("below", round(below));
        json.addProperty("category", category.id());
        json.addProperty("water", water);
        json.addProperty("source", source);
        return json;
    }

    /** Lee el JSON de un sello propio; lo que falte o no valga toma un valor razonable. */
    public static OwnStamp fromJson(String name, JsonObject json, HeightStamp stamp) {
        OwnStamp fallback = plain(name, stamp);
        StampCategory category;
        try {
            category = StampCategory.byId(string(json, "category", fallback.category.id()));
        } catch (IllegalArgumentException exception) {
            category = fallback.category;
        }
        return new OwnStamp(name, string(json, "author", ""), string(json, "authorId", ""),
                string(json, "description", ""), number(json, "created", 0.0).longValue(),
                (int) Math.clamp(number(json, "sizeX", fallback.sizeX).longValue(), 1, HeightStamp.MAX_SIZE),
                (int) Math.clamp(number(json, "sizeZ", fallback.sizeZ).longValue(), 1, HeightStamp.MAX_SIZE),
                Math.clamp(number(json, "above", fallback.above).doubleValue(), 0.0, 4096.0),
                Math.clamp(number(json, "below", fallback.below).doubleValue(), 0.0, 4096.0), category,
                json.has("water") && json.get("water").isJsonPrimitive() && json.get("water").getAsBoolean(),
                string(json, "source", ""));
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static Number number(JsonObject json, String key, double fallback) {
        JsonElement element = json.get(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsDouble() : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Sin saltos de línea, sin {@code §} (Minecraft echa a quien lo manda) y cortado a {@code max}. */
    static String clean(String text, int max) {
        if (text == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < text.length() && result.length() < max; index++) {
            char character = text.charAt(index);
            result.append(character < ' ' || character == '§' || character == 127 ? ' ' : character);
        }
        return result.toString().strip().replaceAll(" {2,}", " ");
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s (%d×%d, +%.0f/-%.0f)", name, sizeX, sizeZ, above, below);
    }
}
