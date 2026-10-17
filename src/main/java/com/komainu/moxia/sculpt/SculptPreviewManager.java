package com.komainu.moxia.sculpt;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.preview.PreviewHologramPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SculptPreviewManager {
    private static final Map<UUID, SculptPreview> PREVIEWS = new ConcurrentHashMap<>();

    private SculptPreviewManager() {
    }

    public static void put(ServerPlayerEntity player, SculptPreview preview) {
        PREVIEWS.put(player.getUuid(), preview);
        show(player, preview);
    }

    public static Optional<SculptPreview> get(UUID playerId) {
        return Optional.ofNullable(PREVIEWS.get(playerId));
    }

    public static boolean remove(UUID playerId) {
        return PREVIEWS.remove(playerId) != null;
    }

    public static boolean remove(ServerPlayerEntity player) {
        boolean removed = PREVIEWS.remove(player.getUuid()) != null;
        if (removed && ServerPlayNetworking.canSend(player, PreviewHologramPayload.ID)) {
            ServerPlayNetworking.send(player, PreviewHologramPayload.clear());
        }
        return removed;
    }

    public static void show(ServerPlayerEntity player, SculptPreview preview) {
        if (!player.getWorld().getRegistryKey().equals(preview.world())) {
            throw new IllegalArgumentException("La previsualización pertenece a otro mundo");
        }
        if (ServerPlayNetworking.canSend(player, PreviewHologramPayload.ID)) {
            ServerPlayNetworking.send(player, PreviewHologramPayload.show(
                    preview.plan().bounds(),
                    preview.color() >= 0 ? preview.color() : color(preview.settings().type())
            ));
        }
    }

    public static void clearAll() {
        PREVIEWS.clear();
    }

    private static int color(SculptBrushType type) {
        return switch (type) {
            case LOWER, EROSION -> 0x4D8CFF;
            case RAISE, NOISE -> 0xFF7043;
            case SMOOTH, FLATTEN -> 0x68E06F;
            default -> 0xE7E7E7;
        };
    }
}
