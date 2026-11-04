package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Estructuras de Minecraft ({@code .nbt}, las del bloque de estructura, {@code /place template} y los datapacks):
 *
 * <pre>
 * {DataVersion, size: [x, y, z], palette: [{Name, Properties}] (o palettes: [[…], …]),
 *  blocks: [{pos: [x, y, z], state: n, nbt: {id, …}}], entities: [{pos: [d, d, d], blockPos: [x, y, z], nbt: {id, …}}]}
 * </pre>
 *
 * <ul>
 *     <li>Lo que no sale en {@code blocks} es vacío de estructura: al leer queda como aire (KomaEdit no pisa con el aire
 *     de serie) y al escribir se listan todos los bloques, también el aire, como guarda el bloque de estructura.</li>
 *     <li>Con varias paletas ({@code palettes}, los naufragios y otras con variantes) se lee la primera.</li>
 *     <li>El origen es la esquina mínima: al escribir, el desplazamiento respecto al origen se pierde.</li>
 *     <li>Sin {@code DataVersion} es de antes de 1.13 y pasa por el DataFixer desde la 500, como hace Minecraft.</li>
 * </ul>
 */
public final class StructureCodec {
    /** Lo más grande que guarda y carga el bloque de estructura; más grande se coloca con {@code /place template}. */
    public static final int STRUCTURE_BLOCK_SIDE = 48;
    private static final int UNVERSIONED = 500;

    private StructureCodec() {
    }

    public static boolean matches(NbtCompound root) {
        return root.contains("size", NbtElement.LIST_TYPE) && root.contains("blocks", NbtElement.LIST_TYPE)
                && (root.contains("palette", NbtElement.LIST_TYPE) || root.contains("palettes", NbtElement.LIST_TYPE));
    }

    public static SchematicData read(NbtCompound root, SchematicLimits limits, List<String> warnings) {
        NbtList sizeTag = root.getList("size", NbtElement.INT_TYPE);
        if (sizeTag.size() != 3) {
            throw new IllegalArgumentException("A la estructura le falta el tamaño");
        }
        int sizeX = sizeTag.getInt(0);
        int sizeY = sizeTag.getInt(1);
        int sizeZ = sizeTag.getInt(2);
        limits.checkBox(sizeX, sizeY, sizeZ);
        int dataVersion = root.contains("DataVersion", NbtElement.NUMBER_TYPE) ? root.getInt("DataVersion")
                : UNVERSIONED;

        NbtList paletteTag;
        if (root.contains("palettes", NbtElement.LIST_TYPE)) {
            NbtList palettes = root.getList("palettes", NbtElement.LIST_TYPE);
            if (palettes.isEmpty()) {
                throw new IllegalArgumentException("La estructura no tiene paleta");
            }
            paletteTag = palettes.getList(0);
            if (palettes.size() > 1) {
                warnings.add("La estructura trae " + palettes.size() + " variantes de paleta: se usa la primera");
            }
        } else {
            paletteTag = root.getList("palette", NbtElement.COMPOUND_TYPE);
        }
        int paletteSize = paletteTag.size();
        if (paletteSize < 1) {
            throw new IllegalArgumentException("La estructura no tiene paleta");
        }
        if (paletteSize > limits.maxPalette()) {
            throw new IllegalArgumentException("La paleta de la estructura tiene " + paletteSize
                    + " estados; el máximo es " + limits.maxPalette());
        }
        List<String> palette = new ArrayList<>(paletteSize + 1);
        for (int index = 0; index < paletteSize; index++) {
            try {
                palette.add(BlockStates.fromNbt(paletteTag.getCompound(index)));
            } catch (IllegalArgumentException exception) {
                BlockStates.warn(warnings, BlockStates.UNREADABLE);
                palette.add(SchematicData.AIR);
            }
        }
        // Lo que no está en la lista es vacío de estructura: aire, en un índice propio al final.
        int empty = palette.size();
        palette.add(SchematicData.AIR);
        int[] blocks = new int[sizeX * sizeY * sizeZ];
        Arrays.fill(blocks, empty);

        NbtList blockList = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        if (blockList.size() > blocks.length) {
            throw new IllegalArgumentException("La estructura trae más bloques que su tamaño");
        }
        List<SchematicData.BlockEntity> blockEntities = new ArrayList<>();
        int dropped = 0;
        for (int index = 0; index < blockList.size(); index++) {
            NbtCompound entry = blockList.getCompound(index);
            int[] pos = SchematicNbt.vector(entry, "pos");
            if (pos == null || !SpongeCodec.inside(pos, sizeX, sizeY, sizeZ)) {
                throw new IllegalArgumentException("La estructura tiene un bloque fuera de su tamaño");
            }
            int state = entry.getInt("state");
            if (state < 0 || state >= paletteSize) {
                throw new IllegalArgumentException("Los bloques de la estructura apuntan fuera de la paleta");
            }
            blocks[pos[0] + pos[2] * sizeX + pos[1] * sizeX * sizeZ] = state;
            if (entry.contains("nbt", NbtElement.COMPOUND_TYPE)) {
                NbtCompound data = entry.getCompound("nbt").copy();
                String id = data.getString("id");
                if (id.isEmpty()) {
                    dropped++;
                    continue;
                }
                if (blockEntities.size() >= limits.maxBlockEntities()) {
                    throw new IllegalArgumentException("La estructura trae más de " + limits.maxBlockEntities()
                            + " bloques con datos");
                }
                SpongeCodec.strip(data, "id", "x", "y", "z");
                blockEntities.add(new SchematicData.BlockEntity(pos[0], pos[1], pos[2], id, data));
            }
        }

        NbtList entityList = root.getList("entities", NbtElement.COMPOUND_TYPE);
        if (entityList.size() > limits.maxEntities()) {
            throw new IllegalArgumentException("La estructura trae " + entityList.size() + " entidades; el máximo es "
                    + limits.maxEntities());
        }
        List<SchematicData.Entity> entities = new ArrayList<>();
        for (int index = 0; index < entityList.size(); index++) {
            NbtCompound entry = entityList.getCompound(index);
            double[] pos = SchematicNbt.position(entry, "pos");
            NbtCompound data = entry.getCompound("nbt").copy();
            String id = data.getString("id");
            if (pos == null || id.isEmpty()) {
                dropped++;
                continue;
            }
            SpongeCodec.strip(data, "id", "Pos", "UUID");
            entities.add(new SchematicData.Entity(pos[0], pos[1], pos[2], id, data));
        }
        if (dropped > 0) {
            warnings.add(dropped + " datos de bloque o entidades sin id o sin posición, quitados");
        }
        return new SchematicData(sizeX, sizeY, sizeZ, palette, blocks, blockEntities, entities, dataVersion, 0, 0, 0,
                SchematicData.Info.EMPTY);
    }

    /** La estructura (sin comprimir), con todos los bloques de la caja. */
    public static NbtCompound write(SchematicData data) {
        SchematicData compact = data.compact();
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", compact.dataVersion());
        root.put("size", SchematicNbt.ints(compact.sizeX(), compact.sizeY(), compact.sizeZ()));
        NbtList palette = new NbtList();
        for (String state : compact.palette()) {
            palette.add(BlockStates.toNbt(state));
        }
        root.put("palette", palette);
        Map<Long, SchematicData.BlockEntity> withData = new HashMap<>();
        for (SchematicData.BlockEntity entity : compact.blockEntities()) {
            withData.put(key(entity.x(), entity.y(), entity.z()), entity);
        }
        NbtList blocks = new NbtList();
        NbtList last = new NbtList();
        int sizeX = compact.sizeX();
        int sizeZ = compact.sizeZ();
        int[] indices = compact.blocks();
        for (int index = 0; index < indices.length; index++) {
            int y = index / (sizeX * sizeZ);
            int rest = index - y * sizeX * sizeZ;
            int z = rest / sizeX;
            int x = rest - z * sizeX;
            NbtCompound entry = new NbtCompound();
            entry.put("pos", SchematicNbt.ints(x, y, z));
            entry.putInt("state", indices[index]);
            SchematicData.BlockEntity entity = withData.get(key(x, y, z));
            if (entity != null) {
                NbtCompound nbt = entity.data().copy();
                nbt.putString("id", entity.id());
                entry.put("nbt", nbt);
                // Como Minecraft: los bloques con datos, al final.
                last.add(entry);
            } else {
                blocks.add(entry);
            }
        }
        blocks.addAll(last);
        root.put("blocks", blocks);
        NbtList entities = new NbtList();
        for (SchematicData.Entity entity : compact.entities()) {
            NbtCompound entry = new NbtCompound();
            entry.put("pos", SchematicNbt.doubles(entity.x(), entity.y(), entity.z()));
            entry.put("blockPos", SchematicNbt.ints((int) Math.floor(entity.x()), (int) Math.floor(entity.y()),
                    (int) Math.floor(entity.z())));
            NbtCompound nbt = entity.data().copy();
            nbt.putString("id", entity.id());
            nbt.put("Pos", SchematicNbt.doubles(entity.x(), entity.y(), entity.z()));
            entry.put("nbt", nbt);
            entities.add(entry);
        }
        root.put("entities", entities);
        return root;
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 42) | ((long) y << 21) | z;
    }
}
