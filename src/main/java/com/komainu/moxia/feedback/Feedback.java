package com.komainu.moxia.feedback;

import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.progress.ProgressLine;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.UUID;

/**
 * Estilo común de los mensajes de KomaEdit: un prefijo corto ({@code KE ·}), un color por nivel y una línea por
 * acción.
 *
 * <ul>
 *     <li>{@link Level#OK} verde: algo terminó bien.</li>
 *     <li>{@link Level#REPLY} aguamarina: la respuesta a una consulta (estado, ajustes, listas); sale siempre.</li>
 *     <li>{@link Level#INFO} gris: informativo; no sale con {@code /komaedit quiet}.</li>
 *     <li>{@link Level#WARN} amarillo: algo no salió del todo o hay que mirarlo.</li>
 *     <li>{@link Level#ERROR} rojo: no se hizo; mejor con la causa y cómo arreglarlo ({@link #fix}).</li>
 * </ul>
 *
 * Lo efímero (progreso, «calculando…», «en cola») va a la action bar con {@link #status}, también silenciable. Los
 * números de {@link #format} salen con separador de miles y en blanco. A los clientes con el mod les llega además
 * un {@link FeedbackEventPayload} para el sonido de confirmar, terminar o fallar.
 */
public final class Feedback {
    public static final String PREFIX = "KE";

    private Feedback() {
    }

    public enum Level {
        OK(Formatting.GREEN),
        REPLY(Formatting.AQUA),
        INFO(Formatting.GRAY),
        WARN(Formatting.YELLOW),
        ERROR(Formatting.RED);

        private final Formatting color;

        Level(Formatting color) {
            this.color = color;
        }

        public Formatting color() {
            return color;
        }
    }

    /** Por dónde sale un mensaje. */
    public enum Channel {
        CHAT,
        ACTION_BAR,
        NONE
    }

    /** A quién se le entrega: el jugador, o uno de mentira en las pruebas. */
    public interface Target {
        UUID id();

        void chat(Text text);

        void actionBar(Text text);

        /** Aviso para el sonido del cliente; los clientes sin el mod no reciben nada. */
        void event(FeedbackEventPayload.Kind kind);
    }

    /**
     * Dónde sale un mensaje: los errores y avisos, siempre al chat; lo informativo, al chat salvo en modo
     * silencioso; lo efímero, a la action bar salvo en modo silencioso.
     */
    public static Channel route(Level level, boolean ephemeral, boolean quiet) {
        if (level == Level.ERROR || level == Level.WARN) {
            return ephemeral ? Channel.ACTION_BAR : Channel.CHAT;
        }
        if (quiet && (ephemeral || level == Level.INFO)) {
            return Channel.NONE;
        }
        return ephemeral ? Channel.ACTION_BAR : Channel.CHAT;
    }

    // ---------------------------------------------------------------- textos

    /** {@code KE ·}: corto, para que el mensaje quepa en una línea. */
    public static MutableText prefix() {
        return Text.literal(PREFIX).formatted(Formatting.GOLD)
                .append(Text.literal(" · ").formatted(Formatting.DARK_GRAY));
    }

    /** El prefijo y el cuerpo con el color del nivel (lo que ya traiga color propio lo conserva). */
    public static MutableText line(Level level, Text body) {
        return prefix().append(Text.empty().append(body).formatted(level.color()));
    }

    public static MutableText line(Level level, String body) {
        return line(level, Text.literal(body));
    }

    /**
     * Texto con huecos {@code %s}: los números salen agrupados ({@code 12 300}) y en blanco; el resto, tal cual.
     * Sobran huecos: se quedan vacíos; sobran valores: se añaden al final separados por espacio.
     */
    public static MutableText format(String template, Object... values) {
        MutableText text = Text.empty();
        int start = 0;
        int used = 0;
        while (true) {
            int hole = template.indexOf("%s", start);
            if (hole < 0) {
                break;
            }
            if (hole > start) {
                text.append(template.substring(start, hole));
            }
            if (used < values.length) {
                text.append(value(values[used++]));
            }
            start = hole + 2;
        }
        if (start < template.length()) {
            text.append(template.substring(start));
        }
        while (used < values.length) {
            text.append(" ").append(value(values[used++]));
        }
        return text;
    }

    /** Un número agrupado y en blanco. */
    public static MutableText number(long value) {
        return Text.literal(ProgressLine.group(value)).formatted(Formatting.WHITE);
    }

    /** «1 bloque», «12 300 bloques». */
    public static String count(long value, String singular, String plural) {
        return ProgressLine.group(value) + " " + (value == 1 ? singular : plural);
    }

    /** Botón que ejecuta {@code command} al pulsarlo. */
    public static MutableText button(String label, String command, Formatting color, String hover) {
        return Text.literal(label).styled(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover))));
    }

    /** Texto que escribe {@code command} en el chat (sin ejecutarlo) para corregirlo o completarlo. */
    public static MutableText fix(String label, String command) {
        String shown = slashed(command);
        return Text.literal(label).styled(style -> style
                .withColor(Formatting.AQUA)
                .withUnderline(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, shown))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Text.literal("Pulsa para escribir ").append(Text.literal(shown).formatted(Formatting.WHITE)))));
    }

    /** La orden con su barra delante, como se escribe en el chat. */
    public static String slashed(String command) {
        String trimmed = command == null ? "" : command.strip();
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    /** La causa en rojo y, en gris, cómo arreglarlo. */
    public static MutableText problem(String cause, String remedy) {
        MutableText text = Text.literal(cause);
        if (remedy != null && !remedy.isBlank()) {
            text.append(Text.literal(" · " + remedy).formatted(Formatting.GRAY));
        }
        return text;
    }

    private static Text value(Object value) {
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return number(((Number) value).longValue());
        }
        if (value instanceof Text text) {
            return text;
        }
        return Text.literal(String.valueOf(value));
    }

    // ---------------------------------------------------------------- entrega

    public static void deliver(Target target, Level level, boolean ephemeral, Text body) {
        Channel channel = route(level, ephemeral, QuietMode.isQuiet(target.id()));
        switch (channel) {
            case CHAT -> target.chat(line(level, body));
            case ACTION_BAR -> target.actionBar(line(level, body));
            case NONE -> {
            }
        }
        if (level == Level.ERROR) {
            target.event(FeedbackEventPayload.Kind.FAIL);
        }
    }

    public static void send(ServerPlayerEntity player, Level level, Text body) {
        deliver(target(player), level, false, body);
    }

    public static void ok(ServerPlayerEntity player, Text body) {
        send(player, Level.OK, body);
    }

    public static void ok(ServerPlayerEntity player, String body) {
        ok(player, Text.literal(body));
    }

    /** Respuesta a una consulta: sale también en modo silencioso. */
    public static void reply(ServerPlayerEntity player, Text body) {
        send(player, Level.REPLY, body);
    }

    public static void reply(ServerPlayerEntity player, String body) {
        reply(player, Text.literal(body));
    }

    /** Informativo: no sale si el jugador activó {@code /komaedit quiet}. */
    public static void info(ServerPlayerEntity player, Text body) {
        send(player, Level.INFO, body);
    }

    public static void info(ServerPlayerEntity player, String body) {
        info(player, Text.literal(body));
    }

    public static void warn(ServerPlayerEntity player, Text body) {
        send(player, Level.WARN, body);
    }

    public static void warn(ServerPlayerEntity player, String body) {
        warn(player, Text.literal(body));
    }

    public static void error(ServerPlayerEntity player, Text body) {
        send(player, Level.ERROR, body);
    }

    public static void error(ServerPlayerEntity player, String body) {
        error(player, Text.literal(body));
    }

    /** Efímero, en la action bar: progreso, «calculando…», «en cola». No sale en modo silencioso. */
    public static void status(ServerPlayerEntity player, Text body) {
        deliver(target(player), Level.INFO, true, body);
    }

    public static void status(ServerPlayerEntity player, String body) {
        status(player, Text.literal(body));
    }

    /** Aviso efímero en la action bar que sale siempre (un clic de herramienta que no pudo hacerse). */
    public static void statusWarn(ServerPlayerEntity player, Text body) {
        deliver(target(player), Level.WARN, true, body);
    }

    /** Error de una orden: rojo, con el prefijo, y el sonido de fallo si quien la ejecuta es un jugador. */
    public static int fail(ServerCommandSource source, Text body) {
        source.sendError(line(Level.ERROR, body));
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            sound(player, FeedbackEventPayload.Kind.FAIL);
        }
        return 0;
    }

    public static int fail(ServerCommandSource source, String body) {
        return fail(source, Text.literal(body));
    }

    /** Solo el sonido (si el cliente tiene el mod y lo tiene activado). */
    public static void sound(ServerPlayerEntity player, FeedbackEventPayload.Kind kind) {
        target(player).event(kind);
    }

    public static Target target(ServerPlayerEntity player) {
        return new PlayerTarget(player);
    }

    private record PlayerTarget(ServerPlayerEntity player) implements Target {
        @Override
        public UUID id() {
            return player.getUuid();
        }

        @Override
        public void chat(Text text) {
            player.sendMessage(text, false);
        }

        @Override
        public void actionBar(Text text) {
            player.sendMessage(text, true);
        }

        @Override
        public void event(FeedbackEventPayload.Kind kind) {
            if (player.networkHandler != null && ServerPlayNetworking.canSend(player, FeedbackEventPayload.ID)) {
                ServerPlayNetworking.send(player, new FeedbackEventPayload(kind));
            }
        }
    }
}
