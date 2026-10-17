package com.komainu.moxia.brush.place;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Lo que coloca el pincel de colocación. {@link #prepare} se llama en el hilo del servidor en cada clic (lee el
 * portapapeles, un esquemático o calcula una figura) y devuelve una plantilla inmutable para el worker.
 *
 * <p>Punto de extensión: las estructuras de los comandos de colocación ({@code Schematic}, {@code Shape},
 * {@code Expression}…) se registran en {@link StructureSources} y el pincel las usa sin más cambios.</p>
 */
public interface StructureSource {
    /** Nombre para mensajes. */
    String describe();

    /**
     * @param seed distinta en cada clic, para las estructuras con azar
     */
    PlacementTemplate prepare(ServerPlayerEntity player, long seed);

    /** Giro propio de la estructura alrededor de su «arriba», en cuartos de vuelta. */
    default int quarterTurns() {
        return 0;
    }
}
