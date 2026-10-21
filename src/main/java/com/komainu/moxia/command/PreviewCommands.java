package com.komainu.moxia.command;

import com.komainu.moxia.biome.BiomeEditService;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PendingBuilds;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.gizmo.GhostService;
import com.komainu.moxia.ivy.IvyService;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.performance.ServerWork;
import com.komainu.moxia.preview.LastEdits;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.preview.PreviewFlags;
import com.komainu.moxia.preview.PreviewPreferences;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.sculpt.SnapshotCaptureService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Órdenes de la vista previa universal y del progreso: {@code preview} (preferencia «previsualizar siempre»),
 * {@code cancel} (para lo que esté en marcha) y la confirmación de {@code confirm}. También instala la bandera
 * {@code -v} en todo el árbol de {@code /komaedit} ({@link #installFlags}).
 */
final class PreviewCommands {
    static final PreviewFlags.Hooks<ServerCommandSource> HOOKS = new PreviewFlags.Hooks<>() {
        @Override
        public UUID player(ServerCommandSource source) {
            ServerPlayerEntity player = source.getPlayer();
            return player == null ? null : player.getUuid();
        }

        @Override
        public boolean eligible(List<String> path) {
            return PreviewScope.eligible(path);
        }

        @Override
        public boolean shortFlagTaken(List<String> path) {
            return PreviewScope.shortFlagTaken(path);
        }
    };

    private PreviewCommands() {
    }

    static void register(LiteralCommandNode<ServerCommandSource> root) {
        for (String name : new String[]{"preview", "vistaprevia"}) {
            root.addChild(previewCommand(name).build());
        }
        for (String name : new String[]{"cancel", "cancelar"}) {
            root.addChild(literal(name).executes(PreviewCommands::cancel).build());
        }
        for (String name : new String[]{"repeat", "repetir"}) {
            LiteralArgumentBuilder<ServerCommandSource> repeat = literal(name)
                    .executes(context -> repeat(context, false));
            for (String flag : new String[]{PreviewFlags.SHORT, PreviewFlags.LONG}) {
                repeat.then(literal(flag).executes(context -> repeat(context, true)));
            }
            root.addChild(repeat.build());
        }
    }

    /**
     * {@code /komaedit repeat [-v]}: vuelve a ejecutar la última orden que colocó (o enseñó) un plan. Corre con su
     * propia petición, así recuerda su vista previa y se recalcula sola si hace falta.
     */
    private static int repeat(CommandContext<ServerCommandSource> context, boolean preview)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        Optional<LastEdits.Entry> last = LastEdits.last(player.getUuid());
        if (last.isEmpty()) {
            return Feedback.fail(context.getSource(), Feedback.problem("No hay ninguna orden que repetir",
                    "se apunta la última que coloca bloques (no los clics de pincel)"));
        }
        String command = last.get().commandFor(preview);
        Feedback.status(player, "Repitiendo /" + command);
        try {
            return context.getSource().getServer().getCommandManager().getDispatcher()
                    .execute(command, context.getSource());
        } catch (CommandSyntaxException exception) {
            return Feedback.fail(context.getSource(), Text.literal("No se pudo repetir: " + exception.getMessage())
                    .append(" ").append(Feedback.fix("[editar]", command)));
        }
    }

    /** Engancha {@code -v}/{@code --preview} en las órdenes ya registradas bajo {@code root}. */
    static int installFlags(CommandDispatcher<ServerCommandSource> dispatcher,
                            LiteralCommandNode<ServerCommandSource> root) {
        return PreviewFlags.install(dispatcher, root, HOOKS);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> previewCommand(String name) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal(name).executes(PreviewCommands::status);
        for (String always : new String[]{"always", "siempre"}) {
            command.then(literal(always).then(argument("bloques", IntegerArgumentType.integer(1,
                    PreviewPreferences.MAX_THRESHOLD)).executes(context -> setAlways(context,
                    IntegerArgumentType.getInteger(context, "bloques")))));
        }
        for (String off : new String[]{"off", "nunca"}) {
            command.then(literal(off).executes(context -> setAlways(context, 0)));
        }
        return command;
    }

    private static int status(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int threshold = PreviewPreferences.alwaysAbove(player.getUuid());
        String always = threshold > 0
                ? "siempre a partir de " + ProgressLine.group(threshold) + " bloques"
                : "solo con -v (usa /komaedit preview always <bloques> para previsualizar siempre)";
        MutableText line = Text.literal("Vista previa: " + always);
        if (PlanPreviews.hasPending(player.getUuid())) {
            line.append(" · tienes una esperando ")
                    .append(EzCommandSupport.button("[Aplicar]", "/komaedit confirm", Formatting.GREEN))
                    .append(" ")
                    .append(EzCommandSupport.button("[Descartar]", "/komaedit cancel", Formatting.RED));
        }
        Feedback.reply(player, line);
        return threshold;
    }

    private static int setAlways(CommandContext<ServerCommandSource> context, int blocks)
            throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        try {
            PreviewPreferences.setAlwaysAbove(player.getUuid(), blocks);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return EzCommandSupport.fail(context.getSource(), exception.getMessage());
        }
        EzCommandSupport.ok(player, blocks > 0
                ? "Previsualizarás siempre las ediciones de " + ProgressLine.group(blocks) + " bloques o más"
                : "Vista previa solo con -v");
        return 1;
    }

    /**
     * {@code /komaedit confirm}: aplica la vista previa si hay una.
     *
     * @return el resultado de la orden, o vacío si no había vista previa (se pasa al Build en espera)
     */
    static Optional<Integer> confirm(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (GhostService.requestConfirm(player)) {
            // El fantasma se coloca donde lo tenga ahora el cliente.
            return Optional.of(1);
        }
        return switch (PlanPreviews.confirm(player)) {
            case NONE -> Optional.empty();
            case PREPARING -> Optional.of(EzCommandSupport.fail(context.getSource(),
                    "La vista previa aún se está preparando; espera un momento"));
            case CHECKING -> Optional.of(EzCommandSupport.fail(context.getSource(), "Ya se está aplicando"));
            case BUSY -> Optional.of(EzCommandSupport.fail(context.getSource(),
                    "Tienes otra edición colocándose: espera a que termine (o /komaedit job cancel) y confirma; "
                            + "la vista previa sigue esperando"));
            case EXPIRED -> Optional.of(0);
            case STARTED -> {
                Feedback.status(player, "Confirmado: comprobando que la zona no ha cambiado…");
                yield Optional.of(1);
            }
        };
    }

    /** {@code /komaedit confirm cancel}: descarta la vista previa si hay una. */
    static Optional<String> discard(ServerPlayerEntity player) {
        Optional<String> ghost = GhostService.cancel(player);
        return ghost.isPresent() ? ghost : PlanPreviews.cancel(player.getUuid());
    }

    /** {@code /komaedit cancel}: para todo lo que el jugador tenga en marcha. */
    private static int cancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        List<String> stopped = stopAll(player);
        if (stopped.isEmpty()) {
            return EzCommandSupport.fail(context.getSource(), "No tenías nada en marcha que cancelar");
        }
        Feedback.warn(player, "Cancelado: " + String.join(" · ", stopped));
        return stopped.size();
    }

    /**
     * Para todo lo que tenga en marcha {@code player}, en la fase que sea (vista previa, lectura, cálculo, cola,
     * colocación, biomas) y le quita la pausa. Lo ya colocado queda en su //undo.
     *
     * @return lo que se paró, para el aviso
     */
    static List<String> stopAll(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        List<String> stopped = new ArrayList<>();
        PlanPreviews.cancel(id).ifPresent(description -> stopped.add("vista previa de " + description));
        GhostService.cancel(player).ifPresent(description -> stopped.add("fantasma de " + description));
        PendingBuilds.cancel(id).ifPresent(description -> stopped.add("Build en espera (" + description + ")"));
        if (RegionOperationService.cancel(id) | SnapshotCaptureService.cancel(id)) {
            stopped.add("lectura del mundo");
        }
        if (AsyncEngine.cancel(id) | IvyService.cancel(id)) {
            stopped.add("cálculo");
        }
        com.komainu.moxia.rebuild.RebuildService.cancel(id).ifPresent(stopped::add);
        if (BiomeEditService.cancel(id)) {
            stopped.add("pintado de bioma (lo ya pintado, en //undo)");
        }
        Optional<BatchEditService.JobStatus> job = BatchEditService.status(id);
        if (BatchEditService.cancel(id, true)) {
            BatchEditService.JobStatus status = job.orElseThrow();
            stopped.add(status.queuePosition() > 0
                    ? status.description() + " (sale de la cola sin haber colocado nada)"
                    : "colocación de " + status.description() + " al " + status.percentage() + " % ("
                    + ProgressLine.group(status.changed()) + " bloques ya puestos, en //undo)");
        }
        ServerWork.pause(id, false);
        return stopped;
    }
}
