package com.komainu.moxia.preview;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastEditsTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    @AfterEach
    void forget() {
        LastEdits.clearAll();
    }

    @Test
    void theLastTypedEditIsRepeatedAsIs() {
        LastEdits.remember(PLAYER, EditRequest.command("/komaedit tree oak", false));
        LastEdits.Entry entry = LastEdits.last(PLAYER).orElseThrow();
        assertEquals("komaedit tree oak", entry.commandFor(false));
        assertEquals("komaedit tree oak --preview", entry.commandFor(true), "con -v, con vista previa");
    }

    @Test
    void anEditAskedWithPreviewIsRepeatedWithPreview() {
        LastEdits.remember(PLAYER, EditRequest.command("komaedit texture rockify stone", true));
        assertEquals("komaedit texture rockify stone --preview",
                LastEdits.last(PLAYER).orElseThrow().commandFor(false));
    }

    @Test
    void toolClicksConfirmationsAndRepeatItselfDoNotCount() {
        LastEdits.remember(PLAYER, EditRequest.command("komaedit tree oak", false));
        LastEdits.remember(PLAYER, EditRequest.TOOL);
        LastEdits.remember(PLAYER, EditRequest.CONFIRMED);
        LastEdits.remember(PLAYER, EditRequest.command("komaedit repeat", false));
        LastEdits.remember(PLAYER, EditRequest.command("ke repetir -v", true));
        assertEquals("komaedit tree oak", LastEdits.last(PLAYER).orElseThrow().command());
    }

    @Test
    void nothingRememberedIsEmpty() {
        assertTrue(LastEdits.last(UUID.randomUUID()).isEmpty());
    }
}
