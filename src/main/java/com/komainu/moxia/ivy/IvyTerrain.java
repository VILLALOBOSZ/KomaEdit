package com.komainu.moxia.ivy;

/**
 * Lo que la hiedra necesita saber del mundo, sin Minecraft: qué celdas puede ocupar y qué caras sirven de apoyo.
 * Fuera de la zona conocida todo debe responder {@code false}.
 */
public interface IvyTerrain {
    /** Celda vacía (aire) donde se puede colocar un bloque de la hiedra. */
    boolean free(int x, int y, int z);

    /**
     * El bloque de {@code (x, y, z)} tiene la cara {@code face} entera y sólida (índices de {@link IvyGrower}),
     * así que una enredadera puede agarrarse a ella desde la celda de al lado.
     */
    boolean sturdy(int x, int y, int z, int face);

    /** Encima del bloque de {@code (x, y, z)} se puede tender una alfombra aunque su cara de arriba no sea entera. */
    boolean rests(int x, int y, int z);
}
