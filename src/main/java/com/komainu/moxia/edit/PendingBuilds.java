package com.komainu.moxia.edit;

import com.komainu.moxia.config.KomaEditConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds grandes en espera de confirmación. La geometría ya está calculada: confirmar solo la manda a la cola, así
 * que lo que se construye es exactamente lo que se estimó. Caduca a los dos minutos o al pedir otro Build.
 */
public final class PendingBuilds {
    private static final long EXPIRY_NANOS = 120_000_000_000L;
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private PendingBuilds() {
    }

    private record Pending(String description, BuildEstimate estimate, Runnable submit, long createdAt) {
    }

    /**
     * Manda el plan a la cola si es pequeño; si supera el umbral de {@code confirmBuildAboveBlocks}, lo deja en
     * espera y pregunta con botones clicables. Devuelve {@code true} si se ha encolado ya.
     */
    public static boolean submitOrHold(
            ServerPlayerEntity player,
            String description,
            BuildEstimate estimate,
            Runnable submit
    ) {
        int threshold = KomaEditConfig.settings().confirmBuildAboveBlocks();
        if (!BuildEstimate.needsConfirmation(estimate.blocks(), threshold)) {
            PENDING.remove(player.getUuid());
            submit.run();
            return true;
        }
        PENDING.put(player.getUuid(), new Pending(description, estimate, submit, System.nanoTime()));
        player.sendMessage(Text.literal("KomaEdit · ").formatted(Formatting.GOLD)
                .append(Text.literal(description + " es grande: " + estimate.summary()).formatted(Formatting.YELLOW)), false);
        player.sendMessage(Text.literal("  ")
                .append(button("[Construir]", "/komaedit confirm", Formatting.GREEN, "Manda la construcción a la cola"))
                .append(Text.literal("  "))
                .append(button("[Descartar]", "/komaedit confirm cancel", Formatting.RED, "No construye nada"))
                .append(Text.literal("  caduca en 2 min · avisa por encima de " + BuildEstimate.group(threshold)
                        + " bloques").formatted(Formatting.GRAY)), false);
        player.sendMessage(Text.literal("KomaEdit · Build grande en espera: /komaedit confirm")
                .formatted(Formatting.YELLOW), true);
        return false;
    }

    /** Encola la construcción en espera. Lanza si no hay ninguna o ha caducado. */
    public static String confirm(ServerPlayerEntity player) {
        Pending pending = take(player.getUuid())
                .orElseThrow(() -> new IllegalArgumentException("No hay ningún Build esperando confirmación"));
        pending.submit().run();
        return pending.description();
    }

    public static Optional<String> cancel(UUID playerId) {
        return take(playerId).map(Pending::description);
    }

    public static void remove(UUID playerId) {
        PENDING.remove(playerId);
    }

    public static void clearAll() {
        PENDING.clear();
    }

    private static Optional<Pending> take(UUID playerId) {
        Pending pending = PENDING.remove(playerId);
        if (pending == null || System.nanoTime() - pending.createdAt() > EXPIRY_NANOS) {
            return Optional.empty();
        }
        return Optional.of(pending);
    }

    private static MutableText button(String label, String command, Formatting color, String hover) {
        return Text.literal(label).styled(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover))));
    }
}
