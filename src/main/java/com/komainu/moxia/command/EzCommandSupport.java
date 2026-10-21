package com.komainu.moxia.command;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEditException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Mensajes y utilidades comunes de las órdenes de paletas, ruidos y selecciones. */
final class EzCommandSupport {
    private EzCommandSupport() {
    }

    @FunctionalInterface
    interface PlayerAction {
        int run(ServerPlayerEntity player) throws Exception;
    }

    /** Ejecuta una acción de jugador y convierte los errores en un mensaje rojo. */
    static int run(CommandContext<ServerCommandSource> context, PlayerAction action) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            return action.run(player);
        } catch (IncompleteRegionException exception) {
            return noSelection(context.getSource());
        } catch (CommandSyntaxException exception) {
            throw exception;
        } catch (Exception exception) {
            return fail(context.getSource(), reason(exception));
        }
    }

    /**
     * Lo que se le dice al jugador de un error: el texto de los errores de uso ({@link IllegalArgumentException},
     * {@link IllegalStateException} y los de WorldEdit, que ya dicen qué falta), también cuando llegan de otro hilo
     * envueltos en otra excepción. De un fallo inesperado (un número que no cabe, un índice, un nulo…) nunca el texto
     * de Java ni el nombre de la clase: una frase que lo dice, y el detalle al registro del servidor.
     */
    static String reason(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && (current instanceof CompletionException
                || current instanceof ExecutionException || blank(current.getMessage()))) {
            current = current.getCause();
        }
        boolean usage = current instanceof IllegalArgumentException && !(current instanceof NumberFormatException)
                || current instanceof IllegalStateException || current instanceof WorldEditException;
        if (usage && !blank(current.getMessage())) {
            return current.getMessage();
        }
        KomaEditMod.LOGGER.error("Unexpected failure in a KomaEdit command", throwable);
        return "Fallo inesperado de KomaEdit: avisa a un admin (el detalle está en el registro del servidor)";
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    static boolean can(ServerCommandSource source, String permission) {
        ServerPlayerEntity player = source.getPlayer();
        return player == null ? source.hasPermissionLevel(2) : KomaPermissions.can(player, permission);
    }

    /** ¿Puede usar alguna orden de la familia? Para mostrar u ocultar una orden con subcomandos. */
    static boolean canAny(ServerCommandSource source, String prefix) {
        ServerPlayerEntity player = source.getPlayer();
        return player == null ? source.hasPermissionLevel(2) : KomaPermissions.canAny(player, prefix);
    }

    static java.util.function.Predicate<ServerCommandSource> node(String permission) {
        return source -> can(source, permission);
    }

    static MutableText prefix() {
        return Feedback.prefix();
    }

    static void send(ServerPlayerEntity player, Text text) {
        player.sendMessage(prefix().append(text), false);
    }

    static void ok(ServerPlayerEntity player, String message) {
        send(player, Text.literal(message).formatted(Formatting.GREEN));
    }

    /**
     * Aviso de «N bloques en cola»: efímero, en la action bar, y como los informativos no sale con
     * {@code /komaedit quiet} (el de «completada» sale siempre, en el chat).
     */
    static void queued(ServerPlayerEntity player, String message) {
        QuietMode.status(player, prefix().append(Text.literal(message).formatted(Formatting.GREEN)));
    }

    /** «Calculando…» y demás estados de paso: en la action bar, y no salen con {@code /komaedit quiet}. */
    static void status(ServerPlayerEntity player, String message) {
        QuietMode.status(player, prefix().append(Text.literal(message).formatted(Formatting.GRAY)));
    }

    /** Mensaje informativo: no sale si el jugador activó {@code /komaedit quiet}. */
    static void info(ServerPlayerEntity player, String message) {
        QuietMode.info(player, prefix().append(Text.literal(message).formatted(Formatting.GRAY)));
    }

    /**
     * Error de una orden, en rojo y con sonido de fallo. Si es un «desconocido» con propuesta («helx» → «helix»),
     * lleva además un botón que escribe la orden corregida en el chat.
     */
    static int fail(ServerCommandSource source, String message) {
        String text = message == null || message.isBlank() ? "Operación inválida" : CommandHints.enrich(message);
        MutableText body = Text.literal(text);
        EditRequest request = EditRequests.current();
        CommandHints.suggestion(message)
                .flatMap(found -> CommandHints.corrected(request == null ? null : request.command(), found))
                .ifPresent(fixed -> body.append(" ").append(Feedback.fix("[corregir]", fixed)));
        // Una selección que pasa del límite: los trozos que se acaban de preparar, con un botón cada uno.
        if (message != null && message.contains("/komaedit trozo 1") && source.getPlayer() != null) {
            JobCommands.partButtons(source.getPlayer()).ifPresent(buttons -> body.append(" ").append(buttons));
        }
        return Feedback.fail(source, body);
    }

    /** Sin selección de WorldEdit: qué falta y cómo hacerla. */
    static int noSelection(ServerCommandSource source) {
        return Feedback.fail(source, Feedback.problem("No hay selección de WorldEdit",
                "marca dos esquinas con //pos1 y //pos2 o con la varita").append(" ")
                .append(Feedback.fix("[//wand]", "//wand")));
    }

    /** Texto que se copia al portapapeles al pulsarlo. */
    static MutableText copyable(String label, String copied, Formatting color) {
        return Text.literal(label).styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copied))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Pulsa para copiar"))));
    }

    /** Texto que ejecuta una orden al pulsarlo. */
    static MutableText button(String label, String command, Formatting color) {
        return Text.literal(label).styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command))));
    }

    static SuggestionProvider<ServerCommandSource> words(Collection<String> words) {
        return (context, builder) -> {
            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (String word : words) {
                if (word.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                    builder.suggest(word);
                }
            }
            return builder.buildFuture();
        };
    }
}
