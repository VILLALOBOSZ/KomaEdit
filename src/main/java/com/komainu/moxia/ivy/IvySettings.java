package com.komainu.moxia.ivy;

import java.util.Locale;
import java.util.OptionalLong;

/**
 * Parámetros de la hiedra viva.
 *
 * @param length    recorrido de cada tallo principal, en bloques (vigor)
 * @param branching de 0 a 1: cuánto se ramifica
 * @param hang      de 0 a 1: cuánto cuelga de bordes y techos
 * @param stems     tallos que salen de cada semilla
 * @param style     paleta de bloques
 * @param seed      semilla del ruido; vacía si el jugador no la fijó
 */
public record IvySettings(double length, double branching, double hang, int stems, IvyStyle style, OptionalLong seed) {
    public static final double MIN_LENGTH = 4.0;
    public static final double MAX_LENGTH = 200.0;
    public static final int MAX_STEMS = 8;

    public IvySettings {
        if (!(length >= MIN_LENGTH && length <= MAX_LENGTH)) {
            throw new IllegalArgumentException("El largo debe estar entre 4 y 200 bloques");
        }
        if (!(branching >= 0.0 && branching <= 1.0) || !(hang >= 0.0 && hang <= 1.0)) {
            throw new IllegalArgumentException("Ramas y cuelgue van de 0 a 1");
        }
        if (stems < 1 || stems > MAX_STEMS) {
            throw new IllegalArgumentException("Los tallos por semilla van de 1 a " + MAX_STEMS);
        }
        if (style == null || seed == null) {
            throw new IllegalArgumentException("Falta el estilo o la semilla");
        }
    }

    public static IvySettings defaults() {
        return new IvySettings(28.0, 0.4, 0.5, 2, IvyStyle.HIEDRA, OptionalLong.empty());
    }

    public IvySettings withSeed(long value) {
        return new IvySettings(length, branching, hang, stems, style, OptionalLong.of(value));
    }

    public IvySettings withStyle(IvyStyle value) {
        return new IvySettings(length, branching, hang, stems, value, seed);
    }

    public IvySettings withLength(double value) {
        return new IvySettings(value, branching, hang, stems, style, seed);
    }

    /** Largo máximo que puede alcanzar una tira colgante. */
    public int maxHang() {
        return (int) Math.round(1.0 + hang * 10.0 * style.hangMultiplier());
    }

    /** Distancia máxima a la que puede llegar la hiedra desde su semilla, para saber cuánto mundo capturar. */
    public int reach() {
        return (int) Math.ceil(length * 1.15) + maxHang() + 2;
    }

    /**
     * {@code [estilo] [-l largo] [-b ramas] [-g cuelgue] [-n tallos] [-seed semilla]}; también con nombres largos
     * ({@code -largo}, {@code -ramas}, {@code -cuelga}, {@code -tallos}, {@code -semilla}, {@code -estilo}).
     */
    public static IvySettings parse(String input) {
        IvySettings settings = defaults();
        double length = settings.length;
        double branching = settings.branching;
        double hang = settings.hang;
        int stems = settings.stems;
        IvyStyle style = settings.style;
        OptionalLong seed = OptionalLong.empty();
        boolean styleSet = false;
        String[] tokens = input == null || input.isBlank() ? new String[0] : input.strip().split("\\s+");
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            String flag = token.toLowerCase(Locale.ROOT);
            if (!flag.startsWith("-")) {
                if (styleSet) {
                    throw new IllegalArgumentException("Sobra el texto «" + token + "»: el estilo va una sola vez");
                }
                style = IvyStyle.byId(token);
                styleSet = true;
                continue;
            }
            if (index + 1 >= tokens.length) {
                throw new IllegalArgumentException("Falta el valor de " + token);
            }
            String value = tokens[++index];
            switch (flag) {
                case "-l", "-largo", "-length" -> length = number(value, MIN_LENGTH, MAX_LENGTH, token);
                case "-b", "-ramas", "-branches" -> branching = number(value, 0.0, 1.0, token);
                case "-g", "-cuelga", "-hang" -> hang = number(value, 0.0, 1.0, token);
                case "-n", "-tallos", "-stems" -> stems = (int) Math.round(number(value, 1.0, MAX_STEMS, token));
                case "-seed", "-semilla" -> seed = OptionalLong.of(Math.round(number(value, -1.0E15, 1.0E15, token)));
                case "-s", "-estilo", "-style" -> {
                    if (styleSet) {
                        throw new IllegalArgumentException("El estilo va una sola vez");
                    }
                    style = IvyStyle.byId(value);
                    styleSet = true;
                }
                default -> throw new IllegalArgumentException("Bandera desconocida: " + token);
            }
        }
        return new IvySettings(length, branching, hang, stems, style, seed);
    }

    private static double number(String value, double minimum, double maximum, String flag) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(flag + " espera un número, no «" + value + "»");
        }
        if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(flag + " debe estar entre " + format(minimum) + " y " + format(maximum));
        }
        return parsed;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
