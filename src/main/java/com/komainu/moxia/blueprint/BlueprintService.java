package com.komainu.moxia.blueprint;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.color.BlockColor;
import com.komainu.moxia.color.BlockColorCatalog;
import com.komainu.moxia.color.BlockColorCatalogs;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.Transform;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * La biblioteca de planos en el juego: guarda el portapapeles de WorldEdit como esquema ({@code sponge.3}) con su
 * miniatura, lo vuelve a cargar, comparte y borra. Lo que lee el mundo o la sesión de WorldEdit pasa en el hilo del
 * servidor; escribir y leer esquemas y pintar miniaturas, en un hilo propio («KomaEdit planos»), como hace WorldEdit
 * con {@code //schem save}.
 */
public final class BlueprintService {
    public static final ClipboardFormat FORMAT = BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC;
    /** Alias del formato en el JSON del plano; {@code ClipboardFormats.findByAlias} lo entiende. */
    public static final String FORMAT_ALIAS = "sponge.3";
    /** Bloques de caja que se guardan como mucho en la caché de esquemas leídos. */
    private static final long CACHE_BLOCKS = 4_000_000L;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit planos");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<String, Cached> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static long cachedBlocks;
    private static volatile BlueprintLibrary library;

    private BlueprintService() {
    }

    private record Cached(Clipboard clipboard, long blocks, BlueprintTemplate[] template) {
    }

    public static BlueprintLibrary library() {
        BlueprintLibrary current = library;
        if (current == null) {
            synchronized (BlueprintService.class) {
                current = library;
                if (current == null) {
                    current = new BlueprintLibrary(KomaEditConfig.directory().resolve("blueprints"));
                    try {
                        current.recover();
                    } catch (RuntimeException exception) {
                        KomaEditMod.LOGGER.warn("KomaEdit could not recover blueprint files: {}",
                                exception.getMessage());
                    }
                    library = current;
                }
            }
        }
        return current;
    }

    // ------------------------------------------------------------------ Guardar

    /** @return el mensaje de «guardando»; el resultado llega después por el chat */
    public static String save(ServerPlayerEntity player, String rawName, String tagsText) {
        KomaPermissions.require(player, BlueprintPermissions.SAVE);
        String name = BlueprintRef.name(rawName);
        List<String> tags = BlueprintTags.parse(tagsText);
        ClipboardHolder holder;
        try {
            holder = WorldEditService.context(player).session().getClipboard();
        } catch (EmptyClipboardException exception) {
            throw new IllegalArgumentException("El portapapeles está vacío: copia algo con //copy");
        }
        Clipboard clipboard = holder.getClipboard();
        Transform transform = holder.getTransform();
        BlockVector3 dimensions = clipboard.getDimensions();
        BlueprintLimits limits = BlueprintConfig.limits();
        boolean unlimited = KomaPermissions.can(player, BlueprintPermissions.UNLIMITED);
        limits.checkSize(dimensions.x(), dimensions.y(), dimensions.z(), unlimited);
        UUID owner = player.getUuid();
        List<BlueprintLibrary.Entry> own = library().own(owner);
        BlueprintLibrary.Entry previous = own.stream().filter(entry -> entry.meta().name().equals(name)).findFirst()
                .orElse(null);
        limits.checkCount(own.size(), previous != null, unlimited);
        String author = player.getGameProfile().getName();
        long now = System.currentTimeMillis();
        long created = previous == null ? now : previous.meta().created();
        MinecraftServer server = player.getServer();
        IO.execute(() -> {
            try {
                // Como //schem save: el giro del portapapeles (//rotate, //flip) se aplica al guardar.
                Clipboard flat = transform.isIdentity() ? clipboard : clipboard.transform(transform);
                byte[] schematic = write(flat);
                Voxels voxels = voxels(flat);
                byte[] png = BlueprintThumbnail.png(voxels.grid());
                BlueprintMeta meta = new BlueprintMeta(name, BlueprintScope.PLAYER, owner, author, created, now,
                        voxels.grid().sizeX(), voxels.grid().sizeY(), voxels.grid().sizeZ(), voxels.blocks(), tags,
                        "", FORMAT_ALIAS, "", 0L);
                BlueprintLibrary.Entry entry = library().save(meta, schematic, png, limits, unlimited);
                forget(entry);
                server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(owner);
                    if (current != null) {
                        current.sendMessage(message("Plano guardado: " + entry.reference() + " · "
                                + meta.describeSize() + " · " + meta.blocks() + " bloques"
                                + (tags.isEmpty() ? "" : " · #" + String.join(" #", tags)), Formatting.GREEN), false);
                        BlueprintNetwork.sendCatalog(current);
                    }
                });
            } catch (Exception exception) {
                report(server, owner, "No se pudo guardar el plano", exception);
            }
        });
        return "Guardando el plano «" + name + "» (" + dimensions.x() + "×" + dimensions.y() + "×" + dimensions.z()
                + ")…";
    }

    /** Copia del portapapeles a la rejilla de colores de la miniatura, contando los bloques que no son aire. */
    static Voxels voxels(Clipboard clipboard) {
        Region region = clipboard.getRegion();
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 size = region.getMaximumPoint().subtract(minimum).add(1, 1, 1);
        BlueprintThumbnail.Grid grid = new BlueprintThumbnail.Grid(size.x(), size.y(), size.z());
        BlockColorCatalog catalog = BlockColorCatalogs.get();
        Map<BlockState, Integer> colors = new HashMap<>();
        int blocks = 0;
        for (BlockVector3 position : region) {
            BlockState state = clipboard.getBlock(position);
            if (state.getBlockType().getMaterial().isAir()) {
                continue;
            }
            blocks++;
            int color = colors.computeIfAbsent(state, value -> ThumbnailColors.color(value.getBlockType().id(), id -> {
                BlockColor block = catalog.get(id);
                return block == null ? -1 : block.rgb();
            }));
            BlockVector3 local = position.subtract(minimum);
            grid.set(local.x(), local.y(), local.z(), color);
        }
        return new Voxels(grid, blocks);
    }

    record Voxels(BlueprintThumbnail.Grid grid, int blocks) {
    }

    private static byte[] write(Clipboard clipboard) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ClipboardWriter writer = FORMAT.getWriter(output)) {
            writer.write(clipboard);
        }
        return output.toByteArray();
    }

    // ------------------------------------------------------------------ Cargar

    /** Al portapapeles de WorldEdit, como {@code //schem load}. */
    public static String load(ServerPlayerEntity player, String reference) {
        KomaPermissions.require(player, BlueprintPermissions.LOAD);
        BlueprintLibrary.Entry entry = library().require(player.getUuid(), BlueprintRef.parse(reference));
        MinecraftServer server = player.getServer();
        UUID owner = player.getUuid();
        IO.execute(() -> {
            try {
                Clipboard clipboard = read(entry);
                server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(owner);
                    if (current == null) {
                        return;
                    }
                    WorldEditService.context(current).session().setClipboard(new ClipboardHolder(clipboard));
                    current.sendMessage(message("Plano en el portapapeles: " + entry.reference() + " · "
                            + entry.meta().describeSize() + " · //paste o /komaedit place Clipboard", Formatting.GREEN),
                            false);
                });
            } catch (Exception exception) {
                report(server, owner, "No se pudo cargar el plano", exception);
            }
        });
        return "Cargando " + entry.reference() + "…";
    }

    /**
     * El esquema de un plano, leído o de la caché. Se llama en el hilo del servidor al colocar o estampar: los
     * planos usados hace poco no se vuelven a leer del disco.
     */
    public static Clipboard clipboard(BlueprintLibrary.Entry entry) {
        return cached(entry).clipboard();
    }

    /** La plantilla del pincel «estampar» (bloques con el origen en el centro de la base). */
    public static BlueprintTemplate template(BlueprintLibrary.Entry entry) {
        Cached cached = cached(entry);
        synchronized (CACHE) {
            if (cached.template()[0] == null) {
                cached.template()[0] = BlueprintTemplate.of(entry.reference(), cached.clipboard());
            }
            return cached.template()[0];
        }
    }

    private static Cached cached(BlueprintLibrary.Entry entry) {
        String key = key(entry);
        synchronized (CACHE) {
            Cached cached = CACHE.get(key);
            if (cached != null) {
                return cached;
            }
        }
        Clipboard clipboard;
        try {
            clipboard = read(entry);
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer el plano " + entry.reference() + ": "
                    + exception.getMessage());
        }
        long blocks = clipboard.getRegion().getVolume();
        Cached cached = new Cached(clipboard, blocks, new BlueprintTemplate[1]);
        synchronized (CACHE) {
            CACHE.put(key, cached);
            cachedBlocks += blocks;
            var oldest = CACHE.entrySet().iterator();
            while (cachedBlocks > CACHE_BLOCKS && CACHE.size() > 1 && oldest.hasNext()) {
                Cached removed = oldest.next().getValue();
                if (removed == cached) {
                    continue;
                }
                cachedBlocks -= removed.blocks();
                oldest.remove();
            }
        }
        return cached;
    }

    private static String key(BlueprintLibrary.Entry entry) {
        long modified;
        try {
            modified = java.nio.file.Files.getLastModifiedTime(entry.schematic()).toMillis();
        } catch (IOException exception) {
            modified = 0L;
        }
        return entry.schematic() + "@" + modified;
    }

    private static void forget(BlueprintLibrary.Entry entry) {
        String prefix = entry.schematic() + "@";
        synchronized (CACHE) {
            var iterator = CACHE.entrySet().iterator();
            while (iterator.hasNext()) {
                var next = iterator.next();
                if (next.getKey().startsWith(prefix)) {
                    cachedBlocks -= next.getValue().blocks();
                    iterator.remove();
                }
            }
        }
    }

    static Clipboard read(BlueprintLibrary.Entry entry) throws IOException {
        ClipboardFormat format = ClipboardFormats.findByAlias(entry.meta().schematicFormat());
        if (format == null) {
            format = ClipboardFormats.findByFile(entry.schematic().toFile());
        }
        if (format == null) {
            throw new IOException("formato de esquema desconocido");
        }
        byte[] data = library().readSchematic(entry);
        BlueprintConfig.limits().checkFile(data.length);
        try (ClipboardReader reader = format.getReader(new ByteArrayInputStream(data))) {
            return reader.read();
        }
    }

    /**
     * Los esquemas de una lista de planos ({@code roca_1|#rocas}) para la colocación, repetidos según su peso para
     * que {@code place}, {@code scatter} y {@code array}, que eligen al azar entre sus estructuras, respeten los pesos.
     */
    public static List<Named> structures(ServerPlayerEntity player, String selection) {
        KomaPermissions.require(player, BlueprintPermissions.LOAD);
        List<BlueprintSelection.Weighted> chosen = BlueprintSelection.parse(selection)
                .resolve(library(), player.getUuid(), com.komainu.moxia.placement.PlacementService.MAX_SCHEMATICS);
        double lightest = chosen.stream().mapToDouble(BlueprintSelection.Weighted::weight).min().orElse(1.0);
        List<Named> structures = new ArrayList<>();
        for (BlueprintSelection.Weighted weighted : chosen) {
            Named named = new Named(weighted.entry().reference(), clipboard(weighted.entry()));
            long copies = Math.max(1, Math.min(16, Math.round(weighted.weight() / lightest)));
            for (int copy = 0; copy < copies; copy++) {
                structures.add(named);
            }
        }
        return structures;
    }

    public record Named(String name, Clipboard clipboard) {
    }

    // ------------------------------------------------------------------ Gestionar

    public static String delete(ServerPlayerEntity player, String reference) {
        BlueprintLibrary.Entry entry = library().require(player.getUuid(), BlueprintRef.parse(reference));
        boolean mine = entry.meta().owner().equals(player.getUuid());
        if (entry.meta().scope() == BlueprintScope.SHARED) {
            if (!(mine && KomaPermissions.can(player, BlueprintPermissions.SHARE))
                    && !KomaPermissions.can(player, BlueprintPermissions.MANAGE)) {
                throw new IllegalArgumentException("Solo quien lo compartió o un admin puede quitar "
                        + entry.reference());
            }
        } else {
            KomaPermissions.require(player, BlueprintPermissions.SAVE);
        }
        library().delete(entry);
        forget(entry);
        BlueprintNetwork.sendCatalog(player);
        return "Plano borrado: " + entry.reference();
    }

    public static String share(ServerPlayerEntity player, String reference) {
        KomaPermissions.require(player, BlueprintPermissions.SHARE);
        BlueprintRef ref = BlueprintRef.parse(reference);
        if (ref.sharedOnly()) {
            throw new IllegalArgumentException("Se comparte un plano tuyo: escribe su nombre sin servidor/");
        }
        BlueprintLibrary.Entry own = library().find(player.getUuid(), ref)
                .filter(entry -> entry.meta().scope() == BlueprintScope.PLAYER)
                .orElseThrow(() -> new IllegalArgumentException("No tienes ningún plano «" + ref.name() + "»"));
        boolean manage = KomaPermissions.can(player, BlueprintPermissions.MANAGE);
        BlueprintLibrary.Entry shared = library().share(own, player.getGameProfile().getName(),
                System.currentTimeMillis(), BlueprintConfig.limits(),
                KomaPermissions.can(player, BlueprintPermissions.UNLIMITED), manage);
        forget(shared);
        BlueprintNetwork.sendCatalogToAll(player.getServer());
        return "Plano compartido con el servidor: " + shared.reference();
    }

    public static String retag(ServerPlayerEntity player, String reference, String tagsText) {
        KomaPermissions.require(player, BlueprintPermissions.SAVE);
        BlueprintLibrary.Entry entry = library().require(player.getUuid(), BlueprintRef.parse(reference));
        if (!entry.meta().owner().equals(player.getUuid()) && !KomaPermissions.can(player, BlueprintPermissions.MANAGE)) {
            throw new IllegalArgumentException("Solo puedes cambiar las etiquetas de tus planos");
        }
        List<String> tags = BlueprintTags.parse(tagsText);
        BlueprintLibrary.Entry updated = library().retag(entry, tags, System.currentTimeMillis());
        BlueprintNetwork.sendCatalog(player);
        return updated.reference() + (tags.isEmpty() ? " ya no tiene etiquetas" : ": #" + String.join(" #", tags));
    }

    public static List<BlueprintSummary> summaries(ServerPlayerEntity player) {
        UUID viewer = player.getUuid();
        List<BlueprintSummary> summaries = new ArrayList<>();
        for (BlueprintLibrary.Entry entry : library().visible(viewer)) {
            if (summaries.size() >= BlueprintPayloads.MAX_CATALOG) {
                break;
            }
            summaries.add(BlueprintSummary.of(entry, viewer));
        }
        return summaries;
    }

    /** Lee en el hilo de los planos y entrega en el del servidor. */
    static void readThumbnail(MinecraftServer server, UUID viewer, String hash, Consumer<byte[]> onRead) {
        IO.execute(() -> {
            try {
                library().thumbnail(viewer, hash).ifPresent(entry -> {
                    byte[] data = library().readThumbnail(entry);
                    server.execute(() -> onRead.accept(data));
                });
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.warn("KomaEdit could not read a blueprint thumbnail: {}", exception.getMessage());
            }
        });
    }

    private static void report(MinecraftServer server, UUID playerId, String what, Exception exception) {
        String detail = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        if (!(exception instanceof IllegalArgumentException) && !(exception instanceof WorldEditException)) {
            KomaEditMod.LOGGER.warn("KomaEdit blueprint error: {}", detail, exception);
        }
        server.execute(() -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                player.sendMessage(message(what + ": " + detail, Formatting.RED), false);
            }
        });
    }

    static Text message(String text, Formatting color) {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD).append(Text.literal(text).formatted(color));
    }
}
