package com.komainu.moxia.weathering;

import java.util.Locale;

/**
 * Parámetros de Envejecer. Forma corta del comando:
 * {@code [intensidad] [clima] [-d derrumbe] [-v vida] [-seed semilla] [-agua]}, con intensidad, derrumbe y vida de
 * 0 a 100. Sin {@code -seed} la semilla queda sin fijar y la elige el servidor (y la dice para poder repetirla).
 */
public record WeatheringSettings(
        int intensity,
        int collapse,
        int vegetation,
        Long seed,
        WeatherStyle style,
        boolean water
) {
    public static final int DEFAULT_INTENSITY = 50;
    public static final int DEFAULT_COLLAPSE = 50;
    public static final int DEFAULT_VEGETATION = 50;

    public WeatheringSettings {
        intensity = percent(intensity, "intensidad");
        collapse = percent(collapse, "derrumbe");
        vegetation = percent(vegetation, "vida");
        if (style == null) {
            style = WeatherStyle.TEMPERATE;
        }
    }

    public static WeatheringSettings defaults() {
        return new WeatheringSettings(DEFAULT_INTENSITY, DEFAULT_COLLAPSE, DEFAULT_VEGETATION, null,
                WeatherStyle.TEMPERATE, false);
    }

    public WeatheringSettings withSeed(long value) {
        return new WeatheringSettings(intensity, collapse, vegetation, value, style, water);
    }

    public long seedOrZero() {
        return seed == null ? 0L : seed;
    }

    public static WeatheringSettings parse(String input) {
        int intensity = DEFAULT_INTENSITY;
        int collapse = DEFAULT_COLLAPSE;
        int vegetation = DEFAULT_VEGETATION;
        Long seed = null;
        WeatherStyle style = WeatherStyle.TEMPERATE;
        boolean water = false;
        boolean intensitySeen = false;
        boolean styleSeen = false;
        String[] tokens = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            String flag = token.toLowerCase(Locale.ROOT);
            if (flag.equals("-agua") || flag.equals("-water")) {
                water = true;
                continue;
            }
            if (isFlag(flag)) {
                if (index + 1 >= tokens.length) {
                    throw new IllegalArgumentException("Falta el valor de " + token);
                }
                String value = tokens[++index];
                switch (flag) {
                    case "-d", "-derrumbe", "-collapse" -> collapse = integer(value, 0, 100, token);
                    case "-v", "-vida", "-vegetation" -> vegetation = integer(value, 0, 100, token);
                    case "-i", "-intensidad", "-intensity" -> intensity = integer(value, 0, 100, token);
                    case "-seed", "-semilla" -> seed = (long) integer(value, -1_000_000_000, 1_000_000_000, token);
                    case "-s", "-clima", "-style" -> style = WeatherStyle.byId(value);
                    default -> throw new IllegalArgumentException("Bandera desconocida: " + token);
                }
                continue;
            }
            if (!intensitySeen && isNumber(flag)) {
                intensity = integer(token, 0, 100, "intensidad");
                intensitySeen = true;
            } else if (!styleSeen && WeatherStyle.isStyle(flag)) {
                style = WeatherStyle.byId(flag);
                styleSeen = true;
            } else {
                throw new IllegalArgumentException("Sobra el texto «" + token + "»");
            }
        }
        return new WeatheringSettings(intensity, collapse, vegetation, seed, style, water);
    }

    private static boolean isFlag(String token) {
        return token.length() > 1 && token.charAt(0) == '-' && Character.isLetter(token.charAt(1));
    }

    private static boolean isNumber(String token) {
        return token.matches("-?\\d+");
    }

    private static int integer(String value, int minimum, int maximum, String name) {
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " espera un número entero, no «" + value + "»");
        }
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(name + " debe estar entre " + minimum + " y " + maximum);
        }
        return parsed;
    }

    private static int percent(int value, String name) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " debe estar entre 0 y 100");
        }
        return value;
    }
}
