package com.komainu.moxia.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deja un esquema de WorldEdit ({@code sponge.3}, el de los planos) que llega en un paquete listo para este
 * servidor y sin nada que haga algo al colocarlo:
 * <ul>
 *     <li>los bloques que no existen aquí pasan al más parecido ({@link ShareCheck#block}) y los de administración
 *     (bloques de órdenes, de estructura, puzles) a aire;</li>
 *     <li>de los datos de bloque solo se quedan los de los bloques normales de Minecraft: los carteles sin clics ni
 *     selectores, los cofres sin tabla de botín y los objetos de dentro sin sus componentes (nombre, encantamientos,
 *     libros); los generadores de criaturas, las bóvedas, los portales del End y los datos de otros mods se quitan;</li>
 *     <li>de las entidades solo viajan las de decorado (cuadros, marcos, soportes, vagonetas y barcas), sin
 *     pasajeros, etiquetas ni atributos;</li>
 *     <li>los biomas que no existen pasan a llanura.</li>
 * </ul>
 * Comprueba también que el tamaño y los datos cuadran, para que WorldEdit no falle a medias al leerlo.
 */
public final class SchematicSanitizer {
    /** Lado más largo que puede tener un esquema de Sponge (corto sin signo). */
    static final int MAX_SIDE = 65_535;
    private static final String PLAINS = "minecraft:plains";
    private static final Set<String> SIGNS = Set.of("minecraft:sign", "minecraft:hanging_sign");
    /** Datos de bloque que se quedan (saneados); el resto se quita y el bloque se coloca con los de serie. */
    private static final Set<String> KEPT_BLOCK_ENTITIES = Set.of("minecraft:sign", "minecraft:hanging_sign",
            "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:shulker_box",
            "minecraft:dispenser", "minecraft:dropper", "minecraft:hopper", "minecraft:furnace",
            "minecraft:blast_furnace", "minecraft:smoker", "minecraft:brewing_stand", "minecraft:campfire",
            "minecraft:chiseled_bookshelf", "minecraft:decorated_pot", "minecraft:crafter", "minecraft:lectern",
            "minecraft:jukebox", "minecraft:brushable_block", "minecraft:banner", "minecraft:skull", "minecraft:bed",
            "minecraft:bell", "minecraft:conduit", "minecraft:beacon", "minecraft:enchanting_table",
            "minecraft:ender_chest", "minecraft:comparator", "minecraft:daylight_detector", "minecraft:piston",
            "minecraft:sculk_sensor", "minecraft:calibrated_sculk_sensor", "minecraft:sculk_catalyst",
            "minecraft:sculk_shrieker", "minecraft:beehive", "minecraft:end_portal");
    /** Entidades de decorado. */
    private static final Set<String> KEPT_ENTITIES = Set.of("minecraft:painting", "minecraft:item_frame",
            "minecraft:glow_item_frame", "minecraft:armor_stand", "minecraft:leash_knot", "minecraft:minecart",
            "minecraft:chest_minecart", "minecraft:furnace_minecart", "minecraft:hopper_minecart", "minecraft:boat",
            "minecraft:chest_boat");
    /** Lo que se quita siempre de unos datos: botín, cerrojos, pasajeros, etiquetas, atributos y efectos. */
    private static final Set<String> STRIPPED_KEYS = Set.of("LootTable", "LootTableSeed", "Lock", "lock", "bees",
            "Passengers", "Tags", "Attributes", "attributes", "ActiveEffects", "active_effects", "data",
            "Brain", "Leash", "leash", "Owner", "owner");
    /** Objetos que no entran por un paquete: guardan órdenes, estructuras o datos de criaturas. */
    private static final Set<String> RESTRICTED_ITEMS = Set.of("minecraft:command_block",
            "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:command_block_minecart",
            "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "minecraft:debug_stick",
            "minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault");
    /** Claves de un texto de Minecraft que se quedan; los clics, los selectores y las lecturas de datos no. */
    private static final Set<String> TEXT_KEYS = Set.of("text", "translate", "fallback", "with", "extra", "color",
            "bold", "italic", "underlined", "strikethrough", "obfuscated", "font", "keybind", "type");
    private static final int MAX_TEXT_DEPTH = 16;

    private SchematicSanitizer() {
    }

    /**
     * El esquema saneado.
     *
     * @param data   el esquema comprimido, listo para guardar
     * @param blocks bloques que no son aire
     */
    public record Result(byte[] data, int sizeX, int sizeY, int sizeZ, int blocks) {
        public long volume() {
            return (long) sizeX * sizeY * sizeZ;
        }
    }

    /**
     * @param maxVolume bloques de caja como mucho (ancho × alto × fondo)
     */
    public static Result sanitize(byte[] schematic, ShareCheck check, long maxVolume) {
        long maxBytes = check.limits().maxSchematicBytes();
        NbtCompound root;
        try {
            // El contador de tamaño corta antes de reservar memoria: un esquema bomba no pasa de aquí.
            root = NbtIo.readCompressed(new ByteArrayInputStream(schematic), new NbtSizeTracker(maxBytes, 128));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("El esquema del plano está dañado o es demasiado grande");
        }
        if (!root.contains("Schematic", NbtElement.COMPOUND_TYPE)) {
            throw new IllegalArgumentException("El plano no es un esquema sponge.3 de WorldEdit");
        }
        NbtCompound body = root.getCompound("Schematic");
        if (body.getInt("Version") != 3) {
            throw new IllegalArgumentException("El plano no es un esquema sponge.3 de WorldEdit (versión "
                    + body.getInt("Version") + ")");
        }
        int sizeX = Short.toUnsignedInt(body.getShort("Width"));
        int sizeY = Short.toUnsignedInt(body.getShort("Height"));
        int sizeZ = Short.toUnsignedInt(body.getShort("Length"));
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) {
            throw new IllegalArgumentException("El esquema del plano no dice su tamaño");
        }
        long volume = (long) sizeX * sizeY * sizeZ;
        if (volume > maxVolume) {
            throw new IllegalArgumentException("El plano ocupa " + volume + " bloques de caja; el máximo es "
                    + maxVolume);
        }
        if (!body.contains("Blocks", NbtElement.COMPOUND_TYPE)) {
            throw new IllegalArgumentException("Al esquema del plano le faltan los bloques");
        }
        NbtCompound blocks = body.getCompound("Blocks");
        int solid = palette(blocks, volume, check, true);
        blockEntities(blocks, sizeX, sizeY, sizeZ, check);
        if (body.contains("Biomes", NbtElement.COMPOUND_TYPE)) {
            palette(body.getCompound("Biomes"), volume, check, false);
        }
        entities(body, check);
        ByteArrayOutputStream output = new ByteArrayOutputStream(schematic.length);
        try {
            NbtIo.writeCompressed(root, output);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo escribir el esquema", exception);
        }
        return new Result(output.toByteArray(), sizeX, sizeY, sizeZ, solid);
    }

    // ------------------------------------------------------------------ Paleta y datos

    /**
     * Comprueba la paleta (bloques o biomas), cambia lo que falta y rehace los datos si dos entradas quedan
     * iguales.
     *
     * @return cuántas posiciones no son aire (solo en los bloques)
     */
    private static int palette(NbtCompound container, long volume, ShareCheck check, boolean blocks) {
        if (!container.contains("Palette", NbtElement.COMPOUND_TYPE)
                || !container.contains("Data", NbtElement.BYTE_ARRAY_TYPE)) {
            throw new IllegalArgumentException("Al esquema del plano le falta la paleta o los datos");
        }
        NbtCompound palette = container.getCompound("Palette");
        int size = palette.getSize();
        if (size < 1 || size > volume + 1) {
            throw new IllegalArgumentException("La paleta del esquema no cuadra con su tamaño");
        }
        Map<Integer, String> byIndex = new HashMap<>();
        for (String key : palette.getKeys()) {
            if (palette.getType(key) != NbtElement.INT_TYPE) {
                throw new IllegalArgumentException("La paleta del esquema está dañada");
            }
            int index = palette.getInt(key);
            if (index < 0 || index >= size || byIndex.put(index, key) != null) {
                throw new IllegalArgumentException("La paleta del esquema está dañada");
            }
        }
        int[] remap = new int[size];
        boolean[] air = new boolean[size];
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (int index = 0; index < size; index++) {
            String key = byIndex.get(index);
            String fixed;
            try {
                fixed = blocks ? check.block(key) : biome(key, check);
            } catch (IllegalArgumentException exception) {
                check.warn("Entrada de la paleta que no se entiende (" + ShareText.display(key, 48) + "): aire");
                fixed = blocks ? BlockSubstitutes.AIR : PLAINS;
            }
            Integer target = merged.putIfAbsent(fixed, merged.size());
            remap[index] = target == null ? merged.size() - 1 : target;
            air[index] = blocks && air(fixed);
        }
        byte[] data = container.getByteArray("Data");
        boolean same = merged.size() == size;
        for (int index = 0; same && index < size; index++) {
            same = remap[index] == index;
        }
        ByteArrayOutputStream rewritten = same ? null : new ByteArrayOutputStream(data.length);
        long count = 0;
        int solid = 0;
        int position = 0;
        while (position < data.length) {
            int value = 0;
            int shift = 0;
            int read;
            do {
                if (position >= data.length || shift > 28) {
                    throw new IllegalArgumentException("Los datos del esquema están dañados");
                }
                read = data[position++];
                value |= (read & 0x7F) << shift;
                shift += 7;
            } while ((read & 0x80) != 0);
            if (value < 0 || value >= size) {
                throw new IllegalArgumentException("Los datos del esquema apuntan fuera de la paleta");
            }
            if (++count > volume) {
                throw new IllegalArgumentException("El esquema trae más bloques que su tamaño");
            }
            if (!air[value]) {
                solid++;
            }
            if (rewritten != null) {
                writeVarInt(rewritten, remap[value]);
            }
        }
        if (count != volume) {
            throw new IllegalArgumentException("Al esquema le faltan bloques (" + count + " de " + volume + ")");
        }
        if (rewritten != null) {
            NbtCompound fixedPalette = new NbtCompound();
            merged.forEach(fixedPalette::putInt);
            container.put("Palette", fixedPalette);
            container.putByteArray("Data", rewritten.toByteArray());
        }
        return blocks ? solid : 0;
    }

    private static String biome(String key, ShareCheck check) {
        String id = key.indexOf(':') < 0 ? "minecraft:" + key : key;
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("bioma no válido");
        }
        if (check.blocks().hasBiome(id)) {
            return id;
        }
        check.warn("Biomas que no hay en este servidor, cambiados por llanura: " + id);
        return PLAINS;
    }

    private static boolean air(String state) {
        return state.equals(BlockSubstitutes.AIR) || state.equals("minecraft:cave_air")
                || state.equals("minecraft:void_air");
    }

    private static void writeVarInt(ByteArrayOutputStream output, int value) {
        while ((value & -128) != 0) {
            output.write(value & 127 | 128);
            value >>>= 7;
        }
        output.write(value);
    }

    // ------------------------------------------------------------------ Datos de bloque y entidades

    private static void blockEntities(NbtCompound blocks, int sizeX, int sizeY, int sizeZ, ShareCheck check) {
        if (!blocks.contains("BlockEntities", NbtElement.LIST_TYPE)) {
            return;
        }
        NbtList list = blocks.getList("BlockEntities", NbtElement.COMPOUND_TYPE);
        NbtList kept = new NbtList();
        int removed = 0;
        for (int index = 0; index < list.size(); index++) {
            NbtCompound entry = list.getCompound(index);
            String id = entry.getString("Id");
            int[] pos = entry.getIntArray("Pos");
            boolean inside = pos.length == 3 && pos[0] >= 0 && pos[0] < sizeX && pos[1] >= 0 && pos[1] < sizeY
                    && pos[2] >= 0 && pos[2] < sizeZ;
            if (!inside || !KEPT_BLOCK_ENTITIES.contains(id)) {
                removed++;
                continue;
            }
            NbtCompound data = entry.getCompound("Data");
            clean(data, check);
            if (SIGNS.contains(id)) {
                for (String side : new String[]{"front_text", "back_text"}) {
                    if (data.contains(side, NbtElement.COMPOUND_TYPE)) {
                        NbtCompound text = data.getCompound(side);
                        for (String lines : new String[]{"messages", "filtered_messages"}) {
                            if (text.contains(lines, NbtElement.LIST_TYPE)) {
                                text.put(lines, texts(text.getList(lines, NbtElement.STRING_TYPE)));
                            }
                        }
                    }
                }
            }
            NbtCompound fixed = new NbtCompound();
            fixed.putString("Id", id);
            fixed.putIntArray("Pos", pos);
            fixed.put("Data", data);
            kept.add(fixed);
        }
        blocks.put("BlockEntities", kept);
        if (removed > 0) {
            check.warn(removed + " bloques con datos llegan vacíos (generadores, bóvedas, órdenes u otros mods)");
        }
    }

    private static void entities(NbtCompound body, ShareCheck check) {
        if (!body.contains("Entities", NbtElement.LIST_TYPE)) {
            return;
        }
        NbtList list = body.getList("Entities", NbtElement.COMPOUND_TYPE);
        NbtList kept = new NbtList();
        int removed = 0;
        for (int index = 0; index < list.size(); index++) {
            NbtCompound entry = list.getCompound(index);
            String id = entry.getString("Id");
            if (!KEPT_ENTITIES.contains(id) || entry.getList("Pos", NbtElement.DOUBLE_TYPE).size() != 3) {
                removed++;
                continue;
            }
            NbtCompound data = entry.getCompound("Data");
            clean(data, check);
            NbtCompound fixed = new NbtCompound();
            fixed.putString("Id", id);
            fixed.put("Pos", entry.getList("Pos", NbtElement.DOUBLE_TYPE));
            fixed.put("Data", data);
            kept.add(fixed);
        }
        if (kept.isEmpty()) {
            body.remove("Entities");
        } else {
            body.put("Entities", kept);
        }
        if (removed > 0) {
            check.warn(removed + " entidades quitadas: solo viajan las de decorado (cuadros, marcos, soportes…)");
        }
    }

    /** Quita lo peligroso de unos datos y deja los objetos de dentro sin componentes. */
    private static void clean(NbtCompound data, ShareCheck check) {
        for (String key : STRIPPED_KEYS) {
            data.remove(key);
        }
        if (data.contains("CustomName", NbtElement.STRING_TYPE)) {
            data.putString("CustomName", text(data.getString("CustomName")));
        }
        for (String key : List.copyOf(data.getKeys())) {
            NbtElement value = data.get(key);
            if (value instanceof NbtCompound compound) {
                if (item(compound)) {
                    if (!cleanItem(compound, check)) {
                        data.remove(key);
                    }
                } else {
                    clean(compound, check);
                }
            } else if (value instanceof NbtList list && list.getHeldType() == NbtElement.COMPOUND_TYPE) {
                NbtList cleaned = new NbtList();
                for (int index = 0; index < list.size(); index++) {
                    NbtCompound element = list.getCompound(index);
                    if (item(element)) {
                        if (cleanItem(element, check)) {
                            cleaned.add(element);
                        }
                    } else {
                        clean(element, check);
                        cleaned.add(element);
                    }
                }
                data.put(key, cleaned);
            }
        }
    }

    private static boolean item(NbtCompound compound) {
        return compound.contains("id", NbtElement.STRING_TYPE)
                && (compound.contains("count", NbtElement.NUMBER_TYPE) || compound.contains("Count",
                NbtElement.NUMBER_TYPE));
    }

    /** Deja el objeto en su id, su cantidad y su hueco. {@code false} si no puede entrar. */
    private static boolean cleanItem(NbtCompound item, ShareCheck check) {
        String id = item.getString("id");
        if (RESTRICTED_ITEMS.contains(id)) {
            check.warn("Objetos de administración quitados de los cofres y marcos");
            return false;
        }
        boolean hadData = false;
        for (String key : List.copyOf(item.getKeys())) {
            if (!key.equals("id") && !key.equals("count") && !key.equals("Count") && !key.equals("Slot")) {
                item.remove(key);
                hadData = true;
            }
        }
        if (hadData) {
            check.warn("Los objetos de dentro llegan sin nombre, encantamientos ni contenido");
        }
        return true;
    }

    private static NbtList texts(NbtList lines) {
        NbtList result = new NbtList();
        for (int index = 0; index < lines.size(); index++) {
            result.add(NbtString.of(text(lines.getString(index))));
        }
        return result;
    }

    /** Un texto de Minecraft en JSON sin clics, selectores ni lecturas de datos (solo letras y formato). */
    static String text(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            JsonElement clean = textElement(parsed, 0);
            return clean == null ? "\"\"" : clean.toString();
        } catch (JsonParseException | StackOverflowError exception) {
            return new JsonPrimitive(ShareText.display(json, 256)).toString();
        }
    }

    private static JsonElement textElement(JsonElement element, int depth) {
        if (depth > MAX_TEXT_DEPTH || element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return new JsonPrimitive(ShareText.display(element.getAsString(), 256));
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                JsonElement clean = textElement(child, depth + 1);
                if (clean != null) {
                    result.add(clean);
                }
            }
            return result;
        }
        JsonObject result = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (!TEXT_KEYS.contains(key)) {
                continue;
            }
            if (key.equals("extra") || key.equals("with")) {
                JsonElement clean = textElement(entry.getValue(), depth + 1);
                if (clean != null) {
                    result.add(key, clean);
                }
            } else if (key.equals("type")) {
                String type = entry.getValue().isJsonPrimitive() ? entry.getValue().getAsString() : "";
                if (type.equals("text") || type.equals("translatable") || type.equals("keybind")) {
                    result.add(key, entry.getValue());
                }
            } else if (entry.getValue().isJsonPrimitive()) {
                JsonPrimitive value = entry.getValue().getAsJsonPrimitive();
                result.add(key, value.isString() ? new JsonPrimitive(ShareText.display(value.getAsString(), 256))
                        : value);
            }
        }
        if (!result.has("text") && !result.has("translate") && !result.has("keybind")) {
            result.addProperty("text", "");
        }
        return result;
    }
}
