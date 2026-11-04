package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.komainu.moxia.schematic.LitematicCodecTest.assertSameBlocks;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los {@code .schem} de WorldEdit: el puente entre los otros formatos y los planos. */
class SpongeCodecTest {
    private static final SchematicLimits LIMITS = SchematicLimits.DEFAULT;

    @Test
    void version3RoundTripKeepsTheOriginAndTheData() {
        SchematicData original = SchematicFixtures.random(5, 4, 3, 17, 7);
        NbtCompound root = SpongeCodec.write(original);
        NbtCompound body = root.getCompound("Schematic");
        assertEquals(3, body.getInt("Version"));
        assertArrayEquals(new int[]{-2, 0, 3}, body.getIntArray("Offset"));
        assertArrayEquals(new int[]{0, 0, 0}, body.getCompound("Metadata").getCompound("WorldEdit")
                .getIntArray("Origin"), "WorldEdit pone la esquina en Origin + Offset");
        SchematicData read = SpongeCodec.read(root, LIMITS, new ArrayList<>());
        assertSameBlocks(original, read, "sponge.3");
        assertEquals(-2, read.offsetX());

        SchematicData furnished = SchematicFixtures.furnished();
        SchematicData back = SchematicIo.read(SchematicIo.toSponge(furnished), LIMITS, "").data();
        assertSameBlocks(furnished, back, "salón");
        assertEquals(2, back.blockEntities().size());
        assertEquals("minecraft:armor_stand", back.entities().getFirst().id());
        assertEquals(2.5, back.entities().getFirst().x(), 1e-9);
    }

    @Test
    void readsVersion2WithWorldPositionsAndLooseData() {
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 2);
        root.putInt("DataVersion", 2586);
        root.putShort("Width", (short) 2);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 1);
        root.putIntArray("Offset", new int[]{100, 64, 200});
        NbtCompound metadata = new NbtCompound();
        metadata.putInt("WEOffsetX", -1);
        metadata.putInt("WEOffsetY", 0);
        metadata.putInt("WEOffsetZ", 0);
        root.put("Metadata", metadata);
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:barrel[facing=up,open=false]", 0);
        palette.putInt("minecraft:air", 1);
        root.put("Palette", palette);
        root.putByteArray("BlockData", new byte[]{0, 1});
        NbtList blockEntities = new NbtList();
        NbtCompound barrel = new NbtCompound();
        barrel.putString("Id", "minecraft:barrel");
        barrel.putIntArray("Pos", new int[]{0, 0, 0});
        barrel.putString("CustomName", "\"Cosas\"");
        blockEntities.add(barrel);
        root.put("BlockEntities", blockEntities);
        NbtList entities = new NbtList();
        NbtCompound painting = new NbtCompound();
        painting.putString("Id", "minecraft:painting");
        painting.put("Pos", SchematicNbt.doubles(101.5, 64.5, 200.5));
        painting.putString("variant", "minecraft:kebab");
        entities.add(painting);
        root.put("Entities", entities);

        SchematicData data = SpongeCodec.read(root, LIMITS, new ArrayList<>());
        assertEquals(2586, data.dataVersion());
        assertEquals(-1, data.offsetX(), "el origen de v2 sale de WEOffset");
        assertEquals("minecraft:barrel[facing=up,open=false]", data.state(0, 0, 0));
        assertEquals("\"Cosas\"", data.blockEntities().getFirst().data().getString("CustomName"));
        assertTrue(!data.blockEntities().getFirst().data().contains("Pos"));
        assertEquals(1.5, data.entities().getFirst().x(), 1e-9, "las entidades de v2 van en coordenadas del mundo");
        assertEquals("minecraft:kebab", data.entities().getFirst().data().getString("variant"));
    }

    @Test
    void brokenSchematicsAreRejected() {
        NbtCompound root = SpongeCodec.write(SchematicFixtures.random(2, 2, 2, 3, 1));
        NbtCompound short1 = root.copy();
        short1.getCompound("Schematic").getCompound("Blocks").putByteArray("Data", new byte[]{0, 1});
        assertRejected(short1, "faltan bloques");
        NbtCompound outside = root.copy();
        outside.getCompound("Schematic").getCompound("Blocks").putByteArray("Data",
                new byte[]{0, 1, 2, 9, 0, 0, 0, 0});
        assertRejected(outside, "fuera de la paleta");
        NbtCompound future = root.copy();
        future.getCompound("Schematic").putInt("Version", 4);
        assertRejected(future, "versión que no se conoce");
        NbtCompound many = root.copy();
        NbtList list = new NbtList();
        for (int index = 0; index < 70_000; index++) {
            NbtCompound entry = new NbtCompound();
            entry.putString("Id", "minecraft:chest");
            list.add(entry);
        }
        many.getCompound("Schematic").getCompound("Blocks").put("BlockEntities", list);
        assertRejected(many, "bloques con datos");
    }

    @Test
    void unreadableStatesBecomeAirWithAWarning() {
        List<String> warnings = new ArrayList<>();
        assertEquals(SchematicData.AIR, SpongeCodec.state("minecraft:stone[broken", warnings));
        assertEquals("minecraft:oak_log[axis=y]", SpongeCodec.state("oak_log[axis=y]", warnings));
        assertEquals(List.of(BlockStates.UNREADABLE), warnings);
        assertEquals(NbtElement.COMPOUND_TYPE, BlockStates.toNbt("minecraft:oak_log[axis=y]")
                .get("Properties").getType());
    }

    private static void assertRejected(NbtCompound root, String fragment) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read(SchematicNbt.write(root), LIMITS, ""));
        assertTrue(exception.getMessage().contains(fragment), exception.getMessage());
    }
}
