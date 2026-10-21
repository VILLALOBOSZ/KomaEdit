package com.komainu.moxia.persistence;

import com.google.gson.JsonParser;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathStoreTest {
    /**
     * Guardar dos veces deja la copia de la anterior ({@code .bak}); al volver a entrar, el proyecto recupera los
     * archivos que faltan desde su copia. Un recorrido borrado no puede volver así.
     */
    @Test
    void aDeletedPathDoesNotComeBackOnTheNextLogin(@TempDir Path root) throws IOException {
        Path file = root.resolve("paths").resolve("puente.json");
        AtomicStorage.writeString(file, "{\"format\":1,\"points\":[[0,64,0]]}");
        AtomicStorage.writeString(file, "{\"format\":1,\"points\":[[5,64,5]]}");
        assertTrue(PathStore.delete(file));
        // Lo que hace ProjectStorage.bind cuando el jugador vuelve a entrar.
        AtomicStorage.recoverTree(root);
        assertFalse(Files.exists(file), "el recorrido borrado ha vuelto desde su copia");
    }

    @Test
    void roundTripsThroughJson() {
        PathStore.Saved saved = new PathStore.Saved("puente", "minecraft:overworld", true,
                List.of(BlockVector3.at(-10, 64, 3), BlockVector3.at(20, 70, -8)));
        PathStore.Saved read = PathStore.fromJson(JsonParser.parseString(PathStore.toJson(saved).toString()));
        assertEquals(saved, read);
    }

    @Test
    void toleratesUnknownFieldsAndRejectsFutureFormats() {
        String extra = "{\"format\":1,\"points\":[[1,2,3]],\"color\":\"red\"}";
        assertEquals(List.of(BlockVector3.at(1, 2, 3)), PathStore.fromJson(JsonParser.parseString(extra)).points());
        assertThrows(IllegalArgumentException.class,
                () -> PathStore.fromJson(JsonParser.parseString("{\"format\":2,\"points\":[]}")));
        assertThrows(IllegalArgumentException.class,
                () -> PathStore.fromJson(JsonParser.parseString("{\"format\":1,\"points\":[[1,2]]}")));
    }

    @Test
    void rejectsMoreThanTheMaximumPoints() {
        StringBuilder points = new StringBuilder();
        for (int index = 0; index < 33; index++) {
            points.append(index == 0 ? "" : ",").append("[").append(index).append(",0,0]");
        }
        assertThrows(IllegalArgumentException.class,
                () -> PathStore.fromJson(JsonParser.parseString("{\"format\":1,\"points\":[" + points + "]}")));
    }

    @Test
    void normalizesNames() {
        assertEquals("puente_norte", PathStore.normalize(" Puente_Norte "));
        assertThrows(IllegalArgumentException.class, () -> PathStore.normalize("puénte"));
        assertThrows(IllegalArgumentException.class, () -> PathStore.normalize("../fuera"));
        assertThrows(IllegalArgumentException.class, () -> PathStore.normalize(""));
    }
}
