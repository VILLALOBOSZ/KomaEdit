package com.komainu.moxia.color;

import com.komainu.moxia.texture.Vectors;

import java.util.Locale;

/**
 * De dónde viene la luz de {@code /komaedit shade}. Se resuelve en el hilo del servidor con lo que se sabe en ese
 * momento (hora del mundo, dónde está y adónde mira el jugador, centro de la selección) y queda fija.
 *
 * @param towards dirección unitaria hacia la luz, o {@code null} si es un foco
 * @param point   posición del foco, o {@code null} si es una luz direccional
 * @param label   cómo se describe al jugador
 */
public record ShadeLight(double[] towards, double[] point, String label) {
    /** Altura mínima y máxima del sol (grados): ni rasante ni a plomo, que aplanan el relieve. */
    static final double LOWEST_SUN = 20.0;
    static final double HIGHEST_SUN = 65.0;
    private static final double SLANT = Math.toRadians(45.0);

    /** Lo que hace falta del mundo y del jugador para resolver la luz. */
    public record Scene(double skyAngle, double[] eye, double[] look, double[] center) {
    }

    /**
     * @param input {@code sol}, {@code jugador}, {@code mirada}, {@code foco}, {@code arriba}, un rumbo ({@code n},
     *              {@code ne}, {@code e}, {@code se}, {@code s}, {@code so}/{@code sw}, {@code o}/{@code w},
     *              {@code no}/{@code nw}; la luz viene de ahí a 45°) o {@code x;y;z}, hacia dónde va la luz como en
     *              {@code /komaedit texture sunlight -l}
     */
    public static ShadeLight resolve(String input, Scene scene) {
        String text = input == null ? "sol" : input.trim().toLowerCase(Locale.ROOT);
        return switch (text) {
            case "sol", "sun" -> sun(scene.skyAngle());
            case "jugador", "player", "me", "yo" -> {
                double[] towards = {scene.eye()[0] - scene.center()[0], scene.eye()[1] - scene.center()[1],
                        scene.eye()[2] - scene.center()[2]};
                if (Math.sqrt(towards[0] * towards[0] + towards[1] * towards[1] + towards[2] * towards[2]) < 1.0E-6) {
                    throw new IllegalArgumentException("Estás en el centro de la selección: apártate para usar -l "
                            + "jugador");
                }
                yield new ShadeLight(unit(towards), null, "desde donde estás");
            }
            case "mirada", "look", "vista" -> new ShadeLight(unit(new double[]{-scene.look()[0], -scene.look()[1],
                    -scene.look()[2]}), null, "hacia donde miras");
            case "foco", "spot", "point" -> new ShadeLight(null, scene.eye().clone(), "foco en tu posición");
            case "arriba", "up", "cenit", "zenith" -> new ShadeLight(new double[]{0, 1, 0}, null, "desde arriba");
            default -> {
                double[] compass = compass(text);
                if (compass != null) {
                    yield new ShadeLight(compass, null, "desde el " + text.toUpperCase(Locale.ROOT) + " a 45°");
                }
                double[] travel = Vectors.direction(text);
                yield new ShadeLight(new double[]{-travel[0], -travel[1], -travel[2]}, null, "hacia " + text);
            }
        };
    }

    /**
     * Sol (o luna, de noche) según el ángulo del cielo de Minecraft: 0 es mediodía, 0,25 la puesta por el oeste
     * (x negativa) y 0,75 la salida por el este. La altura se limita a {@link #LOWEST_SUN}–{@link #HIGHEST_SUN}.
     */
    public static ShadeLight sun(double skyAngle) {
        double angle = skyAngle * 2.0 * Math.PI;
        double x = -Math.sin(angle);
        double y = Math.cos(angle);
        String label = "sol";
        if (y < 0.0) {
            x = -x;
            y = -y;
            label = "luna";
        }
        double side = Math.abs(x) < 1.0E-6 ? -1.0 : Math.signum(x);
        double elevation = Math.toDegrees(Math.asin(Math.clamp(y, -1.0, 1.0)));
        double clamped = Math.toRadians(Math.clamp(elevation, LOWEST_SUN, HIGHEST_SUN));
        double[] towards = {side * Math.cos(clamped), Math.sin(clamped), 0.0};
        return new ShadeLight(towards, null, label + (side < 0 ? " por el oeste" : " por el este") + " a "
                + Math.round(Math.toDegrees(clamped)) + "°");
    }

    private static double[] compass(String text) {
        double[] horizontal = switch (text) {
            case "n", "norte", "north" -> new double[]{0, -1};
            case "s", "sur", "south" -> new double[]{0, 1};
            case "e", "este", "east" -> new double[]{1, 0};
            case "o", "w", "oeste", "west" -> new double[]{-1, 0};
            case "ne", "noreste", "northeast" -> new double[]{1, -1};
            case "no", "nw", "noroeste", "northwest" -> new double[]{-1, -1};
            case "se", "sureste", "southeast" -> new double[]{1, 1};
            case "so", "sw", "suroeste", "southwest" -> new double[]{-1, 1};
            default -> null;
        };
        if (horizontal == null) {
            return null;
        }
        double length = Math.hypot(horizontal[0], horizontal[1]);
        return new double[]{horizontal[0] / length * Math.cos(SLANT), Math.sin(SLANT),
                horizontal[1] / length * Math.cos(SLANT)};
    }

    private static double[] unit(double[] vector) {
        double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        if (length < 1.0E-9) {
            throw new IllegalArgumentException("La dirección de la luz no puede ser nula");
        }
        return new double[]{vector[0] / length, vector[1] / length, vector[2] / length};
    }
}
