package com.komainu.moxia.display;

import java.util.Locale;

/**
 * Los pasos del editor de displays: cuánto se mueve de golpe (1, 1/4 o 1/16 de bloque), cuánto gira (15° o 5°) y si
 * va libre. Mover ajusta el centro del objeto a la rejilla del paso (así queda alineado con los píxeles de los
 * bloques); girar suma pasos desde donde empezó el arrastre; la escala sube de paso en paso del movimiento.
 */
public record DisplaySteps(Move move, Angle angle) {
    public static final DisplaySteps DEFAULT = new DisplaySteps(Move.SIXTEENTH, Angle.DEG15);

    public enum Move {
        ONE("1", 1.0),
        QUARTER("1/4", 0.25),
        SIXTEENTH("1/16", 1.0 / 16.0),
        FREE("libre", 0.0);

        private final String id;
        private final double size;

        Move(String id, double size) {
            this.id = id;
            this.size = size;
        }

        public String id() {
            return id;
        }

        /** En bloques; 0 si va libre. */
        public double size() {
            return size;
        }

        public Move next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Move parse(String text) {
            String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
            return switch (value) {
                case "1", "uno", "bloque", "block" -> ONE;
                case "1/4", "0.25", "cuarto", "quarter" -> QUARTER;
                case "1/16", "0.0625", "pixel", "píxel", "dieciseisavo" -> SIXTEENTH;
                case "libre", "free", "off", "0" -> FREE;
                default -> throw new IllegalArgumentException("Paso desconocido: " + text + " (1, 1/4, 1/16 o libre)");
            };
        }
    }

    public enum Angle {
        DEG15("15", 15.0),
        DEG5("5", 5.0),
        FREE("libre", 0.0);

        private final String id;
        private final double degrees;

        Angle(String id, double degrees) {
            this.id = id;
            this.degrees = degrees;
        }

        public String id() {
            return id;
        }

        /** En grados; 0 si va libre. */
        public double degrees() {
            return degrees;
        }

        public Angle next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Angle parse(String text) {
            String value = text == null ? "" : text.strip().toLowerCase(Locale.ROOT).replace("°", "");
            return switch (value) {
                case "15" -> DEG15;
                case "5" -> DEG5;
                case "libre", "free", "off", "0" -> FREE;
                default -> throw new IllegalArgumentException("Giro desconocido: " + text + " (15, 5 o libre)");
            };
        }
    }

    public DisplaySteps {
        if (move == null || angle == null) {
            throw new IllegalArgumentException("Pasos incompletos");
        }
    }

    public DisplaySteps withMove(Move value) {
        return new DisplaySteps(value, angle);
    }

    public DisplaySteps withAngle(Angle value) {
        return new DisplaySteps(move, value);
    }

    /** {@code value} en la rejilla de {@code step} (sin rejilla si {@code step} es 0). */
    public static double snap(double value, double step) {
        if (!(step > 0.0)) {
            return value;
        }
        return Math.round(value / step) * step;
    }

    /** Coordenada ajustada a la rejilla del paso de mover; con {@code free}, tal cual. */
    public double snapPosition(double value, boolean free) {
        return free ? value : snap(value, move.size());
    }

    /** Ángulo en radianes ajustado al paso de giro; con {@code free}, tal cual. */
    public double snapAngle(double radians, boolean free) {
        return free || angle == Angle.FREE ? radians : Math.toRadians(snap(Math.toDegrees(radians), angle.degrees()));
    }

    /**
     * Una escala ajustada al paso de mover (nunca por debajo de un paso); con {@code free} o el paso libre, tal cual
     * pero nunca por debajo de {@code minimum}.
     */
    public double snapScale(double value, boolean free, double minimum) {
        double step = move.size();
        if (free || !(step > 0.0)) {
            return Math.max(minimum, value);
        }
        return Math.max(Math.max(step, minimum), snap(value, step));
    }
}
