package com.komainu.moxia.command;

import com.komainu.moxia.permission.KomaPermissions;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.sk89q.worldedit.IncompleteRegionException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collection;
import java.util.Locale;

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
            return fail(context.getSource(), "Primero haz una selección con WorldEdit (//pos1 y //pos2)");
        } catch (CommandSyntaxException exception) {
            throw exception;
        } catch (Exception exception) {
            String message = exception.getMessage();
            return fail(context.getSource(), message == null || message.isBlank()
                    ? exception.getClass().getSimpleName().toLowerCase(Locale.ROOT) : message);
        }
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
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD);
    }

    static void send(ServerPlayerEntity player, Text text) {
        player.sendMessage(prefix().append(text), false);
    }

    static void ok(ServerPlayerEntity player, String message) {
        send(player, Text.literal(message).formatted(Formatting.GREEN));
    }

    static void info(ServerPlayerEntity player, String message) {
        send(player, Text.literal(message).formatted(Formatting.GRAY));
    }

    static int fail(ServerCommandSource source, String message) {
        source.sendError(prefix().append(Text.literal(message).formatted(Formatting.RED)));
        return 0;
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
