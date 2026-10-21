package com.komainu.moxia.command;

import com.komainu.moxia.capability.CapabilitySettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Los rangos de los argumentos numéricos de Brigadier dejan escribir lo que la orden admite de verdad. */
class CommandArgumentRangesTest {
    private final OpenCommandTree tree = new OpenCommandTree();

    @Test
    void extrudeTakesAsManyBlocksAsItsOption() {
        // De serie son 256 por capa y la opción admite hasta 65 536: el atajo no puede quedarse en 64.
        for (int blocks : new int[]{1, 64, 256, 512, CapabilitySettings.MAX_EXTRUDE_LIMIT}) {
            assertNull(tree.problem("komaedit capability extrude " + blocks, true), "extrude " + blocks);
            assertNull(tree.problem("komaedit capacidad extruir " + blocks, true), "extruir " + blocks);
        }
        assertNotNull(tree.problem("komaedit capability extrude 0", true), "0 bloques no es un valor");
        assertNotNull(tree.problem("komaedit capability extrude " + (CapabilitySettings.MAX_EXTRUDE_LIMIT + 1),
                true));
        // Las distancias siguen admitiendo lo de siempre (el servidor las recorta a su máximo).
        assertNull(tree.problem("komaedit capability angel 8", true));
        assertNull(tree.problem("komaedit capability reach 32", true));
    }
}
