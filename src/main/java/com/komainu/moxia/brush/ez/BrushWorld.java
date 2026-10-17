package com.komainu.moxia.brush.ez;

/**
 * Vista mínima del terreno capturado que necesitan los pinceles. La lógica de los pinceles solo ve esto, así se
 * prueba sin Minecraft; en el juego la implementa la foto de {@code SculptManager}.
 */
public interface BrushWorld {
    /** Id con espacio de nombres ({@code minecraft:stone}); aire fuera de la foto. */
    String blockId(int x, int y, int z);

    Cell cell(int x, int y, int z);

    boolean contains(int x, int y, int z);

    /** Valor entero de una propiedad del estado ({@code flower_amount}), o -1 si no la tiene. */
    default int intProperty(int x, int y, int z, String property) {
        return -1;
    }

    /** Qué clase de bloque hay: decide qué toca cada pincel. */
    enum Cell {
        AIR,
        /** Cubo entero (piedra, tablones, cristal…). */
        FULL,
        /** Losa o escalera: solo se texturizan con la bandera de piezas. */
        SHAPED,
        LIQUID,
        /** Todo lo demás: flores, vallas, antorchas, pétalos… */
        OTHER;

        public boolean solidGround() {
            return this == FULL;
        }
    }
}
