package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.List;

/**
 * Leer cualquier esquema que KomaEdit entiende y escribirlo en cualquiera de sus formatos. Todo es puro (bytes y NBT):
 * se usa en un hilo aparte y en las pruebas sin arrancar Minecraft ni WorldEdit.
 */
public final class SchematicIo {
    private SchematicIo() {
    }

    /**
     * Un esquema leído.
     *
     * @param warnings lo que hubo que quitar o cambiar al leerlo, para enseñárselo al jugador
     */
    public record Loaded(SchematicData data, SchematicFormat format, List<String> warnings) {
        public Loaded {
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * Lee un archivo: se deduce el formato por lo que hay dentro.
     *
     * @param region en un {@code .litematic}, solo esa región; vacío o {@code null}, todas juntas
     */
    public static Loaded read(byte[] bytes, SchematicLimits limits, String region) {
        NbtCompound root = SchematicNbt.read(bytes, limits);
        return read(root, limits, region);
    }

    public static Loaded read(NbtCompound root, SchematicLimits limits, String region) {
        SchematicFormat format = SchematicFormat.detect(root);
        List<String> warnings = new ArrayList<>();
        if (region != null && !region.isBlank() && format != SchematicFormat.LITEMATIC) {
            throw new IllegalArgumentException("Solo los .litematic tienen regiones");
        }
        SchematicData data = switch (format) {
            case LITEMATIC -> LitematicCodec.read(root, limits, region, warnings);
            case STRUCTURE -> StructureCodec.read(root, limits, warnings);
            case SPONGE -> SpongeCodec.read(root, limits, warnings);
        };
        return new Loaded(data, format, warnings);
    }

    /**
     * El archivo en ese formato, comprimido.
     *
     * @param name el nombre del plano: en un {@code .litematic}, el de la región
     */
    public static byte[] write(SchematicData data, SchematicFormat format, String name) {
        NbtCompound root = switch (format) {
            case LITEMATIC -> LitematicCodec.write(data, name);
            case STRUCTURE -> StructureCodec.write(data);
            case SPONGE -> SpongeCodec.write(data);
        };
        return SchematicNbt.write(root);
    }

    /** El esquema {@code sponge.3} de los planos y de WorldEdit, comprimido. */
    public static byte[] toSponge(SchematicData data) {
        return write(data, SchematicFormat.SPONGE, "");
    }
}
