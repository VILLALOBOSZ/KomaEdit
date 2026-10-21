package com.komainu.moxia.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.komainu.moxia.blueprint.BlueprintLibrary;
import com.komainu.moxia.blueprint.BlueprintPermissions;
import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintTags;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Planos de la biblioteca. Datos: {@code {"size":[x,y,z], "blocks":n, "tags":[…], "author":"…"}} y el esquema
 * {@code sponge.3} como adjunto. Al importarlo el esquema pasa por {@link SchematicSanitizer} (bloques que faltan,
 * bloques de órdenes, carteles, cofres y entidades) y el tamaño y los bloques se calculan de nuevo, sin creer lo que
 * dice el paquete. La miniatura no viaja: se pinta aquí al guardarlo.
 */
public final class BlueprintKind implements ShareKind {
    /** Guarda un plano ya saneado; en el juego, {@code BlueprintService.importShared} (lee y pinta en su hilo). */
    @FunctionalInterface
    public interface Sink {
        String save(ShareTarget target, Imported blueprint, boolean replace);
    }

    /** Un plano listo para guardar. */
    public record Imported(String name, String author, List<String> tags, byte[] schematic, int sizeX, int sizeY,
                           int sizeZ, int blocks) {
    }

    private final Supplier<BlueprintLibrary> library;
    private final Sink sink;

    public BlueprintKind(Supplier<BlueprintLibrary> library, Sink sink) {
        this.library = library;
        this.sink = sink;
    }

    @Override
    public String id() {
        return "blueprint";
    }

    @Override
    public List<String> aliases() {
        return List.of("plano", "planos", "blueprints");
    }

    @Override
    public String singular() {
        return "plano";
    }

    @Override
    public String plural() {
        return "planos";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String permission() {
        return BlueprintPermissions.SAVE;
    }

    @Override
    public List<String> names(ShareTarget target) {
        return library.get().visible(target.id()).stream().map(BlueprintLibrary.Entry::reference).toList();
    }

    @Override
    public SharePack.Item export(ShareTarget target, String name) {
        // Los compartidos del servidor también se exportan: hace falta poder verlos.
        if (!target.can(BlueprintPermissions.LOAD)) {
            throw new IllegalArgumentException("Te falta el permiso " + BlueprintPermissions.LOAD);
        }
        BlueprintLibrary.Entry entry = library.get().require(target.id(), BlueprintRef.parse(name));
        JsonObject data = new JsonObject();
        JsonArray size = new JsonArray();
        size.add(entry.meta().sizeX());
        size.add(entry.meta().sizeY());
        size.add(entry.meta().sizeZ());
        data.add("size", size);
        data.addProperty("blocks", entry.meta().blocks());
        JsonArray tags = new JsonArray();
        entry.meta().tags().forEach(tags::add);
        data.add("tags", tags);
        data.addProperty("author", entry.meta().author());
        return new SharePack.Item(id(), entry.meta().name(), version(), data,
                library.get().readSchematic(entry));
    }

    @Override
    public SharePack.Item check(SharePack.Item item, ShareCheck check) {
        if (item.version() > version()) {
            throw new IllegalArgumentException("Es de una versión más nueva de KomaEdit");
        }
        if (!item.hasBlob()) {
            throw new IllegalArgumentException("Le falta el esquema");
        }
        SchematicSanitizer.Result schematic = SchematicSanitizer.sanitize(item.blob(), check, Long.MAX_VALUE);
        JsonObject data = item.data();
        List<String> tags = new ArrayList<>();
        JsonElement tagArray = data.get("tags");
        if (tagArray != null && tagArray.isJsonArray()) {
            for (JsonElement tag : tagArray.getAsJsonArray()) {
                if (tags.size() >= BlueprintTags.MAX_TAGS) {
                    break;
                }
                try {
                    String normalized = BlueprintTags.normalize(tag.getAsString());
                    if (!tags.contains(normalized)) {
                        tags.add(normalized);
                    }
                } catch (RuntimeException ignored) {
                    // Una etiqueta que no vale aquí se queda fuera; el plano sí entra.
                }
            }
        }
        JsonObject result = new JsonObject();
        JsonArray size = new JsonArray();
        size.add(schematic.sizeX());
        size.add(schematic.sizeY());
        size.add(schematic.sizeZ());
        result.add("size", size);
        result.addProperty("blocks", schematic.blocks());
        JsonArray resultTags = new JsonArray();
        tags.forEach(resultTags::add);
        result.add("tags", resultTags);
        result.addProperty("author", ShareText.display(ShareCodec.string(data, "author"), ShareCodec.MAX_AUTHOR));
        return new SharePack.Item(id(), normalizeName(item.name()), version(), result, schematic.data());
    }

    @Override
    public String normalizeName(String name) {
        return BlueprintRef.name(name);
    }

    @Override
    public boolean exists(ShareTarget target, String name) {
        return library.get().own(target.id()).stream().anyMatch(entry -> entry.meta().name().equals(name));
    }

    @Override
    public String save(ShareTarget target, SharePack.Item item, String name, boolean replace) {
        JsonObject data = item.data();
        JsonArray size = data.getAsJsonArray("size");
        List<String> tags = new ArrayList<>();
        data.getAsJsonArray("tags").forEach(tag -> tags.add(tag.getAsString()));
        return sink.save(target, new Imported(name, ShareCodec.string(data, "author"), tags, item.blob(),
                size.get(0).getAsInt(), size.get(1).getAsInt(), size.get(2).getAsInt(),
                data.get("blocks").getAsInt()), replace);
    }
}
