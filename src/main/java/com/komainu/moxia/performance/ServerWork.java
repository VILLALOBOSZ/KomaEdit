package com.komainu.moxia.performance;

import com.komainu.moxia.KomaEditMod;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Todo lo que KomaEdit hace en el hilo del servidor tick a tick, con un presupuesto común ({@link TickBudget}).
 *
 * <ul>
 *     <li><b>Ligero</b> (pinceles, vista previa, fantasma, pintura): cada servicio pide su plazo con
 *     {@link #deadline}; si hay ediciones pesadas en marcha, entre todos los ligeros usan como mucho la mitad del
 *     presupuesto.</li>
 *     <li><b>Pesado</b> (leer una selección, colocar, pintar biomas): cada servicio es un {@link Lane} y el tiempo
 *     que queda se reparte por turnos entre jugadores ({@link FairShare}), sea cual sea la fase de cada uno.</li>
 * </ul>
 *
 * Un administrador puede poner en pausa los trabajos pesados de un jugador ({@link #pause}).
 */
public final class ServerWork {
    private static final Identifier EARLY = Identifier.of(KomaEditMod.MOD_ID, "budget_start");
    private static final Identifier LATE = Identifier.of(KomaEditMod.MOD_ID, "budget_end");
    private static final TickBudget BUDGET = new TickBudget(TickBudget.Settings.DEFAULT);
    private static final List<Consumer<MinecraftServer>> LIGHT = new ArrayList<>();
    private static final List<Lane> LANES = new ArrayList<>();
    private static final Set<UUID> PAUSED = ConcurrentHashMap.newKeySet();
    private static boolean open;
    private static long lightEnd;
    private static long windowEnd;
    private static int rotation;

    private ServerWork() {
    }

    /** Un servicio con trabajo pesado por jugador. */
    public interface Lane {
        /** Jugadores con trabajo en este servicio ahora mismo. */
        void owners(Collection<UUID> into);

        /**
         * Avanza el trabajo de {@code owner} hasta {@code deadline}.
         *
         * @return {@code true} si aún le queda
         */
        boolean step(MinecraftServer server, UUID owner, long deadline);
    }

    /** Registra los eventos de tick. Los servicios se apuntan antes con {@link #light} y {@link #lane}. */
    public static void register() {
        ServerTickEvents.START_SERVER_TICK.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        ServerTickEvents.START_SERVER_TICK.register(EARLY, server -> BUDGET.startTick(System.nanoTime()));
        ServerTickEvents.END_SERVER_TICK.register(ServerWork::tick);
        ServerTickEvents.END_SERVER_TICK.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
        ServerTickEvents.END_SERVER_TICK.register(LATE, server -> BUDGET.endTick(System.nanoTime()));
    }

    /** Un servicio ligero: se llama una vez por tick y pide su plazo con {@link #deadline}. */
    public static void light(Consumer<MinecraftServer> ticker) {
        LIGHT.add(ticker);
    }

    public static void lane(Lane lane) {
        LANES.add(lane);
    }

    public static void configure(TickBudget.Settings settings) {
        BUDGET.configure(settings);
    }

    public static TickBudget budget() {
        return BUDGET;
    }

    /**
     * Plazo para un trabajo ligero que querría usar {@code maxNanos}: nunca pasa de lo que le queda al tick. Fuera
     * del tick (una orden, una prueba) es {@code maxNanos} sin más, hasta el presupuesto.
     */
    public static long deadline(long maxNanos) {
        long now = System.nanoTime();
        if (!open) {
            return now + Math.min(maxNanos, BUDGET.allowance());
        }
        return Math.min(now + maxNanos, lightEnd);
    }

    /** Pone en pausa (o reanuda) los trabajos pesados de un jugador. */
    public static boolean pause(UUID owner, boolean paused) {
        return paused ? PAUSED.add(owner) : PAUSED.remove(owner);
    }

    public static boolean paused(UUID owner) {
        return PAUSED.contains(owner);
    }

    public static void forget(UUID owner) {
        PAUSED.remove(owner);
    }

    public static void clearAll() {
        PAUSED.clear();
    }

    private static void tick(MinecraftServer server) {
        long start = System.nanoTime();
        long allowance = BUDGET.allowance();
        windowEnd = start + allowance;
        Set<UUID> owners = new LinkedHashSet<>();
        for (Lane lane : LANES) {
            lane.owners(owners);
        }
        // Lo interactivo va primero, pero sin quitar a las ediciones pesadas más de la mitad del tick.
        lightEnd = owners.isEmpty() ? windowEnd : start + allowance / 2;
        open = true;
        try {
            for (Consumer<MinecraftServer> ticker : LIGHT) {
                try {
                    ticker.accept(server);
                } catch (RuntimeException exception) {
                    KomaEditMod.LOGGER.error("KomaEdit tick task failed", exception);
                }
            }
            if (!owners.isEmpty()) {
                int laneOffset = rotation;
                FairShare.run(List.copyOf(owners), rotation++, windowEnd, System::nanoTime,
                        (owner, deadline) -> stepOwner(server, owner, deadline, laneOffset));
            }
        } finally {
            open = false;
            BUDGET.spent(System.nanoTime() - start);
        }
    }

    /** Los servicios de un jugador, empezando por uno distinto cada tick. */
    private static boolean stepOwner(MinecraftServer server, UUID owner, long deadline, int offset) {
        boolean more = false;
        for (int index = 0; index < LANES.size(); index++) {
            if (System.nanoTime() >= deadline) {
                return true;
            }
            Lane lane = LANES.get(Math.floorMod(offset + index, LANES.size()));
            try {
                more |= lane.step(server, owner, deadline);
            } catch (RuntimeException exception) {
                KomaEditMod.LOGGER.error("KomaEdit work for {} failed", owner, exception);
            }
        }
        return more;
    }
}
