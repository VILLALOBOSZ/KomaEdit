package com.komainu.moxia.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

/**
 * El árbol entero de {@code /komaedit} sin el juego y con los permisos abiertos, para leer órdenes como las lee
 * Brigadier: lo que el jugador puede escribir, hasta qué nodo llega y qué le sugiere el autocompletado. Sin jugador no
 * se pueden ejecutar (cada orden pide uno), así que lo que hacen se prueba con sus analizadores.
 */
final class OpenCommandTree {
    final CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
    /** Una fuente sin servidor ni jugador: basta para leer y para las sugerencias que no miran el mundo. */
    final ServerCommandSource source = new ServerCommandSource(CommandOutput.DUMMY, Vec3d.ZERO, Vec2f.ZERO, null, 4,
            "prueba", Text.literal("prueba"), null, null);

    OpenCommandTree() {
        MoxiaCommands.register(dispatcher);
        try {
            Field requirement = CommandNode.class.getDeclaredField("requirement");
            requirement.setAccessible(true);
            open(dispatcher.getRoot(), requirement, Collections.newSetFromMap(new IdentityHashMap<>()));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("No se pudieron abrir los permisos del árbol", exception);
        }
    }

    /**
     * @param complete si además tiene que llegar a un nodo que ejecuta algo (un ejemplo entero, no solo el comienzo
     *                 de una orden)
     * @return {@code null} si Brigadier la lee entera, o por qué no
     */
    String problem(String command, boolean complete) {
        ParseResults<ServerCommandSource> parse = dispatcher.parse(command, source);
        if (parse.getReader().canRead()) {
            return "no se lee desde «" + parse.getReader().getRemaining() + "» "
                    + parse.getExceptions().values().stream().map(Exception::getMessage).toList();
        }
        CommandContextBuilder<ServerCommandSource> context = parse.getContext();
        while (context.getChild() != null) {
            context = context.getChild();
        }
        return complete && context.getCommand() == null ? "no llega a ninguna orden" : null;
    }

    Suggestions suggestions(String input) throws Exception {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).get();
    }

    private static void open(CommandNode<ServerCommandSource> node, Field requirement,
                             Set<CommandNode<ServerCommandSource>> seen) throws ReflectiveOperationException {
        if (!seen.add(node)) {
            return;
        }
        requirement.set(node, (Predicate<ServerCommandSource>) source -> true);
        for (CommandNode<ServerCommandSource> child : node.getChildren()) {
            open(child, requirement, seen);
        }
        if (node.getRedirect() != null) {
            open(node.getRedirect(), requirement, seen);
        }
    }
}
