package com.komainu.moxia.gradient;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paletas de serie del servidor: las que el administrador define en {@code config/komaedit/palettes.json} y que todos
 * los jugadores usan con {@code ##nombre}, como el {@code DefaultPalettes.json} de ezEdits. Se suman a las de
 * {@link GradientPalette}; si un nombre coincide con una de esas, gana la de KomaEdit.
 *
 * <pre>{@code
 * {
 *   "format": 1,
 *   "palettes": {
 *     "murallas": "stone_bricks,cracked_stone_bricks,mossy_stone_bricks",
 *     "atardecer": "##FadeOrange,-##FadePurple"
 *   }
 * }
 * }</pre>
 *
 * <p>Cada definición admite la notación completa de {@link PaletteSpec} y puede usar las paletas de serie y las del
 * servidor definidas antes en el archivo. Las que no se pueden leer se saltan con un aviso en el registro.</p>
 */
public final class ServerPalettes {
    private static final int FORMAT_VERSION = 1;
    private static volatile Map<String, List<String>> palettes = Map.of();

    private ServerPalettes() {
    }

    /** Lee el archivo (o lo crea vacío). Devuelve cuántas paletas quedaron cargadas. */
    public static int load(Path file) {
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(file.getParent());
                JsonObject root = new JsonObject();
                root.addProperty("format", FORMAT_VERSION);
                root.add("palettes", new JsonObject());
                Files.writeString(file, root.toString(), StandardCharsets.UTF_8);
                palettes = Map.of();
                return 0;
            }
            palettes = parse(Files.readString(file, StandardCharsets.UTF_8));
            return palettes.size();
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.error("Could not load KomaEdit server palettes from {}", file, exception);
            palettes = Map.of();
            return 0;
        }
    }

    /** Lee el contenido del archivo; las paletas inválidas se saltan. */
    static Map<String, List<String>> parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported KomaEdit palettes format: " + format);
        }
        Map<String, List<String>> loaded = new LinkedHashMap<>();
        if (!root.has("palettes")) {
            return Collections.unmodifiableMap(loaded);
        }
        Map<String, List<String>> previous = palettes;
        try {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("palettes").entrySet()) {
                String name = entry.getKey().trim().toLowerCase(Locale.ROOT);
                if (!name.matches("[a-z0-9_]{1,32}")) {
                    KomaEditMod.LOGGER.warn("Skipping server palette with an invalid name: {}", entry.getKey());
                    continue;
                }
                try {
                    // Mientras se carga, las definiciones ya leídas se pueden usar con ##nombre.
                    palettes = Collections.unmodifiableMap(new LinkedHashMap<>(loaded));
                    loaded.put(name, PaletteSpec.parse(entry.getValue().getAsString()));
                } catch (RuntimeException exception) {
                    KomaEditMod.LOGGER.warn("Skipping server palette {}: {}", name, exception.getMessage());
                }
            }
        } finally {
            palettes = previous;
        }
        return Collections.unmodifiableMap(loaded);
    }

    /** Sustituye las paletas cargadas (pruebas). */
    static void set(Map<String, List<String>> value) {
        palettes = Map.copyOf(value);
    }

    /** Las entradas de la paleta del servidor, o {@code null} si no existe. */
    public static List<String> get(String name) {
        return palettes.get(name.toLowerCase(Locale.ROOT));
    }

    public static Map<String, List<String>> all() {
        return palettes;
    }
}
