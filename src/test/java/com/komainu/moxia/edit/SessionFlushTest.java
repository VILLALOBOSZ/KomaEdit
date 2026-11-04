package com.komainu.moxia.edit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WorldEdit guarda lo escrito en la {@code EditSession} hasta vaciarla, y al vaciarla aplica de golpe la luz, los
 * vecinos y el envío. Lo que escribe durante varios ticks lo vacía por tramos con el plazo del tick
 * ({@link BatchEditService#session}): el trazo de pintar vaciaba cada tanda con {@code flushSession()} (decenas de miles
 * de bloques en un tick) y la pintura rápida lo dejaba todo para el tick de soltar el botón.
 */
class SessionFlushTest {
    private static final Path SOURCES = Path.of("src", "main", "java", "com", "komainu", "moxia");

    @Test
    void nothingFlushesAWholeSessionAtOnce() throws IOException {
        List<String> whole = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                if (Files.readString(file, StandardCharsets.UTF_8).contains(".flushSession()")) {
                    whole.add(SOURCES.relativize(file).toString());
                }
            }
        }
        assertEquals(List.of(), whole, "vacía la sesión de golpe: usa BatchEditService.session(...).flush()");
    }

    @Test
    void paintingFlushesWhatItWritesTickByTick() throws IOException {
        for (Path file : List.of(SOURCES.resolve(Path.of("editor", "FastPaintService.java")),
                SOURCES.resolve(Path.of("brush", "paint", "PaintStrokes.java")))) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            assertTrue(source.contains("BatchEditService.session(") && source.contains(".resume("),
                    file.getFileName() + " no vacía por tramos lo que escribe");
        }
    }
}
