package com.komainu.moxia.blueprint;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.schematic.SchematicData;
import com.komainu.moxia.schematic.SchematicFormat;
import com.komainu.moxia.schematic.SchematicIo;
import com.komainu.moxia.schematic.SchematicLimits;
import com.komainu.moxia.schematic.StructureCodec;
import com.komainu.moxia.share.SchematicPacks;
import com.komainu.moxia.share.ShareConfig;
import com.komainu.moxia.share.SharePermissions;
import com.komainu.moxia.share.ShareService;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.session.ClipboardHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /komaedit blueprint export}: un plano, el portapapeles o la selección como {@code .litematic} (de serie),
 * estructura {@code .nbt} o {@code .schem}. Si el cliente tiene KomaEdit, el archivo baja a su carpeta
 * {@code schematics} (la de Litematica); si no, o con {@code -servidor}, se escribe en {@code config/komaedit/exports}
 * (hace falta {@code komaedit.share.file}). Leer el mundo o la sesión de WorldEdit pasa en el hilo del servidor;
 * convertir y escribir, en el de los planos.
 */
public final class BlueprintExport {
    /** De dónde sale lo que se exporta. */
    public enum Source {
        BLUEPRINT, CLIPBOARD, SELECTION
    }

    private BlueprintExport() {
    }

    /**
     * Lo que se pidió: {@code <plano|portapapeles|selección> [litematic|nbt|schem] [nombre] [-servidor]}, en
     * cualquier orden tras el primero.
     */
    public record Request(Source source, String blueprint, SchematicFormat format, String name, boolean server) {
        public static Request parse(String input) {
            List<String> words = new ArrayList<>();
            for (String word : (input == null ? "" : input).strip().split("\\s+")) {
                if (!word.isEmpty()) {
                    words.add(word);
                }
            }
            if (words.isEmpty()) {
                throw new IllegalArgumentException("Falta qué exportar: un plano, portapapeles o selección");
            }
            String first = words.getFirst();
            Source source = switch (first.toLowerCase(Locale.ROOT)) {
                case "portapapeles", "clipboard" -> Source.CLIPBOARD;
                case "seleccion", "selección", "selection" -> Source.SELECTION;
                default -> Source.BLUEPRINT;
            };
            SchematicFormat format = SchematicFormat.LITEMATIC;
            String name = "";
            boolean server = false;
            for (String word : words.subList(1, words.size())) {
                String lower = word.toLowerCase(Locale.ROOT);
                if (lower.equals("-servidor") || lower.equals("-server") || lower.equals("-s")) {
                    server = true;
                } else if (SchematicFormat.byName(lower).isPresent()) {
                    format = SchematicFormat.byName(lower).get();
                } else if (name.isEmpty()) {
                    name = SchematicPacks.blueprintName(word);
                } else {
                    throw new IllegalArgumentException("Sobra «" + word + "»: escribe qué exportar, el formato "
                            + "(litematic, nbt o schem) y, si quieres, el nombre del archivo");
                }
            }
            String blueprint = source == Source.BLUEPRINT ? first : "";
            if (name.isEmpty()) {
                name = switch (source) {
                    case CLIPBOARD -> "portapapeles";
                    case SELECTION -> "seleccion";
                    case BLUEPRINT -> SchematicPacks.blueprintName(BlueprintRef.parse(first).name());
                };
            }
            return new Request(source, blueprint, format, name, server);
        }

        public String fileName() {
            return name + format.extension();
        }
    }

    /** Lo que se convierte en el hilo de los planos: el {@code sponge.3} y la miniatura, si hay. */
    private interface Snapshot {
        byte[] sponge() throws IOException;

        int[] preview() throws IOException;
    }

    /** @return el mensaje de «exportando»; el resultado llega después por el chat */
    public static String export(ServerPlayerEntity player, String input) throws WorldEditException {
        Request request = Request.parse(input);
        boolean download = !request.server() && BlueprintTransfers.canDownload(player);
        if (!download) {
            if (!ShareConfig.limits().allowFiles()) {
                throw new IllegalArgumentException(request.server() ? "En este servidor no se exporta a archivo"
                        : "Para recibir el archivo hace falta KomaEdit en tu cliente (en este servidor no se exporta a"
                        + " archivo)");
            }
            if (!KomaPermissions.can(player, SharePermissions.FILE)) {
                throw new IllegalArgumentException(request.server()
                        ? "Te falta el permiso " + SharePermissions.FILE + " para escribir en el servidor"
                        : "Para recibir el archivo hace falta KomaEdit en tu cliente");
            }
        }
        BlueprintLimits limits = BlueprintConfig.limits();
        boolean unlimited = KomaPermissions.can(player, BlueprintPermissions.UNLIMITED);
        String author = player.getGameProfile().getName();
        long now = System.currentTimeMillis();
        Snapshot snapshot;
        String what;
        long created = now;
        String description = "";
        switch (request.source()) {
            case BLUEPRINT -> {
                KomaPermissions.require(player, BlueprintPermissions.LOAD);
                BlueprintLibrary.Entry entry = BlueprintService.library().require(player.getUuid(),
                        BlueprintRef.parse(request.blueprint()));
                author = entry.meta().author();
                created = entry.meta().created();
                description = entry.meta().tags().isEmpty() ? "" : "#" + String.join(" #", entry.meta().tags());
                what = entry.reference();
                snapshot = new Snapshot() {
                    @Override
                    public byte[] sponge() {
                        return BlueprintService.library().readSchematic(entry);
                    }

                    @Override
                    public int[] preview() throws IOException {
                        if (!Files.isRegularFile(entry.thumbnail())) {
                            return null;
                        }
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
                                BlueprintService.library().readThumbnail(entry)));
                        return image == null ? null : pixels(image);
                    }
                };
            }
            case CLIPBOARD -> {
                KomaPermissions.require(player, BlueprintPermissions.SAVE);
                ClipboardHolder holder;
                try {
                    holder = WorldEditService.context(player).session().getClipboard();
                } catch (EmptyClipboardException exception) {
                    throw new IllegalArgumentException("El portapapeles está vacío: copia algo con //copy");
                }
                Clipboard clipboard = holder.getClipboard();
                Transform transform = holder.getTransform();
                BlockVector3 size = clipboard.getDimensions();
                limits.checkSize(size.x(), size.y(), size.z(), unlimited);
                what = "el portapapeles";
                snapshot = clipboardSnapshot(clipboard, transform);
            }
            default -> {
                KomaPermissions.require(player, BlueprintPermissions.SAVE);
                Clipboard clipboard;
                try {
                    var region = WorldEditService.selection(player);
                    limits.checkSize(region.getWidth(), region.getHeight(), region.getLength(), unlimited);
                    clipboard = WorldEditService.copySelectionApart(player);
                } catch (IncompleteRegionException exception) {
                    throw new IllegalArgumentException("Primero selecciona una zona (//wand o la selección del Studio)");
                }
                what = "la selección";
                snapshot = clipboardSnapshot(clipboard, null);
            }
        }
        ShareService.chargeExport(player);
        MinecraftServer server = player.getServer();
        UUID owner = player.getUuid();
        String fileAuthor = author;
        long fileCreated = created;
        String fileDescription = description;
        BlueprintService.background(() -> {
            try {
                Converted converted = convert(snapshot, request, fileAuthor, fileDescription, fileCreated, now);
                server.execute(() -> deliver(server, owner, request, converted, download));
            } catch (Exception exception) {
                BlueprintService.report(server, owner, "No se pudo exportar " + what, exception);
            }
        });
        return "Exportando " + what + " como " + request.fileName() + " (" + request.format().label() + ")…";
    }

    private static Snapshot clipboardSnapshot(Clipboard clipboard, Transform transform) {
        return new Snapshot() {
            private Clipboard flat;

            private Clipboard flat() throws WorldEditException {
                if (flat == null) {
                    // Como //schem save: el giro del portapapeles (//rotate, //flip) se aplica al exportar.
                    flat = transform == null || transform.isIdentity() ? clipboard : clipboard.transform(transform);
                }
                return flat;
            }

            @Override
            public byte[] sponge() throws IOException {
                try {
                    return BlueprintService.write(flat());
                } catch (WorldEditException exception) {
                    throw new IOException(exception.getMessage(), exception);
                }
            }

            @Override
            public int[] preview() throws IOException {
                try {
                    BlueprintService.Voxels voxels = BlueprintService.voxels(flat());
                    return pixels(BlueprintThumbnail.render(voxels.grid(), 140));
                } catch (WorldEditException exception) {
                    throw new IOException(exception.getMessage(), exception);
                }
            }
        };
    }

    /** El archivo listo y lo que conviene saber de él. */
    private record Converted(byte[] file, String tip) {
    }

    /** En el hilo de los planos: del {@code sponge.3} al formato pedido, con nombre, autor, fechas y miniatura. */
    private static Converted convert(Snapshot snapshot, Request request, String author, String description,
                                     long created, long now) throws IOException {
        SchematicIo.Loaded loaded = SchematicIo.read(snapshot.sponge(), SchematicLimits.DEFAULT, null);
        int[] preview = request.format() == SchematicFormat.LITEMATIC ? snapshot.preview() : null;
        SchematicData data = loaded.data().withInfo(new SchematicData.Info(request.name(), author, description,
                created, now, preview, List.of()));
        byte[] file = SchematicIo.write(data, request.format(), request.name());
        if (file.length > FileChunks.MAX_BYTES) {
            throw new IllegalArgumentException("El archivo ocupa " + file.length / 1024 + " KiB; el máximo es "
                    + FileChunks.MAX_BYTES / 1024 + " KiB");
        }
        // Las estructuras de más de 48 de lado no caben en el bloque de estructura: se colocan con /place template.
        int side = Math.max(data.sizeX(), Math.max(data.sizeY(), data.sizeZ()));
        String tip = request.format() == SchematicFormat.STRUCTURE && side > StructureCodec.STRUCTURE_BLOCK_SIDE
                ? " · mide " + side + " de lado: el bloque de estructura carga hasta "
                + StructureCodec.STRUCTURE_BLOCK_SIDE + ", usa /place template" : "";
        return new Converted(file, tip);
    }

    private static void deliver(MinecraftServer server, UUID owner, Request request, Converted converted,
                                boolean download) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
        if (player == null) {
            return;
        }
        byte[] file = converted.file();
        String size = ProgressLine.group(Math.max(1, file.length / 1024)) + " KiB";
        String tip = converted.tip();
        if (download) {
            if (!BlueprintTransfers.download(player, request.fileName(), file)) {
                Feedback.error(player, "Ya tienes varios archivos bajando: espera a que lleguen");
                return;
            }
            player.sendMessage(BlueprintService.message("Bajando " + request.fileName() + " (" + size
                    + ") a tu carpeta schematics" + tip, Formatting.GREEN), false);
            return;
        }
        BlueprintService.background(() -> {
            try {
                Path written = ShareService.exportFiles().writeSchematic(request.name(), request.format().extension(),
                        file);
                server.execute(() -> {
                    ServerPlayerEntity online = server.getPlayerManager().getPlayer(owner);
                    if (online != null) {
                        online.sendMessage(BlueprintService.message("Exportado: config/komaedit/exports/"
                                + written.getFileName() + " (" + size + ")" + tip, Formatting.GREEN), false);
                    }
                });
            } catch (Exception exception) {
                BlueprintService.report(server, owner, "No se pudo guardar el archivo", exception);
            }
        });
    }

    private static int[] pixels(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != height) {
            return null;
        }
        return image.getRGB(0, 0, width, height, null, 0, width);
    }
}
