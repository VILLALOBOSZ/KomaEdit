package com.komainu.moxia.client.utility;

import com.komainu.moxia.ruler.RulerMode;
import com.komainu.moxia.views.SavedView;
import com.komainu.moxia.views.ViewNames;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Las utilidades del cliente (vistas, regla y notas) y sus órdenes bajo {@code /kegui}: {@code vista},
 * {@code vistas}, {@code regla} y {@code notas}.
 */
public final class UtilitiesClient {
    private UtilitiesClient() {
    }

    public static void register() {
        RulerClient.register();
        ClientViews.register();
        ClientNotes.register();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("kegui")
                        .then(literal("vistas").executes(context -> open(ViewsScreen::new)))
                        .then(views("vista"))
                        .then(views("view"))
                        .then(ruler("regla"))
                        .then(ruler("ruler"))
                        .then(notes("notas"))
                        .then(notes("notes"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> views(String name) {
        return literal(name).executes(context -> open(ViewsScreen::new))
                .then(literal("guardar").then(named(name1 -> () -> ClientViews.save(client(), name1))))
                .then(literal("save").then(named(name1 -> () -> ClientViews.save(client(), name1))))
                .then(literal("ir").then(named(name1 -> () -> {
                    ClientViews.go(client(), name1);
                    return "";
                })))
                .then(literal("borrar").then(named(name1 -> () -> ClientViews.remove(client(), name1))))
                .then(literal("fijar").then(named(name1 -> () -> ClientViews.pin(client(), name1, true))))
                .then(literal("soltar").then(named(name1 -> () -> ClientViews.pin(client(), name1, false))))
                .then(literal("compartir").then(named(name1 -> () -> {
                    SavedView view = ClientViews.find(client(), name1).filter(found -> !found.shared())
                            .orElseThrow(() -> new IllegalArgumentException(ClientWords.INSTANCE.tr(
                                    "gui.komaedit.views.missing", ViewNames.clean(name1))));
                    ClientViews.share(client(), view);
                    return "";
                })))
                .then(literal("volver").executes(context -> run(context, () -> {
                    ClientViews.back(client());
                    return "";
                })))
                .then(literal("fija").executes(context -> run(context, () -> {
                    ClientViews.pinned(client());
                    return "";
                })));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String> named(
            Function<String, Supplier<String>> action) {
        return argument("nombre", StringArgumentType.greedyString()).suggests(viewNames())
                .executes(context -> run(context, action.apply(StringArgumentType.getString(context, "nombre"))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> ruler(String name) {
        LiteralArgumentBuilder<FabricClientCommandSource> command = literal(name).executes(context -> run(context,
                () -> {
                    RulerClient.toggle(client(), null);
                    return "";
                }));
        for (RulerMode mode : RulerMode.values()) {
            command.then(literal(mode.id()).executes(context -> run(context, () -> {
                RulerClient.toggle(client(), mode);
                return "";
            })));
        }
        return command
                .then(literal("copiar").executes(context -> run(context, () -> {
                    RulerClient.copy(client());
                    return "";
                })))
                .then(literal("borrar").executes(context -> run(context, () -> {
                    RulerClient.clear(client());
                    return "";
                })))
                .then(literal("off").executes(context -> run(context, () -> {
                    if (RulerClient.active()) {
                        RulerClient.toggle(client(), null);
                    }
                    return "";
                })));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> notes(String name) {
        return literal(name).executes(context -> open(NotesScreen::new))
                .then(literal("ocultar").executes(context -> run(context, () -> {
                    ClientNotes.hidden(true);
                    return ClientWords.INSTANCE.tr("gui.komaedit.notes.hidden");
                })))
                .then(literal("mostrar").executes(context -> run(context, () -> {
                    ClientNotes.hidden(false);
                    return ClientWords.INSTANCE.tr("gui.komaedit.notes.shown");
                })))
                .then(literal("resueltas").executes(context -> run(context, () -> {
                    ClientNotes.showResolved(!ClientNotes.showResolved());
                    return ClientWords.INSTANCE.tr(ClientNotes.showResolved() ? "gui.komaedit.notes.resolved_shown"
                            : "gui.komaedit.notes.resolved_hidden");
                })));
    }

    /** Ejecuta y enseña el mensaje (verde) o el error (rojo) en el chat. */
    private static int run(CommandContext<FabricClientCommandSource> context, Supplier<String> action) {
        try {
            String message = action.get();
            if (message != null && !message.isEmpty()) {
                context.getSource().sendFeedback(ClientWords.prefix().append(Text.literal(message)
                        .formatted(Formatting.GREEN)));
            }
            return 1;
        } catch (IllegalArgumentException exception) {
            context.getSource().sendError(ClientWords.prefix().append(Text.literal(exception.getMessage())));
            return 0;
        }
    }

    /** Abre una pantalla después de la orden, para que el chat al cerrarse no la cierre. */
    private static int open(Function<Screen, Screen> screen) {
        MinecraftClient client = client();
        client.send(() -> client.setScreen(screen.apply(null)));
        return 1;
    }

    private static MinecraftClient client() {
        return MinecraftClient.getInstance();
    }

    private static SuggestionProvider<FabricClientCommandSource> viewNames() {
        return (context, builder) -> {
            String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (SavedView view : ClientViews.own(client())) {
                if (view.name().toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(view.name());
                }
            }
            for (SavedView view : ClientViews.catalog().shared()) {
                if (view.name().toLowerCase(Locale.ROOT).startsWith(typed)) {
                    builder.suggest(view.name());
                }
            }
            return builder.buildFuture();
        };
    }
}
