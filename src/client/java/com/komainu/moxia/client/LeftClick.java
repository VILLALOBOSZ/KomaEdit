package com.komainu.moxia.client;

import com.komainu.moxia.client.brush.BrushToolControls;
import com.komainu.moxia.client.capability.CapabilityControls;
import com.komainu.moxia.client.display.DisplayEditorClient;
import com.komainu.moxia.client.gizmo.GhostClient;
import com.komainu.moxia.client.reference.ReferenceClient;
import com.komainu.moxia.client.utility.RulerClient;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;

/**
 * El clic izquierdo de KomaEdit en el mundo, antes que Minecraft, en un solo orden que se lee de arriba abajo. Cada uno
 * deja pasar el clic que no es suyo:
 * <ol>
 *     <li>lo de un momento con piezas bajo el punto de mira: las flechas del fantasma ({@link GhostClient}), los
 *     displays y su gizmo ({@link DisplayEditorClient}) y la imagen de referencia que se edita
 *     ({@link ReferenceClient});</li>
 *     <li>los modos que se quedan puestos: la regla ({@link RulerClient}), el pincel de la mano
 *     ({@link BrushToolControls}) y las capacidades ({@link CapabilityControls});</li>
 *     <li>al final, un clic en el vacío con displays elegidos los suelta.</li>
 * </ol>
 * Como con el clic derecho ({@code MinecraftClientMixin}): con la regla o una capacidad puesta se siguen agarrando las
 * flechas del gizmo, y un clic en un display no rompe el bloque de detrás.
 */
public final class LeftClick {
    private LeftClick() {
    }

    public static void register() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) ->
                GhostClient.onAttack(client, clickCount)
                        || DisplayEditorClient.onAttack(client, clickCount)
                        || ReferenceClient.onAttack(client, clickCount)
                        || RulerClient.onAttack(client, clickCount)
                        || BrushToolControls.onAttack(client, player, clickCount)
                        || CapabilityControls.onAttack(client, player, clickCount)
                        || DisplayEditorClient.onAttackEmpty(client, clickCount));
    }
}
