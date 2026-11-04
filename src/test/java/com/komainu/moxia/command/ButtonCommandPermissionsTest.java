package com.komainu.moxia.command;

import com.komainu.moxia.studio.net.ButtonCommandService;
import com.komainu.moxia.studio.net.ButtonCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El paquete de los botones no es una puerta trasera: lo que la orden escrita no deja, el botón tampoco, y con la misma
 * negativa; lo que deja, el botón también.
 *
 * <p>La orden de chat se toma como la trata Minecraft: {@code ServerPlayNetworkHandler.parse} la lee con la fuente del
 * jugador y {@code CommandManager.execute} empieza por {@code checkCommand}, que contesta los errores de lectura (aquí
 * se llama al de verdad, por reflexión). Sin servidor no se puede ejecutar más allá; lo que importa es que el botón
 * llega a ese mismo punto con la misma lectura y la misma fuente, sin abrir nada. Los permisos son los del árbol real
 * de {@code /komaedit}: sin jugador detrás (en una prueba no hay), quien no es jugador no tiene acceso a KomaEdit y el
 * nivel 2 da los permisos finos ({@link EzCommandSupport#can}).</p>
 */
class ButtonCommandPermissionsTest {
    private static final String TREE = "komaedit tree spruce -v";
    private static Method checkCommand;
    /** Todo abierto salvo {@code tree}, que pide su permiso de verdad: quien usa KomaEdit pero no planta árboles. */
    private static CommandDispatcher<ServerCommandSource> withoutTree;
    /** Tal cual. */
    private static CommandDispatcher<ServerCommandSource> untouched;

    @BeforeAll
    static void commandTrees() throws ReflectiveOperationException {
        checkCommand = CommandManager.class.getDeclaredMethod("checkCommand", ParseResults.class, String.class,
                ServerCommandSource.class);
        checkCommand.setAccessible(true);
        untouched = new CommandDispatcher<>();
        MoxiaCommands.register(untouched);
        withoutTree = new CommandDispatcher<>();
        MoxiaCommands.register(withoutTree);
        CommandNode<ServerCommandSource> tree = withoutTree.getRoot().getChild("komaedit").getChild("tree");
        assertNotNull(tree, "la orden del árbol cuelga de /komaedit");
        Field requirement = CommandNode.class.getDeclaredField("requirement");
        requirement.setAccessible(true);
        open(withoutTree.getRoot(), tree, requirement, new IdentityHashMap<>());
    }

    /** Todo nodo se puede usar menos {@code keep} (y lo que cuelga de él), que sigue pidiendo su permiso. */
    private static void open(CommandNode<ServerCommandSource> node, CommandNode<ServerCommandSource> keep,
                             Field requirement, Map<CommandNode<?>, Boolean> seen) throws IllegalAccessException {
        if (node == keep || seen.put(node, true) != null) {
            return;
        }
        Predicate<ServerCommandSource> anyone = source -> true;
        requirement.set(node, anyone);
        for (CommandNode<ServerCommandSource> child : node.getChildren()) {
            open(child, keep, requirement, seen);
        }
    }

    @Test
    void withoutThePermissionTheButtonGetsTheSameRefusalAsTheCommand() {
        Outcome typed = typed(withoutTree, TREE, 0);
        assertFalse(typed.reached(), "la orden escrita se rechaza");
        assertFalse(typed.messages().isEmpty(), "y se le dice al jugador");
        assertEquals(typed, button(withoutTree, TREE, 0), "el botón, igual: ni llega a la orden ni dice otra cosa");
    }

    @Test
    void withoutKomaEditAtAllTheButtonGetsTheSameRefusalAsTheCommand() {
        for (int level : new int[]{0, 4}) {
            Outcome typed = typed(untouched, TREE, level);
            assertFalse(typed.reached(), "nivel " + level);
            assertFalse(typed.messages().isEmpty());
            assertEquals(typed, button(untouched, TREE, level), "nivel " + level);
        }
    }

    @Test
    void withThePermissionTheButtonReachesTheSameCommand() {
        Outcome typed = typed(withoutTree, TREE, 2);
        assertTrue(typed.reached());
        assertEquals(List.of(), typed.messages());
        assertEquals(typed, button(withoutTree, TREE, 2), "el botón no niega lo que la orden deja");
    }

    /** Hasta dónde llegó la orden y qué se le dijo al jugador. */
    private record Outcome(boolean reached, List<Text> messages) {
    }

    /** La orden escrita en el chat, como la lee Minecraft. */
    private static Outcome typed(CommandDispatcher<ServerCommandSource> dispatcher, String command, int level) {
        Output output = new Output();
        ServerCommandSource source = source(output, level);
        ParseResults<ServerCommandSource> parse = dispatcher.parse(command, source);
        return new Outcome(check(parse, command, source) != null, output.messages);
    }

    /** La misma orden desde un botón: lo que hace el servidor con el paquete. */
    private static Outcome button(CommandDispatcher<ServerCommandSource> dispatcher, String command, int level) {
        assertTrue(ButtonCommands.problem(command).isEmpty(), "el paquete la lleva: lo que decide es el árbol");
        Output output = new Output();
        ServerCommandSource source = source(output, level);
        AtomicReference<Object> chain = new AtomicReference<>();
        boolean ran = ButtonCommandService.run(dispatcher, source, command, (parse, line) -> {
            // Lo que hace CommandManager.execute, con la fuente que trae la lectura.
            assertSame(source, parse.getContext().getSource(), "con la fuente del jugador, sin cambiarla");
            chain.set(check(parse, line, parse.getContext().getSource()));
        });
        assertTrue(ran);
        return new Outcome(chain.get() != null, output.messages);
    }

    private static Object check(ParseResults<ServerCommandSource> parse, String command, ServerCommandSource source) {
        try {
            return checkCommand.invoke(null, parse, command, source);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ServerCommandSource source(Output output, int level) {
        return new ServerCommandSource(output, Vec3d.ZERO, Vec2f.ZERO, null, level, "builder",
                Text.literal("builder"), null, null);
    }

    private static final class Output implements CommandOutput {
        final List<Text> messages = new ArrayList<>();

        @Override
        public void sendMessage(Text message) {
            messages.add(message);
        }

        @Override
        public boolean shouldReceiveFeedback() {
            return true;
        }

        @Override
        public boolean shouldTrackOutput() {
            return true;
        }

        @Override
        public boolean shouldBroadcastConsoleToOps() {
            return false;
        }
    }
}
