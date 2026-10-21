package com.komainu.moxia.command;

import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.capability.CapabilityOption;
import com.komainu.moxia.capability.CapabilityService;
import com.komainu.moxia.capability.CapabilitySettings;
import com.komainu.moxia.permission.KomaPermissions;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
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
 * {@code /komaedit capability} (alias {@code capacidad}): las capacidades de construcción a mano desde el chat. El
 * menú rápido del cliente hace lo mismo; la orden sirve también sin el mod en el cliente para el alcance largo y la
 * velocidad de vuelo, que funcionan solo con el servidor.
 */
public final class CapabilityCommands {
    private static final String FAMILY = BuildCapability.PERMISSION_PREFIX.substring(0,
            BuildCapability.PERMISSION_PREFIX.length() - 1);

    private CapabilityCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("capability").build());
        root.addChild(command("capacidad").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        List<String> names = new ArrayList<>();
        for (BuildCapability capability : BuildCapability.values()) {
            names.add(capability.id());
            names.add(capability.alias());
        }
        List<String> options = new ArrayList<>();
        for (CapabilityOption option : CapabilityOption.values()) {
            options.add(option.id());
        }
        return literal(name)
                .requires(source -> EzCommandSupport.canAny(source, FAMILY))
                .executes(CapabilityCommands::list)
                .then(literal("list").executes(CapabilityCommands::list))
                .then(literal("option")
                        .then(argument("option", StringArgumentType.word())
                                .suggests(EzCommandSupport.words(options))
                                .then(argument("value", StringArgumentType.word())
                                        .suggests(EzCommandSupport.words(List.of("on", "off", "type", "state")))
                                        .executes(CapabilityCommands::option))))
                .then(argument("name", StringArgumentType.word())
                        .suggests(EzCommandSupport.words(names))
                        .executes(context -> toggle(context, -1))
                        .then(literal("on").executes(context -> toggle(context, 1)))
                        .then(literal("off").executes(context -> toggle(context, 0)))
                        .then(literal("toggle").executes(context -> toggle(context, -1)))
                        // Distancia (angel, reach) o bloques por capa (extrude, 256 de serie): el servidor recorta
                        // cada uno a su máximo y la respuesta dice con cuánto se queda.
                        .then(argument("distance", IntegerArgumentType.integer(1,
                                        CapabilitySettings.MAX_EXTRUDE_LIMIT))
                                .executes(CapabilityCommands::distance)));
    }

    private static int toggle(CommandContext<ServerCommandSource> context, int value) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            BuildCapability capability = capability(StringArgumentType.getString(context, "name"));
            EzCommandSupport.ok(player, CapabilityService.toggle(player, capability, value));
            return 1;
        });
    }

    /** {@code /komaedit capability angel 8} o {@code reach 32}: atajo de la opción de distancia. */
    private static int distance(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            BuildCapability capability = capability(StringArgumentType.getString(context, "name"));
            CapabilityOption option = switch (capability) {
                case ANGEL -> CapabilityOption.ANGEL_DISTANCE;
                case REACH -> CapabilityOption.REACH_DISTANCE;
                case EXTRUDE -> CapabilityOption.EXTRUDE_LIMIT;
                default -> throw new IllegalArgumentException(capability.displayName() + " no tiene distancia");
            };
            EzCommandSupport.ok(player, CapabilityService.option(player, option,
                    IntegerArgumentType.getInteger(context, "distance")));
            return 1;
        });
    }

    private static int option(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CapabilityOption option = CapabilityOption.byName(StringArgumentType.getString(context, "option"));
            if (option == null) {
                throw new IllegalArgumentException("Opción desconocida; usa family, match, limit, distance, reach "
                        + "o flyspeed");
            }
            EzCommandSupport.ok(player, CapabilityService.option(player, option,
                    value(option, StringArgumentType.getString(context, "value"))));
            return 1;
        });
    }

    private static int value(CapabilityOption option, String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (List.of("on", "si", "sí", "true", "state", "estado").contains(value)) {
            return 1;
        }
        if (List.of("off", "no", "false", "type", "tipo").contains(value)) {
            return 0;
        }
        try {
            if (option == CapabilityOption.FLY_SPEED) {
                // En el chat va como multiplicador (2.5 = dos veces y media); dentro, en décimas.
                return (int) Math.round(Double.parseDouble(value.replace(',', '.')) * 10.0);
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valor no válido: " + raw);
        }
    }

    private static int list(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            CapabilitySettings settings = CapabilityService.settings(player);
            EzCommandSupport.send(player, Text.literal("Capacidades de construcción (menú rápido: mantén Z)")
                    .formatted(Formatting.AQUA));
            for (BuildCapability capability : BuildCapability.values()) {
                player.sendMessage(line(player, capability, settings), false);
            }
            MutableText options = Text.literal("  Opciones: ").formatted(Formatting.GRAY);
            for (CapabilityOption option : CapabilityOption.values()) {
                options.append(Text.literal(CapabilityService.describe(option, settings) + "  ")
                        .formatted(Formatting.DARK_AQUA));
            }
            player.sendMessage(options, false);
            return 1;
        });
    }

    private static MutableText line(ServerPlayerEntity player, BuildCapability capability,
                                    CapabilitySettings settings) {
        boolean on = settings.has(capability);
        boolean permitted = KomaPermissions.can(player, capability.permission());
        String command = "/komaedit capability " + capability.id() + (on ? " off" : " on");
        MutableText line = Text.literal("  ");
        if (permitted) {
            line.append(EzCommandSupport.button(on ? "[✔]" : "[  ]", command,
                    on ? Formatting.GREEN : Formatting.DARK_GRAY));
        } else {
            line.append(Text.literal("[✖]").formatted(Formatting.DARK_RED));
        }
        line.append(Text.literal(" " + capability.displayName()).styled(style -> style
                .withColor(on ? Formatting.WHITE : Formatting.GRAY)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(capability.description())))));
        if (!permitted) {
            line.append(Text.literal("  sin permiso " + capability.permission()).formatted(Formatting.DARK_GRAY));
        }
        return line;
    }

    private static BuildCapability capability(String name) {
        BuildCapability capability = BuildCapability.byName(name);
        if (capability == null) {
            throw new IllegalArgumentException("No existe la capacidad «" + name + "»");
        }
        return capability;
    }
}
