package com.komainu.moxia.roof;

import com.komainu.moxia.edit.PackedBlockPlan;

/**
 * Lo que el tejado necesita saber del mundo, leído de una foto en el hilo del servidor: dónde hay aire (el tejado
 * no pisa bloques salvo con {@code -r}), dónde hay muros (para sacar la planta y su material) y el estado de cada
 * posición (para que la construcción respete lo que se cambie a mano mientras tanto). Se usa desde el worker.
 */
public interface RoofWorld {
    /** Mundo vacío: todo es aire y nada se comprueba al construir. */
    RoofWorld AIR = new RoofWorld() {
        @Override
        public boolean air(int x, int y, int z) {
            return true;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return false;
        }

        @Override
        public String blockId(int x, int y, int z) {
            return null;
        }

        @Override
        public int stateId(int x, int y, int z) {
            return PackedBlockPlan.NO_EXPECTED_STATE;
        }
    };

    boolean air(int x, int y, int z);

    /** Bloque macizo (cuenta como muro al detectar la planta). */
    boolean solid(int x, int y, int z);

    /** Identificador del bloque ({@code minecraft:stone_bricks}), o {@code null} si es aire o no se leyó. */
    String blockId(int x, int y, int z);

    /** Estado leído en la foto, o {@link PackedBlockPlan#NO_EXPECTED_STATE} si la posición no se leyó. */
    int stateId(int x, int y, int z);
}
