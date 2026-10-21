package com.komainu.moxia.display;

import com.komainu.moxia.shape.Vec3;

/**
 * Dónde sale un display nuevo: contra la cara del bloque que se mira, como un bloque puesto a mano.
 *
 * <ul>
 *     <li><b>Bloque</b> y <b>objeto</b>: centrados en la celda de al lado de la cara (un bloque ocupa justo esa celda),
 *     con la entidad en su centro para que giren sobre sí mismos. El objeto mira al jugador, de 90° en 90°.</li>
 *     <li><b>Texto</b>: en una pared, pegado a ella y mirando hacia fuera, centrado en el punto mirado; encima de algo,
 *     de pie sobre ese punto y mirando al jugador; debajo, colgando. El punto se ajusta a 1/16.</li>
 * </ul>
 *
 * Lógica pura: las caras se numeran como en el fantasma (oeste, este, abajo, arriba, norte, sur).
 */
public final class DisplayPlacement {
    /** Lo que se separa un texto de la pared, para que no parpadee con ella. */
    public static final double WALL_GAP = 1.0 / 64.0;
    private static final double GRID = 1.0 / 16.0;

    private DisplayPlacement() {
    }

    /** La normal de una cara ({@code 0} oeste … {@code 5} sur). */
    public static Vec3 normal(int face) {
        return switch (face) {
            case 0 -> new Vec3(-1, 0, 0);
            case 1 -> new Vec3(1, 0, 0);
            case 2 -> new Vec3(0, -1, 0);
            case 3 -> new Vec3(0, 1, 0);
            case 4 -> new Vec3(0, 0, -1);
            case 5 -> new Vec3(0, 0, 1);
            default -> throw new IllegalArgumentException("Cara desconocida: " + face);
        };
    }

    /**
     * El giro que hace que el frente del display (su +Z, el lado que se lee de un texto) mire hacia quien tiene esa
     * guiñada, redondeado a {@code snapDegrees}.
     */
    public static Quat facingPlayer(double playerYaw, double snapDegrees) {
        double degrees = 180.0 - playerYaw;
        if (snapDegrees > 0.0) {
            degrees = Math.round(degrees / snapDegrees) * snapDegrees;
        }
        return Quat.axis(1, Math.toRadians(degrees));
    }

    /** El giro que pone el frente mirando en la dirección horizontal {@code normal}. */
    public static Quat facing(Vec3 normal) {
        return Quat.axis(1, Math.atan2(normal.x(), normal.z()));
    }

    /**
     * La pose de un display nuevo.
     *
     * @param block       el bloque mirado
     * @param face        la cara mirada
     * @param hit         el punto exacto mirado (sobre la cara)
     * @param volume      el volumen del modelo (para el texto, el estimado)
     * @param playerYaw   guiñada del jugador, en grados
     */
    public static DisplayPose place(DisplayKind kind, int blockX, int blockY, int blockZ, int face, Vec3 hit,
                                    DisplayVolume volume, double playerYaw) {
        Vec3 normal = normal(face);
        Vec3 center = volume.center();
        return switch (kind) {
            case BLOCK -> DisplayPose.centered(cell(blockX, blockY, blockZ, normal), Quat.IDENTITY, DisplayPose.ONE,
                    center);
            case ITEM -> DisplayPose.centered(cell(blockX, blockY, blockZ, normal), facingPlayer(playerYaw, 90.0),
                    DisplayPose.ONE, center);
            case TEXT -> text(face, normal, snap(hit), volume, playerYaw);
        };
    }

    private static DisplayPose text(int face, Vec3 normal, Vec3 hit, DisplayVolume volume, double playerYaw) {
        Vec3 center = volume.center();
        if (face == 3) {
            // De pie: la base del texto sobre el punto.
            return DisplayPose.centered(hit.add(new Vec3(0, WALL_GAP, 0)), facingPlayer(playerYaw, 15.0),
                    DisplayPose.ONE, new Vec3(center.x(), 0.0, 0.0));
        }
        if (face == 2) {
            // Colgando: la parte de arriba en el punto.
            double top = volume.unit().apply(new Vec3(0.5, 1.0, 0.5)).y();
            return DisplayPose.centered(hit.subtract(new Vec3(0, WALL_GAP, 0)), facingPlayer(playerYaw, 15.0),
                    DisplayPose.ONE, new Vec3(center.x(), top, 0.0));
        }
        return DisplayPose.centered(hit.add(normal.multiply(WALL_GAP)), facing(normal), DisplayPose.ONE, center);
    }

    private static Vec3 cell(int x, int y, int z, Vec3 normal) {
        return new Vec3(x + 0.5 + normal.x(), y + 0.5 + normal.y(), z + 0.5 + normal.z());
    }

    private static Vec3 snap(Vec3 point) {
        return new Vec3(DisplaySteps.snap(point.x(), GRID), DisplaySteps.snap(point.y(), GRID),
                DisplaySteps.snap(point.z(), GRID));
    }
}
