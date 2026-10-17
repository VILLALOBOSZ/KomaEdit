package com.komainu.moxia.placement;

import com.komainu.moxia.brush.place.PlacementTemplate;
import com.komainu.moxia.brush.place.StructureSource;
import com.komainu.moxia.brush.place.StructureSources;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

/**
 * Lleva las estructuras de los comandos de colocación al pincel de colocación ({@code /komaedit pincel place}):
 * {@code Schematic}, {@code Shape}, {@code Expression}, {@code TexturedShape} e {@code Icosphere}. En cada clic la
 * estructura se pasa a bloques con su tamaño natural (20×20×20 las procedurales) y el pincel la orienta.
 */
public final class PlacementBrushStructures {
    private static boolean registered;

    private PlacementBrushStructures() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        StructureSources.register(spec -> source(spec, "Schematic"), "Schematic", "Sc", "Esquema");
        StructureSources.register(spec -> source(spec, "Shape"), "Shape", "Sh", "Forma");
        StructureSources.register(spec -> source(spec, "Expression"), "Expression", "Ex", "Expresion");
        StructureSources.register(spec -> source(spec, "TexturedShape"), "TexturedShape", "TS");
        StructureSources.register(spec -> source(spec, "Icosphere"), "Icosphere", "Ic", "Icosfera", "Roca");
    }

    private static StructureSource source(com.komainu.moxia.brush.place.StructureSpec brushSpec, String name) {
        String text = name + "(" + brushSpec.options().entrySet().stream()
                .map(PlacementBrushStructures::option)
                .collect(Collectors.joining(",")) + ")";
        StructureSpec spec = StructureSpec.parse(text);
        return new Source(spec);
    }

    private static String option(Map.Entry<String, String> entry) {
        String value = entry.getValue();
        char quote = value.indexOf('"') >= 0 ? '\'' : '"';
        return entry.getKey() + ":" + quote + value + quote;
    }

    private record Source(StructureSpec spec) implements StructureSource {
        @Override
        public String describe() {
            return spec.source();
        }

        @Override
        public PlacementTemplate prepare(ServerPlayerEntity player, long seed) {
            PlacementService.Resolved resolved = PlacementService.resolve(player, spec);
            SplittableRandom random = new SplittableRandom(seed);
            int choice = random.nextInt(resolved.structures().size());
            Structure structure = resolved.structures().get(choice);
            List<BlockState> palette = resolved.palettes().get(choice);
            Placement placement = new Placement(structure, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY,
                    structure.blocksPerUnit(), random.nextLong());
            PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(List.of(placement),
                    PlacementVoxelizer.Options.defaults().withPlaceAir(true));
            Pattern pattern = resolved.pattern() == null ? null
                    : WorldEditService.parsePatternUnchecked(WorldEditService.context(player), resolved.pattern());
            BlockState air = BlockTypes.AIR.getDefaultState();
            int size = result.size();
            int[] xs = new int[size];
            int[] ys = new int[size];
            int[] zs = new int[size];
            BlockState[] states = new BlockState[size];
            int index = 0;
            for (Long2LongMap.Entry entry : Long2LongMaps.fastIterable(result.cells())) {
                long key = entry.getLongKey();
                int x = PackedBlockPlan.unpackX(key);
                int y = PackedBlockPlan.unpackY(key);
                int z = PackedBlockPlan.unpackZ(key);
                int value = PlacementVoxelizer.Result.palette(entry.getLongValue());
                xs[index] = x;
                ys[index] = y;
                zs[index] = z;
                states[index] = value < 0 ? air : pattern != null
                        ? pattern.applyBlock(BlockVector3.at(x, y, z)).toImmutableState()
                        : palette.get(value);
                index++;
            }
            return new Template(xs, ys, zs, states, index);
        }
    }

    private record Template(int[] xs, int[] ys, int[] zs, BlockState[] states, int size) implements PlacementTemplate {
        @Override
        public void forEach(Visitor visitor) {
            for (int index = 0; index < size; index++) {
                visitor.accept(xs[index], ys[index], zs[index], states[index]);
            }
        }
    }
}
