package com.komainu.moxia.sculpt.model;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.sculpt.BrushFalloff;
import com.komainu.moxia.sculpt.BrushMask;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.SculptProfile;
import com.komainu.moxia.sculpt.SculptSettings;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Una pasada de un pincel de modelado sobre el motor común: {@code SculptManager.run} captura la zona, este plan se
 * calcula en un worker con {@link ModelSculptor} y el resultado va a la vista previa o a {@code BatchEditService}
 * (una sola entrada de //undo).
 *
 * <p>La foto se traduce a una {@link ModelGrid}: cuenta como masa todo lo que no es aire, líquido ni reemplazable
 * (hierba alta, flores, nieve en capa). La máscara decide qué celdas se pueden tocar.</p>
 */
public record ModelOperation(ModelSettings settings, ModelMaterial added, ModelMaterial fill, CellMask mask)
        implements BrushOperation {
    public static final int MAX_CHANGES = 1_000_000;
    private static final BlockState AIR = Objects.requireNonNull(BlockTypes.AIR).getDefaultState();
    /** Lo que se cubre al enterrarse: la hierba tapada pasa a tierra; el nilio, a netherrack. */
    private static final Map<String, String> BURIED = Map.of(
            "minecraft:grass_block", "minecraft:dirt",
            "minecraft:mycelium", "minecraft:dirt",
            "minecraft:podzol", "minecraft:dirt",
            "minecraft:dirt_path", "minecraft:dirt",
            "minecraft:farmland", "minecraft:dirt",
            "minecraft:crimson_nylium", "minecraft:netherrack",
            "minecraft:warped_nylium", "minecraft:netherrack");

    @Override
    public String displayName() {
        return settings.mode().displayName();
    }

    @Override
    public int previewColor() {
        return switch (settings.mode()) {
            case DRAW -> settings.invert() ? 0xE57373 : 0x81C784;
            case ROCK -> 0xB0A89A;
            case WELD -> 0x4FC3F7;
            case MELT -> 0xFF8A65;
            case ROUGHEN -> 0xCE93D8;
            case SHATTER -> 0xFFD54F;
        };
    }

    @Override
    public String pattern() {
        return added.pattern() == null ? "air" : added.pattern();
    }

    @Override
    public Capture capture() {
        int reach = settings.reach();
        return new Capture(BlockVector3.at(settings.centerX(), settings.centerY(), settings.centerZ()), reach, reach);
    }

    @Override
    public PackedBlockPlan plan(WorldSnapshot snapshot) {
        mask.bind(snapshot);
        Table table = new Table();
        ModelGrid grid = grid(snapshot, table);
        ModelSculptor.Result result = ModelSculptor.apply(grid, settings, table.chooser(added, settings.seed()),
                table.chooser(fill, settings.seed() ^ 0x5348415454L), ModelOperation::checkCancelled);
        if (result.changes() > MAX_CHANGES) {
            throw new IllegalArgumentException("El pincel supera el límite de " + ProgressLine.group(MAX_CHANGES) + " bloques");
        }
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(Math.max(16, result.changes()));
        int[] after = result.after();
        for (int index = 0; index < after.length; index++) {
            if (!result.changed(index)) {
                continue;
            }
            int x = grid.x(index);
            int y = grid.y(index);
            int z = grid.z(index);
            int material = after[index];
            if (material == ModelGrid.FREE) {
                builder.addOverride(x, y, z, AIR);
            } else if (material == table.patternId) {
                builder.addPattern(x, y, z);
            } else {
                builder.addOverride(x, y, z, table.state(material));
            }
        }
        return builder.build();
    }

    /**
     * La pasada de {@code /komaedit sculpt} con un tipo de modelado. Lo que trae la orden se reparte así: el radio y
     * la caída tal cual; la intensidad es la altura de esculpir, la amplitud de la rugosidad (la mitad, en bloques),
     * el desenfoque de soldar y fundir ({@code 0,5 + intensidad/4}) y la celda de las grietas ({@code intensidad + 2});
     * hueco ({@code hollow}) invierte esculpir y roca; el ruido de {@code sculpt tune} es la rugosidad de la roca. El
     * patrón es el material de lo añadido ({@code auto}: el de alrededor) o el relleno de las grietas
     * ({@code air}: grietas vacías). La máscara de la brocha decide qué se toca.
     */
    public static ModelOperation fromSculpt(SculptProfile profile, BlockVector3 center, Direction side) {
        SculptSettings sculpt = profile.settings();
        ModelMode mode = sculpt.type().modelMode();
        int intensity = sculpt.intensity();
        ModelSettings settings = ModelSettings.defaults(mode, center.x(), center.y(), center.z(), sculpt.radius())
                .withFalloff(mode == ModelMode.SHATTER && sculpt.falloff() == BrushFalloff.SMOOTH
                        ? BrushFalloff.CONSTANT : sculpt.falloff())
                .withInvert(sculpt.hollow())
                .withSeed(ThreadLocalRandom.current().nextLong())
                .withHint(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
        settings = switch (mode) {
            case DRAW -> settings.withStrength(intensity);
            case ROUGHEN -> settings.withStrength(Math.max(0.5, intensity * 0.5));
            case WELD, MELT -> settings.withSigma(0.5 + intensity * 0.25);
            case SHATTER -> settings.withScale(intensity + 2.0);
            case ROCK -> sculpt.noise() > 0 ? settings.withRoughness(sculpt.noise() / 100.0) : settings;
        };
        String pattern = profile.pattern();
        ModelMaterial material = ModelMaterial.surrounding(pattern) ? ModelMaterial.SURROUNDING
                : ModelMaterial.pattern(pattern);
        ModelMaterial fill = ModelMaterial.AIR;
        if (mode == ModelMode.SHATTER) {
            fill = ModelMaterial.air(pattern) || ModelMaterial.surrounding(pattern) ? ModelMaterial.AIR : material;
            material = ModelMaterial.SURROUNDING;
        }
        BrushMask brushMask = sculpt.mask();
        return new ModelOperation(settings, material, fill,
                (snapshot, x, y, z, state, solid) -> brushMask.allows(state, !solid));
    }

    /** La foto como rejilla: un número por estado con masa y la máscara ya evaluada. */
    private ModelGrid grid(WorldSnapshot snapshot, Table table) {
        BlockVector3 minimum = snapshot.minimum();
        BlockVector3 maximum = snapshot.maximum();
        ModelGrid grid = ModelGrid.create(minimum.x(), minimum.y(), minimum.z(), maximum.x() - minimum.x() + 1,
                maximum.y() - minimum.y() + 1, maximum.z() - minimum.z() + 1);
        IdentityHashMap<BlockState, Integer> ids = new IdentityHashMap<>();
        for (BlockState state : snapshot.distinctStates()) {
            ids.put(state, mass(state) ? table.id(state) : ModelGrid.FREE);
        }
        for (int y = minimum.y(); y <= maximum.y(); y++) {
            checkCancelled();
            for (int z = minimum.z(); z <= maximum.z(); z++) {
                for (int x = minimum.x(); x <= maximum.x(); x++) {
                    BlockState state = snapshot.get(x, y, z);
                    int id = ids.get(state);
                    grid.set(x, y, z, id);
                    grid.setEditable(x, y, z, mask.allows(snapshot, x, y, z, state, id != ModelGrid.FREE));
                }
            }
        }
        return grid;
    }

    /** ¿Es masa para el modelado? Ni aire, ni líquido, ni lo que se reemplaza al construir. */
    public static boolean mass(BlockState state) {
        var material = state.getBlockType().getMaterial();
        return !material.isAir() && !material.isLiquid() && !material.isReplacedDuringPlacement();
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Cálculo cancelado");
        }
    }

    /** Qué celdas puede tocar el pincel. */
    public interface CellMask {
        CellMask ALL = (snapshot, x, y, z, state, solid) -> true;

        /** Conecta la foto antes de evaluar (las máscaras de WorldEdit la leen). */
        default void bind(WorldSnapshot snapshot) {
        }

        /**
         * @param solid si la celda tiene masa: entonces la máscara decide si se puede quitar o cambiar; si no, si se
         *              puede llenar
         */
        boolean allows(WorldSnapshot snapshot, int x, int y, int z, BlockState state, boolean solid);
    }

    /** Estados de la pasada: los de la foto con masa, los de la paleta y, si hace falta, el patrón. */
    private static final class Table {
        private final List<BlockState> states = new ArrayList<>(List.of(AIR));
        private final IdentityHashMap<BlockState, Integer> ids = new IdentityHashMap<>();
        private int patternId = -1;

        int id(BlockState state) {
            return ids.computeIfAbsent(state, key -> {
                states.add(key);
                return states.size() - 1;
            });
        }

        BlockState state(int id) {
            return states.get(id);
        }

        MaterialChooser chooser(ModelMaterial material, long seed) {
            if (material.air()) {
                return MaterialChooser.AIR;
            }
            if (material.pattern() != null) {
                states.add(null);
                patternId = states.size() - 1;
                int pattern = patternId;
                return (x, y, z, inherited, covered) -> pattern;
            }
            List<BlockState> palette = material.palette();
            if (!palette.isEmpty()) {
                int[] palettes = palette.stream().mapToInt(this::id).toArray();
                return (x, y, z, inherited, covered) -> palettes[Math.min(palettes.length - 1,
                        (int) (ModelFields.unit(x, y, z, seed) * palettes.length))];
            }
            Map<Integer, Integer> buried = new HashMap<>();
            return (x, y, z, inherited, covered) -> {
                if (!covered || inherited == ModelGrid.FREE) {
                    return inherited;
                }
                return buried.computeIfAbsent(inherited, key -> {
                    String replacement = BURIED.get(states.get(key).getBlockType().id());
                    BlockType type = replacement == null ? null : BlockTypes.get(replacement);
                    return type == null ? key : id(type.getDefaultState());
                });
            };
        }
    }
}
