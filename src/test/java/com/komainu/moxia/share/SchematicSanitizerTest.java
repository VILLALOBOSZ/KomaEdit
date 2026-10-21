package com.komainu.moxia.share;

import com.google.gson.JsonParser;
import com.komainu.moxia.blueprint.BlueprintLibrary;
import com.komainu.moxia.blueprint.BlueprintLimits;
import com.komainu.moxia.blueprint.BlueprintMeta;
import com.komainu.moxia.blueprint.BlueprintScope;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Esquemas {@code sponge.3} montados a mano con lo que un paquete malicioso o de otro servidor podría traer: bloques
 * de mods que faltan, bloques de órdenes, carteles que ejecutan órdenes al pulsarlos, cofres con objetos con datos,
 * generadores de criaturas y entidades que no son de decorado.
 */
class SchematicSanitizerTest {
    @TempDir
    Path root;

    /** 3×2×2: piedra, escalera de un mod, bloque de órdenes, cofre, cartel, generador y aire. */
    static byte[] schematic() throws IOException {
        List<String> palette = List.of("minecraft:stone", "biomesoplenty:fir_stairs[facing=east,half=bottom]",
                "minecraft:command_block[conditional=false,facing=up]", "minecraft:chest[facing=north,type=single]",
                "minecraft:oak_sign[rotation=0]", "minecraft:spawner", "minecraft:air");
        int[] blocks = {0, 1, 2, 3, 4, 5, 6, 0, 0, 1, 6, 6};
        NbtCompound body = new NbtCompound();
        body.putInt("Version", 3);
        body.putInt("DataVersion", 3955);
        body.putShort("Width", (short) 3);
        body.putShort("Height", (short) 2);
        body.putShort("Length", (short) 2);
        body.putIntArray("Offset", new int[]{0, 0, 0});
        NbtCompound blockContainer = paletted(palette, blocks);
        NbtList entities = new NbtList();
        entities.add(blockEntity("minecraft:command_block", new int[]{2, 0, 0}, command()));
        entities.add(blockEntity("minecraft:chest", new int[]{0, 0, 1}, chest()));
        entities.add(blockEntity("minecraft:sign", new int[]{1, 0, 1}, sign()));
        entities.add(blockEntity("minecraft:mob_spawner", new int[]{2, 0, 1}, new NbtCompound()));
        entities.add(blockEntity("ironchests:iron_chest", new int[]{0, 1, 0}, new NbtCompound()));
        entities.add(blockEntity("minecraft:chest", new int[]{9, 9, 9}, new NbtCompound()));
        blockContainer.put("BlockEntities", entities);
        body.put("Blocks", blockContainer);
        body.put("Biomes", paletted(List.of("minecraft:plains", "mod:strange_woods"),
                new int[]{0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1}));
        NbtList mobs = new NbtList();
        mobs.add(entity("minecraft:painting", new NbtCompound()));
        NbtCompound frame = new NbtCompound();
        frame.put("Item", item("minecraft:written_book", true));
        frame.put("Tags", new NbtList());
        frame.put("Passengers", new NbtList());
        mobs.add(entity("minecraft:item_frame", frame));
        mobs.add(entity("minecraft:command_block_minecart", new NbtCompound()));
        mobs.add(entity("minecraft:villager", new NbtCompound()));
        body.put("Entities", mobs);
        NbtCompound root = new NbtCompound();
        root.put("Schematic", body);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, output);
        return output.toByteArray();
    }

    private static NbtCompound paletted(List<String> keys, int[] values) {
        NbtCompound palette = new NbtCompound();
        for (int index = 0; index < keys.size(); index++) {
            palette.putInt(keys.get(index), index);
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int value : values) {
            data.write(value);
        }
        NbtCompound container = new NbtCompound();
        container.put("Palette", palette);
        container.putByteArray("Data", data.toByteArray());
        return container;
    }

    private static NbtCompound blockEntity(String id, int[] pos, NbtCompound data) {
        NbtCompound entry = new NbtCompound();
        entry.putString("Id", id);
        entry.putIntArray("Pos", pos);
        entry.put("Data", data);
        return entry;
    }

    private static NbtCompound entity(String id, NbtCompound data) {
        NbtCompound entry = new NbtCompound();
        entry.putString("Id", id);
        NbtList pos = new NbtList();
        pos.add(NbtDouble.of(0.5));
        pos.add(NbtDouble.of(1.0));
        pos.add(NbtDouble.of(0.5));
        entry.put("Pos", pos);
        entry.put("Data", data);
        return entry;
    }

    private static NbtCompound command() {
        NbtCompound data = new NbtCompound();
        data.putString("Command", "op Atacante");
        data.putByte("auto", (byte) 1);
        return data;
    }

    private static NbtCompound chest() {
        NbtCompound data = new NbtCompound();
        data.putString("LootTable", "minecraft:chests/end_city_treasure");
        data.putString("CustomName", "{\"text\":\"Tesoro\",\"color\":\"gold\",\"clickEvent\":{\"action\":"
                + "\"run_command\",\"value\":\"/op Atacante\"}}");
        NbtList items = new NbtList();
        items.add(item("minecraft:diamond_sword", true));
        items.add(item("minecraft:command_block", false));
        items.add(item("minecraft:bread", false));
        data.put("Items", items);
        return data;
    }

    private static NbtCompound item(String id, boolean components) {
        NbtCompound item = new NbtCompound();
        item.putString("id", id);
        item.putInt("count", 1);
        item.putByte("Slot", (byte) 0);
        if (components) {
            NbtCompound parts = new NbtCompound();
            parts.putString("minecraft:custom_name", "{\"text\":\"Excalibur\"}");
            parts.put("minecraft:enchantments", new NbtCompound());
            item.put("components", parts);
        }
        return item;
    }

    private static NbtCompound sign() {
        NbtCompound front = new NbtCompound();
        NbtList messages = new NbtList();
        messages.add(NbtString.of("{\"text\":\"Pulsa\",\"color\":\"red\",\"clickEvent\":{\"action\":\"run_command\","
                + "\"value\":\"/op Atacante\"}}"));
        messages.add(NbtString.of("{\"selector\":\"@a\"}"));
        messages.add(NbtString.of("{\"nbt\":\"Inventory\",\"entity\":\"@p\"}"));
        messages.add(NbtString.of("\"normal\""));
        front.put("messages", messages);
        NbtCompound data = new NbtCompound();
        data.put("front_text", front);
        return data;
    }

    private static NbtCompound read(byte[] data) throws IOException {
        return NbtIo.readCompressed(new ByteArrayInputStream(data), NbtSizeTracker.ofUnlimitedBytes())
                .getCompound("Schematic");
    }

    /** Los bloques en orden de los datos, con la paleta ya resuelta. */
    private static List<String> blocks(NbtCompound container) {
        NbtCompound palette = container.getCompound("Palette");
        Map<Integer, String> byIndex = new LinkedHashMap<>();
        palette.getKeys().forEach(key -> byIndex.put(palette.getInt(key), key));
        List<String> result = new ArrayList<>();
        byte[] data = container.getByteArray("Data");
        for (int position = 0; position < data.length; ) {
            int value = 0;
            int shift = 0;
            int read;
            do {
                read = data[position++];
                value |= (read & 0x7F) << shift;
                shift += 7;
            } while ((read & 0x80) != 0);
            result.add(byIndex.get(value));
        }
        return result;
    }

    @Test
    void sanitizesEverythingThatCouldDoSomething() throws IOException {
        ShareCheck check = ShareFixtures.check();
        check.begin();
        SchematicSanitizer.Result result = SchematicSanitizer.sanitize(schematic(), check, Long.MAX_VALUE);
        assertEquals(3, result.sizeX());
        assertEquals(2, result.sizeY());
        assertEquals(2, result.sizeZ());
        NbtCompound body = read(result.data());
        List<String> blocks = blocks(body.getCompound("Blocks"));
        assertEquals("minecraft:stone", blocks.get(0));
        assertEquals("minecraft:oak_stairs[facing=east,half=bottom]", blocks.get(1));
        assertEquals("minecraft:air", blocks.get(2), "el bloque de órdenes pasa a aire");
        assertEquals("minecraft:air", blocks.get(6));
        // El aire del bloque de órdenes y el de antes se juntan en una sola entrada de la paleta.
        assertEquals(6, body.getCompound("Blocks").getCompound("Palette").getSize());
        assertEquals(8, result.blocks(), "bloques que no son aire");

        NbtList entities = body.getCompound("Blocks").getList("BlockEntities", NbtElement.COMPOUND_TYPE);
        List<String> kept = new ArrayList<>();
        for (int index = 0; index < entities.size(); index++) {
            kept.add(entities.getCompound(index).getString("Id"));
        }
        assertEquals(List.of("minecraft:chest", "minecraft:sign"), kept);

        NbtCompound chest = entities.getCompound(0).getCompound("Data");
        assertFalse(chest.contains("LootTable"));
        assertFalse(chest.getString("CustomName").contains("clickEvent"));
        assertTrue(chest.getString("CustomName").contains("Tesoro"));
        NbtList items = chest.getList("Items", NbtElement.COMPOUND_TYPE);
        assertEquals(2, items.size(), "el bloque de órdenes no entra en el cofre");
        assertEquals("minecraft:diamond_sword", items.getCompound(0).getString("id"));
        assertFalse(items.getCompound(0).contains("components"));

        NbtList messages = entities.getCompound(1).getCompound("Data").getCompound("front_text")
                .getList("messages", NbtElement.STRING_TYPE);
        String first = messages.getString(0);
        assertFalse(first.contains("clickEvent"), first);
        assertEquals("red", JsonParser.parseString(first).getAsJsonObject().get("color").getAsString());
        assertFalse(messages.getString(1).contains("selector"), messages.getString(1));
        assertFalse(messages.getString(2).contains("nbt"), messages.getString(2));
        assertEquals("\"normal\"", messages.getString(3));

        NbtList mobs = body.getList("Entities", NbtElement.COMPOUND_TYPE);
        assertEquals(2, mobs.size(), "solo el cuadro y el marco");
        NbtCompound frame = mobs.getCompound(1).getCompound("Data");
        assertFalse(frame.contains("Tags"));
        assertFalse(frame.contains("Passengers"));
        assertFalse(frame.getCompound("Item").contains("components"));

        List<String> biomes = blocks(body.getCompound("Biomes"));
        assertTrue(biomes.stream().allMatch("minecraft:plains"::equals), biomes.toString());

        String warnings = String.join(" | ", check.warnings());
        assertTrue(warnings.contains("biomesoplenty:fir_stairs → oak_stairs"), warnings);
        assertTrue(warnings.contains("vacíos"), warnings);
        assertTrue(warnings.contains("entidades quitadas"), warnings);
        assertTrue(warnings.contains("llanura"), warnings);

        // Saneado otra vez queda igual.
        ShareCheck again = ShareFixtures.check();
        again.begin();
        assertEquals(result.blocks(), SchematicSanitizer.sanitize(result.data(), again, Long.MAX_VALUE).blocks());
    }

    @Test
    void brokenOrHugeSchematicsAreRefused() throws IOException {
        ShareCheck check = ShareFixtures.check();
        check.begin();
        ShareCodecTest.assertMessage("dañado", () -> SchematicSanitizer.sanitize(new byte[]{1, 2, 3}, check,
                Long.MAX_VALUE));
        ShareCodecTest.assertMessage("máximo", () -> SchematicSanitizer.sanitize(schematic(), check, 5));
        // Una bomba: un arreglo de ceros que se comprime en nada y descomprimido pasa del límite.
        NbtCompound body = new NbtCompound();
        body.putInt("Version", 3);
        body.putByteArray("Junk", new byte[8 * 1024 * 1024]);
        NbtCompound root = new NbtCompound();
        root.put("Schematic", body);
        ByteArrayOutputStream bomb = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, bomb);
        assertTrue(bomb.size() < 64 * 1024);
        ShareCheck small = new ShareCheck(ShareFixtures.catalog(), new ShareLimits(32_000, 1024 * 1024,
                16L * 1024 * 1024, 64, 1024 * 1024, 30, 60, true));
        ShareCodecTest.assertMessage("demasiado grande", () -> SchematicSanitizer.sanitize(bomb.toByteArray(), small,
                Long.MAX_VALUE));
        // Datos que apuntan fuera de la paleta o que no cuadran con el tamaño.
        ShareCodecTest.assertMessage("paleta", () -> SchematicSanitizer.sanitize(withData(new int[]{0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 99}), check, Long.MAX_VALUE));
        ShareCodecTest.assertMessage("faltan", () -> SchematicSanitizer.sanitize(withData(new int[]{0, 0, 0}), check,
                Long.MAX_VALUE));
    }

    private static byte[] withData(int[] values) throws IOException {
        NbtCompound root = NbtIo.readCompressed(new ByteArrayInputStream(schematic()),
                NbtSizeTracker.ofUnlimitedBytes());
        NbtCompound blocks = root.getCompound("Schematic").getCompound("Blocks");
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int value : values) {
            data.write(value);
        }
        blocks.putByteArray("Data", data.toByteArray());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, output);
        return output.toByteArray();
    }

    @Test
    void blueprintsTravelWithoutTheirDangerousParts() throws IOException {
        ShareTarget alice = ShareFixtures.player(ShareFixtures.ALICE, "Alicia");
        ShareTarget bruno = ShareFixtures.player(ShareFixtures.BRUNO, "Bruno");
        BlueprintLibrary library = new BlueprintLibrary(root.resolve("blueprints"));
        library.save(new BlueprintMeta("torre", BlueprintScope.PLAYER, alice.id(), "Alicia", 1L, 1L, 3, 2, 2, 7,
                List.of("medieval", "torre"), "", "sponge.3", "", 0L), schematic(), new byte[0],
                BlueprintLimits.DEFAULT, false);
        List<BlueprintKind.Imported> saved = new ArrayList<>();
        ShareKinds.Registry kinds = new ShareKinds.Registry();
        kinds.register(new BlueprintKind(() -> library, (target, blueprint, replace) -> {
            saved.add(blueprint);
            return "se guardó";
        }));
        SharePack pack = new ShareExport(List.of("plano:torre"), "", true).build(alice, kinds::find, "test", 1L,
                ShareLimits.DEFAULT);
        SharePack received = ShareCodec.fromFile(ShareCodec.toFile(pack), ShareLimits.DEFAULT);
        ShareImport plan = ShareImport.prepare(received, bruno, kinds::find, ShareFixtures.check());
        assertEquals(ShareImport.Status.NEW, plan.entries().getFirst().status(), plan.entries().toString());
        plan.apply(bruno, ShareImport.Policy.RENAME);
        BlueprintKind.Imported imported = saved.getFirst();
        assertEquals("torre", imported.name());
        assertEquals("Alicia", imported.author());
        assertEquals(List.of("medieval", "torre"), imported.tags());
        assertEquals(8, imported.blocks());
        NbtCompound body = read(imported.schematic());
        assertFalse(blocks(body.getCompound("Blocks")).stream().anyMatch(block -> block.contains("command")));

        // Sin el permiso de guardar planos no entra.
        ShareTarget noBlueprints = ShareFixtures.player(ShareFixtures.BRUNO, "Bruno", "komaedit.blueprint.save");
        assertEquals(ShareImport.Status.DENIED, ShareImport.prepare(received, noBlueprints, kinds::find,
                ShareFixtures.check()).entries().getFirst().status());
    }
}
