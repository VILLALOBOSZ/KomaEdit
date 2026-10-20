package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.capability.CapabilityStatePayload;
import com.komainu.moxia.client.capability.CapabilityClientState;
import com.komainu.moxia.client.preview.PathHologramRenderer;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.studio.net.JobStatusPayload;
import com.komainu.moxia.studio.net.MaterialListPayload;
import com.komainu.moxia.studio.net.ToolStatePayload;
import com.komainu.moxia.studio.net.ToolStateRequestPayload;
import com.komainu.moxia.studio.tools.ToolContext;
import com.komainu.moxia.studio.tools.ui.ToolsInfo;
import com.komainu.moxia.studio.ui.StudioHost;
import com.komainu.moxia.worldedit.EditHistory;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * Lo último que el servidor contó para la etapa Herramientas: permisos, selección, presets y simetría; el progreso
 * de lo que el jugador tiene en marcha (para el pie del Studio) y la última lista de materiales. Con un servidor sin
 * estos paquetes todo queda vacío y nada sale apagado: el servidor decide al construir.
 */
public final class ToolClientState {
    /** Si en este tiempo no llega progreso, se da por terminado (por si se pierde el último paquete). */
    private static final long JOB_STALE_MILLIS = 3_000L;

    private static volatile ToolStatePayload state;
    private static volatile JobStatusPayload job = JobStatusPayload.IDLE;
    private static volatile long jobAt;
    private static volatile MaterialListPayload materials;
    private static List<String> biomes = List.of();
    private static Object biomeWorld;

    private ToolClientState() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(ToolStatePayload.ID,
                (payload, context) -> context.client().execute(() -> state = payload));
        ClientPlayNetworking.registerGlobalReceiver(JobStatusPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    job = payload;
                    jobAt = System.currentTimeMillis();
                }));
        ClientPlayNetworking.registerGlobalReceiver(MaterialListPayload.ID,
                (payload, context) -> context.client().execute(() -> materials = payload));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    public static void clear() {
        state = null;
        job = JobStatusPayload.IDLE;
        materials = null;
        biomes = List.of();
        biomeWorld = null;
    }

    /** ¿Tiene el servidor la red de herramientas? */
    public static boolean supported() {
        return ClientPlayNetworking.canSend(ToolStateRequestPayload.ID);
    }

    public static void request() {
        if (supported()) {
            ClientPlayNetworking.send(ToolStateRequestPayload.INSTANCE);
        }
    }

    public static ToolContext context() {
        ToolStatePayload current = state;
        int points = PathHologramRenderer.points().size();
        if (current == null) {
            return new ToolContext(false, java.util.Set.of(), false, 0L, points, PathHologramRenderer.closed());
        }
        return new ToolContext(true, new HashSet<>(current.granted()), current.hasSelection(),
                current.selectionVolume(), points, PathHologramRenderer.closed());
    }

    public static ToolsInfo info(MinecraftClient client) {
        ToolStatePayload current = state;
        CapabilityStatePayload capability = CapabilityClientState.get();
        ToolsInfo.Capabilities capabilities = capability == null || !CapabilityClientState.supported() ? null
                : new ToolsInfo.Capabilities(capability.enabled(), capability.permitted(), capability.modeAllowed(),
                capability.flySpeedAllowed(), capability.replaceFamily(), capability.extrudeByState(),
                capability.extrudeLimit(), capability.angelDistance(), capability.reachDistance(),
                capability.flySpeed(), capability.maxReach(), capability.maxLayerBlocks(),
                capability.maxFlySpeed());
        MaterialListPayload list = materials;
        ToolsInfo.Materials report = list == null ? null : new ToolsInfo.Materials(list.blocks(), list.types(),
                list.items(), list.slots(), list.chests(), list.lines().stream()
                .map(line -> new ToolsInfo.Materials.Line(line.item(), line.amount(), line.maxStack())).toList(),
                list.hidden(), list.clipboard());
        if (current == null) {
            return new ToolsInfo(false, new int[0], List.of(), java.util.Map.of(), java.util.Map.of(),
                    ToolsInfo.Symmetry.OFF, capabilities, biomes(client), report);
        }
        ToolsInfo.Symmetry symmetry = new ToolsInfo.Symmetry(current.symmetryActive(), current.symmetryMode(),
                current.symmetryArms(), current.symmetryCenter(), current.symmetryWorldEdit(),
                current.symmetryElsewhere());
        return new ToolsInfo(true, current.selection(), current.fonts(), current.treePresets(),
                current.rockPresets(), symmetry, capabilities, biomes(client), report);
    }

    /** Biomas del registro que el servidor mandó al entrar (también los de datapacks y mods), ordenados. */
    private static List<String> biomes(MinecraftClient client) {
        if (client == null || client.world == null) {
            return List.of();
        }
        if (biomeWorld != client.world) {
            biomeWorld = client.world;
            biomes = client.world.getRegistryManager().get(RegistryKeys.BIOME).getIds().stream()
                    .map(Identifier::toString)
                    .sorted()
                    .toList();
        }
        return biomes;
    }

    /**
     * El historial de deshacer del mundo que contó el servidor; las operaciones sin nombre (las de WorldEdit) salen
     * como «WorldEdit».
     */
    public static StudioHost.WorldHistory history(StudioHost host) {
        ToolStatePayload current = state;
        if (current == null || current.history().isEmpty()) {
            return StudioHost.WorldHistory.EMPTY;
        }
        List<StudioHost.WorldHistory.Entry> entries = new ArrayList<>();
        for (EditHistory.Line line : current.history()) {
            entries.add(new StudioHost.WorldHistory.Entry(line.label().isEmpty()
                    ? host.tr("gui.komaedit.history.worldedit") : line.label(), line.blocks()));
        }
        return new StudioHost.WorldHistory(entries, current.historyUndone());
    }

    /** Lo que el jugador tiene en marcha, o vacío. */
    public static Optional<JobStatusPayload> job() {
        JobStatusPayload current = job;
        if (!current.active() || System.currentTimeMillis() - jobAt > JOB_STALE_MILLIS) {
            return Optional.empty();
        }
        return Optional.of(current);
    }

    /** ¿Hay una vista previa esperando a Confirmar? */
    public static boolean previewWaiting() {
        return job().map(status -> status.phaseValue() == ProgressLine.Phase.WAITING).orElse(false);
    }
}
