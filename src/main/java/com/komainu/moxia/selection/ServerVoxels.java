package com.komainu.moxia.selection;

import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

/**
 * El mundo del servidor visto por la selección: aire, lo que no es sólido (lo que se atraviesa andando: plantas,
 * agua, antorchas…) y lo sólido. Nunca carga un trozo: lo que no está cargado es {@link #UNLOADED}. Guarda el último
 * trozo leído, que casi siempre es el siguiente. Solo en el hilo del servidor.
 */
public final class ServerVoxels implements Voxels {
    private final ServerWorld world;
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;
    private WorldChunk chunk;

    public ServerVoxels(ServerWorld world) {
        this.world = world;
    }

    @Override
    public int kind(int x, int y, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        if (cx != chunkX || cz != chunkZ) {
            chunkX = cx;
            chunkZ = cz;
            chunk = world.getChunkManager().isChunkLoaded(cx, cz) ? world.getChunk(cx, cz) : null;
        }
        if (chunk == null) {
            return UNLOADED;
        }
        cursor.set(x, y, z);
        BlockState state = chunk.getBlockState(cursor);
        if (state.isAir()) {
            return AIR;
        }
        return state.getCollisionShape(world, cursor).isEmpty() ? NON_SOLID : SOLID;
    }

    @Override
    public int minY() {
        return world.getBottomY();
    }

    @Override
    public int maxY() {
        return world.getTopY() - 1;
    }
}
