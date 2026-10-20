package com.komainu.moxia.nature;

import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.persistence.NamedDefinitions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Argumentos de {@code tree} y {@code rock} (comando y pincel), con los presets guardados. Los posicionales se
 * reconocen por su forma y no por su posición: una palabra es la especie o el tipo y un número es la altura o el
 * tamaño. Así {@code #mipreset 25 -s 7} usa el preset con otra altura y otra semilla: lo que escribe el jugador manda
 * sobre lo guardado.
 */
final class NatureArgs {
    private final BrushOptions typed;
    private final BrushOptions preset;
    private final String presetName;
    private final List<String> words = new ArrayList<>();
    private final List<String> numbers = new ArrayList<>();

    private NatureArgs(BrushOptions typed, BrushOptions preset, String presetName) {
        this.typed = typed;
        this.preset = preset;
        this.presetName = presetName;
        collect(typed, presetName != null);
        if (preset != null) {
            List<String> typedWords = List.copyOf(words);
            List<String> typedNumbers = List.copyOf(numbers);
            words.clear();
            numbers.clear();
            collect(preset, false);
            // Lo escrito sustituye a lo guardado del mismo tipo.
            if (!typedWords.isEmpty()) {
                words.clear();
                words.addAll(typedWords);
            }
            if (!typedNumbers.isEmpty()) {
                numbers.clear();
                numbers.addAll(typedNumbers);
            }
        }
    }

    /**
     * @param presets   busca el texto de un preset por su nombre ({@code null} si no existe)
     * @param reserved  palabras que nunca son un preset (las especies o los tipos de serie)
     */
    static NatureArgs parse(String input, Set<Character> switches, Set<Character> valued,
                            Function<String, String> presets, Predicate<String> reserved) {
        BrushOptions typed = BrushOptions.parse(input == null ? "" : input, switches, valued);
        String first = typed.positional(0);
        if (first == null || BrushOptions.isNumber(first) || !first.startsWith("#") && reserved.test(first)) {
            return new NatureArgs(typed, null, null);
        }
        String name;
        try {
            name = NamedDefinitions.normalizeName(first);
        } catch (IllegalArgumentException exception) {
            if (first.startsWith("#")) {
                throw exception;
            }
            return new NatureArgs(typed, null, null);
        }
        String saved = presets == null ? null : presets.apply(name);
        if (saved == null) {
            if (first.startsWith("#")) {
                throw new IllegalArgumentException("No tienes ningún preset llamado " + name);
            }
            return new NatureArgs(typed, null, null);
        }
        BrushOptions preset = BrushOptions.parse(saved, switches, valued);
        String inner = preset.positional(0);
        if (inner != null && inner.startsWith("#")) {
            throw new IllegalArgumentException("El preset " + name + " apunta a otro preset; guárdalo con sus ajustes");
        }
        return new NatureArgs(typed, preset, name);
    }

    private void collect(BrushOptions options, boolean skipFirst) {
        for (int index = skipFirst ? 1 : 0; index < options.count(); index++) {
            String value = options.positional(index);
            if (BrushOptions.isNumber(value)) {
                numbers.add(value);
            } else {
                words.add(value);
            }
        }
    }

    /** El preset usado, o {@code null}. */
    String presetName() {
        return presetName;
    }

    /** La única palabra suelta (especie o tipo), o {@code null}. */
    String word(String what) {
        if (words.size() > 1) {
            throw new IllegalArgumentException("Sobra «" + words.get(1) + "»: solo cabe " + what);
        }
        return words.isEmpty() ? null : words.getFirst();
    }

    /** El único número suelto, o {@code null}. */
    String number(String what) {
        if (numbers.size() > 1) {
            throw new IllegalArgumentException("Sobra «" + numbers.get(1) + "»: solo cabe " + what);
        }
        return numbers.isEmpty() ? null : numbers.getFirst();
    }

    boolean has(char flag) {
        return typed.has(flag) || preset != null && preset.has(flag);
    }

    String flag(char flag) {
        if (typed.has(flag)) {
            return typed.flag(flag);
        }
        return preset != null && preset.has(flag) ? preset.flag(flag) : null;
    }

    double decimal(char flag, double fallback, double minimum, double maximum, String what) {
        String value = flag(flag);
        if (value == null) {
            return fallback;
        }
        return number(value, minimum, maximum, what);
    }

    static double number(String value, double minimum, double maximum, String what) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(what + " debe ser un número: " + value);
        }
        if (!Double.isFinite(parsed) || parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(what + " debe estar entre " + format(minimum) + " y "
                    + format(maximum));
        }
        return parsed;
    }

    static long seed(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La semilla (-s) debe ser un número entero: " + value);
        }
    }

    /** ¿Desactiva el material ({@code no}, {@code none}, {@code ninguna}…)? */
    static boolean disabled(String material) {
        if (material == null) {
            return false;
        }
        return switch (TreeSpecies.normalize(material)) {
            case "no", "none", "ninguna", "ninguno", "nada", "off", "sin" -> true;
            default -> false;
        };
    }

    static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
