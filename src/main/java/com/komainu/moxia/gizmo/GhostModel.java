package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.PackedBlockPlan;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Un objeto ya calculado, listo para moverse: cada bloque con su posición relativa al pivote y su estado (el id
 * crudo de Minecraft, el mismo en el servidor y en el cliente). Se calcula una vez; moverlo, girarlo o voltearlo
 * solo cambia la {@link GhostTransform} con la que se coloca.
 *
 * <p>El pivote es el bloque del centro de la caja: el objeto gira sobre sí mismo. Puede llevar aire (lo que borra al
 * pegarse con aire) y, si viene de una instancia del Studio, el índice de captura de cada bloque. Lógica pura.</p>
 */
public final class GhostModel {
    private final long[] positions;
    private final int[] states;
    private final BitSet air;
    private final int[] captures;
    private final BlockBounds bounds;
    private final int pivotX;
    private final int pivotY;
    private final int pivotZ;
    private final int[] palette;
    private final int airCount;

    private GhostModel(long[] positions, int[] states, BitSet air, int[] captures, BlockBounds bounds, int pivotX,
                       int pivotY, int pivotZ) {
        this.positions = positions;
        this.states = states;
        this.air = air;
        this.captures = captures;
        this.bounds = bounds;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        IntOpenHashSet distinct = new IntOpenHashSet();
        for (int index = 0; index < states.length; index++) {
            if (!air.get(index)) {
                distinct.add(states[index]);
            }
        }
        int[] sorted = distinct.toIntArray();
        Arrays.sort(sorted);
        this.palette = sorted;
        this.airCount = air.cardinality();
    }

    public static Builder builder(int expected) {
        return new Builder(expected);
    }

    public int size() {
        return positions.length;
    }

    /** Posición local (relativa al pivote) empaquetada con {@link PackedBlockPlan#pack}. */
    public long position(int index) {
        return positions[index];
    }

    public long[] positions() {
        return positions;
    }

    public int state(int index) {
        return states[index];
    }

    public int[] states() {
        return states;
    }

    public boolean isAir(int index) {
        return air.get(index);
    }

    public int airCount() {
        return airCount;
    }

    public boolean hasAir() {
        return airCount > 0;
    }

    /** Índice de captura de la instancia del Studio, o {@link PackedBlockPlan#NO_CAPTURE}. */
    public int capture(int index) {
        return captures == null ? PackedBlockPlan.NO_CAPTURE : captures[index];
    }

    public boolean hasCaptures() {
        return captures != null;
    }

    /** La caja local, alrededor del pivote. */
    public BlockBounds bounds() {
        return bounds;
    }

    /** Dónde estaba el pivote en el mundo al calcularse: la posición de partida. */
    public int pivotX() {
        return pivotX;
    }

    public int pivotY() {
        return pivotY;
    }

    public int pivotZ() {
        return pivotZ;
    }

    /** La transformación que deja el objeto donde se calculó. */
    public GhostTransform start() {
        return GhostTransform.at(pivotX, pivotY, pivotZ);
    }

    /**
     * Dónde cae, con {@code transform}, un punto del mundo de partida (un bloque del objeto, un nodo de su
     * trayectoria…): se mueve y gira con el objeto. Devuelve {x, y, z}.
     */
    public int[] carry(int worldX, int worldY, int worldZ, GhostTransform transform) {
        int localX = worldX - pivotX;
        int localY = worldY - pivotY;
        int localZ = worldZ - pivotZ;
        return new int[]{transform.worldX(localX, localY, localZ), transform.worldY(localX, localY, localZ),
                transform.worldZ(localX, localY, localZ)};
    }

    /** Estados distintos que no son aire, ordenados. */
    public int[] palette() {
        return palette;
    }

    public int sizeX() {
        return bounds.maximumX() - bounds.minimumX() + 1;
    }

    public int sizeY() {
        return bounds.maximumY() - bounds.minimumY() + 1;
    }

    public int sizeZ() {
        return bounds.maximumZ() - bounds.minimumZ() + 1;
    }

    /** Junta los bloques en posiciones del mundo; si una posición se repite, gana la última (como al aplicar). */
    public static final class Builder {
        private final LongArrayList positions;
        private final IntArrayList states;
        private final IntArrayList captures;
        private final BitSet air = new BitSet();
        private final Long2IntOpenHashMap seen;
        private boolean anyCapture;

        private Builder(int expected) {
            int capacity = Math.max(16, Math.min(expected, 1 << 22));
            positions = new LongArrayList(capacity);
            states = new IntArrayList(capacity);
            captures = new IntArrayList(capacity);
            seen = new Long2IntOpenHashMap(capacity);
            seen.defaultReturnValue(-1);
        }

        public void add(int x, int y, int z, int state, boolean isAir, int capture) {
            long packed = PackedBlockPlan.pack(x, y, z);
            int index = seen.get(packed);
            if (index < 0) {
                index = positions.size();
                seen.put(packed, index);
                positions.add(packed);
                states.add(state);
                captures.add(capture);
            } else {
                states.set(index, state);
                captures.set(index, capture);
            }
            air.set(index, isAir);
            anyCapture |= capture != PackedBlockPlan.NO_CAPTURE;
        }

        public int size() {
            return positions.size();
        }

        public GhostModel build() {
            int count = positions.size();
            if (count == 0) {
                throw new IllegalStateException("El objeto no tiene bloques");
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int index = 0; index < count; index++) {
                long packed = positions.getLong(index);
                int x = PackedBlockPlan.unpackX(packed);
                int y = PackedBlockPlan.unpackY(packed);
                int z = PackedBlockPlan.unpackZ(packed);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
                maxZ = Math.max(maxZ, z);
            }
            int pivotX = Math.floorDiv(minX + maxX, 2);
            int pivotY = Math.floorDiv(minY + maxY, 2);
            int pivotZ = Math.floorDiv(minZ + maxZ, 2);
            long[] local = new long[count];
            for (int index = 0; index < count; index++) {
                long packed = positions.getLong(index);
                local[index] = PackedBlockPlan.pack(PackedBlockPlan.unpackX(packed) - pivotX,
                        PackedBlockPlan.unpackY(packed) - pivotY, PackedBlockPlan.unpackZ(packed) - pivotZ);
            }
            BlockBounds bounds = new BlockBounds(minX - pivotX, minY - pivotY, minZ - pivotZ, maxX - pivotX,
                    maxY - pivotY, maxZ - pivotZ);
            return new GhostModel(local, states.toIntArray(), (BitSet) air.clone(),
                    anyCapture ? captures.toIntArray() : null, bounds, pivotX, pivotY, pivotZ);
        }
    }
}
