package com.komainu.moxia.command;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.gradient.GradientPalette;
import com.komainu.moxia.gradient.ServerPalettes;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.permission.PermissionNodes;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Órdenes generales y de administración, como {@code /ezedits info} y {@code /ezedits reload}:
 * {@code /komaedit info}, {@code /komaedit reload} y {@code /komaedit permisos}.
 */
public final class AdminCommands {
    private AdminCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(literal("info").executes(AdminCommands::info).build());
        root.addChild(reload("reload").build());
        root.addChild(reload("recargar").build());
        root.addChild(permissions("permissions").build());
        root.addChild(permissions("permisos").build());
    }

    private static int info(CommandContext<ServerCommandSource> context) {
        String version = FabricLoader.getInstance().getModContainer(KomaEditMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        ServerCommandSource source = context.getSource();
        source.sendFeedback(() -> EzCommandSupport.prefix().append(Text.literal("KomaEdit " + version)
                .formatted(Formatting.AQUA)), false);
        source.sendFeedback(() -> Text.literal("  Paletas de serie: " + GradientPalette.values().length
                + " · del servidor: " + ServerPalettes.all().size() + " · ruidos de serie: "
                + NoiseSpec.PRESETS.size()).formatted(Formatting.GRAY), false);
        source.sendFeedback(() -> Text.literal("  Ayuda: ").formatted(Formatting.GRAY)
                .append(EzCommandSupport.button("/komaedit help", "/komaedit help", Formatting.YELLOW))
                .append(Text.literal(" · tus permisos: ").formatted(Formatting.GRAY))
                .append(EzCommandSupport.button("/komaedit permisos", "/komaedit permisos", Formatting.YELLOW)),
                false);
        return 1;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> reload(String name) {
        return literal(name)
                .requires(source -> EzCommandSupport.can(source, KomaPermissions.ADMIN_RELOAD))
                .executes(context -> {
                    KomaEditConfig.load();
                    int palettes = ServerPalettes.load(KomaEditConfig.palettesFile());
                    KomaEditConfig.Settings settings = KomaEditConfig.settings();
                    context.getSource().sendFeedback(() -> EzCommandSupport.prefix().append(Text.literal(
                            "Configuración recargada: " + settings.maxBlocksPerOperation() + " bloques por operación, "
                                    + settings.maxConcurrentJobs() + " trabajos a la vez y " + palettes
                                    + " paletas del servidor").formatted(Formatting.GREEN)), true);
                    return 1;
                });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> permissions(String name) {
        return literal(name)
                .executes(context -> permissions(context, null))
                .then(argument("pack", StringArgumentType.word())
                        .suggests(EzCommandSupport.words(packIds()))
                        .executes(context -> permissions(context, StringArgumentType.getString(context, "pack"))));
    }

    private static List<String> packIds() {
        List<String> ids = new ArrayList<>();
        for (PermissionNodes.Pack pack : PermissionNodes.Pack.values()) {
            ids.add(pack.id());
        }
        return ids;
    }

    /** Los nodos de un paquete (o de todos) con una marca de si el jugador los tiene. */
    private static int permissions(CommandContext<ServerCommandSource> context, String packName)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        PermissionNodes.Pack only = null;
        if (packName != null) {
            for (PermissionNodes.Pack pack : PermissionNodes.Pack.values()) {
                if (pack.id().equals(packName.toLowerCase(Locale.ROOT))) {
                    only = pack;
                }
            }
            if (only == null) {
                return EzCommandSupport.fail(context.getSource(), "Paquete desconocido: " + packName
                        + " (" + String.join(", ", packIds()) + ")");
            }
        }
        EzCommandSupport.send(player, Text.literal("Permisos · verde = lo tienes · pasa el ratón para ver el nodo "
                + "de ezEdits").formatted(Formatting.GRAY));
        for (PermissionNodes.Pack pack : PermissionNodes.Pack.values()) {
            if (only != null && pack != only) {
                continue;
            }
            MutableText line = Text.literal(pack.displayName() + " (" + pack.node() + "): ")
                    .formatted(Formatting.YELLOW);
            boolean first = true;
            for (PermissionNodes.Node node : PermissionNodes.all()) {
                if (node.pack() != pack) {
                    continue;
                }
                if (!first) {
                    line.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
                }
                first = false;
                boolean has = KomaPermissions.can(player, node.node());
                String hover = node.description() + (node.ezEdits() == null ? "" : "\nezEdits: " + node.ezEdits());
                line.append(Text.literal(node.node().replace("komaedit.", "")).styled(style -> style
                        .withColor(has ? Formatting.GREEN : Formatting.RED)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Text.literal(node.node() + "\n" + hover)))));
            }
            EzCommandSupport.send(player, line);
        }
        return 1;
    }
}
