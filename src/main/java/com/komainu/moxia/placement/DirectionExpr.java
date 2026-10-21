package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

import java.util.Locale;

/**
 * Dirección escrita a mano: ejes ({@code x}, {@code -z}), puntos cardinales ({@code east}, {@code norte}), vectores
 * ({@code (1,2,0)}), direcciones relativas al jugador ({@code aim}, {@code forward}, {@code left}…) y sumas con
 * factores, como {@code east-z+(0,0.5,0)} o {@code 2*up+aim}. Un {@code =} final se ignora.
 *
 * <p>Se compila una vez y se evalúa con el {@link Context} del jugador, porque {@code aim} o {@code left} dependen
 * de hacia dónde mira cuando lanza el comando.</p>
 */
public final class DirectionExpr {
    private final Node root;
    private final String source;

    private DirectionExpr(Node root, String source) {
        this.root = root;
        this.source = source;
    }

    public static DirectionExpr parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta la dirección");
        }
        String text = input.trim();
        if (text.endsWith("=")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        Parser parser = new Parser(text);
        Node node = parser.sum();
        parser.skip();
        if (parser.position < text.length()) {
            throw new IllegalArgumentException("Sobra «" + text.substring(parser.position) + "» en la dirección " + text);
        }
        if (node.scalar()) {
            throw new IllegalArgumentException("«" + text + "» es un número, no una dirección");
        }
        return new DirectionExpr(node, text);
    }

    public static DirectionExpr constant(Vec3 vector) {
        return new DirectionExpr(new Constant(vector), vector.x() + "," + vector.y() + "," + vector.z());
    }

    public Vec3 evaluate(Context context) {
        return root.vector(context);
    }

    /** Si la dirección no depende del jugador (sirve para validar sin contexto). */
    public boolean playerIndependent() {
        return root.independent();
    }

    /**
     * La dirección ya calculada con la mirada de {@code view}: lo que dependía del jugador ({@code aim},
     * {@code left}…) se queda fijo. Conserva el texto original para los mensajes.
     */
    public DirectionExpr bind(Context view) {
        return playerIndependent() ? this : new DirectionExpr(new Constant(root.vector(view)), source);
    }

    @Override
    public String toString() {
        return source;
    }

    /** Dónde mira el jugador: {@code aim} exacto y su versión horizontal para delante, atrás, izquierda y derecha. */
    public record Context(Vec3 aim) {
        public static final Context NONE = new Context(new Vec3(1, 0, 0));

        public Vec3 forward() {
            Vec3 horizontal = new Vec3(aim.x(), 0, aim.z());
            if (horizontal.length() < 1.0E-6) {
                return new Vec3(1, 0, 0);
            }
            return horizontal.normalized();
        }

        public Vec3 left() {
            return new Vec3(0, 1, 0).cross(forward());
        }
    }

    private interface Node {
        boolean scalar();

        boolean independent();

        default double number(Context context) {
            throw new IllegalStateException();
        }

        default Vec3 vector(Context context) {
            throw new IllegalStateException();
        }
    }

    private record Number(double value) implements Node {
        public boolean scalar() {
            return true;
        }

        public boolean independent() {
            return true;
        }

        public double number(Context context) {
            return value;
        }
    }

    private record Constant(Vec3 value) implements Node {
        public boolean scalar() {
            return false;
        }

        public boolean independent() {
            return true;
        }

        public Vec3 vector(Context context) {
            return value;
        }
    }

    private enum Relative implements Node {
        AIM, FORWARD, BACK, LEFT, RIGHT;

        public boolean scalar() {
            return false;
        }

        public boolean independent() {
            return false;
        }

        public Vec3 vector(Context context) {
            return switch (this) {
                case AIM -> context.aim().normalized();
                case FORWARD -> context.forward();
                case BACK -> context.forward().multiply(-1);
                case LEFT -> context.left();
                case RIGHT -> context.left().multiply(-1);
            };
        }
    }

    private record Vector(Node x, Node y, Node z) implements Node {
        public boolean scalar() {
            return false;
        }

        public boolean independent() {
            return true;
        }

        public Vec3 vector(Context context) {
            return new Vec3(x.number(context), y.number(context), z.number(context));
        }
    }

    private record Sum(Node left, Node right, double sign) implements Node {
        public boolean scalar() {
            return left.scalar();
        }

        public boolean independent() {
            return left.independent() && right.independent();
        }

        public double number(Context context) {
            return left.number(context) + sign * right.number(context);
        }

        public Vec3 vector(Context context) {
            return left.vector(context).add(right.vector(context).multiply(sign));
        }
    }

    private record Product(Node left, Node right) implements Node {
        public boolean scalar() {
            return left.scalar() && right.scalar();
        }

        public boolean independent() {
            return left.independent() && right.independent();
        }

        public double number(Context context) {
            return left.number(context) * right.number(context);
        }

        public Vec3 vector(Context context) {
            if (left.scalar()) {
                return right.vector(context).multiply(left.number(context));
            }
            return left.vector(context).multiply(right.number(context));
        }
    }

    private record Negate(Node inner) implements Node {
        public boolean scalar() {
            return inner.scalar();
        }

        public boolean independent() {
            return inner.independent();
        }

        public double number(Context context) {
            return -inner.number(context);
        }

        public Vec3 vector(Context context) {
            return inner.vector(context).multiply(-1);
        }
    }

    private static final class Parser {
        private final String text;
        private int position;

        Parser(String text) {
            this.text = text;
        }

        Node sum() {
            Node node = product();
            while (true) {
                skip();
                if (peek() == '+' || peek() == '-') {
                    double sign = text.charAt(position++) == '+' ? 1 : -1;
                    Node right = product();
                    if (node.scalar() != right.scalar()) {
                        throw new IllegalArgumentException("No se puede sumar un número y una dirección en " + text);
                    }
                    node = new Sum(node, right, sign);
                } else {
                    return node;
                }
            }
        }

        Node product() {
            Node node = unary();
            while (true) {
                skip();
                if (peek() == '*') {
                    position++;
                    Node right = unary();
                    if (!node.scalar() && !right.scalar()) {
                        throw new IllegalArgumentException("No se pueden multiplicar dos direcciones en " + text);
                    }
                    node = new Product(node, right);
                } else if (node.scalar() && (Character.isLetter(peek()) || peek() == '(')) {
                    // 2up, 0.5(1,0,0): un número pegado multiplica.
                    node = new Product(node, unary());
                } else {
                    return node;
                }
            }
        }

        Node unary() {
            skip();
            if (peek() == '-') {
                position++;
                return new Negate(unary());
            }
            if (peek() == '+') {
                position++;
                return unary();
            }
            return primary();
        }

        Node primary() {
            skip();
            char character = peek();
            if (character == '(') {
                position++;
                Node first = sum();
                skip();
                if (peek() == ',') {
                    position++;
                    Node second = sum();
                    expect(',');
                    Node third = sum();
                    expect(')');
                    if (!first.scalar() || !second.scalar() || !third.scalar()) {
                        throw new IllegalArgumentException("Las componentes de un vector son números: " + text);
                    }
                    return new Vector(first, second, third);
                }
                expect(')');
                return first;
            }
            if (Character.isDigit(character) || character == '.') {
                int start = position;
                while (position < text.length()
                        && (Character.isDigit(text.charAt(position)) || text.charAt(position) == '.')) {
                    position++;
                }
                try {
                    return new Number(Double.parseDouble(text.substring(start, position)));
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Número inválido en la dirección " + text);
                }
            }
            if (Character.isLetter(character)) {
                int start = position;
                while (position < text.length() && Character.isLetter(text.charAt(position))) {
                    position++;
                }
                return named(text.substring(start, position));
            }
            throw new IllegalArgumentException(position >= text.length()
                    ? "La dirección " + text + " está incompleta"
                    : "Carácter inesperado «" + character + "» en la dirección " + text);
        }

        private Node named(String word) {
            return switch (word.toLowerCase(Locale.ROOT)) {
                case "x", "east", "e", "este" -> new Constant(new Vec3(1, 0, 0));
                case "west", "w", "oeste" -> new Constant(new Vec3(-1, 0, 0));
                case "y", "up", "u", "arriba" -> new Constant(new Vec3(0, 1, 0));
                case "down", "d", "abajo" -> new Constant(new Vec3(0, -1, 0));
                case "z", "south", "s", "sur" -> new Constant(new Vec3(0, 0, 1));
                case "north", "n", "norte" -> new Constant(new Vec3(0, 0, -1));
                case "aim", "look", "mirada" -> Relative.AIM;
                case "forward", "me", "f", "delante", "adelante" -> Relative.FORWARD;
                case "back", "backward", "backwards", "b", "detras", "atras" -> Relative.BACK;
                case "left", "l", "izquierda" -> Relative.LEFT;
                case "right", "r", "derecha" -> Relative.RIGHT;
                default -> throw new IllegalArgumentException("Dirección desconocida: " + word
                        + " (x, y, z, up, down, north, south, east, west, aim, forward, back, left, right o (a,b,c))");
            };
        }

        private void expect(char character) {
            skip();
            if (peek() != character) {
                throw new IllegalArgumentException("Falta «" + character + "» en la dirección " + text);
            }
            position++;
        }

        void skip() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        char peek() {
            return position < text.length() ? text.charAt(position) : 0;
        }
    }
}
