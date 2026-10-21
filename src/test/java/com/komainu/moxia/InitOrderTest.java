package com.komainu.moxia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que se carga en {@code KomaEditMod.onInitialize} corre antes de que WorldEdit tenga sus registros: una constante
 * estática con {@code BlockTypes.AIR} (o cualquier otro registro de WorldEdit) en una de esas clases tumba el arranque
 * del servidor y del cliente con «WorldEdit is not initialized yet». Pasó en la 2.0.0 con {@code RebuildService}.
 * Las pruebas no arrancan WorldEdit, así que esto lo vigila leyendo el código.
 */
class InitOrderTest {
    private static final Path SOURCES = Path.of("src/main/java");
    private static final Pattern REFERENCE = Pattern.compile(
            "\\b(?:com\\.komainu\\.moxia\\.[\\w.]+\\.)?([A-Z]\\w+)\\s*(?:\\.\\s*\\w+\\s*\\(|::)");
    private static final Pattern STATIC_FIELD = Pattern.compile(
            "^\\s*(?:private |public |protected )?static final [^=;(]+=\\s*([^;]+);", Pattern.MULTILINE);
    private static final Pattern WORLDEDIT_REGISTRY = Pattern.compile(
            "\\b(BlockTypes|ItemTypes|BiomeTypes|EntityTypes|FluidTypes|BlockCategories|ItemCategories)\\.");

    @Test
    void nothingLoadedAtInitTouchesWorldEditRegistries() throws IOException {
        Map<String, Path> classes = new HashMap<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> classes.putIfAbsent(path.getFileName().toString().replace(".java", ""), path));
        }
        String mod = Files.readString(SOURCES.resolve("com/komainu/moxia/KomaEditMod.java"), StandardCharsets.UTF_8);
        int start = mod.indexOf("public void onInitialize()");
        assertTrue(start >= 0, "no encuentro onInitialize");
        String init = mod.substring(start);

        List<String> problems = new ArrayList<>();
        Matcher reference = REFERENCE.matcher(init);
        java.util.Set<String> seen = new java.util.HashSet<>();
        while (reference.find()) {
            String name = reference.group(1);
            Path source = classes.get(name);
            if (source == null || !seen.add(name)) {
                continue;
            }
            String text = Files.readString(source, StandardCharsets.UTF_8);
            Matcher field = STATIC_FIELD.matcher(text);
            while (field.find()) {
                if (WORLDEDIT_REGISTRY.matcher(field.group(1)).find()) {
                    problems.add(name + ": " + field.group(0).strip());
                }
            }
        }
        assertEquals(List.of(), problems,
                "constantes con registros de WorldEdit en clases que se cargan al iniciar el mod: hazlas perezosas");
    }
}
