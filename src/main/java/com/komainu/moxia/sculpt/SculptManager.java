package com.komainu.moxia.sculpt;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.sculpt.model.ModelOperation;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SculptManager {
    private static final Map<UUID, SculptProfile> PROFILES = new ConcurrentHashMap<>();
    /** Bloques que puede leer una pasada de {@link BrushOperation} (un trazo largo captura su caja entera). */
    public static final long MAX_OPERATION_CAPTURE = 4_000_000L;

    private SculptManager() {
    }

    public static void activate(ServerPlayerEntity player, SculptProfile profile) {
        PROFILES.put(player.getUuid(), profile);
        if (!player.getInventory().contains(new ItemStack(Items.BRUSH))) {
            player.getInventory().offerOrDrop(new ItemStack(Items.BRUSH));
        }
    }

    public static Optional<SculptProfile> profile(UUID playerId) {
        return Optional.ofNullable(PROFILES.get(playerId));
    }

    public static boolean isCalculating(UUID playerId) {
        return SnapshotCaptureService.isRunning(playerId) || AsyncEngine.isRunning(playerId);
    }

    public static boolean deactivate(UUID playerId) {
        SnapshotCaptureService.cancel(playerId);
        AsyncEngine.cancel(playerId);
        SculptPreviewManager.remove(playerId);
        return PROFILES.remove(playerId) != null;
    }

    public static boolean deactivate(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        SnapshotCaptureService.cancel(playerId);
        AsyncEngine.cancel(playerId);
        SculptPreviewManager.remove(player);
        return PROFILES.remove(playerId) != null;
    }

    public static boolean cancelPreview(UUID playerId) {
        boolean cancelledCapture = SnapshotCaptureService.cancel(playerId);
        boolean cancelledTask = AsyncEngine.cancel(playerId);
        boolean removedPreview = SculptPreviewManager.remove(playerId);
        return cancelledCapture || cancelledTask || removedPreview;
    }

    public static boolean cancelPreview(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        boolean cancelledCapture = SnapshotCaptureService.cancel(playerId);
        boolean cancelledTask = AsyncEngine.cancel(playerId);
        boolean removedPreview = SculptPreviewManager.remove(player);
        return cancelledCapture || cancelledTask || removedPreview;
    }

    public static void previewTarget(ServerPlayerEntity player) {
        HitResult result = player.raycast(192.0, 1.0F, false);
        if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("Mira un bloque para previsualizar la brocha");
        }
        preview(player, hit);
    }

    public static void preview(ServerPlayerEntity player, BlockHitResult hit) {
        SculptProfile profile = PROFILES.get(player.getUuid());
        if (profile == null) {
            throw new IllegalArgumentException("Activa primero una brocha con /komaedit sculpt set");
        }

        UUID playerId = player.getUuid();
        SculptSettings settings = profile.settings();
        BlockVector3 center = BlockVector3.at(
                hit.getBlockPos().getX(),
                hit.getBlockPos().getY(),
                hit.getBlockPos().getZ()
        );
        if (settings.type().category() == SculptBrushType.Category.MODEL) {
            run(player, ModelOperation.fromSculpt(profile, center, hit.getSide()), true);
            return;
        }
        List<BlockVector3> linePoints;
        if (settings.type() == SculptBrushType.LINE) {
            var path = PathManager.saved(playerId);
            ArrayList<BlockVector3> points = new ArrayList<>(path.points());
            if (path.closed() && !points.isEmpty()) {
                points.add(points.getFirst());
            }
            linePoints = List.copyOf(points);
        } else {
            linePoints = List.of();
        }
        SnapshotCaptureService.cancel(playerId);
        AsyncEngine.cancel(playerId);
        SculptPreviewManager.remove(player);
        MinecraftServer server = player.getServer();
        RegistryKey<World> worldKey = player.getWorld().getRegistryKey();
        if (settings.type().needsSnapshot()) {
            int horizontal = settings.radius() + 2;
            int vertical = Math.max(settings.radius() + settings.intensity() + 3, settings.height() + 3);
            player.sendMessage(message("Capturando el terreno sin bloquear el servidor…"), true);
            SnapshotCaptureService.submit(
                    player,
                    WorldEditService.context(player).world(),
                    center,
                    horizontal,
                    vertical,
                    snapshot -> startPlanning(
                            server,
                            playerId,
                            worldKey,
                            profile,
                            center,
                            hit.getSide(),
                            linePoints,
                            snapshot
                    ),
                    throwable -> player.sendMessage(message(
                            "No se pudo capturar el terreno: " + readableMessage(throwable)
                    ), false)
            );
            return;
        }
        startPlanning(server, playerId, worldKey, profile, center, hit.getSide(), linePoints, null);
    }

    private static void startPlanning(
            MinecraftServer server,
            UUID playerId,
            RegistryKey<World> worldKey,
            SculptProfile profile,
            BlockVector3 center,
            Direction side,
            List<BlockVector3> linePoints,
            WorldSnapshot snapshot
    ) {
        SculptSettings settings = profile.settings();
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null || !player.getWorld().getRegistryKey().equals(worldKey)) {
            return;
        }
        player.sendMessage(message("Calculando " + settings.type().displayName() + "…"), true);
        AsyncEngine.submit(
                playerId,
                () -> SculptPlanner.plan(settings, center, side, snapshot, linePoints),
                plan -> server.execute(() -> {
                    ServerPlayerEntity currentPlayer = server.getPlayerManager().getPlayer(playerId);
                    if (currentPlayer == null || !currentPlayer.getWorld().getRegistryKey().equals(worldKey)) {
                        return;
                    }
                    SculptPreview preview = new SculptPreview(
                            worldKey,
                            settings,
                            profile.pattern(),
                            plan,
                            System.currentTimeMillis()
                    );
                    SculptPreviewManager.put(currentPlayer, preview);
                    currentPlayer.sendMessage(message(
                            "Preview " + settings.type().displayName() + ": " + ProgressLine.group(plan.size())
                                    + " bloques · /komaedit sculpt apply"
                    ), false);
                }),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity currentPlayer = server.getPlayerManager().getPlayer(playerId);
                    if (currentPlayer != null) {
                        currentPlayer.sendMessage(message("No se pudo calcular: " + readableMessage(throwable)), false);
                    }
                })
        );
    }

    /**
     * Calcula una pasada de pincel por el mismo camino que las brochas: captura repartida entre ticks, cálculo en
     * un worker cancelable y, después, vista previa ({@code preview}) o edición por lotes con su //undo.
     */
    public static void run(ServerPlayerEntity player, BrushOperation operation, boolean preview) {
        UUID playerId = player.getUuid();
        SnapshotCaptureService.cancel(playerId);
        AsyncEngine.cancel(playerId);
        SculptPreviewManager.remove(player);
        MinecraftServer server = player.getServer();
        RegistryKey<World> worldKey = player.getWorld().getRegistryKey();
        BrushOperation.Capture capture = operation.capture();
        if (capture == null) {
            planOperation(server, playerId, worldKey, operation, preview, null);
            return;
        }
        long side = capture.horizontalRadius() * 2L + 1L;
        long volume = side * side * (capture.verticalRadius() * 2L + 1L);
        if (volume > MAX_OPERATION_CAPTURE) {
            throw new IllegalArgumentException(operation.displayName() + ": la zona es demasiado grande ("
                    + ProgressLine.group(volume) + " bloques; máximo " + MAX_OPERATION_CAPTURE + ")");
        }
        SnapshotCaptureService.submit(
                player,
                WorldEditService.context(player).world(),
                capture.center(),
                capture.horizontalRadius(),
                capture.verticalRadius(),
                snapshot -> planOperation(server, playerId, worldKey, operation, preview, snapshot),
                throwable -> player.sendMessage(message(
                        "No se pudo capturar el terreno: " + readableMessage(throwable)
                ), false)
        );
    }

    private static void planOperation(
            MinecraftServer server,
            UUID playerId,
            RegistryKey<World> worldKey,
            BrushOperation operation,
            boolean preview,
            WorldSnapshot snapshot
    ) {
        AsyncEngine.submit(
                playerId,
                () -> operation.plan(snapshot),
                plan -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null || !current.getWorld().getRegistryKey().equals(worldKey)) {
                        return;
                    }
                    if (plan.size() == 0) {
                        current.sendMessage(message(operation.displayName() + ": no hay nada que cambiar aquí"), true);
                        return;
                    }
                    if (preview) {
                        SculptPreviewManager.put(current, new SculptPreview(worldKey, null, operation.pattern(), plan,
                                System.currentTimeMillis(), operation.displayName(), operation.previewColor()));
                        current.sendMessage(message("Vista previa " + operation.displayName() + ": " + ProgressLine.group(plan.size())
                                + " bloques · /komaedit pincel aplicar"), false);
                        return;
                    }
                    try {
                        // Un trazo de pincel se aplica al momento: sin la vista previa universal ni su preferencia.
                        int queued = EditRequests.within(EditRequest.TOOL,
                                () -> BatchEditService.submit(current, plan, operation.pattern(),
                                        operation.displayName()));
                        current.sendMessage(message(operation.displayName() + ": " + ProgressLine.group(queued)
                                + " bloques · //undo para deshacer"), true);
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        current.sendMessage(message(operation.displayName() + ": " + exception.getMessage()), true);
                    }
                }),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null) {
                        current.sendMessage(message(operation.displayName() + ": " + readableMessage(throwable)),
                                false);
                    }
                })
        );
    }

    public static int apply(ServerPlayerEntity player) {
        SculptPreview preview = SculptPreviewManager.get(player.getUuid())
                .orElseThrow(() -> new IllegalArgumentException("No hay una previsualización de brocha activa"));
        if (!player.getWorld().getRegistryKey().equals(preview.world())) {
            throw new IllegalArgumentException("La previsualización pertenece a otro mundo");
        }
        // Ya se vio con la vista previa de la brocha: no vuelve a pedir otra.
        int scheduled = EditRequests.within(EditRequest.CONFIRMED, () -> BatchEditService.submit(
                player,
                preview.plan(),
                preview.pattern(),
                preview.description()
        ));
        SculptPreviewManager.remove(player);
        return scheduled;
    }

    public static void remove(UUID playerId) {
        SnapshotCaptureService.cancel(playerId);
        AsyncEngine.cancel(playerId);
        PROFILES.remove(playerId);
        SculptPreviewManager.remove(playerId);
    }

    public static void clearAll() {
        SnapshotCaptureService.clearAll();
        PROFILES.clear();
        SculptPreviewManager.clearAll();
    }

    private static Text message(String value) {
        return Feedback.prefix()
                .append(Text.literal(value).formatted(Formatting.GRAY));
    }

    private static String readableMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
