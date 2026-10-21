package com.komainu.moxia.feedback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * {@link Words} con los archivos de idioma del repositorio, como hace el juego con {@code I18n.translate}: una clave
 * que falte hace fallar la prueba en lugar de salir tal cual.
 */
public final class LangWords implements Words {
    private final JsonObject lang;

    private LangWords(String name) {
        Path file = Path.of("src", "main", "resources", "assets", "komaedit", "lang", name + ".json");
        try {
            lang = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static LangWords spanish() {
        return new LangWords("es_es");
    }

    public static LangWords english() {
        return new LangWords("en_us");
    }

    @Override
    public String tr(String key, Object... arguments) {
        if (!lang.has(key)) {
            throw new AssertionError("falta la clave de idioma " + key);
        }
        return String.format(Locale.ROOT, lang.get(key).getAsString(), arguments);
    }
}
