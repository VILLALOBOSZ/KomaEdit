package com.komainu.moxia.blueprint;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.share.ShareConfig;
import com.komainu.moxia.share.ShareService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Esquemas enteros entre el servidor y el Studio: el que se exporta baja a la carpeta {@code schematics} del jugador
 * (la de Litematica) y el que el jugador elige de esa carpeta sube para importarlo. Van en trozos de 16 KiB con un
 * presupuesto por jugador y tick ({@link FileChunks}); lo que sube pasa después por la importación de siempre
 * ({@link ShareService}), que enseña lo que trae antes de guardar nada.
 */
public final class BlueprintTransfers {
    /** Bytes por jugador y tick que bajan (unos 640 KiB/s). */
    private static final int BYTES_PER_TICK = 32 * 1024;
    /** Una subida a medias que no avanza en este tiempo se olvida. */
    private static final long STALE_MILLIS = 120_000L;
    /** Trozos de subida por jugador y tick como mucho (el cliente manda dos): más deprisa, la subida se corta. */
    private static final int UPLOAD_CHUNKS_PER_TICK = 8;
    private static final Map<UUID, FileChunks.Queue> DOWNLOADS = new ConcurrentHashMap<>();
    /**
     * Subidas a medias entre todos los jugadores y lo que ocupan, como mucho: muchos jugadores (o bots) empezando
     * subidas sin acabarlas no agotan la memoria del servidor.
     */
    private static final FileChunks.Pool UPLOAD_POOL = new FileChunks.Pool(4, 32L * 1024 * 1024);
    private static final Map<UUID, FileChunks.Assembler> UPLOADS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> CHUNKS_THIS_TICK = new ConcurrentHashMap<>();
    /** La subida que se rechazó al empezar (sin permiso, sin cuota): sus trozos se ignoran sin repetir el aviso. */
    private static final Map<UUID, Integer> REFUSED = new ConcurrentHashMap<>();
    private static final AtomicInteger TRANSFERS = new AtomicInteger();

    private BlueprintTransfers() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(BlueprintPayloads.Download.ID, BlueprintPayloads.Download.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintPayloads.Upload.ID, BlueprintPayloads.Upload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BlueprintPayloads.Upload.ID, (payload, context) ->
                context.server().execute(() -> upload(context.player(), payload.chunk())));
        ServerTickEvents.END_SERVER_TICK.register(BlueprintTransfers::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            DOWNLOADS.remove(handler.player.getUuid());
            FileChunks.Assembler upload = UPLOADS.remove(handler.player.getUuid());
            if (upload != null) {
                upload.clear();
            }
            CHUNKS_THIS_TICK.remove(handler.player.getUuid());
            REFUSED.remove(handler.player.getUuid());
        });
    }

    /** ¿Tiene el cliente KomaEdit para recibir el archivo? */
    public static boolean canDownload(ServerPlayerEntity player) {
        return ServerPlayNetworking.canSend(player, BlueprintPayloads.Download.ID);
    }

    /**
     * Pone el archivo en la cola de bajada del jugador.
     *
     * @return {@code false} si ya tiene demasiados en cola
     */
    public static boolean download(ServerPlayerEntity player, String fileName, byte[] data) {
        FileChunks.Queue queue = DOWNLOADS.computeIfAbsent(player.getUuid(), key -> new FileChunks.Queue());
        return queue.offer(FileChunks.split(TRANSFERS.incrementAndGet(), fileName, data));
    }

    private static void upload(ServerPlayerEntity player, FileChunks.Chunk chunk) {
        UUID id = player.getUuid();
        Integer refused = REFUSED.get(id);
        if (refused != null && refused == chunk.transfer()) {
            return;
        }
        if (!KomaPermissions.canAccess(player)) {
            return;
        }
        int max = (int) Math.min(FileChunks.MAX_BYTES, ShareConfig.limits().maxFileBytes());
        FileChunks.Assembler assembler = UPLOADS.computeIfAbsent(id, key -> new FileChunks.Assembler(max,
                UPLOAD_POOL));
        if (CHUNKS_THIS_TICK.merge(id, 1, Integer::sum) > UPLOAD_CHUNKS_PER_TICK) {
            assembler.clear();
            refuse(player, chunk, "llega demasiado deprisa");
            return;
        }
        boolean starting = assembler.starts(chunk);
        Optional<FileChunks.Complete> complete;
        try {
            complete = assembler.accept(chunk, System.currentTimeMillis());
        } catch (IllegalArgumentException exception) {
            refuse(player, chunk, exception.getMessage());
            return;
        }
        if (starting) {
            // Cada subida cuenta como una importación desde el primer trozo: con permiso y cuota, o se tira.
            try {
                ShareService.chargeImport(player);
            } catch (IllegalArgumentException exception) {
                assembler.clear();
                refuse(player, chunk, exception.getMessage());
                return;
            }
        }
        complete.ifPresent(file -> {
            try {
                ShareService.importUploaded(player, file);
            } catch (IllegalArgumentException exception) {
                Feedback.error(player, exception.getMessage());
            }
        });
    }

    private static void refuse(ServerPlayerEntity player, FileChunks.Chunk chunk, String why) {
        REFUSED.put(player.getUuid(), chunk.transfer());
        Feedback.error(player, "No se pudo subir «" + chunk.name() + "»: " + why);
    }

    private static void tick(MinecraftServer server) {
        CHUNKS_THIS_TICK.clear();
        if (!UPLOADS.isEmpty()) {
            long before = System.currentTimeMillis() - STALE_MILLIS;
            UPLOADS.values().removeIf(assembler -> {
                boolean drop = assembler.stale(before) || !assembler.pending();
                if (drop) {
                    // Devuelve su sitio en UPLOAD_POOL.
                    assembler.clear();
                }
                return drop;
            });
        }
        if (DOWNLOADS.isEmpty()) {
            return;
        }
        var iterator = DOWNLOADS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !canDownload(player)) {
                iterator.remove();
                continue;
            }
            for (FileChunks.Chunk chunk : entry.getValue().poll(BYTES_PER_TICK)) {
                ServerPlayNetworking.send(player, new BlueprintPayloads.Download(chunk));
            }
            if (entry.getValue().isEmpty()) {
                iterator.remove();
            }
        }
    }
}
