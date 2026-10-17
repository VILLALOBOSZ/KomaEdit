package com.komainu.moxia.brush.ez;

import com.komainu.moxia.edit.SmoothBlockStates;
import com.komainu.moxia.gradient.PaletteSpec;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Paleta de un pincel ya convertida en estados de bloque, con la losa y la escalera del material de cada entrada.
 * Se resuelve en el hilo del servidor (parser de WorldEdit y registros) y después se usa desde el worker.
 */
public final class ResolvedPalette {
    private final String source;
    private final List<BlockState> full;
    private final List<BlockState> slabs;
    private final List<BlockState> stairs;
    private final List<Boolean> explicit;

    private ResolvedPalette(String source, List<BlockState> full, List<BlockState> slabs, List<BlockState> stairs,
                            List<Boolean> explicit) {
        this.source = source;
        this.full = full;
        this.slabs = slabs;
        this.stairs = stairs;
        this.explicit = explicit;
    }

    public static ResolvedPalette resolve(ServerPlayerEntity player, String input, int minimumSize) {
        List<String> entries = PaletteSpec.parse(input);
        if (entries.size() < minimumSize) {
            throw new IllegalArgumentException("La paleta necesita al menos " + minimumSize
                    + " bloques, por ejemplo stone,andesite o ##Grayscale");
        }
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        ParserContext parser = new ParserContext();
        parser.setActor(context.actor());
        parser.setSession(context.session());
        parser.setWorld(context.world());
        parser.setRestricted(!context.actor().hasPermission("worldedit.anyblock"));
        parser.setPreferringWildcard(false);
        List<BlockState> full = new ArrayList<>(entries.size());
        List<BlockState> slabs = new ArrayList<>(entries.size());
        List<BlockState> stairs = new ArrayList<>(entries.size());
        List<Boolean> explicit = new ArrayList<>(entries.size());
        for (String entry : entries) {
            BlockState state;
            try {
                state = WorldEdit.getInstance().getBlockFactory().parseFromInput(entry, parser).toImmutableState();
            } catch (InputParseException exception) {
                throw new IllegalArgumentException("«" + entry + "» no es un bloque: los pinceles de paleta necesitan"
                        + " bloques sueltos (" + exception.getMessage() + ")");
            }
            full.add(state);
            explicit.add(entry.contains("["));
            Block[] family = SmoothBlockStates.pieceFamily(FabricAdapter.adapt(state));
            slabs.add(family[0] == null ? null : FabricAdapter.adapt(family[0].getDefaultState()));
            stairs.add(family[1] == null ? null : FabricAdapter.adapt(family[1].getDefaultState()));
        }
        return new ResolvedPalette(input, Collections.unmodifiableList(full), Collections.unmodifiableList(slabs),
                Collections.unmodifiableList(stairs), List.copyOf(explicit));
    }

    public int size() {
        return full.size();
    }

    public String source() {
        return source;
    }

    public PaletteShift shift(boolean wrap) {
        return new PaletteShift(ids(full), ids(slabs), ids(stairs), wrap);
    }

    /**
     * Estado que pone la entrada {@code index} donde ahora está {@code existing}. Una losa o escalera se cambia por
     * la del material de la entrada conservando su forma ({@code null} si ese material no tiene); un bloque entero
     * hereda las propiedades que compartan (el eje de un tronco) salvo que la entrada fije las suyas.
     */
    public BlockState place(int index, BlockState existing, PaletteShift.Kind kind) {
        return switch (kind) {
            case SLAB -> copyProperties(slabs.get(index), existing);
            case STAIRS -> copyProperties(stairs.get(index), existing);
            case FULL -> explicit.get(index) || existing == null
                    ? full.get(index)
                    : copyProperties(full.get(index), existing);
        };
    }

    /** Clase de pieza de un estado del mundo para {@link #place}. */
    public static PaletteShift.Kind kindOf(BlockState state) {
        String id = state.getBlockType().id();
        if (id.endsWith("_slab")) {
            return PaletteShift.Kind.SLAB;
        }
        if (id.endsWith("_stairs")) {
            return PaletteShift.Kind.STAIRS;
        }
        return PaletteShift.Kind.FULL;
    }

    private static List<String> ids(List<BlockState> states) {
        List<String> ids = new ArrayList<>(states.size());
        for (BlockState state : states) {
            ids.add(state == null ? null : state.getBlockType().id());
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    static BlockState copyProperties(BlockState target, BlockState source) {
        if (target == null) {
            return null;
        }
        if (source == null) {
            return target;
        }
        BlockState result = target;
        for (Map.Entry<Property<?>, Object> entry : source.getStates().entrySet()) {
            Property<Object> property = (Property<Object>) result.getBlockType().getPropertyMap()
                    .get(entry.getKey().getName());
            if (property != null && property.getValues().contains(entry.getValue())) {
                result = result.with(property, entry.getValue());
            }
        }
        return result;
    }
}
