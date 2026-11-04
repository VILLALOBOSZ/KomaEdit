package com.komainu.moxia.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.komainu.moxia.schematic.SchematicFormat;
import com.komainu.moxia.schematic.SchematicImport;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Un esquema suelto ({@code .litematic}, {@code .nbt}, {@code .schem} o {@code .schematic}) como un paquete con un
 * plano: así se importa por el mismo camino que un {@code .kpack}, enseñando antes lo que trae, lo que choca y lo que
 * se cambia, y con las mismas comprobaciones ({@link BlueprintKind#check}).
 */
public final class SchematicPacks {
    /** Lo más largo de un nombre de plano. */
    private static final int MAX_NAME = 32;

    private SchematicPacks() {
    }

    /** ¿Es un esquema suelto y no un paquete? Por la extensión o, si no la dice, por los primeros bytes. */
    public static boolean isSchematic(String fileName, byte[] data) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(ShareCodec.FILE_EXTENSION) || lower.endsWith(".txt") || lower.endsWith(".krecipe")) {
            return false;
        }
        return SchematicFormat.schematicFile(fileName) || SchematicImport.looksLikeSchematic(data);
    }

    /**
     * @param region en un {@code .litematic}, la región que se quiere importar; vacío, todas juntas
     */
    public static SharePack pack(String fileName, byte[] data, String region) {
        JsonObject item = new JsonObject();
        item.add("tags", new JsonArray());
        item.addProperty("author", "");
        if (region != null && !region.isBlank()) {
            item.addProperty("region", region.strip());
        }
        String name = blueprintName(fileName);
        return SharePack.of(ShareText.display(fileName, 64), "", "", 0L,
                List.of(new SharePack.Item("blueprint", name, 1, item, data)));
    }

    /**
     * El nombre de plano que sale de un archivo: sin extensión, en minúsculas, sin tildes y con {@code _} donde había
     * espacios u otros signos ({@code Casa Pequeña (v2).litematic} → {@code casa_pequena_v2}).
     */
    public static String blueprintName(String fileName) {
        String base = fileName == null ? "" : fileName.strip();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        base = base.substring(slash + 1);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        String plain = Normalizer.normalize(base, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        StringBuilder name = new StringBuilder();
        for (char character : plain.toCharArray()) {
            if (name.length() >= MAX_NAME) {
                break;
            }
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9' || character == '-') {
                name.append(character);
            } else if (!name.isEmpty() && name.charAt(name.length() - 1) != '_') {
                name.append('_');
            }
        }
        while (!name.isEmpty() && (name.charAt(name.length() - 1) == '_' || name.charAt(name.length() - 1) == '-')) {
            name.setLength(name.length() - 1);
        }
        while (!name.isEmpty() && (name.charAt(0) == '-' || name.charAt(0) == '_')) {
            name.deleteCharAt(0);
        }
        return name.isEmpty() ? "plano" : name.toString();
    }
}
