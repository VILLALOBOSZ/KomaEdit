package com.komainu.moxia.performance;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lo que pasa en el servidor cuando un jugador se va, y lo que no puede hacer quien lo programe.
 *
 * <p>Fabric lanza {@code ServerPlayConnectionEvents.DISCONNECT} desde el hilo de Netty (su {@code ClientConnectionMixin}
 * engancha {@code channelInactive}): cerrar ahí una edición que se está colocando la escribe desde dos hilos y tumba el
 * servidor. Todo va por {@link PlayerLeave}, que lo pasa al hilo del servidor.</p>
 */
class PlayerLeaveTest {
    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final Path BRIDGE = Path.of("com", "komainu", "moxia", "performance", "PlayerLeave.java");
    private static final Path RAY = Path.of("com", "komainu", "moxia", "performance", "LoadedRay.java");
    /** El rayo de la mirada de una entidad o de un mundo (no el de una forma, {@code VoxelShape.raycast}). */
    private static final Pattern WORLD_RAY = Pattern.compile("\\b(player|entity|world|getWorld\\(\\)|"
            + "getServerWorld\\(\\))\\s*\\.\\s*raycast\\(");

    private final List<ServerPlayConnectionEvents.Disconnect> registered = new ArrayList<>();

    @AfterEach
    void forget() {
        registered.forEach(PlayerLeave::forget);
        PlayerLeave.forgetConnections();
    }

    private void register(ServerPlayConnectionEvents.Disconnect listener) {
        registered.add(listener);
        PlayerLeave.register(listener);
    }

    @Test
    void onlyTheBridgeListensToFabricsDisconnect() {
        List<String> direct = new ArrayList<>();
        for (Path file : java()) {
            if (!file.endsWith(BRIDGE) && read(file).contains("ServerPlayConnectionEvents.DISCONNECT")) {
                direct.add(SOURCES.relativize(file).toString());
            }
        }
        assertEquals(List.of(), direct, "DISCONNECT llega desde Netty: apúntalo con PlayerLeave.register");
        String mod = read(SOURCES.resolve(Path.of("com", "komainu", "moxia", "KomaEditMod.java")));
        assertEquals(true, mod.contains("PlayerLeave.install();"), "sin el puente, nada de lo apuntado corre");
    }

    @Test
    void theServerNeverCastsARayThatLoadsChunks() {
        List<String> direct = new ArrayList<>();
        for (Path file : java()) {
            if (!file.endsWith(RAY) && WORLD_RAY.matcher(read(file)).find()) {
                direct.add(SOURCES.relativize(file).toString());
            }
        }
        assertEquals(List.of(), direct, "el rayo de la mirada carga los chunks que cruza: usa LoadedRay.cast");
    }

    @Test
    void eachServiceRunsOnceAndAFailingOneDoesNotStopTheOthers() {
        AtomicInteger cleaned = new AtomicInteger();
        register((handler, server) -> {
            throw new IllegalStateException("roto");
        });
        register((handler, server) -> cleaned.incrementAndGet());
        // La de la parada y la que llega luego desde Netty son la misma conexión.
        PlayerLeave.leave(null, null);
        PlayerLeave.leave(null, null);
        assertEquals(1, cleaned.get(), "una vez por conexión, aunque otro falle antes");
        PlayerLeave.forgetConnections();
        PlayerLeave.leave(null, null);
        assertEquals(2, cleaned.get());
    }

    private static List<Path> java() {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
