package com.komainu.moxia.brush;

import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BrushManager {
    private static final Map<UUID, BrushSettings> ACTIVE = new ConcurrentHashMap<>();

    private BrushManager() {
    }

    public static void activate(ServerPlayerEntity player, BrushSettings settings) {
        ACTIVE.put(player.getUuid(), settings);
    }

    public static Optional<BrushSettings> settings(UUID playerId) {
        return Optional.ofNullable(ACTIVE.get(playerId));
    }

    public static boolean deactivate(UUID playerId) {
        return ACTIVE.remove(playerId) != null;
    }

    public static int stampTarget(ServerPlayerEntity player)
            throws InputParseException, MaxChangedBlocksException {
        HitResult result = com.komainu.moxia.studio.net.StudioAim.target(player, 160.0);
        if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("Mira la cara de un bloque para estampar la figura");
        }
        return stamp(player, hit);
    }

    public static int stamp(ServerPlayerEntity player, BlockHitResult hit)
            throws InputParseException, MaxChangedBlocksException {
        BrushSettings settings = ACTIVE.get(player.getUuid());
        if (settings == null) {
            throw new IllegalArgumentException("Activa primero un brush de KomaEdit");
        }

        Direction side = hit.getSide();
        BlockPos placement = hit.getBlockPos().offset(side);
        Vec3 normal = new Vec3(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        Vec3 preferredUp;
        if (Math.abs(normal.y()) > 0.9) {
            double yaw = Math.toRadians(player.getYaw());
            preferredUp = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        } else {
            preferredUp = new Vec3(0.0, 1.0, 0.0);
        }
        Vec3 center = new Vec3(placement.getX() + 0.5, placement.getY() + 0.5, placement.getZ() + 0.5);
        var blocks = ShapeGenerator.generateStamp(center, normal, preferredUp, settings.parameters());
        return WorldEditService.applyBlocks(player, blocks, settings.pattern());
    }

    public static void remove(UUID playerId) {
        ACTIVE.remove(playerId);
    }

    public static void clearAll() {
        ACTIVE.clear();
    }
}
