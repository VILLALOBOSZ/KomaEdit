package com.komainu.moxia.command;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PendingBuilds;
import com.komainu.moxia.ivy.IvyService;
import com.komainu.moxia.performance.AsyncEngine;
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
        EzCommandSupport.send(player, Text.literal("Vista previa: " + always).formatted(Formatting.AQUA));
        if (PlanPreviews.hasPending(player.getUuid())) {
            player.sendMessage(Text.literal("  Tienes una vista previa esperando: ")
                    .formatted(Formatting.GRAY)
                    .append(EzCommandSupport.button("[Aplicar]", "/komaedit confirm", Formatting.GREEN))
                    .append(Text.literal(" "))
                    .append(EzCommandSupport.button("[Descartar]", "/komaedit cancel", Formatting.RED)), false);
        }
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
                EzCommandSupport.info(player, "Confirmado: comprobando que la zona no ha cambiado…");
                yield Optional.of(1);
            }
        };
    }

    /** {@code /komaedit confirm cancel}: descarta la vista previa si hay una. */
    static Optional<String> discard(ServerPlayerEntity player) {
        return PlanPreviews.cancel(player.getUuid());
    }

    /** {@code /komaedit cancel}: para todo lo que el jugador tenga en marcha. */
    private static int cancel(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        UUID id = player.getUuid();
        List<String> stopped = new ArrayList<>();
        PlanPreviews.cancel(id).ifPresent(description -> stopped.add("vista previa de " + description));
        PendingBuilds.cancel(id).ifPresent(description -> stopped.add("Build en espera (" + description + ")"));
        if (RegionOperationService.cancel(id) | SnapshotCaptureService.cancel(id)) {
            stopped.add("lectura del mundo");
        }
        if (AsyncEngine.cancel(id) | IvyService.cancel(id)) {
            stopped.add("cálculo");
        }
        Optional<BatchEditService.JobStatus> job = BatchEditService.status(id);
        if (BatchEditService.cancel(id, true)) {
            BatchEditService.JobStatus status = job.orElseThrow();
            stopped.add("colocación de " + status.description() + " al " + status.percentage() + " % ("
                    + ProgressLine.group(status.changed()) + " bloques ya puestos, en //undo)");
        }
        if (stopped.isEmpty()) {
            return EzCommandSupport.fail(context.getSource(), "No tenías nada en marcha que cancelar");
        }
        EzCommandSupport.send(player, Text.literal("Cancelado: " + String.join(" · ", stopped))
                .formatted(Formatting.YELLOW));
        return stopped.size();
    }
}
