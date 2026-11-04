package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.komainu.moxia.schematic.LitematicCodecTest.assertSameBlocks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estructuras {@code .nbt} de Minecraft hechas por las pruebas: ida y vuelta, variantes y archivos rotos. */
class StructureCodecTest {
    private static final SchematicLimits LIMITS = SchematicLimits.DEFAULT;

    @Test
    void roundTripKeepsBlocksDataAndEntities() {
        for (int palette : new int[]{1, 2, 3, 4, 17, 300}) {
            SchematicData original = SchematicFixtures.random(6, 4, 7, palette, palette * 31L);
            SchematicIo.Loaded loaded = SchematicIo.read(SchematicIo.write(original, SchematicFormat.STRUCTURE, ""),
                    LIMITS, null);
            assertEquals(SchematicFormat.STRUCTURE, loaded.format());
            assertSameBlocks(original, loaded.data(), "paleta de " + palette);
            assertEquals(0, loaded.data().offsetX(), "una estructura tiene el origen en la esquina");
        }
        SchematicData furnished = SchematicFixtures.furnished();
        SchematicData read = SchematicIo.read(SchematicIo.write(furnished, SchematicFormat.STRUCTURE, ""), LIMITS,
                "").data();
        assertSameBlocks(furnished, read, "salón");
        assertEquals("minecraft:diamond", read.blockEntities().stream()
                .filter(entity -> entity.id().equals("minecraft:chest")).findFirst().orElseThrow().data()
                .getList("Items", NbtElement.COMPOUND_TYPE).getCompound(0).getString("id"));
        assertEquals(1, read.entities().size());
        assertEquals(2.5, read.entities().getFirst().x(), 1e-9);
        assertTrue(read.entities().getFirst().data().getBoolean("ShowArms"));
    }

    @Test
    void writesWhatMinecraftReads() {
        SchematicData data = SchematicFixtures.furnished();
        NbtCompound root = SchematicNbt.read(SchematicIo.write(data, SchematicFormat.STRUCTURE, ""), LIMITS);
        assertEquals(3955, root.getInt("DataVersion"));
        assertEquals(3, root.getList("size", NbtElement.INT_TYPE).size());
        NbtList blocks = root.getList("blocks", NbtElement.COMPOUND_TYPE);
        assertEquals(24, blocks.size(), "todos los bloques de la caja, también el aire");
        NbtCompound lastBlock = blocks.getCompound(blocks.size() - 1);
        assertTrue(lastBlock.contains("nbt", NbtElement.COMPOUND_TYPE), "los bloques con datos van al final");
        assertFalse(lastBlock.getCompound("nbt").getString("id").isEmpty());
        NbtCompound entity = root.getList("entities", NbtElement.COMPOUND_TYPE).getCompound(0);
        assertEquals(3, entity.getList("blockPos", NbtElement.INT_TYPE).size());
        assertEquals("minecraft:armor_stand", entity.getCompound("nbt").getString("id"));
    }

    @Test
    void missingBlocksAreAirAndOnlyTheFirstPaletteIsUsed() {
        NbtCompound root = new NbtCompound();
        root.put("size", SchematicNbt.ints(2, 1, 1));
        NbtList palettes = new NbtList();
        for (String state : new String[]{"minecraft:oak_planks", "minecraft:spruce_planks"}) {
            NbtList palette = new NbtList();
            palette.add(BlockStates.toNbt(state));
            palettes.add(palette);
        }
        root.put("palettes", palettes);
        NbtList blocks = new NbtList();
        NbtCompound block = new NbtCompound();
        block.put("pos", SchematicNbt.ints(1, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        root.put("blocks", blocks);
        List<String> warnings = new ArrayList<>();
        SchematicData data = StructureCodec.read(root, LIMITS, warnings);
        assertEquals(SchematicData.AIR, data.state(0, 0, 0), "lo que no está listado es vacío: aire");
        assertEquals("minecraft:oak_planks", data.state(1, 0, 0));
        assertEquals(500, data.dataVersion(), "sin DataVersion, de antes de 1.13");
        assertTrue(warnings.getFirst().contains("2 variantes"), warnings.toString());
    }

    @Test
    void brokenStructuresAreRejected() {
        NbtCompound outside = structure();
        outside.getList("blocks", NbtElement.COMPOUND_TYPE).getCompound(0).put("pos", SchematicNbt.ints(5, 0, 0));
        assertRejected(outside, "fuera de su tamaño");
        NbtCompound badState = structure();
        badState.getList("blocks", NbtElement.COMPOUND_TYPE).getCompound(0).putInt("state", 9);
        assertRejected(badState, "fuera de la paleta");
        NbtCompound huge = structure();
        huge.put("size", SchematicNbt.ints(5000, 5000, 5000));
        assertRejected(huge, "de lado");
        NbtCompound negative = structure();
        negative.put("size", SchematicNbt.ints(-1, 2, 2));
        assertRejected(negative, "no tiene tamaño");
        NbtCompound crowded = structure();
        NbtList blocks = crowded.getList("blocks", NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < 10; index++) {
            blocks.add(blocks.getCompound(0).copy());
        }
        assertRejected(crowded, "más bloques que su tamaño");
    }

    private static NbtCompound structure() {
        NbtCompound root = new NbtCompound();
        root.putInt("DataVersion", 3955);
        root.put("size", SchematicNbt.ints(2, 1, 1));
        NbtList palette = new NbtList();
        palette.add(BlockStates.toNbt("minecraft:stone"));
        root.put("palette", palette);
        NbtList blocks = new NbtList();
        NbtCompound block = new NbtCompound();
        block.put("pos", SchematicNbt.ints(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        root.put("blocks", blocks);
        return root;
    }

    private static void assertRejected(NbtCompound root, String fragment) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SchematicIo.read(SchematicNbt.write(root), LIMITS, ""));
        assertTrue(exception.getMessage().contains(fragment), exception.getMessage());
    }
}
