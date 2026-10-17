package com.komainu.moxia.studio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudioTranslationsTest {
    @Test
    void spanishAndEnglishHaveTheSameKeys() throws IOException {
        JsonObject spanish = lang("es_es");
        JsonObject english = lang("en_us");
        assertEquals(spanish.keySet(), english.keySet());
    }

    @Test
    void everyStudioTextOfTheLogicIsTranslated() throws IOException {
        List<String> keys = new ArrayList<>();
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
        for (String language : List.of("es_es", "en_us")) {
            JsonObject values = lang(language);
            for (String key : keys) {
                assertTrue(values.has(key), language + " sin " + key);
            }
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
