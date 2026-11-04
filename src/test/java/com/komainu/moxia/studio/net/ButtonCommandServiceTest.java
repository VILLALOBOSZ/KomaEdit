package com.komainu.moxia.studio.net;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sk89q.worldedit.internal.expression.Expression;
import net.minecraft.server.command.CommandOutput;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que el servidor deja pasar del paquete de los botones: solo órdenes de KomaEdit, y con un freno que no molesta a
 * quien hace clic deprisa pero corta a quien lo inunda. Los permisos los mira el árbol de órdenes
 * ({@code ButtonCommandPermissionsTest}).
 */
class ButtonCommandServiceTest {
    private static final String TREE = "komaedit tree spruce -v";

    @Test
    void theButtonOnlyRunsKomaEditCommands() {
        UUID player = new UUID(0L, 1L);
        for (String command : List.of("gamemode creative", "op builder", "kill @e", "say hola", "//set tnt")) {
            ButtonCommandService.Admission admission = ButtonCommandService.admit(player, command, 1_000_000_000L);
            assertFalse(admission.run(), command);
            assertTrue(admission.message().startsWith("No se ejecuta: "), admission.message());
        }
    }

    /** El fallo del #74: con unos 11 clics seguidos por el chat, Minecraft echaba a quien no es OP. */
    @Test
    void thirtyClicksInARowAllRun() {
        UUID player = new UUID(0L, 2L);
        long now = 5_000_000_000L;
        for (int click = 0; click < 30; click++) {
            assertEquals(ButtonCommandService.Admission.RUN, ButtonCommandService.admit(player, TREE, now),
                    "clic " + (click + 1));
            now += 40_000_000L;
        }
    }

    @Test
    void aFloodIsCutWithASingleWarning() {
        UUID player = new UUID(0L, 3L);
        long now = 9_000_000_000L;
        assertEquals(new Flood(ButtonCommandService.BURST, 1), flood(player, now));
        // Al rato vuelve a atender lo que da de sí el freno, y otra inundación vuelve a avisar una vez.
        now += 1_000_000_000L;
        assertEquals(new Flood((int) ButtonCommandService.PER_SECOND, 1), flood(player, now));
        // Al irse el jugador se olvida.
        ButtonCommandService.forget(player);
        assertEquals(ButtonCommandService.Admission.RUN, ButtonCommandService.admit(player, TREE, now));
    }

    @Test
    void floodingWithBadCommandsDoesNotFloodTheChatWithErrors() {
        UUID player = new UUID(0L, 4L);
        int messages = 0;
        for (int sent = 0; sent < 1_000; sent++) {
            ButtonCommandService.Admission admission = ButtonCommandService.admit(player, "op builder",
                    3_000_000_000L);
            assertFalse(admission.run());
            if (admission.message() != null) {
                messages++;
            }
        }
        assertEquals(ButtonCommandService.BURST + 1, messages, "un error por orden atendida y un aviso de freno");
    }

    /**
     * Por el chat una orden tiene 256 caracteres; por el paquete, 8192. Una expresión de WorldEdit con 4000 paréntesis
     * (o una máscara con miles de {@code !}) agota la pila al leerse, y en el hilo del servidor eso lo tumbaba: ahora
     * no se ejecuta y se le dice al jugador.
     */
    @Test
    void aCommandTooDeepForTheStackDoesNotTakeTheServerDown() throws Exception {
        String deep = "komaedit expresion " + "(".repeat(4_000) + "x" + ")".repeat(4_000);
        Outcome outcome = runOnSmallStack(deep);
        assertEquals(null, outcome.escaped(), "no se escapa de run()");
        assertFalse(outcome.ran());
        assertEquals(1, outcome.errors().size());
        assertTrue(outcome.errors().getFirst().contains(ButtonCommands.TOO_DEEP), outcome.errors().toString());
    }

    @Test
    void aNormalExpressionStillRuns() throws Exception {
        Outcome outcome = runOnSmallStack("komaedit expresion " + "(".repeat(100) + "x" + ")".repeat(100));
        assertEquals(null, outcome.escaped());
        assertTrue(outcome.ran());
        assertEquals(List.of(), outcome.errors());
    }

    private record Outcome(boolean ran, List<String> errors, Throwable escaped) {
    }

    /** Como en el hilo del servidor: con la pila de un hilo de la JVM (1 MiB), fijada para que no dependa de ella. */
    private static Outcome runOnSmallStack(String command) throws InterruptedException {
        CommandDispatcher<ServerCommandSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("komaedit")
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("expresion")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("texto",
                                        StringArgumentType.greedyString())
                                .executes(context -> {
                                    Expression.compile(StringArgumentType.getString(context, "texto"), "x");
                                    return 1;
                                }))));
        List<String> errors = new ArrayList<>();
        CommandOutput output = new CommandOutput() {
            @Override
            public void sendMessage(Text message) {
                errors.add(message.getString());
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
        };
        ServerCommandSource source = new ServerCommandSource(output, Vec3d.ZERO, Vec2f.ZERO, null, 0, "prueba",
                Text.literal("prueba"), null, null);
        boolean[] ran = {false};
        Throwable[] escaped = {null};
        Thread thread = new Thread(null, () -> {
            try {
                ran[0] = ButtonCommandService.run(dispatcher, source, command, (parse, text) -> {
                    try {
                        dispatcher.execute(parse);
                    } catch (CommandSyntaxException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
            } catch (Throwable throwable) {
                escaped[0] = throwable;
            }
        }, "servidor de prueba", 1024 * 1024);
        thread.start();
        thread.join();
        return new Outcome(ran[0], errors, escaped[0]);
    }

    private record Flood(int ran, int warnings) {
    }

    private static Flood flood(UUID player, long now) {
        int ran = 0;
        int warnings = 0;
        for (int sent = 0; sent < 1_000; sent++) {
            ButtonCommandService.Admission admission = ButtonCommandService.admit(player, TREE, now);
            if (admission.run()) {
                ran++;
            } else if (admission.message() != null) {
                assertEquals(ButtonCommandService.TOO_FAST, admission.message());
                warnings++;
            }
        }
        return new Flood(ran, warnings);
    }
}
