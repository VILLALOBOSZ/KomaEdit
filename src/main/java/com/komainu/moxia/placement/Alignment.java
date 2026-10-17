package com.komainu.moxia.placement;

import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.tube.TubeFormula;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Una dirección de alineación de ezEdits. La primaria fija el +y de la estructura y la secundaria sugiere su +x
 * (ver {@link AlignmentFrame}). Cada modo calcula su dirección para una colocación concreta a partir del
 * {@link Context}; el resultado no tiene por qué estar normalizado.
 */
public sealed interface Alignment {
    Vec3 UP = new Vec3(0, 1, 0);
    Vec3 EAST = new Vec3(1, 0, 0);

    Vec3 direction(Context context);

    /** Necesita la normal de la superficie: obliga a leer el mundo alrededor de cada colocación. */
    default boolean needsNormal() {
        return false;
    }

    /** Solo tiene sentido a lo largo de una trayectoria (array). */
    default boolean needsPath() {
        return false;
    }

    /** Solo en pinceles: la dirección sale de un segundo clic. */
    default boolean needsViewDiff() {
        return false;
    }

    /** Lo que sabe una colocación de sí misma y de su entorno. */
    record Context(
            Vec3 position,
            Vec3 player,
            DirectionExpr.Context view,
            Vec3 normal,
            Vec3 tangent,
            Vec3 pathNormal,
            Vec3 local,
            Vec3 normalized,
            SplittableRandom random,
            Vec3 viewDiff
    ) {
        /** Contexto mínimo (tests y colocación suelta). */
        public static Context at(Vec3 position, Vec3 player, Vec3 aim, long seed) {
            return new Context(position, player, new DirectionExpr.Context(aim), null, null, null,
                    Vec3Zero.ZERO, Vec3Zero.ZERO, new SplittableRandom(seed), null);
        }

        public Context withNormal(Vec3 surfaceNormal) {
            return new Context(position, player, view, surfaceNormal, tangent, pathNormal, local, normalized, random,
                    viewDiff);
        }

        public Context withPath(Vec3 pathTangent, Vec3 orthogonal) {
            return new Context(position, player, view, normal, pathTangent, orthogonal, local, normalized, random,
                    viewDiff);
        }

        public Context withSpaces(Vec3 localPosition, Vec3 normalizedPosition) {
            return new Context(position, player, view, normal, tangent, pathNormal, localPosition, normalizedPosition,
                    random, viewDiff);
        }
    }

    final class Vec3Zero {
        static final Vec3 ZERO = new Vec3(0, 0, 0);

        private Vec3Zero() {
        }
    }

    /**
     * Lee una alineación: {@code Constant(D:…)}, {@code Random}, {@code Noise(N:…)}, {@code Aim}, {@code Upward},
     * {@code PlayerRelative}, {@code SurfaceNormal}, {@code ViewDiff}, {@code Expression(E:…,S:…)},
     * {@code Tangential}, {@code Orthogonal(A:…)} o una mezcla {@code 10%C,2%R}, con nombres largos, abreviados o
     * en español.
     *
     * @param primary cambia la dirección por defecto de {@code Constant}: +y en la primaria, +x en la secundaria
     */
    static Alignment parse(String input, boolean primary) {
        String text = ParamText.unquote(input);
        List<String> parts = ParamText.split(text, ',');
        if (parts.size() > 1 || text.contains("%")) {
            List<Weighted> entries = new ArrayList<>();
            for (String part : parts) {
                String entry = part.trim();
                int percent = entry.indexOf('%');
                if (percent < 0) {
                    throw new IllegalArgumentException("En una mezcla cada alineación lleva su peso: 10%C,2%R");
                }
                double weight = ParamText.number(entry.substring(0, percent), -1.0E6, 1.0E6, "el peso de la mezcla");
                entries.add(new Weighted(weight, single(entry.substring(percent + 1), primary)));
            }
            return new Mixed(List.copyOf(entries));
        }
        return single(text, primary);
    }

    private static Alignment single(String text, boolean primary) {
        ParamText.Call call = ParamText.call(text);
        String name = ParamText.normalize(call.name());
        return switch (name) {
            case "constant", "c", "constante", "fija" -> {
                call.requireOnly("direction", "d", "direccion");
                String direction = call.get("direction", "d", "direccion");
                yield new Constant(direction == null
                        ? DirectionExpr.constant(primary ? UP : EAST)
                        : DirectionExpr.parse(direction));
            }
            case "random", "r", "aleatoria", "azar" -> {
                call.requireOnly();
                yield new Random();
            }
            case "noise", "n", "ruido" -> {
                call.requireOnly("noise", "n", "ruido");
                String noise = call.get("noise", "n", "ruido");
                yield new Noise(NoiseSpec.parse(noise == null ? "Perlin(Freq:0.01)" : noise));
            }
            case "aim", "a", "mirada" -> {
                call.requireOnly();
                yield new Aim();
            }
            case "upward", "u", "haciaarriba" -> {
                call.requireOnly();
                yield new Upward();
            }
            case "playerrelative", "p", "jugador" -> {
                call.requireOnly();
                yield new PlayerRelative();
            }
            case "surfacenormal", "s", "normal", "superficie" -> {
                call.requireOnly();
                yield new SurfaceNormal();
            }
            case "viewdiff", "v", "dosclics" -> {
                call.requireOnly();
                yield new ViewDiff();
            }
            case "expression", "e", "ex", "expr", "expresion" -> {
                call.requireOnly("expression", "e", "expresion", "space", "s", "espacio");
                String source = call.get("expression", "e", "expresion");
                if (source == null) {
                    throw new IllegalArgumentException("Expression necesita E:, por ejemplo E(E:\"=rx=z;rz=-x\",S:N)");
                }
                String space = call.get("space", "s", "espacio");
                yield Expression.compile(source, space == null ? Space.WORLD : Space.byId(space));
            }
            case "tangential", "t", "tangente", "tangencial" -> {
                call.requireOnly();
                yield new Tangential();
            }
            case "orthogonal", "o", "ortogonal" -> {
                call.requireOnly("angle", "a", "angulo");
                String angle = call.get("angle", "a", "angulo");
                yield new Orthogonal(angle == null ? 0.0
                        : ParamText.number(angle, -3_600, 3_600, "el ángulo de Orthogonal"));
            }
            default -> throw new IllegalArgumentException("Alineación desconocida: " + call.name()
                    + " (Constant, Random, Noise, Aim, Upward, PlayerRelative, SurfaceNormal, ViewDiff, Expression,"
                    + " Tangential, Orthogonal o una mezcla 10%C,2%R)");
        };
    }

    record Constant(DirectionExpr direction) implements Alignment {
        public Vec3 direction(Context context) {
            return direction.evaluate(context.view());
        }
    }

    record Random() implements Alignment {
        public Vec3 direction(Context context) {
            double z = context.random().nextDouble() * 2.0 - 1.0;
            double angle = context.random().nextDouble() * Math.PI * 2.0;
            double radius = Math.sqrt(Math.max(0.0, 1.0 - z * z));
            return new Vec3(radius * Math.cos(angle), radius * Math.sin(angle), z);
        }
    }

    /** Tres muestras del ruido con semillas distintas, centradas en 0: celdas o manchas con su propia dirección. */
    final class Noise implements Alignment {
        private final NoiseSpec spec;
        private final NoiseSpec.NoiseField first;
        private final NoiseSpec.NoiseField second;
        private final NoiseSpec.NoiseField third;

        Noise(NoiseSpec spec) {
            this.spec = spec;
            first = spec.build(0x51A7L);
            second = spec.build(0x2B7E1L);
            third = spec.build(0x9E37L);
        }

        public Vec3 direction(Context context) {
            Vec3 position = context.position();
            return new Vec3(
                    first.sample(position.x(), position.y(), position.z()) - 0.5,
                    second.sample(position.x() + 101.3, position.y() - 57.1, position.z() + 33.7) - 0.5,
                    third.sample(position.x() - 211.9, position.y() + 87.5, position.z() - 149.2) - 0.5
            );
        }

        @Override
        public String toString() {
            return "Noise(" + spec + ")";
        }
    }

    record Aim() implements Alignment {
        public Vec3 direction(Context context) {
            return context.view().aim();
        }
    }

    /** La dirección más hacia arriba de las perpendiculares a la mirada. */
    record Upward() implements Alignment {
        public Vec3 direction(Context context) {
            Vec3 aim = context.view().aim().normalized();
            Vec3 upward = UP.subtract(aim.multiply(UP.dot(aim)));
            if (upward.length() < 1.0E-6) {
                // Mirando en vertical: lo más «arriba» en pantalla es hacia donde apunta la cabeza.
                return context.view().forward().multiply(aim.y() > 0 ? -1 : 1);
            }
            return upward;
        }
    }

    record PlayerRelative() implements Alignment {
        public Vec3 direction(Context context) {
            return context.player().subtract(context.position());
        }
    }

    record SurfaceNormal() implements Alignment {
        public Vec3 direction(Context context) {
            return context.normal() == null ? UP : context.normal();
        }

        public boolean needsNormal() {
            return true;
        }
    }

    record ViewDiff() implements Alignment {
        public Vec3 direction(Context context) {
            if (context.viewDiff() == null) {
                throw new IllegalArgumentException("ViewDiff solo funciona con un pincel: el segundo clic marca la dirección");
            }
            return context.viewDiff();
        }

        public boolean needsViewDiff() {
            return true;
        }
    }

    record Tangential() implements Alignment {
        public Vec3 direction(Context context) {
            if (context.tangent() == null) {
                throw new IllegalArgumentException("Tangential solo funciona en array: sigue la trayectoria");
            }
            return context.tangent();
        }

        public boolean needsPath() {
            return true;
        }
    }

    /** Perpendicular a la trayectoria: 0° arriba, 90° izquierda, 180° abajo y 270° derecha al empezar. */
    record Orthogonal(double angle) implements Alignment {
        public Vec3 direction(Context context) {
            if (context.tangent() == null || context.pathNormal() == null) {
                throw new IllegalArgumentException("Orthogonal solo funciona en array: sigue la trayectoria");
            }
            // Girar en negativo alrededor de la tangente deja la izquierda a +90°, como en ezEdits.
            return Mat3.rotation(context.tangent(), -angle).apply(context.pathNormal());
        }

        public boolean needsPath() {
            return true;
        }
    }

    record Weighted(double weight, Alignment alignment) {
    }

    /** Suma de direcciones normalizadas con sus pesos. */
    record Mixed(List<Weighted> entries) implements Alignment {
        public Vec3 direction(Context context) {
            Vec3 total = Vec3Zero.ZERO;
            for (Weighted entry : entries) {
                Vec3 direction = entry.alignment().direction(context);
                double length = direction.length();
                if (length > 1.0E-9) {
                    total = total.add(direction.multiply(entry.weight() / length));
                }
            }
            return total;
        }

        public boolean needsNormal() {
            return entries.stream().anyMatch(entry -> entry.alignment().needsNormal());
        }

        public boolean needsPath() {
            return entries.stream().anyMatch(entry -> entry.alignment().needsPath());
        }

        public boolean needsViewDiff() {
            return entries.stream().anyMatch(entry -> entry.alignment().needsViewDiff());
        }
    }

    /** Dominio de {@code x, y, z} en las expresiones. */
    enum Space {
        WORLD, LOCAL, NORMALIZED;

        static Space byId(String input) {
            return switch (ParamText.normalize(input)) {
                case "world", "w", "mundo" -> WORLD;
                case "local", "l" -> LOCAL;
                case "normalized", "n", "normalizado" -> NORMALIZED;
                default -> throw new IllegalArgumentException("Espacio desconocido: " + input + " (WORLD, LOCAL, NORMALIZED)");
            };
        }
    }

    /**
     * {@code rx}, {@code ry} y {@code rz} definidos con {@code =} y separados por {@code ;}, por ejemplo
     * {@code =rx=z;rz=-x}. Las componentes sin definir valen 0 y las demás asignaciones son variables auxiliares.
     * Se evalúa con el compilador de fórmulas de KomaEdit.
     */
    final class Expression implements Alignment {
        private final Space space;
        private final TubeFormula[] components;
        private final String source;

        private Expression(Space space, TubeFormula[] components, String source) {
            this.space = space;
            this.components = components;
            this.source = source;
        }

        static Expression compile(String input, Space space) {
            String text = input.trim();
            if (text.startsWith("=")) {
                text = text.substring(1).trim();
            }
            List<String> statements = new ArrayList<>();
            for (String statement : ParamText.split(text, ';')) {
                if (!statement.isBlank()) {
                    statements.add(statement.trim());
                }
            }
            String[] names = {"rx", "ry", "rz"};
            boolean[] defined = new boolean[3];
            for (String statement : statements) {
                String target = assignmentTarget(statement);
                if (target == null) {
                    throw new IllegalArgumentException("Cada parte de la expresión es una asignación, como rx=z: "
                            + statement);
                }
                for (int index = 0; index < 3; index++) {
                    defined[index] |= target.equals(names[index]);
                }
            }
            if (!defined[0] && !defined[1] && !defined[2]) {
                throw new IllegalArgumentException("La expresión no define rx, ry ni rz");
            }
            String prefix = String.join(";", statements);
            TubeFormula[] compiled = new TubeFormula[3];
            for (int index = 0; index < 3; index++) {
                compiled[index] = defined[index] ? TubeFormula.compile(prefix + ";" + names[index]) : null;
            }
            return new Expression(space, compiled, input);
        }

        private static String assignmentTarget(String statement) {
            int equals = statement.indexOf('=');
            if (equals <= 0 || equals + 1 < statement.length() && statement.charAt(equals + 1) == '=') {
                return null;
            }
            String name = statement.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            return name.matches("[a-z_][a-z0-9_]*") ? name : null;
        }

        public Vec3 direction(Context context) {
            Vec3 input = switch (space) {
                case WORLD -> context.position();
                case LOCAL -> context.local();
                case NORMALIZED -> context.normalized();
            };
            double[] result = new double[3];
            for (int index = 0; index < 3; index++) {
                if (components[index] != null) {
                    result[index] = components[index].runner(0L, 0)
                            .evaluate(input.x(), input.y(), input.z(), 0.0);
                }
            }
            return new Vec3(result[0], result[1], result[2]);
        }

        @Override
        public String toString() {
            return "Expression(" + source + ")";
        }
    }
}
