package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Los formatos de esquema que KomaEdit lee y escribe por sí mismo. Se eligen por nombre en las órdenes
 * ({@link #byName}), por la extensión del archivo ({@link #byFileName}) o, si no, por lo que hay dentro
 * ({@link #detect}): el jugador no tiene que saber de qué formato es un archivo.
 */
public enum SchematicFormat {
    LITEMATIC("litematic", ".litematic", "Litematica", List.of("litematic", "litematica", "lm")),
    STRUCTURE("nbt", ".nbt", "estructura de Minecraft", List.of("nbt", "estructura", "structure", "vanilla")),
    SPONGE("schem", ".schem", "WorldEdit", List.of("schem", "sponge", "worldedit", "we"));

    private final String id;
    private final String extension;
    private final String label;
    private final List<String> names;

    SchematicFormat(String id, String extension, String label, List<String> names) {
        this.id = id;
        this.extension = extension;
        this.label = label;
        this.names = names;
    }

    public String id() {
        return id;
    }

    /** {@code .litematic}, {@code .nbt} o {@code .schem}. */
    public String extension() {
        return extension;
    }

    /** Para los mensajes: «Litematica», «estructura de Minecraft», «WorldEdit». */
    public String label() {
        return label;
    }

    /** Lo que se escribe en las órdenes: {@code litematic}, {@code nbt}, {@code estructura}, {@code schem}… */
    public static Optional<SchematicFormat> byName(String text) {
        String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        if (value.startsWith(".")) {
            value = value.substring(1);
        }
        for (SchematicFormat format : values()) {
            if (format.names.contains(value)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    /** Por la extensión; {@code .schematic} (MCEdit) no es ninguno de estos. */
    public static Optional<SchematicFormat> byFileName(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (SchematicFormat format : values()) {
            if (lower.endsWith(format.extension)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    /** ¿Tiene el nombre de un esquema que se puede importar (incluido el {@code .schematic} de MCEdit)? */
    public static boolean schematicFile(String name) {
        return byFileName(name).isPresent() || name.toLowerCase(Locale.ROOT).endsWith(".schematic");
    }

    /** Por lo que hay dentro del NBT. */
    public static SchematicFormat detect(NbtCompound root) {
        if (LitematicCodec.matches(root)) {
            return LITEMATIC;
        }
        if (StructureCodec.matches(root)) {
            return STRUCTURE;
        }
        if (SpongeCodec.matches(root)) {
            return SPONGE;
        }
        if (legacy(root)) {
            throw new IllegalArgumentException("Es un esquema antiguo de MCEdit (.schematic, de antes de 1.13)");
        }
        throw new IllegalArgumentException("No es un .litematic, una estructura .nbt ni un .schem de WorldEdit");
    }

    /** ¿Es un {@code .schematic} de MCEdit (ids numéricos, de antes de 1.13)? */
    public static boolean legacy(NbtCompound root) {
        return root.contains("Blocks", NbtElement.BYTE_ARRAY_TYPE) && root.contains("Width", NbtElement.NUMBER_TYPE);
    }
}
