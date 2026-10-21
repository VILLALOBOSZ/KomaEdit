package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.views.SavedView;
import com.komainu.moxia.views.ViewPermissions;
import com.komainu.moxia.views.ViewService;
import com.komainu.moxia.views.ViewTeleport;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit vista} (alias {@code vistas}, {@code view}, {@code views}): las vistas compartidas con el equipo.
 * Las privadas son del cliente ({@code /kegui vista}); aquí se comparte la vista de donde estás, se lista, se salta a
 * una con las reglas del servidor y se quita.
 */
public final class ViewCommands {
    private static final int PAGE = 8;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private ViewCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : new String[]{"vista", "vistas", "view", "views"}) {
            root.addChild(command(name).build());
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(source -> EzCommandSupport.canAny(source, "komaedit.view")
                        || EzCommandSupport.can(source, ViewPermissions.MANAGE))
                .executes(context -> list(context, 1));
        for (String list : new String[]{"lista", "list"}) {
            command.then(literal(list).requires(EzCommandSupport.node(ViewPermissions.SEE_SHARED))
                    .executes(context -> list(context, 1))
                    .then(argument("pagina", IntegerArgumentType.integer(1))
                            .executes(context -> list(context, IntegerArgumentType.getInteger(context, "pagina")))));
        }
        for (String go : new String[]{"ir", "go", "tp"}) {
            command.then(literal(go).requires(EzCommandSupport.node(ViewPermissions.TELEPORT))
                    .then(argument("nombre", StringArgumentType.greedyString()).suggests(names(false))
                            .executes(context -> EzCommandSupport.run(context, player ->
                                    go(context, player, StringArgumentType.getString(context, "nombre"))))));
        }
        for (String share : new String[]{"compartir", "share"}) {
            command.then(literal(share).requires(EzCommandSupport.node(ViewPermissions.SHARE))
                    .then(argument("nombre", StringArgumentType.greedyString())
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                EzCommandSupport.ok(player, ViewService.shareHere(player,
                                        StringArgumentType.getString(context, "nombre")));
                                return 1;
                            }))));
        }
        for (String delete : new String[]{"borrar", "quitar", "delete"}) {
            command.then(literal(delete).requires(source -> EzCommandSupport.can(source, ViewPermissions.SHARE)
                            || EzCommandSupport.can(source, ViewPermissions.MANAGE))
                    .then(argument("nombre", StringArgumentType.greedyString()).suggests(names(true))
                            .executes(context -> EzCommandSupport.run(context, player -> {
                                SavedView removed = ViewService.remove(player,
                                        StringArgumentType.getString(context, "nombre"));
                                EzCommandSupport.ok(player, "Vista «" + removed.name() + "» quitada del equipo");
                                return 1;
                            }))));
        }
        return command;
    }

    private static int go(CommandContext<ServerCommandSource> context, ServerPlayerEntity player, String name) {
        SavedView view = ViewService.find(player, name).orElseThrow(() -> new IllegalArgumentException(
                "No hay ninguna vista compartida «" + name.strip() + "» · mira /komaedit vista lista"));
        String problem = ViewService.jump(player, ViewTeleport.Target.of(view));
        if (problem != null) {
            return EzCommandSupport.fail(context.getSource(), problem);
        }
        EzCommandSupport.status(player, "En la vista «" + view.name() + "» de " + view.author());
        return 1;
    }

    private static int list(CommandContext<ServerCommandSource> context, int page) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            if (!KomaPermissions.can(player, ViewPermissions.SEE_SHARED)) {
                throw new IllegalArgumentException("Te falta el permiso " + ViewPermissions.SEE_SHARED
                        + " para ver las vistas del equipo");
            }
            List<SavedView> views = ViewService.visible(player);
            if (views.isEmpty()) {
                EzCommandSupport.info(player, "El equipo aún no ha compartido ninguna vista · /komaedit vista"
                        + " compartir <nombre> comparte la de donde estás");
                return 1;
            }
            int pages = (views.size() + PAGE - 1) / PAGE;
            int shown = Math.clamp(page, 1, pages);
            EzCommandSupport.send(player, Text.literal("Vistas del equipo (" + views.size() + ")"
                    + (pages > 1 ? " · página " + shown + "/" + pages : "")).formatted(Formatting.GOLD));
            boolean manage = KomaPermissions.can(player, ViewPermissions.MANAGE);
            boolean teleport = KomaPermissions.can(player, ViewPermissions.TELEPORT);
            String here = ViewService.dimension(player.getWorld());
            for (SavedView view : views.subList((shown - 1) * PAGE, Math.min(views.size(), shown * PAGE))) {
                MutableText line = Text.literal("  ");
                if (teleport) {
                    line.append(Feedback.button("[ir]", "/komaedit vista ir " + view.name(), Formatting.GREEN,
                            "Saltar a «" + view.name() + "»")).append(" ");
                }
                line.append(Text.literal(view.name()).formatted(Formatting.WHITE));
                StringBuilder details = new StringBuilder(" · ").append(view.author()).append(" · ")
                        .append(DATE.format(Instant.ofEpochMilli(view.created())));
                if (!view.dimension().equals(here)) {
                    details.append(" · ").append(shortDimension(view.dimension()));
                } else {
                    double distance = Math.sqrt(view.distanceSquared(player.getX(), player.getY(), player.getZ()));
                    details.append(" · a ").append(Math.round(distance)).append(" bloques");
                }
                line.append(Text.literal(details.toString()).formatted(Formatting.GRAY));
                if (manage || player.getUuid().equals(view.authorId())) {
                    line.append(" ").append(Feedback.button("[quitar]", "/komaedit vista borrar " + view.name(),
                            Formatting.RED, "Dejar de compartir «" + view.name() + "»"));
                }
                player.sendMessage(line, false);
            }
            if (shown < pages) {
                EzCommandSupport.send(player, Feedback.button("[siguiente página]", "/komaedit vista lista "
                        + (shown + 1), Formatting.AQUA, "Página " + (shown + 1)));
            }
            return 1;
        });
    }

    /** {@code overworld}, {@code the_nether}: la dimensión sin el espacio de nombres de Minecraft. */
    static String shortDimension(String dimension) {
        return dimension.startsWith("minecraft:") ? dimension.substring("minecraft:".length()) : dimension;
    }

    private static SuggestionProvider<ServerCommandSource> names(boolean ownOnly) {
        return (context, builder) -> {
            ServerPlayerEntity player = context.getSource().getPlayer();
            if (player != null) {
                boolean manage = KomaPermissions.can(player, ViewPermissions.MANAGE);
                String typed = builder.getRemaining().toLowerCase(Locale.ROOT);
                for (SavedView view : ViewService.visible(player)) {
                    boolean mine = player.getUuid().equals(view.authorId());
                    if ((!ownOnly || mine || manage) && view.name().toLowerCase(Locale.ROOT).startsWith(typed)) {
                        builder.suggest(view.name());
                    }
                }
            }
            return builder.buildFuture();
        };
    }
}
