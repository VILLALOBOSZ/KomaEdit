package com.komainu.moxia.share;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.komainu.moxia.blueprint.BlueprintLibrary;
import com.komainu.moxia.blueprint.BlueprintPermissions;
import com.komainu.moxia.blueprint.BlueprintRef;
import com.komainu.moxia.blueprint.BlueprintTags;
import com.komainu.moxia.schematic.SchematicImport;
import com.komainu.moxia.schematic.SchematicLimits;
import com.komainu.moxia.schematic.SchematicUpgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Planos de la biblioteca. Datos: {@code {"size":[x,y,z], "blocks":n, "tags":[…], "author":"…"}} y el esquema
 * {@code sponge.3} como adjunto. Al importarlo el adjunto puede ser también un {@code .litematic} (con
 * {@code "region"} para quedarse con una sola), una estructura {@code .nbt} o un {@code .schem} de otra versión: se
 * convierte a {@code sponge.3} y se pone al día con el DataFixer ({@link SchematicImport}). Después pasa por
 * {@link SchematicSanitizer} (bloques que faltan, bloques de órdenes, carteles, cofres y entidades) y el tamaño y los
 * bloques se calculan de nuevo, sin creer lo que dice el paquete. La miniatura no viaja: se pinta aquí al guardarlo.
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
    private final SchematicUpgrade.Fixer fixer;
    private final SchematicImport.Legacy legacy;

    public BlueprintKind(Supplier<BlueprintLibrary> library, Sink sink) {
        this(library, sink, SchematicUpgrade.vanilla(), null);
    }

    /** @param legacy convierte los {@code .schematic} de MCEdit (en el juego, con WorldEdit), o {@code null} */
    public BlueprintKind(Supplier<BlueprintLibrary> library, Sink sink, SchematicUpgrade.Fixer fixer,
                         SchematicImport.Legacy legacy) {
        this.library = library;
        this.sink = sink;
        this.fixer = fixer;
        this.legacy = legacy;
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
        JsonObject data = item.data();
        SchematicLimits limits = SchematicLimits.DEFAULT.withNbtBytes(check.limits().maxSchematicBytes());
        SchematicImport.Prepared prepared = SchematicImport.toSponge(item.blob(), ShareCodec.string(data, "region"),
                limits, fixer, legacy);
        SchematicSanitizer.Result schematic = SchematicSanitizer.sanitize(prepared.sponge(), check, Long.MAX_VALUE);
        prepared.warnings().forEach(check::warn);
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
        String author = ShareCodec.string(data, "author");
        result.addProperty("author", ShareText.display(author.isBlank() ? prepared.info().author() : author,
                ShareCodec.MAX_AUTHOR));
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
