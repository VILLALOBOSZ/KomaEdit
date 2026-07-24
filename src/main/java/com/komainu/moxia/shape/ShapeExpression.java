package com.komainu.moxia.shape;

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
                throw new IllegalArgumentException("Valor inválido para " + pair[0].trim() + ": " + pair[1].trim());
            }
            if (!Double.isFinite(number) || Math.abs(number) > 1_000.0) {
                throw new IllegalArgumentException("Valor fuera de rango para " + pair[0].trim());
            }
            options.put(name, number);
        }
        return new ShapeExpression(shape, new ShapeOptions(options));
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
            case "d", "depth", "depthmultiplier" -> "depthmultiplier";
            default -> throw new IllegalArgumentException("Parámetro de figura desconocido: " + input.trim());
        };
    }
}
