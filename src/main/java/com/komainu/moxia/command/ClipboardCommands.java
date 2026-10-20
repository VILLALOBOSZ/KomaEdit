package com.komainu.moxia.command;

import com.komainu.moxia.clipboard.ClipboardScaleService;
import com.komainu.moxia.clipboard.ClipboardTransform;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * {@code /komaedit clipboard} (alias {@code portapapeles}): escalar y girar el portapapeles de WorldEdit sin dientes
 * de sierra ({@code nearest}, {@code scale2x}, {@code scale3x}, {@code rotsprite}) y volver al de antes. No coloca
 * nada: lo de después se pega con //paste o con {@code /komaedit place Clipboard}.
 */
public final class ClipboardCommands {
    private static final List<String> MODES = List.of("nearest", "scale2x", "scale3x", "rotsprite");

    private ClipboardCommands() {
    }

    public static void register(LiteralCommandNode<ServerCommandSource> root) {
        root.addChild(command("clipboard").build());
        root.addChild(command("portapapeles").build());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> command(String name) {
        return literal(name)
                .requires(EzCommandSupport.node(KomaPermissions.CLIPBOARD))
                .executes(ClipboardCommands::status)
                .then(scale("scale"))
                .then(scale("escalar"))
                .then(rotate("rotate"))
                .then(rotate("girar"))
                .then(literal("restore").executes(ClipboardCommands::restore))
                .then(literal("restaurar").executes(ClipboardCommands::restore));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> scale(String name) {
        return literal(name).then(argument("factor", DoubleArgumentType.doubleArg(0.1, 8.0))
                .suggests(EzCommandSupport.words(List.of("2", "3", "1.5", "0.5", "4")))
                .executes(context -> change(context, false, DoubleArgumentType.getDouble(context, "factor"),
                        "nearest"))
                .then(argument("modo", StringArgumentType.word())
                        .suggests(EzCommandSupport.words(MODES))
                        .executes(context -> change(context, false, DoubleArgumentType.getDouble(context, "factor"),
                                StringArgumentType.getString(context, "modo")))));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> rotate(String name) {
        return literal(name).then(argument("grados", DoubleArgumentType.doubleArg(-360.0, 360.0))
                .suggests(EzCommandSupport.words(List.of("30", "45", "15", "90", "-30")))
                .executes(context -> change(context, true, DoubleArgumentType.getDouble(context, "grados"),
                        "rotsprite"))
                .then(argument("modo", StringArgumentType.word())
                        .suggests(EzCommandSupport.words(List.of("rotsprite", "nearest", "scale2x", "scale3x")))
                        .executes(context -> change(context, true, DoubleArgumentType.getDouble(context, "grados"),
                                StringArgumentType.getString(context, "modo")))));
    }

    private static int change(CommandContext<ServerCommandSource> context, boolean rotate, double amount, String mode)
            throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            ClipboardScaleService.Change change = new ClipboardScaleService.Change(rotate, amount,
                    ClipboardTransform.Mode.byId(mode));
            EzCommandSupport.info(player, "Calculando " + change.describe() + " del portapapeles…");
            ClipboardScaleService.start(player, change,
                    outcome -> {
                        BlockVector3 size = outcome.size();
                        EzCommandSupport.send(player, Text.literal("Portapapeles: " + change.describe() + " · "
                                        + outcome.blocksBefore() + " → " + outcome.blocksAfter() + " bloques · "
                                        + size.x() + "×" + size.y() + "×" + size.z()
                                        + (outcome.droppedEntities() > 0 ? " · sin sus " + outcome.droppedEntities()
                                        + " entidades" : "") + ". Pégalo con //paste; ").formatted(Formatting.GREEN)
                                .append(EzCommandSupport.button("[RESTAURAR]", "/komaedit clipboard restore",
                                        Formatting.YELLOW)));
                    },
                    throwable -> player.sendMessage(EzCommandSupport.prefix().append(Text.literal(
                            "No se pudo transformar el portapapeles: " + readable(throwable))
                            .formatted(Formatting.RED)), false));
            return 1;
        });
    }

    private static int restore(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            if (!ClipboardScaleService.restore(player)) {
                throw new IllegalArgumentException("No hay ningún portapapeles anterior que restaurar");
            }
            EzCommandSupport.ok(player, "Portapapeles restaurado (el de antes del último escalado o giro)");
            return 1;
        });
    }

    private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        return EzCommandSupport.run(context, player -> {
            Clipboard clipboard;
            try {
                clipboard = WorldEditService.clipboard(player);
            } catch (EmptyClipboardException exception) {
                throw new IllegalArgumentException("El portapapeles está vacío: copia algo con //copy");
            }
            BlockVector3 size = clipboard.getDimensions();
            EzCommandSupport.info(player, "Portapapeles de " + size.x() + "×" + size.y() + "×" + size.z()
                    + ". /komaedit clipboard scale <factor> [nearest|scale2x|scale3x|rotsprite] ·"
                    + " rotate <grados> [rotsprite|nearest] · restore");
            return 1;
        });
    }

    private static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
