package com.komainu.moxia.notes;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * Las notas de un mundo, que guarda el servidor en {@code <mundo>/komaedit/notes.json}. Java puro sobre un archivo:
 * cada cambio se escribe con {@link AtomicStorage} antes de darlo por bueno, así que un fallo del disco deja las notas
 * como estaban.
 */
public final class NoteBook {
    public static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path file;
    private final Map<String, WorldNote> notes = new LinkedHashMap<>();

    /** Quién pide un cambio: su UUID, su nombre y lo que puede. */
    public record Actor(UUID id, String name, boolean seesTeam, boolean shares, boolean manages) {
    }

    /**
     * Dónde va una nota nueva (lo resuelve el servidor: el bloque mirado o la posición).
     *
     * @param side la cara del bloque en la que se puso ({@link WorldNote#side})
     */
    public record Anchor(String dimension, double x, double y, double z, boolean block, int side) {
    }

    public NoteBook(Path file) {
        this.file = file;
    }

    /**
     * Lee el archivo (si existe); las notas rotas se saltan. Devuelve cuántas se saltaron. Si el archivo entero no se
     * puede leer (a medias o de una versión más nueva), se aparta como {@code notes.broken-N.json} para que las notas
     * nuevas no lo pisen, y lanza diciendo dónde quedó.
     */
    public synchronized int load() {
        notes.clear();
        if (file == null || !Files.isRegularFile(file)) {
            return 0;
        }
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        try {
            return read(json);
        } catch (RuntimeException exception) {
            notes.clear();
            try {
                Path aside = AtomicStorage.setAside(file);
                throw new IllegalStateException("No se pudo leer " + file.getFileName() + " (" + exception.getMessage()
                        + "); quedó apartado como " + aside.getFileName(), exception);
            } catch (IOException moveFailure) {
                throw new UncheckedIOException(moveFailure);
            }
        }
    }

    synchronized int read(String json) {
        notes.clear();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT;
        if (format > FORMAT) {
            throw new IllegalArgumentException("Formato de notas desconocido: " + format);
        }
        int skipped = 0;
        JsonArray array = root.has("notes") ? root.getAsJsonArray("notes") : new JsonArray();
        for (JsonElement element : array) {
            try {
                WorldNote note = WorldNote.fromJson(element.getAsJsonObject());
                if (notes.containsKey(note.id())) {
                    throw new IllegalArgumentException("Id repetido");
                }
                notes.put(note.id(), note);
            } catch (RuntimeException exception) {
                skipped++;
            }
        }
        return skipped;
    }

    public synchronized int size() {
        return notes.size();
    }

    public synchronized Optional<WorldNote> find(String id) {
        return Optional.ofNullable(notes.get(id == null ? "" : id.strip().toLowerCase(java.util.Locale.ROOT)));
    }

    /**
     * Una nota que este jugador puede ver; lanza si no existe o no es para él. Las privadas de otro no las ve nadie,
     * tampoco quien gestiona las notas.
     */
    public synchronized WorldNote visible(String id, Actor actor) {
        WorldNote note = find(id).orElse(null);
        if (note == null || !note.visibleTo(actor.id(), actor.seesTeam() || actor.manages())) {
            throw new IllegalArgumentException("No hay ninguna nota «" + id + "» que puedas ver");
        }
        return note;
    }

    public synchronized int countBy(UUID author) {
        return (int) notes.values().stream().filter(note -> note.authorId().equals(author)).count();
    }

    public synchronized int teamCount() {
        return (int) notes.values().stream().filter(WorldNote::team).count();
    }

    /** Las notas que ve un jugador en una dimensión ({@code null}: en todas), de la más reciente a la más vieja. */
    public synchronized List<WorldNote> visibleTo(Actor actor, String dimension) {
        List<WorldNote> list = new ArrayList<>();
        for (WorldNote note : notes.values()) {
            if ((dimension == null || note.dimension().equals(dimension)) && note.visibleTo(actor.id(),
                    actor.seesTeam())) {
                list.add(note);
            }
        }
        list.sort(Comparator.comparingLong(WorldNote::created).reversed());
        return List.copyOf(list);
    }

    /**
     * Pone una nota nueva.
     *
     * @param random para el id (las pruebas pasan uno fijo)
     */
    public synchronized WorldNote add(NoteRequest request, Anchor anchor, Actor actor, NoteLimits limits, long now,
                                      IntUnaryOperator random) {
        String text = limits.checkText(request.text());
        int distance = limits.checkDistance(request.distanceOr(Math.min(NoteLimits.DEFAULT_DISTANCE,
                limits.maxDistance())));
        if (request.team()) {
            if (!limits.allowTeam()) {
                throw new IllegalArgumentException("En este servidor no se pueden poner notas para el equipo");
            }
            if (!actor.shares()) {
                throw new IllegalArgumentException("Te falta el permiso " + NotePermissions.SHARE
                        + " para poner notas del equipo");
            }
        }
        if (!actor.manages()) {
            int own = countBy(actor.id());
            if (own >= limits.maxPerPlayer()) {
                throw new IllegalArgumentException("Ya tienes " + own + " notas (máximo " + limits.maxPerPlayer()
                        + "): borra las resueltas con /komaedit nota limpiar");
            }
            if (request.team() && teamCount() >= limits.maxTeam()) {
                throw new IllegalArgumentException("El equipo ya tiene " + teamCount() + " notas (máximo "
                        + limits.maxTeam() + ")");
            }
        }
        Set<String> taken = new HashSet<>(notes.keySet());
        WorldNote note = new WorldNote(NoteIds.next(taken, random), text,
                request.colorOr(NoteColor.AMARILLO.rgb()), request.iconOr(NoteIcon.NOTA), anchor.dimension(),
                anchor.x(), anchor.y(), anchor.z(), anchor.block(), anchor.side(), distance, request.team(),
                actor.id(), actor.name(), now, "", 0L);
        Map<String, WorldNote> next = new LinkedHashMap<>(notes);
        next.put(note.id(), note);
        commit(next);
        return note;
    }

    /**
     * Marca una nota como resuelta (o la reabre). Las privadas, su autor; las del equipo, quien las ve.
     *
     * @return la nota tal como queda
     */
    public synchronized WorldNote resolve(String id, Actor actor, boolean resolved, long now) {
        WorldNote note = visible(id, actor);
        if (note.resolved() == resolved) {
            throw new IllegalArgumentException(resolved ? "La nota ya estaba resuelta" : "La nota no estaba resuelta");
        }
        WorldNote changed = resolved ? note.resolve(actor.name(), now) : note.reopen();
        Map<String, WorldNote> next = new LinkedHashMap<>(notes);
        next.put(note.id(), changed);
        commit(next);
        return changed;
    }

    /** Borra una nota: su autor o quien gestiona las notas. */
    public synchronized WorldNote remove(String id, Actor actor) {
        WorldNote note = visible(id, actor);
        if (!note.authorId().equals(actor.id()) && !actor.manages()) {
            throw new IllegalArgumentException("La nota es de " + note.author()
                    + ": solo la puede borrar quien la puso (o márcala como resuelta)");
        }
        Map<String, WorldNote> next = new LinkedHashMap<>(notes);
        next.remove(note.id());
        commit(next);
        return note;
    }

    /** Borra las notas resueltas propias (y, quien gestiona, todas las resueltas que ve). Devuelve las borradas. */
    public synchronized List<WorldNote> clearResolved(Actor actor) {
        List<WorldNote> removed = new ArrayList<>();
        Map<String, WorldNote> next = new LinkedHashMap<>(notes);
        for (WorldNote note : notes.values()) {
            boolean mine = note.authorId().equals(actor.id());
            boolean allowed = mine || actor.manages() && note.visibleTo(actor.id(), true);
            if (note.resolved() && allowed) {
                next.remove(note.id());
                removed.add(note);
            }
        }
        if (!removed.isEmpty()) {
            commit(next);
        }
        return List.copyOf(removed);
    }

    synchronized String toJson() {
        return toJson(notes);
    }

    private static String toJson(Map<String, WorldNote> map) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        JsonArray array = new JsonArray();
        map.values().forEach(note -> array.add(note.toJson()));
        root.add("notes", array);
        return GSON.toJson(root);
    }

    private void commit(Map<String, WorldNote> next) {
        if (file != null) {
            try {
                Files.createDirectories(file.getParent());
                AtomicStorage.writeString(file, toJson(next));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        notes.clear();
        notes.putAll(next);
    }
}
