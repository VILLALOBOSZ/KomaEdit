package com.komainu.moxia.command;

import com.komainu.moxia.feedback.Feedback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Las órdenes que cambiaron de nombre al unir herramientas siguen funcionando igual; la primera vez que alguien usa
 * una en cada sesión del servidor se le dice, sin estorbar, cómo se llama ahora ({@code /komaedit snow} es
 * {@code /komaedit retocar nieve}). Una sola línea gris, con un botón que escribe la nueva en el chat.
 */
final class Renamed {
    private static final Map<UUID, Set<String>> TOLD = new ConcurrentHashMap<>();

    private Renamed() {
    }

    /** El aviso, solo la primera vez para este jugador y esta orden. */
    static void notice(ServerPlayerEntity player, String old, String now) {
        if (!TOLD.computeIfAbsent(player.getUuid(), id -> ConcurrentHashMap.newKeySet()).add(old)) {
            return;
        }
        Feedback.info(player, Text.literal(message(old, now) + " ").formatted(Formatting.GRAY)
                .append(Feedback.fix("[usarla]", now)));
    }

    /** «/komaedit snow ahora se llama /komaedit retocar nieve; la de antes sigue valiendo». */
    static String message(String old, String now) {
        return old + " ahora se llama " + now + "; la de antes sigue valiendo";
    }

    static void forget(UUID player) {
        TOLD.remove(player);
    }

    static void clearAll() {
        TOLD.clear();
    }
}
