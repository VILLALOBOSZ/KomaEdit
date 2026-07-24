package com.komainu.moxia.recipe;

import com.komainu.moxia.path.PathSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public final class SurfaceConformService {
    private SurfaceConformService() {
    }

    public static PathSnapshot project(
            ServerPlayerEntity player,
            PathSnapshot original,
            RecipeExtensions extensions
    ) {
        if (extensions.surfaceMode() == SurfaceMode.NONE) {
            return original;
        }
        List<BlockVector3> projected = new ArrayList<>(original.points().size());
        for (BlockVector3 point : original.points()) {
            BlockPos source = new BlockPos(point.x(), point.y(), point.z());
            BlockPos result = switch (extensions.surfaceMode()) {
                case NONE -> source;
                case FLOOR -> floor(player, source, extensions.conformRadius(), extensions.surfaceOffset());
                case CEILING -> ceiling(player, source, extensions.conformRadius(), extensions.surfaceOffset());
                case NEAREST -> nearest(player, source, extensions.conformRadius(), extensions.surfaceOffset());
            };
            projected.add(BlockVector3.at(result.getX(), result.getY(), result.getZ()));
        }
        long unique = projected.stream().distinct().count();
        if (unique < (original.closed() ? 3 : 2)) {
            throw new IllegalArgumentException("El conformado proyectó varios nodos en el mismo bloque");
        }
        return new PathSnapshot(projected, original.closed(), original.source() + " · conformado");
    }

    private static BlockPos floor(ServerPlayerEntity player, BlockPos source, int radius, int offset) {
        for (int distance = 0; distance <= radius; distance++) {
            BlockPos surface = source.down(distance);
            if (solid(player, surface) && empty(player, surface.up())) {
                return surface.up(1 + offset);
            }
        }
        return source;
    }

    private static BlockPos ceiling(ServerPlayerEntity player, BlockPos source, int radius, int offset) {
        for (int distance = 0; distance <= radius; distance++) {
            BlockPos surface = source.up(distance);
            if (solid(player, surface) && empty(player, surface.down())) {
                return surface.down(1 + offset);
            }
        }
        return source;
    }

    private static BlockPos nearest(ServerPlayerEntity player, BlockPos source, int radius, int offset) {
        BlockPos best = source;
        Direction bestDirection = Direction.UP;
        double bestDistance = Double.MAX_VALUE;
        for (int shell = 0; shell <= radius; shell++) {
            for (int x = -shell; x <= shell; x++) {
                for (int y = -shell; y <= shell; y++) {
                    for (int z = -shell; z <= shell; z++) {
                        if (Math.max(Math.max(Math.abs(x), Math.abs(y)), Math.abs(z)) != shell) {
                            continue;
                        }
                        double distance = x * x + y * y + z * z;
                        if (distance >= bestDistance || distance > radius * radius) {
                            continue;
                        }
                        BlockPos block = source.add(x, y, z);
                        if (!solid(player, block)) {
                            continue;
                        }
                        for (Direction direction : Direction.values()) {
                            BlockPos outside = block.offset(direction);
                            if (empty(player, outside)) {
                                double faceDistance = outside.getSquaredDistance(source);
                                if (faceDistance < bestDistance) {
                                    bestDistance = faceDistance;
                                    best = outside;
                                    bestDirection = direction;
                                }
                            }
                        }
                    }
                }
            }
            if (bestDistance <= (shell + 1.0) * (shell + 1.0)) {
                break;
            }
        }
        return bestDistance == Double.MAX_VALUE ? source : best.offset(bestDirection, offset);
    }

    private static boolean solid(ServerPlayerEntity player, BlockPos position) {
        if (!player.getServerWorld().isChunkLoaded(position)) {
            return false;
        }
        var state = player.getServerWorld().getBlockState(position);
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    private static boolean empty(ServerPlayerEntity player, BlockPos position) {
        if (!player.getServerWorld().isChunkLoaded(position)) {
            return false;
        }
        return player.getServerWorld().getBlockState(position).isAir();
    }
}
