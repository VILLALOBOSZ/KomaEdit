package com.komainu.moxia.studio.net;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.analysis.MaterialReport;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.nature.NaturePresets;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.symmetry.SymmetryService;
import com.komainu.moxia.symmetry.SymmetrySettings;
import com.komainu.moxia.text.TextFonts;
import com.komainu.moxia.text.TextService;
import com.komainu.moxia.worldedit.EditHistory;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.regions.Region;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Red de la etapa Herramientas del Studio. El servidor contesta qué herramientas puede usar el jugador y con qué
 * (selección, fuentes, presets, simetría), le cuenta el progreso de lo que tiene en marcha para el pie del Studio y
 * le manda la lista de materiales al terminar un {@code analyze}.
 *
 * <p>Nada de esto ejecuta nada: Construir sigue mandando la orden de chat de siempre, y el servidor la comprueba
 * como cualquier otra. A un cliente sin KomaEdit no se le manda ningún paquete.</p>
 */
public final class StudioToolsNetwork {
    /** Jugadores a los que se les dijo que había algo en marcha: al terminar reciben un último paquete vacío. */
    private static final Set<UUID> BUSY = new HashSet<>();
    /** Caracteres de cada paleta guardada que llegan al Studio. */
    private static final int PALETTE_PREVIEW = 160;

    private StudioToolsNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(ToolStateRequestPayload.ID, ToolStateRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ToolStatePayload.ID, ToolStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(JobStatusPayload.ID, JobStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MaterialListPayload.ID, MaterialListPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StudioAimPayload.ID, StudioAimPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ToolStateRequestPayload.ID, (payload, context) ->
                context.server().execute(() -> sendState(context.player())));
        // Ya en el hilo del servidor y en el orden en que llegó: la orden de chat que el cliente manda justo detrás
        // tiene que encontrarse el clic apuntado (otro execute lo dejaría para después de ella).
        ServerPlayNetworking.registerGlobalReceiver(StudioAimPayload.ID, (payload, context) ->
                StudioAim.receive(context.player(), payload));
    }

    /** El estado de las herramientas para este jugador. */
    public static ToolStatePayload state(ServerPlayerEntity player) {
        List<String> granted = new ArrayList<>();
        for (String node : ToolCatalog.permissionNodes()) {
            if (KomaPermissions.can(player, node)) {
                granted.add(node);
            }
        }
        int[] selection = new int[0];
        try {
            Region region = WorldEditService.selection(player);
            selection = new int[]{region.getWidth(), region.getHeight(), region.getLength()};
        } catch (Exception ignored) {
            // Sin selección completa (o sin sesión de WorldEdit): la tarjeta lo avisa.
        }
        List<String> fonts;
        try {
            fonts = TextFonts.custom(TextService.fontsDirectory());
        } catch (RuntimeException exception) {
            fonts = List.of();
        }
        Map<String, String> trees = Map.of();
        Map<String, String> rocks = Map.of();
        Map<String, String> palettes = Map.of();
        try {
            trees = NaturePresets.trees(player.getUuid()).all();
            rocks = NaturePresets.rocks(player.getUuid()).all();
            // Solo se enseñan (en el tooltip de «Compartir»): este estado va cada segundo, así que corto.
            palettes = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : SavedPalettes.store(player.getUuid()).all().entrySet()) {
                String value = entry.getValue();
                palettes.put(entry.getKey(), value.length() <= PALETTE_PREVIEW ? value
                        : value.substring(0, PALETTE_PREVIEW - 1) + "…");
            }
        } catch (RuntimeException ignored) {
            // Sin proyecto abierto no hay presets guardados.
        }
        SymmetrySettings symmetry = SymmetryService.settings(player.getUuid());
        boolean elsewhere = symmetry.center() != null && symmetry.world() != null
                && !symmetry.world().equals(SymmetryService.dimension(player.getWorld()));
        EditHistory.Snapshot history = EditHistory.Snapshot.EMPTY;
        try {
            history = EditHistory.snapshot(WorldEditService.context(player).session(), ToolStatePayload.MAX_HISTORY);
        } catch (RuntimeException ignored) {
            // Sin sesión de WorldEdit no hay historial que enseñar.
        }
        return new ToolStatePayload(granted, selection, fonts, trees, rocks, palettes, symmetry.active(),
                symmetry.mode().id(), symmetry.mode().arms(),
                symmetry.center() == null ? "" : symmetry.center().describe(), symmetry.worldEdit(), elsewhere,
                history.lines(), history.undone());
    }

    public static void sendState(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ToolStatePayload.ID)) {
            return;
        }
        try {
            ServerPlayNetworking.send(player, state(player));
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.warn("Could not send KomaEdit tool state to {}", player.getUuid(), exception);
        }
    }

    /** Lo llama la barra de progreso en cada actualización con lo que hace el jugador (o nada). */
    public static void sendJobStatus(ServerPlayerEntity player, Optional<ProgressLine> line) {
        UUID id = player.getUuid();
        if (!ServerPlayNetworking.canSend(player, JobStatusPayload.ID)) {
            return;
        }
        if (line.isPresent()) {
            ProgressLine current = line.get();
            long changes = current.phase() == ProgressLine.Phase.WAITING ? PlanPreviews.pendingChanges(id) : 0L;
            ServerPlayNetworking.send(player, JobStatusPayload.of(current, changes));
            BUSY.add(id);
        } else if (BUSY.remove(id)) {
            ServerPlayNetworking.send(player, JobStatusPayload.IDLE);
        }
    }

    public static void forget(UUID player) {
        BUSY.remove(player);
        StudioAim.forget(player);
    }

    public static void clearAll() {
        BUSY.clear();
        StudioAim.clearAll();
    }

    /** La lista de {@code analyze} para la tarjeta Materiales del Studio. */
    public static void sendMaterials(ServerPlayerEntity player, MaterialReport report) {
        if (!ServerPlayNetworking.canSend(player, MaterialListPayload.ID)) {
            return;
        }
        List<MaterialListPayload.Line> lines = new ArrayList<>();
        for (MaterialReport.Line line : report.cost()) {
            if (lines.size() >= MaterialListPayload.MAX_LINES) {
                break;
            }
            lines.add(new MaterialListPayload.Line(line.item(), line.amount(), line.maxStack()));
        }
        ServerPlayNetworking.send(player, new MaterialListPayload(report.blocks(), report.placed().size(),
                report.totalItems(), report.totalSlots(), report.chests(), lines,
                Math.max(0, report.cost().size() - lines.size()), report.clipboard()));
    }
}
