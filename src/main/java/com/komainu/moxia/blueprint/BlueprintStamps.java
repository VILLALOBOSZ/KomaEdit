package com.komainu.moxia.blueprint;

import com.komainu.moxia.brush.ez.BrushMasks;
import com.komainu.moxia.permission.KomaPermissions;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * El pincel «estampar» en el servidor: en cada clic lee la lista de planos (por si alguno cambió), los prepara y
 * lanza la pasada. Recuerda dónde estampó hace poco para que la separación mínima valga también entre clics.
 */
public final class BlueprintStamps {
    /** Planos distintos que puede mezclar un pincel. */
    public static final int MAX_BLUEPRINTS = 32;
    /** Lo estampado hace más de esto ya no cuenta para la separación. */
    private static final long MEMORY_MILLIS = 60_000L;
    private static final int MEMORY_POINTS = 256;

    private final StampSettings settings;
    private final Deque<long[]> recent = new ArrayDeque<>();

    public BlueprintStamps(ServerPlayerEntity player, StampSettings settings) {
        KomaPermissions.require(player, BlueprintPermissions.LOAD);
        this.settings = settings;
        // Que falle al ponerlo en la mano, no al primer clic, si un plano o una etiqueta no existen.
        BlueprintSelection.parse(settings.source()).resolve(BlueprintService.library(), player.getUuid(),
                MAX_BLUEPRINTS);
        if (settings.mask() != null) {
            BrushMasks.parse(player, settings.mask());
        }
    }

    public StampSettings settings() {
        return settings;
    }

    public String controls() {
        return "clic derecho: estampa " + settings.describe() + " donde miras · clic izquierdo: olvida la separación"
                + " con lo ya estampado";
    }

    public void forget() {
        synchronized (recent) {
            recent.clear();
        }
    }

    /** La pasada de un clic derecho sobre el bloque mirado. */
    public StampOperation operation(ServerPlayerEntity player, BlockHitResult hit) {
        KomaPermissions.require(player, BlueprintPermissions.LOAD);
        List<BlueprintSelection.Weighted> chosen = BlueprintSelection.parse(settings.source())
                .resolve(BlueprintService.library(), player.getUuid(), MAX_BLUEPRINTS);
        List<BlueprintTemplate> templates = new ArrayList<>(chosen.size());
        double[] weights = new double[chosen.size()];
        for (int index = 0; index < chosen.size(); index++) {
            templates.add(BlueprintService.template(chosen.get(index).entry()));
            weights[index] = chosen.get(index).weight();
        }
        var side = hit.getSide();
        BlockVector3 anchor = BlockVector3.at(hit.getBlockPos().getX() + side.getOffsetX(),
                hit.getBlockPos().getY() + side.getOffsetY(), hit.getBlockPos().getZ() + side.getOffsetZ());
        int lookTurns = Math.floorMod(Math.round(player.getYaw() / 90.0F), 4);
        return new StampOperation(settings, List.copyOf(templates), weights, anchor, lookTurns,
                settings.mask() == null ? null : BrushMasks.parse(player, settings.mask()), occupied(),
                ThreadLocalRandom.current().nextLong(), this::remember);
    }

    private List<int[]> occupied() {
        long now = System.currentTimeMillis();
        synchronized (recent) {
            recent.removeIf(point -> now - point[2] > MEMORY_MILLIS);
            List<int[]> points = new ArrayList<>(recent.size());
            for (long[] point : recent) {
                points.add(new int[]{(int) point[0], (int) point[1]});
            }
            return points;
        }
    }

    private void remember(List<int[]> points) {
        long now = System.currentTimeMillis();
        synchronized (recent) {
            for (int[] point : points) {
                recent.addLast(new long[]{point[0], point[1], now});
            }
            while (recent.size() > MEMORY_POINTS) {
                recent.removeFirst();
            }
        }
    }
}
