package com.komainu.moxia.region;

import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.enginehub.linbus.tree.LinCompoundTag;

import java.util.ArrayList;
import java.util.List;

/**
 * La parte de {@code tweak wax} que no cabe en el estado del bloque: los carteles (también los colgantes) guardan la
 * cera en sus datos ({@code is_waxed}), y un cartel encerado ya no se puede editar. Se cambia con una edición de
 * WorldEdit, así que {@code //undo} la deshace; va en su propia entrada, aparte de la del cobre.
 */
public final class SignWax {
    /** Columnas de chunks como mucho que se recorren buscando carteles (solo las cargadas cuentan). */
    private static final long MAX_CHUNKS = 1_000_000L;

    private SignWax() {
    }

    /** @return los carteles cambiados */
    public static int apply(ServerPlayerEntity player, boolean wax) throws IncompleteRegionException {
        Region region = WorldEditService.selection(player);
        List<BlockVector3> signs = find(player.getServerWorld(), region, wax);
        if (signs.isEmpty()) {
            return 0;
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        EditSession editSession = context.session().createEditSession(context.actor());
        int changed = 0;
        try {
            for (BlockVector3 position : signs) {
                BaseBlock block = editSession.getFullBlock(position);
                LinCompoundTag data = block.getNbt();
                if (data == null) {
                    continue;
                }
                LinCompoundTag waxed = data.toBuilder().putByte("is_waxed", (byte) (wax ? 1 : 0)).build();
                if (editSession.setBlock(position, block.toImmutableState().toBaseBlock(
                        LazyReference.computed(waxed)))) {
                    changed++;
                }
            }
        } catch (WorldEditException exception) {
            throw new IllegalStateException("No se pudieron encerar los carteles: " + exception.getMessage(),
                    exception);
        } finally {
            editSession.close();
            context.session().remember(editSession);
        }
        return changed;
    }

    /** Carteles de la selección que hay que cambiar, en los chunks cargados. */
    private static List<BlockVector3> find(ServerWorld world, Region region, boolean wax) {
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        int minimumChunkX = minimum.x() >> 4;
        int maximumChunkX = maximum.x() >> 4;
        int minimumChunkZ = minimum.z() >> 4;
        int maximumChunkZ = maximum.z() >> 4;
        long columns = (long) (maximumChunkX - minimumChunkX + 1) * (maximumChunkZ - minimumChunkZ + 1);
        if (columns > MAX_CHUNKS) {
            throw new IllegalArgumentException("La selección es demasiado grande para buscar carteles");
        }
        List<BlockVector3> found = new ArrayList<>();
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                for (BlockEntity entity : world.getChunk(chunkX, chunkZ).getBlockEntities().values()) {
                    if (!(entity instanceof SignBlockEntity sign) || sign.isWaxed() == wax) {
                        continue;
                    }
                    BlockVector3 position = BlockVector3.at(entity.getPos().getX(), entity.getPos().getY(),
                            entity.getPos().getZ());
                    if (region.contains(position)) {
                        found.add(position);
                    }
                }
            }
        }
        return found;
    }
}
