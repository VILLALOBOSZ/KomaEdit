package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.ShapeOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Fórmulas escritas por el usuario para la figura {@code expression}.
 *
 * <p>Es un lenguaje pequeño y cerrado: números, variables, operadores, llamadas a funciones puras, el ternario
 * {@code ?:} y definiciones {@code nombre = valor;} al principio. No tiene bucles, asignaciones posteriores ni
 * estado global, así que cada evaluación acaba en un número fijo de pasos y el mismo punto da siempre el mismo
 * valor: el holograma y el Build coinciden.</p>
 *
 * <p>No se usa el motor de expresiones de WorldEdit porque cada llamada a su {@code evaluate} consulta el reloj,
 * busca las variables en un mapa y crea objetos: medido en 7.3.8, 150–335 ns por evaluación frente a los
 * 12–63 ns de este árbol con las mismas fórmulas, y una figura evalúa millones de puntos. Además su lenguaje
 * admite bucles y búferes globales compartidos entre jugadores ({@code gmegabuf}), que aquí habría que vetar uno
 * a uno.</p>
 *
 * <p>La fórmula se compila una vez a un árbol inmutable con las constantes ya plegadas; cada hilo evalúa con su
 * propio {@link Runner}, que guarda las variables locales.</p>
 */
public final class TubeFormula {
    public static final int MAX_LENGTH = ShapeOptions.MAX_FORMULA_LENGTH;
    static final int MAX_NODES = 400;
    static final int MAX_DEPTH = 40;
    static final int MAX_LOCALS = 16;

    private static final Set<String> RESERVED = Set.of("x", "y", "z", "t", "pi", "e");
    /** En la expresión de corte del spline de ruido {@code t} no es una entrada y se puede definir. */
    private static final Set<String> CUT_RESERVED = Set.of("x", "y", "z", "pi", "e");
    /** Variables propias que una definición sí puede tapar (como {@code r=0.1;} en las fórmulas de ezEdits). */
    private static final Set<String> BUILT_IN = Set.of("r", "a", "n");
    private static final Set<String> LOOPS = Set.of("while", "for", "do", "loop", "repeat", "until", "goto");

    private final String source;
    private final Node[] definitions;
    private final Node root;
    private final boolean usesRadial;
    private final boolean usesAngle;
    private final double radialBound;

    private TubeFormula(String source, Node[] definitions, Node root, boolean usesRadial, boolean usesAngle) {
        this.source = source;
        this.definitions = definitions;
        this.root = root;
        this.usesRadial = usesRadial;
        this.usesAngle = usesAngle;
        radialBound = new RadialBound(definitions).bound(root);
    }

    /**
     * Compila la fórmula.
     *
     * @throws IllegalArgumentException con un mensaje en español que señala la posición del error
     */
    public static TubeFormula compile(String source) {
        if (source == null || source.isBlank()) {
            throw new FormulaException(-1, "La fórmula está vacía");
        }
        String trimmed = source.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new FormulaException(MAX_LENGTH, "La fórmula es demasiado larga (" + trimmed.length()
                    + " caracteres; máximo " + MAX_LENGTH + ")");
        }
        return new Parser(trimmed, false).parse();
    }

    /**
     * Compila una expresión de corte para el spline de ruido ({@code //ezspline noise … -i}): además de las
     * variables de siempre, {@code n} es el valor del ruido en el punto y {@code d} la profundidad pedida, y
     * {@code t} se puede redefinir como en las expresiones de ezEdits.
     */
    public static TubeFormula compileNoiseCut(String source) {
        if (source == null || source.isBlank()) {
            throw new FormulaException(-1, "La expresión de corte está vacía");
        }
        String trimmed = source.trim();
        if (trimmed.length() > MAX_LENGTH) {
            throw new FormulaException(MAX_LENGTH, "La expresión de corte es demasiado larga (" + trimmed.length()
                    + " caracteres; máximo " + MAX_LENGTH + ")");
        }
        return new Parser(trimmed, true).parse();
    }

    /** Comprueba la fórmula sin guardarla; devuelve el mensaje de error o {@code null} si es válida. */
    public static String validate(String source) {
        try {
            compile(source);
            return null;
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    public String source() {
        return source;
    }

    /**
     * Radio de la sección (en radios) más allá del cual la fórmula nunca coloca bloque. Si no se puede acotar es
     * √2, la esquina del cuadrado [-1, 1]; con {@code r <= 1 && …} es 1 y el motor no recorre las esquinas de balde.
     */
    public double radialBound() {
        return radialBound;
    }

    /**
     * Evaluador con su propio marco de variables: uno por hilo.
     *
     * @param seed semilla de {@code noise()}
     * @param repeat valor de {@code n} (lados o repeticiones de la figura)
     */
    public Runner runner(long seed, int repeat) {
        return new Runner(this, new TubeShapes.Noise(seed), repeat);
    }

    /** Error de compilación con la posición (desde 0) en la fórmula recortada; -1 si no hay posición. */
    public static final class FormulaException extends IllegalArgumentException {
        private final int position;

        FormulaException(int position, String message) {
            super(message);
            this.position = position;
        }

        public int position() {
            return position;
        }
    }

    public static final class Runner {
        private final TubeFormula formula;
        private final Frame frame;

        private Runner(TubeFormula formula, TubeShapes.Noise noise, int repeat) {
            this.formula = formula;
            frame = new Frame(formula.definitions.length, noise, repeat);
        }

        /** Evalúa una expresión de corte: {@code n} es el ruido en el punto y {@code d} la profundidad. */
        public double evaluate(double x, double y, double z, double t, double noise, double depth) {
            frame.n = noise;
            frame.d = depth;
            return evaluate(x, y, z, t);
        }

        /**
         * @param x lateral en radios (-1 a 1)
         * @param y vertical en radios (-1 a 1)
         * @param z avance a lo largo del recorrido, en radios
         * @param t progreso a lo largo del recorrido (0 a 1)
         */
        public double evaluate(double x, double y, double z, double t) {
            Frame current = frame;
            current.x = x;
            current.y = y;
            current.z = z;
            current.t = t;
            if (formula.usesRadial) {
                current.r = Math.sqrt(x * x + y * y);
            }
            if (formula.usesAngle) {
                current.a = Math.atan2(y, x);
            }
            Node[] definitions = formula.definitions;
            for (int index = 0; index < definitions.length; index++) {
                current.locals[index] = definitions[index].eval(current);
            }
            return formula.root.eval(current);
        }
    }

    static final class Frame {
        double x;
        double y;
        double z;
        double t;
        double r;
        double a;
        double n;
        double d;
        final double[] locals;
        final TubeShapes.Noise noise;

        Frame(int locals, TubeShapes.Noise noise, int repeat) {
            this.locals = new double[locals];
            this.noise = noise;
            n = repeat;
        }
    }

    // ---------------------------------------------------------------- Análisis

    private static final class Parser {
        private final String text;
        private final boolean cut;
        private final Map<String, Integer> locals = new HashMap<>();
        private final List<Node> definitions = new ArrayList<>();
        private int position;
        private int depth;
        private int nodes;
        private boolean usesRadial;
        private boolean usesAngle;

        Parser(String text, boolean cut) {
            this.text = text;
            this.cut = cut;
        }

        TubeFormula parse() {
            Node root;
            while (true) {
                skipSpaces();
                int start = position;
                String name = peekDefinition();
                if (name == null) {
                    root = expression();
                    skipSpaces();
                    if (peek() == ';') {
                        position++;
                        skipSpaces();
                        if (position < text.length()) {
                            throw error(start, "Solo se puede definir con «nombre = valor;»; la última parte es "
                                    + "la que decide la figura");
                        }
                    }
                    break;
                }
                defineLocal(name, start);
            }
            skipSpaces();
            if (position < text.length()) {
                throw error(position, "Sobra «" + excerpt(position) + "»");
            }
            Node[] compiled = definitions.toArray(Node[]::new);
            return new TubeFormula(text, compiled, root, usesRadial, usesAngle);
        }

        /** Si viene {@code nombre =} (y no {@code ==}), devuelve el nombre y deja el cursor tras el igual. */
        private String peekDefinition() {
            int saved = position;
            if (!isIdentifierStart(peek())) {
                return null;
            }
            String name = identifier();
            skipSpaces();
            if (peek() == '=' && peekAt(1) != '=') {
                position++;
                return name;
            }
            position = saved;
            return null;
        }

        private void defineLocal(String name, int start) {
            if ((cut ? CUT_RESERVED : RESERVED).contains(name) || Functions.exists(name)) {
                throw error(start, "No se puede redefinir «" + name + "»");
            }
            if (locals.size() >= MAX_LOCALS && !locals.containsKey(name)) {
                throw error(start, "Demasiadas definiciones (máximo " + MAX_LOCALS + ")");
            }
            Node value = expression();
            skipSpaces();
            if (peek() != ';') {
                throw error(position, "Falta «;» después de definir «" + name + "»");
            }
            position++;
            // Redefinir crea un hueco nuevo: lo escrito antes sigue viendo el valor anterior.
            locals.put(name, definitions.size());
            definitions.add(value);
        }

        private Node expression() {
            if (++depth > MAX_DEPTH) {
                throw error(position, "La fórmula está demasiado anidada (máximo " + MAX_DEPTH + " niveles)");
            }
            Node condition = or();
            skipSpaces();
            if (peek() == '?') {
                position++;
                Node whenTrue = expression();
                expect(':', "Falta «:» en el ternario «condición ? sí : no»");
                Node whenFalse = expression();
                condition = node(new Ternary(condition, whenTrue, whenFalse));
            }
            depth--;
            return condition;
        }

        private Node or() {
            Node left = and();
            while (true) {
                skipSpaces();
                if (peek() == '|' && peekAt(1) == '|') {
                    position += 2;
                    left = node(new Or(left, and()));
                } else {
                    return left;
                }
            }
        }

        private Node and() {
            Node left = comparison();
            while (true) {
                skipSpaces();
                if (peek() == '&' && peekAt(1) == '&') {
                    position += 2;
                    left = node(new And(left, comparison()));
                } else {
                    return left;
                }
            }
        }

        private Node comparison() {
            Node left = additive();
            while (true) {
                skipSpaces();
                char current = peek();
                char next = peekAt(1);
                int operator;
                if (current == '<') {
                    operator = next == '=' ? Compare.LE : Compare.LT;
                } else if (current == '>') {
                    operator = next == '=' ? Compare.GE : Compare.GT;
                } else if (current == '=' && next == '=') {
                    operator = Compare.EQ;
                } else if (current == '!' && next == '=') {
                    operator = Compare.NE;
                } else if (current == '=') {
                    throw error(position, "Usa «==» para comparar; «=» solo sirve para definir al principio");
                } else {
                    return left;
                }
                position += next == '=' ? 2 : 1;
                left = node(new Compare(operator, left, additive()));
            }
        }

        private Node additive() {
            Node left = multiplicative();
            while (true) {
                skipSpaces();
                char current = peek();
                if (current == '+') {
                    position++;
                    left = node(new Add(left, multiplicative()));
                } else if (current == '-') {
                    position++;
                    left = node(new Subtract(left, multiplicative()));
                } else {
                    return left;
                }
            }
        }

        private Node multiplicative() {
            Node left = unary();
            while (true) {
                skipSpaces();
                char current = peek();
                if (current == '*') {
                    position++;
                    left = node(new Multiply(left, unary()));
                } else if (current == '/') {
                    position++;
                    left = node(new Divide(left, unary()));
                } else if (current == '%') {
                    position++;
                    left = node(new Remainder(left, unary()));
                } else {
                    return left;
                }
            }
        }

        /** El signo menos agrupa menos que la potencia: {@code -x^2} es {@code -(x^2)}. */
        private Node unary() {
            skipSpaces();
            char current = peek();
            if (current == '-') {
                position++;
                return node(new Negate(nested()));
            }
            if (current == '+') {
                position++;
                return nested();
            }
            if (current == '!' && peekAt(1) != '=') {
                position++;
                return node(new Not(nested()));
            }
            return power();
        }

        private Node nested() {
            if (++depth > MAX_DEPTH) {
                throw error(position, "La fórmula está demasiado anidada (máximo " + MAX_DEPTH + " niveles)");
            }
            Node value = unary();
            depth--;
            return value;
        }

        /** La potencia asocia por la derecha: {@code 2^3^2} es {@code 2^9}. */
        private Node power() {
            Node base = primary();
            skipSpaces();
            if (peek() == '^') {
                position++;
                return node(new Power(base, nested()));
            }
            return base;
        }

        private Node primary() {
            skipSpaces();
            int start = position;
            char current = peek();
            if (current == '(') {
                position++;
                Node inner = expression();
                expect(')', "Falta «)» para cerrar el paréntesis de la posición " + (start + 1));
                return inner;
            }
            if (Character.isDigit(current) || current == '.') {
                return node(new Constant(number()));
            }
            if (isIdentifierStart(current)) {
                String name = identifier();
                skipSpaces();
                if (LOOPS.contains(name.toLowerCase(Locale.ROOT))) {
                    throw error(start, "Las fórmulas no admiten bucles («" + name + "»)");
                }
                if (peek() == '(') {
                    return call(name, start);
                }
                return variable(name, start);
            }
            if (current == '\0') {
                throw error(position, "La fórmula termina antes de tiempo");
            }
            if (current == ';') {
                throw error(position, "Falta un valor antes de «;»");
            }
            throw error(position, "Carácter inesperado «" + current + "»");
        }

        /** Los nombres distinguen mayúsculas, como en WorldEdit: {@code R} y {@code r} son variables distintas. */
        private Node variable(String name, int start) {
            Integer slot = locals.get(name);
            if (slot != null) {
                Node definition = definitions.get(slot);
                if (definition instanceof Constant constant) {
                    return node(new Constant(constant.value));
                }
                return node(new Local(slot));
            }
            return switch (name) {
                case "x" -> node(new Variable(Variable.X));
                case "y" -> node(new Variable(Variable.Y));
                case "z" -> node(new Variable(Variable.Z));
                case "t" -> node(new Variable(Variable.T));
                case "r" -> {
                    usesRadial = true;
                    yield node(new Variable(Variable.R));
                }
                case "a" -> {
                    usesAngle = true;
                    yield node(new Variable(Variable.A));
                }
                case "n" -> node(new Variable(Variable.N));
                case "d" -> {
                    if (!cut) {
                        throw error(start, "Variable desconocida «d»: usa x, y, z, t, r, a, n, pi, e o "
                                + "defínela al principio con «d = valor;»");
                    }
                    yield node(new Variable(Variable.D));
                }
                case "pi" -> node(new Constant(Math.PI));
                case "e" -> node(new Constant(Math.E));
                default -> {
                    if (Functions.exists(name)) {
                        throw error(start, "«" + name + "» es una función: escribe " + name + "(…)");
                    }
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (!lower.equals(name) && (RESERVED.contains(lower) || BUILT_IN.contains(lower))) {
                        throw error(start, "Variable desconocida «" + name + "»: las mayúsculas cuentan, "
                                + "escribe «" + lower + "»");
                    }
                    throw error(start, "Variable desconocida «" + name + "»: usa x, y, z, t, r, a, n, "
                            + (cut ? "d, " : "") + "pi, e o defínela al principio con «" + name + " = valor;»");
                }
            };
        }

        private Node call(String name, int start) {
            if (!Functions.exists(name)) {
                String lower = name.toLowerCase(Locale.ROOT);
                throw error(start, "Función desconocida «" + name + "» ("
                        + (Functions.exists(lower) ? "las mayúsculas cuentan, escribe «" + lower + "»" : Functions.NAMES)
                        + ")");
            }
            position++;
            List<Node> arguments = new ArrayList<>();
            skipSpaces();
            if (peek() != ')') {
                while (true) {
                    arguments.add(expression());
                    skipSpaces();
                    if (peek() == ',') {
                        position++;
                        continue;
                    }
                    break;
                }
            }
            expect(')', "Falta «)» para cerrar la llamada a «" + name + "»");
            String arityError = Functions.checkArity(name, arguments.size());
            if (arityError != null) {
                throw error(start, arityError);
            }
            return node(Functions.create(name, arguments.toArray(Node[]::new)));
        }

        private double number() {
            int start = position;
            while (Character.isDigit(peek())) {
                position++;
            }
            if (peek() == '.') {
                position++;
                while (Character.isDigit(peek())) {
                    position++;
                }
            }
            if ((peek() == 'e' || peek() == 'E')
                    && (Character.isDigit(peekAt(1))
                    || (peekAt(1) == '-' || peekAt(1) == '+') && Character.isDigit(peekAt(2)))) {
                position += 2;
                while (Character.isDigit(peek())) {
                    position++;
                }
            }
            String literal = text.substring(start, position);
            try {
                double value = Double.parseDouble(literal);
                if (!Double.isFinite(value)) {
                    throw error(start, "Número fuera de rango «" + literal + "»");
                }
                return value;
            } catch (NumberFormatException exception) {
                throw error(start, "Número no válido «" + literal + "»");
            }
        }

        private String identifier() {
            int start = position;
            while (isIdentifierPart(peek())) {
                position++;
            }
            return text.substring(start, position);
        }

        private Node node(Node value) {
            if (++nodes > MAX_NODES) {
                throw error(position, "La fórmula es demasiado compleja (máximo " + MAX_NODES + " operaciones)");
            }
            return value.fold();
        }

        private void expect(char expected, String message) {
            skipSpaces();
            if (peek() != expected) {
                throw error(position, message);
            }
            position++;
        }

        private void skipSpaces() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private char peek() {
            return peekAt(0);
        }

        private char peekAt(int offset) {
            int index = position + offset;
            return index < text.length() ? text.charAt(index) : '\0';
        }

        private String excerpt(int start) {
            String rest = text.substring(start);
            return rest.length() > 16 ? rest.substring(0, 16) + "…" : rest;
        }

        private FormulaException error(int at, String message) {
            return new FormulaException(at, "Fórmula, posición " + (at + 1) + ": " + message);
        }

        private static boolean isIdentifierStart(char value) {
            return value >= 'a' && value <= 'z' || value >= 'A' && value <= 'Z' || value == '_';
        }

        private static boolean isIdentifierPart(char value) {
            return isIdentifierStart(value) || value >= '0' && value <= '9';
        }
    }

    // ---------------------------------------------------------------- Cota radial

    /**
     * Análisis conservador de hasta qué radio puede ser positiva la fórmula. Reconoce las condiciones sobre
     * {@code r} ({@code r <= c}, {@code c > r}, {@code c - r}), las combina por {@code &&}, {@code ||}, el ternario
     * y los productos por una condición, y sigue las definiciones. Lo que no sabe acotar vale √2.
     */
    private static final class RadialBound {
        static final double FULL = Math.sqrt(2.0);
        private final double[] localBounds;
        private final double[] localUppers;
        private final boolean[] localBooleans;

        RadialBound(Node[] definitions) {
            localBounds = new double[definitions.length];
            localUppers = new double[definitions.length];
            localBooleans = new boolean[definitions.length];
            for (int index = 0; index < definitions.length; index++) {
                localBounds[index] = bound(definitions[index]);
                localUppers[index] = upper(definitions[index]);
                localBooleans[index] = isBoolean(definitions[index]);
            }
        }

        /** Un radio R tal que {@code node > 0} implica {@code r <= R}. */
        double bound(Node node) {
            if (node instanceof Constant constant) {
                return constant.value > 0.0 ? FULL : 0.0;
            }
            if (node instanceof Local local) {
                return localBounds[local.slot];
            }
            if (node instanceof And and) {
                return Math.min(bound(and.left), bound(and.right));
            }
            if (node instanceof Or or) {
                return Math.max(bound(or.left), bound(or.right));
            }
            if (node instanceof Ternary ternary) {
                return Math.max(Math.min(bound(ternary.condition), bound(ternary.whenTrue)), bound(ternary.whenFalse));
            }
            if (node instanceof Multiply product) {
                // Un factor que solo vale 0 o 1 deja el producto en cero allí donde él es cero.
                double result = FULL;
                if (isBoolean(product.left)) {
                    result = Math.min(result, bound(product.left));
                }
                if (isBoolean(product.right)) {
                    result = Math.min(result, bound(product.right));
                }
                return result;
            }
            if (node instanceof Compare compare) {
                boolean below = compare.operator == Compare.LT || compare.operator == Compare.LE;
                boolean above = compare.operator == Compare.GT || compare.operator == Compare.GE;
                if (below && isRadius(compare.left)) {
                    return clamp(upper(compare.right));
                }
                if (above && isRadius(compare.right)) {
                    return clamp(upper(compare.left));
                }
                return FULL;
            }
            if (node instanceof Subtract difference && isRadius(difference.right)) {
                return clamp(upper(difference.left));
            }
            return FULL;
        }

        /** Cota superior del valor; infinita si no se sabe. */
        double upper(Node node) {
            if (node instanceof Constant constant) {
                return constant.value;
            }
            if (node instanceof Local local) {
                return localUppers[local.slot];
            }
            if (node instanceof Ternary ternary) {
                return Math.max(upper(ternary.whenTrue), upper(ternary.whenFalse));
            }
            if (node instanceof Add sum) {
                return upper(sum.left) + upper(sum.right);
            }
            if (node instanceof Call2 call && (call.function == Call2.MIN || call.function == Call2.MAX)) {
                return call.function == Call2.MIN
                        ? Math.min(upper(call.left), upper(call.right))
                        : Math.max(upper(call.left), upper(call.right));
            }
            return isBoolean(node) ? 1.0 : Double.POSITIVE_INFINITY;
        }

        private boolean isBoolean(Node node) {
            if (node instanceof Local local) {
                return localBooleans[local.slot];
            }
            return node instanceof Compare || node instanceof And || node instanceof Or || node instanceof Not;
        }

        private static boolean isRadius(Node node) {
            return node instanceof Variable variable && variable.which == Variable.R;
        }

        private static double clamp(double value) {
            return Double.isNaN(value) ? FULL : Math.clamp(value, 0.0, FULL);
        }
    }

    // ---------------------------------------------------------------- Nodos

    abstract static class Node {
        abstract double eval(Frame frame);

        /** Hijos para el plegado de constantes; las hojas no tienen. */
        Node[] children() {
            return EMPTY;
        }

        /** Solo el ruido depende de algo más que sus argumentos (la semilla) y no se puede plegar. */
        boolean pure() {
            return true;
        }

        /** Si todos los hijos son constantes, el nodo se sustituye por su valor. */
        final Node fold() {
            Node[] children = children();
            if (children.length == 0 || !pure()) {
                return this;
            }
            for (Node child : children) {
                if (!(child instanceof Constant)) {
                    return this;
                }
            }
            return new Constant(eval(null));
        }

        private static final Node[] EMPTY = new Node[0];
    }

    static final class Constant extends Node {
        final double value;

        Constant(double value) {
            this.value = value;
        }

        @Override
        double eval(Frame frame) {
            return value;
        }
    }

    static final class Variable extends Node {
        static final int X = 0;
        static final int Y = 1;
        static final int Z = 2;
        static final int T = 3;
        static final int R = 4;
        static final int A = 5;
        static final int N = 6;
        static final int D = 7;
        private final int which;

        Variable(int which) {
            this.which = which;
        }

        @Override
        double eval(Frame frame) {
            return switch (which) {
                case X -> frame.x;
                case Y -> frame.y;
                case Z -> frame.z;
                case T -> frame.t;
                case R -> frame.r;
                case A -> frame.a;
                case D -> frame.d;
                default -> frame.n;
            };
        }
    }

    static final class Local extends Node {
        private final int slot;

        Local(int slot) {
            this.slot = slot;
        }

        @Override
        double eval(Frame frame) {
            return frame.locals[slot];
        }
    }

    abstract static class Unary extends Node {
        final Node operand;

        Unary(Node operand) {
            this.operand = operand;
        }

        @Override
        final Node[] children() {
            return new Node[]{operand};
        }
    }

    abstract static class Binary extends Node {
        final Node left;
        final Node right;

        Binary(Node left, Node right) {
            this.left = left;
            this.right = right;
        }

        @Override
        final Node[] children() {
            return new Node[]{left, right};
        }
    }

    static final class Negate extends Unary {
        Negate(Node operand) {
            super(operand);
        }

        @Override
        double eval(Frame frame) {
            return -operand.eval(frame);
        }
    }

    static final class Not extends Unary {
        Not(Node operand) {
            super(operand);
        }

        @Override
        double eval(Frame frame) {
            return operand.eval(frame) > 0.0 ? 0.0 : 1.0;
        }
    }

    static final class Add extends Binary {
        Add(Node left, Node right) {
            super(left, right);
        }

        @Override
        double eval(Frame frame) {
            return left.eval(frame) + right.eval(frame);
        }
    }

    static final class Subtract extends Binary {
        Subtract(Node left, Node right) {
            super(left, right);
        }

        @Override
        double eval(Frame frame) {
            return left.eval(frame) - right.eval(frame);
        }
    }

    static final class Multiply extends Binary {
        Multiply(Node left, Node right) {
            super(left, right);
        }

        @Override
        double eval(Frame frame) {
            return left.eval(frame) * right.eval(frame);
        }
    }

    static final class Divide extends Binary {
        Divide(Node left, Node right) {
            super(left, right);
        }

        @Override
        double eval(Frame frame) {
            return left.eval(frame) / right.eval(frame);
        }
    }

    /** Resto con el signo del dividendo, como {@code %} en WorldEdit; {@code mod()} es el módulo siempre positivo. */
    static final class Remainder extends Binary {
        Remainder(Node left, Node right) {
            super(left, right);
        }

        @Override
        double eval(Frame frame) {
            return left.eval(frame) % right.eval(frame);
        }
    }

    static final class Power extends Binary {
        Power(Node left, Node right) {
            super(left, right);
        }

        @Override
        double eval(Frame frame) {
            double exponent = right.eval(frame);
            double base = left.eval(frame);
            return exponent == 2.0 ? base * base : Math.pow(base, exponent);
        }
    }

    static final class Compare extends Binary {
        static final int LT = 0;
        static final int LE = 1;
        static final int GT = 2;
        static final int GE = 3;
        static final int EQ = 4;
        static final int NE = 5;
        private final int operator;

        Compare(int operator, Node left, Node right) {
            super(left, right);
            this.operator = operator;
        }

        @Override
        double eval(Frame frame) {
            double first = left.eval(frame);
            double second = right.eval(frame);
            boolean result = switch (operator) {
                case LT -> first < second;
                case LE -> first <= second;
                case GT -> first > second;
                case GE -> first >= second;
                case EQ -> first == second;
                default -> first != second;
            };
            return result ? 1.0 : 0.0;
        }
    }

    /** Verdadero es cualquier valor mayor que cero, igual que la salida de la figura. */
    static final class And extends Binary {
        And(Node left, Node right) {
            super(left, right);
        }

        @Override
        double eval(Frame frame) {
            return left.eval(frame) > 0.0 && right.eval(frame) > 0.0 ? 1.0 : 0.0;
        }
    }

    static final class Or extends Binary {
        Or(Node left, Node right) {
            super(left, right);
        }

        @Override
        double eval(Frame frame) {
            return left.eval(frame) > 0.0 || right.eval(frame) > 0.0 ? 1.0 : 0.0;
        }
    }

    static final class Ternary extends Node {
        private final Node condition;
        private final Node whenTrue;
        private final Node whenFalse;

        Ternary(Node condition, Node whenTrue, Node whenFalse) {
            this.condition = condition;
            this.whenTrue = whenTrue;
            this.whenFalse = whenFalse;
        }

        @Override
        Node[] children() {
            return new Node[]{condition, whenTrue, whenFalse};
        }

        @Override
        double eval(Frame frame) {
            return condition.eval(frame) > 0.0 ? whenTrue.eval(frame) : whenFalse.eval(frame);
        }
    }

    // ---------------------------------------------------------------- Funciones

    /** Funciones puras disponibles; los ángulos van en radianes. */
    static final class Functions {
        static final String NAMES = "sin, cos, tan, asin, acos, atan, atan2, abs, sqrt, cbrt, exp, ln, log, log10, "
                + "pow, min, max, floor, ceil, round, frac, mod, sign, clamp, hypot, noise";
        private static final Map<String, Integer> ONE = Map.ofEntries(
                Map.entry("sin", Call1.SIN), Map.entry("cos", Call1.COS), Map.entry("tan", Call1.TAN),
                Map.entry("asin", Call1.ASIN), Map.entry("acos", Call1.ACOS), Map.entry("atan", Call1.ATAN),
                Map.entry("abs", Call1.ABS), Map.entry("sqrt", Call1.SQRT), Map.entry("cbrt", Call1.CBRT),
                Map.entry("exp", Call1.EXP), Map.entry("ln", Call1.LN), Map.entry("log", Call1.LN),
                Map.entry("log10", Call1.LOG10), Map.entry("floor", Call1.FLOOR), Map.entry("ceil", Call1.CEIL),
                Map.entry("round", Call1.ROUND), Map.entry("frac", Call1.FRAC), Map.entry("sign", Call1.SIGN)
        );
        private static final Map<String, Integer> TWO = Map.of(
                "atan2", Call2.ATAN2, "pow", Call2.POW, "mod", Call2.MOD, "hypot", Call2.HYPOT
        );

        private Functions() {
        }

        static boolean exists(String name) {
            return ONE.containsKey(name) || TWO.containsKey(name)
                    || name.equals("min") || name.equals("max") || name.equals("clamp") || name.equals("noise");
        }

        static String checkArity(String name, int count) {
            if (ONE.containsKey(name)) {
                return count == 1 ? null : "«" + name + "» espera 1 argumento";
            }
            if (TWO.containsKey(name)) {
                return count == 2 ? null : "«" + name + "» espera 2 argumentos";
            }
            return switch (name) {
                case "min", "max" -> count >= 2 && count <= 8 ? null : "«" + name + "» espera de 2 a 8 argumentos";
                case "clamp" -> count == 3 ? null : "«clamp» espera 3 argumentos: valor, mínimo y máximo";
                case "noise" -> count == 3 ? null : "«noise» espera 3 argumentos: x, y y z";
                default -> "Función desconocida «" + name + "»";
            };
        }

        static Node create(String name, Node[] arguments) {
            Integer one = ONE.get(name);
            if (one != null) {
                return new Call1(one, arguments[0]);
            }
            Integer two = TWO.get(name);
            if (two != null) {
                return new Call2(two, arguments[0], arguments[1]);
            }
            return switch (name) {
                case "min" -> arguments.length == 2
                        ? new Call2(Call2.MIN, arguments[0], arguments[1])
                        : new Extreme(false, arguments);
                case "max" -> arguments.length == 2
                        ? new Call2(Call2.MAX, arguments[0], arguments[1])
                        : new Extreme(true, arguments);
                case "clamp" -> new Clamp(arguments[0], arguments[1], arguments[2]);
                case "noise" -> new NoiseCall(arguments[0], arguments[1], arguments[2]);
                default -> throw new IllegalStateException("Función desconocida «" + name + "»");
            };
        }
    }

    static final class Call1 extends Unary {
        static final int SIN = 0;
        static final int COS = 1;
        static final int TAN = 2;
        static final int ASIN = 3;
        static final int ACOS = 4;
        static final int ATAN = 5;
        static final int ABS = 6;
        static final int SQRT = 7;
        static final int CBRT = 8;
        static final int EXP = 9;
        static final int LN = 10;
        static final int LOG10 = 11;
        static final int FLOOR = 12;
        static final int CEIL = 13;
        static final int ROUND = 14;
        static final int FRAC = 15;
        static final int SIGN = 16;
        private final int function;

        Call1(int function, Node operand) {
            super(operand);
            this.function = function;
        }

        @Override
        double eval(Frame frame) {
            double value = operand.eval(frame);
            return switch (function) {
                case SIN -> Math.sin(value);
                case COS -> Math.cos(value);
                case TAN -> Math.tan(value);
                case ASIN -> Math.asin(value);
                case ACOS -> Math.acos(value);
                case ATAN -> Math.atan(value);
                case ABS -> Math.abs(value);
                case SQRT -> Math.sqrt(value);
                case CBRT -> Math.cbrt(value);
                case EXP -> Math.exp(value);
                case LN -> Math.log(value);
                case LOG10 -> Math.log10(value);
                case FLOOR -> Math.floor(value);
                case CEIL -> Math.ceil(value);
                case ROUND -> Math.rint(value);
                case FRAC -> value - Math.floor(value);
                default -> Math.signum(value);
            };
        }
    }

    static final class Call2 extends Binary {
        static final int ATAN2 = 0;
        static final int POW = 1;
        static final int MOD = 2;
        static final int HYPOT = 3;
        static final int MIN = 4;
        static final int MAX = 5;
        private final int function;

        Call2(int function, Node left, Node right) {
            super(left, right);
            this.function = function;
        }

        @Override
        double eval(Frame frame) {
            double first = left.eval(frame);
            double second = right.eval(frame);
            return switch (function) {
                case ATAN2 -> Math.atan2(first, second);
                case POW -> second == 2.0 ? first * first : Math.pow(first, second);
                case MOD -> first - Math.floor(first / second) * second;
                case HYPOT -> Math.sqrt(first * first + second * second);
                case MIN -> Math.min(first, second);
                default -> Math.max(first, second);
            };
        }
    }

    static final class Extreme extends Node {
        private final boolean maximum;
        private final Node[] arguments;

        Extreme(boolean maximum, Node[] arguments) {
            this.maximum = maximum;
            this.arguments = arguments;
        }

        @Override
        Node[] children() {
            return arguments;
        }

        @Override
        double eval(Frame frame) {
            double result = arguments[0].eval(frame);
            for (int index = 1; index < arguments.length; index++) {
                double value = arguments[index].eval(frame);
                result = maximum ? Math.max(result, value) : Math.min(result, value);
            }
            return result;
        }
    }

    static final class Clamp extends Node {
        private final Node value;
        private final Node minimum;
        private final Node maximum;

        Clamp(Node value, Node minimum, Node maximum) {
            this.value = value;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        @Override
        Node[] children() {
            return new Node[]{value, minimum, maximum};
        }

        @Override
        double eval(Frame frame) {
            return Math.max(minimum.eval(frame), Math.min(maximum.eval(frame), value.eval(frame)));
        }
    }

    /** Ruido de Perlin fractal en [0, 1] con la semilla de la figura (la del recorrido si no se indica). */
    static final class NoiseCall extends Node {
        private final Node x;
        private final Node y;
        private final Node z;

        NoiseCall(Node x, Node y, Node z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        boolean pure() {
            return false;
        }

        @Override
        Node[] children() {
            return new Node[]{x, y, z};
        }

        @Override
        double eval(Frame frame) {
            return frame.noise.fractal(x.eval(frame), y.eval(frame), z.eval(frame));
        }
    }
}
