package com.komainu.moxia.share;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.komainu.moxia.gradient.PaletteCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Las dos formas de un {@link SharePack}:
 * <ul>
 *     <li><b>Código</b>: {@code kpack1:} y el JSON del paquete comprimido con Deflate (con un diccionario fijo de
 *     palabras frecuentes, para que lo pequeño quepa en el chat) en Base64 apto para URL. Los esquemas de los planos
 *     van dentro en Base64.</li>
 *     <li><b>Archivo {@code .kpack}</b>: un ZIP con {@code pack.json} y los esquemas aparte en {@code blobs/}.</li>
 * </ul>
 *
 * <p>Lee también lo que había antes: los códigos de paleta {@code kp1:} ({@link PaletteCodec}) y los paquetes de
 * receta {@code .krecipe} (un ZIP con {@code recipe.json}). Todo lo que entra tiene tope de tamaño antes de
 * descomprimirse del todo; las entradas del ZIP nunca se escriben en disco por su nombre.</p>
 *
 * <p>El JSON usa claves cortas para que el código ocupe poco:</p>
 * <pre>
 * {"f":1, "n":"nombre", "a":"autor", "m":"versión del mod", "t":fecha,
 *  "i":[{"k":"palette", "n":"rocas", "v":1, "d":{…}, "b":"adjunto"}]}
 * </pre>
 */
public final class ShareCodec {
    public static final String PREFIX = "kpack1:";
    public static final String FILE_EXTENSION = ".kpack";
    public static final String MANIFEST = "pack.json";
    public static final String LEGACY_RECIPE = "recipe.json";
    /** Nombre que reciben las paletas de un código {@code kp1:}, que no lo llevaba. */
    public static final String LEGACY_PALETTE_NAME = "importada";
    static final int MAX_NAME = 48;
    static final int MAX_AUTHOR = 32;
    static final int MAX_ITEM_NAME = 64;
    private static final Pattern KIND = Pattern.compile("[a-z0-9_]{1,32}");
    private static final Pattern NEWER = Pattern.compile("kpack[0-9]+:.*", Pattern.DOTALL);
    private static final Pattern BLOB = Pattern.compile("blobs/[0-9]{1,4}\\.[a-z]{1,8}");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Diccionario de Deflate del código {@code kpack1:}: lo que más se repite en un paquete pequeño (las claves y los
     * bloques de construcción más usados), lo más frecuente al final. <b>No se puede cambiar</b>: los códigos ya
     * repartidos dejarían de leerse. Un diccionario distinto es otro prefijo ({@code kpack2:}).
     */
    static final byte[] DICTIONARY = ("polished_blackstone_bricks;cracked_polished_blackstone_bricks;blackstone;"
            + "basalt;smooth_basalt;nether_bricks;end_stone_bricks;purpur_block;prismarine_bricks;dark_prismarine;"
            + "white_concrete;light_gray_concrete;gray_concrete;black_concrete;white_wool;terracotta;"
            + "white_terracotta;brown_terracotta;red_sandstone;smooth_sandstone;cut_sandstone;sandstone;sand;"
            + "red_sand;clay;mud_bricks;packed_mud;bricks;quartz_block;smooth_quartz;calcite;dripstone_block;"
            + "snow_block;packed_ice;blue_ice;spruce_log;spruce_planks;spruce_leaves;birch_log;birch_planks;"
            + "birch_leaves;dark_oak_log;dark_oak_planks;dark_oak_leaves;oak_log;oak_planks;oak_leaves;"
            + "stripped_oak_log;moss_block;rooted_dirt;coarse_dirt;podzol;dirt;grass_block;gravel;"
            + "cobbled_deepslate;deepslate_bricks;deepslate_tiles;polished_deepslate;deepslate;tuff_bricks;"
            + "polished_tuff;tuff;polished_andesite;andesite;polished_diorite;diorite;polished_granite;granite;"
            + "mossy_cobblestone;cobblestone;cracked_stone_bricks;mossy_stone_bricks;stone_bricks;smooth_stone;"
            + "stone;minecraft:stairs[facing=north,half=bottom]_slab[type=bottom]_wall"
            + "{\"k\":\"blueprint\",\"n\":\"\",\"v\":1,\"d\":{\"size\":[],\"blocks\":,\"tags\":[]},\"b\":\"H4sIAAAAAAAA"
            + "{\"k\":\"recipe\",\"n\":\"\",\"v\":1,\"d\":{\"version\":4,\"shape\":\"\",\"pattern\":\"\",\"radius\":"
            + "{\"k\":\"sculpt\",\"n\":\"\",\"v\":1,\"d\":{\"version\":1,\"type\":\"\",\"pattern\":\"\","
            + "{\"k\":\"rock\",\"n\":\"\",\"v\":1,\"d\":{\"t\":\"boulder -b \"}},"
            + "{\"k\":\"tree\",\"n\":\"\",\"v\":1,\"d\":{\"t\":\"oak -g -h -t \"}},"
            + "{\"k\":\"noise\",\"n\":\"\",\"v\":1,\"d\":{\"t\":\"perlin(\"}},"
            + "{\"f\":1,\"n\":\"\",\"a\":\"\",\"m\":\"\",\"t\":17,\"i\":[{\"k\":\"palette\",\"n\":\"\",\"v\":1,"
            + "\"d\":{\"e\":\"stone;").getBytes(StandardCharsets.UTF_8);

    private ShareCodec() {
    }

    // ------------------------------------------------------------------ Código de texto

    /** El código de texto del paquete, con los adjuntos dentro. */
    public static String toCode(SharePack pack) {
        byte[] json = GSON.toJson(toJson(pack, null)).getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setDictionary(DICTIONARY);
            deflater.setInput(json);
            deflater.finish();
            ByteArrayOutputStream output = new ByteArrayOutputStream(json.length / 2 + 64);
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                output.write(buffer, 0, deflater.deflate(buffer));
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        } finally {
            deflater.end();
        }
    }

    /**
     * Lee un código de texto ({@code kpack1:…} o un código de paleta {@code kp1:…}). Los espacios, los saltos de
     * línea y las comillas invertidas de Discord se ignoran.
     */
    public static SharePack fromCode(String text, ShareLimits limits) {
        String code = clean(text);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Falta el código del paquete");
        }
        if (code.length() > limits.maxCodeChars()) {
            throw new IllegalArgumentException("El código tiene " + code.length() + " caracteres; el máximo es "
                    + limits.maxCodeChars() + ": pásalo como archivo .kpack");
        }
        if (code.startsWith(PaletteCodec.PREFIX)) {
            List<String> entries = PaletteCodec.decode(code);
            return new SharePack(SharePack.FORMAT, "Paleta (código kp1)", "", "", 0L,
                    List.of(PaletteKind.item(LEGACY_PALETTE_NAME, entries)));
        }
        if (!code.startsWith(PREFIX)) {
            if (NEWER.matcher(code).matches()) {
                throw new IllegalArgumentException("Ese código es de una versión más nueva de KomaEdit: actualiza el mod");
            }
            throw new IllegalArgumentException("Un código de KomaEdit empieza por " + PREFIX);
        }
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("El código está dañado (no es Base64): cópialo entero");
        }
        byte[] json = inflate(compressed, limits.maxPackBytes());
        return fromJson(parse(json), blob -> decodeInline(blob, limits), limits);
    }

    /** Quita lo que suele colarse al copiar: espacios, saltos de línea y las comillas invertidas de Discord. */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder clean = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (!Character.isWhitespace(character) && character != '`') {
                clean.append(character);
            }
        }
        return clean.toString();
    }

    private static byte[] inflate(byte[] compressed, long max) {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setDictionary(DICTIONARY);
            inflater.setInput(compressed);
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(compressed.length * 4, 1 << 16));
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("El código está incompleto: cópialo entero");
                }
                output.write(buffer, 0, read);
                if (output.size() > max) {
                    throw new IllegalArgumentException("El paquete pasa de " + max / 1024 + " KiB al descomprimirlo");
                }
            }
            return output.toByteArray();
        } catch (DataFormatException exception) {
            throw new IllegalArgumentException("El código está dañado: cópialo entero");
        } finally {
            inflater.end();
        }
    }

    private static byte[] decodeInline(String blob, ShareLimits limits) {
        if (blob.length() > limits.maxFileBytes() / 3 * 4 + 4) {
            throw new IllegalArgumentException("Un adjunto del paquete es demasiado grande");
        }
        try {
            return Base64.getDecoder().decode(blob);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Un adjunto del paquete está dañado");
        }
    }

    // ------------------------------------------------------------------ Archivo .kpack

    /** El archivo {@code .kpack}: un ZIP con {@code pack.json} y los adjuntos en {@code blobs/}. */
    public static byte[] toFile(SharePack pack) {
        Map<String, byte[]> blobs = new LinkedHashMap<>();
        JsonObject root = toJson(pack, blobs);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(MANIFEST));
            zip.write(PRETTY.toJson(root).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> blob : blobs.entrySet()) {
                zip.putNextEntry(new ZipEntry(blob.getKey()));
                zip.write(blob.getValue());
                zip.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo escribir el paquete: " + exception.getMessage(), exception);
        }
        return output.toByteArray();
    }

    /**
     * Lee lo que haya en un archivo: un {@code .kpack}, un {@code .krecipe} de antes o un texto con un código (un
     * {@code .txt} con lo que alguien pegó en Discord).
     */
    public static SharePack fromFile(byte[] data, ShareLimits limits) {
        if (data.length > limits.maxFileBytes()) {
            throw new IllegalArgumentException("El archivo ocupa " + data.length / 1024 + " KiB; el máximo es "
                    + limits.maxFileBytes() / 1024 + " KiB");
        }
        if (data.length >= 4 && data[0] == 'P' && data[1] == 'K') {
            return fromZip(data, limits);
        }
        return fromCode(new String(data, StandardCharsets.UTF_8), limits);
    }

    private static SharePack fromZip(byte[] data, ShareLimits limits) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        // Lo descomprimido de todas las entradas, también las que se saltan: un ZIP bomba no pasa de aquí.
        long[] budget = {limits.maxFileBytes() * 2 + limits.maxPackBytes()};
        int maxEntries = limits.maxItems() + 8;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!safeEntry(name)) {
                    throw new IllegalArgumentException("El paquete trae una ruta no permitida: «"
                            + ShareText.display(name, 64) + "»");
                }
                if (!seen.add(name)) {
                    throw new IllegalArgumentException("El paquete repite «" + name + "»");
                }
                if (seen.size() > maxEntries) {
                    throw new IllegalArgumentException("El paquete trae demasiados archivos");
                }
                boolean wanted = name.equals(MANIFEST) || name.equals(LEGACY_RECIPE) || BLOB.matcher(name).matches();
                long cap = name.equals(MANIFEST) || name.equals(LEGACY_RECIPE) ? limits.maxPackBytes()
                        : limits.maxFileBytes();
                byte[] read = read(zip, wanted ? cap : Long.MAX_VALUE, budget, name);
                if (wanted) {
                    entries.put(name, read);
                }
            }
            if (seen.isEmpty()) {
                throw new IllegalArgumentException("El archivo está dañado (ZIP no válido)");
            }
        } catch (ZipException exception) {
            throw new IllegalArgumentException("El archivo está dañado (ZIP no válido)");
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el archivo: " + exception.getMessage());
        }
        byte[] manifest = entries.get(MANIFEST);
        if (manifest != null) {
            return fromJson(parse(manifest), path -> {
                byte[] blob = entries.get(path);
                if (blob == null) {
                    throw new IllegalArgumentException("Al paquete le falta " + ShareText.display(path, 64));
                }
                return blob;
            }, limits);
        }
        byte[] recipe = entries.get(LEGACY_RECIPE);
        if (recipe != null) {
            JsonObject root = parse(recipe);
            return new SharePack(SharePack.FORMAT, "Receta (.krecipe)", "", "", 0L,
                    List.of(RecipeKind.legacyItem(root)));
        }
        throw new IllegalArgumentException("El archivo no es un paquete de KomaEdit (le falta " + MANIFEST + ")");
    }

    /** Una entrada con nombre relativo, sin {@code ..}, sin barras invertidas ni unidades. */
    static boolean safeEntry(String name) {
        if (name == null || name.isEmpty() || name.length() > 128 || name.startsWith("/") || name.contains("\\")
                || name.contains(":") || ShareText.unsafe(name)) {
            return false;
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static byte[] read(InputStream input, long cap, long[] budget, String name) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) > 0) {
            budget[0] -= read;
            if (budget[0] < 0) {
                throw new IllegalArgumentException("El paquete ocupa demasiado al descomprimirlo");
            }
            if (cap == Long.MAX_VALUE) {
                continue;
            }
            if (output.size() + read > cap) {
                throw new IllegalArgumentException("«" + name + "» pasa de " + cap / 1024 + " KiB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    // ------------------------------------------------------------------ JSON

    /**
     * @param blobs dónde dejar los adjuntos (el archivo), o {@code null} para meterlos en Base64 (el código)
     */
    static JsonObject toJson(SharePack pack, Map<String, byte[]> blobs) {
        JsonObject root = new JsonObject();
        root.addProperty("f", pack.format());
        if (!pack.name().isEmpty()) {
            root.addProperty("n", pack.name());
        }
        if (!pack.author().isEmpty()) {
            root.addProperty("a", pack.author());
        }
        if (!pack.modVersion().isEmpty()) {
            root.addProperty("m", pack.modVersion());
        }
        if (pack.created() > 0L) {
            root.addProperty("t", pack.created());
        }
        JsonArray items = new JsonArray();
        for (SharePack.Item item : pack.items()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("k", item.kind());
            entry.addProperty("n", item.name());
            entry.addProperty("v", item.version());
            entry.add("d", item.data());
            if (item.hasBlob()) {
                if (blobs == null) {
                    entry.addProperty("b", Base64.getEncoder().encodeToString(item.blob()));
                } else {
                    String path = "blobs/" + blobs.size() + ".bin";
                    blobs.put(path, item.blob());
                    entry.addProperty("b", path);
                }
            }
            items.add(entry);
        }
        root.add("i", items);
        return root;
    }

    private static JsonObject parse(byte[] json) {
        try {
            JsonElement element = JsonParser.parseString(new String(json, StandardCharsets.UTF_8));
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("El paquete está dañado (no es un objeto JSON)");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException | StackOverflowError exception) {
            throw new IllegalArgumentException("El paquete está dañado (JSON no válido)");
        }
    }

    static SharePack fromJson(JsonObject root, Function<String, byte[]> blobs, ShareLimits limits) {
        try {
            int format = root.has("f") ? root.get("f").getAsInt() : 0;
            if (format > SharePack.FORMAT) {
                throw new IllegalArgumentException("Paquete de una versión más nueva de KomaEdit (formato " + format
                        + "): actualiza el mod");
            }
            if (format < 1) {
                throw new IllegalArgumentException("El paquete no dice su formato");
            }
            JsonArray items = root.has("i") && root.get("i").isJsonArray() ? root.getAsJsonArray("i")
                    : new JsonArray();
            if (items.isEmpty()) {
                throw new IllegalArgumentException("El paquete está vacío");
            }
            if (items.size() > limits.maxItems()) {
                throw new IllegalArgumentException("El paquete trae " + items.size() + " cosas; el máximo es "
                        + limits.maxItems());
            }
            List<SharePack.Item> result = new ArrayList<>(items.size());
            long blobBytes = 0L;
            for (JsonElement element : items) {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("El paquete está dañado (elemento no válido)");
                }
                SharePack.Item item = item(element.getAsJsonObject(), blobs);
                blobBytes += item.blob().length;
                if (blobBytes > limits.maxFileBytes()) {
                    throw new IllegalArgumentException("Los planos del paquete pasan de "
                            + limits.maxFileBytes() / 1024 + " KiB");
                }
                result.add(item);
            }
            return new SharePack(format, ShareText.display(string(root, "n"), MAX_NAME),
                    ShareText.display(string(root, "a"), MAX_AUTHOR), ShareText.display(string(root, "m"), 32),
                    root.has("t") ? Math.max(0L, root.get("t").getAsLong()) : 0L, result);
        } catch (IllegalStateException | ClassCastException | UnsupportedOperationException
                 | NumberFormatException exception) {
            throw new IllegalArgumentException("El paquete está dañado (campo con un tipo inesperado)");
        }
    }

    private static SharePack.Item item(JsonObject entry, Function<String, byte[]> blobs) {
        String kind = string(entry, "k");
        if (!KIND.matcher(kind).matches()) {
            throw new IllegalArgumentException("El paquete está dañado (tipo «" + ShareText.display(kind, 32) + "»)");
        }
        String name = string(entry, "n");
        if (name.isEmpty() || name.length() > MAX_ITEM_NAME) {
            throw new IllegalArgumentException("El paquete está dañado (un " + kind + " sin nombre válido)");
        }
        int version = entry.has("v") ? entry.get("v").getAsInt() : 1;
        if (version < 1) {
            throw new IllegalArgumentException("El paquete está dañado (versión de " + kind + " no válida)");
        }
        JsonElement data = entry.get("d");
        if (data != null && !data.isJsonObject()) {
            throw new IllegalArgumentException("El paquete está dañado (datos de " + kind + " no válidos)");
        }
        byte[] blob = entry.has("b") ? blobs.apply(entry.get("b").getAsString()) : null;
        return new SharePack.Item(kind, name, version, data == null ? null : data.getAsJsonObject(), blob);
    }

    static String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
