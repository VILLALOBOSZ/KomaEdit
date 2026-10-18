package com.komainu.moxia.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.KomaEditMod;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code /komaedit quiet}, como {@code /ezedits quiet}: cada jugador puede silenciar los mensajes informativos de
 * KomaEdit (los grises de «leyendo…», «calculando…», la semilla usada y el aviso de bloques en cola). Los errores,
 * los avisos y el mensaje final de cada edición ({@code completada: N bloques · //undo}) salen siempre.
 *
 * <p>Se guarda en el proyecto del mundo ({@code komaedit/players/<uuid>/settings/chat.json}), así dura entre
 * sesiones, y se recuerda en memoria mientras el jugador está conectado.</p>
 */
public final class QuietMode {
    private static final int FORMAT_VERSION = 1;
    private static final Map<UUID, Boolean> CACHE = new ConcurrentHashMap<>();

    private QuietMode() {
    }

    public static boolean isQuiet(UUID playerId) {
        Boolean cached = CACHE.get(playerId);
        if (cached != null) {
            return cached;
        }
        Boolean loaded = load(playerId);
        if (loaded == null) {
            return false;
        }
        CACHE.put(playerId, loaded);
        return loaded;
    }

    /** Guarda la preferencia; falla si el proyecto del jugador no está abierto. */
    public static void set(UUID playerId, boolean quiet) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("quiet", quiet);
        try {
            AtomicStorage.writeString(file(playerId), root.toString());
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo guardar la preferencia: " + exception.getMessage(), exception);
        }
        CACHE.put(playerId, quiet);
    }

    /** Olvida lo recordado en memoria (al desconectarse); en disco sigue. */
    public static void forget(UUID playerId) {
        CACHE.remove(playerId);
    }

    public static void clearAll() {
        CACHE.clear();
    }

    /** Envía un mensaje informativo, salvo que el jugador los haya silenciado. */
    public static void info(ServerPlayerEntity player, Text text) {
        if (!isQuiet(player.getUuid())) {
            player.sendMessage(text, false);
        }
    }

    /** @return la preferencia guardada, o {@code null} si no se puede leer (sin proyecto abierto) */
    private static Boolean load(UUID playerId) {
        try {
            Path file = file(playerId);
            if (!Files.isRegularFile(file)) {
                return false;
            }
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            return root.has("quiet") && root.get("quiet").getAsBoolean();
        } catch (IOException | RuntimeException exception) {
            // Sin proyecto abierto o con el archivo roto, los mensajes salen como siempre.
            KomaEditMod.LOGGER.debug("Could not read the KomaEdit chat preference of {}", playerId, exception);
            return null;
        }
    }

    private static Path file(UUID playerId) {
        return ProjectStorage.namespace(playerId, "settings").resolve("chat.json");
    }
}
