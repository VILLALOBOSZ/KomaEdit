package com.komainu.moxia.hotbar;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Barras en JSON. Cada hueco tiene la forma de {@code ItemStack.CODEC} ({@code {"id": …, "count": …, "components":
 * {…}}}, o {@code null} si está vacío), así el servidor lo lee directamente con el códec de Minecraft y el archivo se
 * entiende a simple vista.
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "active": "piedra",
 *   "autosave": true,
 *   "hotbars": [
 *     {"name": "piedra", "icon": "minecraft:stone_bricks",
 *      "slots": [{"id": "minecraft:stone_bricks", "count": 64}, null, …]}
 *   ]
 * }
 * }</pre>
 */
public final class HotbarJson {
    public static final int FORMAT = 1;
    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();

    private HotbarJson() {
    }

    // ---- Huecos -----------------------------------------------------------------------------------------------

    public static JsonElement slot(HotbarItem item) {
        return slot(item, false);
    }

    /**
     * @param compact sin {@code minecraft:} en el id ni la cantidad cuando es 1 (para los códigos de compartir)
     */
    static JsonElement slot(HotbarItem item, boolean compact) {
        if (item.isEmpty()) {
            return JsonNull.INSTANCE;
        }
        JsonObject object = new JsonObject();
        object.addProperty("id", compact ? item.shortId() : item.id());
        if (!compact || item.count() != 1) {
            object.addProperty("count", item.count());
        }
        if (!item.components().isEmpty()) {
            object.add("components", JsonParser.parseString(item.components()));
        }
        return object;
    }

    /** Lee un hueco; sin {@code count} vale 1, como en {@code ItemStack.CODEC}. */
    public static HotbarItem slot(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return HotbarItem.EMPTY;
        }
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Un hueco de barra es un objeto o null");
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("id") || !object.get("id").isJsonPrimitive()) {
            throw new IllegalArgumentException("Falta el objeto de un hueco");
        }
        int count = object.has("count") ? object.get("count").getAsInt() : 1;
        if (count < 1 || count > HotbarItem.MAX_COUNT) {
            throw new IllegalArgumentException("La cantidad de un hueco va de 1 a " + HotbarItem.MAX_COUNT);
        }
        JsonElement components = object.get("components");
        if (components != null && !components.isJsonNull() && !components.isJsonObject()) {
            throw new IllegalArgumentException("Los componentes de un hueco son un objeto JSON");
        }
        return new HotbarItem(object.get("id").getAsString(), count,
                components == null || components.isJsonNull() ? "" : canonical(components));
    }

    // ---- Barras -----------------------------------------------------------------------------------------------

    public static JsonObject hotbar(SavedHotbar hotbar) {
        return hotbar(hotbar, false);
    }

    static JsonObject hotbar(SavedHotbar hotbar, boolean compact) {
        JsonObject object = new JsonObject();
        object.addProperty("name", hotbar.name());
        if (!hotbar.icon().isEmpty() || !compact) {
            object.addProperty("icon", compact ? HotbarItem.shortId(hotbar.icon()) : hotbar.icon());
        }
        JsonArray slots = new JsonArray();
        for (HotbarItem item : hotbar.slots()) {
            slots.add(slot(item, compact));
        }
        object.add("slots", slots);
        return object;
    }

    public static SavedHotbar hotbar(JsonObject object) {
        if (!object.has("name")) {
            throw new IllegalArgumentException("Falta el nombre de una barra");
        }
        String icon = object.has("icon") && !object.get("icon").isJsonNull() ? object.get("icon").getAsString() : "";
        List<HotbarItem> slots = new ArrayList<>();
        JsonElement array = object.get("slots");
        if (array != null && !array.isJsonArray()) {
            throw new IllegalArgumentException("Los huecos de una barra van en una lista");
        }
        if (array != null) {
            for (JsonElement element : array.getAsJsonArray()) {
                slots.add(slot(element));
            }
        }
        return new SavedHotbar(object.get("name").getAsString(), icon, slots);
    }

    // ---- Colección --------------------------------------------------------------------------------------------

    public static JsonObject book(HotbarBook book) {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT);
        root.addProperty("active", book.active());
        root.addProperty("autosave", book.autosave());
        JsonArray hotbars = new JsonArray();
        for (SavedHotbar hotbar : book.hotbars()) {
            hotbars.add(hotbar(hotbar));
        }
        root.add("hotbars", hotbars);
        return root;
    }

    public static HotbarBook book(JsonObject root) {
        int version = root.has("version") ? root.get("version").getAsInt() : FORMAT;
        if (version != FORMAT) {
            throw new IllegalArgumentException("Versión de barras desconocida: " + version);
        }
        List<SavedHotbar> hotbars = new ArrayList<>();
        if (root.has("hotbars")) {
            for (JsonElement element : root.getAsJsonArray("hotbars")) {
                hotbars.add(hotbar(element.getAsJsonObject()));
            }
        }
        String active = root.has("active") ? root.get("active").getAsString() : "";
        boolean autosave = !root.has("autosave") || root.get("autosave").getAsBoolean();
        return new HotbarBook(hotbars, active, autosave);
    }

    // ---- Utilidades -------------------------------------------------------------------------------------------

    /** JSON compacto con las claves de cada objeto ordenadas: el mismo valor da siempre el mismo texto. */
    public static String canonical(JsonElement element) {
        return COMPACT.toJson(sorted(element));
    }

    private static JsonElement sorted(JsonElement element) {
        if (element.isJsonObject()) {
            Map<String, JsonElement> keys = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                keys.put(entry.getKey(), sorted(entry.getValue()));
            }
            JsonObject result = new JsonObject();
            keys.forEach(result::add);
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(sorted(child));
            }
            return result;
        }
        return element;
    }

    /** Bytes de la barra en JSON compacto: lo que cuenta para los límites. */
    public static int size(SavedHotbar hotbar) {
        return COMPACT.toJson(hotbar(hotbar)).getBytes(StandardCharsets.UTF_8).length;
    }

    static String compact(JsonElement element) {
        return COMPACT.toJson(element);
    }
}
