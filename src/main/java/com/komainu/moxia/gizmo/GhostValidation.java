package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;

import java.util.Optional;

/**
 * Lo que el servidor comprueba de una transformación antes de colocar: que el objeto quepa en la altura del mundo,
 * dentro del borde, cerca del jugador y en chunks cargados, y que admita esa orientación. Lo de cada posición
 * (dónde puede construir) lo mira {@link GhostPlacement.Planner} al recorrerlo. Lógica pura: el mundo llega por
 * {@link World}.
 */
public final class GhostValidation {
    /** Distancia máxima del jugador a la caja del objeto, en bloques. */
    public static final double MAX_DISTANCE = 512.0;

    private GhostValidation() {
    }

    /** Lo que se pregunta al mundo. */
    public interface World {
        int bottomY();

        /** La primera altura que ya no es del mundo. */
        int topY();

        boolean insideBorder(int x, int z);

        boolean chunkLoaded(int chunkX, int chunkZ);
    }

    /**
     * @param bounds   caja del mundo con la transformación pedida
     * @param admitted si los estados del objeto admiten esa orientación
     * @return el motivo para no colocar, o vacío si se puede
     */
    public static Optional<String> check(BlockBounds bounds, double playerX, double playerY, double playerZ,
                                         boolean admitted, World world) {
        if (!admitted) {
            return Optional.of("ese giro no lo admite el objeto (tiene escaleras, losas, puertas… que no se pueden "
                    + "tumbar)");
        }
        if (bounds.minimumY() < world.bottomY() || bounds.maximumY() >= world.topY()) {
            return Optional.of("se sale de la altura del mundo (de " + bounds.minimumY() + " a " + bounds.maximumY()
                    + "; el mundo va de " + world.bottomY() + " a " + (world.topY() - 1) + ")");
        }
        if (!world.insideBorder(bounds.minimumX(), bounds.minimumZ())
                || !world.insideBorder(bounds.maximumX(), bounds.maximumZ())) {
            return Optional.of("se sale del borde del mundo");
        }
        double distance = distance(bounds, playerX, playerY, playerZ);
        if (distance > MAX_DISTANCE) {
            return Optional.of("está demasiado lejos (" + Math.round(distance) + " bloques; como mucho "
                    + Math.round(MAX_DISTANCE) + ")");
        }
        for (int chunkX = bounds.minimumX() >> 4; chunkX <= bounds.maximumX() >> 4; chunkX++) {
            for (int chunkZ = bounds.minimumZ() >> 4; chunkZ <= bounds.maximumZ() >> 4; chunkZ++) {
                if (!world.chunkLoaded(chunkX, chunkZ)) {
                    return Optional.of("parte de la zona no está cargada (acércate)");
                }
            }
        }
        return Optional.empty();
    }

    /** Distancia del punto a la caja (0 dentro). */
    public static double distance(BlockBounds bounds, double x, double y, double z) {
        double dx = Math.max(0.0, Math.max(bounds.minimumX() - x, x - (bounds.maximumX() + 1)));
        double dy = Math.max(0.0, Math.max(bounds.minimumY() - y, y - (bounds.maximumY() + 1)));
        double dz = Math.max(0.0, Math.max(bounds.minimumZ() - z, z - (bounds.maximumZ() + 1)));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
