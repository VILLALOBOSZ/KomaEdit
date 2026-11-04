package com.komainu.moxia.worldedit;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@code //undo} y {@code //redo} con una edición de KomaEdit a medias. Las ediciones largas (la cola, la
 * reconstrucción, los biomas) escriben en una {@code EditSession} que solo entra en el historial de WorldEdit al
 * acabar. Si mientras tanto el jugador hace {@code //undo} (o pulsa deshacer en el Studio, pensando en la que se está
 * colocando), WorldEdit deshace la anterior; al acabar, la nueva entra en el historial y WorldEdit borra lo que se
 * podía rehacer: la anterior ya no vuelve. Y el mundo queda mezclado, con lo deshecho bajo lo que se sigue colocando.
 *
 * <p>Por eso, mientras una edición así coloca (o espera en la cola), deshacer y rehacer se niegan con el motivo. Los
 * trazos de pintar (que duran lo que se mantiene el botón) se cierran antes: van al historial y {@code //undo} deshace
 * justo ese trazo. Java puro: las fuentes llegan registradas y el árbol de órdenes es de Brigadier.</p>
 */
public final class UndoGuard {
    /** Las órdenes de WorldEdit que deshacen, con una barra y con dos. */
    public static final List<String> UNDO_COMMANDS = List.of("undo", "/undo");
    /** Y las que rehacen. */
    public static final List<String> REDO_COMMANDS = List.of("redo", "/redo");
    /** El del juego: los servicios se apuntan al arrancar ({@code KomaEditMod}). */
    public static final UndoGuard GAME = new UndoGuard();

    /** Una edición que aún escribe en el mundo y todavía no está en el historial. */
    @FunctionalInterface
    public interface Placing {
        /** @return su nombre («Relleno de agua»), o {@code null} si el jugador no tiene ninguna */
        String placing(UUID player);
    }

    private final List<Placing> placing = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> strokes = new CopyOnWriteArrayList<>();

    /** Deshacer y rehacer esperan mientras {@code source} diga que el jugador coloca algo. */
    public void blockWhile(Placing source) {
        placing.add(source);
    }

    /** Un trazo abierto se cierra (y va al historial) antes de deshacer o rehacer. */
    public void closeFirst(Consumer<UUID> stroke) {
        strokes.add(stroke);
    }

    /**
     * Justo antes de deshacer o rehacer: el motivo para no hacerlo o, si se puede, vacío (con los trazos ya cerrados).
     */
    public Optional<String> before(UUID player, boolean redo) {
        for (Placing source : placing) {
            String name = source.placing(player);
            if (name != null) {
                return Optional.of(message(name, redo));
            }
        }
        for (Consumer<UUID> stroke : strokes) {
            stroke.accept(player);
        }
        return Optional.empty();
    }

    static String message(String name, boolean redo) {
        if (redo) {
            return "«" + name + "» aún se está colocando: espera a que acabe para rehacer, o párala con /komaedit"
                    + " cancel";
        }
        return "«" + name + "» aún se está colocando y no está en el historial: deshacer quitaría la edición de antes"
                + " (y al acabar esta ya no se podría rehacer). Espera a que acabe o párala con /komaedit cancel;"
                + " después, //undo la deshace";
    }

    /**
     * Pone la comprobación delante de las órdenes de deshacer y rehacer de WorldEdit que haya en {@code root} (la
     * orden y su argumento). Hay que llamarlo después de que WorldEdit registre las suyas.
     *
     * @param player quién la manda ({@code null} si no es un jugador: la consola deshace como siempre)
     * @param refuse cómo se le dice que no
     * @return cuántos nodos quedaron vigilados
     */
    public static <S> int install(CommandNode<S> root, Function<S, UUID> player, BiConsumer<S, String> refuse,
                                  UndoGuard guard) {
        int guarded = 0;
        for (String name : UNDO_COMMANDS) {
            guarded += guard(root, name, false, player, refuse, guard);
        }
        for (String name : REDO_COMMANDS) {
            guarded += guard(root, name, true, player, refuse, guard);
        }
        return guarded;
    }

    private static <S> int guard(CommandNode<S> root, String name, boolean redo, Function<S, UUID> player,
                                 BiConsumer<S, String> refuse, UndoGuard guard) {
        CommandNode<S> node = root.getChild(name);
        if (node == null) {
            return 0;
        }
        int guarded = 0;
        // addChild con el mismo nombre fusiona: solo sustituye la orden del nodo que ya hay (y deja sus hijos).
        if (node.getCommand() != null) {
            root.addChild(LiteralArgumentBuilder.<S>literal(name)
                    .executes(guarded(node.getCommand(), redo, player, refuse, guard)).build());
            guarded++;
        }
        for (CommandNode<S> child : List.copyOf(node.getChildren())) {
            if (child.getCommand() != null && child instanceof ArgumentCommandNode<S, ?> argument) {
                @SuppressWarnings("unchecked")
                ArgumentType<Object> type = (ArgumentType<Object>) argument.getType();
                node.addChild(RequiredArgumentBuilder.<S, Object>argument(argument.getName(), type)
                        .executes(guarded(child.getCommand(), redo, player, refuse, guard)).build());
                guarded++;
            }
        }
        return guarded;
    }

    private static <S> Command<S> guarded(Command<S> original, boolean redo, Function<S, UUID> player,
                                          BiConsumer<S, String> refuse, UndoGuard guard) {
        return context -> {
            UUID id = player.apply(context.getSource());
            if (id != null) {
                Optional<String> refusal = guard.before(id, redo);
                if (refusal.isPresent()) {
                    refuse.accept(context.getSource(), refusal.get());
                    return 0;
                }
            }
            return original.run(context);
        };
    }
}
