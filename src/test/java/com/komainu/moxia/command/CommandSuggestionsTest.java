package com.komainu.moxia.command;

import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El autocompletado se pide en cada tecla: ninguna sugerencia puede lanzar una excepción con lo que se está
 * escribiendo a medias (Brigadier solo recoge las de sintaxis; cualquier otra sale del manejo del paquete y el
 * jugador se queda sin sugerencias) ni tardar. Recorre todo el árbol de {@code /komaedit} con trozos de texto típicos
 * de quien escribe: nada, una letra, una bandera, una comilla sin cerrar…
 */
class CommandSuggestionsTest {
    /** Lo que se escribe tras cada argumento. */
    private static final List<String> TYPING = List.of("", "a", "-", "1 ", "x y ", "-v ", "\"", "P:\"dirt",
            "Sh(S:Cone,P:\"stone", "(", "#", "##");
    /** Estas sugieren lo que hay en el servidor (biomas, jugadores, sellos de la carpeta) y aquí no hay servidor. */
    private static final Set<String> NEED_THE_SERVER = Set.of("biome", "bioma", "jobs", "trabajos", "relieve",
            "relief");
    private static final long MAX_MILLIS = 250;

    @Test
    void noSuggestionThrowsOrTakesLongWhileTyping() throws Exception {
        OpenCommandTree tree = new OpenCommandTree();
        List<String> prefixes = new ArrayList<>();
        walk(tree.dispatcher.getRoot().getChild("komaedit"), "komaedit", prefixes, 0);
        assertTrue(prefixes.size() > 1_000, "recorrió " + prefixes.size() + " órdenes");
        List<String> wrong = new ArrayList<>();
        for (String prefix : prefixes) {
            String[] words = prefix.split(" ");
            if (words.length > 1 && NEED_THE_SERVER.contains(words[1])) {
                continue;
            }
            for (String typed : TYPING) {
                String input = prefix + " " + typed;
                try {
                    tree.suggestions(input);
                    long start = System.nanoTime();
                    tree.suggestions(input);
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    if (millis > MAX_MILLIS) {
                        wrong.add("«" + input + "» tarda " + millis + " ms");
                    }
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
                    wrong.add("«" + input + "» " + cause);
                }
            }
        }
        assertEquals(List.of(), wrong, "sugerencias que fallan o tardan");
    }

    @Test
    void anUnclosedQuoteInAPlacementStillSuggests() {
        OpenCommandTree tree = new OpenCommandTree();
        for (String command : List.of("place", "array", "scatter")) {
            assertDoesNotThrow(() -> tree.suggestions("komaedit " + command + " Sh(S:Cone,P:\"dirt"), command);
            assertDoesNotThrow(() -> tree.suggestions("komaedit " + command + " Cl P:\"dirt,stone\" "), command);
        }
    }

    /** Cada camino del árbol hasta 6 palabras, con un valor de ejemplo en cada argumento. */
    private static void walk(CommandNode<ServerCommandSource> node, String path, List<String> prefixes, int depth) {
        prefixes.add(path);
        if (depth >= 6) {
            return;
        }
        for (CommandNode<ServerCommandSource> child : node.getChildren()) {
            String word;
            if (child instanceof LiteralCommandNode<ServerCommandSource> literal) {
                word = literal.getLiteral();
            } else if (child instanceof ArgumentCommandNode<ServerCommandSource, ?> argument) {
                word = argument.getType().getExamples().stream().findFirst().orElse("1");
                if (word.contains(" ")) {
                    continue;
                }
            } else {
                continue;
            }
            if (child.getRedirect() == null) {
                walk(child, path + " " + word, prefixes, depth + 1);
            }
        }
    }
}
