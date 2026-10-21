package com.komainu.moxia.studio.live;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.progress.ProgressBars;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.recipe.RecipeBuildService;
import com.komainu.moxia.sculpt.SnapshotCaptureService;
import com.komainu.moxia.studio.net.StudioAim;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * La vista previa en vivo del Studio en el servidor. El cliente manda cada petición por su paquete
 * ({@link LivePreviewRequestPayload}) en lugar de escribirla en el chat:
 *
 * <ul>
 *     <li><b>Sin antispam.</b> Las órdenes de chat suman al contador de Minecraft, que echa a quien no es OP; este
 *     paquete no, y {@link LiveQueue} ya impide que se acumulen.</li>
 *     <li><b>Solo la última.</b> Por jugador se calcula una como mucho; detrás espera solo la última que llegó y las de
 *     en medio se descartan sin calcular.</li>
 *     <li><b>En el mismo sitio.</b> Lo que se coloca «donde miras» sale donde cayó el Construir de la sesión
 *     ({@link LiveAnchors}), mire donde mire el jugador ahora.</li>
 *     <li><b>Con respuesta.</b> Al salir el fantasma o la vista previa, o al fallar, se contesta
 *     ({@link LivePreviewResultPayload}); la respuesta del fantasma llega antes que él, así el cliente sabe que es el
 *     del Studio. Las de rehacer no repiten en el chat el aviso de cada vista previa.</li>
 * </ul>
 *
 * La orden se ejecuta como si el jugador la escribiera: con sus permisos y por el árbol de órdenes de siempre.
 */
public final class LiveService {
    private static final LiveQueue<LivePreviewRequestPayload> QUEUE = new LiveQueue<>();
    /** Jugadores cuya petición en marcha rehace la de antes (no es el Construir): sus avisos de chat se callan. */
    private static final Set<UUID> REFRESHING = new HashSet<>();

    private LiveService() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(LivePreviewRequestPayload.ID, LivePreviewRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(LivePreviewResultPayload.ID, LivePreviewResultPayload.CODEC);
        // Ya en el hilo del servidor.
        ServerPlayNetworking.registerGlobalReceiver(LivePreviewRequestPayload.ID, (payload, context) ->
                receive(context.player(), payload));
        ServerTickEvents.START_SERVER_TICK.register(LiveService::tick);
    }

    static void receive(ServerPlayerEntity player, LivePreviewRequestPayload request) {
        if (!KomaPermissions.canAccess(player)) {
            reply(player, LivePreviewResultPayload.of(request, LivePreviewResultPayload.Outcome.FAILED, 0,
                    "No tienes permiso para usar KomaEdit"));
            return;
        }
        var problem = LiveRequests.problem(request);
        if (problem.isPresent()) {
            reply(player, LivePreviewResultPayload.of(request, LivePreviewResultPayload.Outcome.FAILED, 0,
                    problem.get()));
            return;
        }
        QUEUE.offer(player.getUuid(), request);
    }

    private static void tick(MinecraftServer server) {
        if (QUEUE.players().isEmpty()) {
            return;
        }
        long now = System.nanoTime() / 1_000_000L;
        for (UUID id : QUEUE.players()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player == null) {
                forget(id);
                continue;
            }
            boolean busy = busy(id);
            LivePreviewRequestPayload settled = QUEUE.settle(id, now, busy);
            if (settled != null) {
                REFRESHING.remove(id);
                PlanPreviews.liveEnded(player);
                reply(player, LivePreviewResultPayload.of(settled, LivePreviewResultPayload.Outcome.NOTHING, 0, ""));
            }
            LivePreviewRequestPayload next = QUEUE.next(id, now, busy);
            if (next != null) {
                run(player, next);
            }
        }
    }

    private static void run(ServerPlayerEntity player, LivePreviewRequestPayload request) {
        UUID id = player.getUuid();
        if (request.first()) {
            REFRESHING.remove(id);
        } else {
            REFRESHING.add(id);
        }
        String problem;
        try {
            problem = StudioAim.live(id, request.session(), request.first(), () -> execute(player, request));
        } catch (RuntimeException exception) {
            KomaEditMod.LOGGER.warn("KomaEdit live preview failed for {}", id, exception);
            problem = readable(exception);
        }
        if (problem != null) {
            QUEUE.failed(id);
            REFRESHING.remove(id);
            PlanPreviews.liveEnded(player);
            reply(player, LivePreviewResultPayload.of(request, LivePreviewResultPayload.Outcome.FAILED, 0, problem));
        }
    }

    /** @return por qué no se pudo, o {@code null} si la orden salió adelante */
    private static String execute(ServerPlayerEntity player, LivePreviewRequestPayload request) {
        if (request.figure()) {
            try {
                KomaPermissions.requireBuild(player, request.recipe().shape().id());
                RecipeBuildService.submit(player, request.recipe(), !request.first());
                return null;
            } catch (Exception exception) {
                return readable(exception);
            }
        }
        try {
            int result = player.getServer().getCommandManager().getDispatcher().execute(request.command(),
                    player.getCommandSource());
            return result > 0 ? null : "la orden no hizo nada (mira el chat)";
        } catch (CommandSyntaxException exception) {
            return exception.getMessage();
        }
    }

    /**
     * Acaba de salir un fantasma ({@link LivePreviewResultPayload.Outcome#GHOST}) o una vista previa
     * ({@link LivePreviewResultPayload.Outcome#PREVIEW}) para el jugador. Si lo pidió la vista previa en vivo, se le
     * contesta antes de que el cliente lo reciba.
     *
     * @return si rehacía la de antes: quien lo enseña no repite su aviso en el chat
     */
    public static boolean shown(ServerPlayerEntity player, LivePreviewResultPayload.Outcome outcome, int shownId) {
        UUID id = player.getUuid();
        LivePreviewRequestPayload request = QUEUE.shown(id);
        if (request == null) {
            return false;
        }
        REFRESHING.remove(id);
        reply(player, LivePreviewResultPayload.of(request, outcome, shownId, ""));
        return !request.first();
    }

    /** Si lo que hace ahora el jugador es rehacer su vista previa en vivo (sus avisos de chat sobran). */
    public static boolean refreshing(UUID player) {
        return REFRESHING.contains(player) && QUEUE.running(player) != null;
    }

    /** Si el servidor está leyendo, calculando o colocando algo del jugador (una vista previa esperando no cuenta). */
    static boolean busy(UUID player) {
        return SnapshotCaptureService.isRunning(player) || ProgressBars.current(player)
                .map(line -> line.phase() != ProgressLine.Phase.WAITING).orElse(false);
    }

    private static void reply(ServerPlayerEntity player, LivePreviewResultPayload result) {
        if (player.networkHandler != null && ServerPlayNetworking.canSend(player, LivePreviewResultPayload.ID)) {
            ServerPlayNetworking.send(player, result);
        }
    }

    private static String readable(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public static void forget(UUID player) {
        QUEUE.forget(player);
        REFRESHING.remove(player);
        StudioAim.forgetLive(player);
    }

    public static void clearAll() {
        QUEUE.clear();
        REFRESHING.clear();
    }
}
