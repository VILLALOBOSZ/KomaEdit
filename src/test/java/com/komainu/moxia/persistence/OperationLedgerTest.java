package com.komainu.moxia.persistence;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La bitácora de trabajos ({@code <mundo>/komaedit/players/<uuid>/jobs}) solo sirve para avisar de lo que una caída
 * dejó a medias. Cada edición dejaba su archivo para siempre, y al entrar el jugador se leían todos en el hilo del
 * servidor: miles de archivos al cabo de unos meses de obra.
 */
class OperationLedgerTest {
    @TempDir
    Path directory;

    @Test
    void aFinishedJobLeavesNothingOnDisk() throws IOException {
        String id = OperationLedger.start(directory, "Rellenar", 1_000);
        OperationLedger.progress(directory, id, "Rellenar", 500, 1_000, 480);
        OperationLedger.finish(directory, id, "Rellenar", 1_000, 1_000, 950, 0, false);
        String cancelled = OperationLedger.start(directory, "Escalera", 10);
        OperationLedger.finish(directory, cancelled, "Escalera", 3, 10, 3, 0, true);

        assertEquals(List.of(), files(), "ni el .json ni su .bak");
        // Al volver a abrir el proyecto, AtomicStorage rehace un .json que solo tenga su .bak: no debe volver nada.
        AtomicStorage.recoverTree(directory);
        assertEquals(0, OperationLedger.markInterrupted(directory));
    }

    @Test
    void aCrashStillLeavesTheRunningJobToWarnAbout() throws IOException {
        String id = OperationLedger.start(directory, "Rellenar", 1_000);
        OperationLedger.progress(directory, id, "Rellenar", 500, 1_000, 480);

        assertEquals(1, OperationLedger.markInterrupted(directory));
        assertEquals("INTERRUPTED", status(directory.resolve(id + ".json")));
        assertEquals(0, OperationLedger.markInterrupted(directory), "se avisa una vez");
    }

    @Test
    void joiningClearsWhatEarlierVersionsLeftBehind() throws IOException {
        for (int index = 0; index < 3; index++) {
            String id = "00000000-0000-0000-0000-00000000000" + index;
            AtomicStorage.writeString(directory.resolve(id + ".json"),
                    "{\"format\":1,\"id\":\"" + id + "\",\"status\":\"" + (index == 2 ? "CANCELLED" : "COMPLETED")
                            + "\"}");
            AtomicStorage.writeString(directory.resolve(id + ".json"), Files.readString(directory.resolve(id + ".json")));
        }
        String interrupted = "00000000-0000-0000-0000-000000000009";
        AtomicStorage.writeString(directory.resolve(interrupted + ".json"),
                "{\"format\":1,\"id\":\"" + interrupted + "\",\"status\":\"INTERRUPTED\"}");

        assertEquals(0, OperationLedger.markInterrupted(directory));
        assertEquals(List.of(interrupted + ".json"), files(),
                "los terminados se van; el interrumpido se queda para project status");
    }

    @Test
    void aBrokenFileDoesNotStopTheRest() throws IOException {
        // Delante en el orden de la carpeta (en Windows, alfabético): antes, el primero roto paraba todo lo demás.
        Files.writeString(directory.resolve("0-roto.json"), "{\"format\":1", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("1-sin-estado.json"), "{\"format\":1}", StandardCharsets.UTF_8);
        String running = OperationLedger.start(directory, "Rellenar", 10);
        String done = "00000000-0000-0000-0000-000000000005";
        AtomicStorage.writeString(directory.resolve(done + ".json"),
                "{\"format\":1,\"id\":\"" + done + "\",\"status\":\"COMPLETED\"}");

        assertEquals(1, OperationLedger.markInterrupted(directory));
        assertEquals("INTERRUPTED", status(directory.resolve(running + ".json")));
        assertEquals(Stream.of("0-roto.json", "1-sin-estado.json", running + ".json", running + ".json.bak")
                        .sorted().toList(), files(), "lo ilegible se queda donde está; lo terminado se va");
    }

    private List<String> files() throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    private static String status(Path file) throws IOException {
        assertTrue(Files.isRegularFile(file), file + " existe");
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
                .get("status").getAsString();
    }
}
