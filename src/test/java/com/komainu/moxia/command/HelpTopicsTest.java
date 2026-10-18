package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpTopicsTest {
    @Test
    void clickFillsTheCommandUpToItsFirstArgument() {
        assertEquals("/komaedit path insert ", HelpTopics.suggestionOf("/komaedit path insert <n> [here]"));
        assertEquals("/komaedit path close ", HelpTopics.suggestionOf("/komaedit path close [on|off]"));
        assertEquals("/komaedit path reverse", HelpTopics.suggestionOf("/komaedit path reverse"));
        assertEquals("/komaedit tweak ", HelpTopics.suggestionOf("/komaedit tweak deinfest|persist"));
    }

    @Test
    void findsTopicsByIdAliasOrTypo() {
        assertEquals("path", HelpTopics.find("ruta").orElseThrow().id());
        assertEquals("generate", HelpTopics.find("Catenaria").orElseThrow().id());
        assertEquals("symmetry", HelpTopics.find("Simetría").orElseThrow().id());
        assertEquals(Optional.of("tubes"), HelpTopics.closest("tbos"));
        assertTrue(HelpTopics.find("nada").isEmpty());
    }

    @Test
    void everyEntryIsAKomaEditCommandAndNamesAreUnique() {
        Set<String> names = new HashSet<>();
        for (String name : HelpTopics.names()) {
            assertTrue(names.add(DidYouMean.fold(name)), "nombre repetido: " + name);
        }
        HelpTopics.TOPICS.forEach(topic -> topic.entries().forEach(entry -> {
            assertTrue(entry.syntax().startsWith("/komaedit "), entry.syntax());
            assertTrue(!entry.description().isBlank(), entry.syntax());
        }));
    }
}
