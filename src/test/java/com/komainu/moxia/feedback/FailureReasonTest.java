package com.komainu.moxia.feedback;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.MaxChangedBlocksException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que lee el jugador cuando algo falla fuera de las órdenes (la cola, la vista previa, los pinceles, los planos…):
 * el motivo si es un error de uso, y si no, una frase que lo dice sin el texto en inglés de Java ni el nombre de la
 * clase, con el detalle al registro.
 */
class FailureReasonTest {
    @Test
    void usageErrorsSayWhatHappened() {
        List<Throwable> logged = new ArrayList<>();
        assertEquals("El radio tiene que ser mayor que 0",
                FailureReason.of(new IllegalArgumentException("El radio tiene que ser mayor que 0"), log(logged)));
        assertEquals("Ya hay una edición en curso",
                FailureReason.of(new IllegalStateException("Ya hay una edición en curso"), log(logged)));
        assertEquals("WorldEdit llegó a su límite de 10 bloques por operación · //limit lo sube",
                FailureReason.of(new MaxChangedBlocksException(10), log(logged)),
                "el límite de WorldEdit no trae texto: se dice cuál es y cómo subirlo");
        assertEquals("Primero haz una selección de WorldEdit",
                FailureReason.of(new CompletionException(new IncompleteRegionException()), log(logged)));
        assertEquals(List.of(), logged, "un error de uso no es para el registro");
    }

    @Test
    void whatComesFromAnotherThreadIsUnwrapped() {
        List<Throwable> logged = new ArrayList<>();
        assertEquals("Selección vacía", FailureReason.of(new CompletionException(
                new IllegalArgumentException("Selección vacía")), log(logged)));
        assertEquals("Selección vacía", FailureReason.of(new ExecutionException(
                new IllegalArgumentException("Selección vacía")), log(logged)));
        assertEquals("Selección vacía", FailureReason.of(new RuntimeException(
                new IllegalArgumentException("Selección vacía")), log(logged)));
        assertEquals(List.of(), logged);
    }

    @Test
    void theUnexpectedNeverShowsJavaTextOrClassNames() {
        Map<String, Throwable> unexpected = Map.of(
                "NullPointerException", new NullPointerException(
                        "Cannot invoke \"net.minecraft.world.World.getBlockState()\" because \"world\" is null"),
                "ArrayIndexOutOfBoundsException", new ArrayIndexOutOfBoundsException(3),
                "NumberFormatException", new NumberFormatException("For input string: \"99999999999\""),
                "IOException", new IOException("D:\\servidor\\world\\komaedit\\plano.schem (Acceso denegado)"),
                "IllegalArgumentException", new IllegalArgumentException(),
                "UnsupportedOperationException", new CompletionException(new UnsupportedOperationException()));
        for (Map.Entry<String, Throwable> entry : unexpected.entrySet()) {
            List<Throwable> logged = new ArrayList<>();
            String reason = FailureReason.of(entry.getValue(), log(logged));
            assertEquals(FailureReason.UNEXPECTED, reason, entry.getKey());
            assertFalse(reason.contains(entry.getKey()), entry.getKey());
            assertEquals(List.of(entry.getValue()), logged, entry.getKey() + ": el detalle, al registro");
        }
    }

    @Test
    void nothingIsUnexpectedToo() {
        assertEquals(FailureReason.UNEXPECTED, FailureReason.of(null, log(new ArrayList<>())));
    }

    /**
     * En ninguna parte del mod (servidor ni cliente) llega al jugador el nombre de una clase: el «mensaje o nombre de
     * la clase» que había en una veintena de servicios pasa por {@link FailureReason}. Como
     * {@code CommandSourceRulesTest} con {@code command/}, pero en todas las fuentes.
     */
    @Test
    void noSourceTurnsAClassNameIntoAMessage() throws IOException {
        // Usos que no son un mensaje para el jugador, con el porqué.
        Map<String, String> allowed = Map.of(
                "SuperwandManager.java", "la clave que dice si la selección cambió, no se enseña",
                "ToolValues.java", "un error de programación al pedir un ajuste con otro tipo, nunca con datos del jugador");
        List<String> wrong = new ArrayList<>();
        for (Path root : List.of(Path.of("src", "main", "java"), Path.of("src", "client", "java"))) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    if (text.contains("getSimpleName()") && !allowed.containsKey(file.getFileName().toString())) {
                        wrong.add(file.getFileName().toString());
                    }
                }
            }
        }
        assertEquals(List.of(), wrong, "fuentes que pueden enseñar el nombre de una excepción");
    }

    private static java.util.function.BiConsumer<String, Throwable> log(List<Throwable> logged) {
        return (text, failure) -> logged.add(failure);
    }
}
