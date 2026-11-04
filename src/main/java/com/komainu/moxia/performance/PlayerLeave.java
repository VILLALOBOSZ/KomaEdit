package com.komainu.moxia.performance;

import com.komainu.moxia.KomaEditMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lo que KomaEdit hace cuando un jugador se va, siempre en el hilo del servidor.
 *
 * <p>Fabric lanza {@link ServerPlayConnectionEvents#DISCONNECT} desde el hilo de red (Netty) en cuanto se cierra la
 * conexión: su {@code ClientConnectionMixin} lo engancha al principio de {@code channelInactive}, con el jugador aún en
 * la lista y el servidor en mitad de su tick. Cerrar ahí la {@code EditSession} de una edición que se está colocando
 * escribía en el mundo desde dos hilos a la vez («Accessing PalettedContainer from multiple threads»), y quitarla de
 * los mapas que recorre el tick lanzaba {@code ConcurrentModificationException}: las dos cosas tumban el servidor.
 * Por eso lo de cada servicio se apunta aquí ({@link #register}) y no en el evento de Fabric:</p>
 *
 * <ul>
 *     <li>Una desconexión se pasa al hilo del servidor ({@code server.execute}; si ya está en él, corre en el acto).</li>
 *     <li>Al parar el servidor ({@code SERVER_STOPPING}, antes de guardar los mundos y con WorldEdit aún en pie) corre
 *     para todos los que siguen conectados: sus desconexiones llegan después, desde Netty, con los mundos guardándose o
 *     ya cerrados.</li>
 *     <li>Corre una vez por conexión (la de la parada y la de Netty no se repiten), y si un servicio falla los demás
 *     siguen.</li>
 * </ul>
 */
public final class PlayerLeave {
    private static final List<ServerPlayConnectionEvents.Disconnect> LISTENERS = new CopyOnWriteArrayList<>();
    /** Las conexiones ya atendidas; débil: la conexión cerrada no se queda aquí. */
    private static final Set<Object> DONE = Collections.newSetFromMap(new WeakHashMap<>());

    private PlayerLeave() {
    }

    /** Lo que hace un servicio cuando un jugador se va. Corre en el hilo del servidor. */
    public static void register(ServerPlayConnectionEvents.Disconnect listener) {
        LISTENERS.add(listener);
    }

    /** Engancha el puente con Fabric. Va lo primero al iniciar el mod: su parada corre antes que las demás. */
    public static void install() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                server.execute(() -> leave(handler, server)));
        ServerLifecycleEvents.SERVER_STOPPING.register(PlayerLeave::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> forgetConnections());
    }

    private static void stopping(MinecraftServer server) {
        for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
            leave(player.networkHandler, server);
        }
    }

    /** Los servicios, una vez por conexión. */
    static void leave(ServerPlayNetworkHandler handler, MinecraftServer server) {
        if (!first(handler)) {
            return;
        }
        for (ServerPlayConnectionEvents.Disconnect listener : LISTENERS) {
            try {
                listener.onPlayDisconnect(handler, server);
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.error("KomaEdit could not clean up after a player left", exception);
            }
        }
    }

    private static boolean first(Object connection) {
        synchronized (DONE) {
            return DONE.add(connection);
        }
    }

    static void forgetConnections() {
        synchronized (DONE) {
            DONE.clear();
        }
    }

    /** Quita un servicio (las pruebas). */
    static boolean forget(ServerPlayConnectionEvents.Disconnect listener) {
        return LISTENERS.remove(listener);
    }
}
