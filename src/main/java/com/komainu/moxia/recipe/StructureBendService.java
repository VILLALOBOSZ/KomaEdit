package com.komainu.moxia.recipe;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.tube.TubeBender;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.world.block.BlockState;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dobla el portapapeles de WorldEdit a lo largo de la trayectoria: repite el módulo copia tras copia o estira una
 * sola copia por todo el recorrido. La geometría la calcula {@link TubeBender}; aquí se capturan los bloques y se
 * giran sus estados (escaleras, troncos, vallas…) en cuartos de vuelta para que sigan la curva.
 */
public final class StructureBendService {
    private static final int MAX_TEMPLATE_BLOCKS = 100_000;
    private static final int MAX_RESULT_BLOCKS = 1_500_000;

    private StructureBendService() {
    }

    /** Lee el portapapeles en el hilo del servidor; el resto del trabajo puede ir a un worker. */
    public static Captured capture(ServerPlayerEntity player) {
        try {
            Clipboard clipboard = WorldEditService.clipboard(player);
            if (clipboard.getRegion().getVolume() > MAX_TEMPLATE_BLOCKS) {
                throw new IllegalArgumentException("El portapapeles supera 100 000 bloques; copia un módulo más pequeño");
            }
            BlockVector3 origin = clipboard.getOrigin();
            Builder builder = new Builder();
            for (BlockVector3 source : clipboard.getRegion()) {
                BlockState block = clipboard.getFullBlock(source).toImmutableState();
                if (!block.getBlockType().getMaterial().isAir()) {
                    builder.add(source.x() - origin.x(), source.y() - origin.y(), source.z() - origin.z(), block);
                }
            }
            return builder.build();
        } catch (EmptyClipboardException exception) {
            throw new IllegalArgumentException("Copia con WorldEdit (//copy) el módulo que quieres doblar", exception);
        }
    }

    static Captured fromKitbash(KitbashSocketService.Template template) {
        Builder builder = new Builder();
        for (KitbashSocketService.Voxel voxel : template.voxels()) {
            builder.add(voxel.x(), voxel.y(), voxel.z(), voxel.block());
        }
        return builder.build();
    }

    public static PackedBlockPlan plan(Captured captured, PathSnapshot path, TubeBender.Settings settings) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(Math.max(16, captured.template().size() * 4));
        append(builder, captured, path, settings);
        return builder.build();
    }

    public static int append(
            PackedBlockPlan.Builder builder,
            Captured captured,
            PathSnapshot path,
            TubeBender.Settings settings
    ) {
        SampledPath sampled = SampledPath.createSmooth(path.points(), path.closed());
        Long2IntOpenHashMap bent = TubeBender.bend(sampled, captured.template(), settings, MAX_RESULT_BLOCKS);
        BlockState[][] rotated = new BlockState[captured.palette().size()][4];
        for (Long2IntMap.Entry entry : Long2IntMaps.fastIterable(bent)) {
            long packed = entry.getLongKey();
            int encoded = entry.getIntValue();
            int index = TubeBender.paletteIndex(encoded);
            int turns = TubeBender.quarterTurns(encoded);
            BlockState state = rotated[index][turns];
            if (state == null) {
                state = rotate(captured.palette().get(index), turns);
                rotated[index][turns] = state;
            }
            builder.addOverride(
                    PackedBlockPlan.unpackX(packed),
                    PackedBlockPlan.unpackY(packed),
                    PackedBlockPlan.unpackZ(packed),
                    state
            );
        }
        return bent.size();
    }

    private static BlockState rotate(BlockState state, int turns) {
        if (turns == 0) {
            return state;
        }
        return BlockTransformExtent.transform(state, new AffineTransform().rotateY(90.0 * turns));
    }

    public record Captured(List<BlockState> palette, TubeBender.Template template) {
        public int blocks() {
            return template.size();
        }
    }

    private static final class Builder {
        private final List<BlockState> palette = new ArrayList<>();
        private final Map<BlockState, Integer> indices = new HashMap<>();
        private final TubeBender.Template template = new TubeBender.Template();

        void add(int x, int y, int z, BlockState block) {
            Integer index = indices.get(block);
            if (index == null) {
                index = palette.size();
                palette.add(block);
                indices.put(block, index);
            }
            template.add(x, y, z, index);
        }

        Captured build() {
            if (template.size() == 0) {
                throw new IllegalArgumentException("El portapapeles no contiene bloques sólidos");
            }
            return new Captured(List.copyOf(palette), template);
        }
    }
}
