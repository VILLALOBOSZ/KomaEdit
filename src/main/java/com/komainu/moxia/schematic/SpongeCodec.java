package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Esquemas de WorldEdit ({@code .schem}, formato de Sponge): lee las versiones 1, 2 y 3 y escribe la 3, la de los
 * planos. Es el puente entre los otros formatos y WorldEdit: un {@code .litematic} se convierte a esto y WorldEdit lo
 * lee como cualquier esquema suyo.
 *
 * <ul>
 *     <li>v3: {@code {Schematic: {Version: 3, DataVersion, Width, Height, Length, Offset, Metadata, Blocks: {Palette,
 *     Data, BlockEntities}, Entities}}}; posiciones desde la esquina mínima y datos dentro de {@code Data}.</li>
 *     <li>v1 y v2: todo en la raíz, {@code BlockData} y los datos de bloques y entidades sueltos; la posición de las
 *     entidades es la del mundo, y el origen sale de {@code Metadata.WEOffset*}.</li>
 * </ul>
 * Los biomas no se leen: ni Litematica ni las estructuras los llevan.
 */
public final class SpongeCodec {
    public static final int VERSION = 3;

    private SpongeCodec() {
    }

    /** ¿Es un esquema de Sponge (cualquier versión)? */
    public static boolean matches(NbtCompound root) {
        NbtCompound body = root.contains("Schematic", NbtElement.COMPOUND_TYPE) ? root.getCompound("Schematic") : root;
        return body.contains("Version", NbtElement.INT_TYPE) && body.contains("Width", NbtElement.NUMBER_TYPE)
                && (body.contains("Blocks", NbtElement.COMPOUND_TYPE) || body.contains("Palette", NbtElement.COMPOUND_TYPE));
    }

    public static int version(NbtCompound root) {
        NbtCompound body = root.contains("Schematic", NbtElement.COMPOUND_TYPE) ? root.getCompound("Schematic") : root;
        return body.getInt("Version");
    }

    /**
     * Los topes de un {@code sponge.3} que se queda tal cual, sin pasar por {@link #read}: el tamaño de la caja, la
     * paleta y cuántos bloques con datos y entidades trae. Sin esto, uno con {@code DataVersion} de una versión más
     * nueva se saltaba todos los topes (medio millón de soportes de armadura en unos pocos KiB).
     */
    public static void checkLimits(NbtCompound root, SchematicLimits limits) {
        NbtCompound body = root.contains("Schematic", NbtElement.COMPOUND_TYPE) ? root.getCompound("Schematic") : root;
        limits.checkBox(Short.toUnsignedInt(body.getShort("Width")), Short.toUnsignedInt(body.getShort("Height")),
                Short.toUnsignedInt(body.getShort("Length")));
        NbtCompound blocks = body.getCompound("Blocks");
        for (NbtCompound container : List.of(blocks, body.getCompound("Biomes"))) {
            int size = container.getCompound("Palette").getSize();
            if (size > limits.maxPalette()) {
                throw new IllegalArgumentException("La paleta del esquema tiene " + size + " estados; el máximo es "
                        + limits.maxPalette());
            }
        }
        int blockEntities = blocks.getList("BlockEntities", NbtElement.COMPOUND_TYPE).size();
        if (blockEntities > limits.maxBlockEntities()) {
            throw new IllegalArgumentException("El esquema trae " + blockEntities
                    + " bloques con datos; el máximo es " + limits.maxBlockEntities());
        }
        int entities = body.getList("Entities", NbtElement.COMPOUND_TYPE).size();
        if (entities > limits.maxEntities()) {
            throw new IllegalArgumentException("El esquema trae " + entities + " entidades; el máximo es "
                    + limits.maxEntities());
        }
    }

    public static SchematicData read(NbtCompound root, SchematicLimits limits, List<String> warnings) {
        NbtCompound body = root.contains("Schematic", NbtElement.COMPOUND_TYPE) ? root.getCompound("Schematic") : root;
        int version = body.getInt("Version");
        if (version < 1 || version > VERSION) {
            throw new IllegalArgumentException("Esquema de WorldEdit de una versión que no se conoce (" + version + ")");
        }
        int sizeX = Short.toUnsignedInt(body.getShort("Width"));
        int sizeY = Short.toUnsignedInt(body.getShort("Height"));
        int sizeZ = Short.toUnsignedInt(body.getShort("Length"));
        limits.checkBox(sizeX, sizeY, sizeZ);
        int volume = (int) ((long) sizeX * sizeY * sizeZ);
        // v1 no lleva versión de datos: es de 1.13 (la primera con estados de bloque con nombre).
        int dataVersion = body.contains("DataVersion", NbtElement.INT_TYPE) ? body.getInt("DataVersion") : 1631;
        NbtCompound blocks = version >= 3 ? body.getCompound("Blocks") : body;
        String paletteKey = "Palette";
        String dataKey = version >= 3 ? "Data" : "BlockData";
        if (!blocks.contains(paletteKey, NbtElement.COMPOUND_TYPE) || !blocks.contains(dataKey, NbtElement.BYTE_ARRAY_TYPE)) {
            throw new IllegalArgumentException("Al esquema le falta la paleta o los bloques");
        }
        List<String> palette = palette(blocks.getCompound(paletteKey), limits, warnings);
        int[] indices = varints(blocks.getByteArray(dataKey), volume, palette.size());

        int[] offset = {0, 0, 0};
        int[] worldMin = {0, 0, 0};
        if (version >= 3) {
            int[] value = SchematicNbt.vector(body, "Offset");
            if (value != null) {
                offset = value;
            }
        } else {
            int[] value = SchematicNbt.vector(body, "Offset");
            if (value != null) {
                worldMin = value;
            }
            NbtCompound metadata = body.getCompound("Metadata");
            if (metadata.contains("WEOffsetX", NbtElement.INT_TYPE)) {
                offset = new int[]{metadata.getInt("WEOffsetX"), metadata.getInt("WEOffsetY"),
                        metadata.getInt("WEOffsetZ")};
            }
        }

        String entitiesKey = blocks.contains("BlockEntities", NbtElement.LIST_TYPE) ? "BlockEntities" : "TileEntities";
        List<SchematicData.BlockEntity> blockEntities = new ArrayList<>();
        NbtList blockEntityList = blocks.getList(entitiesKey, NbtElement.COMPOUND_TYPE);
        if (blockEntityList.size() > limits.maxBlockEntities()) {
            throw new IllegalArgumentException("El esquema trae " + blockEntityList.size()
                    + " bloques con datos; el máximo es " + limits.maxBlockEntities());
        }
        int dropped = 0;
        for (int index = 0; index < blockEntityList.size(); index++) {
            NbtCompound entry = blockEntityList.getCompound(index);
            int[] pos = SchematicNbt.vector(entry, "Pos");
            String id = entry.getString("Id");
            if (pos == null || id.isEmpty() || !inside(pos, sizeX, sizeY, sizeZ)) {
                dropped++;
                continue;
            }
            NbtCompound data = version >= 3 ? entry.getCompound("Data").copy() : entry.copy();
            strip(data, "Id", "Pos", "id", "x", "y", "z");
            blockEntities.add(new SchematicData.BlockEntity(pos[0], pos[1], pos[2], id, data));
        }
        List<SchematicData.Entity> entities = new ArrayList<>();
        NbtList entityList = body.getList("Entities", NbtElement.COMPOUND_TYPE);
        if (entityList.size() > limits.maxEntities()) {
            throw new IllegalArgumentException("El esquema trae " + entityList.size() + " entidades; el máximo es "
                    + limits.maxEntities());
        }
        for (int index = 0; index < entityList.size(); index++) {
            NbtCompound entry = entityList.getCompound(index);
            double[] pos = SchematicNbt.position(entry, "Pos");
            String id = entry.getString("Id");
            if (pos == null || id.isEmpty()) {
                dropped++;
                continue;
            }
            NbtCompound data = version >= 3 ? entry.getCompound("Data").copy() : entry.copy();
            strip(data, "Id", "Pos", "id");
            if (version < 3) {
                pos = new double[]{pos[0] - worldMin[0], pos[1] - worldMin[1], pos[2] - worldMin[2]};
            }
            entities.add(new SchematicData.Entity(pos[0], pos[1], pos[2], id, data));
        }
        if (dropped > 0) {
            warnings.add(dropped + " datos de bloque o entidades sin posición o fuera del esquema, quitados");
        }
        NbtCompound metadata = body.getCompound("Metadata");
        SchematicData.Info info = new SchematicData.Info(SchematicNbt.text(metadata, "Name", 64),
                SchematicNbt.text(metadata, "Author", 64), "", metadata.getLong("Date"), metadata.getLong("Date"),
                null, List.of());
        return new SchematicData(sizeX, sizeY, sizeZ, palette, indices, blockEntities, entities, dataVersion,
                offset[0], offset[1], offset[2], info);
    }

    /** El esquema v3 (sin comprimir), con el origen en el sitio de {@code offset*}. */
    public static NbtCompound write(SchematicData data) {
        SchematicData compact = data.compact();
        NbtCompound body = new NbtCompound();
        body.putInt("Version", VERSION);
        body.putInt("DataVersion", compact.dataVersion());
        NbtCompound metadata = new NbtCompound();
        SchematicData.Info info = compact.info();
        if (!info.name().isEmpty()) {
            metadata.putString("Name", info.name());
        }
        if (!info.author().isEmpty()) {
            metadata.putString("Author", info.author());
        }
        if (info.modified() > 0) {
            metadata.putLong("Date", info.modified());
        }
        // WorldEdit pone el origen en Metadata.WorldEdit.Origin y la esquina en Origin + Offset.
        NbtCompound worldEdit = new NbtCompound();
        worldEdit.putIntArray("Origin", new int[]{0, 0, 0});
        metadata.put("WorldEdit", worldEdit);
        body.put("Metadata", metadata);
        body.putShort("Width", (short) compact.sizeX());
        body.putShort("Height", (short) compact.sizeY());
        body.putShort("Length", (short) compact.sizeZ());
        body.putIntArray("Offset", new int[]{compact.offsetX(), compact.offsetY(), compact.offsetZ()});

        NbtCompound blocks = new NbtCompound();
        NbtCompound palette = new NbtCompound();
        List<String> states = compact.palette();
        for (int index = 0; index < states.size(); index++) {
            palette.putInt(states.get(index), index);
        }
        blocks.put("Palette", palette);
        ByteArrayOutputStream varints = new ByteArrayOutputStream(compact.blocks().length);
        for (int value : compact.blocks()) {
            while ((value & -128) != 0) {
                varints.write(value & 127 | 128);
                value >>>= 7;
            }
            varints.write(value);
        }
        blocks.putByteArray("Data", varints.toByteArray());
        NbtList blockEntities = new NbtList();
        for (SchematicData.BlockEntity entity : compact.blockEntities()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Id", entity.id());
            entry.putIntArray("Pos", new int[]{entity.x(), entity.y(), entity.z()});
            entry.put("Data", entity.data().copy());
            blockEntities.add(entry);
        }
        blocks.put("BlockEntities", blockEntities);
        body.put("Blocks", blocks);
        if (!compact.entities().isEmpty()) {
            NbtList entities = new NbtList();
            for (SchematicData.Entity entity : compact.entities()) {
                NbtCompound entry = new NbtCompound();
                entry.putString("Id", entity.id());
                entry.put("Pos", SchematicNbt.doubles(entity.x(), entity.y(), entity.z()));
                entry.put("Data", entity.data().copy());
                entities.add(entry);
            }
            body.put("Entities", entities);
        }
        NbtCompound root = new NbtCompound();
        root.put("Schematic", body);
        return root;
    }

    // ------------------------------------------------------------------ Interno

    private static List<String> palette(NbtCompound tag, SchematicLimits limits, List<String> warnings) {
        int size = tag.getSize();
        if (size < 1 || size > limits.maxPalette()) {
            throw new IllegalArgumentException("La paleta del esquema tiene " + size + " estados; el máximo es "
                    + limits.maxPalette());
        }
        String[] byIndex = new String[size];
        for (String key : tag.getKeys()) {
            if (tag.getType(key) != NbtElement.INT_TYPE) {
                throw new IllegalArgumentException("La paleta del esquema está dañada");
            }
            int index = tag.getInt(key);
            if (index < 0 || index >= size || byIndex[index] != null) {
                throw new IllegalArgumentException("La paleta del esquema está dañada");
            }
            byIndex[index] = state(key, warnings);
        }
        return List.of(byIndex);
    }

    /** Un estado de la paleta; si no se entiende, aire con aviso. */
    static String state(String text, List<String> warnings) {
        try {
            return BlockStates.canonical(text);
        } catch (IllegalArgumentException exception) {
            BlockStates.warn(warnings, BlockStates.UNREADABLE);
            return SchematicData.AIR;
        }
    }

    private static int[] varints(byte[] data, int volume, int paletteSize) {
        int[] values = new int[volume];
        int count = 0;
        int position = 0;
        while (position < data.length) {
            int value = 0;
            int shift = 0;
            int read;
            do {
                if (position >= data.length || shift > 28) {
                    throw new IllegalArgumentException("Los bloques del esquema están dañados");
                }
                read = data[position++];
                value |= (read & 0x7F) << shift;
                shift += 7;
            } while ((read & 0x80) != 0);
            if (value < 0 || value >= paletteSize) {
                throw new IllegalArgumentException("Los bloques del esquema apuntan fuera de la paleta");
            }
            if (count >= volume) {
                throw new IllegalArgumentException("El esquema trae más bloques que su tamaño");
            }
            values[count++] = value;
        }
        if (count != volume) {
            throw new IllegalArgumentException("Al esquema le faltan bloques (" + count + " de " + volume + ")");
        }
        return values;
    }

    static boolean inside(int[] pos, int sizeX, int sizeY, int sizeZ) {
        return pos[0] >= 0 && pos[0] < sizeX && pos[1] >= 0 && pos[1] < sizeY && pos[2] >= 0 && pos[2] < sizeZ;
    }

    static void strip(NbtCompound data, String... keys) {
        for (String key : keys) {
            data.remove(key);
        }
    }

}
