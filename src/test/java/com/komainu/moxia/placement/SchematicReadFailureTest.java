package com.komainu.moxia.placement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Un esquema dañado de la carpeta de WorldEdit ({@code Schematic(N:…)}, {@code place Schematic}) no le enseña al
 * jugador el texto de Java del lector («Not in GZIP format») ni «null»: dice que está dañado y el detalle va al
 * registro. Lo trajo la caché de esquemas del #110 con el texto de la excepción tal cual.
 */
class SchematicReadFailureTest {
    @Test
    void aDamagedSchematicSaysSoInSpanish(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("roble.schem");
        Files.write(file, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> PlacementService.read(file, "sponge"));
        String message = failure.getMessage();
        assertTrue(message.startsWith("No se pudo leer roble.schem: "), message);
        assertTrue(message.contains("dañado"), message);
        assertFalse(message.contains("GZIP") || message.contains("null") || message.contains("Exception")
                || message.contains(folder.toString()), message);
    }
}
