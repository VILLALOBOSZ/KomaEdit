package com.komainu.moxia.reference;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.zip.CRC32;

/**
 * Las referencias abiertas en un mundo o servidor, en el orden en que se dibujan (la última, encima), y si se ven:
 * la tecla de mostrar u ocultar cambia {@link #shown} sin tocar cada una. Se guarda una por mundo en
 * {@code config/komaedit/references/.worlds/<clave>.json} ({@link #key}).
 */
public record ReferenceSet(boolean shown, List<ReferenceImage> references) {
    public static final int FORMAT = 1;
    /** Más referencias a la vez no caben en pantalla ni sirven para calcar. */
    public static final int MAX = 16;
    public static final ReferenceSet EMPTY = new ReferenceSet(true, List.of());

    public ReferenceSet {
        List<ReferenceImage> unique = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ReferenceImage reference : references == null ? List.<ReferenceImage>of() : references) {
            if (reference != null && unique.size() < MAX && ids.add(reference.id())) {
                unique.add(reference);
            }
        }
        references = List.copyOf(unique);
    }

    public boolean full() {
        return references.size() >= MAX;
    }

    public Optional<ReferenceImage> byId(String id) {
        return references.stream().filter(reference -> reference.id().equals(id)).findFirst();
    }

    /** Añade una referencia encima de las demás; si ya hay {@link #MAX}, no cambia nada. */
    public ReferenceSet add(ReferenceImage reference) {
        if (full()) {
            return this;
        }
        List<ReferenceImage> next = new ArrayList<>(references);
        next.add(reference);
        return new ReferenceSet(shown, next);
    }

    /** Sustituye la referencia con el mismo identificador (si no está, no cambia nada). */
    public ReferenceSet replace(ReferenceImage reference) {
        List<ReferenceImage> next = new ArrayList<>(references);
        for (int index = 0; index < next.size(); index++) {
            if (next.get(index).id().equals(reference.id())) {
                next.set(index, reference);
                return new ReferenceSet(shown, next);
            }
        }
        return this;
    }

    public ReferenceSet remove(String id) {
        List<ReferenceImage> next = new ArrayList<>(references);
        next.removeIf(reference -> reference.id().equals(id));
        return new ReferenceSet(shown, next);
    }

    /** La pone encima de las demás (se dibuja la última). */
    public ReferenceSet toFront(String id) {
        Optional<ReferenceImage> found = byId(id);
        if (found.isEmpty()) {
            return this;
        }
        List<ReferenceImage> next = new ArrayList<>(references);
        next.remove(found.get());
        next.add(found.get());
        return new ReferenceSet(shown, next);
    }

    public ReferenceSet withShown(boolean value) {
        return new ReferenceSet(value, references);
    }

    /** Las que se dibujan ahora en ese modo: con el conjunto a la vista y cada una visible. */
    public List<ReferenceImage> drawn(ReferenceImage.Mode mode) {
        if (!shown) {
            return List.of();
        }
        return references.stream().filter(reference -> reference.visible() && reference.mode() == mode).toList();
    }

    /** Un identificador de 8 cifras hexadecimales que no esté ya en el conjunto. */
    public String newId(IntSupplier random) {
        Set<String> used = new HashSet<>();
        references.forEach(reference -> used.add(reference.id()));
        for (int attempt = 0; attempt < 64; attempt++) {
            String id = String.format(Locale.ROOT, "%08x", random.getAsInt());
            if (!used.contains(id)) {
                return id;
            }
        }
        int counter = references.size();
        while (used.contains("r" + counter)) {
            counter++;
        }
        return "r" + counter;
    }

    // ---------------------------------------------------------------- guardado

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("shown", shown);
        JsonArray list = new JsonArray();
        references.forEach(reference -> list.add(reference.toJson()));
        root.add("references", list);
        return root.toString();
    }

    /** Lee un conjunto guardado; un archivo roto da el conjunto vacío y una referencia rota se salta. */
    public static ReferenceSet parse(String json) {
        try {
            JsonElement element = JsonParser.parseString(json == null ? "" : json);
            if (!element.isJsonObject()) {
                return EMPTY;
            }
            JsonObject root = element.getAsJsonObject();
            List<ReferenceImage> references = new ArrayList<>();
            JsonElement list = root.get("references");
            if (list != null && list.isJsonArray()) {
                for (JsonElement entry : list.getAsJsonArray()) {
                    ReferenceImage.parse(entry).ifPresent(references::add);
                }
            }
            return new ReferenceSet(ReferenceImage.bool(root, "shown", true), references);
        } catch (RuntimeException exception) {
            return EMPTY;
        }
    }

    /**
     * Nombre del archivo de un mundo o servidor: {@code mundo_<nombre>} o {@code servidor_<dirección>} en minúsculas,
     * con lo que no sea letra, cifra, punto o guion cambiado por {@code _}, y un resumen corto del nombre original
     * para que «Mi mundo» y «mi_mundo» no compartan archivo.
     *
     * @param singleplayer un mundo de un jugador (su carpeta); si no, la dirección del servidor
     */
    public static String key(boolean singleplayer, String name) {
        String raw = name == null ? "" : name.trim();
        StringBuilder slug = new StringBuilder();
        for (char character : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            boolean plain = character >= 'a' && character <= 'z' || character >= '0' && character <= '9'
                    || character == '.' || character == '-';
            slug.append(plain ? character : '_');
            if (slug.length() >= 48) {
                break;
            }
        }
        CRC32 crc = new CRC32();
        crc.update(raw.getBytes(StandardCharsets.UTF_8));
        String prefix = singleplayer ? "mundo_" : "servidor_";
        String body = slug.isEmpty() ? "sin_nombre" : slug.toString();
        return prefix + body + "_" + String.format(Locale.ROOT, "%08x", crc.getValue());
    }
}
