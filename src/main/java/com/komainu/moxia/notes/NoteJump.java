package com.komainu.moxia.notes;

import com.komainu.moxia.views.ViewTeleport;

/**
 * Adónde lleva «ir a la nota»: a unos pasos del rótulo y mirándolo; delante de la pared si la nota está en una, y si
 * no, del lado por el que llega el jugador (si está en otra dimensión o justo encima, desde el sur). El servidor lo
 * pasa después por {@link ViewTeleport#check}.
 */
public final class NoteJump {
    /** Bloques de separación en horizontal. */
    public static final double BACK = 4.0;
    /** Altura de los ojos sobre los pies. */
    private static final double EYES = 1.62;

    private NoteJump() {
    }

    public static ViewTeleport.Target target(WorldNote note, String dimension, double x, double z) {
        double labelX = note.labelX();
        double labelY = note.labelY();
        double labelZ = note.labelZ();
        double dx = 0.0;
        double dz = 1.0;
        if (note.onWall()) {
            // En una pared, siempre delante de ella: desde detrás no se vería.
            int[] normal = note.normal();
            dx = normal[0];
            dz = normal[2];
        } else if (note.dimension().equals(dimension)) {
            double ax = x - labelX;
            double az = z - labelZ;
            double length = Math.hypot(ax, az);
            if (length > 0.5) {
                dx = ax / length;
                dz = az / length;
            }
        }
        double feetX = labelX + dx * BACK;
        double feetZ = labelZ + dz * BACK;
        double feetY = labelY - EYES + 0.4;
        // Mirar al rótulo: guiñada de Minecraft (0 = sur, 90 = oeste) y cabeceo positivo hacia abajo.
        double lookX = labelX - feetX;
        double lookZ = labelZ - feetZ;
        double lookY = labelY - (feetY + EYES);
        float yaw = (float) Math.toDegrees(Math.atan2(-lookX, lookZ));
        float pitch = (float) -Math.toDegrees(Math.atan2(lookY, Math.hypot(lookX, lookZ)));
        return new ViewTeleport.Target(note.dimension(), feetX, feetY, feetZ, yaw, pitch);
    }
}
