package com.komainu.moxia.share;

import com.komainu.moxia.blueprint.BlueprintLibrary;
import com.komainu.moxia.schematic.BlockStates;
import com.komainu.moxia.schematic.PackedStates;
import com.komainu.moxia.schematic.SchematicData;
import com.komainu.moxia.schematic.SchematicFormat;
import com.komainu.moxia.schematic.SchematicIo;
import com.komainu.moxia.schematic.SchematicLimits;
import com.komainu.moxia.schematic.SchematicNbt;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Esquemas sueltos ({@code .litematic}, {@code .nbt}) importados como planos por el camino de los paquetes: se ponen
 * al día con el DataFixer de Minecraft y después pasan por las mismas comprobaciones que un plano de otro servidor.
 * Los archivos los hacen las pruebas, con lo que podría traer uno hecho a mala idea.
 */
class SchematicPacksTest {
    @BeforeAll
    static void minecraftVersion() {
        // El DataFixer de Minecraft pregunta la versión del juego; en el juego ya está puesta.
        SharedConstants.createGameVersion();
    }

    @TempDir
    Path root;
    private final List<BlueprintKind.Imported> saved = new ArrayList<>();
    private ShareKinds.Registry kinds;
    private ShareTarget bruno;

    @BeforeEach
    void setUp() {
        BlueprintLibrary library = new BlueprintLibrary(root.resolve("blueprints"));
        kinds = new ShareKinds.Registry();
        kinds.register(new BlueprintKind(() -> library, (target, blueprint, replace) -> {
            saved.add(blueprint);
            return "se guardó";
        }));
        bruno = ShareFixtures.player(ShareFixtures.BRUNO, "Bruno");
    }

    /**
     * Un {@code .litematic} de 1.19.4 (datos 3337) de 3×2×2: hierba (que en 1.20.3 pasó a {@code short_grass}), un
     * bloque de órdenes, un cofre con un libro con datos, un cartel de antes de 1.20 que ejecuta una orden al
     * pulsarlo y una escalera de un mod; con un aldeano y un cuadro.
     */
    static byte[] oldLitematic() {
        List<String> states = List.of("minecraft:air", "minecraft:stone", "minecraft:grass",
                "minecraft:command_block[conditional=false,facing=up]",
                "minecraft:chest[facing=north,type=single,waterlogged=false]",
                "minecraft:oak_sign[rotation=0,waterlogged=false]",
                "biomesoplenty:fir_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]");
        int[] values = {1, 2, 3, 4, 5, 6, 1, 1, 0, 0, 0, 1};
        NbtCompound region = new NbtCompound();
        region.put("Position", xyz(0, 0, 0));
        region.put("Size", xyz(3, 2, 2));
        NbtList palette = new NbtList();
        states.forEach(state -> palette.add(BlockStates.toNbt(state)));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", PackedStates.pack(values, PackedStates.bits(states.size())));
        NbtList tiles = new NbtList();
        NbtCompound command = tile("minecraft:command_block", 2, 0, 0);
        command.putString("Command", "op Atacante");
        tiles.add(command);
        NbtCompound chest = tile("minecraft:chest", 0, 0, 1);
        NbtList items = new NbtList();
        NbtCompound book = new NbtCompound();
        book.putString("id", "minecraft:written_book");
        book.putByte("Count", (byte) 1);
        book.putByte("Slot", (byte) 0);
        NbtCompound tag = new NbtCompound();
        tag.putString("title", "Peligro");
        book.put("tag", tag);
        items.add(book);
        chest.put("Items", items);
        tiles.add(chest);
        NbtCompound sign = tile("minecraft:sign", 1, 0, 1);
        sign.putString("Text1", "{\"text\":\"Pulsa\",\"clickEvent\":{\"action\":\"run_command\","
                + "\"value\":\"/op Atacante\"}}");
        for (int line = 2; line <= 4; line++) {
            sign.putString("Text" + line, "\"\"");
        }
        tiles.add(sign);
        region.put("TileEntities", tiles);
        NbtList entities = new NbtList();
        entities.add(entity("minecraft:villager", 1.5, 1.0, 0.5));
        NbtCompound painting = entity("minecraft:painting", 0.5, 1.5, 0.0);
        painting.putString("variant", "minecraft:kebab");
        entities.add(painting);
        region.put("Entities", entities);
        NbtCompound regions = new NbtCompound();
        regions.put("Casa", region);
        NbtCompound metadata = new NbtCompound();
        metadata.putString("Name", "Casa vieja");
        metadata.putString("Author", "§kAlicia‮");
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 6);
        root.putInt("SubVersion", 1);
        root.putInt("MinecraftDataVersion", 3337);
        root.put("Metadata", metadata);
        root.put("Regions", regions);
        return SchematicNbt.write(root);
    }

    @Test
    void anOldLitematicIsUpgradedAndThenCheckedLikeAnyBlueprint() {
        SharePack pack = SchematicPacks.pack("Casa Vieja (v2).litematic", oldLitematic(), "");
        ShareImport plan = ShareImport.prepare(pack, bruno, kinds::find, ShareFixtures.check());
        ShareImport.Entry entry = plan.entries().getFirst();
        assertEquals(ShareImport.Status.NEW, entry.status(), entry.problem());
        assertEquals("casa_vieja_v2", entry.name());
        String warnings = String.join(" | ", entry.warnings());
        assertTrue(warnings.contains("short_grass"), "el DataFixer va antes de la revisión: " + warnings);
        assertTrue(warnings.contains("command_block"), warnings);
        assertTrue(warnings.contains("fir_stairs"), warnings);
        assertTrue(warnings.contains("entidades quitadas"), warnings);

        plan.apply(bruno, ShareImport.Policy.RENAME);
        BlueprintKind.Imported imported = saved.getFirst();
        assertEquals("casa_vieja_v2", imported.name());
        assertEquals("Alicia", imported.author(), "el autor del archivo, sin códigos de color ni caracteres raros");
        assertEquals(3, imported.sizeX());
        assertEquals(2, imported.sizeY());
        SchematicData data = SchematicIo.read(imported.schematic(), SchematicLimits.DEFAULT, null).data();
        assertEquals(3955, data.dataVersion());
        assertFalse(data.palette().stream().anyMatch(state -> state.contains("command_block")), data.palette()
                .toString());
        NbtCompound signData = data.blockEntities().stream().filter(entity -> entity.id().equals("minecraft:sign"))
                .findFirst().orElseThrow().data();
        assertFalse(signData.contains("Text1"), "las líneas viejas, con su clic, fuera");
        String front = signData.getCompound("front_text").getList("messages", NbtElement.STRING_TYPE).getString(0);
        assertTrue(front.contains("Pulsa"), front);
        assertFalse(front.contains("clickEvent") || front.contains("op Atacante"), front);
        NbtCompound item = data.blockEntities().stream().filter(entity -> entity.id().equals("minecraft:chest"))
                .findFirst().orElseThrow().data().getList("Items", NbtElement.COMPOUND_TYPE).getCompound(0);
        assertEquals("minecraft:written_book", item.getString("id"), "el cofre conserva sus objetos…");
        assertFalse(item.contains("components") || item.contains("tag"), "…sin sus datos: " + item);
        assertEquals(List.of("minecraft:painting"), data.entities().stream().map(SchematicData.Entity::id).toList());
        assertEquals("minecraft:kebab", data.entities().getFirst().data().getString("variant"));
    }

    @Test
    void severalRegionsAreAnnouncedAndOneCanBeChosen() {
        byte[] file = twoRegions();
        ShareImport joined = ShareImport.prepare(SchematicPacks.pack("pueblo.litematic", file, ""), bruno,
                kinds::find, ShareFixtures.check());
        String warnings = String.join(" | ", joined.entries().getFirst().warnings());
        assertTrue(warnings.contains("2 regiones juntas") && warnings.contains("(casa, torre)"), warnings);
        ShareImport tower = ShareImport.prepare(SchematicPacks.pack("pueblo.litematic", file, "TORRE"), bruno,
                kinds::find, ShareFixtures.check());
        assertEquals(ShareImport.Status.NEW, tower.entries().getFirst().status());
        tower.apply(bruno, ShareImport.Policy.RENAME);
        assertEquals(1, saved.getFirst().sizeX(), "solo la torre");
        assertEquals(4, saved.getFirst().sizeY());
        ShareImport missing = ShareImport.prepare(SchematicPacks.pack("pueblo.litematic", file, "jardin"), bruno,
                kinds::find, ShareFixtures.check());
        assertEquals(ShareImport.Status.INVALID, missing.entries().getFirst().status());
        assertTrue(missing.entries().getFirst().problem().contains("casa, torre"),
                missing.entries().getFirst().problem());
    }

    @Test
    void structuresComeInToo() {
        SchematicData data = new SchematicData(2, 1, 1, List.of("minecraft:oak_planks", "minecraft:tuff"),
                new int[]{0, 1}, List.of(), List.of(), 3955, 0, 0, 0, null);
        byte[] file = SchematicIo.write(data, SchematicFormat.STRUCTURE, "");
        ShareImport plan = ShareImport.prepare(SchematicPacks.pack("igloo.nbt", file, ""), bruno, kinds::find,
                ShareFixtures.check());
        assertEquals(ShareImport.Status.NEW, plan.entries().getFirst().status(), plan.entries().toString());
        plan.apply(bruno, ShareImport.Policy.RENAME);
        assertEquals("igloo", saved.getFirst().name());
        assertEquals(2, saved.getFirst().blocks());
    }

    @Test
    void brokenOrForeignFilesAreRefusedWithAClearMessage() {
        assertProblem(SchematicPacks.pack("roto.litematic", "no soy un esquema".getBytes(), ""), "dañado");
        NbtCompound legacy = new NbtCompound();
        legacy.putShort("Width", (short) 1);
        legacy.putShort("Height", (short) 1);
        legacy.putShort("Length", (short) 1);
        legacy.putByteArray("Blocks", new byte[1]);
        legacy.putByteArray("Data", new byte[1]);
        assertProblem(SchematicPacks.pack("viejo.schematic", SchematicNbt.write(legacy), ""), "MCEdit");
        NbtCompound huge = new NbtCompound();
        huge.put("size", intList(5000, 5000, 5000));
        huge.put("palette", new NbtList());
        huge.put("blocks", new NbtList());
        assertProblem(SchematicPacks.pack("enorme.nbt", SchematicNbt.write(huge), ""), "de lado");
        assertProblem(SchematicPacks.pack("pueblo.nbt", SchematicIo.toSponge(
                new SchematicData(1, 1, 1, List.of("minecraft:stone"), new int[1], List.of(), List.of(), 3955, 0, 0,
                        0, null)), "tejado"), "regiones");
    }

    @Test
    void fileNamesBecomeBlueprintNamesAndPacksAreToldApart() {
        assertEquals("casa_pequena_v2", SchematicPacks.blueprintName("Casa Pequeña (v2).litematic"));
        assertEquals("torre-norte", SchematicPacks.blueprintName("aldea/torre-norte.nbt"));
        assertEquals("plano", SchematicPacks.blueprintName("___.schem"));
        assertEquals(32, SchematicPacks.blueprintName("a".repeat(80) + ".litematic").length());
        byte[] litematic = oldLitematic();
        assertTrue(SchematicPacks.isSchematic("casa.litematic", litematic));
        assertTrue(SchematicPacks.isSchematic("sin_extension", litematic), "por los primeros bytes (gzip)");
        assertFalse(SchematicPacks.isSchematic("rocas.kpack", litematic));
        assertFalse(SchematicPacks.isSchematic("codigo.txt", "kpack1:abc".getBytes()));
    }

    // ------------------------------------------------------------------ Utilidades

    private void assertProblem(SharePack pack, String fragment) {
        ShareImport.Entry entry = ShareImport.prepare(pack, bruno, kinds::find, ShareFixtures.check()).entries()
                .getFirst();
        assertEquals(ShareImport.Status.INVALID, entry.status(), entry.toString());
        assertTrue(entry.problem().contains(fragment), entry.problem());
    }

    private static byte[] twoRegions() {
        NbtCompound regions = new NbtCompound();
        regions.put("casa", simpleRegion(0, 0, 0, 3, 2, 3));
        regions.put("torre", simpleRegion(4, 0, 0, 1, 4, 1));
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 7);
        root.putInt("MinecraftDataVersion", 3955);
        root.put("Metadata", new NbtCompound());
        root.put("Regions", regions);
        return SchematicNbt.write(root);
    }

    private static NbtCompound simpleRegion(int x, int y, int z, int sizeX, int sizeY, int sizeZ) {
        NbtCompound region = new NbtCompound();
        region.put("Position", xyz(x, y, z));
        region.put("Size", xyz(sizeX, sizeY, sizeZ));
        NbtList palette = new NbtList();
        palette.add(BlockStates.toNbt("minecraft:air"));
        palette.add(BlockStates.toNbt("minecraft:stone"));
        region.put("BlockStatePalette", palette);
        int[] values = new int[sizeX * sizeY * sizeZ];
        Arrays.fill(values, 1);
        region.putLongArray("BlockStates", PackedStates.pack(values, 2));
        return region;
    }

    private static NbtCompound tile(String id, int x, int y, int z) {
        NbtCompound tag = new NbtCompound();
        tag.putString("id", id);
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static NbtCompound entity(String id, double x, double y, double z) {
        NbtCompound tag = new NbtCompound();
        tag.putString("id", id);
        NbtList pos = new NbtList();
        pos.add(NbtDouble.of(x));
        pos.add(NbtDouble.of(y));
        pos.add(NbtDouble.of(z));
        tag.put("Pos", pos);
        return tag;
    }

    private static NbtCompound xyz(int x, int y, int z) {
        NbtCompound tag = new NbtCompound();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static NbtList intList(int x, int y, int z) {
        NbtList list = new NbtList();
        list.add(NbtInt.of(x));
        list.add(NbtInt.of(y));
        list.add(NbtInt.of(z));
        return list;
    }
}
