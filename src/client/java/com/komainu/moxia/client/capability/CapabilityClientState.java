package com.komainu.moxia.client.capability;

import com.komainu.moxia.capability.BuildCapability;
import com.komainu.moxia.capability.CapabilityActionPayload;
import com.komainu.moxia.capability.CapabilitySettings;
import com.komainu.moxia.capability.CapabilityStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lo último que el servidor dijo de las capacidades de este jugador. Sin servidor con KomaEdit (o hasta que llega
 * el primer estado) no hay nada activo y el cliente no intercepta ni ofrece nada.
 */
public final class CapabilityClientState {
    private static volatile CapabilityStatePayload state;

    private CapabilityClientState() {
    }

    public static void update(CapabilityStatePayload payload) {
        state = payload;
    }

    public static void clear() {
        state = null;
    }

    /** ¿Tiene el servidor KomaEdit con capacidades? */
    public static boolean supported() {
        return ClientPlayNetworking.canSend(CapabilityActionPayload.ID);
    }

    public static CapabilityStatePayload get() {
        return state;
    }

    /** Activada por el jugador (aunque ahora no pueda usarla). */
    public static boolean enabled(BuildCapability capability) {
        CapabilityStatePayload current = state;
        return current != null && (current.enabled() & capability.bit()) != 0;
    }

    public static boolean permitted(BuildCapability capability) {
        CapabilityStatePayload current = state;
        return current != null && (current.permitted() & capability.bit()) != 0;
    }

    /** Activada, con permiso y en un modo de juego que la deja usar. */
    public static boolean active(BuildCapability capability) {
        CapabilityStatePayload current = state;
        return current != null && current.modeAllowed() && enabled(capability) && permitted(capability)
                && supported();
    }

    public static Set<BuildCapability> activeSet() {
        EnumSet<BuildCapability> active = EnumSet.noneOf(BuildCapability.class);
        CapabilityStatePayload current = state;
        if (current == null || !current.modeAllowed() || !supported()) {
            return active;
        }
        active.addAll(CapabilitySettings.fromMask(current.enabled() & current.permitted()));
        return active;
    }

    public static boolean smearPending() {
        CapabilityStatePayload current = state;
        return current != null && current.smearPending();
    }
}
