package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code .litematic} hechos por las pruebas: ida y vuelta con paletas de 1 a 300 estados, regiones con tamaño
 * negativo, varias regiones juntas o una sola, y archivos rotos o hechos a mala idea.
 */
class LitematicCodecTest {
    private static final SchematicLimits LIMITS = SchematicLimits.DEFAULT;

    @Test
    void roundTripKeepsEveryBlockForEveryPaletteSize() {
        for (int palette : new int[]{1, 2, 3, 4, 17, 300}) {
            SchematicData original = SchematicFixtures.random(7, 5, 9, palette, palette);
            byte[] file = SchematicIo.write(original, SchematicFormat.LITEMATIC, "torre");
            SchematicIo.Loaded loaded = SchematicIo.read(file, LIMITS, "");
            assertEquals(SchematicFormat.LITEMATIC, loaded.format());
            assertTrue(loaded.warnings().isEmpty(), loaded.warnings().toString());
            assertSameBlocks(original, loaded.data(), "paleta de " + palette);
            assertEquals(-2, loaded.data().offsetX());
            assertEquals(3, loaded.data().offsetZ());
            assertEquals(3955, loaded.data().dataVersion());
        }
    }

    @Test
    void writesWhatLitematicaExpects() {
        SchematicData data = SchematicFixtures.random(4, 3, 5, 17, 1).withInfo(
                new SchematicData.Info("casa", "Alicia", "con tejado", 100L, 200L, new int[140 * 140], List.of()));
        NbtCompound root = SchematicNbt.read(SchematicIo.write(data, SchematicFormat.LITEMATIC, "casa"), LIMITS);
        assertEquals(7, root.getInt("Version"));
        assertEquals(1, root.getInt("SubVersion"));
        assertEquals(3955, root.getInt("MinecraftDataVersion"));
        NbtCompound metadata = root.getCompound("Metadata");
        assertEquals("casa", metadata.getString("Name"));
        assertEquals("Alicia", metadata.getString("Author"));
        assertEquals("con tejado", metadata.getString("Description"));
        assertEquals(1, metadata.getInt("RegionCount"));
        assertEquals(60, metadata.getInt("TotalVolume"));
        assertEquals(data.solidBlocks(), metadata.getInt("TotalBlocks"));
        assertEquals(4, metadata.getCompound("EnclosingSize").getInt("x"));
        assertEquals(100L, metadata.getLong("TimeCreated"));
        assertEquals(200L, metadata.getLong("TimeModified"));
        assertEquals(140 * 140, metadata.getIntArray("PreviewImageData").length);
        NbtCompound region = root.getCompound("Regions").getCompound("casa");
        assertEquals(-2, region.getCompound("Position").getInt("x"));
        assertEquals(5, region.getCompound("Size").getInt("z"));
        NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
        assertEquals("minecraft:air", palette.getCompound(0).getString("Name"), "el aire, el primero");
        assertEquals(17, palette.size());
        assertEquals(PackedStates.longs(60, 5), region.getLongArray("BlockStates").length);
        assertTrue(region.contains("PendingBlockTicks", NbtElement.LIST_TYPE));
        assertTrue(region.contains("PendingFluidTicks", NbtElement.LIST_TYPE));
    }

    @Test
    void blockEntitiesAndEntitiesKeepTheirData() {
        SchematicData original = SchematicFixtures.furnished();
        SchematicIo.Loaded loaded = SchematicIo.read(SchematicIo.write(original, SchematicFormat.LITEMATIC, "salon"),
                LIMITS, null);
        SchematicData read = loaded.data();
        assertSameBlocks(original, read, "salón");
        assertEquals(2, read.blockEntities().size());
        SchematicData.BlockEntity chest = read.blockEntities().getFirst();
        assertEquals("minecraft:chest", chest.id());
        assertEquals(1, chest.x());
        NbtCompound item = chest.data().getList("Items", NbtElement.COMPOUND_TYPE).getCompound(0);
        assertEquals("minecraft:diamond", item.getString("id"));
        assertEquals(3, item.getInt("count"));
        assertFalse(chest.data().contains("x"), "la posición no se queda dentro de los datos");
        SchematicData.BlockEntity sign = read.blockEntities().get(1);
        assertEquals("\"Hola\"", sign.data().getCompound("front_text").getList("messages", NbtElement.STRING_TYPE)
                .getString(0));
        assertEquals(1, read.entities().size());
        SchematicData.Entity stand = read.entities().getFirst();
        assertEquals("minecraft:armor_stand", stand.id());
        assertEquals(2.5, stand.x(), 1e-9);
        assertEquals(1.0, stand.y(), 1e-9);
        assertTrue(stand.data().getBoolean("ShowArms"));
        assertEquals("salón", read.info().name());
        assertEquals("Bruno", read.info().author());
    }

    @Test
    void negativeSizesGoFromPositionBackwards() {
        // Position (5, 0, 5) y Size (-3, 2, -2): la región va de (3, 0, 4) a (5, 1, 5).
        List<String> states = List.of("minecraft:air", "minecraft:stone", "minecraft:glass");
        int[] values = new int[3 * 2 * 2];
        values[0] = 1;                       // esquina mínima (3, 0, 4)
        values[2 + 3 + 3 * 2] = 2;           // (5, 1, 5), la Position
        NbtCompound region = region(new int[]{5, 0, 5}, new int[]{-3, 2, -2}, states, values);
        NbtCompound chest = new NbtCompound();
        chest.putString("id", "minecraft:chest");
        chest.putInt("x", 2);
        chest.putInt("y", 1);
        chest.putInt("z", 1);
        region.getList("TileEntities", NbtElement.COMPOUND_TYPE).add(chest);
        NbtCompound frame = new NbtCompound();
        frame.putString("id", "minecraft:item_frame");
        frame.put("Pos", SchematicNbt.doubles(0.5, 0.5, 0.5));
        region.getList("Entities", NbtElement.COMPOUND_TYPE).add(frame);
        SchematicData data = LitematicCodec.read(file(region), LIMITS, "", new ArrayList<>());
        assertEquals(3, data.sizeX());
        assertEquals(2, data.sizeY());
        assertEquals(2, data.sizeZ());
        assertEquals(3, data.offsetX());
        assertEquals(4, data.offsetZ());
        assertEquals("minecraft:stone", data.state(0, 0, 0));
        assertEquals("minecraft:glass", data.state(2, 1, 1));
        assertEquals(2, data.blockEntities().getFirst().x(), "los datos de bloque, desde la esquina mínima");
        // La entidad, desde la Position (5, 0, 5): 0.5 + 5 - 3 = 2.5.
        assertEquals(2.5, data.entities().getFirst().x(), 1e-9);
        assertEquals(1.5, data.entities().getFirst().z(), 1e-9);
    }

    @Test
    void severalRegionsAreJoinedWithTheirOffsetsOrPickedByName() {
        List<String> stone = List.of("minecraft:air", "minecraft:stone");
        List<String> glass = List.of("minecraft:air", "minecraft:glass");
        int[] full = {1, 1, 1, 1, 1, 1, 1, 1};
        int[] half = {1, 0, 0, 0, 0, 0, 0, 1};
        NbtCompound regions = new NbtCompound();
        regions.put("base", region(new int[]{0, 0, 0}, new int[]{2, 2, 2}, stone, full));
        regions.put("Tejado", region(new int[]{1, 1, 0}, new int[]{2, 2, 2}, glass, half));
        NbtCompound root = root(regions);
        List<String> warnings = new ArrayList<>();
        SchematicData joined = LitematicCodec.read(root, LIMITS, "", warnings);
        assertEquals(3, joined.sizeX());
        assertEquals(3, joined.sizeY());
        assertEquals(2, joined.sizeZ());
        assertEquals("minecraft:stone", joined.state(0, 0, 0));
        assertEquals("minecraft:glass", joined.state(1, 1, 0), "la segunda región pisa con sus bloques");
        assertEquals("minecraft:stone", joined.state(1, 1, 1), "su aire no borra lo de la primera");
        assertEquals("minecraft:glass", joined.state(2, 2, 1));
        assertEquals(SchematicData.AIR, joined.state(2, 0, 0));
        assertEquals(List.of("base", "Tejado"), joined.info().regions());

        SchematicData roof = LitematicCodec.read(root, LIMITS, "tejado", warnings);
        assertEquals(2, roof.sizeX());
        assertEquals(1, roof.offsetX(), "una sola región, en su sitio respecto al origen");
        assertEquals(1, roof.offsetY());
        assertEquals("minecraft:glass", roof.state(0, 0, 0));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> LitematicCodec.read(root, LIMITS, "jardin", new ArrayList<>()));
        assertTrue(missing.getMessage().contains("base, Tejado"), missing.getMessage());
    }

    @Test
    void hugeRegionsArePickedUpBeforeAllocating() {
        NbtCompound region = region(new int[]{0, 0, 0}, new int[]{2, 2, 2}, List.of("minecraft:air"), new int[8]);
        region.put("Size", SchematicNbt.xyz(100_000, 100_000, 100_000));
        assertRejected(file(region), "de lado");
        region.put("Size", SchematicNbt.xyz(4000, 4000, 4000));
        assertRejected(file(region), "bloques de caja");
        region.put("Size", SchematicNbt.xyz(0, 2, 2));
        assertRejected(file(region), "no tiene tamaño");
        NbtCompound far = region(new int[]{0, 0, 0}, new int[]{2, 2, 2}, List.of("minecraft:air"), new int[8]);
        far.put("Position", SchematicNbt.xyz(Integer.MAX_VALUE, 0, 0));
        assertRejected(file(far), "fuera del mundo");
        // Dos regiones pequeñas muy separadas: la caja que las abarca es enorme.
        NbtCompound regions = new NbtCompound();
        regions.put("a", region(new int[]{0, 0, 0}, new int[]{2, 2, 2}, List.of("minecraft:air"), new int[8]));
        regions.put("b", region(new int[]{3000, 3000, 3000}, new int[]{2, 2, 2}, List.of("minecraft:air"),
                new int[8]));
        assertRejected(root(regions), "bloques de caja");
    }

    @Test
    void brokenOrMaliciousFilesAreRejectedWithAClearMessage() {
        NbtCompound wrongLength = region(new int[]{0, 0, 0}, new int[]{4, 4, 4}, SchematicFixtures.palette(17),
                new int[64]);
        wrongLength.putLongArray("BlockStates", new long[3]);
        assertRejected(file(wrongLength), "no cuadran");

        NbtCompound outside = region(new int[]{0, 0, 0}, new int[]{2, 1, 1}, SchematicFixtures.palette(3),
                new int[2]);
        outside.putLongArray("BlockStates", PackedStates.pack(new int[]{3, 0}, 2));
        assertRejected(file(outside), "fuera de la paleta");

        NbtCompound noPalette = region(new int[]{0, 0, 0}, new int[]{1, 1, 1}, List.of("minecraft:air"), new int[1]);
        noPalette.put("BlockStatePalette", new NbtList());
        assertRejected(file(noPalette), "no tiene paleta");

        NbtCompound bigPalette = region(new int[]{0, 0, 0}, new int[]{1, 1, 1}, List.of("minecraft:air"), new int[1]);
        NbtList states = new NbtList();
        for (int index = 0; index < 70_000; index++) {
            states.add(BlockStates.toNbt("minecraft:stone"));
        }
        bigPalette.put("BlockStatePalette", states);
        assertRejected(file(bigPalette), "estados");

        NbtCompound many = new NbtCompound();
        for (int index = 0; index < 65; index++) {
            many.put("r" + index, region(new int[]{0, 0, 0}, new int[]{1, 1, 1}, List.of("minecraft:air"),
                    new int[1]));
        }
        assertRejected(root(many), "regiones");

        NbtCompound crowded = region(new int[]{0, 0, 0}, new int[]{1, 1, 1}, List.of("minecraft:air"), new int[1]);
        NbtList entities = crowded.getList("Entities", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < 16_385; index++) {
            NbtCompound pig = new NbtCompound();
            pig.putString("id", "minecraft:pig");
            pig.put("Pos", SchematicNbt.doubles(0, 0, 0));
            entities.add(pig);
        }
        assertRejected(file(crowded), "entidades");

        assertRejected(new NbtCompound(), "No es un .litematic");
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read(root(new NbtCompound()), LIMITS, ""));
        assertTrue(empty.getMessage().contains("no tiene regiones"), empty.getMessage());
    }

    @Test
    void zipBombsDeepNestingAndGarbageAreRejected() throws IOException {
        // Un long array de 16 millones de ceros: 128 MiB al descomprimir, unos pocos KiB comprimido.
        ByteArrayOutputStream bomb = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bomb);
             DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeByte(NbtElement.COMPOUND_TYPE);
            out.writeUTF("");
            out.writeByte(NbtElement.LONG_ARRAY_TYPE);
            out.writeUTF("BlockStates");
            out.writeInt(16 * 1024 * 1024);
            byte[] zeros = new byte[64 * 1024];
            for (int written = 0; written < 128; written++) {
                out.write(zeros);
            }
        }
        assertTrue(bomb.size() < 1024 * 1024, "la bomba es pequeña comprimida: " + bomb.size());
        IllegalArgumentException big = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read(bomb.toByteArray(), LIMITS, ""));
        assertTrue(big.getMessage().contains("demasiado grande"), big.getMessage());

        NbtCompound deep = new NbtCompound();
        NbtCompound current = deep;
        for (int depth = 0; depth < 600; depth++) {
            NbtCompound child = new NbtCompound();
            current.put("n", child);
            current = child;
        }
        IllegalArgumentException nested = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read(SchematicNbt.write(deep), LIMITS, ""));
        assertTrue(nested.getMessage().contains("anidado"), nested.getMessage());

        IllegalArgumentException garbage = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read("no soy un esquema".getBytes(), LIMITS, ""));
        assertTrue(garbage.getMessage().contains("dañado"), garbage.getMessage());
        byte[] valid = SchematicIo.write(SchematicFixtures.random(3, 3, 3, 4, 2), SchematicFormat.LITEMATIC, "x");
        IllegalArgumentException truncated = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read(Arrays.copyOf(valid, valid.length / 2), LIMITS, ""));
        assertTrue(truncated.getMessage().contains("dañado"), truncated.getMessage());
    }

    @Test
    void oddPreviewsAndUnreadableStatesAreDroppedWithoutFailing() {
        NbtCompound region = region(new int[]{0, 0, 0}, new int[]{2, 1, 1},
                List.of("minecraft:air", "minecraft:stone"), new int[]{0, 1});
        NbtList palette = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
        NbtCompound weird = new NbtCompound();
        weird.putString("Name", "Not A Block!");
        palette.set(1, weird);
        NbtCompound root = file(region);
        root.getCompound("Metadata").putIntArray("PreviewImageData", new int[]{1, 2, 3});
        List<String> warnings = new ArrayList<>();
        SchematicData data = LitematicCodec.read(root, LIMITS, "", warnings);
        assertEquals(SchematicData.AIR, data.state(1, 0, 0));
        assertNull(data.info().preview());
        assertTrue(warnings.contains(BlockStates.UNREADABLE), warnings.toString());
    }

    // ------------------------------------------------------------------ Utilidades

    static void assertSameBlocks(SchematicData expected, SchematicData actual, String message) {
        assertEquals(expected.sizeX(), actual.sizeX(), message);
        assertEquals(expected.sizeY(), actual.sizeY(), message);
        assertEquals(expected.sizeZ(), actual.sizeZ(), message);
        String[] wanted = new String[(int) expected.volume()];
        String[] got = new String[(int) actual.volume()];
        for (int y = 0; y < expected.sizeY(); y++) {
            for (int z = 0; z < expected.sizeZ(); z++) {
                for (int x = 0; x < expected.sizeX(); x++) {
                    int index = expected.index(x, y, z);
                    wanted[index] = expected.state(x, y, z);
                    got[index] = actual.state(x, y, z);
                }
            }
        }
        assertArrayEquals(wanted, got, message);
    }

    private static void assertRejected(NbtCompound root, String fragment) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read(SchematicNbt.write(root), LIMITS, ""));
        assertTrue(exception.getMessage().contains(fragment), exception.getMessage());
    }

    /** Una región hecha a mano con su paleta y sus bloques (en el orden de Litematica). */
    static NbtCompound region(int[] position, int[] size, List<String> states, int[] values) {
        NbtCompound region = new NbtCompound();
        region.put("Position", SchematicNbt.xyz(position[0], position[1], position[2]));
        region.put("Size", SchematicNbt.xyz(size[0], size[1], size[2]));
        NbtList palette = new NbtList();
        states.forEach(state -> palette.add(BlockStates.toNbt(state)));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", PackedStates.pack(values, PackedStates.bits(states.size())));
        region.put("TileEntities", new NbtList());
        region.put("Entities", new NbtList());
        return region;
    }

    static NbtCompound file(NbtCompound region) {
        NbtCompound regions = new NbtCompound();
        regions.put("region", region);
        return root(regions);
    }

    static NbtCompound root(NbtCompound regions) {
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 6);
        root.putInt("SubVersion", 1);
        root.putInt("MinecraftDataVersion", 3955);
        root.put("Metadata", new NbtCompound());
        root.put("Regions", regions);
        return root;
    }
}
