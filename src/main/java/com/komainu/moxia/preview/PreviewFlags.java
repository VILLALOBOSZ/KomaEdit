package com.komainu.moxia.preview;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * La bandera {@code -v} (o {@code --preview}) de todas las órdenes de KomaEdit, sin tocar los analizadores de cada
 * una. {@link #install} recorre el árbol de Brigadier una vez registradas las órdenes:
 *
 * <ul>
 *     <li>A cada nodo ejecutable le cuelga los literales {@code -v} y {@code --preview}, que ejecutan la misma orden
 *     pidiendo vista previa (van al final: {@code build sphere stone 8 -v}).</li>
 *     <li>En los nodos de texto libre ({@code greedyString}) la bandera va dentro del texto, en cualquier sitio, como
 *     las de ezEdits: se quita del texto y la orden se vuelve a ejecutar sin ella.</li>
 *     <li>Toda orden envuelta corre con su {@link EditRequest}, para que la preferencia «previsualizar siempre» sepa
 *     que la edición viene de una orden y no de un pincel.</li>
 * </ul>
 *
 * Donde ezEdits ya usa {@code -v} con otro sentido (por ejemplo {@code deform noise -v}, vertical), solo vale
 * {@code --preview}: lo decide {@link Hooks#shortFlagTaken}.
 */
public final class PreviewFlags {
    public static final String SHORT = "-v";
    public static final String LONG = "--preview";

    private PreviewFlags() {
    }

    /** Texto sin la bandera y si estaba. */
    public record Stripped(String text, boolean found) {
    }

    /** Lo que la instalación necesita saber de las órdenes del mod. */
    public interface Hooks<S> {
        /** Jugador que ejecuta, o {@code null} (consola, bloque de comandos). */
        UUID player(S source);

        /**
         * Si la orden de esta ruta admite vista previa. La ruta son los nombres de los nodos bajo la raíz; los
         * argumentos aparecen como {@code <nombre>}.
         */
        boolean eligible(List<String> path);

        /**
         * Si la orden ya usa {@code -v} con otro sentido. La ruta es la escrita (con el valor de cada argumento), en
         * minúsculas.
         */
        boolean shortFlagTaken(List<String> path);
    }

    /** Quita las apariciones sueltas de {@code --preview} (y de {@code -v} si {@code allowShort}). */
    public static Stripped strip(String text, boolean allowShort) {
        if (text == null || text.isEmpty()) {
            return new Stripped(text == null ? "" : text, false);
        }
        StringBuilder out = new StringBuilder(text.length());
        String gap = "";
        boolean found = false;
        int index = 0;
        int length = text.length();
        while (index < length) {
            int start = index;
            if (Character.isWhitespace(text.charAt(index))) {
                while (index < length && Character.isWhitespace(text.charAt(index))) {
                    index++;
                }
                gap = text.substring(start, index);
                continue;
            }
            while (index < length && !Character.isWhitespace(text.charAt(index))) {
                index++;
            }
            String token = text.substring(start, index);
            if (isFlag(token, allowShort)) {
                found = true;
                continue;
            }
            if (!out.isEmpty()) {
                out.append(gap);
            }
            out.append(token);
            gap = "";
        }
        return new Stripped(found ? out.toString() : text, found);
    }

    public static boolean isFlag(String token, boolean allowShort) {
        return LONG.equalsIgnoreCase(token) || (allowShort && SHORT.equals(token));
    }

    /**
     * Engancha la bandera en el árbol de {@code root}. Las órdenes que se registren después no la tendrán: llamar al
     * final del registro.
     *
     * @return cuántos nodos ejecutables quedaron envueltos
     */
    public static <S> int install(CommandDispatcher<S> dispatcher, CommandNode<S> root, Hooks<S> hooks) {
        Set<CommandNode<S>> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int[] count = {0};
        for (CommandNode<S> child : List.copyOf(root.getChildren())) {
            walk(dispatcher, root, root, child, List.of(), hooks, visited, count);
        }
        return count[0];
    }

    private static <S> void walk(
            CommandDispatcher<S> dispatcher,
            CommandNode<S> root,
            CommandNode<S> parent,
            CommandNode<S> node,
            List<String> parentPath,
            Hooks<S> hooks,
            Set<CommandNode<S>> visited,
            int[] count
    ) {
        if (!visited.add(node) || node.getRedirect() != null || isFlag(node.getName(), true)) {
            return;
        }
        List<String> path = new ArrayList<>(parentPath);
        path.add(node instanceof LiteralCommandNode<S> literal
                ? literal.getLiteral().toLowerCase(Locale.ROOT)
                : "<" + node.getName() + ">");
        List<CommandNode<S>> children = List.copyOf(node.getChildren());
        Command<S> original = node.getCommand();
        if (original != null && hooks.eligible(List.copyOf(path))) {
            if (isGreedy(node)) {
                // addChild con el mismo nombre fusiona: solo sustituye la orden del nodo existente.
                parent.addChild(copy(node, greedy(dispatcher, root, node.getName(), original, hooks)));
            } else {
                parent.addChild(copy(node, plain(original, hooks)));
                // Con un hijo de texto libre, la bandera la recoge ese hijo: un literal «-v» al lado le quitaría
                // la primera palabra («texture -v rockify» ya no entraría en el texto).
                if (children.stream().noneMatch(PreviewFlags::isGreedy)) {
                    Command<S> flagged = flagged(original, hooks);
                    node.addChild(LiteralArgumentBuilder.<S>literal(SHORT).executes(flagged).build());
                    node.addChild(LiteralArgumentBuilder.<S>literal(LONG).executes(flagged).build());
                }
            }
            count[0]++;
        }
        for (CommandNode<S> child : children) {
            walk(dispatcher, root, node, child, path, hooks, visited, count);
        }
    }

    private static <S> Command<S> plain(Command<S> original, Hooks<S> hooks) {
        return context -> run(hooks.player(context.getSource()), EditRequest.command(context.getInput(), false),
                () -> original.run(context));
    }

    private static <S> Command<S> flagged(Command<S> original, Hooks<S> hooks) {
        return context -> run(hooks.player(context.getSource()),
                EditRequest.command(dropLastToken(context.getInput()), true), () -> original.run(context));
    }

    private static <S> Command<S> greedy(
            CommandDispatcher<S> dispatcher,
            CommandNode<S> root,
            String argument,
            Command<S> original,
            Hooks<S> hooks
    ) {
        return context -> {
            UUID player = hooks.player(context.getSource());
            String value = context.getArgument(argument, String.class);
            Stripped stripped = strip(value, !hooks.shortFlagTaken(runtimePath(context, root)));
            StringRange range = rangeOf(context, argument);
            if (!stripped.found() || range == null) {
                return run(player, EditRequest.command(context.getInput(), false), () -> original.run(context));
            }
            String input = context.getInput();
            String rewritten = (input.substring(0, range.getStart()) + stripped.text()
                    + input.substring(range.getEnd())).strip();
            return run(player, EditRequest.command(rewritten, true),
                    () -> dispatcher.execute(rewritten, context.getSource()));
        };
    }

    private static int run(UUID player, EditRequest request, EditRequests.CommandBody body)
            throws CommandSyntaxException {
        try {
            return EditRequests.withinCommand(request, player, body);
        } catch (CommandSyntaxException | RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <S> CommandNode<S> copy(CommandNode<S> node, Command<S> command) {
        if (node instanceof LiteralCommandNode<S> literal) {
            return LiteralArgumentBuilder.<S>literal(literal.getLiteral()).executes(command).build();
        }
        ArgumentCommandNode<S, ?> argument = (ArgumentCommandNode<S, ?>) node;
        return RequiredArgumentBuilder.<S, Object>argument(argument.getName(),
                (ArgumentType<Object>) argument.getType()).executes(command).build();
    }

    private static boolean isGreedy(CommandNode<?> node) {
        return node instanceof ArgumentCommandNode<?, ?> argument
                && argument.getType() instanceof StringArgumentType type
                && type.getType() == StringArgumentType.StringType.GREEDY_PHRASE;
    }

    /** Ruta escrita sin la raíz: literales y valores de los argumentos, en minúsculas. */
    static <S> List<String> runtimePath(CommandContext<S> context, CommandNode<S> root) {
        List<String> path = new ArrayList<>();
        for (ParsedCommandNode<S> parsed : context.getNodes()) {
            CommandNode<S> node = parsed.getNode();
            if (node == root) {
                continue;
            }
            String text = node instanceof LiteralCommandNode<S> literal
                    ? literal.getLiteral()
                    : parsed.getRange().get(context.getInput());
            for (String word : text.strip().split("\\s+")) {
                if (!word.isEmpty()) {
                    path.add(word.toLowerCase(Locale.ROOT));
                }
            }
        }
        return path;
    }

    private static <S> StringRange rangeOf(CommandContext<S> context, String argument) {
        StringRange found = null;
        for (ParsedCommandNode<S> parsed : context.getNodes()) {
            if (parsed.getNode() instanceof ArgumentCommandNode<?, ?> && parsed.getNode().getName().equals(argument)) {
                found = parsed.getRange();
            }
        }
        return found;
    }

    static String dropLastToken(String input) {
        String trimmed = input.strip();
        int space = trimmed.length() - 1;
        while (space >= 0 && !Character.isWhitespace(trimmed.charAt(space))) {
            space--;
        }
        return space < 0 ? "" : trimmed.substring(0, space).strip();
    }
}
