package com.komainu.moxia.command;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHintsFixTest {
    @Test
    void anUnknownShapeGetsTheCorrectedCommand() {
        Optional<CommandHints.Suggestion> suggestion = CommandHints.suggestion("Forma 3D desconocida: helx");
        assertEquals("helix", suggestion.orElseThrow().found());
        assertEquals(Optional.of("komaedit tube helix stone 4"),
                CommandHints.corrected("komaedit tube helx stone 4", suggestion.orElseThrow()));
    }

    @Test
    void anExpressionKeepsItsParameters() {
        CommandHints.Suggestion suggestion = CommandHints.suggestion(
                "Forma 3D desconocida: helx(engine:exact)").orElseThrow();
        assertEquals("helx", suggestion.typed());
        assertEquals(Optional.of("komaedit tube helix(engine:exact) stone 4"),
                CommandHints.corrected("komaedit tube helx(engine:exact) stone 4", suggestion));
    }

    @Test
    void onlyAWholeWordIsReplaced() {
        CommandHints.Suggestion suggestion = new CommandHints.Suggestion("st", "stone");
        assertEquals(Optional.of("komaedit build stone st2"),
                CommandHints.corrected("komaedit build st st2", suggestion));
        assertTrue(CommandHints.corrected("komaedit build minecraft:st", suggestion).isEmpty(),
                "no toca un id con espacio de nombres");
        assertTrue(CommandHints.corrected(null, suggestion).isEmpty());
    }

    @Test
    void messagesWithoutAProposalHaveNoFix() {
        assertTrue(CommandHints.suggestion("Mira un bloque").isEmpty());
        assertTrue(CommandHints.suggestion(null).isEmpty());
    }
}
