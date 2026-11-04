package com.komainu.moxia.schematic;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Cualquier esquema que llega de fuera ({@code .litematic}, {@code .nbt}, {@code .schem} de cualquier versión o, en
 * el juego, el {@code .schematic} de MCEdit) convertido al {@code sponge.3} de los planos y puesto al día con el
 * DataFixer, listo para que lo revise {@code share.SchematicSanitizer}. Un {@code sponge.3} de esta versión pasa tal
 * cual (con sus biomas).
 */
public final class SchematicImport {
    /** Convierte un {@code .schematic} de MCEdit a {@code sponge.3}; en el juego lo hace WorldEdit. */
    @FunctionalInterface
    public interface Legacy {
        byte[] toSponge(byte[] schematic) throws IOException;
    }

    private SchematicImport() {
    }

    /**
     * El esquema convertido.
     *
     * @param sponge   el {@code sponge.3} comprimido
     * @param format   de qué formato venía
     * @param info     nombre, autor, regiones… del archivo
     * @param warnings lo que hubo que cambiar o quitar
     */
    public record Prepared(byte[] sponge, SchematicFormat format, SchematicData.Info info, List<String> warnings) {
        public Prepared {
            warnings = List.copyOf(warnings);
        }
    }

    /**
     * @param region en un {@code .litematic}, la región que se quiere; vacío, todas juntas
     * @param legacy para los {@code .schematic} de MCEdit, o {@code null} si no se pueden convertir
     */
    public static Prepared toSponge(byte[] bytes, String region, SchematicLimits limits, SchematicUpgrade.Fixer fixer,
                                    Legacy legacy) {
        NbtCompound root = SchematicNbt.read(bytes, limits);
        if (SchematicFormat.legacy(root)) {
            if (legacy == null) {
                throw new IllegalArgumentException("Es un esquema antiguo de MCEdit (.schematic, de antes de 1.13): "
                        + "ábrelo con //schem load y guárdalo como plano");
            }
            checkLegacy(root, limits);
            byte[] converted;
            try {
                converted = legacy.toSponge(bytes);
            } catch (IOException | RuntimeException exception) {
                throw new IllegalArgumentException("No se pudo leer el esquema de MCEdit: " + message(exception));
            }
            Prepared prepared = toSponge(converted, region, limits, fixer, null);
            List<String> warnings = new ArrayList<>(prepared.warnings());
            warnings.add(0, "Esquema de MCEdit (de antes de 1.13), convertido con WorldEdit");
            return new Prepared(prepared.sponge(), prepared.format(), prepared.info(), warnings);
        }
        SchematicFormat format = SchematicFormat.detect(root);
        boolean wholeFile = region == null || region.isBlank();
        if (format == SchematicFormat.SPONGE && wholeFile && SpongeCodec.version(root) == SpongeCodec.VERSION) {
            NbtCompound body = root.getCompound("Schematic");
            int dataVersion = body.getInt("DataVersion");
            if (dataVersion >= fixer.current()) {
                SpongeCodec.checkLimits(root, limits);
                List<String> warnings = new ArrayList<>();
                if (dataVersion > fixer.current()) {
                    warnings.add("Es de una versión más nueva de Minecraft (datos " + dataVersion + "; aquí, "
                            + fixer.current() + "): lo que no exista aquí se cambia");
                }
                NbtCompound metadata = body.getCompound("Metadata");
                SchematicData.Info info = new SchematicData.Info(SchematicNbt.text(metadata, "Name", 64),
                        SchematicNbt.text(metadata, "Author", 64), "", 0L, 0L, null, List.of());
                return new Prepared(bytes, format, info, warnings);
            }
        }
        SchematicIo.Loaded loaded = SchematicIo.read(root, limits, region);
        List<String> warnings = new ArrayList<>(loaded.warnings());
        SchematicData data = SchematicUpgrade.upgrade(loaded.data(), fixer, warnings);
        List<String> regions = data.info().regions();
        if (format == SchematicFormat.LITEMATIC && wholeFile && regions.size() > 1) {
            warnings.add(0, regions.size() + " regiones juntas, cada una en su sitio (" + String.join(", ", regions)
                    + "); se puede importar solo una");
        }
        return new Prepared(SchematicIo.toSponge(data), format, data.info(), warnings);
    }

    /**
     * Antes de dárselo a WorldEdit: sus medidas caben en los topes y cuadran con sus bloques. WorldEdit reserva la
     * caja entera con lo que dice la cabecera, y un {@code .schematic} de unos cien bytes que decía medir 32767 ×
     * 32767 agotaba la memoria del servidor.
     */
    static void checkLegacy(NbtCompound root, SchematicLimits limits) {
        int width = root.getShort("Width");
        int height = root.getShort("Height");
        int length = root.getShort("Length");
        if (width < 1 || height < 1 || length < 1) {
            throw new IllegalArgumentException("El esquema de MCEdit no tiene un tamaño válido");
        }
        limits.checkBox(width, height, length);
        long volume = (long) width * height * length;
        if (root.getByteArray("Blocks").length != volume || root.getByteArray("Data").length != volume) {
            throw new IllegalArgumentException("El esquema de MCEdit está dañado (sus bloques no cuadran con su tamaño)");
        }
    }

    /** ¿Tiene pinta de esquema (NBT con gzip), y no de ZIP ni de texto? */
    public static boolean looksLikeSchematic(byte[] bytes) {
        return SchematicNbt.gzip(bytes) || bytes.length > 0 && bytes[0] == NbtElement.COMPOUND_TYPE;
    }

    /**
     * Lo que se le dice al jugador de un esquema que no se pudo leer: lo que dice el lector de WorldEdit o, si no dice
     * nada, que está dañado (nunca el nombre de la clase).
     */
    private static String message(Exception exception) {
        String text = exception.getMessage();
        return text == null || text.isBlank() || exception instanceof NullPointerException
                ? "el archivo está dañado o no es un esquema de ese formato" : text;
    }
}
