package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.function.mask.AbstractMask;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code #near} guarda las respuestas de su máscara interior para no repetirlas en la búsqueda. WorldEdit guarda la
 * máscara de un pincel o la {@code //gmask} mientras dure: con su tope de un millón de entradas eran unos 70 MB por
 * máscara, que se quedaban con el pincel.
 */
class NearMaskMemoryTest {
    @Test
    void theNearCacheStaysSmall() {
        EzMasks.NearMask near = new EzMasks.NearMask(new AbstractMask() {
            @Override
            public boolean test(BlockVector3 vector) {
                return vector.y() < 0;
            }
        }, 0.0, 1.0);
        int most = 0;
        for (int x = 0; x < 400; x++) {
            for (int z = 0; z < 400; z++) {
                near.test(BlockVector3.at(x, 0, z));
                most = Math.max(most, near.cached());
            }
        }
        assertTrue(most <= EzMasks.NearMask.CACHE_LIMIT, "guarda " + most + " respuestas");
        assertTrue(EzMasks.NearMask.CACHE_LIMIT <= 1 << 16, "el tope es de " + EzMasks.NearMask.CACHE_LIMIT);
        assertFalse(near.test(BlockVector3.at(0, -1, 0)), "el propio bloque de la máscara no cuenta");
        assertTrue(near.test(BlockVector3.at(0, 0, 0)), "al lado de uno de la máscara");
    }
}
