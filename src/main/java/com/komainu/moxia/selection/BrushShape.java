package com.komainu.moxia.selection;

import java.util.Locale;

/** La forma del pincel de selección y de cada paso al ampliar o reducir: esfera, cubo u octaedro. */
public enum BrushShape {
    SPHERE, CUBE, OCTAHEDRON;

    public static final int MAX_RADIUS = 16;

    /** Si el desplazamiento {@code (dx, dy, dz)} cae dentro de la forma de radio {@code radius}. */
    public boolean inside(int dx, int dy, int dz, int radius) {
        return switch (this) {
            // r·(r+1) = (r+½)² − ¼: la esfera de bloques de siempre, sin el pico suelto en cada polo.
            case SPHERE -> dx * dx + dy * dy + dz * dz <= radius * (radius + 1);
            case CUBE -> Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) <= radius;
            case OCTAHEDRON -> Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= radius;
        };
    }

    /**
     * Lo que selecciona una pasada del pincel en {@code (x, y, z)}: los bloques de la forma que cuentan (lo sólido y,
     * si se pide, lo que no lo es). El aire nunca: para eso está la caja.
     */
    public BlockSet stamp(int x, int y, int z, int radius, Voxels voxels, boolean nonSolid) {
        if (radius < 0 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("El radio del pincel va de 0 a " + MAX_RADIUS);
        }
        BlockSet blocks = new BlockSet();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int by = y + dy;
                if (!BlockSet.storable(by)) {
                    continue;
                }
                for (int dz = -radius; dz <= radius; dz++) {
                    if (inside(dx, dy, dz, radius) && voxels.counts(x + dx, by, z + dz, nonSolid)) {
                        blocks.add(x + dx, by, z + dz);
                    }
                }
            }
        }
        return blocks;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BrushShape byId(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "sphere", "esfera" -> SPHERE;
            case "cube", "cubo" -> CUBE;
            case "octahedron", "octaedro", "diamond", "rombo" -> OCTAHEDRON;
            default -> throw new IllegalArgumentException("Forma desconocida: " + id + " (sphere, cube, octahedron)");
        };
    }
}
