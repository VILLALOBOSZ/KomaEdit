package com.komainu.moxia.rebuild;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lo que se escribe tras {@code /komaedit rebuild [portapapeles|plano <nombre>]}: el orden y las banderas.
 *
 * <pre>
 * [orden] [-b bloques/tick | -t duración] [-p pausa] [-i retraso iguales] [-d retraso distintos] [-w espera]
 *         [-e eje] [-c x,y,z] [-s semilla] [-f] [-n] [-a] [-l x,y,z]
 * </pre>
 *
 * Los tiempos van en segundos ({@code 2}, {@code 1.5}, {@code 1,5}, {@code 2s}) o en ticks con {@code t}
 * ({@code 10t}). Las coordenadas admiten {@code ~} relativo a {@code base} ({@code ~,~2,~-3}). Java puro.
 *
 * @param seed       semilla del orden al azar; {@code null} si se elige al lanzar
 * @param particles  partículas discretas al poner cada bloque ({@code -f})
 * @param sound      sonido de colocación, flojo y espaciado ({@code -n})
 * @param skipAir    portapapeles y planos: no vaciar donde el portapapeles tiene aire ({@code -a})
 * @param center     centro de las ondas ({@code -c}), o {@code null}
 * @param location   dónde va el portapapeles o el plano ({@code -l}), o {@code null}
 */
public record RebuildSettings(RebuildOrder order, RebuildAxis axis, int[] center, Long seed, RebuildPace pace,
                              boolean particles, boolean sound, boolean skipAir, int[] location) {
    public static final RebuildOrder DEFAULT_ORDER = RebuildOrder.BUILDER;
    /** Las banderas, para el autocompletado. */
    public static final List<String> FLAGS = List.of("-b", "-t", "-p", "-i", "-d", "-w", "-e", "-c", "-s", "-f",
            "-n", "-a", "-l");

    public RebuildSettings {
        center = center == null ? null : center.clone();
        location = location == null ? null : location.clone();
    }

    public static RebuildSettings defaults() {
        return new RebuildSettings(DEFAULT_ORDER, RebuildAxis.X_POSITIVE, null, null, RebuildPace.DEFAULT, false,
                false, false, null);
    }

    /**
     * @param base la posición del jugador, para {@code ~} en las coordenadas
     */
    public static RebuildSettings parse(String input, int[] base) {
        List<String> words = words(input);
        RebuildOrder order = null;
        RebuildAxis axis = null;
        int[] center = null;
        Long seed = null;
        RebuildPace pace = RebuildPace.DEFAULT;
        Double blocksPerTick = null;
        Double duration = null;
        boolean particles = false;
        boolean sound = false;
        boolean skipAir = false;
        int[] location = null;
        for (int index = 0; index < words.size(); index++) {
            String word = words.get(index);
            String flag = word.toLowerCase(Locale.ROOT);
            if (!flag.startsWith("-") || flag.length() < 2 || Character.isDigit(flag.charAt(1))) {
                if (order != null) {
                    throw new IllegalArgumentException("Sobra «" + word + "»: el orden ya es " + order.id());
                }
                order = RebuildOrder.parse(word).orElseThrow(() -> new IllegalArgumentException(
                        "Orden desconocido «" + word + "»: usa " + String.join(", ", RebuildOrder.names())));
                continue;
            }
            switch (flag) {
                case "-f" -> particles = true;
                case "-n" -> sound = true;
                case "-a" -> skipAir = true;
                default -> {
                    if (index + 1 >= words.size()) {
                        throw new IllegalArgumentException("Falta el valor de " + flag);
                    }
                    String value = words.get(++index);
                    switch (flag) {
                        case "-b" -> blocksPerTick = number(value, flag);
                        case "-t" -> duration = ticks(value, flag);
                        case "-p" -> pace = pace.withLayerPause(ticks(value, flag));
                        case "-i" -> pace = pace.withDelays(ticks(value, flag), pace.otherDelay());
                        case "-d" -> pace = pace.withDelays(pace.sameDelay(), ticks(value, flag));
                        case "-w" -> pace = pace.withLeadIn(ticks(value, flag));
                        case "-e" -> axis = RebuildAxis.parse(value).orElseThrow(() -> new IllegalArgumentException(
                                "Eje desconocido «" + value + "»: x, -x, y, -y, z o -z"));
                        case "-c" -> center = coordinates(value, base, flag);
                        case "-l" -> location = coordinates(value, base, flag);
                        case "-s" -> seed = seed(value);
                        default -> throw new IllegalArgumentException("Bandera desconocida " + word + ": usa "
                                + String.join(" ", FLAGS));
                    }
                }
            }
        }
        if (blocksPerTick != null && duration != null) {
            throw new IllegalArgumentException("Usa -b (bloques por tick) o -t (duración), no los dos");
        }
        if (blocksPerTick != null) {
            pace = pace.withBlocksPerTick(blocksPerTick);
        }
        if (duration != null) {
            if (duration <= 0.0) {
                throw new IllegalArgumentException("La duración tiene que ser mayor que 0");
            }
            pace = pace.withDuration(duration);
        }
        if (order == null) {
            order = axis != null ? RebuildOrder.AXIS : center != null ? RebuildOrder.WAVES : DEFAULT_ORDER;
        }
        if (axis != null && order != RebuildOrder.AXIS) {
            throw new IllegalArgumentException("-e solo vale con el orden eje");
        }
        if (center != null && order != RebuildOrder.WAVES) {
            throw new IllegalArgumentException("-c solo vale con el orden ondas");
        }
        if (seed != null && order != RebuildOrder.RANDOM) {
            throw new IllegalArgumentException("-s solo vale con el orden azar");
        }
        return new RebuildSettings(order, axis == null ? RebuildAxis.X_POSITIVE : axis, center, seed, pace,
                particles, sound, skipAir, location);
    }

    /** Las opciones del orden con la semilla ya elegida. */
    public RebuildSequence.Options options(long chosenSeed) {
        return new RebuildSequence.Options(axis, center, seed == null ? chosenSeed : seed);
    }

    public RebuildSettings withSeed(long value) {
        return new RebuildSettings(order, axis, center, value, pace, particles, sound, skipAir, location);
    }

    /** Un número de segundos ({@code 2}, {@code 1.5}, {@code 1,5}, {@code 2s}) o de ticks ({@code 10t}), en ticks. */
    static double ticks(String value, String flag) {
        String text = value.toLowerCase(Locale.ROOT);
        if (text.endsWith("t")) {
            return number(text.substring(0, text.length() - 1), flag);
        }
        if (text.endsWith("s")) {
            text = text.substring(0, text.length() - 1);
        }
        return number(text, flag) * 20.0;
    }

    static double number(String value, String flag) {
        try {
            double number = Double.parseDouble(value.replace(',', '.'));
            if (!Double.isFinite(number) || number < 0.0) {
                throw new NumberFormatException();
            }
            return number;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("El valor de " + flag + " no es un número válido: «" + value + "»");
        }
    }

    private static long seed(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            // Una palabra también vale de semilla: siempre la misma para el mismo texto.
            return value.toLowerCase(Locale.ROOT).hashCode();
        }
    }

    static int[] coordinates(String value, int[] base, String flag) {
        String[] parts = value.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException(flag + " lleva tres coordenadas separadas por comas: x,y,z");
        }
        int[] result = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            String part = parts[axis].strip();
            try {
                if (part.startsWith("~")) {
                    int offset = part.length() == 1 ? 0 : Integer.parseInt(part.substring(1));
                    result[axis] = Math.addExact(base == null ? 0 : base[axis], offset);
                } else {
                    result[axis] = Integer.parseInt(part);
                }
            } catch (NumberFormatException | ArithmeticException exception) {
                throw new IllegalArgumentException("Coordenada no válida en " + flag + ": «" + part + "»");
            }
        }
        return result;
    }

    private static List<String> words(String input) {
        List<String> words = new ArrayList<>();
        if (input == null) {
            return words;
        }
        for (String word : input.strip().split("\\s+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        return words;
    }
}
