package com.komainu.moxia.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lo que guarda un bloque cuando se escribe encima. WorldEdit 7.3.8 vacía siempre el contenedor antes de escribir
 * ({@code FabricWorld.setBlock} llama a {@code clearContainerBlockContents}), así que anegar un cofre, girar un horno o
 * retocar a mano la dirección de un barril con un estado sin datos tiraba lo que llevaban.
 */
class KeptDataTest {
    private static final String CHEST = "minecraft:chest";

    @Test
    void aStateChangeOfTheSameBlockKeepsWhatItHolds() {
        // Anegar un cofre, girar un horno: se calculó sobre lo que hay y no trae datos propios.
        assertTrue(KeptData.keeps(false, true, true, CHEST, CHEST));
    }

    @Test
    void everythingElseWritesWhatItBrings() {
        // Cambiarlo por otro bloque (reemplazar) o quitarlo: lo que guardaba se va (y queda en el historial).
        assertFalse(KeptData.keeps(false, true, true, "minecraft:barrel", CHEST));
        assertFalse(KeptData.keeps(false, true, true, "minecraft:air", CHEST));
        // Lo que trae sus propios datos (pegar, el gizmo, la reconstrucción) los pone tal cual.
        assertFalse(KeptData.keeps(true, true, true, CHEST, CHEST));
        // Sin saber qué había al calcularlo, se escribe lo del plan, como //paste.
        assertFalse(KeptData.keeps(false, false, true, CHEST, CHEST));
        // Sin entidad de bloque no hay nada que guardar.
        assertFalse(KeptData.keeps(false, true, false, "minecraft:oak_stairs", "minecraft:oak_stairs"));
    }

    /**
     * Las copias de la simetría de WorldEdit llevan los datos del original: retocar un cofre con la simetría puesta
     * copiaría su contenido al cofre reflejado. Una copia que cae sobre el mismo contenedor conserva lo suyo.
     */
    @Test
    void aSymmetryCopyOverTheSameContainerKeepsItsOwn() {
        assertTrue(KeptData.copyKeeps(true, CHEST, CHEST));
        assertFalse(KeptData.copyKeeps(true, CHEST, "minecraft:barrel"), "sobre otro bloque, la copia trae lo suyo");
        assertFalse(KeptData.copyKeeps(false, CHEST, CHEST), "sobre un cofre vacío de datos, igual");
    }
}
