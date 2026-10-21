package com.komainu.moxia.display;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Qué displays creó cada jugador con KomaEdit, para el límite por jugador aunque estén en chunks sin cargar. Cada
 * entrada guarda el mundo y el chunk donde quedó el display. Si un display desaparece por otro camino ({@code /kill},
 * otro mod), la entrada se quita la próxima vez que su chunk esté cargado y se cuente ({@link #prune}).
 *
 * <p>Lógica pura; {@link #toJson} y {@link #fromJson} lo guardan en {@code <mundo>/komaedit/displays.json}.</p>
 */
public final class DisplayLedger {
    /** Dónde está un display de un jugador. */
    public record Entry(UUID owner, String world, int chunkX, int chunkZ) {
    }

    /** El mundo visto desde el registro: si el chunk está cargado y si el display sigue ahí. */
    public interface Lookup {
        boolean loaded(String world, int chunkX, int chunkZ);

        boolean exists(String world, UUID display);
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private boolean dirty;

    public synchronized void add(UUID display, UUID owner, String world, int chunkX, int chunkZ) {
        entries.put(display, new Entry(owner, world, chunkX, chunkZ));
        dirty = true;
    }

    public synchronized void remove(UUID display) {
        dirty |= entries.remove(display) != null;
    }

    /** El display se movió: cambia su chunk (sigue siendo del mismo jugador). */
    public synchronized void moved(UUID display, String world, int chunkX, int chunkZ) {
        Entry entry = entries.get(display);
        if (entry != null && (entry.chunkX() != chunkX || entry.chunkZ() != chunkZ || !entry.world().equals(world))) {
            entries.put(display, new Entry(entry.owner(), world, chunkX, chunkZ));
            dirty = true;
        }
    }

    public synchronized Entry get(UUID display) {
        return entries.get(display);
    }

    public synchronized int count(UUID owner) {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.owner().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Quita los displays del jugador cuyo chunk está cargado y ya no están.
     *
     * @return cuántos quitó
     */
    public synchronized int prune(UUID owner, Lookup lookup) {
        int removed = 0;
        var iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> next = iterator.next();
            Entry entry = next.getValue();
            if (entry.owner().equals(owner) && lookup.loaded(entry.world(), entry.chunkX(), entry.chunkZ())
                    && !lookup.exists(entry.world(), next.getKey())) {
                iterator.remove();
                removed++;
            }
        }
        dirty |= removed > 0;
        return removed;
    }

    /** Los displays que cumplen {@code filter}. */
    public synchronized int count(Predicate<Entry> filter) {
        return (int) entries.values().stream().filter(filter).count();
    }

    public synchronized boolean dirty() {
        return dirty;
    }

    public synchronized void saved() {
        dirty = false;
    }

    public synchronized String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        JsonArray list = new JsonArray();
        for (Map.Entry<UUID, Entry> entry : entries.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("display", entry.getKey().toString());
            item.addProperty("owner", entry.getValue().owner().toString());
            item.addProperty("world", entry.getValue().world());
            item.addProperty("chunkX", entry.getValue().chunkX());
            item.addProperty("chunkZ", entry.getValue().chunkZ());
            list.add(item);
        }
        root.add("displays", list);
        return root.toString();
    }

    /** Lee el registro; las entradas rotas se saltan. */
    public static DisplayLedger fromJson(String json) {
        DisplayLedger ledger = new DisplayLedger();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("displays")) {
            return ledger;
        }
        for (JsonElement element : root.getAsJsonArray("displays")) {
            try {
                JsonObject item = element.getAsJsonObject();
                ledger.entries.put(UUID.fromString(item.get("display").getAsString()),
                        new Entry(UUID.fromString(item.get("owner").getAsString()), item.get("world").getAsString(),
                                item.get("chunkX").getAsInt(), item.get("chunkZ").getAsInt()));
            } catch (RuntimeException ignored) {
                // Una entrada rota no tumba el registro entero.
            }
        }
        return ledger;
    }
}
