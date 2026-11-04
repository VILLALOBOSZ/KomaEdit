package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un esquema en memoria, sin formato: lo que tienen en común un {@code .litematic}, una estructura {@code .nbt} y un
 * {@code .schem} de WorldEdit. Los códecs ({@link LitematicCodec}, {@link StructureCodec}, {@link SpongeCodec})
 * convierten entre esto y el NBT de cada formato; no toca Minecraft ni WorldEdit más allá de las clases de NBT.
 *
 * <ul>
 *     <li>Los bloques son índices a {@link #palette()}, en el orden de WorldEdit y Litematica:
 *     {@code x + z·ancho + y·ancho·fondo}.</li>
 *     <li>La paleta son estados escritos como en WorldEdit ({@code minecraft:oak_stairs[facing=east,half=bottom]}),
 *     con las propiedades por orden alfabético.</li>
 *     <li>{@link #offsetX()}…: dónde cae la esquina mínima respecto al origen (el punto por el que se pega o se coloca),
 *     como el {@code Offset} de Sponge o la {@code Position} de Litematica.</li>
 *     <li>Los bloques con datos y las entidades van con su posición respecto a la esquina mínima y sus datos sin
 *     {@code id} ni posición.</li>
 * </ul>
 *
 * @param dataVersion la versión de datos de Minecraft con que se escribió
 */
public record SchematicData(
        int sizeX,
        int sizeY,
        int sizeZ,
        List<String> palette,
        int[] blocks,
        List<BlockEntity> blockEntities,
        List<Entity> entities,
        int dataVersion,
        int offsetX,
        int offsetY,
        int offsetZ,
        Info info
) {
    public static final String AIR = "minecraft:air";

    public SchematicData {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("El esquema no tiene tamaño");
        }
        palette = List.copyOf(palette);
        if (palette.isEmpty()) {
            throw new IllegalArgumentException("El esquema no tiene paleta");
        }
        if (blocks.length != (long) sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("Los bloques del esquema no cuadran con su tamaño");
        }
        blockEntities = List.copyOf(blockEntities);
        entities = List.copyOf(entities);
        info = info == null ? Info.EMPTY : info;
    }

    /** Un bloque con datos (cofre, cartel…): posición desde la esquina mínima, id y datos sin id ni posición. */
    public record BlockEntity(int x, int y, int z, String id, NbtCompound data) {
    }

    /** Una entidad: posición desde la esquina mínima, id y datos sin id ni posición. */
    public record Entity(double x, double y, double z, String id, NbtCompound data) {
    }

    /**
     * Lo que se sabe del esquema aparte de los bloques.
     *
     * @param created  milisegundos desde 1970, o 0
     * @param modified milisegundos desde 1970, o 0
     * @param preview  la miniatura de Litematica (ARGB, cuadrada) o {@code null}
     * @param regions  los nombres de las regiones de un {@code .litematic}, en orden
     */
    public record Info(String name, String author, String description, long created, long modified, int[] preview,
                       List<String> regions) {
        public static final Info EMPTY = new Info("", "", "", 0L, 0L, null, List.of());

        public Info {
            name = name == null ? "" : name;
            author = author == null ? "" : author;
            description = description == null ? "" : description;
            regions = List.copyOf(regions == null ? List.of() : regions);
        }

        public Info withPreview(int[] value) {
            return new Info(name, author, description, created, modified, value, regions);
        }
    }

    public long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    public int index(int x, int y, int z) {
        return x + z * sizeX + y * sizeX * sizeZ;
    }

    /** El estado de un bloque, con su posición desde la esquina mínima. */
    public String state(int x, int y, int z) {
        return palette.get(blocks[index(x, y, z)]);
    }

    /** Bloques que no son aire. */
    public int solidBlocks() {
        boolean[] air = new boolean[palette.size()];
        for (int index = 0; index < air.length; index++) {
            air[index] = BlockStates.isAir(palette.get(index));
        }
        int count = 0;
        for (int value : blocks) {
            if (!air[value]) {
                count++;
            }
        }
        return count;
    }

    public SchematicData withPalette(List<String> value) {
        return new SchematicData(sizeX, sizeY, sizeZ, value, blocks, blockEntities, entities, dataVersion, offsetX,
                offsetY, offsetZ, info);
    }

    public SchematicData withInfo(Info value) {
        return new SchematicData(sizeX, sizeY, sizeZ, palette, blocks, blockEntities, entities, dataVersion, offsetX,
                offsetY, offsetZ, value);
    }

    public SchematicData withData(List<String> newPalette, List<BlockEntity> newBlockEntities,
                                  List<Entity> newEntities, int newDataVersion) {
        return new SchematicData(sizeX, sizeY, sizeZ, newPalette, blocks, newBlockEntities, newEntities,
                newDataVersion, offsetX, offsetY, offsetZ, info);
    }

    /**
     * La paleta sin repetidos ni estados que no se usan, con el aire primero (como la escribe Litematica), y los
     * bloques renumerados. Si ya estaba así, devuelve el mismo esquema.
     */
    public SchematicData compact() {
        int[] used = new int[palette.size()];
        for (int value : blocks) {
            used[value]++;
        }
        List<String> merged = new ArrayList<>();
        merged.add(AIR);
        Map<String, Integer> indices = new HashMap<>();
        indices.put(AIR, 0);
        int[] remap = new int[palette.size()];
        for (int index = 0; index < palette.size(); index++) {
            String state = palette.get(index);
            if (used[index] == 0) {
                remap[index] = -1;
                continue;
            }
            Integer target = indices.get(state);
            if (target == null) {
                target = merged.size();
                merged.add(state);
                indices.put(state, target);
            }
            remap[index] = target;
        }
        boolean same = merged.size() == palette.size();
        for (int index = 0; same && index < remap.length; index++) {
            same = remap[index] == index;
        }
        if (same) {
            return this;
        }
        int[] renumbered = new int[blocks.length];
        for (int index = 0; index < blocks.length; index++) {
            renumbered[index] = remap[blocks[index]];
        }
        return new SchematicData(sizeX, sizeY, sizeZ, merged, renumbered, blockEntities, entities, dataVersion,
                offsetX, offsetY, offsetZ, info);
    }
}
