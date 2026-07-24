package com.komainu.moxia.path;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public final class PathHologramSync {
    private PathHologramSync() {
    }

    public static void sync(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, PathHologramPayload.ID)) {
            return;
        }
        if (!PathManager.isCaptureMode(player.getUuid())) {
            ServerPlayNetworking.send(player, PathHologramPayload.clear());
            return;
        }
        PathSnapshot path = PathManager.saved(player.getUuid());
        ServerPlayNetworking.send(player, new PathHologramPayload(
                true,
                path.closed(),
                path.points().stream()
                        .map(point -> new BlockPos(point.x(), point.y(), point.z()))
                        .toList()
        ));
    }
}
