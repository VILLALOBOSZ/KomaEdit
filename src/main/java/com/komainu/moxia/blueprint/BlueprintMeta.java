package com.komainu.moxia.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Lo que acompaña a cada plano en su JSON: autor, fechas, tamaño, número de bloques, etiquetas y la huella de la
 * miniatura. El esquema va aparte, en el formato de WorldEdit ({@link #schematicFormat()}).
 *
 * @param owner           UUID de quien lo guardó (también en los compartidos)
 * @param author          nombre de quien lo guardó, tal y como se llamaba entonces
 * @param blocks          bloques que no son aire
 * @param thumbnail       SHA-256 del PNG de la miniatura; el cliente la pide y la guarda por esta huella
 * @param schematicFormat alias del formato de WorldEdit con que se escribió ({@code sponge.3})
 * @param sharedBy        quién lo compartió, o vacío si no es compartido
 */
public record BlueprintMeta(
        String name,
        BlueprintScope scope,
        UUID owner,
        String author,
        long created,
        long updated,
        int sizeX,
        int sizeY,
        int sizeZ,
        int blocks,
        List<String> tags,
        String thumbnail,
        String schematicFormat,
        String sharedBy,
        long sharedAt
) {
    public static final int FORMAT_VERSION = 1;

    public BlueprintMeta {
        name = BlueprintRef.name(name);
        if (scope == null || owner == null) {
            throw new IllegalArgumentException("Al plano le falta el ámbito o el dueño");
        }
        author = author == null ? "" : author;
        tags = List.copyOf(tags == null ? List.of() : tags);
        thumbnail = thumbnail == null ? "" : thumbnail;
        schematicFormat = schematicFormat == null || schematicFormat.isBlank() ? "sponge.3" : schematicFormat;
        sharedBy = sharedBy == null ? "" : sharedBy;
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1 || blocks < 0) {
            throw new IllegalArgumentException("Tamaño de plano no válido");
        }
    }

    public long volume() {
        return (long) sizeX * sizeY * sizeZ;
    }

    /** {@code 12×8×12}: ancho (X), alto (Y) y fondo (Z). */
    public String describeSize() {
        return sizeX + "×" + sizeY + "×" + sizeZ;
    }

    /** El nombre con el que se le encuentra sin ambigüedad ({@code servidor/…} si es compartido). */
    public String reference() {
        return BlueprintRef.display(name, scope);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public BlueprintMeta withTags(List<String> value, long now) {
        return new BlueprintMeta(name, scope, owner, author, created, now, sizeX, sizeY, sizeZ, blocks, value,
                thumbnail, schematicFormat, sharedBy, sharedAt);
    }

    /** La copia compartida: mismo autor y fechas, otro ámbito y quién lo compartió. */
    public BlueprintMeta asShared(String by, long now) {
        return new BlueprintMeta(name, BlueprintScope.SHARED, owner, author, created, updated, sizeX, sizeY, sizeZ,
                blocks, tags, thumbnail, schematicFormat, by, now);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_VERSION);
        root.addProperty("name", name);
        root.addProperty("scope", scope.id());
        root.addProperty("owner", owner.toString());
        root.addProperty("author", author);
        root.addProperty("created", created);
        root.addProperty("updated", updated);
        JsonArray size = new JsonArray();
        size.add(sizeX);
        size.add(sizeY);
        size.add(sizeZ);
        root.add("size", size);
        root.addProperty("blocks", blocks);
        JsonArray tagArray = new JsonArray();
        tags.forEach(tagArray::add);
        root.add("tags", tagArray);
        root.addProperty("thumbnail", thumbnail);
        root.addProperty("schematic", schematicFormat);
        if (!sharedBy.isEmpty()) {
            root.addProperty("sharedBy", sharedBy);
            root.addProperty("sharedAt", sharedAt);
        }
        return root;
    }

    public static BlueprintMeta fromJson(JsonObject root) {
        int format = root.has("format") ? root.get("format").getAsInt() : FORMAT_VERSION;
        if (format > FORMAT_VERSION) {
            throw new IllegalArgumentException("Plano de una versión más nueva de KomaEdit (formato " + format + ")");
        }
        JsonArray size = root.getAsJsonArray("size");
        if (size == null || size.size() != 3) {
            throw new IllegalArgumentException("Al plano le falta el tamaño");
        }
        List<String> tags = new ArrayList<>();
        JsonArray tagArray = root.getAsJsonArray("tags");
        if (tagArray != null) {
            for (JsonElement element : tagArray) {
                tags.add(BlueprintTags.normalize(element.getAsString()));
            }
        }
        return new BlueprintMeta(
                root.get("name").getAsString(),
                BlueprintScope.byId(root.get("scope").getAsString()),
                UUID.fromString(root.get("owner").getAsString()),
                string(root, "author"),
                root.has("created") ? root.get("created").getAsLong() : 0L,
                root.has("updated") ? root.get("updated").getAsLong() : 0L,
                size.get(0).getAsInt(),
                size.get(1).getAsInt(),
                size.get(2).getAsInt(),
                root.has("blocks") ? root.get("blocks").getAsInt() : 0,
                tags,
                string(root, "thumbnail").toLowerCase(Locale.ROOT),
                string(root, "schematic"),
                string(root, "sharedBy"),
                root.has("sharedAt") ? root.get("sharedAt").getAsLong() : 0L
        );
    }

    private static String string(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }
}
