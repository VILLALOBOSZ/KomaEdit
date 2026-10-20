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
 * <p>Una máscara de WorldEdit se parsea en el hilo del servidor en cada clic: así lo que fija al leerse ({@code #aim},
 * el origen de {@code #radialgradientmask}, la barra de {@code #hotbar}) es lo del momento del clic, y cada pasada
 * tiene su propio extent. Las listas sencillas no cambian: se leen una vez por pincel ({@link #fixed}).</p>
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

    /**
     * La máscara ya leída si no depende del momento del clic (la de serie o una lista sencilla, que se leen una vez
     * por pincel), o {@code null} si hay que leerla en cada clic (una de WorldEdit).
     */
    public static BrushMask fixed(String text) {
        if (text == null) {
            return BlockFilter.FULL_BLOCK;
        }
        return RegionMasks.plainList(text, CLASSES) ? BlockFilter.parse(text) : null;
    }

    /** La máscara de un clic: la ya leída del pincel ({@link #fixed}) o, si no la hay, leída ahora. */
    public static BrushMask forClick(BrushMask fixed, ServerPlayerEntity player, String text) {
        return fixed != null ? fixed : parse(player, text);
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
