package com.komainu.moxia.worldedit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code //undo} con una edición de KomaEdit colocándose deshacía la anterior y, al acabar, la que se colocaba entraba
 * en el historial y borraba lo que se podía rehacer: la anterior se perdía. Ahora espera, con el motivo.
 */
class UndoGuardTest {
    private static final UUID BUILDER = UUID.fromString("0f8e4a1c-2b3d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final UUID OTHER = UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");

    /** Quien manda la orden ({@code null}: la consola) y lo que se le dice. */
    private record Source(UUID player, List<String> told) {
        Source(UUID player) {
            this(player, new ArrayList<>());
        }
    }

    /** El historial de un jugador como el de WorldEdit ({@code LocalSession}): recordar borra lo que se podía rehacer. */
    private static final class History {
        final List<String> edits = new ArrayList<>();
        final List<String> world = new ArrayList<>();
        int pointer;

        void remember(String edit) {
            while (pointer < edits.size()) {
                edits.remove(pointer);
            }
            edits.add(edit);
            pointer = edits.size();
        }

        int undo() {
            if (pointer == 0) {
                return 0;
            }
            world.remove(edits.get(--pointer));
            return 1;
        }

        int redo() {
            if (pointer == edits.size()) {
                return 0;
            }
            world.add(edits.get(pointer++));
            return 1;
        }
    }

    /** Las órdenes como las registra WorldEdit en Fabric: el literal y un argumento con el resto. */
    private static CommandDispatcher<Source> worldEdit(Map<UUID, History> histories) {
        CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();
        for (String name : List.of("undo", "/undo", "redo", "/redo")) {
            boolean redo = name.endsWith("redo");
            com.mojang.brigadier.Command<Source> command = context -> {
                History history = histories.get(context.getSource().player());
                return redo ? history.redo() : history.undo();
            };
            dispatcher.register(LiteralArgumentBuilder.<Source>literal(name).executes(command)
                    .then(RequiredArgumentBuilder.<Source, String>argument("args", StringArgumentType.greedyString())
                            .executes(command)));
        }
        return dispatcher;
    }

    @Test
    void undoWaitsWhileAnEditIsStillBeingPlacedAndNothingIsLost() throws CommandSyntaxException {
        Map<UUID, History> histories = new HashMap<>();
        History history = new History();
        histories.put(BUILDER, history);
        String[] placing = {null};
        UndoGuard guard = new UndoGuard();
        guard.blockWhile(player -> player.equals(BUILDER) ? placing[0] : null);
        CommandDispatcher<Source> dispatcher = worldEdit(histories);
        assertEquals(8, UndoGuard.install(dispatcher.getRoot(), Source::player,
                (source, reason) -> source.told().add(reason), guard), "las cuatro órdenes y su argumento");

        // Una montaña ya hecha y un relleno de agua que aún se coloca.
        history.world.add("montaña");
        history.remember("montaña");
        placing[0] = "Relleno de agua";
        history.world.add("relleno de agua");
        Source builder = new Source(BUILDER);
        for (String typed : List.of("/undo", "undo", "/undo 1", "undo 2")) {
            assertEquals(0, dispatcher.execute(typed, builder), typed);
        }
        assertEquals(List.of("montaña", "relleno de agua"), history.world, "la montaña sigue ahí");
        assertEquals(4, builder.told().size());
        assertTrue(builder.told().getFirst().contains("Relleno de agua"), builder.told().getFirst());
        assertTrue(builder.told().getFirst().contains("/komaedit cancel"));

        // Acaba: entra en el historial y //undo deshace justo esa, y la montaña se puede deshacer después.
        placing[0] = null;
        history.remember("relleno de agua");
        assertEquals(1, dispatcher.execute("/undo", builder));
        assertEquals(List.of("montaña"), history.world);
        assertEquals(1, dispatcher.execute("redo", builder));
        assertEquals(List.of("montaña", "relleno de agua"), history.world, "y se puede rehacer");
    }

    @Test
    void redoWaitsToo() throws CommandSyntaxException {
        Map<UUID, History> histories = new HashMap<>();
        History history = new History();
        histories.put(BUILDER, history);
        history.world.add("muro");
        history.remember("muro");
        history.undo();
        UndoGuard guard = new UndoGuard();
        guard.blockWhile(player -> "Reconstrucción de la selección");
        CommandDispatcher<Source> dispatcher = worldEdit(histories);
        UndoGuard.install(dispatcher.getRoot(), Source::player, (source, reason) -> source.told().add(reason), guard);
        Source builder = new Source(BUILDER);
        assertEquals(0, dispatcher.execute("/redo", builder));
        assertEquals(List.of(), history.world);
        assertTrue(builder.told().getFirst().contains("rehacer"));
    }

    @Test
    void othersAndTheConsoleUndoAsAlways() throws CommandSyntaxException {
        Map<UUID, History> histories = new HashMap<>();
        History other = new History();
        histories.put(OTHER, other);
        histories.put(null, new History());
        other.world.add("casa");
        other.remember("casa");
        UndoGuard guard = new UndoGuard();
        guard.blockWhile(player -> BUILDER.equals(player) ? "Relleno de agua" : null);
        CommandDispatcher<Source> dispatcher = worldEdit(histories);
        UndoGuard.install(dispatcher.getRoot(), Source::player, (source, reason) -> source.told().add(reason), guard);
        assertEquals(1, dispatcher.execute("/undo", new Source(OTHER)), "lo del otro jugador no le hace esperar");
        assertEquals(List.of(), other.world);
        assertEquals(0, dispatcher.execute("/undo", new Source(null)), "la consola, sin historial, como siempre");
    }

    /** Un trazo de pintar se cierra antes: entra en el historial y //undo deshace justo ese trazo. */
    @Test
    void aPaintStrokeIsClosedFirstSoUndoTakesIt() throws CommandSyntaxException {
        Map<UUID, History> histories = new HashMap<>();
        History history = new History();
        histories.put(BUILDER, history);
        history.world.add("trazo");
        List<UUID> closed = new ArrayList<>();
        UndoGuard guard = new UndoGuard();
        guard.closeFirst(player -> {
            closed.add(player);
            history.remember("trazo");
        });
        CommandDispatcher<Source> dispatcher = worldEdit(histories);
        UndoGuard.install(dispatcher.getRoot(), Source::player, (source, reason) -> source.told().add(reason), guard);
        assertEquals(1, dispatcher.execute("/undo", new Source(BUILDER)));
        assertEquals(List.of(BUILDER), closed);
        assertEquals(List.of(), history.world);

        guard.blockWhile(player -> "Relleno de agua");
        dispatcher.execute("/undo", new Source(BUILDER));
        assertEquals(1, closed.size(), "si se niega, el trazo sigue abierto");
    }

    @Test
    void withoutWorldEditThereIsNothingToGuard() {
        assertEquals(0, UndoGuard.install(new CommandDispatcher<Source>().getRoot(), Source::player,
                (source, reason) -> { }, new UndoGuard()));
    }
}
