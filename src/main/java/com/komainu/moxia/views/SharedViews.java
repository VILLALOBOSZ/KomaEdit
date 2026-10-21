package com.komainu.moxia.views;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.persistence.AtomicStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Las vistas compartidas con el equipo, que guarda el servidor en {@code <mundo>/komaedit/views.json}. Una por nombre
 * en todo el mundo (sin mayúsculas): compartir con un nombre que ya es tuyo la reemplaza; con uno de otro, solo quien
 * gestiona las vistas ({@code komaedit.admin.views}). Java puro sobre un archivo, escrito con {@link AtomicStorage}.
 */
public final class SharedViews {
    public static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path file;
    private final List<SavedView> views = new ArrayList<>();

    /**
     * Límites al compartir.
     *
     * @param maxPerPlayer vistas compartidas de un mismo jugador
     * @param maxTotal     en todo el mundo
     */
    public record Limits(boolean allowSharing, int maxPerPlayer, int maxTotal) {
        public static final Limits DEFAULT = new Limits(true, 25, 300);

        public Limits {
            if (maxPerPlayer < 0 || maxTotal < 0) {
                throw new IllegalArgumentException("Límites de vistas no válidos");
            }
        }
    }

    public SharedViews(Path file) {
        this.file = file;
    }

    /** Lee el archivo (si existe); las vistas rotas se saltan. Devuelve cuántas se saltaron. */
    public synchronized int load() {
        views.clear();
        if (!Files.isRegularFile(file)) {
            return 0;
        }
        try {
            return read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    synchronized int read(String json) {
        views.clear();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT;
        if (format > FORMAT) {
            throw new IllegalArgumentException("Formato de vistas compartidas desconocido: " + format);
        }
        int skipped = 0;
        JsonArray array = root.has("views") ? root.getAsJsonArray("views") : new JsonArray();
        for (JsonElement element : array) {
            try {
                SavedView view = SavedView.fromJson(element.getAsJsonObject());
                if (!view.shared() || find(view.name()).isPresent()) {
                    throw new IllegalArgumentException("Vista sin autor o repetida");
                }
                views.add(view.withPinned(false));
            } catch (RuntimeException exception) {
                skipped++;
            }
        }
        return skipped;
    }

    public synchronized List<SavedView> all() {
        List<SavedView> sorted = new ArrayList<>(views);
        sorted.sort(Comparator.comparing(view -> ViewNames.key(view.name())));
        return List.copyOf(sorted);
    }

    public synchronized Optional<SavedView> find(String name) {
        String key = ViewNames.key(name);
        return views.stream().filter(view -> ViewNames.key(view.name()).equals(key)).findFirst();
    }

    public synchronized int countBy(UUID author) {
        return (int) views.stream().filter(view -> author.equals(view.authorId())).count();
    }

    /**
     * Comparte una vista (ya con autor, {@link SavedView#sharedBy}).
     *
     * @param unlimited se salta los límites de número
     * @param manage    puede reemplazar la de otro con el mismo nombre
     * @return la vista que reemplazó, si había
     */
    public synchronized Optional<SavedView> share(SavedView view, Limits limits, boolean unlimited, boolean manage) {
        if (!view.shared()) {
            throw new IllegalArgumentException("Una vista compartida necesita autor");
        }
        if (!limits.allowSharing()) {
            throw new IllegalArgumentException("En este servidor no se pueden compartir vistas");
        }
        ViewNames.validate(view.name());
        ViewNames.validateDimension(view.dimension());
        Optional<SavedView> existing = find(view.name());
        if (existing.isPresent() && !existing.get().authorId().equals(view.authorId()) && !manage) {
            throw new IllegalArgumentException("Ya hay una vista compartida «" + existing.get().name() + "» de "
                    + existing.get().author() + ": compártela con otro nombre");
        }
        if (existing.isEmpty() && !unlimited) {
            int own = countBy(view.authorId());
            if (own >= limits.maxPerPlayer()) {
                throw new IllegalArgumentException("Ya has compartido " + own + " vistas (máximo "
                        + limits.maxPerPlayer() + "): borra alguna con /komaedit vista borrar");
            }
            if (views.size() >= limits.maxTotal()) {
                throw new IllegalArgumentException("El servidor ya tiene " + views.size()
                        + " vistas compartidas (máximo " + limits.maxTotal() + ")");
            }
        }
        List<SavedView> next = new ArrayList<>(views);
        existing.ifPresent(next::remove);
        next.add(view.withPinned(false));
        commit(next);
        return existing;
    }

    /**
     * Deja de compartir una vista: solo su autor o quien gestiona las vistas.
     *
     * @return la vista quitada
     */
    public synchronized SavedView remove(String name, UUID requester, boolean manage) {
        SavedView view = find(name).orElseThrow(() -> new IllegalArgumentException("No hay ninguna vista compartida «"
                + ViewNames.clean(name) + "»"));
        if (!view.authorId().equals(requester) && !manage) {
            throw new IllegalArgumentException("La vista «" + view.name() + "» es de " + view.author()
                    + ": solo la puede quitar quien la compartió");
        }
        List<SavedView> next = new ArrayList<>(views);
        next.remove(view);
        commit(next);
        return view;
    }

    synchronized String toJson() {
        return toJson(views);
    }

    private static String toJson(List<SavedView> list) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonArray array = new JsonArray();
        list.forEach(view -> array.add(view.toJson()));
        root.add("views", array);
        return GSON.toJson(root);
    }

    /** Escribe la lista nueva y solo entonces la da por buena: si el disco falla, queda la de antes. */
    private void commit(List<SavedView> next) {
        try {
            Files.createDirectories(file.getParent());
            AtomicStorage.writeString(file, toJson(next));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        views.clear();
        views.addAll(next);
    }
}
