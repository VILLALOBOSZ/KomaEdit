package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de los mensajes de las órdenes que solo se verían con un jugador de verdad, comprobadas en el código de
 * {@code command/} (como {@code InitOrderTest} con el arranque):
 *
 * <ul>
 *     <li>Al jugador nunca le llega el nombre de una clase de Java: un fallo inesperado se explica con
 *     {@link EzCommandSupport#reason}.</li>
 *     <li>Los estados de paso («Calculando…», «Colocando…», la hiedra que empieza) respetan {@code /komaedit quiet}:
 *     van con {@code EzCommandSupport.status} o {@code info}, no con {@code sendFeedback}, que sale siempre.</li>
 * </ul>
 */
class CommandSourceRulesTest {
    private static final Path COMMANDS = Path.of("src", "main", "java", "com", "komainu", "moxia", "command");

    @Test
    void noClassNameReachesThePlayer() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path file : sources()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.contains("getSimpleName()")) {
                wrong.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of(), wrong, "órdenes que pueden enseñar el nombre de una excepción");
    }

    @Test
    void stepMessagesRespectQuietMode() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Path file : sources()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            int from = 0;
            while ((from = text.indexOf("sendFeedback(", from)) >= 0) {
                String statement = statement(text, from + "sendFeedback(".length());
                // Un estado de paso acaba en «…», o es lo que devuelve el servicio al empezar («started»).
                if (statement.contains("…\")") || statement.matches("(?s).*\\bstarted\\b.*")) {
                    int line = (int) text.substring(0, from).chars().filter(character -> character == '\n').count()
                            + 1;
                    wrong.add(file.getFileName() + ":" + line);
                }
                from += "sendFeedback(".length();
            }
        }
        assertEquals(List.of(), wrong, "estados de paso que salen aunque el jugador pidió silencio");
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.list(COMMANDS)) {
            List<Path> list = files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
            assertTrue(list.size() > 30, "no se encontraron las órdenes en " + COMMANDS);
            return list;
        }
    }

    /** Lo que va entre los paréntesis de la llamada que empieza en {@code start}. */
    private static String statement(String text, int start) {
        int depth = 1;
        int index = start;
        while (index < text.length() && depth > 0) {
            char character = text.charAt(index++);
            if (character == '(') {
                depth++;
            } else if (character == ')') {
                depth--;
            }
        }
        return text.substring(start, index);
    }
}
