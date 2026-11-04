package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /**
     * La lista de temas de {@code docs/MOD_DOCUMENTATION.md} («Ayuda y comandos») tiene cada tema de
     * {@code /komaedit help} una vez, en el mismo orden: le faltaban siete y repetía cinco.
     */
    @Test
    void theDocumentationListsEveryTopicOnce() throws IOException {
        String text = Files.readString(Path.of("docs", "MOD_DOCUMENTATION.md"), StandardCharsets.UTF_8);
        int start = text.indexOf("```text", text.indexOf("## Ayuda y comandos"));
        String block = text.substring(start, text.indexOf("```", start + 7));
        List<String> listed = new ArrayList<>();
        for (String line : block.lines().toList()) {
            String[] words = line.strip().split(" +");
            if (words.length > 2 && words[0].equals("/komaedit") && words[1].equals("help")
                    && words[2].matches("[a-z]+")) {
                listed.add(words[2]);
            }
        }
        assertEquals(HelpTopics.TOPICS.stream().map(HelpTopics.Topic::id).toList(), listed);
    }
}
