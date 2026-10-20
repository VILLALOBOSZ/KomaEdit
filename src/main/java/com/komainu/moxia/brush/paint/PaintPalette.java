package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.PaletteShift;
import com.komainu.moxia.brush.ez.ResolvedPalette;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * La paleta del pincel de pintura ya resuelta (con {@link ResolvedPalette}: sus estados, losas y escaleras) y lo que
 * hace falta para pintar por pasadas: en qué tono está cada bloque del mundo. Se resuelve una vez al configurar el
 * pincel, en el hilo del servidor; los tonos de cada estado se recuerdan aquí, así cada clic no los busca otra vez.
 */
public final class PaintPalette {
    private final ResolvedPalette resolved;
    /** Id de cada entrada, su losa y su escalera → entrada (la primera si se repite). */
    private final Map<String, Integer> indexById;
    private final Map<BlockState, Integer> byState = new IdentityHashMap<>();

    private PaintPalette(ResolvedPalette resolved) {
        this.resolved = resolved;
        Map<String, Integer> ids = new HashMap<>();
        for (int index = 0; index < resolved.size(); index++) {
            for (PaletteShift.Kind kind : PaletteShift.Kind.values()) {
                BlockState state = resolved.place(index, null, kind);
                if (state != null) {
                    ids.putIfAbsent(state.getBlockType().id(), index);
                }
            }
        }
        this.indexById = Map.copyOf(ids);
    }

    /** {@code input}: una paleta de serie del pincel ({@link PaintPalettes}) o cualquier paleta de KomaEdit. */
    public static PaintPalette resolve(ServerPlayerEntity player, String input) {
        return new PaintPalette(ResolvedPalette.resolve(player, PaintPalettes.expand(input), 2));
    }

    public int size() {
        return resolved.size();
    }

    /** Tono del estado (contando su losa y su escalera), o -1 si no es de la paleta. */
    public int indexOf(BlockState state) {
        synchronized (byState) {
            Integer known = byState.get(state);
            if (known == null) {
                known = indexById.getOrDefault(state.getBlockType().id(), -1);
                if (byState.size() < 4_096) {
                    byState.put(state, known);
                }
            }
            return known;
        }
    }

    /** Lo que pone el tono {@code index} donde está {@code existing}, con su forma; {@code null} si no la tiene. */
    public BlockState place(int index, BlockState existing) {
        return resolved.place(index, existing, ResolvedPalette.kindOf(existing));
    }
}
