package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;

import java.util.Locale;

/**
 * Direcciones escritas a mano: absolutas ({@code up}, {@code north}…, también en español) o relativas a hacia dónde
 * mira el jugador ({@code me}, {@code back}, {@code left}, {@code right}). Los ángulos son los de Minecraft: yaw 0
 * mira al sur (+Z) y crece hacia el oeste; pitch -90 mira arriba.
 */
public final class EzDirections {
    /** Por encima de este pitch, «mirar» cuenta como mirar arriba o abajo. */
    private static final double VERTICAL_PITCH = 67.5;

    private EzDirections() {
    }

    public static BlockVector3 parse(String input, float yaw, float pitch) {
        String name = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "up", "u", "arriba" -> BlockVector3.UNIT_Y;
            case "down", "d", "abajo" -> BlockVector3.UNIT_MINUS_Y;
            case "north", "n", "norte" -> BlockVector3.UNIT_MINUS_Z;
            case "south", "s", "sur" -> BlockVector3.UNIT_Z;
            case "east", "e", "este" -> BlockVector3.UNIT_X;
            case "west", "w", "oeste" -> BlockVector3.UNIT_MINUS_X;
            case "", "me", "forward", "f", "aim", "delante", "frente" -> facing(yaw, pitch);
            case "back", "b", "behind", "detras", "detrás" -> facing(yaw, pitch).multiply(-1);
            case "left", "l", "izquierda" -> rotateLeft(horizontal(yaw));
            case "right", "r", "derecha" -> rotateLeft(horizontal(yaw)).multiply(-1);
            default -> throw new IllegalArgumentException("Dirección desconocida: " + input
                    + " (up, down, north, south, east, west, me, back, left, right)");
        };
    }

    /** La dirección de las seis a la que más se parece la mirada. */
    public static BlockVector3 facing(float yaw, float pitch) {
        if (pitch <= -VERTICAL_PITCH) {
            return BlockVector3.UNIT_Y;
        }
        if (pitch >= VERTICAL_PITCH) {
            return BlockVector3.UNIT_MINUS_Y;
        }
        return horizontal(yaw);
    }

    public static BlockVector3 horizontal(float yaw) {
        int quadrant = Math.floorMod(Math.round(yaw / 90.0f), 4);
        return switch (quadrant) {
            case 0 -> BlockVector3.UNIT_Z;
            case 1 -> BlockVector3.UNIT_MINUS_X;
            case 2 -> BlockVector3.UNIT_MINUS_Z;
            default -> BlockVector3.UNIT_X;
        };
    }

    /** Vector unitario de la mirada exacta. */
    public static Vector3 look(float yaw, float pitch) {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double horizontal = Math.cos(pitchRadians);
        return Vector3.at(-Math.sin(yawRadians) * horizontal, -Math.sin(pitchRadians),
                Math.cos(yawRadians) * horizontal);
    }

    /**
     * Un vector: tres números {@code x,y,z} o una dirección con nombre. Se normaliza para que mida 1.
     */
    public static Vector3 vector(String input, float yaw, float pitch) {
        String text = input == null ? "" : input.trim();
        String[] parts = text.split("[,;:]");
        if (parts.length == 3) {
            try {
                Vector3 vector = Vector3.at(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()));
                if (vector.lengthSq() < 1.0E-12) {
                    throw new IllegalArgumentException("El vector no puede ser 0,0,0");
                }
                return vector.normalize();
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Vector inválido: " + input + " (x,y,z o una dirección)");
            }
        }
        if (text.equalsIgnoreCase("look") || text.equalsIgnoreCase("mirada")) {
            return look(yaw, pitch);
        }
        return parse(text, yaw, pitch).toVector3();
    }

    private static BlockVector3 rotateLeft(BlockVector3 forward) {
        // Mirando al sur (+Z) el este (+X) queda a la izquierda.
        return BlockVector3.at(forward.z(), 0, -forward.x());
    }
}
