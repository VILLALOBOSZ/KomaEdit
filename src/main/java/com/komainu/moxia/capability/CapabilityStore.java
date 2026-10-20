package com.komainu.moxia.capability;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.persistence.AtomicStorage;
import com.komainu.moxia.persistence.ProjectStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Capacidades de cada jugador en su proyecto: {@code <mundo>/komaedit/players/<uuid>/capabilities/settings.json}. */
public final class CapabilityStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private CapabilityStore() {
    }

    /** Lo guardado, o los valores por defecto si no hay nada, no se puede leer o el proyecto no está abierto. */
    public static CapabilitySettings load(UUID playerId) {
        try {
            Path file = file(playerId);
            if (!Files.isRegularFile(file)) {
                return CapabilitySettings.DEFAULT;
            }
            return CapabilitySettings.fromJson(JsonParser.parseString(
                    Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not read KomaEdit capabilities for {}", playerId, exception);
            return CapabilitySettings.DEFAULT;
        }
    }

    /** Guarda; si falla (proyecto cerrado, disco) se queda en memoria hasta salir y se avisa en el registro. */
    public static void save(UUID playerId, CapabilitySettings settings) {
        try {
            AtomicStorage.writeString(file(playerId), GSON.toJson(settings.toJson()));
        } catch (IOException | RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not save KomaEdit capabilities for {}", playerId, exception);
        }
    }

    private static Path file(UUID playerId) {
        return ProjectStorage.namespace(playerId, "capabilities").resolve("settings.json");
    }
}
