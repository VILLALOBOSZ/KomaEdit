package com.komainu.moxia.placement;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code Schematic(N:…)} lleva una expresión regular escrita por el jugador que se prueba, en el hilo del servidor,
 * contra cada archivo de la carpeta de WorldEdit. Una con retroceso catastrófico tardaba minutos con un nombre normal.
 */
// Lo que se cuenta son letras leídas, no tiempo: el resultado no depende de la máquina.
class BoundedMatchTest {
    private static final String NAME = "medieval_house_large_v2_final_x";

    @Test
    void catastrophicBacktrackingIsCutInsteadOfHangingTheServer() {
        // Con Java 21 aún se atascan (miles de millones de vueltas con un nombre de 31 letras).
        for (String evil : new String[]{".*.*.*.*.*.*.*.*.*.*.*.*!", "((\\w+)+)+!", "(.*){1,32000}[!]"}) {
            Pattern pattern = Pattern.compile(evil, Pattern.CASE_INSENSITIVE);
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> BoundedMatch.matches(pattern, NAME), evil);
            assertTrue(refused.getMessage().contains("cuesta demasiado"), refused.getMessage());
        }
    }

    @Test
    void everydayExpressionsStillWork() {
        assertTrue(BoundedMatch.matches(Pattern.compile("medieval_.*", Pattern.CASE_INSENSITIVE), NAME));
        assertTrue(BoundedMatch.matches(Pattern.compile("MEDIEVAL_HOUSE_(large|small)_v[0-9]+_final_x",
                Pattern.CASE_INSENSITIVE), NAME));
        assertFalse(BoundedMatch.matches(Pattern.compile("roble_[0-9]+"), NAME));
        assertTrue(BoundedMatch.matches(Pattern.compile("roble_[0-9]+"), "roble_12"));
    }
}
