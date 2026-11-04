package com.komainu.moxia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nada del cliente escribe órdenes en el chat por su cuenta. Cada orden de chat suma al antispam de Minecraft, que
 * echa a quien no es OP tras unas diez seguidas: los botones del Studio, sus pantallas y las teclas de KomaEdit van
 * por {@code client.ButtonCommandClient}, que usa el paquete de los botones y solo vuelve al chat cuando debe. Lee las
 * fuentes, como {@link ClientInputOrderTest}: una orden de chat nueva en otro sitio tiene que ir por ahí o entrar en
 * {@link #ALLOWED} con su porqué.
 */
class ChatCommandsAuditTest {
    private static final List<Path> SOURCES = List.of(Path.of("src", "client", "java"), Path.of("src", "main", "java"));
    /** Lo que escribe en el chat por el jugador: las órdenes y los mensajes, con su método o su paquete. */
    private static final Pattern CHAT = Pattern.compile("\\.(sendChatCommand|sendCommand|sendChatMessage)\\s*\\("
            + "|new\\s+(CommandExecutionC2SPacket|ChatCommandSignedC2SPacket|ChatMessageC2SPacket)\\s*\\(");
    /** Quién puede escribir en el chat (ruta desde {@code com/komainu/moxia}) y por qué. */
    private static final Map<String, String> ALLOWED = Map.of(
            "client/ButtonCommandClient.java", "la única salida: las órdenes del propio cliente (kegui, kedisplay),"
                    + " que Fabric atiende sin mandar nada; un servidor sin el paquete de los botones (un KomaEdit"
                    + " anterior), frenando antes del antispam; y lo que el paquete no lleva (más de 8 192"
                    + " caracteres)");

    @Test
    void onlyTheRouterWritesInTheChat() {
        Map<String, Integer> found = new TreeMap<>();
        for (Path root : SOURCES) {
            for (Path file : java(root)) {
                Matcher matcher = CHAT.matcher(read(file));
                while (matcher.find()) {
                    found.merge(name(root, file), 1, Integer::sum);
                }
            }
        }
        assertEquals(new TreeMap<>(ALLOWED).keySet(), found.keySet(),
                "órdenes de chat fuera de ButtonCommandClient (o una excepción que ya no hace falta): " + found);
    }

    /** Todo lo que manda la vista común del Studio pasa por {@code StudioHost.send}: en el juego, por el paquete. */
    @Test
    void theStudioSendsThroughTheRouter() {
        String host = read(SOURCES.getFirst().resolve(Path.of("com", "komainu", "moxia", "client", "gui", "studio",
                "ClientStudioHost.java")));
        int start = host.indexOf("public void send(String command)");
        assertTrue(start >= 0, "ClientStudioHost.send");
        String body = host.substring(start, host.indexOf("\n    }\n", start));
        assertTrue(body.contains("ButtonCommandClient.send("), body);
    }

    private static String name(Path root, Path file) {
        return root.resolve(Path.of("com", "komainu", "moxia")).relativize(file).toString().replace('\\', '/');
    }

    private static List<Path> java(Path root) {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
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
