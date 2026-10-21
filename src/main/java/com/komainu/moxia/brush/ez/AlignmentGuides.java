package com.komainu.moxia.brush.ez;

import com.komainu.moxia.brush.BrushTool;
import com.komainu.moxia.feedback.AlignmentGuidePayload;
import com.komainu.moxia.placement.AlignmentGuide;
import com.komainu.moxia.placement.AlignmentGuideSettings;
import com.komainu.moxia.placement.PlacementSettings;
import com.komainu.moxia.placement.ScatterPlanner;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.net.StudioAim;
import com.sk89q.worldedit.math.BlockVector3;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El visualizador de la alineación ({@link AlignmentGuide}) de los pinceles {@code place}, {@code scatter} y
 * {@code array}: con uno en la mano, cada {@code updateTicks} ticks ({@code visualisations.json}) se calcula en el
 * bloque que se mira y se manda al cliente con KomaEdit, que dibuja las flechas. A los clientes sin el mod les llegan
 * unos block displays que solo ve ese jugador ({@link AlignmentGuideDisplays}). Al soltar el pincel se quita.
 */
public final class AlignmentGuides {
    /** Semilla de lo que va al azar si el pincel no fija una con {@code -i}: el ejemplo no salta. */
    private static final long EXAMPLE_SEED = 0x51A7L;
    private static final Map<UUID, AlignmentGuidePayload> SENT = new HashMap<>();

    private AlignmentGuides() {
    }

    public static void tick(MinecraftServer server) {
        AlignmentGuideSettings settings = AlignmentGuideSettings.get();
        if (server.getTicks() % settings.updateTicks() != 0) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.networkHandler == null) {
                continue;
            }
            AlignmentGuide.Result result = null;
            if (settings.enabled() && !player.isSpectator()) {
                try {
                    result = compute(player);
                } catch (RuntimeException exception) {
                    // Un pincel a medio configurar o una expresión que falla en este sitio: sin flechas.
                    result = null;
                }
            }
            show(player, result, settings);
        }
    }

    /** La alineación del pincel de colocación en la mano en el bloque que se mira, o {@code null}. */
    static AlignmentGuide.Result compute(ServerPlayerEntity player) {
        String toolId = BrushTool.id(player.getMainHandStack());
        if (toolId == null) {
            return null;
        }
        EzBrushManager.PlacementView view = EzBrushManager.placement(player.getUuid(), toolId).orElse(null);
        if (view == null) {
            return null;
        }
        HitResult hit = StudioAim.target(player, EzBrushManager.REACH);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        Direction side = block.getSide();
        BlockPos position = block.getBlockPos();
        ServerWorld world = player.getServerWorld();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        // Solo lo cargado: mirar al borde de lo cargado no debe cargar chunks para una flecha.
        ScatterPlanner.AirTest air = (x, y, z) -> world.isChunkLoaded(x >> 4, z >> 4)
                && world.getBlockState(cursor.set(x, y, z)).isAir();
        var eye = player.getEyePos();
        var look = player.getRotationVec(1.0F);
        PlacementSettings settings = view.settings();
        AlignmentGuide.Probe probe = AlignmentGuide.probe(settings,
                BlockVector3.at(position.getX(), position.getY(), position.getZ()),
                new Vec3(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ()), new Vec3(eye.x, eye.y, eye.z),
                new Vec3(look.x, look.y, look.z), view.pending(), view.stroke(), air);
        return AlignmentGuide.compute(settings, probe, settings.randomSeed() ? EXAMPLE_SEED : settings.seed());
    }

    private static void show(ServerPlayerEntity player, AlignmentGuide.Result result,
                             AlignmentGuideSettings settings) {
        if (ServerPlayNetworking.canSend(player, AlignmentGuidePayload.ID)) {
            AlignmentGuidePayload payload = AlignmentGuidePayload.of(result);
            if (!payload.equals(SENT.getOrDefault(player.getUuid(), AlignmentGuidePayload.NONE))) {
                SENT.put(player.getUuid(), payload);
                ServerPlayNetworking.send(player, payload);
            }
            return;
        }
        AlignmentGuideDisplays.show(player, settings.vanillaDisplays() ? result : null, settings.updateTicks());
    }

    /**
     * Quita las flechas de un jugador ahora mismo ({@code /komaedit debug removevisualiserentities}); con el pincel
     * en la mano vuelven en la siguiente pasada.
     *
     * @return cuántos block displays se le han retirado
     */
    public static int clear(ServerPlayerEntity player) {
        SENT.remove(player.getUuid());
        if (ServerPlayNetworking.canSend(player, AlignmentGuidePayload.ID)) {
            ServerPlayNetworking.send(player, AlignmentGuidePayload.NONE);
        }
        return AlignmentGuideDisplays.clear(player);
    }

    public static void forget(UUID player) {
        SENT.remove(player);
        AlignmentGuideDisplays.forget(player);
    }

    public static void clearAll() {
        SENT.clear();
        AlignmentGuideDisplays.clearAll();
    }
}
