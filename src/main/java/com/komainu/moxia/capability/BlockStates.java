package com.komainu.moxia.capability;

import com.komainu.moxia.KomaEditMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.data.family.BlockFamilies;
import net.minecraft.data.family.BlockFamily;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Paso entre los estados de bloque de Minecraft y la lógica pura de las capacidades (propiedades en texto). */
public final class BlockStates {
    private static volatile BlockFamilyIndex families;

    private BlockStates() {
    }

    public static String id(Block block) {
        return Registries.BLOCK.getId(block).toString();
    }

    public static Optional<Block> block(String id) {
        Identifier identifier = Identifier.tryParse(id);
        return identifier == null ? Optional.empty() : Registries.BLOCK.getOrEmpty(identifier);
    }

    /** Propiedades del estado para «Retocar», en el orden del bloque. */
    public static List<PropertyCycle.Property> properties(BlockState state) {
        List<PropertyCycle.Property> properties = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            properties.add(new PropertyCycle.Property(property.getName(), values(property), value(state, property)));
        }
        return properties;
    }

    /** Valores actuales de todas las propiedades. */
    public static Map<String, String> values(BlockState state) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            values.put(property.getName(), value(state, property));
        }
        return values;
    }

    /** Propiedades del bloque con sus valores posibles. */
    public static Map<String, List<String>> allowed(Block block) {
        Map<String, List<String>> allowed = new LinkedHashMap<>();
        for (Property<?> property : block.getStateManager().getProperties()) {
            allowed.put(property.getName(), values(property));
        }
        return allowed;
    }

    /** El estado con las propiedades dadas; las que el bloque no tenga o no entienda se ignoran. */
    public static BlockState apply(BlockState base, Map<String, String> values) {
        BlockState result = base;
        for (Property<?> property : base.getProperties()) {
            String value = values.get(property.getName());
            if (value != null) {
                result = with(result, property, value);
            }
        }
        return result;
    }

    /** El estado con una propiedad cambiada. */
    public static BlockState withValue(BlockState state, String name, String value) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return with(state, property, value);
            }
        }
        return state;
    }

    /** ¿Es un bloque de dos partes (puerta, planta alta, cama)? */
    public static boolean multiBlock(Block block) {
        return StateTransfer.multiBlock(allowed(block));
    }

    /** ¿Está cargado el chunk de la posición? Sin cargar nada. */
    public static boolean loaded(World world, BlockPos pos) {
        return world.getChunkManager().isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()),
                ChunkSectionPos.getSectionCoord(pos.getZ()));
    }

    public static Cell cell(BlockPos pos) {
        return new Cell(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos pos(Cell cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }

    public static Cell normal(Direction direction) {
        return new Cell(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }

    /** Las familias de bloques de Minecraft para «Reemplazar familia». Vacío si no se pudieron leer. */
    public static BlockFamilyIndex families() {
        BlockFamilyIndex index = families;
        if (index == null) {
            index = loadFamilies();
            families = index;
        }
        return index;
    }

    private static BlockFamilyIndex loadFamilies() {
        List<BlockFamilyIndex.Family> list = new ArrayList<>();
        try {
            BlockFamilies.getFamilies().forEach(family -> list.add(family(family)));
        } catch (RuntimeException | LinkageError exception) {
            KomaEditMod.LOGGER.warn("Could not read Minecraft block families; family replace is disabled", exception);
        }
        return new BlockFamilyIndex(list);
    }

    private static BlockFamilyIndex.Family family(BlockFamily family) {
        Map<String, String> variants = new LinkedHashMap<>();
        family.getVariants().forEach((variant, block) ->
                variants.put(variant.name().toLowerCase(Locale.ROOT), id(block)));
        String base = id(family.getBaseBlock());
        return new BlockFamilyIndex.Family(base, base, variants);
    }

    private static <T extends Comparable<T>> List<String> values(Property<T> property) {
        List<String> values = new ArrayList<>();
        for (T value : property.getValues()) {
            values.add(property.name(value));
        }
        return values;
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.name(state.get(property));
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.parse(value).map(parsed -> state.with(property, parsed)).orElse(state);
    }
}
