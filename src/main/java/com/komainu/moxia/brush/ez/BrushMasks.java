package com.komainu.moxia.brush.ez;

import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.SnapshotExtent;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

/**
 * Lee la {@code -m} de los pinceles. Una lista sencilla ({@code #fullblock}, {@code grass_block,moss_block},
 * {@code #solid,!sand}) se queda en {@link BlockFilter}, con su significado de siempre; cualquier otra cosa se parsea
 * como máscara de WorldEdit (con las {@code #…} de KomaEdit) y se evalúa sobre la foto de la pasada.
 *
 * <p>Se parsea en el hilo del servidor en cada clic: así lo que la máscara fija al leerse ({@code #aim}, el origen de
 * {@code #radialgradientmask}) es lo del momento del clic, y cada pasada tiene su propio extent.</p>
 */
public final class BrushMasks {
    private static final Set<String> CLASSES = Set.of("#fullblock", "#full", "#entero", "#solid", "#solido",
            "#existing", "#existente", "#*");

    private BrushMasks() {
    }

    /** @param text la máscara o {@code null} para la de serie ({@code #fullblock}) */
    public static BrushMask parse(ServerPlayerEntity player, String text) {
        if (text == null) {
            return BlockFilter.FULL_BLOCK;
        }
        if (RegionMasks.plainList(text, CLASSES)) {
            return BlockFilter.parse(text);
        }
        return new WorldEditMask(RegionMasks.worldEdit(player, text));
    }

    /** Una máscara de WorldEdit sobre la foto del pincel. */
    static final class WorldEditMask implements BrushMask {
        private final RegionMasks.Parsed parsed;

        WorldEditMask(RegionMasks.Parsed parsed) {
            this.parsed = parsed;
        }

        @Override
        public boolean matches(BrushWorld world, int x, int y, int z) {
            return parsed.test(x, y, z);
        }

        @Override
        public void bind(WorldSnapshot snapshot) {
            parsed.extent().bind(new SnapshotExtent.Source() {
                @Override
                public BlockState state(int x, int y, int z) {
                    return snapshot.get(x, y, z);
                }

                @Override
                public BlockVector3 minimum() {
                    return snapshot.minimum();
                }

                @Override
                public BlockVector3 maximum() {
                    return snapshot.maximum();
                }
            });
        }
    }
}
