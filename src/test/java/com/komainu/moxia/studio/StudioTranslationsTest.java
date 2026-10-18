package com.komainu.moxia.studio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.tube.TubeEngine;
import com.komainu.moxia.shape.tube.TubeSettings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioTranslationsTest {
    private static final Path CLIENT_SOURCES = Path.of("src", "client", "java");
    /** Clases cuyo texto llega a la pantalla del Studio o al HUD de captura. */
    private static final List<Path> PLAYER_FACING = List.of(
            CLIENT_SOURCES.resolve("com/komainu/moxia/client/gui/ProceduralStudioScreen.java"),
            CLIENT_SOURCES.resolve("com/komainu/moxia/client/gui/studio/StudioUi.java"),
            CLIENT_SOURCES.resolve("com/komainu/moxia/client/hud/PathCaptureHud.java"),
            CLIENT_SOURCES.resolve("com/komainu/moxia/client/preview/ProceduralPreviewRenderer.java")
    );
    private static final Pattern KEY = Pattern.compile("\"((?:gui|hud)\\.komaedit\\.[a-z0-9_.]*[a-z0-9_])\"");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern WORDS = Pattern.compile("\\p{L}{2,} \\p{L}{2,}");

    @Test
    void spanishAndEnglishHaveTheSameKeys() throws IOException {
        JsonObject spanish = lang("es_es");
        JsonObject english = lang("en_us");
        assertEquals(spanish.keySet(), english.keySet());
    }

    @Test
    void everyStudioTextOfTheLogicIsTranslated() throws IOException {
        List<String> keys = new ArrayList<>(StudioNames.allKeys());
        for (StudioShortcuts.Shortcut shortcut : StudioShortcuts.ALL) {
            keys.add(shortcut.keysKey());
            keys.add(shortcut.translationKey());
        }
        for (ShapeCategory category : ShapeCategory.values()) {
            keys.add(category.translationKey());
        }
        for (ShapeVariants.Preset preset : ShapeVariants.Preset.values()) {
            keys.add(preset.translationKey());
            keys.add(preset.translationKey() + ".help");
        }
        // Claves que la pantalla arma con un id: estados de normales y piezas y ayudas de las filas de ciclo.
        for (TubeSettings.NormalMode mode : TubeSettings.NormalMode.values()) {
            keys.add("gui.komaedit.status.normal." + mode.id());
        }
        for (SmoothMode mode : SmoothMode.values()) {
            keys.add("gui.komaedit.status.smooth." + mode.id());
        }
        for (String option : List.of(TubeEngine.OPTION, "preset", "profile", "hollow", "end", "normal", "smooth",
                "coverage", "accent", "socket_placement", "easing")) {
            keys.add("gui.komaedit.help.option." + option);
        }
        for (String stage : List.of("path", "figure", "structures", "modifiers", "material", "weather",
                "instances", "recipes")) {
            keys.add("gui.komaedit.stage." + stage);
            keys.add("gui.komaedit.stage." + stage + ".subtitle");
        }
        for (String control : List.of("radius", "spacing", "thickness", "roll", "height", "dither", "start_scale",
                "end_scale", "start_spacing", "end_spacing", "twist", "rhythm_interval", "conform_radius",
                "surface_offset", "socket_interval", "weather_intensity", "weather_collapse", "weather_vegetation")) {
            keys.add("gui.komaedit.help." + control);
        }
        assertPresentInBothLanguages(keys);
    }

    @Test
    void everyKeyWrittenInTheClientExistsInBothLanguages() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(CLIENT_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = KEY.matcher(Files.readString(file));
                while (matcher.find()) {
                    keys.add(matcher.group(1));
                }
            }
        }
        assertTrue(keys.size() > 100, "se encontraron " + keys.size() + " claves");
        assertPresentInBothLanguages(new ArrayList<>(keys));
    }

    /**
     * El Studio salía mitad en inglés y mitad en español porque había frases escritas a fuego. Ninguna frase
     * visible puede quedar fuera de los idiomas: solo se admiten comandos y fragmentos sin palabras.
     */
    @Test
    void theStudioHasNoHardcodedSentences() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : PLAYER_FACING) {
            String source = Files.readString(file)
                    .replaceAll("(?s)/\\*.*?\\*/", "")
                    .replaceAll("//[^\\n]*", "");
            Matcher matcher = LITERAL.matcher(source);
            while (matcher.find()) {
                String literal = matcher.group(1);
                if (WORDS.matcher(literal).find() && !literal.startsWith("komaedit ")) {
                    found.add(file.getFileName() + ": \"" + literal + "\"");
                }
            }
        }
        assertTrue(found.isEmpty(), "Textos sin traducir:\n" + String.join("\n", found));
    }

    @Test
    void placeholdersMatchBetweenLanguages() throws IOException {
        JsonObject spanish = lang("es_es");
        JsonObject english = lang("en_us");
        Pattern placeholder = Pattern.compile("%(?:\\d+\\$)?s|%%");
        for (String key : spanish.keySet()) {
            assertEquals(count(placeholder, spanish.get(key).getAsString()),
                    count(placeholder, english.get(key).getAsString()), key);
            // Un % suelto lo interpreta Minecraft como formato; los porcentajes van como %%.
            String withoutValid = placeholder.matcher(spanish.get(key).getAsString()).replaceAll("");
            assertTrue(!withoutValid.contains("%"), key + " tiene un % sin escapar");
        }
    }

    private static int count(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        int total = 0;
        while (matcher.find()) {
            total++;
        }
        return total;
    }

    private static void assertPresentInBothLanguages(List<String> keys) throws IOException {
        for (String language : List.of("es_es", "en_us")) {
            JsonObject values = lang(language);
            List<String> missing = keys.stream().filter(key -> !values.has(key)).toList();
            assertTrue(missing.isEmpty(), language + " sin " + missing);
        }
    }

    private static JsonObject lang(String language) throws IOException {
        try (InputStream stream = StudioTranslationsTest.class.getResourceAsStream(
                "/assets/komaedit/lang/" + language + ".json")) {
            assertNotNull(stream, language);
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
