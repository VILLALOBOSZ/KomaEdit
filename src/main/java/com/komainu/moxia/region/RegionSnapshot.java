package com.komainu.moxia.region;

import com.sk89q.worldedit.world.block.BlockState;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.BitSet;

/**
 * Foto inmutable de una región del mundo para calcular operaciones fuera del hilo del servidor.
 *
 * <p>Guarda el id crudo de cada estado (el mismo que comprueba {@code BatchEditService} antes de escribir, así una
 * operación no pisa bloques que cambien mientras se calcula), una tabla con la información de cada estado distinto
 * y qué posiciones están dentro de la selección de WorldEdit. Alrededor de la selección puede haber un margen
 * capturado para que las operaciones vean a los vecinos.</p>
 */
public final class RegionSnapshot {
    private final int minimumX;
    private final int minimumY;
    private final int minimumZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] ids;
    private final BitSet selected;
    private final Int2ObjectMap<StateInfo> states;
    private final StateInfo air;
    /** Luz de bloque en los 4 bits altos y luz del cielo en los bajos; {@code null} si no se capturó. */
    private final byte[] light;

    RegionSnapshot(
            int minimumX,
            int minimumY,
            int minimumZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            int[] ids,
            BitSet selected,
            Int2ObjectMap<StateInfo> states,
            byte[] light
    ) {
        this.minimumX = minimumX;
        this.minimumY = minimumY;
        this.minimumZ = minimumZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.ids = ids;
        this.selected = selected;
        this.states = states;
        this.light = light;
        StateInfo foundAir = states.get(0);
        this.air = foundAir != null ? foundAir : new StateInfo(0, null, "minecraft:air", true, false, false);
    }

    /** Construye una foto a mano (tests y herramientas sin Minecraft). */
    public static Builder builder(int minimumX, int minimumY, int minimumZ, int sizeX, int sizeY, int sizeZ) {
        return new Builder(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ);
    }

    public int minimumX() {
        return minimumX;
    }

    public int minimumY() {
        return minimumY;
    }

    public int minimumZ() {
        return minimumZ;
    }

    public int maximumX() {
        return minimumX + sizeX - 1;
    }

    public int maximumY() {
        return minimumY + sizeY - 1;
    }

    public int maximumZ() {
        return minimumZ + sizeZ - 1;
    }

    public long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minimumX && x < minimumX + sizeX
                && y >= minimumY && y < minimumY + sizeY
                && z >= minimumZ && z < minimumZ + sizeZ;
    }

    /** Dentro de la selección del jugador (no del margen). */
    public boolean selected(int x, int y, int z) {
        return contains(x, y, z) && selected.get(index(x, y, z));
    }

    public int rawId(int x, int y, int z) {
        return contains(x, y, z) ? ids[index(x, y, z)] : 0;
    }

    public StateInfo info(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return air;
        }
        StateInfo info = states.get(ids[index(x, y, z)]);
        return info == null ? air : info;
    }

    /** Información de un estado leído en la foto por su id crudo; el aire si ese id no aparece. */
    public StateInfo stateInfo(int rawId) {
        StateInfo info = states.get(rawId);
        return info == null ? air : info;
    }

    /** Estado de WorldEdit; {@code null} en fotos construidas sin Minecraft. */
    public BlockState state(int x, int y, int z) {
        return info(x, y, z).state();
    }

    public String blockId(int x, int y, int z) {
        return info(x, y, z).blockId();
    }

    public boolean isAir(int x, int y, int z) {
        return info(x, y, z).air();
    }

    public boolean isLiquid(int x, int y, int z) {
        return info(x, y, z).liquid();
    }

    public boolean isSolid(int x, int y, int z) {
        return info(x, y, z).solid();
    }

    /** Si la foto guarda los niveles de luz (solo cuando la operación los pide). */
    public boolean hasLight() {
        return light != null;
    }

    /** Luz de bloque (0–15) sin contar el cielo; 0 fuera de la foto o si no se capturó la luz. */
    public int blockLight(int x, int y, int z) {
        return light == null || !contains(x, y, z) ? 0 : (light[index(x, y, z)] >> 4) & 15;
    }

    /** Luz del cielo (0–15); fuera de la foto se supone cielo abierto. */
    public int skyLight(int x, int y, int z) {
        if (light == null) {
            return 0;
        }
        return contains(x, y, z) ? light[index(x, y, z)] & 15 : 15;
    }

    /** Luz que emite el bloque (0–15). */
    public int luminance(int x, int y, int z) {
        return info(x, y, z).luminance();
    }

    /** Caras al aire (0–6): la base de casi todas las métricas de exposición. */
    public int exposedFaces(int x, int y, int z) {
        int exposed = 0;
        exposed += isSolid(x + 1, y, z) ? 0 : 1;
        exposed += isSolid(x - 1, y, z) ? 0 : 1;
        exposed += isSolid(x, y + 1, z) ? 0 : 1;
        exposed += isSolid(x, y - 1, z) ? 0 : 1;
        exposed += isSolid(x, y, z + 1) ? 0 : 1;
        exposed += isSolid(x, y, z - 1) ? 0 : 1;
        return exposed;
    }

    private int index(int x, int y, int z) {
        return ((y - minimumY) * sizeZ + (z - minimumZ)) * sizeX + (x - minimumX);
    }

    /**
     * Información de un estado. {@code blockId} es el id con espacio de nombres ({@code minecraft:stone}) y
     * {@code properties} el texto de propiedades de WorldEdit ({@code facing=north,half=top}) o vacío.
     * {@code luminance} es la luz que emite (0–15).
     */
    public record StateInfo(
            int rawId,
            BlockState state,
            String blockId,
            boolean air,
            boolean liquid,
            boolean solid,
            String properties,
            int luminance
    ) {
        public StateInfo(int rawId, BlockState state, String blockId, boolean air, boolean liquid, boolean solid) {
            this(rawId, state, blockId, air, liquid, solid, "", 0);
        }

        public StateInfo(
                int rawId,
                BlockState state,
                String blockId,
                boolean air,
                boolean liquid,
                boolean solid,
                String properties
        ) {
            this(rawId, state, blockId, air, liquid, solid, properties, 0);
        }
    }

    /** Montaje bloque a bloque; lo usan la captura y los tests. */
    public static final class Builder {
        private final int minimumX;
        private final int minimumY;
        private final int minimumZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int[] ids;
        private final BitSet selected;
        private final Int2ObjectMap<StateInfo> states = new Int2ObjectOpenHashMap<>();
        private byte[] light;

        private Builder(int minimumX, int minimumY, int minimumZ, int sizeX, int sizeY, int sizeZ) {
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new IllegalArgumentException("La región está vacía");
            }
            this.minimumX = minimumX;
            this.minimumY = minimumY;
            this.minimumZ = minimumZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.ids = new int[Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)];
            this.selected = new BitSet(ids.length);
        }

        public Builder state(StateInfo info) {
            states.putIfAbsent(info.rawId(), info);
            return this;
        }

        public boolean knows(int rawId) {
            return states.containsKey(rawId);
        }

        public Builder set(int x, int y, int z, int rawId, boolean inSelection) {
            int index = ((y - minimumY) * sizeZ + (z - minimumZ)) * sizeX + (x - minimumX);
            ids[index] = rawId;
            selected.set(index, inSelection);
            return this;
        }

        /** Guarda los niveles de luz de una posición (luz de bloque y del cielo, 0–15). */
        public Builder light(int x, int y, int z, int blockLight, int skyLight) {
            if (light == null) {
                light = new byte[ids.length];
            }
            int index = ((y - minimumY) * sizeZ + (z - minimumZ)) * sizeX + (x - minimumX);
            light[index] = (byte) ((Math.clamp(blockLight, 0, 15) << 4) | Math.clamp(skyLight, 0, 15));
            return this;
        }

        public RegionSnapshot build() {
            return new RegionSnapshot(minimumX, minimumY, minimumZ, sizeX, sizeY, sizeZ, ids, selected,
                    new Int2ObjectOpenHashMap<>(states), light);
        }
    }
}
