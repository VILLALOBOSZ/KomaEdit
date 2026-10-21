package com.komainu.moxia.blueprint;

import com.komainu.moxia.permission.KomaPermissions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Red de la biblioteca de planos: el Studio pide el catálogo y las miniaturas que le faltan, y el servidor las manda
 * en trozos, con un presupuesto de bytes por jugador y tick para no atascar la conexión.
 */
public final class BlueprintNetwork {
    /** Bytes de miniaturas por jugador y tick (unos 320 KiB/s). */
    private static final int BYTES_PER_TICK = 16 * 1024;
    /** Entre dos catálogos del mismo jugador, como poco. */
    private static final long CATALOG_COOLDOWN_MILLIS = 250L;
    private static final Map<UUID, ThumbnailChunks.Queue> QUEUES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_CATALOG = new ConcurrentHashMap<>();

    private BlueprintNetwork() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(BlueprintPayloads.Request.ID, BlueprintPayloads.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintPayloads.Catalog.ID, BlueprintPayloads.Catalog.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintPayloads.Thumbnail.ID, BlueprintPayloads.Thumbnail.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.Request.ID, (payload, context) ->
                context.server().execute(() -> handle(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(BlueprintNetwork::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            QUEUES.remove(handler.player.getUuid());
            LAST_CATALOG.remove(handler.player.getUuid());
        });
    }

    private static void handle(ServerPlayerEntity player, BlueprintPayloads.Request request) {
        if (!KomaPermissions.canAccess(player)) {
            return;
        }
        switch (request.action()) {
            case BlueprintPayloads.Request.CATALOG -> {
                long now = System.currentTimeMillis();
                Long last = LAST_CATALOG.get(player.getUuid());
                if (last != null && now - last < CATALOG_COOLDOWN_MILLIS) {
                    return;
                }
                LAST_CATALOG.put(player.getUuid(), now);
                sendCatalog(player);
            }
            case BlueprintPayloads.Request.THUMBNAILS -> {
                if (!KomaPermissions.can(player, BlueprintPermissions.LOAD)) {
                    return;
                }
                UUID viewer = player.getUuid();
                MinecraftServer server = player.getServer();
                ThumbnailChunks.Queue queue = QUEUES.computeIfAbsent(viewer, key -> new ThumbnailChunks.Queue());
                for (String hash : request.values()) {
                    // La que ya se está leyendo o enviando no se vuelve a leer: pedirla sin parar no cuesta nada.
                    if (ThumbnailChunks.validHash(hash) && queue.request(hash)) {
                        BlueprintService.readThumbnail(server, viewer, hash, data -> {
                            try {
                                if (data != null && server.getPlayerManager().getPlayer(viewer) != null) {
                                    queue.offer(ThumbnailChunks.split(data));
                                }
                            } finally {
                                queue.finished(hash);
                            }
                        });
                    }
                }
            }
            default -> {
            }
        }
    }

    /** El catálogo para el Studio; vacío si el jugador no puede ver la biblioteca. */
    public static void sendCatalog(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, BlueprintPayloads.Catalog.ID)) {
            return;
        }
        boolean load = KomaPermissions.can(player, BlueprintPermissions.LOAD);
        List<BlueprintSummary> blueprints = load ? BlueprintService.summaries(player) : List.of();
        ServerPlayNetworking.send(player, new BlueprintPayloads.Catalog(blueprints,
                KomaPermissions.can(player, BlueprintPermissions.SAVE),
                KomaPermissions.can(player, BlueprintPermissions.PLACE),
                KomaPermissions.can(player, BlueprintPermissions.STAMP),
                KomaPermissions.can(player, BlueprintPermissions.SHARE) && BlueprintConfig.limits().allowSharing()));
    }

    /** Tras compartir: los compartidos cambian para todos. */
    public static void sendCatalogToAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendCatalog(player);
        }
    }

    private static void tick(MinecraftServer server) {
        if (QUEUES.isEmpty()) {
            return;
        }
        var iterator = QUEUES.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !ServerPlayNetworking.canSend(player, BlueprintPayloads.Thumbnail.ID)) {
                iterator.remove();
                continue;
            }
            for (ThumbnailChunks.Chunk chunk : entry.getValue().poll(BYTES_PER_TICK)) {
                ServerPlayNetworking.send(player, new BlueprintPayloads.Thumbnail(chunk));
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }
}
