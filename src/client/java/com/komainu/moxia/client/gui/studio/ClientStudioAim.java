package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.studio.net.StudioAimPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * «Donde haces clic» en el cliente: el bloque en el que se hizo clic con el Studio abierto, que se le dice al servidor
 * antes de la orden ({@link StudioAimPayload}) y que el pie enseña. Al cerrar el Studio se olvida en los dos lados.
 */
public final class ClientStudioAim {
    private static int[] last;

    private ClientStudioAim() {
    }

    /** Apunta {@code hit} en el servidor (antes de mandar la orden que va ahí). */
    public static void send(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        last = new int[]{pos.getX(), pos.getY(), pos.getZ()};
        if (ClientPlayNetworking.canSend(StudioAimPayload.ID)) {
            ClientPlayNetworking.send(new StudioAimPayload(true, pos.getX(), pos.getY(), pos.getZ(),
                    hit.getSide().getId()));
        }
    }

    /** Donde se hizo clic la última vez, o {@code null}. */
    public static int[] last() {
        return last == null ? null : last.clone();
    }

    public static void clear() {
        if (last != null && ClientPlayNetworking.canSend(StudioAimPayload.ID)) {
            ClientPlayNetworking.send(StudioAimPayload.CLEAR);
        }
        last = null;
    }
}
