package com.komainu.moxia.region;

import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.performance.AsyncEngine;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Lee una región, calcula sobre la foto en un worker y entrega el resultado en el hilo del servidor. Es el camino de
 * las órdenes que no terminan en un {@link RegionEdits} (texto, análisis, biomas): la lectura y la escritura siguen
 * siendo del hilo del servidor y el cálculo, de {@link AsyncEngine}.
 */
public final class SnapshotJobs {
    private SnapshotJobs() {
    }

    /**
     * Para lo que edita el mundo: si el jugador cambia de dimensión antes de que termine, el resultado se descarta
     * (era de la foto del mundo de antes).
     *
     * @param compute se ejecuta en un worker: solo puede leer la foto
     * @param apply   recibe al jugador (si sigue conectado y en el mismo mundo) y el resultado, en el hilo del
     *                servidor
     */
    public static <T> void run(
            ServerPlayerEntity player,
            Region region,
            int padding,
            String description,
            Function<RegionSnapshot, T> compute,
            BiConsumer<ServerPlayerEntity, T> apply
    ) {
        ServerWorld world = player.getServerWorld();
        RegionOperationService.capture(player, region, padding, description,
                snapshot -> compute(player.getServer(), player.getUuid(), description, () -> compute.apply(snapshot),
                        (current, result) -> {
                            if (current.getServerWorld() != world) {
                                current.sendMessage(message(description + ": " + RegionOperationService.OTHER_WORLD,
                                        Formatting.YELLOW), false);
                                return;
                            }
                            apply.accept(current, result);
                        }));
    }

    /** Como {@link #run}, para lo que solo informa (el resultado se entrega aunque el jugador cambie de mundo). */
    public static <T> void read(
            ServerPlayerEntity player,
            Region region,
            int padding,
            String description,
            Function<RegionSnapshot, T> compute,
            BiConsumer<ServerPlayerEntity, T> apply
    ) {
        RegionOperationService.capture(player, region, padding, description,
                snapshot -> compute(player.getServer(), player.getUuid(), description, () -> compute.apply(snapshot),
                        apply));
    }

    /** Solo el cálculo en un worker y la entrega en el hilo del servidor, sin leer el mundo. */
    public static <T> void compute(
            MinecraftServer server,
            UUID playerId,
            String description,
            java.util.function.Supplier<T> compute,
            BiConsumer<ServerPlayerEntity, T> apply
    ) {
        AsyncEngine.submit(
                playerId,
                compute,
                result -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current == null) {
                        return;
                    }
                    try {
                        apply.accept(current, result);
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        current.sendMessage(message(description + ": " + readable(exception), Formatting.RED), false);
                    }
                }),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null && !(throwable instanceof CancellationException)) {
                        current.sendMessage(message(description + ": " + readable(throwable), Formatting.RED), false);
                    }
                })
        );
    }

    public static Text message(String text, Formatting color) {
        return Feedback.prefix()
                .append(Text.literal(text).formatted(color));
    }

    public static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
