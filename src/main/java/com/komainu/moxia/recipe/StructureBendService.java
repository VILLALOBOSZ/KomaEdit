package com.komainu.moxia.recipe;

import com.komainu.moxia.brush.place.PlacementTemplate;
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

    /**
     * Captura una estructura de colocación ({@code Clipboard}, {@code Schematic(...)}, {@code TS(...)}…) para
     * doblarla, escalada por vecino más cercano: {@code scale} en la sección y {@code alongScale} a lo largo de su
     * eje +x, que es el que avanza con el recorrido.
     */
    public static Captured fromPlacement(PlacementTemplate template, double scale, double alongScale) {
        Long2IntOpenHashMap cells = new Long2IntOpenHashMap();
        Builder source = new Builder();
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        template.forEach((x, y, z, state) -> {
            if (state.getBlockType().getMaterial().isAir()) {
                return;
            }
            source.add(x, y, z, state);
            cells.put(PackedBlockPlan.pack(x, y, z), (int) source.indices.get(state));
            bounds[0] = Math.min(bounds[0], x);
            bounds[1] = Math.min(bounds[1], y);
            bounds[2] = Math.min(bounds[2], z);
            bounds[3] = Math.max(bounds[3], x);
            bounds[4] = Math.max(bounds[4], y);
            bounds[5] = Math.max(bounds[5], z);
        });
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("La estructura no contiene bloques sólidos");
        }
        if (cells.size() > MAX_TEMPLATE_BLOCKS) {
            throw new IllegalArgumentException("La estructura supera 100 000 bloques; usa una más pequeña");
        }
        if (Math.abs(scale - 1.0) < 1.0E-9 && Math.abs(alongScale - 1.0) < 1.0E-9) {
            return source.build();
        }
        int minimumX = (int) Math.floor(bounds[0] * alongScale);
        int maximumX = (int) Math.ceil((bounds[3] + 1) * alongScale) - 1;
        int minimumY = (int) Math.floor(bounds[1] * scale);
        int maximumY = (int) Math.ceil((bounds[4] + 1) * scale) - 1;
        int minimumZ = (int) Math.floor(bounds[2] * scale);
        int maximumZ = (int) Math.ceil((bounds[5] + 1) * scale) - 1;
        long volume = (long) (maximumX - minimumX + 1) * (maximumY - minimumY + 1) * (maximumZ - minimumZ + 1);
        if (volume > 8_000_000L) {
            throw new IllegalArgumentException("La estructura escalada sería demasiado grande; reduce el radio o -s");
        }
        Builder scaled = new Builder();
        for (int x = minimumX; x <= maximumX; x++) {
            int sourceX = (int) Math.floor((x + 0.5) / alongScale);
            for (int y = minimumY; y <= maximumY; y++) {
                int sourceY = (int) Math.floor((y + 0.5) / scale);
                for (int z = minimumZ; z <= maximumZ; z++) {
                    int sourceZ = (int) Math.floor((z + 0.5) / scale);
                    long key = PackedBlockPlan.pack(sourceX, sourceY, sourceZ);
                    if (cells.containsKey(key)) {
                        scaled.add(x, y, z, source.palette.get(cells.get(key)));
                    }
                }
            }
        }
        return scaled.build();
    }

    /** Radio propio de una estructura: lo que se aleja de su eje +x (el que seguirá el recorrido) en la sección. */
    public static double inherentRadius(PlacementTemplate template) {
        double[] radius = {0.5};
        template.forEach((x, y, z, state) -> {
            if (!state.getBlockType().getMaterial().isAir()) {
                radius[0] = Math.max(radius[0], Math.hypot(y, z) + 0.5);
            }
        });
        return radius[0];
    }

    public static PackedBlockPlan plan(Captured captured, PathSnapshot path, TubeBender.Settings settings) {
        return plan(captured, path, settings, TubeBender.Section.NONE);
    }

    /** Con la sección variable de {@code //ezspline structure} (radios por fotogramas, {@code -t}, {@code -r}). */
    public static PackedBlockPlan plan(Captured captured, PathSnapshot path, TubeBender.Settings settings,
                                       TubeBender.Section section) {
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(Math.max(16, captured.template().size() * 4));
        append(builder, captured, path, settings, section);
        return builder.build();
    }

    public static int append(
            PackedBlockPlan.Builder builder,
            Captured captured,
            PathSnapshot path,
            TubeBender.Settings settings
    ) {
        return append(builder, captured, path, settings, TubeBender.Section.NONE);
    }

    public static int append(
            PackedBlockPlan.Builder builder,
            Captured captured,
            PathSnapshot path,
            TubeBender.Settings settings,
            TubeBender.Section section
    ) {
        SampledPath sampled = SampledPath.createSmooth(path.points(), path.closed());
        Long2IntOpenHashMap bent = TubeBender.bend(sampled, captured.template(), settings, section,
                MAX_RESULT_BLOCKS);
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
