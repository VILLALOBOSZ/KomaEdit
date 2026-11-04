package com.komainu.moxia.worldedit;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.schematic.SchematicData;
import com.komainu.moxia.schematic.SchematicFormat;
import com.komainu.moxia.schematic.SchematicIo;
import com.komainu.moxia.schematic.SchematicLimits;
import com.komainu.moxia.schematic.SchematicNbt;
import com.komainu.moxia.schematic.SchematicUpgrade;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Enseña a WorldEdit los {@code .litematic} y las estructuras {@code .nbt}: valen en {@code //schem load} y
 * {@code //schem save … litematic}, en la estructura {@code Schematic(N:…)} de la colocación y en la máscara
 * {@code #schemblocks}. Por dentro se convierten al {@code sponge.3} de WorldEdit ({@link SchematicIo}), puestos al día
 * con el DataFixer. Se registran una vez por proceso al arrancar el primer servidor, como las máscaras de
 * {@link EzExtensions}.
 */
public final class KomaClipboardFormats {
    /** Bytes del archivo (comprimido) que se leen como mucho. */
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static boolean installed;

    private KomaClipboardFormats() {
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> install());
    }

    private static synchronized void install() {
        if (installed) {
            return;
        }
        ClipboardFormats.registerClipboardFormat(new Format(SchematicFormat.LITEMATIC, "Litematica",
                Set.of("litematic", "litematica")));
        ClipboardFormats.registerClipboardFormat(new Format(SchematicFormat.STRUCTURE, "MinecraftStructure",
                Set.of("nbt", "structure", "estructura")));
        installed = true;
        KomaEditMod.LOGGER.info("KomaEdit schematic formats (.litematic, .nbt) registered in WorldEdit");
    }

    /** Un formato de KomaEdit visto como uno de WorldEdit. */
    static final class Format implements ClipboardFormat {
        private final SchematicFormat format;
        private final String name;
        private final Set<String> aliases;

        Format(SchematicFormat format, String name, Set<String> aliases) {
            this.format = format;
            this.name = name;
            this.aliases = aliases;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Set<String> getAliases() {
            return aliases;
        }

        @Override
        public ClipboardReader getReader(InputStream inputStream) {
            return new ClipboardReader() {
                @Override
                public Clipboard read() throws IOException {
                    byte[] bytes = inputStream.readNBytes(MAX_FILE_BYTES + 1);
                    if (bytes.length > MAX_FILE_BYTES) {
                        throw new IOException("El archivo pasa de " + MAX_FILE_BYTES / (1024 * 1024) + " MiB");
                    }
                    byte[] sponge;
                    try {
                        SchematicIo.Loaded loaded = SchematicIo.read(bytes, SchematicLimits.DEFAULT, null);
                        List<String> warnings = new ArrayList<>(loaded.warnings());
                        SchematicData data = SchematicUpgrade.upgrade(loaded.data(), SchematicUpgrade.vanilla(),
                                warnings);
                        warnings.forEach(warning -> KomaEditMod.LOGGER.info("KomaEdit {}: {}", name, warning));
                        sponge = SchematicIo.toSponge(data);
                    } catch (IllegalArgumentException exception) {
                        throw new IOException(exception.getMessage(), exception);
                    }
                    try (ClipboardReader reader = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getReader(
                            new ByteArrayInputStream(sponge))) {
                        return reader.read();
                    }
                }

                @Override
                public void close() throws IOException {
                    inputStream.close();
                }
            };
        }

        @Override
        public ClipboardWriter getWriter(OutputStream outputStream) {
            return new ClipboardWriter() {
                @Override
                public void write(Clipboard clipboard) throws IOException {
                    ByteArrayOutputStream sponge = new ByteArrayOutputStream();
                    try (ClipboardWriter writer = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC.getWriter(sponge)) {
                        writer.write(clipboard);
                    }
                    try {
                        SchematicData data = SchematicIo.read(sponge.toByteArray(), SchematicLimits.DEFAULT, null)
                                .data();
                        outputStream.write(SchematicIo.write(data, format, "Main"));
                    } catch (IllegalArgumentException exception) {
                        throw new IOException(exception.getMessage(), exception);
                    }
                }

                @Override
                public void close() throws IOException {
                    outputStream.close();
                }
            };
        }

        /** Por la extensión y por empezar como un gzip: sin leer el archivo entero. */
        @Override
        public boolean isFormat(File file) {
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(format.extension())) {
                return false;
            }
            try (InputStream input = new FileInputStream(file)) {
                return SchematicNbt.gzip(input.readNBytes(2));
            } catch (IOException exception) {
                return false;
            }
        }

        @Override
        public String getPrimaryFileExtension() {
            return format.extension().substring(1);
        }

        @Override
        public Set<String> getFileExtensions() {
            return Set.of(getPrimaryFileExtension());
        }
    }
}
