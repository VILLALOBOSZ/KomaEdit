package com.komainu.moxia;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minecraft vuelve a llamar a {@code Screen.init()} con cada cambio de tamaño de la ventana (arrastrar su borde la
 * rehace decenas de veces por segundo) y al cambiar la escala de la GUI. Lo que una pantalla del cliente manda al
 * servidor al abrirse no puede ir suelto en {@code init()}: el Studio mandaba la captura de trayectoria y la receta en
 * cada uno y agotaba el freno de los botones del servidor («Vas demasiado deprisa»), y la de vistas pedía el catálogo.
 * Va en un bloque {@code if (!abierta) { abierta = true; … }} o en otro método que se llama así.
 */
class ScreenInitTest {
    private static final Path CLIENT = Path.of("src", "client", "java");
    private static final Pattern NETWORK = Pattern.compile(
            "ClientPlayNetworking\\.send\\(|ButtonCommandClient\\.send\\(|requestCatalog\\(|sendChatCommand\\(");
    /** {@code if (!campo) {} con {@code campo = true;} dentro: lo de abrir, una sola vez. */
    private static final Pattern ONCE = Pattern.compile("if \\(!(\\w+)\\) \\{\\s*\\1 = true;");

    @Test
    void screensSendNothingEachTimeTheyAreLaidOutAgain() throws IOException {
        List<String> wrong = new ArrayList<>();
        int screens = 0;
        try (Stream<Path> files = Files.walk(CLIENT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                int start = source.indexOf("protected void init() {");
                if (!source.contains("extends Screen") || start < 0) {
                    continue;
                }
                screens++;
                String body = withoutOnceBlocks(block(source, source.indexOf('{', start)));
                Matcher matcher = NETWORK.matcher(body);
                if (matcher.find()) {
                    wrong.add(file.getFileName() + ": " + matcher.group());
                }
            }
        }
        assertTrue(screens >= 5, "se miraron " + screens + " pantallas");
        assertEquals(List.of(), wrong, "pantallas que mandan algo cada vez que se rehacen");
    }

    /**
     * Lo de abrir va una vez por cada vez que la pantalla se muestra, no una vez en su vida: al volver a ella desde una
     * pantalla hija sobre la misma instancia (el Studio desde Referencias), Minecraft llama a {@code removed()} y
     * después a {@code init()}. Cada bandera de un bloque «una vez» de {@code init()} se pone a {@code false} en
     * {@code removed()}, que el redimensionado no llama: si no, el Studio volvía con la captura de trayectoria
     * apagada en el servidor (el {@code path mode off} de {@code removed()} sin su {@code on}).
     */
    @Test
    void aScreenShownAgainOpensAgain() throws IOException {
        List<String> wrong = new ArrayList<>();
        int flags = 0;
        try (Stream<Path> files = Files.walk(CLIENT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file, StandardCharsets.UTF_8);
                int start = source.indexOf("protected void init() {");
                if (!source.contains("extends Screen") || start < 0) {
                    continue;
                }
                Matcher once = ONCE.matcher(block(source, source.indexOf('{', start)));
                while (once.find()) {
                    flags++;
                    int removed = source.indexOf("public void removed() {");
                    String body = removed < 0 ? "" : block(source, source.indexOf('{', removed));
                    if (!body.contains(once.group(1) + " = false;")) {
                        wrong.add(file.getFileName() + ": " + once.group(1));
                    }
                }
            }
        }
        assertTrue(flags >= 2, "se miraron " + flags + " banderas");
        assertEquals(List.of(), wrong, "pantallas que no vuelven a abrirse al mostrarse otra vez");
    }

    /** El bloque entre la llave de {@code open} y la suya. */
    private static String block(String source, int open) {
        int depth = 0;
        for (int index = open; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(open, index + 1);
            }
        }
        throw new AssertionError("llaves sin cerrar");
    }

    private static String withoutOnceBlocks(String body) {
        Matcher matcher = ONCE.matcher(body);
        StringBuilder out = new StringBuilder();
        int from = 0;
        while (matcher.find(from)) {
            int open = body.indexOf('{', matcher.start());
            out.append(body, from, matcher.start());
            from = open + block(body, open).length();
        }
        return out.append(body.substring(from)).toString();
    }
}
