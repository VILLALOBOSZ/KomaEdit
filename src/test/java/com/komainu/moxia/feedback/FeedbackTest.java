package com.komainu.moxia.feedback;

import com.komainu.moxia.persistence.QuietMode;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-00000000f00d");

    private static final class Recorder implements Feedback.Target {
        private final List<String> chat = new ArrayList<>();
        private final List<String> actionBar = new ArrayList<>();
        private final List<FeedbackEventPayload.Kind> events = new ArrayList<>();

        @Override
        public UUID id() {
            return PLAYER;
        }

        @Override
        public void chat(Text text) {
            chat.add(text.getString());
        }

        @Override
        public void actionBar(Text text) {
            actionBar.add(text.getString());
        }

        @Override
        public void event(FeedbackEventPayload.Kind kind) {
            events.add(kind);
        }
    }

    @AfterEach
    void forgetQuiet() {
        QuietMode.forget(PLAYER);
    }

    @Test
    void numbersAreGroupedAndWhite() {
        MutableText text = Feedback.format("%s completada: %s bloques · //undo", "Texturizar", 1_234_567);
        assertEquals("Texturizar completada: 1 234 567 bloques · //undo", text.getString());
        Text number = text.getSiblings().get(2);
        assertEquals(Formatting.WHITE.getColorValue(), number.getStyle().getColor().getRgb());
        assertEquals("12 bloques", Feedback.count(12, "bloque", "bloques"));
        assertEquals("1 bloque", Feedback.count(1, "bloque", "bloques"));
    }

    @Test
    void everyLineStartsWithTheShortPrefix() {
        assertEquals("KE · Hecho", Feedback.line(Feedback.Level.OK, "Hecho").getString());
        assertEquals(Formatting.RED.getColorValue(),
                Feedback.line(Feedback.Level.ERROR, "Mal").getSiblings().get(1).getStyle().getColor().getRgb());
    }

    @Test
    void routingKeepsWarningsAndErrorsEvenWhenQuiet() {
        assertEquals(Feedback.Channel.CHAT, Feedback.route(Feedback.Level.ERROR, false, true));
        assertEquals(Feedback.Channel.CHAT, Feedback.route(Feedback.Level.WARN, false, true));
        assertEquals(Feedback.Channel.CHAT, Feedback.route(Feedback.Level.OK, false, true),
                "el final de cada edición sale siempre");
        assertEquals(Feedback.Channel.NONE, Feedback.route(Feedback.Level.INFO, false, true));
        assertEquals(Feedback.Channel.NONE, Feedback.route(Feedback.Level.INFO, true, true));
        assertEquals(Feedback.Channel.ACTION_BAR, Feedback.route(Feedback.Level.INFO, true, false));
        assertEquals(Feedback.Channel.ACTION_BAR, Feedback.route(Feedback.Level.WARN, true, true));
        assertEquals(Feedback.Channel.CHAT, Feedback.route(Feedback.Level.INFO, false, false));
    }

    @Test
    void quietModeSilencesInformationAndEphemeralStatus() {
        QuietMode.remember(PLAYER, true);
        Recorder recorder = new Recorder();
        Feedback.deliver(recorder, Feedback.Level.INFO, false, Text.literal("Semilla 42"));
        Feedback.deliver(recorder, Feedback.Level.INFO, true, Text.literal("Calculando…"));
        Feedback.deliver(recorder, Feedback.Level.OK, false, Text.literal("Completada"));
        Feedback.deliver(recorder, Feedback.Level.ERROR, false, Text.literal("Sin selección"));
        assertEquals(List.of("KE · Completada", "KE · Sin selección"), recorder.chat);
        assertTrue(recorder.actionBar.isEmpty());
        assertEquals(List.of(FeedbackEventPayload.Kind.FAIL), recorder.events, "el error suena");
    }

    @Test
    void withoutQuietModeEphemeralGoesToTheActionBar() {
        QuietMode.remember(PLAYER, false);
        Recorder recorder = new Recorder();
        Feedback.deliver(recorder, Feedback.Level.INFO, true, Feedback.format("%s bloques en cola", 12_000));
        Feedback.deliver(recorder, Feedback.Level.INFO, false, Text.literal("Semilla 42"));
        assertEquals(List.of("KE · 12 000 bloques en cola"), recorder.actionBar);
        assertEquals(List.of("KE · Semilla 42"), recorder.chat);
        assertTrue(recorder.events.isEmpty());
    }

    @Test
    void aFixWritesTheCorrectedCommand() {
        // El botón lleva además un texto al pasar el ratón, que necesita Minecraft arrancado: aquí, la orden.
        assertEquals("/komaedit tube helix stone 4", Feedback.slashed("komaedit tube helix stone 4"));
        assertEquals("/komaedit cancel", Feedback.slashed(" /komaedit cancel "));
        assertEquals("Faltan los puntos · añade dos con //pos1 y //pos2",
                Feedback.problem("Faltan los puntos", "añade dos con //pos1 y //pos2").getString());
    }
}
