package com.komainu.moxia.rebuild;

import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Lo que una reconstrucción animada toca: cada posición con el bloque que tiene que quedar (el de la obra) y el que
 * había antes (para dejarlo todo como estaba al cancelar). Reconstruir la selección deja lo mismo que había, así que
 * ahí los dos son el mismo.
 *
 * <p>Los bloques van en una paleta: la entrada 0 es siempre el aire. Cada entrada lleva su id crudo (el que da el
 * mundo, para comprobar que una posición sigue como se dejó) y sus {@link BlockTraits}. Lo que se escribe de verdad
 * (el estado con sus datos) lo guarda quien monta el modelo, en el mismo orden de la paleta. Java puro.</p>
 */
public final class RebuildModel {
    /** La entrada de la paleta del aire. */
    public static final int AIR = 0;
    /** Sin sitio en el historial de colocación. */
    public static final long NO_HISTORY = -1L;

    private final long[] positions;
    private final int[] target;
    private final int[] before;
    private final long[] history;
    private final int[] raw;
    private final BlockTraits[] traits;
    private final int[] types;
    private final int buildable;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private RebuildModel(Builder builder) {
        this.positions = builder.positions.toLongArray();
        this.target = builder.target.toIntArray();
        this.before = builder.sameBefore ? target : builder.before.toIntArray();
        this.history = builder.anyHistory ? builder.history.toLongArray() : null;
        this.raw = builder.raw.toIntArray();
        this.traits = builder.traits.toArray(BlockTraits[]::new);
        this.types = builder.types.toIntArray();
        int count = 0;
        int lowX = Integer.MAX_VALUE;
        int lowY = Integer.MAX_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int highY = Integer.MIN_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (int index = 0; index < positions.length; index++) {
            long packed = positions[index];
            int x = PackedBlockPlan.unpackX(packed);
            int y = PackedBlockPlan.unpackY(packed);
            int z = PackedBlockPlan.unpackZ(packed);
            lowX = Math.min(lowX, x);
            lowY = Math.min(lowY, y);
            lowZ = Math.min(lowZ, z);
            highX = Math.max(highX, x);
            highY = Math.max(highY, y);
            highZ = Math.max(highZ, z);
            if (target[index] != AIR) {
                count++;
            }
        }
        this.buildable = count;
        boolean empty = positions.length == 0;
        this.minX = empty ? 0 : lowX;
        this.minY = empty ? 0 : lowY;
        this.minZ = empty ? 0 : lowZ;
        this.maxX = empty ? -1 : highX;
        this.maxY = empty ? -1 : highY;
        this.maxZ = empty ? -1 : highZ;
    }

    /**
     * @param sameBefore el bloque de antes es siempre el de la obra (reconstruir la selección)
     */
    public static Builder builder(boolean sameBefore) {
        return new Builder(sameBefore);
    }

    /** Posiciones que toca la reconstrucción (las que se vacían y, si la obra lleva bloque, se vuelven a poner). */
    public int size() {
        return positions.length;
    }

    /** Posiciones donde la obra pone un bloque (sin el aire): los pasos de la animación. */
    public int buildable() {
        return buildable;
    }

    public long position(int entry) {
        return positions[entry];
    }

    public int x(int entry) {
        return PackedBlockPlan.unpackX(positions[entry]);
    }

    public int y(int entry) {
        return PackedBlockPlan.unpackY(positions[entry]);
    }

    public int z(int entry) {
        return PackedBlockPlan.unpackZ(positions[entry]);
    }

    /** La entrada de la paleta que queda al acabar. */
    public int target(int entry) {
        return target[entry];
    }

    /** La entrada de la paleta que había antes de empezar. */
    public int before(int entry) {
        return before[entry];
    }

    /** Si el bloque de antes es siempre el de la obra (reconstruir lo que ya está). */
    public boolean sameBefore() {
        return before == target;
    }

    /** Puesto de la posición en el historial de colocación (mayor = más reciente), o {@link #NO_HISTORY}. */
    public long history(int entry) {
        return history == null ? NO_HISTORY : history[entry];
    }

    public boolean hasHistory() {
        return history != null;
    }

    public int paletteSize() {
        return raw.length;
    }

    /** Id crudo de una entrada de la paleta, el mismo que da el mundo. */
    public int raw(int palette) {
        return raw[palette];
    }

    public BlockTraits traits(int palette) {
        return traits[palette];
    }

    /** El bloque (sin propiedades) de una entrada de la paleta, como número: iguales si es el mismo bloque. */
    public int type(int palette) {
        return types[palette];
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    /** Monta un modelo entrada a entrada. La paleta empieza con el aire en {@link #AIR}. */
    public static final class Builder {
        private final boolean sameBefore;
        private final LongArrayList positions = new LongArrayList();
        private final IntArrayList target = new IntArrayList();
        private final IntArrayList before = new IntArrayList();
        private final LongArrayList history = new LongArrayList();
        private final IntArrayList raw = new IntArrayList();
        private final List<BlockTraits> traits = new ArrayList<>();
        private final IntArrayList types = new IntArrayList();
        private final Object2IntOpenHashMap<String> typeIds = new Object2IntOpenHashMap<>();
        private boolean anyHistory;

        private Builder(boolean sameBefore) {
            this.sameBefore = sameBefore;
            typeIds.defaultReturnValue(-1);
            raw.add(0);
            traits.add(BlockTraits.AIR);
            types.add(type(BlockTraits.AIR.type()));
        }

        /** El id crudo del aire en este mundo (0 en Minecraft). */
        public Builder airRaw(int airRaw) {
            raw.set(AIR, airRaw);
            return this;
        }

        /** Añade una entrada a la paleta y devuelve su número. Quien llama decide cuándo dos bloques son el mismo. */
        public int palette(int rawId, BlockTraits blockTraits) {
            if (blockTraits.air()) {
                throw new IllegalArgumentException("El aire ya está en la paleta (" + AIR + ")");
            }
            raw.add(rawId);
            traits.add(blockTraits);
            types.add(type(blockTraits.type()));
            return raw.size() - 1;
        }

        private int type(String name) {
            int known = typeIds.getInt(name);
            if (known >= 0) {
                return known;
            }
            int id = typeIds.size();
            typeIds.put(name, id);
            return id;
        }

        public int paletteSize() {
            return raw.size();
        }

        /**
         * Una posición. Si la obra y lo de antes son aire no hace falta: no se toca.
         *
         * @param historyRank puesto en el historial de colocación o {@link #NO_HISTORY}
         */
        public Builder add(long packedPosition, int targetPalette, int beforePalette, long historyRank) {
            check(targetPalette);
            check(beforePalette);
            if (sameBefore && targetPalette != beforePalette) {
                throw new IllegalArgumentException("Este modelo reconstruye lo que ya está: antes y después son lo mismo");
            }
            positions.add(packedPosition);
            target.add(targetPalette);
            if (!sameBefore) {
                before.add(beforePalette);
            }
            history.add(historyRank);
            anyHistory |= historyRank != NO_HISTORY;
            return this;
        }

        public Builder add(int x, int y, int z, int targetPalette, int beforePalette) {
            return add(PackedBlockPlan.pack(x, y, z), targetPalette, beforePalette, NO_HISTORY);
        }

        private void check(int palette) {
            if (palette < 0 || palette >= raw.size()) {
                throw new IllegalArgumentException("Entrada de paleta inexistente: " + palette);
            }
        }

        public int size() {
            return positions.size();
        }

        public RebuildModel build() {
            return new RebuildModel(this);
        }
    }
}
