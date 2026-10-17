package com.komainu.moxia.brush.place;

import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Orientación de una colocación con el sistema primaria + secundaria de ezEdits. La primaria fija hacia dónde
 * apunta el «arriba» (+y) de la estructura; la secundaria sugiere su «delante» (+x): de las direcciones
 * perpendiculares a la primaria se elige la que más se le parece.
 *
 * <p>De momento las dos direcciones se ajustan a los ejes del mundo (giros de 90°), así los bloques se giran con
 * sus estados exactos y sin remuestrear. Los giros libres llegarán con el motor común de colocación.</p>
 */
public final class Alignment {
    private Alignment() {
    }

    public enum Kind {
        CONSTANT, AIM, UPWARD, SURFACE_NORMAL, PLAYER_RELATIVE, RANDOM
    }

    /** Un modo de alineación; {@code x, y, z} solo cuentan en {@link Kind#CONSTANT}. */
    public record Mode(Kind kind, double x, double y, double z) {
        public static Mode constant(double x, double y, double z) {
            return new Mode(Kind.CONSTANT, x, y, z);
        }

        public static Mode of(Kind kind) {
            return new Mode(kind, 0.0, 0.0, 0.0);
        }
    }

    /** Lo que ve el pincel en cada clic. */
    public record Context(
            double aimX, double aimY, double aimZ,
            int normalX, int normalY, int normalZ,
            double toPlayerX, double toPlayerY, double toPlayerZ,
            long seed
    ) {
    }

    /**
     * @param primary  si es la primaria (su {@code Constant} por defecto es arriba; el de la secundaria, el este)
     * @param bindAim  mirada al configurar el pincel, para {@code Constant(D:aim)}
     */
    public static Mode parse(String input, boolean primary, double[] bindAim) {
        if (input == null || input.isBlank()) {
            return primary ? Mode.constant(0, 1, 0) : Mode.constant(1, 0, 0);
        }
        StructureSpec spec = StructureSpec.parse(input);
        if (spec.is("Constant", "C", "Constante")) {
            String direction = spec.option("direction", "d", "direccion");
            if (direction == null) {
                return primary ? Mode.constant(0, 1, 0) : Mode.constant(1, 0, 0);
            }
            double[] vector = direction(direction, bindAim);
            return Mode.constant(vector[0], vector[1], vector[2]);
        }
        if (spec.is("Aim", "A", "Mirada")) {
            return Mode.of(Kind.AIM);
        }
        if (spec.is("Upward", "U", "Arriba")) {
            return Mode.of(Kind.UPWARD);
        }
        if (spec.is("SurfaceNormal", "S", "Normal", "Superficie")) {
            return Mode.of(Kind.SURFACE_NORMAL);
        }
        if (spec.is("PlayerRelative", "P", "Jugador")) {
            return Mode.of(Kind.PLAYER_RELATIVE);
        }
        if (spec.is("Random", "R", "Azar")) {
            return Mode.of(Kind.RANDOM);
        }
        throw new IllegalArgumentException("Alineación desconocida: " + input
                + " (Constant, Aim, Upward, SurfaceNormal, PlayerRelative, Random)");
    }

    /** Dirección escrita: ejes, puntos cardinales, un vector {@code (x,y,z)} o {@code aim}. */
    public static double[] direction(String input, double[] bindAim) {
        String text = input.trim().toLowerCase(Locale.ROOT);
        boolean negative = text.startsWith("-");
        if (negative || text.startsWith("+")) {
            text = text.substring(1).trim();
        }
        double[] vector = switch (text) {
            case "up", "y", "arriba" -> new double[]{0, 1, 0};
            case "down", "abajo" -> new double[]{0, -1, 0};
            case "east", "x", "este" -> new double[]{1, 0, 0};
            case "west", "oeste" -> new double[]{-1, 0, 0};
            case "south", "z", "sur" -> new double[]{0, 0, 1};
            case "north", "norte" -> new double[]{0, 0, -1};
            case "aim", "mirada" -> {
                if (bindAim == null) {
                    throw new IllegalArgumentException("Aquí no se conoce la mirada");
                }
                yield bindAim.clone();
            }
            default -> vector(text);
        };
        if (negative) {
            vector[0] = -vector[0];
            vector[1] = -vector[1];
            vector[2] = -vector[2];
        }
        if (Math.abs(vector[0]) + Math.abs(vector[1]) + Math.abs(vector[2]) < 1.0E-9) {
            throw new IllegalArgumentException("La dirección no puede ser nula: " + input);
        }
        return vector;
    }

    private static double[] vector(String text) {
        String body = text.startsWith("(") && text.endsWith(")") ? text.substring(1, text.length() - 1) : text;
        String[] parts = body.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Dirección desconocida: " + text
                    + " (up, down, north, south, east, west, aim o (x,y,z))");
        }
        try {
            return new double[]{
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim())
            };
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Vector inválido: " + text);
        }
    }

    /** Dirección concreta de un modo en un clic. {@code salt} separa la primaria de la secundaria al azar. */
    public static double[] resolve(Mode mode, Context context, long salt) {
        return switch (mode.kind()) {
            case CONSTANT -> new double[]{mode.x(), mode.y(), mode.z()};
            case AIM -> new double[]{context.aimX(), context.aimY(), context.aimZ()};
            case UPWARD -> upward(context.aimX(), context.aimY(), context.aimZ());
            case SURFACE_NORMAL -> new double[]{context.normalX(), context.normalY(), context.normalZ()};
            case PLAYER_RELATIVE -> new double[]{context.toPlayerX(), context.toPlayerY(), context.toPlayerZ()};
            case RANDOM -> {
                SplittableRandom random = new SplittableRandom(context.seed() * 0x9E3779B97F4A7C15L + salt);
                int axis = random.nextInt(6);
                double[] vector = new double[3];
                vector[axis / 2] = axis % 2 == 0 ? 1 : -1;
                yield vector;
            }
        };
    }

    /** La dirección perpendicular a la mirada que más sube. */
    static double[] upward(double aimX, double aimY, double aimZ) {
        double length = Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ);
        if (length < 1.0E-9) {
            return new double[]{0, 1, 0};
        }
        double ax = aimX / length;
        double ay = aimY / length;
        double az = aimZ / length;
        double ux = -ay * ax;
        double uy = 1.0 - ay * ay;
        double uz = -ay * az;
        if (uy < 1.0E-6) {
            // Mirando en vertical no hay «más arriba»: se toma la horizontal hacia la que se inclina.
            return new double[]{0, 0, 1};
        }
        return new double[]{ux, uy, uz};
    }

    /** Eje del mundo más parecido a la dirección (vector entero de longitud 1). */
    public static int[] snap(double[] vector) {
        int axis = 0;
        for (int index = 1; index < 3; index++) {
            if (Math.abs(vector[index]) > Math.abs(vector[axis])) {
                axis = index;
            }
        }
        int[] result = new int[3];
        result[axis] = vector[axis] >= 0.0 ? 1 : -1;
        return result;
    }

    /**
     * Base de la colocación como matriz 3×3 por filas que lleva coordenadas locales al mundo
     * ({@code mundo = M · local}): columna x = delante, y = arriba, z = x × y.
     *
     * @param perturb si las dos direcciones son colineales, toma una perpendicular cualquiera en vez de fallar
     */
    public static int[] basis(double[] primary, double[] secondary, boolean perturb) {
        int[] up = snap(primary);
        double[] projected = secondary.clone();
        int upAxis = up[0] != 0 ? 0 : up[1] != 0 ? 1 : 2;
        projected[upAxis] = 0.0;
        if (Math.abs(projected[0]) + Math.abs(projected[1]) + Math.abs(projected[2]) < 1.0E-6) {
            if (!perturb) {
                throw new IllegalArgumentException("La primaria y la secundaria apuntan en la misma línea; cambia"
                        + " una o usa -x");
            }
            projected = upAxis == 0 ? new double[]{0, 0, 1} : new double[]{1, 0, 0};
        }
        int[] forward = snap(projected);
        int[] side = cross(forward, up);
        return new int[]{
                forward[0], up[0], side[0],
                forward[1], up[1], side[1],
                forward[2], up[2], side[2]
        };
    }

    /** Gira la estructura {@code quarterTurns} cuartos de vuelta alrededor de su propio «arriba». */
    public static int[] rotateAroundUp(int[] basis, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        // Giro local alrededor de y, en el mismo sentido que //rotate de WorldEdit (horario visto desde arriba).
        int[] local = switch (turns) {
            case 0 -> new int[]{1, 0, 0, 0, 1, 0, 0, 0, 1};
            case 1 -> new int[]{0, 0, -1, 0, 1, 0, 1, 0, 0};
            case 2 -> new int[]{-1, 0, 0, 0, 1, 0, 0, 0, -1};
            default -> new int[]{0, 0, 1, 0, 1, 0, -1, 0, 0};
        };
        return multiply(basis, local);
    }

    public static int[] multiply(int[] first, int[] second) {
        int[] result = new int[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int sum = 0;
                for (int k = 0; k < 3; k++) {
                    sum += first[row * 3 + k] * second[k * 3 + column];
                }
                result[row * 3 + column] = sum;
            }
        }
        return result;
    }

    public static int applyX(int[] matrix, int x, int y, int z) {
        return matrix[0] * x + matrix[1] * y + matrix[2] * z;
    }

    public static int applyY(int[] matrix, int x, int y, int z) {
        return matrix[3] * x + matrix[4] * y + matrix[5] * z;
    }

    public static int applyZ(int[] matrix, int x, int y, int z) {
        return matrix[6] * x + matrix[7] * y + matrix[8] * z;
    }

    private static int[] cross(int[] a, int[] b) {
        return new int[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }
}
