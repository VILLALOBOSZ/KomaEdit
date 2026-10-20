package com.komainu.moxia.preview;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manda el plan de la vista previa al cliente con KomaEdit por partes, unas pocas por tick, para no llenar la
 * conexión (unos 64 KiB por tick y jugador). Solo para quien la pidió: nadie más la ve.
 */
public final class PreviewPlanSender {
    private static final int PARTS_PER_TICK = 2;
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final Map<UUID, Outgoing> QUEUE = new LinkedHashMap<>();

    private PreviewPlanSender() {
    }

    private static final class Outgoing {
        private final int id;
        private final List<byte[]> parts;
        private int cursor;

        private Outgoing(int id, List<byte[]> parts) {
            this.id = id;
            this.parts = parts;
        }
    }

    /** Id nuevo para el plan de una vista previa. */
    public static int nextId() {
        return IDS.updateAndGet(value -> value == Integer.MAX_VALUE ? 1 : value + 1);
    }

    /** Si el cliente del jugador dibuja la vista previa él mismo. */
    public static boolean supported(ServerPlayerEntity player) {
        return player.networkHandler != null && ServerPlayNetworking.canSend(player, PreviewPlanPayload.ID);
    }

    /** Empieza a mandar el plan (sustituye al que estuviera a medias). */
    public static void start(ServerPlayerEntity player, int id, List<byte[]> parts) {
        QUEUE.put(player.getUuid(), new Outgoing(id, List.copyOf(parts)));
        sendSome(player, QUEUE.get(player.getUuid()));
    }

    /** Quita la vista previa {@code id} del cliente y deja de mandarla. */
    public static void clear(ServerPlayerEntity player, int id) {
        Outgoing outgoing = QUEUE.get(player.getUuid());
        if (outgoing != null && outgoing.id == id) {
            QUEUE.remove(player.getUuid());
        }
        if (supported(player)) {
            ServerPlayNetworking.send(player, PreviewPlanPayload.clear(id));
        }
    }

    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Outgoing>> iterator = QUEUE.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Outgoing> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !supported(player) || sendSome(player, entry.getValue())) {
                iterator.remove();
            }
        }
    }

    /** @return {@code true} si ya se mandó todo */
    private static boolean sendSome(ServerPlayerEntity player, Outgoing outgoing) {
        if (outgoing == null) {
            return true;
        }
        int end = Math.min(outgoing.parts.size(), outgoing.cursor + PARTS_PER_TICK);
        while (outgoing.cursor < end) {
            int part = outgoing.cursor++;
            ServerPlayNetworking.send(player, new PreviewPlanPayload(outgoing.id, part, outgoing.parts.size(),
                    outgoing.parts.get(part)));
        }
        return outgoing.cursor >= outgoing.parts.size();
    }

    public static void forget(UUID player) {
        QUEUE.remove(player);
    }

    public static void clearAll() {
        QUEUE.clear();
    }
}
