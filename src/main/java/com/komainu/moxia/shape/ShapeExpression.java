package com.komainu.moxia.shape;

import com.komainu.moxia.shape.smooth.SmoothMode;
import com.komainu.moxia.shape.tube.TubeEngine;
import com.komainu.moxia.shape.tube.TubeSettings;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record ShapeExpression(PathShape shape, ShapeOptions options) {
    public static ShapeExpression parse(String input) {
        String value = input.trim();
        int opening = value.indexOf('(');
        if (opening < 0) {
            return new ShapeExpression(PathShape.byId(value), ShapeOptions.EMPTY);
        }
        if (!value.endsWith(")") || opening == 0) {
            throw new IllegalArgumentException("Parámetros de figura incompletos: " + input);
        }

        PathShape shape = PathShape.byId(value.substring(0, opening));
        String body = value.substring(opening + 1, value.length() - 1).trim();
        if (body.isEmpty()) {
            return new ShapeExpression(shape, ShapeOptions.EMPTY);
        }

        Map<String, Double> options = new LinkedHashMap<>();
        for (String entry : body.split(",")) {
            String[] pair = entry.trim().split("[:=]", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                throw new IllegalArgumentException("Parámetro inválido: " + entry.trim());
            }
            String name = canonicalName(pair[0]);
            double number;
            try {
                number = Double.parseDouble(pair[1].trim());
            } catch (NumberFormatException exception) {
                number = symbolicValue(name, pair[0].trim(), pair[1].trim());
            }
            if (!Double.isFinite(number) || Math.abs(number) > 1_000.0) {
                throw new IllegalArgumentException("Valor fuera de rango para " + pair[0].trim());
            }
            options.put(name, number);
        }
        return new ShapeExpression(shape, new ShapeOptions(options));
    }

    /**
     * Permite escribir {@code profile:star}, {@code end:round}, {@code engine:exact} o {@code hollow:true} en vez
     * de índices.
     */
    private static double symbolicValue(String name, String rawName, String value) {
        return switch (name) {
            case "profile" -> TubeSettings.TubeProfile.byId(value).ordinal();
            case "end" -> TubeSettings.EndMode.byId(value).ordinal();
            case "normal" -> TubeSettings.NormalMode.byId(value).ordinal();
            case "quality" -> TubeSettings.Quality.byId(value).value();
            case "smooth" -> SmoothMode.byId(value).ordinal();
            case "engine" -> TubeEngine.byId(value).ordinal();
            case "hollow", "filled" -> switch (value.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "si", "sí", "on" -> 1.0;
                case "false", "no", "off" -> 0.0;
                default -> throw new IllegalArgumentException("Valor inválido para " + rawName + ": " + value);
            };
            default -> throw new IllegalArgumentException("Valor inválido para " + rawName + ": " + value);
        };
    }

    private static String canonicalName(String input) {
        String name = input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return switch (name) {
            case "e", "extrusion" -> "extrusion";
            case "t", "thickness", "grosor" -> "thickness";
            case "g", "gap", "separation" -> "gap";
            case "m", "majorexponent", "major" -> "majorexponent";
            case "n", "minorexponent", "minor" -> "minorexponent";
            case "s", "stretch" -> "stretch";
            case "q", "quality" -> "quality";
            case "a", "angle" -> "angle";
            case "c", "columns" -> "columns";
            case "h", "horizontaloffset" -> "horizontaloffset";
            case "v", "verticaloffset" -> "verticaloffset";
            case "d", "depth", "depthmultiplier", "profundidad" -> "depthmultiplier";
            case "profile", "perfil" -> "profile";
            case "sides", "lados", "points", "puntas", "petals", "petalos" -> "sides";
            case "aspect", "aspecto" -> "aspect";
            case "exponent", "exponente" -> "exponent";
            case "hollow", "hueco" -> "hollow";
            case "wall", "pared" -> "wall";
            case "filled", "fill", "relleno" -> "filled";
            case "end", "endmode", "extremo", "extremos" -> "end";
            case "normal", "normalmode", "normales" -> "normal";
            case "tension" -> "tension";
            case "bias", "sesgo" -> "bias";
            case "continuity", "continuidad" -> "continuity";
            case "twist", "torsion" -> "twist";
            case "rollend", "girofinal" -> "rollend";
            case "strands", "hebras" -> "strands";
            case "frequency", "frecuencia" -> "frequency";
            case "seed", "semilla" -> "seed";
            case "smooth", "smoothblocks", "piezas", "suavizado", "w" -> "smooth";
            case "coverage", "cobertura" -> "coverage";
            case "engine", "motor" -> "engine";
            case "k0", "k1", "k2", "k3", "k4", "k5", "p1", "p2", "p3", "p4" -> name;
            default -> throw new IllegalArgumentException("Parámetro de figura desconocido: " + input.trim());
        };
    }
}
