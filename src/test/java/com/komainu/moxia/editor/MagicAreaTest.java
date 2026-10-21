package com.komainu.moxia.editor;

import com.komainu.moxia.edit.BlockBounds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La selección mágica llega del cliente con el bloque pulsado: un cliente modificado puede mandar uno a millones de
 * bloques, y leerlo cargaría (o generaría) su chunk en el hilo del servidor.
 */
class MagicAreaTest {
    private static final int AIR = 0;
    private static final int STONE = 1;
    private static final int DIRT = 2;

    /** Un suelo de piedra de 5×5 en y = 64 sobre tierra, todo en el chunk (0, 0); lo demás, sin cargar. */
    private static final class Floor implements MagicArea.World {
        final List<String> unloadedReads = new ArrayList<>();

        @Override
        public boolean readable(int x, int y, int z) {
            return x >> 4 == 0 && z >> 4 == 0 && y >= -64 && y < 320;
        }

        @Override
        public int state(int x, int y, int z) {
            if (!readable(x, y, z)) {
                unloadedReads.add(x + "," + y + "," + z);
                return STONE;
            }
            if (y == 64 && x >= 0 && x < 5 && z >= 0 && z < 5) {
                return STONE;
            }
            return y < 64 ? DIRT : AIR;
        }

        @Override
        public boolean isAir(int state) {
            return state == AIR;
        }
    }

    @Test
    void aClickInAnUnloadedChunkIsNeverRead() {
        Floor world = new Floor();
        assertThrows(IllegalArgumentException.class, () -> MagicArea.flood(9_000_000, 64, 9_000_000, world));
        assertEquals(List.of(), world.unloadedReads, "no se lee (ni se carga) un chunk sin cargar");
    }

    @Test
    void theConnectedBlocksOfTheSameKindAreSelected() {
        MagicArea.Result area = MagicArea.flood(2, 64, 2, new Floor());
        assertEquals(25, area.blocks());
        assertEquals(new BlockBounds(0, 64, 0, 4, 64, 4), area.bounds());
    }

    @Test
    void airIsNotSelectedAndNothingOutsideLoadedChunksIsRead() {
        Floor world = new Floor();
        assertThrows(IllegalArgumentException.class, () -> MagicArea.flood(2, 70, 2, world));
        MagicArea.Result dirt = MagicArea.flood(15, 63, 15, world);
        assertTrue(dirt.blocks() <= MagicArea.LIMIT);
        assertTrue(dirt.bounds().maximumX() <= 15 && dirt.bounds().maximumZ() <= 15, "se para en el borde cargado");
        assertEquals(List.of(), world.unloadedReads);
    }
}
