package com.komainu.moxia.shape.tube;

import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Traduce la forma corta de los comandos de tubo a {@link ShapeParameters}:
 * {@code <perfil> <radios> [patrón] [-e extremo] [-n normales] [-t torsión] [-r giro[,giroFinal]]
 * [-p tensión:sesgo:continuidad] [-q calidad] [-s estiramiento] [-hollow pared] [-d profundidad]
 * [-a aspecto] [-x exponente] [-k hebras] [-f frecuencia] [-seed semilla] [-fill]}.
 */
public final class TubeCommandParser {
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
        return new Result(parameters, pattern == null ? defaultPattern : pattern);
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

    public record Result(ShapeParameters parameters, String pattern) {
    }
}
