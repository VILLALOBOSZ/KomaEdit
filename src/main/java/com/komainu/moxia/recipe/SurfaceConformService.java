package com.komainu.moxia.recipe;

import com.komainu.moxia.path.PathSnapshot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/** «Pegar al terreno» al construir, con el mundo del servidor (el cálculo es el de {@link SurfaceConform}). */
public final class SurfaceConformService {
    private SurfaceConformService() {
    }

    public static PathSnapshot project(
            ServerPlayerEntity player,
            PathSnapshot original,
            RecipeExtensions extensions
    ) {
        return SurfaceConform.project(terrain(player.getServerWorld()), original, extensions);
    }

    private static SurfaceConform.Terrain terrain(ServerWorld world) {
        return new SurfaceConform.Terrain() {
            @Override
            public boolean solid(int x, int y, int z) {
                BlockPos position = new BlockPos(x, y, z);
                if (!world.isChunkLoaded(position)) {
                    return false;
                }
                var state = world.getBlockState(position);
                return !state.isAir() && state.getFluidState().isEmpty();
            }

            @Override
            public boolean empty(int x, int y, int z) {
                BlockPos position = new BlockPos(x, y, z);
                return world.isChunkLoaded(position) && world.getBlockState(position).isAir();
            }
        };
    }
}
