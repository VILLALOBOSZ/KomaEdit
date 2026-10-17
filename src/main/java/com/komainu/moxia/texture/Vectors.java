package com.komainu.moxia.texture;

import java.util.Locale;

/** Direcciones y vectores escritos por el jugador ({@code down}, {@code north}, {@code 1;-2;0}…). */
public final class Vectors {
    private Vectors() {
    }

    /** Vector unitario de una dirección con nombre o de tres números separados por {@code ;} o {@code ,}. */
    public static double[] direction(String input) {
        double[] vector = switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "up", "u", "arriba" -> new double[]{0, 1, 0};
            case "down", "d", "abajo" -> new double[]{0, -1, 0};
            case "north", "n", "norte" -> new double[]{0, 0, -1};
            case "south", "s", "sur" -> new double[]{0, 0, 1};
            case "east", "e", "este" -> new double[]{1, 0, 0};
            case "west", "w", "oeste" -> new double[]{-1, 0, 0};
            default -> vector(input);
        };
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length < 1.0E-9) {
            throw new IllegalArgumentException("La dirección no puede ser nula: " + input);
        }
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }

    /** Tres números: {@code x;y;z} o {@code x,y,z}. */
    public static double[] vector(String input) {
        String[] parts = input.trim().split("[;,]");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Se esperaba una dirección (up, down, north…) o x;y;z, no «"
                    + input + "»");
        }
        double[] vector = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            try {
                vector[axis] = Double.parseDouble(parts[axis].trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("«" + parts[axis].trim() + "» no es un número", exception);
            }
            if (!Double.isFinite(vector[axis])) {
                throw new IllegalArgumentException("«" + parts[axis].trim() + "» no es un número válido");
            }
        }
        return vector;
    }

    static double dot(double[] first, double[] second) {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }

    /** Dos vectores unitarios perpendiculares a {@code normal} y entre sí. */
    static double[][] tangents(double[] normal) {
        double[] helper = Math.abs(normal[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        double[] first = cross(normal, helper);
        normalize(first);
        double[] second = cross(normal, first);
        normalize(second);
        return new double[][]{first, second};
    }

    static double[] cross(double[] first, double[] second) {
        return new double[]{
                first[1] * second[2] - first[2] * second[1],
                first[2] * second[0] - first[0] * second[2],
                first[0] * second[1] - first[1] * second[0]
        };
    }

    static double normalize(double[] vector) {
        double length = Math.sqrt(dot(vector, vector));
        if (length > 1.0E-12) {
            vector[0] /= length;
            vector[1] /= length;
            vector[2] /= length;
        }
        return length;
    }

    /** {@code count} direcciones repartidas por igual en la esfera (espiral de Fibonacci). */
    static double[][] sphere(int count) {
        double[][] directions = new double[count][];
        double golden = Math.PI * (3.0 - Math.sqrt(5.0));
        for (int index = 0; index < count; index++) {
            double y = 1.0 - (index + 0.5) * 2.0 / count;
            double radius = Math.sqrt(1.0 - y * y);
            double angle = golden * index;
            directions[index] = new double[]{Math.cos(angle) * radius, y, Math.sin(angle) * radius};
        }
        return directions;
    }
}
