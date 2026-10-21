package com.komainu.moxia.studio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.studio.tools.ToolMigration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Favoritas y recientes del Studio, por jugador y en su propio equipo (no viajan con las recetas).
 * Se guardan por id de figura; un id que ya no existe se conserva por si vuelve en otra versión.
 * Las herramientas tienen sus propias favoritas y recientes (por id de tarjeta), recuerdan sus últimos ajustes y
 * por dónde se iba, y la vista previa (ninguna, al construir o en vivo). También se recuerdan la última etapa, los
 * bloques elegidos hace poco, qué secciones plegables se dejaron abiertas y el tema de colores. Los sellos de relieve
 * tienen sus propias favoritas. Y si ya salió la bienvenida guiada (su versión), para que salga una sola vez.
 */
public final class StudioPreferences {
    public static final int MAX_RECENTS = 6;
    public static final int MAX_FAVORITES = 64;
    public static final int MAX_BLOCK_RECENTS = 9;
    private static final int MAX_STORED = 128;
    /**
     * 3: la vista previa en vivo pasa a ser la de serie. Hasta la 2 lo de serie era «sin vista previa» y se guardaba
     * igual que si se hubiera elegido, así que un archivo anterior sin vista previa se lee en vivo.
     * 4: herramientas unidas («Pintar», «Retocar»): sus ajustes, favoritas y recientes pasan a la tarjeta nueva
     * ({@link ToolMigration}).
     */
    private static final int FORMAT = ToolMigration.FORMAT;

    /** Qué hace la vista previa de las herramientas. */
    public enum PreviewMode {
        /** Construir toca el mundo directamente. */
        OFF,
        /** Construir enseña la vista previa y espera a Confirmar. */
        BUILD,
        /** Además, al cambiar un ajuste se rehace la vista previa sola (con retardo y solo si es pequeña). */
        LIVE;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public PreviewMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        static PreviewMode byId(String id) {
            for (PreviewMode mode : values()) {
                if (mode.id().equals(id)) {
                    return mode;
                }
            }
            return OFF;
        }
    }

    private final Set<String> favorites = new LinkedHashSet<>();
    private final List<String> recents = new ArrayList<>();
    private final Set<String> toolFavorites = new LinkedHashSet<>();
    private final Set<String> stampFavorites = new LinkedHashSet<>();
    private final List<String> toolRecents = new ArrayList<>();
    private final Map<String, Map<String, Object>> toolValues = new LinkedHashMap<>();
    private final Map<String, Integer> scroll = new LinkedHashMap<>();
    private final List<String> blockRecents = new ArrayList<>();
    private final Set<String> expanded = new LinkedHashSet<>();
    private PreviewMode previewMode = PreviewMode.LIVE;
    private String stage = "";
    private String tool = "";
    private double panelShare;
    private String theme = "";
    private int welcome;

    public boolean isFavorite(String id) {
        return favorites.contains(id);
    }

    /** Marca o desmarca; devuelve si queda marcada. */
    public boolean toggleFavorite(String id) {
        if (favorites.remove(id)) {
            return false;
        }
        if (favorites.size() >= MAX_FAVORITES) {
            return false;
        }
        favorites.add(id);
        return true;
    }

    public List<String> favorites() {
        return List.copyOf(favorites);
    }

    /** La más reciente primero, sin repetidas. */
    public void pushRecent(String id) {
        recents.remove(id);
        recents.addFirst(id);
        while (recents.size() > MAX_RECENTS) {
            recents.removeLast();
        }
    }

    public List<String> recents() {
        return List.copyOf(recents);
    }

    public boolean isToolFavorite(String id) {
        return toolFavorites.contains(id);
    }

    /** Marca o desmarca una herramienta; devuelve si queda marcada. */
    public boolean toggleToolFavorite(String id) {
        if (toolFavorites.remove(id)) {
            return false;
        }
        if (toolFavorites.size() >= MAX_FAVORITES) {
            return false;
        }
        toolFavorites.add(id);
        return true;
    }

    public List<String> toolFavorites() {
        return List.copyOf(toolFavorites);
    }

    public boolean isStampFavorite(String name) {
        return stampFavorites.contains(name);
    }

    /** Marca o desmarca un sello de relieve como favorito; devuelve si queda marcado. */
    public boolean toggleStampFavorite(String name) {
        if (stampFavorites.remove(name)) {
            return false;
        }
        if (stampFavorites.size() >= MAX_FAVORITES) {
            return false;
        }
        stampFavorites.add(name);
        return true;
    }

    /** Los sellos favoritos, en el orden en que se marcaron. */
    public List<String> stampFavorites() {
        return List.copyOf(stampFavorites);
    }

    public void pushToolRecent(String id) {
        toolRecents.remove(id);
        toolRecents.addFirst(id);
        while (toolRecents.size() > MAX_RECENTS) {
            toolRecents.removeLast();
        }
    }

    public List<String> toolRecents() {
        return List.copyOf(toolRecents);
    }

    /** Si Construir de las herramientas pide la vista previa ({@code -v}) antes de tocar el mundo. */
    public boolean toolPreview() {
        return previewMode != PreviewMode.OFF;
    }

    public void setToolPreview(boolean value) {
        previewMode = !value ? PreviewMode.OFF : previewMode == PreviewMode.LIVE ? PreviewMode.LIVE
                : PreviewMode.BUILD;
    }

    public PreviewMode previewMode() {
        return previewMode;
    }

    public void setPreviewMode(PreviewMode mode) {
        previewMode = mode == null ? PreviewMode.OFF : mode;
    }

    /** Los últimos ajustes de una herramienta que no son los de serie (vacío si no hay). */
    public Map<String, Object> toolValues(String id) {
        Map<String, Object> stored = toolValues.get(id);
        return stored == null ? Map.of() : Map.copyOf(stored);
    }

    public void setToolValues(String id, Map<String, Object> values) {
        toolValues.remove(id);
        if (!values.isEmpty()) {
            toolValues.put(id, new LinkedHashMap<>(values));
        }
        while (toolValues.size() > MAX_STORED) {
            toolValues.remove(toolValues.keySet().iterator().next());
        }
    }

    /** Por dónde se iba en una lista ({@code tool:tree}, {@code stage:figure}…); 0 si no se sabe. */
    public int scroll(String key) {
        return scroll.getOrDefault(key, 0);
    }

    public void setScroll(String key, int value) {
        scroll.remove(key);
        if (value > 0) {
            scroll.put(key, value);
        }
        while (scroll.size() > MAX_STORED) {
            scroll.remove(scroll.keySet().iterator().next());
        }
    }

    /** Bloques elegidos hace poco en la biblioteca de bloques, el último primero. */
    public List<String> blockRecents() {
        return List.copyOf(blockRecents);
    }

    public void pushBlockRecent(String id) {
        blockRecents.remove(id);
        blockRecents.addFirst(id);
        while (blockRecents.size() > MAX_BLOCK_RECENTS) {
            blockRecents.removeLast();
        }
    }

    /** Si una sección plegable se dejó abierta. */
    public boolean expanded(String key) {
        return expanded.contains(key);
    }

    public void setExpanded(String key, boolean open) {
        if (!open) {
            expanded.remove(key);
        } else if (expanded.size() < MAX_STORED) {
            expanded.add(key);
        }
    }

    /** La última etapa abierta (su id), o vacío. */
    public String stage() {
        return stage;
    }

    public void setStage(String value) {
        stage = value == null ? "" : value;
    }

    /** La última herramienta elegida (su id), o vacío. */
    public String tool() {
        return tool;
    }

    public void setTool(String value) {
        tool = value == null ? "" : value;
    }

    /**
     * La parte de la pantalla que el jugador dejó al panel al arrastrar su borde (de 0 a 1), o 0 si no lo ha tocado.
     * Se guarda como proporción para que valga igual con cualquier escala de GUI.
     */
    public double panelShare() {
        return panelShare;
    }

    public void setPanelShare(double value) {
        panelShare = Double.isFinite(value) ? Math.clamp(value, 0.0, 1.0) : 0.0;
    }

    /** El tema de colores elegido (su id), o vacío para el de serie. */
    public String theme() {
        return theme;
    }

    public void setTheme(String value) {
        theme = value == null ? "" : value.strip();
    }

    /** La versión de la bienvenida guiada que ya le salió a este jugador (0: nunca). */
    public int welcome() {
        return welcome;
    }

    public void setWelcome(int version) {
        welcome = Math.max(0, version);
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.add("favorites", array(favorites));
        root.add("recents", array(recents));
        root.add("toolFavorites", array(toolFavorites));
        root.add("toolRecents", array(toolRecents));
        root.add("stampFavorites", array(stampFavorites));
        // Las versiones anteriores solo leen este interruptor.
        root.addProperty("toolPreview", previewMode != PreviewMode.OFF);
        root.addProperty("preview", previewMode.id());
        root.addProperty("stage", stage);
        root.addProperty("tool", tool);
        if (panelShare > 0) {
            root.addProperty("panelShare", Math.round(panelShare * 1000) / 1000.0);
        }
        if (!theme.isEmpty()) {
            root.addProperty("theme", theme);
        }
        if (welcome > 0) {
            root.addProperty("welcome", welcome);
        }
        JsonObject valuesObject = new JsonObject();
        toolValues.forEach((id, values) -> {
            JsonObject card = new JsonObject();
            values.forEach((param, value) -> {
                switch (value) {
                    case Number number -> card.addProperty(param, number);
                    case Boolean flag -> card.addProperty(param, flag);
                    default -> card.addProperty(param, value.toString());
                }
            });
            valuesObject.add(id, card);
        });
        root.add("toolValues", valuesObject);
        JsonObject scrollObject = new JsonObject();
        scroll.forEach(scrollObject::addProperty);
        root.add("scroll", scrollObject);
        root.add("blockRecents", array(blockRecents));
        root.add("expanded", array(expanded));
        return root.toString();
    }

    private static JsonArray array(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    /** Lee lo que pueda: un archivo roto o a medias da preferencias vacías, nunca un error. */
    public static StudioPreferences fromJson(String json) {
        StudioPreferences preferences = new StudioPreferences();
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return preferences;
            }
            JsonObject root = parsed.getAsJsonObject();
            for (String id : strings(root, "favorites")) {
                if (preferences.favorites.size() < MAX_FAVORITES) {
                    preferences.favorites.add(id);
                }
            }
            List<String> stored = strings(root, "recents");
            for (int index = stored.size() - 1; index >= 0; index--) {
                preferences.pushRecent(stored.get(index));
            }
            for (String id : strings(root, "toolFavorites")) {
                if (preferences.toolFavorites.size() < MAX_FAVORITES) {
                    preferences.toolFavorites.add(id);
                }
            }
            for (String name : strings(root, "stampFavorites")) {
                if (preferences.stampFavorites.size() < MAX_FAVORITES) {
                    preferences.stampFavorites.add(name);
                }
            }
            List<String> tools = strings(root, "toolRecents");
            for (int index = tools.size() - 1; index >= 0; index--) {
                preferences.pushToolRecent(tools.get(index));
            }
            boolean oldPreview = root.has("toolPreview") && root.get("toolPreview").isJsonPrimitive()
                    && root.getAsJsonPrimitive("toolPreview").isBoolean() && root.get("toolPreview").getAsBoolean();
            String preview = string(root, "preview");
            int format = root.has("format") && root.get("format").isJsonPrimitive()
                    && root.getAsJsonPrimitive("format").isNumber() ? root.get("format").getAsInt() : 1;
            PreviewMode chosen = preview.isEmpty() ? (oldPreview ? PreviewMode.BUILD : PreviewMode.OFF)
                    : PreviewMode.byId(preview);
            preferences.previewMode = format < 3 && chosen == PreviewMode.OFF ? PreviewMode.LIVE : chosen;
            preferences.stage = string(root, "stage");
            preferences.tool = string(root, "tool");
            preferences.theme = string(root, "theme");
            if (root.has("welcome") && root.get("welcome").isJsonPrimitive()
                    && root.getAsJsonPrimitive("welcome").isNumber()) {
                preferences.setWelcome(root.get("welcome").getAsInt());
            }
            if (root.has("panelShare") && root.get("panelShare").isJsonPrimitive()
                    && root.getAsJsonPrimitive("panelShare").isNumber()) {
                preferences.setPanelShare(root.get("panelShare").getAsDouble());
            }
            readToolValues(root, preferences);
            if (root.has("scroll") && root.get("scroll").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("scroll").entrySet()) {
                    if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                        preferences.setScroll(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
                    }
                }
            }
            List<String> blocks = strings(root, "blockRecents");
            for (int index = blocks.size() - 1; index >= 0; index--) {
                preferences.pushBlockRecent(blocks.get(index));
            }
            strings(root, "expanded").forEach(key -> preferences.setExpanded(key, true));
            if (format < ToolMigration.FORMAT) {
                preferences.upgradeTools(format);
            }
        } catch (RuntimeException ignored) {
            return new StudioPreferences();
        }
        return preferences;
    }

    private static void readToolValues(JsonObject root, StudioPreferences preferences) {
        if (!root.has("toolValues") || !root.get("toolValues").isJsonObject()) {
            return;
        }
        for (Map.Entry<String, JsonElement> card : root.getAsJsonObject("toolValues").entrySet()) {
            if (!card.getValue().isJsonObject() || preferences.toolValues.size() >= MAX_STORED) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> param : card.getValue().getAsJsonObject().entrySet()) {
                if (!param.getValue().isJsonPrimitive()) {
                    continue;
                }
                JsonPrimitive primitive = param.getValue().getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    values.put(param.getKey(), primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    values.put(param.getKey(), primitive.getAsInt());
                } else if (primitive.getAsString().length() <= 512) {
                    values.put(param.getKey(), primitive.getAsString());
                }
            }
            if (!values.isEmpty()) {
                preferences.toolValues.put(card.getKey(), values);
            }
        }
    }

    /**
     * Un archivo de antes de unir herramientas: los ajustes de las que se unieron pasan a la que las reúne y sus ids,
     * en favoritas, recientes, la última elegida, secciones abiertas y desplazamiento, al id de ahora.
     */
    private void upgradeTools(int format) {
        Map<String, Map<String, Object>> upgraded = ToolMigration.toolValues(new LinkedHashMap<>(toolValues), format,
                tool);
        toolValues.clear();
        toolValues.putAll(upgraded);
        List<String> favoriteIds = new ArrayList<>(toolFavorites);
        toolFavorites.clear();
        favoriteIds.forEach(id -> toolFavorites.add(ToolMigration.id(id)));
        List<String> recentIds = new ArrayList<>(toolRecents);
        toolRecents.clear();
        recentIds.forEach(id -> {
            String current = ToolMigration.id(id);
            if (!toolRecents.contains(current)) {
                toolRecents.add(current);
            }
        });
        tool = ToolMigration.id(tool);
        List<String> open = new ArrayList<>(expanded);
        expanded.clear();
        open.forEach(key -> expanded.add(renamedKey(key)));
        Map<String, Integer> scrolled = new LinkedHashMap<>(scroll);
        scroll.clear();
        scrolled.forEach((key, value) -> scroll.putIfAbsent(renamedKey(key), value));
    }

    /** {@code tools:more:snow} → {@code tools:more:touchup}, {@code tool:paintbrush} → {@code tool:paint}. */
    private static String renamedKey(String key) {
        int colon = key.lastIndexOf(':');
        if (colon < 0 || !ToolMigration.renamed(key.substring(colon + 1))) {
            return key;
        }
        return key.substring(0, colon + 1) + ToolMigration.id(key.substring(colon + 1));
    }

    public static StudioPreferences load(Path file) {
        try {
            return Files.isRegularFile(file)
                    ? fromJson(Files.readString(file, StandardCharsets.UTF_8))
                    : new StudioPreferences();
        } catch (IOException exception) {
            return new StudioPreferences();
        }
    }

    public void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        AtomicStorage.writeString(file, toJson());
    }

    private static String string(JsonObject root, String key) {
        if (root.has(key) && root.get(key).isJsonPrimitive() && root.getAsJsonPrimitive(key).isString()) {
            String value = root.get(key).getAsString().trim();
            return value.length() <= 64 ? value : "";
        }
        return "";
    }

    private static List<String> strings(JsonObject root, String key) {
        List<String> values = new ArrayList<>();
        if (root.has(key) && root.get(key).isJsonArray()) {
            for (JsonElement element : root.getAsJsonArray(key)) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    String value = element.getAsString().trim();
                    if (!value.isEmpty() && value.length() <= 64) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }
}
