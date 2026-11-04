package com.komainu.moxia.terrain;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.performance.ServerWork;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sellos propios en el juego: capturar un trozo de mundo como sello ({@code /komaedit sello capturar}), borrarlos y
 * describirlos, y mandar su catálogo y sus miniaturas al Studio.
 *
 * <p>La captura lee la superficie de cada columna en el hilo del servidor, repartida entre ticks como un carril de
 * {@link ServerWork} (un chunk sin cargar como mucho por tick): parte del mapa de alturas sin hojas y baja hasta el
 * primer bloque de suelo, sin troncos, plantas ni, si se pide, agua. El sello se calcula y se guarda en un worker de
 * {@link AsyncEngine} ({@link StampCapture}, {@link OwnStamps}).</p>
 */
public final class StampService {
    /** Capturar sellos del mundo y borrar o describir los propios. */
    public static final String CAPTURE = "komaedit.stamps.capture";
    /** Borrar, describir o sustituir los sellos propios de otros. */
    public static final String MANAGE = "komaedit.admin.stamps";
    /** Sin los topes de número de sellos propios. */
    public static final String UNLIMITED = "komaedit.limit.stamps";
    /** Para usar los sellos (y exportarlos): el pincel de relieve. */
    public static final String USE = KomaPermissions.BUILD + ".brush.heightmap";
    /** Bloques por debajo del mapa de alturas que se miran como mucho buscando el suelo (agua honda, árboles). */
    private static final int MAX_SCAN = 96;
    private static final int PREVIEW_BYTES_PER_TICK = 48 * 1024;
    private static final long CATALOG_COOLDOWN_MILLIS = 250L;
    private static final Map<UUID, Capture> JOBS = new ConcurrentHashMap<>();
    private static final Map<UUID, Queue<OwnStamps.Preview>> OUTBOX = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CATALOG = new ConcurrentHashMap<>();
    private static final ExecutorService PREVIEWS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "KomaEdit sellos");
        thread.setDaemon(true);
        return thread;
    });

    private StampService() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(StampPayloads.Request.ID, StampPayloads.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(StampPayloads.Catalog.ID, StampPayloads.Catalog.CODEC);
        PayloadTypeRegistry.playS2C().register(StampPayloads.Preview.ID, StampPayloads.Preview.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(StampPayloads.Request.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(StampService::sendPreviews);
        ServerWork.lane(new ServerWork.Lane() {
            @Override
            public void owners(Collection<UUID> into) {
                for (UUID owner : JOBS.keySet()) {
                    if (!ServerWork.paused(owner)) {
                        into.add(owner);
                    }
                }
            }

            @Override
            public boolean step(MinecraftServer server, UUID owner, long deadline) {
                Capture capture = JOBS.get(owner);
                if (capture == null) {
                    return false;
                }
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(owner);
                if (player == null || player.getServerWorld() != capture.world) {
                    JOBS.remove(owner);
                    if (player != null) {
                        Feedback.warn(player, "Captura del sello «" + capture.name + "»: cambiaste de mundo; no se ha"
                                + " guardado nada");
                    }
                    return false;
                }
                if (!capture.step(deadline)) {
                    return true;
                }
                JOBS.remove(owner);
                capture.finish(server, player);
                return false;
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            JOBS.clear();
            OUTBOX.clear();
            LAST_CATALOG.clear();
            OwnStampIndex.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            JOBS.remove(handler.player.getUuid());
            OUTBOX.remove(handler.player.getUuid());
            LAST_CATALOG.remove(handler.player.getUuid());
        });
    }

    // ---------------------------------------------------------------- capturar

    /**
     * Empieza a capturar la selección como sello.
     *
     * @param keepWater el agua cuenta como suelo (la superficie de un lago); si no, se lee el fondo
     * @param category  la de la miniatura y la paleta, o {@code null} para elegirla por la superficie
     * @param replace   sustituye uno propio con ese nombre
     */
    public static void capture(ServerPlayerEntity player, String input, String description, boolean keepWater,
                               StampCategory category, boolean replace) throws IncompleteRegionException {
        KomaPermissions.require(player, CAPTURE);
        OwnStamps stamps = TerrainService.ownStamps();
        String name = input == null || input.isBlank() ? freeName(stamps) : StampCatalog.normalize(input);
        if (StampLibrary.has(name)) {
            throw new IllegalArgumentException("«" + name + "» es un sello de serie; elige otro nombre");
        }
        OwnStamps.Entry existing = stamps.find(name);
        if (existing != null && !replace) {
            throw new IllegalArgumentException("Ya hay un sello propio «" + name + "»; usa otro nombre o -r para"
                    + " sustituirlo");
        }
        if (JOBS.containsKey(player.getUuid())) {
            throw new IllegalStateException("Ya estás capturando un sello; espera a que termine");
        }
        Region region = WorldEditService.selection(player);
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        int sizeX = maximum.x() - minimum.x() + 1;
        int sizeZ = maximum.z() - minimum.z() + 1;
        StampCapture.Columns columns = new StampCapture.Columns(sizeX, sizeZ);
        ServerWorld world = player.getServerWorld();
        String source = world.getRegistryKey().getValue() + " " + (minimum.x() + sizeX / 2) + " "
                + region.getCenter().toBlockPoint().y() + " " + (minimum.z() + sizeZ / 2);
        Capture capture = new Capture(player.getUuid(), player.getGameProfile().getName(), world, region.clone(),
                minimum.x(), minimum.z(), columns, name, description, keepWater, category, replace,
                KomaPermissions.can(player, MANAGE), KomaPermissions.can(player, UNLIMITED), source);
        JOBS.put(player.getUuid(), capture);
        Feedback.status(player, "Sello «" + name + "»: leyendo " + sizeX + " × " + sizeZ + " columnas…");
    }

    /** {@code mio_1}, {@code mio_2}…: el primero que no esté cogido. */
    static String freeName(OwnStamps stamps) {
        for (int number = 1; number < 10_000; number++) {
            String name = "mio_" + number;
            if (stamps.find(name) == null) {
                return name;
            }
        }
        throw new IllegalArgumentException("Pon un nombre al sello");
    }

    /** Lo que se lee de una selección, columna a columna y fila a fila, con el presupuesto de cada tick. */
    private static final class Capture {
        private final UUID owner;
        private final String author;
        private final ServerWorld world;
        private final Region region;
        private final int minimumX;
        private final int minimumZ;
        private final StampCapture.Columns columns;
        private final String name;
        private final String description;
        private final boolean keepWater;
        private final StampCategory category;
        private final boolean replace;
        private final boolean manage;
        private final boolean unlimited;
        private final String source;
        private final int centerY;
        private final BlockPos.Mutable position = new BlockPos.Mutable();
        private int next;

        Capture(UUID owner, String author, ServerWorld world, Region region, int minimumX, int minimumZ,
                StampCapture.Columns columns, String name, String description, boolean keepWater,
                StampCategory category, boolean replace, boolean manage, boolean unlimited, String source) {
            this.owner = owner;
            this.author = author;
            this.world = world;
            this.region = region;
            this.minimumX = minimumX;
            this.minimumZ = minimumZ;
            this.columns = columns;
            this.name = name;
            this.description = description;
            this.keepWater = keepWater;
            this.category = category;
            this.replace = replace;
            this.manage = manage;
            this.unlimited = unlimited;
            this.source = source;
            this.centerY = region.getCenter().toBlockPoint().y();
        }

        /** Lee columnas hasta el plazo; {@code true} cuando ha terminado. Un chunk sin cargar se carga y se para. */
        boolean step(long deadline) {
            int total = columns.count();
            int sizeX = columns.sizeX();
            while (next < total) {
                int x = minimumX + next % sizeX;
                int z = minimumZ + next / sizeX;
                boolean loaded = world.getChunkManager().isChunkLoaded(x >> 4, z >> 4);
                read(x, z, next % sizeX, next / sizeX);
                next++;
                if (!loaded || (next & 63) == 0 && System.nanoTime() >= deadline) {
                    return next >= total;
                }
            }
            return true;
        }

        private void read(int x, int z, int column, int row) {
            if (!region.contains(BlockVector3.at(x, centerY, z))) {
                columns.set(column, row, StampCapture.NONE, false, null);
                return;
            }
            int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            int bottom = Math.max(world.getBottomY(), top - MAX_SCAN);
            boolean water = false;
            for (int y = top; y >= bottom; y--) {
                BlockState state = world.getBlockState(position.set(x, y, z));
                String id = Registries.BLOCK.getId(state.getBlock()).toString();
                TerrainCell cell = TerrainCell.classify(id, state.isAir(), !state.getFluidState().isEmpty(),
                        state.isSolidBlock(world, position));
                if (cell == TerrainCell.GROUND) {
                    columns.set(column, row, y, water, id);
                    return;
                }
                if (cell.liquid()) {
                    water = true;
                    if (keepWater) {
                        columns.set(column, row, y, true, id);
                        return;
                    }
                }
            }
            columns.set(column, row, StampCapture.NONE, water, null);
        }

        /** Calcula y guarda en un worker; avisa y manda el catálogo nuevo a todos. */
        void finish(MinecraftServer server, ServerPlayerEntity player) {
            Feedback.status(player, "Sello «" + name + "»: calculando…");
            OwnStamps stamps = TerrainService.ownStamps();
            String authorId = owner.toString();
            long created = System.currentTimeMillis();
            AsyncEngine.submit(owner, "Sello " + name, () -> {
                StampCapture.Result result = StampCapture.build(name, columns, category, TerrainService.CANCELLATION);
                OwnStamp meta = new OwnStamp(name, author, authorId, description, created, columns.sizeX(),
                        columns.sizeZ(), result.above(), result.below(), result.category(), result.hadWater(), source);
                stamps.save(meta, result.stamp(), replace, authorId, manage, OwnStamps.Limits.DEFAULT, unlimited);
                return result;
            }, result -> server.execute(() -> {
                ServerPlayerEntity current = server.getPlayerManager().getPlayer(owner);
                if (current != null) {
                    Feedback.ok(current, Text.literal(String.format(Locale.ROOT,
                            "Sello «%s» guardado: %d × %d columnas, %.0f de alto%s · ya está en la galería, en «Míos» ",
                            name, columns.sizeX(), columns.sizeZ(), result.above() + result.below(),
                            result.below() > 0.5 && result.above() > 0.5 ? " (sube y hunde)" : ""))
                            .append(Feedback.fix("[usar]", "/komaedit pincel heightmap " + name))
                            .append(Text.literal(" "))
                            .append(Feedback.fix("[describir]", "/komaedit sello describir " + name + " ")));
                }
                sendCatalogToAll(server);
            }), throwable -> server.execute(() -> {
                ServerPlayerEntity current = server.getPlayerManager().getPlayer(owner);
                if (current != null && !(throwable instanceof java.util.concurrent.CancellationException)) {
                    Feedback.error(current, "Sello «" + name + "»: " + TerrainService.readable(throwable));
                }
            }));
        }
    }

    // ---------------------------------------------------------------- borrar y describir

    public static void delete(ServerPlayerEntity player, String name) {
        KomaPermissions.require(player, CAPTURE);
        TerrainService.ownStamps().delete(name, player.getUuid().toString(), KomaPermissions.can(player, MANAGE));
        Feedback.ok(player, "Sello «" + StampCatalog.normalize(name) + "» borrado");
        sendCatalogToAll(player.getServer());
    }

    public static void describe(ServerPlayerEntity player, String name, String text) {
        KomaPermissions.require(player, CAPTURE);
        TerrainService.ownStamps().describe(name, text, player.getUuid().toString(),
                KomaPermissions.can(player, MANAGE));
        Feedback.ok(player, "Descripción del sello «" + StampCatalog.normalize(name) + "» cambiada");
        sendCatalogToAll(player.getServer());
    }

    // ---------------------------------------------------------------- Studio

    private static void handle(ServerPlayerEntity player, StampPayloads.Request request) {
        if (!KomaPermissions.canAccess(player)) {
            return;
        }
        switch (request.action()) {
            case StampPayloads.Request.CATALOG -> {
                long now = System.currentTimeMillis();
                Long last = LAST_CATALOG.get(player.getUuid());
                if (last != null && now - last < CATALOG_COOLDOWN_MILLIS) {
                    return;
                }
                LAST_CATALOG.put(player.getUuid(), now);
                sendCatalog(player);
            }
            case StampPayloads.Request.PREVIEWS -> {
                UUID viewer = player.getUuid();
                List<String> hashes = List.copyOf(request.values());
                OwnStamps stamps = TerrainService.ownStamps();
                PREVIEWS.execute(() -> {
                    for (OwnStamps.Entry entry : stamps.list()) {
                        if (hashes.contains(entry.hash())) {
                            try {
                                OUTBOX.computeIfAbsent(viewer, key -> new ConcurrentLinkedQueue<>())
                                        .add(stamps.preview(entry, OwnStamps.Preview.SIDE));
                            } catch (RuntimeException exception) {
                                KomaEditMod.LOGGER.warn("KomaEdit: no se pudo leer el sello {}: {}", entry.name(),
                                        exception.getMessage());
                            }
                        }
                    }
                });
            }
            default -> {
            }
        }
    }

    /** El catálogo de los sellos propios para el Studio. */
    public static void sendCatalog(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, StampPayloads.Catalog.ID)) {
            return;
        }
        String viewer = player.getUuid().toString();
        List<OwnStampSummary> summaries = new ArrayList<>();
        for (OwnStamps.Entry entry : TerrainService.ownStamps().list()) {
            OwnStamp meta = entry.meta();
            summaries.add(new OwnStampSummary(entry.name(), meta.author(), meta.description(), meta.category(),
                    (float) meta.heightRatio(), (float) entry.neutral(), entry.hash(), meta.sizeX(), meta.sizeZ(),
                    meta.ownedBy(viewer)));
        }
        ServerPlayNetworking.send(player, new StampPayloads.Catalog(summaries, KomaPermissions.can(player, CAPTURE),
                KomaPermissions.can(player, MANAGE)));
    }

    public static void sendCatalogToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendCatalog(player);
        }
    }

    private static void sendPreviews(MinecraftServer server) {
        if (OUTBOX.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Queue<OwnStamps.Preview>> entry : OUTBOX.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Queue<OwnStamps.Preview> queue = entry.getValue();
            if (player == null || !ServerPlayNetworking.canSend(player, StampPayloads.Preview.ID)) {
                queue.clear();
                continue;
            }
            int budget = PREVIEW_BYTES_PER_TICK;
            OwnStamps.Preview preview;
            while (budget > 0 && (preview = queue.poll()) != null) {
                ServerPlayNetworking.send(player, new StampPayloads.Preview(preview));
                budget -= preview.values().length * 2;
            }
        }
        OUTBOX.values().removeIf(Queue::isEmpty);
    }
}
