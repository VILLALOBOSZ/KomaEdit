package com.komainu.moxia.shape.tube;

import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.smooth.PieceMaterials;
import com.komainu.moxia.shape.smooth.SmoothMode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Traduce la forma corta de los comandos de tubo a {@link ShapeParameters}:
 * {@code <perfil> <radios> [patrón] [-e extremo] [-n normales] [-t torsión] [-r giro[,giroFinal]]
 * [-p tensión:sesgo:continuidad] [-q calidad] [-s estiramiento] [-hollow pared] [-d profundidad]
 * [-a aspecto] [-x exponente] [-k hebras] [-f frecuencia] [-seed semilla] [-fill] [-w perfil]
 * [-c cobertura]}. {@code -w} admite el perfil completo de smoothblocks ({@link SmoothProfile}:
 * {@code SlabsAndStairs(C:0.7,Slab:oak)}); {@code -c} sin {@code -w} activa losas y escaleras. La de {@code expr} es
 * {@code <radios> [banderas] [patrón] <fórmula…>}.
 */
public final class TubeCommandParser {
    /** Una bandera escrita después de la fórmula: se avisa de que van delante. */
    private static final Pattern TRAILING_FLAG = Pattern.compile("\\s-[a-zA-Z]+\\s+\\S");

    private TubeCommandParser() {
    }

    public static Result parse(PathShape shape, String profileToken, String radii, String rest, String defaultPattern) {
        if (!TubeFigures.supports(shape)) {
            throw new IllegalArgumentException(shape.displayName() + " no es una figura de tubo");
        }
        TubeSettings.RadiusProgression.Parsed parsedRadii = TubeSettings.RadiusProgression.parse(radii);
        ShapeParameters defaults = shape.defaults();
        Map<String, Double> options = new LinkedHashMap<>(parsedRadii.options());
        int roll = 0;
        if (profileToken != null && !profileToken.isBlank()) {
            String token = profileToken.trim().toLowerCase(Locale.ROOT);
            int digits = token.length();
            while (digits > 0 && Character.isDigit(token.charAt(digits - 1))) {
                digits--;
            }
            TubeSettings.TubeProfile profile = TubeSettings.TubeProfile.byId(token.substring(0, digits));
            options.put("profile", (double) profile.ordinal());
            if (digits < token.length()) {
                int sides = Integer.parseInt(token.substring(digits));
                if (sides < 2 || sides > 32) {
                    throw new IllegalArgumentException("El número de lados debe estar entre 2 y 32");
                }
                options.put("sides", (double) sides);
            }
        }

        String pattern = null;
        PieceMaterials materials = PieceMaterials.NONE;
        String[] tokens = rest == null || rest.isBlank() ? new String[0] : rest.trim().split("\\s+");
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            String flag = token.toLowerCase(Locale.ROOT);
            if (!isFlag(flag)) {
                if (pattern != null) {
                    throw new IllegalArgumentException("Sobra el texto «" + token + "»: el patrón va en una sola palabra");
                }
                pattern = token;
                continue;
            }
            if (flag.equals("-fill") || flag.equals("-relleno")) {
                options.put("filled", 1.0);
                continue;
            }
            if (index + 1 >= tokens.length) {
                throw new IllegalArgumentException("Falta el valor de " + token);
            }
            String value = tokens[++index];
            if (flag.equals("-w")) {
                int extra = SmoothProfile.extraWords(tokens, index);
                for (int join = 0; join < extra; join++) {
                    value += tokens[++index];
                }
            }
            switch (flag) {
                case "-e" -> options.put("end", (double) TubeSettings.EndMode.byId(value).ordinal());
                case "-n" -> options.put("normal", (double) TubeSettings.NormalMode.byId(value).ordinal());
                case "-q" -> options.put("quality", TubeSettings.Quality.byId(value).value());
                case "-t" -> options.put("twist", number(value, -3_600.0, 3_600.0, token));
                case "-s" -> options.put("stretch", number(value, 0.1, 10.0, token));
                case "-d" -> options.put("depthmultiplier", number(value, 0.0, 1.0, token));
                case "-a" -> options.put("aspect", number(value, 0.1, 1.0, token));
                case "-x" -> options.put("exponent", number(value, 1.0, 24.0, token));
                case "-k" -> options.put("strands", number(value, 2.0, 8.0, token));
                case "-f" -> options.put("frequency", number(value, 0.05, 8.0, token));
                case "-seed", "-semilla" -> options.put("seed", number(value, -1.0E9, 1.0E9, token));
                case "-w" -> {
                    SmoothProfile profile = SmoothProfile.parse(value);
                    options.put("smooth", (double) profile.mode().ordinal());
                    if (SmoothProfile.statesCoverage(value)) {
                        options.put("coverage", profile.coverage());
                    }
                    materials = profile.materials();
                }
                case "-c" -> options.put("coverage", number(value, 0.0, 1.0, token));
                case "-hollow", "-hueco" -> {
                    options.put("hollow", 1.0);
                    options.put("wall", number(value, 1.0, 16.0, token));
                }
                case "-r" -> {
                    String[] angles = value.split(",");
                    double start = number(angles[0], -3_600.0, 3_600.0, token);
                    roll = (int) Math.round(Math.clamp(start, -180.0, 180.0));
                    if (start != roll) {
                        options.put("angle", start - roll);
                    }
                    if (angles.length > 1) {
                        options.put("rollend", number(angles[1], -3_600.0, 3_600.0, token));
                    }
                }
                case "-p" -> {
                    String[] parts = value.split(":");
                    if (parts.length != 3) {
                        throw new IllegalArgumentException("-p espera tensión:sesgo:continuidad, por ejemplo 0:0:0");
                    }
                    options.put("tension", number(parts[0], -1.0, 1.0, "tensión"));
                    options.put("bias", number(parts[1], -1.0, 1.0, "sesgo"));
                    options.put("continuity", number(parts[2], -1.0, 1.0, "continuidad"));
                }
                default -> throw new IllegalArgumentException("Bandera desconocida: " + token);
            }
        }
        if (options.containsKey("coverage") && !options.containsKey("smooth")) {
            options.put("smooth", (double) SmoothMode.STAIRS.ordinal());
        }
        if (options.size() > ShapeOptions.MAX_ENTRIES) {
            throw new IllegalArgumentException("Demasiados parámetros a la vez (máximo " + ShapeOptions.MAX_ENTRIES + ")");
        }
        ShapeParameters parameters = new ShapeParameters(
                shape,
                parsedRadii.baseRadius(),
                defaults.spacing(),
                defaults.thickness(),
                roll,
                new ShapeOptions(options)
        );
        return new Result(parameters, pattern == null ? defaultPattern : pattern, materials);
    }

    /**
     * Forma corta de {@code /komaedit expr <radios> [banderas] [patrón] <fórmula…>}. Las banderas van delante, el
     * patrón es opcional y la fórmula ocupa el resto del texto, con espacios si se quiere; también vale el nombre
     * de una fórmula de serie con {@code n} pegado al final ({@code ventanas8}).
     *
     * <p>El patrón no se puede reconocer por su forma, así que se decide compilando: si todo el texto es una
     * fórmula válida no hay patrón; si no, la primera palabra es el patrón y el resto la fórmula.</p>
     */
    public static Result parseExpression(String radii, String rest, String defaultPattern) {
        String text = rest == null ? "" : rest.strip();
        StringBuilder flags = new StringBuilder();
        int cursor = 0;
        while (cursor < text.length()) {
            int end = tokenEnd(text, cursor);
            String token = text.substring(cursor, end);
            if (!isExpressionFlag(token)) {
                break;
            }
            flags.append(' ').append(token);
            cursor = skipSpaces(text, end);
            String flag = token.toLowerCase(Locale.ROOT);
            if (!flag.equals("-fill") && !flag.equals("-relleno")) {
                if (cursor >= text.length()) {
                    throw new IllegalArgumentException("Falta el valor de " + token);
                }
                int valueEnd = tokenEnd(text, cursor);
                flags.append(' ').append(text, cursor, valueEnd);
                cursor = skipSpaces(text, valueEnd);
            }
        }
        String remainder = text.substring(cursor);
        if (remainder.isEmpty()) {
            throw new IllegalArgumentException("Falta la fórmula: escribe una, como r < 1 && abs(x) > 0.3, "
                    + "o el nombre de una de serie (" + TubeFormulaPreset.ids() + ")");
        }
        Choice formula = choose(remainder);
        String pattern = null;
        if (formula.error() != null) {
            int split = tokenEnd(remainder, 0);
            String tail = remainder.substring(split).strip();
            Choice afterPattern = tail.isEmpty() ? null : choose(tail);
            if (afterPattern != null && afterPattern.error() == null) {
                pattern = remainder.substring(0, split);
                formula = afterPattern;
            } else {
                // Se informa del error que llegó más lejos: el de todo el texto o el de lo que sigue al patrón.
                TubeFormula.FormulaException error = formula.error();
                if (afterPattern != null && afterPattern.error().position()
                        + remainder.length() - tail.length() > error.position()) {
                    error = afterPattern.error();
                }
                String message = error.getMessage();
                if (TRAILING_FLAG.matcher(remainder).find()) {
                    message += " · las banderas (-e, -n, -t…) van antes del patrón y la fórmula";
                }
                throw new IllegalArgumentException(message);
            }
        }
        Result parsed = parse(PathShape.EXPRESSION, null, radii,
                pattern == null ? flags.toString() : flags + " " + pattern, defaultPattern);
        Map<String, Double> options = new LinkedHashMap<>(parsed.parameters().options().values());
        String source = "";
        if (formula.preset() != null) {
            options.put("preset", (double) formula.preset().ordinal());
            if (formula.repeat() > 0) {
                options.put("sides", (double) formula.repeat());
            }
        } else {
            source = formula.source();
        }
        if (options.size() > ShapeOptions.MAX_ENTRIES) {
            throw new IllegalArgumentException("Demasiados parámetros a la vez (máximo " + ShapeOptions.MAX_ENTRIES + ")");
        }
        ShapeParameters parameters = parsed.parameters().withOptions(new ShapeOptions(options, source));
        return new Result(parameters, parsed.pattern(), parsed.materials());
    }

    /** Fórmula de serie (con su {@code n} si lo lleva pegado) o fórmula propia ya comprobada. */
    private record Choice(TubeFormulaPreset preset, int repeat, String source, TubeFormula.FormulaException error) {
    }

    private static Choice choose(String text) {
        String token = text.strip();
        int digits = token.length();
        while (digits > 0 && Character.isDigit(token.charAt(digits - 1))) {
            digits--;
        }
        TubeFormulaPreset preset = digits > 0 ? TubeFormulaPreset.find(token.substring(0, digits)) : null;
        if (preset != null) {
            int repeat = 0;
            if (digits < token.length()) {
                String count = token.substring(digits);
                repeat = count.length() > 2 ? Integer.MAX_VALUE : Integer.parseInt(count);
                if (repeat < 2 || repeat > 32) {
                    throw new IllegalArgumentException("Las repeticiones de " + preset.id()
                            + " deben estar entre 2 y 32");
                }
            }
            return new Choice(preset, repeat, null, null);
        }
        try {
            TubeFormula.compile(token);
            return new Choice(null, 0, token, null);
        } catch (TubeFormula.FormulaException exception) {
            return new Choice(null, 0, null, exception);
        }
    }

    private static boolean isExpressionFlag(String token) {
        if (token.length() < 2 || token.charAt(0) != '-') {
            return false;
        }
        for (int index = 1; index < token.length(); index++) {
            if (!Character.isLetter(token.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static int tokenEnd(String text, int start) {
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static int skipSpaces(String text, int start) {
        int end = start;
        while (end < text.length() && Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static boolean isFlag(String token) {
        return token.length() > 1 && token.charAt(0) == '-' && Character.isLetter(token.charAt(1));
    }

    private static double number(String value, double minimum, double maximum, String name) {
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Valor inválido para " + name + ": " + value);
        }
        if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(name + " debe estar entre " + format(minimum) + " y " + format(maximum));
        }
        return parsed;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /**
     * @param materials materiales fijos de las piezas de {@code -w}; no se guardan en la figura, solo valen para
     *                  la construcción de este comando
     */
    public record Result(ShapeParameters parameters, String pattern, PieceMaterials materials) {
        public Result {
            materials = materials == null ? PieceMaterials.NONE : materials;
        }

        public Result(ShapeParameters parameters, String pattern) {
            this(parameters, pattern, PieceMaterials.NONE);
        }
    }
}
