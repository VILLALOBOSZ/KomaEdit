package com.komainu.moxia.capability;

/**
 * Posición de bloque o dirección unitaria para la lógica pura de las capacidades. Es un {@code BlockPos} sin
 * Minecraft: así la extrusión y el arrastre se prueban sin mundo.
 */
public record Cell(int x, int y, int z) {
    public static final Cell ZERO = new Cell(0, 0, 0);

    public Cell add(Cell other) {
        return new Cell(x + other.x, y + other.y, z + other.z);
    }

    public Cell subtract(Cell other) {
        return new Cell(x - other.x, y - other.y, z - other.z);
    }

    public Cell scale(int factor) {
        return new Cell(x * factor, y * factor, z * factor);
    }

    /** Esta posición desplazada {@code steps} veces en la dirección {@code direction}. */
    public Cell offset(Cell direction, int steps) {
        return new Cell(x + direction.x * steps, y + direction.y * steps, z + direction.z * steps);
    }

    /** ¿Es una dirección unitaria de las seis caras? */
    public boolean isUnitAxis() {
        return Math.abs(x) + Math.abs(y) + Math.abs(z) == 1;
    }

    /** Eje de una dirección unitaria: 0 = X, 1 = Y, 2 = Z. */
    public int axis() {
        if (x != 0) {
            return 0;
        }
        return y != 0 ? 1 : 2;
    }

    /** Componente sobre un eje (0 = X, 1 = Y, 2 = Z). */
    public int component(int axis) {
        return switch (axis) {
            case 0 -> x;
            case 1 -> y;
            default -> z;
        };
    }

    /** Dirección unitaria positiva de un eje. */
    public static Cell unit(int axis) {
        return switch (axis) {
            case 0 -> new Cell(1, 0, 0);
            case 1 -> new Cell(0, 1, 0);
            default -> new Cell(0, 0, 1);
        };
    }
}
