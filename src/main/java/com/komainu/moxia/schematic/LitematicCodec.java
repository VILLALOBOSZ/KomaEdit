package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Esquemas de Litematica ({@code .litematic}), escritos a partir de la descripción pública del formato (sin código ni
 * dependencia del mod).
 *
 * <pre>
 * {Version: 7, SubVersion: 1, MinecraftDataVersion,
 *  Metadata: {Name, Author, Description, EnclosingSize: {x,y,z}, RegionCount, TotalBlocks, TotalVolume,
 *             TimeCreated, TimeModified, PreviewImageData},
 *  Regions: {&lt;nombre&gt;: {Position: {x,y,z}, Size: {x,y,z}, BlockStatePalette: [{Name, Properties}],
 *            BlockStates: [longs], TileEntities: [{x,y,z,id,…}], Entities: [{Pos: [d,d,d], id,…}],
 *            PendingBlockTicks: [], PendingFluidTicks: []}}}
 * </pre>
 *
 * <ul>
 *     <li>{@code Position} es la esquina de la región respecto al origen del esquema; {@code Size} puede ser negativo
 *     en cada eje y entonces la región va de {@code Position + Size + 1} a {@code Position}.</li>
 *     <li>Los bloques van de la esquina mínima de la región en el orden {@code x + z·ancho + y·ancho·fondo}, empaquetados
 *     con {@link PackedStates}.</li>
 *     <li>Los datos de bloque llevan {@code x, y, z} desde la esquina mínima de la región; las entidades, {@code Pos}
 *     desde {@code Position}.</li>
 * </ul>
 * Al leer, varias regiones se juntan en una caja que las abarca a todas (cada una en su sitio; el aire de una no borra
 * lo de otra y, donde se pisan, gana la última por orden alfabético), o se lee solo la que se pida. Los ticks pendientes no se leen: un portapapeles de WorldEdit no los
 * guarda.
 */
public final class LitematicCodec {
    /** La versión del formato que escribe Litematica para Minecraft 1.20.5 y siguientes (datos 3837+). */
    public static final int VERSION = 7;
    public static final int SUB_VERSION = 1;
    /** Lado de la miniatura que se escribe ({@code PreviewImageData}); Litematica hace las suyas de 140. */
    public static final int PREVIEW_SIDE = 140;
    /** Lado de la miniatura más grande que se lee. */
    private static final int MAX_PREVIEW_SIDE = 512;

    private LitematicCodec() {
    }

    public static boolean matches(NbtCompound root) {
        return root.contains("Regions", NbtElement.COMPOUND_TYPE) && root.contains("Version", NbtElement.INT_TYPE);
    }

    /** Una región tal y como viene en el archivo: caja en coordenadas del esquema. */
    private record Region(String name, int[] min, int[] size, int[] position, NbtCompound tag) {
        long volume() {
            return (long) size[0] * size[1] * size[2];
        }
    }

    /** Los nombres de las regiones por orden alfabético, sin leer sus bloques. */
    public static List<String> regions(NbtCompound root) {
        return sorted(root.getCompound("Regions"));
    }

    /** El NBT no guarda el orden de las claves: las regiones van por orden alfabético, siempre igual. */
    private static List<String> sorted(NbtCompound regions) {
        return regions.getKeys().stream().sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(name -> name)).toList();
    }

    /**
     * @param only el nombre de la región que se quiere (sin distinguir mayúsculas), o vacío para juntarlas todas
     */
    public static SchematicData read(NbtCompound root, SchematicLimits limits, String only, List<String> warnings) {
        int version = root.getInt("Version");
        if (version < 1) {
            throw new IllegalArgumentException("El .litematic no dice su versión");
        }
        if (version > VERSION) {
            warnings.add("Es de una versión más nueva de Litematica (formato " + version + "): puede faltar algo");
        }
        int dataVersion = root.contains("MinecraftDataVersion", NbtElement.INT_TYPE)
                ? root.getInt("MinecraftDataVersion") : version < 5 ? 1343 : 1631;
        NbtCompound regionsTag = root.getCompound("Regions");
        List<String> names = sorted(regionsTag);
        if (names.isEmpty()) {
            throw new IllegalArgumentException("El .litematic no tiene regiones");
        }
        if (names.size() > limits.maxRegions()) {
            throw new IllegalArgumentException("El .litematic tiene " + names.size() + " regiones; el máximo es "
                    + limits.maxRegions());
        }
        if (only != null && !only.isBlank()) {
            names = List.of(pick(names, only.strip()));
        }

        // Primero las cajas: el tamaño total se comprueba antes de reservar nada.
        List<Region> regions = new ArrayList<>();
        long[] min = {Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
        long[] max = {Long.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE};
        for (String name : names) {
            if (regionsTag.getType(name) != NbtElement.COMPOUND_TYPE) {
                throw new IllegalArgumentException("La región «" + clip(name) + "» está dañada");
            }
            NbtCompound tag = regionsTag.getCompound(name);
            int[] position = SchematicNbt.vector(tag, "Position");
            int[] size = SchematicNbt.vector(tag, "Size");
            if (position == null || size == null) {
                throw new IllegalArgumentException("A la región «" + clip(name) + "» le falta la posición o el tamaño");
            }
            int[] regionMin = new int[3];
            int[] regionSize = new int[3];
            for (int axis = 0; axis < 3; axis++) {
                long length = Math.abs((long) size[axis]);
                if (length == 0) {
                    throw new IllegalArgumentException("La región «" + clip(name) + "» no tiene tamaño");
                }
                if (length > limits.maxSide()) {
                    throw new IllegalArgumentException("La región «" + clip(name) + "» mide " + length
                            + " bloques de lado; el máximo es " + limits.maxSide());
                }
                long low = size[axis] > 0 ? position[axis] : (long) position[axis] + size[axis] + 1;
                if (Math.abs(low) > 30_000_000L || Math.abs((long) position[axis]) > 30_000_000L) {
                    throw new IllegalArgumentException("La región «" + clip(name) + "» está fuera del mundo");
                }
                regionMin[axis] = (int) low;
                regionSize[axis] = (int) length;
                min[axis] = Math.min(min[axis], low);
                max[axis] = Math.max(max[axis], low + length - 1);
            }
            limits.checkBox(regionSize[0], regionSize[1], regionSize[2]);
            regions.add(new Region(name, regionMin, regionSize, position, tag));
        }
        long sizeX = max[0] - min[0] + 1;
        long sizeY = max[1] - min[1] + 1;
        long sizeZ = max[2] - min[2] + 1;
        limits.checkBox(sizeX, sizeY, sizeZ);
        int width = (int) sizeX;
        int height = (int) sizeY;
        int length = (int) sizeZ;

        List<String> palette = new ArrayList<>();
        palette.add(SchematicData.AIR);
        Map<String, Integer> indices = new HashMap<>();
        indices.put(SchematicData.AIR, 0);
        int[] blocks = new int[width * height * length];
        List<SchematicData.BlockEntity> blockEntities = new ArrayList<>();
        List<SchematicData.Entity> entities = new ArrayList<>();
        int droppedData = 0;
        for (Region region : regions) {
            NbtCompound tag = region.tag();
            NbtList paletteTag = tag.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            int paletteSize = paletteTag.size();
            if (paletteSize < 1) {
                throw new IllegalArgumentException("La región «" + clip(region.name()) + "» no tiene paleta");
            }
            if (paletteSize > limits.maxPalette()) {
                throw new IllegalArgumentException("La paleta de «" + clip(region.name()) + "» tiene " + paletteSize
                        + " estados; el máximo es " + limits.maxPalette());
            }
            int[] remap = new int[paletteSize];
            boolean[] air = new boolean[paletteSize];
            for (int index = 0; index < paletteSize; index++) {
                String state;
                try {
                    state = BlockStates.fromNbt(paletteTag.getCompound(index));
                } catch (IllegalArgumentException exception) {
                    BlockStates.warn(warnings, BlockStates.UNREADABLE);
                    state = SchematicData.AIR;
                }
                air[index] = BlockStates.isAir(state);
                Integer target = indices.get(state);
                if (target == null) {
                    if (palette.size() >= limits.maxPalette()) {
                        throw new IllegalArgumentException("El .litematic tiene más de " + limits.maxPalette()
                                + " estados distintos");
                    }
                    target = palette.size();
                    palette.add(state);
                    indices.put(state, target);
                }
                remap[index] = target;
            }
            if (!tag.contains("BlockStates", NbtElement.LONG_ARRAY_TYPE)) {
                throw new IllegalArgumentException("A la región «" + clip(region.name()) + "» le faltan los bloques");
            }
            long[] packed = tag.getLongArray("BlockStates");
            int count = (int) region.volume();
            int bits = PackedStates.detectBits(packed.length, count, paletteSize);
            if (bits < 0) {
                throw new IllegalArgumentException("Los bloques de la región «" + clip(region.name())
                        + "» no cuadran con su tamaño");
            }
            int[] values;
            try {
                values = PackedStates.unpack(packed, count, bits, paletteSize);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Región «" + clip(region.name()) + "»: " + exception.getMessage());
            }
            int dx = (int) (region.min()[0] - min[0]);
            int dy = (int) (region.min()[1] - min[1]);
            int dz = (int) (region.min()[2] - min[2]);
            int rx = region.size()[0];
            int rz = region.size()[2];
            int layer = rx * rz;
            for (int index = 0; index < count; index++) {
                int value = values[index];
                if (air[value] && regions.size() > 1) {
                    continue;
                }
                int y = index / layer;
                int rest = index - y * layer;
                int z = rest / rx;
                int x = rest - z * rx;
                blocks[(x + dx) + (z + dz) * width + (y + dy) * width * length] = remap[value];
            }

            NbtList tileEntities = tag.getList("TileEntities", NbtElement.COMPOUND_TYPE);
            if (blockEntities.size() + tileEntities.size() > limits.maxBlockEntities()) {
                throw new IllegalArgumentException("El .litematic trae más de " + limits.maxBlockEntities()
                        + " bloques con datos");
            }
            for (int index = 0; index < tileEntities.size(); index++) {
                NbtCompound entry = tileEntities.getCompound(index);
                int[] pos = {entry.getInt("x"), entry.getInt("y"), entry.getInt("z")};
                String id = entry.getString("id");
                if (!entry.contains("x", NbtElement.NUMBER_TYPE) || id.isEmpty()
                        || !SpongeCodec.inside(pos, region.size()[0], region.size()[1], region.size()[2])) {
                    droppedData++;
                    continue;
                }
                NbtCompound data = entry.copy();
                SpongeCodec.strip(data, "id", "x", "y", "z");
                blockEntities.add(new SchematicData.BlockEntity(pos[0] + dx, pos[1] + dy, pos[2] + dz, id, data));
            }

            NbtList entityList = tag.getList("Entities", NbtElement.COMPOUND_TYPE);
            if (entities.size() + entityList.size() > limits.maxEntities()) {
                throw new IllegalArgumentException("El .litematic trae más de " + limits.maxEntities() + " entidades");
            }
            for (int index = 0; index < entityList.size(); index++) {
                NbtCompound entry = entityList.getCompound(index);
                double[] pos = SchematicNbt.position(entry, "Pos");
                String id = entry.getString("id");
                if (pos == null || id.isEmpty()) {
                    droppedData++;
                    continue;
                }
                // Desde Position de la región a la esquina mínima de la caja entera.
                double x = pos[0] + region.position()[0] - min[0];
                double y = pos[1] + region.position()[1] - min[1];
                double z = pos[2] + region.position()[2] - min[2];
                if (x < -1 || y < -1 || z < -1 || x > width + 1 || y > height + 1 || z > length + 1) {
                    droppedData++;
                    continue;
                }
                NbtCompound data = entry.copy();
                SpongeCodec.strip(data, "id", "Pos");
                entities.add(new SchematicData.Entity(x, y, z, id, data));
            }
        }
        if (droppedData > 0) {
            warnings.add(droppedData + " datos de bloque o entidades sin posición o fuera de su región, quitados");
        }

        NbtCompound metadata = root.getCompound("Metadata");
        int[] preview = preview(metadata);
        List<String> allNames = sorted(regionsTag);
        SchematicData.Info info = new SchematicData.Info(SchematicNbt.text(metadata, "Name", 64),
                SchematicNbt.text(metadata, "Author", 64), SchematicNbt.text(metadata, "Description", 256),
                metadata.getLong("TimeCreated"), metadata.getLong("TimeModified"), preview,
                allNames.stream().map(LitematicCodec::clip).toList());
        return new SchematicData(width, height, length, palette, blocks, blockEntities, entities, dataVersion,
                (int) min[0], (int) min[1], (int) min[2], info);
    }

    /**
     * El {@code .litematic} (sin comprimir) con una sola región, en su sitio respecto al origen.
     *
     * @param region nombre de la región (el del plano, por ejemplo)
     */
    public static NbtCompound write(SchematicData data, String region) {
        SchematicData compact = data.compact();
        String regionName = region == null || region.isBlank() ? "Main" : region;
        int sizeX = compact.sizeX();
        int sizeY = compact.sizeY();
        int sizeZ = compact.sizeZ();
        int solid = compact.solidBlocks();

        NbtCompound regionTag = new NbtCompound();
        regionTag.put("Position", SchematicNbt.xyz(compact.offsetX(), compact.offsetY(), compact.offsetZ()));
        regionTag.put("Size", SchematicNbt.xyz(sizeX, sizeY, sizeZ));
        NbtList palette = new NbtList();
        for (String state : compact.palette()) {
            palette.add(BlockStates.toNbt(state));
        }
        regionTag.put("BlockStatePalette", palette);
        regionTag.putLongArray("BlockStates", PackedStates.pack(compact.blocks(),
                PackedStates.bits(compact.palette().size())));
        NbtList tileEntities = new NbtList();
        for (SchematicData.BlockEntity entity : compact.blockEntities()) {
            NbtCompound entry = entity.data().copy();
            entry.putString("id", entity.id());
            entry.putInt("x", entity.x());
            entry.putInt("y", entity.y());
            entry.putInt("z", entity.z());
            tileEntities.add(entry);
        }
        regionTag.put("TileEntities", tileEntities);
        NbtList entities = new NbtList();
        for (SchematicData.Entity entity : compact.entities()) {
            NbtCompound entry = entity.data().copy();
            entry.putString("id", entity.id());
            // Con el tamaño en positivo, Position es la esquina mínima: la posición no cambia.
            entry.put("Pos", SchematicNbt.doubles(entity.x(), entity.y(), entity.z()));
            entities.add(entry);
        }
        regionTag.put("Entities", entities);
        regionTag.put("PendingBlockTicks", new NbtList());
        regionTag.put("PendingFluidTicks", new NbtList());
        NbtCompound regions = new NbtCompound();
        regions.put(regionName, regionTag);

        SchematicData.Info info = compact.info();
        NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", info.name().isEmpty() ? regionName : info.name());
        metadata.putString("Author", info.author());
        metadata.putString("Description", info.description());
        metadata.put("EnclosingSize", SchematicNbt.xyz(sizeX, sizeY, sizeZ));
        metadata.putInt("RegionCount", 1);
        metadata.putInt("TotalBlocks", solid);
        metadata.putInt("TotalVolume", (int) Math.min(Integer.MAX_VALUE, compact.volume()));
        metadata.putLong("TimeCreated", info.created());
        metadata.putLong("TimeModified", info.modified() > 0 ? info.modified() : info.created());
        if (info.preview() != null && validPreview(info.preview())) {
            metadata.putIntArray("PreviewImageData", info.preview());
        }

        NbtCompound root = new NbtCompound();
        root.putInt("Version", VERSION);
        root.putInt("SubVersion", SUB_VERSION);
        root.putInt("MinecraftDataVersion", compact.dataVersion());
        root.put("Metadata", metadata);
        root.put("Regions", regions);
        return root;
    }

    // ------------------------------------------------------------------ Interno

    /** La región con ese nombre: igual, o sin distinguir mayúsculas. */
    private static String pick(List<String> names, String wanted) {
        for (String name : names) {
            if (name.equals(wanted)) {
                return name;
            }
        }
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).equals(wanted.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
        throw new IllegalArgumentException("El .litematic no tiene la región «" + clip(wanted) + "»; tiene: "
                + String.join(", ", names.stream().map(LitematicCodec::clip).toList()));
    }

    private static int[] preview(NbtCompound metadata) {
        if (!metadata.contains("PreviewImageData", NbtElement.INT_ARRAY_TYPE)) {
            return null;
        }
        int[] pixels = metadata.getIntArray("PreviewImageData");
        return validPreview(pixels) ? pixels : null;
    }

    private static boolean validPreview(int[] pixels) {
        int side = (int) Math.round(Math.sqrt(pixels.length));
        return side > 0 && side <= MAX_PREVIEW_SIDE && side * side == pixels.length;
    }

    /** Un nombre de región para los mensajes: corto y sin caracteres de control. */
    private static String clip(String name) {
        StringBuilder clean = new StringBuilder();
        for (int index = 0; index < name.length() && clean.length() < 48; index++) {
            char character = name.charAt(index);
            clean.append(Character.isISOControl(character) || character == '§' ? ' ' : character);
        }
        return clean.toString();
    }
}
