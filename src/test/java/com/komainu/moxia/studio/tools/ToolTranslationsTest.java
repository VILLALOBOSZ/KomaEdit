package com.komainu.moxia.studio.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.tools.ui.JobStrip;
import com.komainu.moxia.terrain.StampLibrary;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Toda la etapa Herramientas sale en el idioma de Minecraft: cada texto que arman las tarjetas existe en es y en. */
class ToolTranslationsTest {
    private static final Path TOOLS_SOURCES = Path.of("src", "main", "java", "com", "komainu", "moxia", "studio",
            "tools");
    private static final Pattern KEY = Pattern.compile("\"(gui\\.komaedit\\.[a-z0-9_.]*[a-z0-9_])\"");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    private static final Pattern WORDS = Pattern.compile("\\p{L}{2,} \\p{L}{2,}");
    /** Problemas que pueden salir en una tarjeta (los de {@link ToolProblem#error}/{@code warning}). */
    private static final List<String> PROBLEMS = List.of("permission", "no_selection", "no_path_or_selection",
            "loft_points", "material", "spaces", "illegal_character", "curve_name", "curve_count", "stamp_name",
            "drops", "thermal_strength", "text_empty", "text_lines", "font", "biome", "creative_only",
            "fluid_directions");

    private static JsonObject lang(String name) throws IOException {
        Path file = Path.of("src", "main", "resources", "assets", "komaedit", "lang", name + ".json");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyTextOfTheCardsExistsInBothLanguages() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (ToolCard card : ToolCatalog.ALL) {
            keys.add(card.nameKey());
            keys.add(card.descriptionKey());
            keys.add(card.buildKey());
            // El botón con cada valor de lo que lo cambia: el pincel («Al pincel») y los modos de Retocar.
            for (ToolParam param : card.params()) {
                if (param instanceof ToolParam.Choice choice && !choice.dynamic()) {
                    for (String option : choice.options()) {
                        keys.add(card.buildKey(card.defaults().with(choice.id(), option)));
                    }
                }
            }
            for (ToolUse use : card.uses()) {
                keys.add("gui.komaedit.tools.use." + use.id() + ".help");
            }
            for (ToolParam param : card.params()) {
                keys.add(param.labelKey());
                keys.add(card.labelKey(param));
                keys.add(card.helpKey(param));
                if (param instanceof ToolParam.Choice choice) {
                    choice.options().forEach(option -> keys.add(choice.optionKey(option)));
                }
                if (param instanceof ToolParam.Text text && card.autoHint(text.id(), card.defaults()) == null) {
                    keys.add(text.placeholderKey());
                }
            }
            for (ToolPreset preset : card.presets()) {
                keys.add(preset.labelKey());
                keys.add(preset.helpKey());
            }
            for (ToolAction action : card.actions()) {
                keys.add(action.labelKey());
                keys.add(action.helpKey());
            }
        }
        for (ToolCategory category : ToolCategory.values()) {
            keys.add(category.translationKey());
        }
        keys.add("gui.komaedit.tools.category.favorites");
        for (ToolTarget target : ToolTarget.values()) {
            keys.add(target.translationKey());
        }
        for (String variant : List.of("some", "none")) {
            keys.add(ToolTarget.SELECTION.translationKey() + "." + variant);
        }
        for (ToolTarget target : List.of(ToolTarget.PATH_OR_LOOK, ToolTarget.PATH_OR_SELECTION)) {
            keys.add(target.translationKey() + ".points");
            keys.add(target.translationKey() + ".none");
        }
        keys.add(ToolTarget.PATH_OR_SELECTION.translationKey() + ".selection");
        for (String problem : PROBLEMS) {
            keys.add("gui.komaedit.tools.problem." + problem);
            keys.add("gui.komaedit.tools.problem." + problem + ".short");
        }
        for (String stamp : StampLibrary.descriptions().keySet()) {
            keys.add("gui.komaedit.tools.option.stamp." + stamp + ".help");
        }
        for (ProgressLine.Phase phase : ProgressLine.Phase.values()) {
            keys.add(JobStrip.phaseKey(phase));
        }
        for (BuildCapability capability : BuildCapability.values()) {
            keys.add("gui.komaedit.capability." + capability.id());
            keys.add("gui.komaedit.capability." + capability.id() + ".desc");
        }
        for (String stage : List.of("tools")) {
            keys.add("gui.komaedit.stage." + stage);
            keys.add("gui.komaedit.stage." + stage + ".subtitle");
        }
        keys.addAll(literalKeys());
        assertPresent(keys);
    }

    /** Las claves escritas tal cual en las clases de la etapa (panel, franja de progreso, tarjetas). */
    private static Set<String> literalKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(TOOLS_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = KEY.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    // Prefijos que el código completa con un id (gui.komaedit.tools.use.<id>…).
                    if (!key.endsWith(".")) {
                        keys.add(key);
                    }
                }
            }
        }
        assertTrue(keys.size() > 40, "se encontraron " + keys.size() + " claves");
        return keys;
    }

    private static void assertPresent(Set<String> keys) throws IOException {
        JsonObject spanish = lang("es_es");
        JsonObject english = lang("en_us");
        List<String> missing = new ArrayList<>();
        for (String key : keys) {
            if (!spanish.has(key)) {
                missing.add("es_es: " + key);
            }
            if (!english.has(key)) {
                missing.add("en_us: " + key);
            }
        }
        assertTrue(missing.isEmpty(), missing.size() + " claves sin traducir:\n" + String.join("\n", missing));
    }

    /** Ninguna frase visible de la etapa puede quedar escrita a fuego en el código. */
    @Test
    void theToolsStageHasNoHardcodedSentences() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : List.of(TOOLS_SOURCES.resolve("ui/ToolsPanel.java"), TOOLS_SOURCES.resolve("ui/JobStrip.java"))) {
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
}
