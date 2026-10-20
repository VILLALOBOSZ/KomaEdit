package com.komainu.moxia.symmetry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada texto de la simetría tiene su clave en los dos idiomas, y el español es el mismo que el de reserva. */
class SymmetryTranslationsTest {
    private static final List<Path> SOURCES = List.of(
            Path.of("src/main/java/com/komainu/moxia/command/SymmetryCommands.java"),
            Path.of("src/main/java/com/komainu/moxia/symmetry/SymmetryService.java")
    );
    /** {@code "komaedit.symmetry.clave", "texto" + "más texto"}: la clave y el texto de reserva, aunque ocupe líneas. */
    private static final Pattern CALL = Pattern.compile(
            "\"(komaedit\\.symmetry\\.[a-z_.]+)\",\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    void everySymmetryTextIsTranslated() throws IOException {
        JsonObject spanish = lang("es_es");
        JsonObject english = lang("en_us");
        Map<String, String> fallbacks = new LinkedHashMap<>();
        for (Path source : SOURCES) {
            Matcher call = CALL.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (call.find()) {
                StringBuilder text = new StringBuilder();
                Matcher literal = LITERAL.matcher(call.group(2));
                while (literal.find()) {
                    text.append(literal.group(1).replace("\\\"", "\""));
                }
                fallbacks.put(call.group(1), text.toString());
            }
        }
        assertTrue(fallbacks.size() >= 30, "claves encontradas: " + fallbacks.keySet());
        for (Map.Entry<String, String> entry : fallbacks.entrySet()) {
            assertTrue(english.has(entry.getKey()), "falta en en_us: " + entry.getKey());
            assertTrue(spanish.has(entry.getKey()), "falta en es_es: " + entry.getKey());
            assertEquals(entry.getValue(), spanish.get(entry.getKey()).getAsString(), entry.getKey());
            assertEquals(count(entry.getValue()), count(english.get(entry.getKey()).getAsString()), entry.getKey());
        }
        for (String key : spanish.keySet()) {
            if (key.startsWith("komaedit.symmetry.")) {
                assertTrue(fallbacks.containsKey(key), "clave sin usar: " + key);
            }
        }
    }

    private static long count(String text) {
        return Pattern.compile("%s").matcher(text).results().count();
    }

    private static JsonObject lang(String name) throws IOException {
        Path file = Path.of("src/main/resources/assets/komaedit/lang/" + name + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
