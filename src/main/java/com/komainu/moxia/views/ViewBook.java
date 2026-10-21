package com.komainu.moxia.views;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Las vistas privadas del cliente, una lista por sitio: cada mundo de un jugador ({@code mundo:<carpeta>}) y cada
 * servidor ({@code servidor:<dirección>}) tiene las suyas, y dentro de cada sitio cada vista dice su dimensión. Se
 * guarda en {@code config/komaedit/views.json}. Java puro: el cliente la lee, la cambia y la escribe.
 *
 * <p>Una vista por nombre en cada sitio (sin mayúsculas): guardar con un nombre que ya existe la reemplaza. Fija hay
 * como mucho una por dimensión: fijar otra suelta la anterior.</p>
 */
public final class ViewBook {
    public static final int FORMAT = 1;
    public static final int MAX_PER_PLACE = 100;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<String, List<SavedView>> places = new LinkedHashMap<>();

    /** El sitio de un mundo de un jugador, por el nombre de su carpeta. */
    public static String singleplayer(String folder) {
        return "mundo:" + folder;
    }

    /** El sitio de un servidor, por su dirección (sin mayúsculas ni el puerto de serie). */
    public static String server(String address) {
        String clean = address == null ? "" : address.strip().toLowerCase(Locale.ROOT);
        if (clean.endsWith(":25565")) {
            clean = clean.substring(0, clean.length() - ":25565".length());
        }
        return "servidor:" + clean;
    }

    /** Las vistas de un sitio: primero la fija de cada dimensión y luego por nombre. */
    public synchronized List<SavedView> list(String place) {
        List<SavedView> views = new ArrayList<>(places.getOrDefault(place, List.of()));
        views.sort(Comparator.comparing((SavedView view) -> !view.pinned())
                .thenComparing(view -> ViewNames.key(view.name())));
        return List.copyOf(views);
    }

    public synchronized Optional<SavedView> find(String place, String name) {
        String key = ViewNames.key(name);
        return places.getOrDefault(place, List.of()).stream()
                .filter(view -> ViewNames.key(view.name()).equals(key)).findFirst();
    }

    /** La vista fija de una dimensión en un sitio. */
    public synchronized Optional<SavedView> pinned(String place, String dimension) {
        return places.getOrDefault(place, List.of()).stream()
                .filter(view -> view.pinned() && view.dimension().equals(dimension)).findFirst();
    }

    /**
     * Guarda (o reemplaza por nombre) una vista. Al reemplazarla conserva si era fija.
     *
     * @return {@code true} si reemplazó una que ya había
     */
    public synchronized boolean save(String place, SavedView view) {
        ViewNames.validate(view.name());
        ViewNames.validateDimension(view.dimension());
        List<SavedView> views = places.computeIfAbsent(place, key -> new ArrayList<>());
        String key = ViewNames.key(view.name());
        for (int index = 0; index < views.size(); index++) {
            SavedView old = views.get(index);
            if (ViewNames.key(old.name()).equals(key)) {
                SavedView replacement = view.withPinned(old.pinned() && old.dimension().equals(view.dimension()));
                views.set(index, replacement);
                return true;
            }
        }
        if (views.size() >= MAX_PER_PLACE) {
            throw new IllegalArgumentException("Ya tienes " + views.size() + " vistas aquí (máximo " + MAX_PER_PLACE
                    + "): borra alguna");
        }
        views.add(view.withPinned(false));
        return false;
    }

    /** Borra una vista; {@code false} si no estaba. */
    public synchronized boolean remove(String place, String name) {
        List<SavedView> views = places.get(place);
        if (views == null) {
            return false;
        }
        String key = ViewNames.key(name);
        boolean removed = views.removeIf(view -> ViewNames.key(view.name()).equals(key));
        if (views.isEmpty()) {
            places.remove(place);
        }
        return removed;
    }

    /** Cambia el nombre; lanza si el nuevo ya lo tiene otra. */
    public synchronized SavedView rename(String place, String name, String newName) {
        SavedView view = find(place, name).orElseThrow(() -> new IllegalArgumentException("No hay ninguna vista «"
                + ViewNames.clean(name) + "»"));
        String clean = ViewNames.validate(newName);
        Optional<SavedView> taken = find(place, clean);
        if (taken.isPresent() && taken.get() != view) {
            throw new IllegalArgumentException("Ya hay una vista «" + taken.get().name() + "»");
        }
        List<SavedView> views = places.get(place);
        SavedView renamed = view.withName(clean);
        views.set(views.indexOf(view), renamed);
        return renamed;
    }

    /**
     * Fija o suelta una vista. Fijar suelta la que estuviera fija en su dimensión.
     *
     * @return la vista tal como queda
     */
    public synchronized SavedView pin(String place, String name, boolean pinned) {
        SavedView view = find(place, name).orElseThrow(() -> new IllegalArgumentException("No hay ninguna vista «"
                + ViewNames.clean(name) + "»"));
        List<SavedView> views = places.get(place);
        if (pinned) {
            for (int index = 0; index < views.size(); index++) {
                SavedView other = views.get(index);
                if (other.pinned() && other.dimension().equals(view.dimension())) {
                    views.set(index, other.withPinned(false));
                }
            }
        }
        SavedView changed = view.withPinned(pinned);
        views.set(indexOf(views, view.name()), changed);
        return changed;
    }

    private static int indexOf(List<SavedView> views, String name) {
        String key = ViewNames.key(name);
        for (int index = 0; index < views.size(); index++) {
            if (ViewNames.key(views.get(index).name()).equals(key)) {
                return index;
            }
        }
        return -1;
    }

    public synchronized int size(String place) {
        return places.getOrDefault(place, List.of()).size();
    }

    // ------------------------------------------------------------------ JSON

    public synchronized String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonObject all = new JsonObject();
        for (Map.Entry<String, List<SavedView>> entry : places.entrySet()) {
            JsonArray views = new JsonArray();
            entry.getValue().forEach(view -> views.add(view.toJson()));
            all.add(entry.getKey(), views);
        }
        root.add("places", all);
        return GSON.toJson(root);
    }

    /**
     * Lee el archivo. Una vista rota se salta (y se cuenta en {@code skipped}); un archivo de un formato que no
     * conoce, o que no es JSON, lanza para que quien lo lee no lo pise.
     */
    public static ViewBook fromJson(String json, int[] skipped) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT;
        if (format > FORMAT) {
            throw new IllegalArgumentException("Formato de vistas desconocido: " + format);
        }
        ViewBook book = new ViewBook();
        JsonObject all = root.has("places") ? root.getAsJsonObject("places") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : all.entrySet()) {
            if (!entry.getValue().isJsonArray()) {
                continue;
            }
            List<SavedView> views = new ArrayList<>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                try {
                    SavedView view = SavedView.fromJson(element.getAsJsonObject());
                    boolean repeated = views.stream().anyMatch(other ->
                            ViewNames.key(other.name()).equals(ViewNames.key(view.name())));
                    if (repeated || views.size() >= MAX_PER_PLACE) {
                        throw new IllegalArgumentException("Vista repetida o de más");
                    }
                    views.add(view);
                } catch (RuntimeException exception) {
                    if (skipped != null && skipped.length > 0) {
                        skipped[0]++;
                    }
                }
            }
            if (!views.isEmpty()) {
                book.places.put(entry.getKey(), views);
            }
        }
        return book;
    }
}
