package com.komainu.moxia.capability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityTranslationsTest {
    private static final Path CLIENT = Path.of("src", "client", "java", "com", "komainu", "moxia", "client",
            "capability");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern WORDS = Pattern.compile("\\p{L}{2,} \\p{L}{2,}");

    @Test
    void everyCapabilityHasNameShortNameDescriptionAndShortcut() throws IOException {
        List<String> keys = new ArrayList<>();
        for (BuildCapability capability : BuildCapability.values()) {
            String base = "gui.komaedit.capability." + capability.id();
            keys.add(base);
            keys.add(base + ".short");
            keys.add(base + ".desc");
            keys.add("key.komaedit.capability." + capability.id());
        }
        keys.add("key.komaedit.capability_menu");
        for (String language : List.of("es_es", "en_us")) {
            JsonObject values = lang(language);
            List<String> missing = keys.stream().filter(key -> !values.has(key)).toList();
            assertTrue(missing.isEmpty(), language + " sin " + missing);
        }
    }

    /** El menú, el HUD y la vista previa no escriben frases a fuego: todo pasa por los idiomas. */
    @Test
    void clientCapabilityScreensHaveNoHardcodedSentences() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(CLIENT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file)
                        .replaceAll("(?s)/\\*.*?\\*/", "")
                        .replaceAll("//[^\\n]*", "");
                Matcher matcher = LITERAL.matcher(source);
                while (matcher.find()) {
                    if (WORDS.matcher(matcher.group(1)).find()) {
                        found.add(file.getFileName() + ": \"" + matcher.group(1) + "\"");
                    }
                }
            }
        }
        assertTrue(found.isEmpty(), "Textos sin traducir:\n" + String.join("\n", found));
    }

    private static JsonObject lang(String language) throws IOException {
        try (InputStream stream = CapabilityTranslationsTest.class.getResourceAsStream(
                "/assets/komaedit/lang/" + language + ".json")) {
            assertNotNull(stream, language);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
