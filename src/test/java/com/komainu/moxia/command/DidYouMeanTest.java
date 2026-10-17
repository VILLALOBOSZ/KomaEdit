package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DidYouMeanTest {
    private static final List<String> NAMES = List.of("helix", "honeycomb", "chainlink", "coil", "spiked_scales",
            "scales", "trenza");

    @Test
    void findsTyposTranspositionsAndPrefixes() {
        assertEquals(Optional.of("helix"), DidYouMean.best("helx", NAMES));
        assertEquals(Optional.of("coil"), DidYouMean.best("coli", NAMES));
        assertEquals(Optional.of("honeycomb"), DidYouMean.best("honey", NAMES));
        assertEquals(Optional.of("spiked_scales"), DidYouMean.best("SPIKEDSCALES", NAMES));
        assertEquals(Optional.of("trenza"), DidYouMean.best("trénza", NAMES));
    }

    @Test
    void ignoresUnrelatedWords() {
        assertTrue(DidYouMean.best("zzz", NAMES).isEmpty());
        assertTrue(DidYouMean.best("", NAMES).isEmpty());
        assertTrue(DidYouMean.best("dragon", NAMES).isEmpty());
    }

    @Test
    void measuresDamerauDistance() {
        assertEquals(1, DidYouMean.distance("coli", "coil"));
        assertEquals(1, DidYouMean.distance("helx", "helix"));
        assertEquals(3, DidYouMean.distance("abc", "xyz"));
    }

    @Test
    void hintAppendsTheProposal() {
        assertEquals("Desconocido: helx · ¿Quisiste decir «helix»?", DidYouMean.hint("Desconocido: helx", "helx", NAMES));
        assertEquals("Desconocido: qqq", DidYouMean.hint("Desconocido: qqq", "qqq", NAMES));
    }

    @Test
    void enrichesTheParsersUnknownMessages() {
        assertEquals("Forma 3D desconocida: coli · ¿Quisiste decir «coil»?",
                CommandHints.enrich("Forma 3D desconocida: coli"));
        assertEquals("Forma 3D desconocida: coli(engine:exact) · ¿Quisiste decir «coil»?",
                CommandHints.enrich("Forma 3D desconocida: coli(engine:exact)"));
        assertTrue(CommandHints.enrich("Perfil desconocido: circel (circle, square)")
                .startsWith("Perfil desconocido: circel · ¿Quisiste decir «circle»? (circle"));
        assertEquals("Bandera desconocida: -hollw · ¿Quisiste decir «-hollow»?",
                CommandHints.enrich("Bandera desconocida: -hollw"));
        assertTrue(CommandHints.enrich("Parámetro de figura desconocido: engin").contains("«engine»"));
        assertEquals("Otra cosa: coli", CommandHints.enrich("Otra cosa: coli"));
    }
}
