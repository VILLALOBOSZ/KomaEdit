package com.komainu.moxia.command;

import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.performance.ServerWork;
import com.komainu.moxia.performance.TickBudget;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.progress.ProgressBars;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.SelectionParts;
import com.komainu.moxia.worldedit.WorldEditService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Órdenes de la cola de trabajos: {@code /komaedit jobs} (admin) lista lo que hace cada jugador y deja pausar,
 * reanudar o cancelar cualquier trabajo; {@code /komaedit trozo <n>} selecciona un trozo de una selección que pasaba
 * del límite.
 */
final class JobCommands {
    /** Botones de trozos en el aviso de límite, como mucho. */
    static final int MAX_PART_BUTTONS = 8;

    private JobCommands() {
    }

    static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : new String[]{"jobs", "trabajos"}) {
            root.addChild(jobs(name).build());
        }
        for (String name : new String[]{"trozo", "part"}) {
            root.addChild(literal(name)
                    .executes(context -> part(context, 1))
                    .then(argument("trozo", IntegerArgumentType.integer(1))
                            .executes(context -> part(context, IntegerArgumentType.getInteger(context, "trozo"))))
                    .build());
        }
    }

    private static LiteralArgumentBuilder<ServerCommandSource> jobs(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name)
                .requires(EzCommandSupport.node(KomaPermissions.ADMIN_JOBS))
                .executes(JobCommands::list);
        for (String pause : new String[]{"pause", "pausar"}) {
            command.then(literal(pause).then(argument("jugador", EntityArgumentType.player())
                    .executes(context -> pause(context, true))));
        }
        for (String resume : new String[]{"resume", "reanudar"}) {
            command.then(literal(resume).then(argument("jugador", EntityArgumentType.player())
                    .executes(context -> pause(context, false))));
        }
        for (String cancel : new String[]{"cancel", "cancelar"}) {
            command.then(literal(cancel).then(argument("jugador", EntityArgumentType.player())
                    .executes(JobCommands::cancel)));
        }
        return command;
    }

    /** {@code /komaedit jobs}: el presupuesto, la cola y una línea por jugador con trabajo. */
    private static int list(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        TickBudget budget = ServerWork.budget();
        KomaEditConfig.Settings settings = KomaEditConfig.settings();
        double rate = BatchEditService.blocksPerSecond();
        source.sendFeedback(() -> Feedback.prefix().append(Text.literal("Servidor a "
                + ProgressLine.decimal(Math.round(budget.tps() * 10.0) / 10.0) + " TPS, "
                + ProgressLine.decimal(Math.round(budget.mspt() * 10.0) / 10.0) + " ms por tick · KomaEdit usa "
                + ProgressLine.decimal(Math.round(budget.ownMillis() * 10.0) / 10.0) + " de "
                + ProgressLine.decimal(Math.round(budget.allowance() / 100_000.0) / 10.0) + " ms (máximo "
                + ProgressLine.decimal(settings.tickBudgetMillis()) + ") · colocan " + settings.maxConcurrentJobs()
                + " a la vez" + (rate > 0 ? " · " + ProgressLine.group(Math.round(rate)) + " bl/s" : ""))
                .formatted(Formatting.GRAY)), false);
        int count = 0;
        for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
            Optional<ProgressLine> line = ProgressBars.current(player.getUuid());
            if (line.isEmpty()) {
                continue;
            }
            count++;
            String name = player.getGameProfile().getName();
            boolean paused = ServerWork.paused(player.getUuid());
            MutableText row = Text.literal("  " + name).formatted(Formatting.AQUA)
                    .append(Text.literal(" · " + line.get().text()).formatted(Formatting.WHITE));
            BatchEditService.status(player.getUuid()).filter(status -> status.planBytes() >= 1_048_576L)
                    .ifPresent(status -> row.append(Text.literal(" · plan "
                            + Math.round(status.planBytes() / 1_048_576.0) + " MB").formatted(Formatting.GRAY)));
            row.append(" ")
                    .append(paused
                            ? Feedback.button("[reanudar]", "/komaedit jobs resume " + name, Formatting.GREEN,
                            "Sigue con sus lecturas, colocaciones y biomas")
                            : Feedback.button("[pausar]", "/komaedit jobs pause " + name, Formatting.YELLOW,
                            "Para sus lecturas, colocaciones y biomas sin perder nada (los cálculos siguen)"))
                    .append(" ")
                    .append(Feedback.button("[cancelar]", "/komaedit jobs cancel " + name, Formatting.RED,
                            "Lo para todo; lo ya colocado queda en su //undo"));
            source.sendFeedback(() -> row, false);
        }
        if (count == 0) {
            source.sendFeedback(() -> Text.literal("  Nadie tiene trabajos de KomaEdit en marcha")
                    .formatted(Formatting.GRAY), false);
        }
        return count;
    }

    private static int pause(CommandContext<ServerCommandSource> context, boolean paused)
            throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "jugador");
        String name = target.getGameProfile().getName();
        if (paused && ProgressBars.current(target.getUuid()).isEmpty()) {
            return EzCommandSupport.fail(context.getSource(), name + " no tiene nada en marcha");
        }
        if (!ServerWork.pause(target.getUuid(), paused)) {
            return EzCommandSupport.fail(context.getSource(), name + (paused ? " ya está en pausa"
                    : " no estaba en pausa"));
        }
        context.getSource().sendFeedback(() -> Feedback.line(Feedback.Level.OK, paused
                ? "Trabajos de " + name + " en pausa: no lee, coloca ni pinta biomas hasta reanudarlos"
                : "Trabajos de " + name + " reanudados"), true);
        if (context.getSource().getPlayer() != target) {
            Feedback.warn(target, paused
                    ? "Un administrador ha puesto en pausa tu edición; seguirá donde se quedó al reanudarla"
                    : "Un administrador ha reanudado tu edición");
        }
        return 1;
    }

    private static int cancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "jugador");
        String name = target.getGameProfile().getName();
        List<String> stopped = PreviewCommands.stopAll(target);
        if (stopped.isEmpty()) {
            return EzCommandSupport.fail(context.getSource(), name + " no tenía nada en marcha");
        }
        context.getSource().sendFeedback(() -> Feedback.line(Feedback.Level.OK,
                "Cancelado a " + name + ": " + String.join(" · ", stopped)), true);
        if (context.getSource().getPlayer() != target) {
            Feedback.warn(target, "Un administrador ha cancelado: " + String.join(" · ", stopped));
        }
        return stopped.size();
    }

    /** {@code /komaedit trozo <n>}: selecciona el trozo n de la última selección que pasó del límite. */
    private static int part(CommandContext<ServerCommandSource> context, int number) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<SelectionParts.Proposal> proposal = SelectionParts.get(player.getUuid());
        if (proposal.isEmpty()) {
            return Feedback.fail(context.getSource(), Feedback.problem("No hay ninguna selección que partir",
                    "los trozos aparecen cuando una orden avisa de que la selección pasa del límite"));
        }
        SelectionParts.Proposal parts = proposal.get();
        if (!parts.world().equals(player.getServerWorld().getRegistryKey().getValue().toString())) {
            return EzCommandSupport.fail(context.getSource(), "Esos trozos son de otro mundo (" + parts.world()
                    + ")");
        }
        if (number > parts.parts().size()) {
            return EzCommandSupport.fail(context.getSource(), "Solo hay " + parts.parts().size() + " trozos");
        }
        BlockBounds box = parts.parts().get(number - 1);
        WorldEditService.setCuboid(player, new BlockPos(box.minimumX(), box.minimumY(), box.minimumZ()),
                new BlockPos(box.maximumX(), box.maximumY(), box.maximumZ()));
        long volume = (long) (box.maximumX() - box.minimumX() + 1) * (box.maximumY() - box.minimumY() + 1)
                * (box.maximumZ() - box.minimumZ() + 1);
        MutableText text = Feedback.format("Trozo %s de %s seleccionado (%s bloques): repite %s", number,
                parts.parts().size(), volume, parts.subject());
        if (number < parts.parts().size()) {
            text.append(" · luego ").append(Feedback.button("[trozo " + (number + 1) + "]",
                    "/komaedit trozo " + (number + 1), Formatting.AQUA, "Selecciona el trozo siguiente"));
        }
        Feedback.ok(player, text);
        return number;
    }

    /** Botones de los trozos para el aviso de límite que se acaba de dar (vacío si no hay propuesta reciente). */
    static Optional<MutableText> partButtons(ServerPlayerEntity player) {
        return SelectionParts.fresh(player.getUuid(), 2_000_000_000L).map(proposal -> {
            MutableText buttons = Text.empty();
            int shown = Math.min(MAX_PART_BUTTONS, proposal.parts().size());
            for (int index = 1; index <= shown; index++) {
                buttons.append(index == 1 ? Text.empty() : Text.literal(" ")).append(Feedback.button(
                        "[trozo " + index + "]", "/komaedit trozo " + index, Formatting.AQUA,
                        "Selecciona el trozo " + index + " de " + proposal.parts().size()));
            }
            if (proposal.parts().size() > shown) {
                buttons.append(Text.literal(" … /komaedit trozo <n>").formatted(Formatting.GRAY));
            }
            return buttons;
        });
    }
}
