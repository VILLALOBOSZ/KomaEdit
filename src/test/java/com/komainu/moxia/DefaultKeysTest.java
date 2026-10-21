package com.komainu.moxia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las entradas de KomaEdit en Controles no traen de serie una tecla de Minecraft ni de Cobblemon. Minecraft solo
 * atiende una entrada por tecla (la que gane en su tabla), así que dos en la misma dejan una muerta: con P, la captura
 * de trayectoria no hacía nada (P es «Interacciones sociales»); con Alt izquierdo, la tira de barras dejaba sin «mirar
 * libre» al montar en Cobblemon. Lo que choca va sin asignar en Controles, con su atajo de serie leído aparte, como el
 * fantasma y las imágenes de referencia.
 */
class DefaultKeysTest {
    private static final Path CLIENT = Path.of("src", "client", "java");
    private static final Pattern BINDING = Pattern.compile("new KeyBinding\\([^;]*?GLFW\\.GLFW_KEY_([A-Z0-9_]+)");
    private static final Pattern HELPER = Pattern.compile("\\bkey\\(\"([a-z_]+)\", GLFW\\.GLFW_KEY_([A-Z0-9_]+)\\)");

    /** Las teclas de serie de Minecraft 1.21.1 ({@code GameOptions}) y de Cobblemon 1.7 y 1.8, con quién las usa. */
    private static final Map<String, String> TAKEN = Map.ofEntries(
            Map.entry("W", "avanzar"), Map.entry("A", "izquierda"), Map.entry("S", "retroceder"),
            Map.entry("D", "derecha"), Map.entry("SPACE", "saltar"), Map.entry("LEFT_SHIFT", "agacharse"),
            Map.entry("LEFT_CONTROL", "correr"), Map.entry("E", "inventario"), Map.entry("F", "cambiar de mano"),
            Map.entry("Q", "soltar"), Map.entry("T", "chat"), Map.entry("TAB", "lista de jugadores"),
            Map.entry("SLASH", "órdenes"), Map.entry("P", "interacciones sociales"), Map.entry("F2", "captura"),
            Map.entry("F5", "perspectiva"), Map.entry("F11", "pantalla completa"), Map.entry("L", "progresos"),
            Map.entry("C", "guardar barra"), Map.entry("X", "cargar barra"), Map.entry("1", "casilla 1"),
            Map.entry("2", "casilla 2"), Map.entry("3", "casilla 3"), Map.entry("4", "casilla 4"),
            Map.entry("5", "casilla 5"), Map.entry("6", "casilla 6"), Map.entry("7", "casilla 7"),
            Map.entry("8", "casilla 8"), Map.entry("9", "casilla 9"),
            Map.entry("R", "Cobblemon: sacar Pokémon"), Map.entry("O", "Cobblemon: ocultar el equipo"),
            Map.entry("N", "Cobblemon: PokéNav"), Map.entry("M", "Cobblemon: resumen"),
            Map.entry("UP", "Cobblemon: equipo arriba"), Map.entry("DOWN", "Cobblemon: equipo abajo"),
            Map.entry("LEFT_ALT", "Cobblemon: mirar libre al montar"));

    @Test
    void noDefaultKeyIsTakenByMinecraftOrCobblemon() throws IOException {
        List<String> found = new ArrayList<>();
        List<String> clashes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(CLIENT)) {
            files.filter(file -> file.toString().endsWith(".java")).forEach(file -> {
                String source = read(file);
                List<String> keys = new ArrayList<>();
                Matcher binding = BINDING.matcher(source);
                while (binding.find()) {
                    keys.add(binding.group(1));
                }
                Matcher helper = HELPER.matcher(source);
                while (helper.find()) {
                    keys.add(helper.group(2));
                }
                for (String key : keys) {
                    found.add(key);
                    if (TAKEN.containsKey(key)) {
                        clashes.add(file.getFileName() + ": " + key + " (" + TAKEN.get(key) + ")");
                    }
                }
            });
        }
        assertTrue(found.contains("K") && found.contains("ENTER"), "se leen las teclas de serie: " + found);
        assertEquals(List.of(), clashes);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
