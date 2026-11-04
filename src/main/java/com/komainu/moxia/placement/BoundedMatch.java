package com.komainu.moxia.placement;

import java.util.regex.Pattern;

/**
 * Una expresión regular escrita por el jugador ({@code Schematic(N:…)}) contra un nombre de archivo, con un tope de
 * trabajo: una expresión con retroceso catastrófico ({@code ([^!]+)+!}) tardaba minutos con un nombre de treinta
 * letras, en el hilo del servidor, y el vigilante de Minecraft lo tumbaba. Cuenta las letras que lee el motor de
 * expresiones y se rinde al pasar del tope. Java puro.
 */
public final class BoundedMatch {
    /** Letras leídas como mucho por nombre: de sobra para cualquier expresión razonable. */
    public static final long STEPS = 200_000L;

    private BoundedMatch() {
    }

    /**
     * ¿Casa el nombre entero?
     *
     * @throws IllegalArgumentException si la expresión cuesta demasiado
     */
    public static boolean matches(Pattern pattern, String text) {
        return matches(pattern, text, STEPS);
    }

    static boolean matches(Pattern pattern, String text, long steps) {
        try {
            return pattern.matcher(new Counted(text, steps)).matches();
        } catch (TooCostly exception) {
            throw new IllegalArgumentException("La expresión del nombre cuesta demasiado de comprobar: simplifícala "
                    + "(sin repeticiones anidadas como (a+)+)");
        }
    }

    private static final class TooCostly extends RuntimeException {
        TooCostly() {
            super(null, null, false, false);
        }
    }

    /** El texto, contando cada letra que se lee. */
    private static final class Counted implements CharSequence {
        private final String text;
        private final long[] left;
        private final int start;
        private final int end;

        Counted(String text, long steps) {
            this(text, new long[]{steps}, 0, text.length());
        }

        private Counted(String text, long[] left, int start, int end) {
            this.text = text;
            this.left = left;
            this.start = start;
            this.end = end;
        }

        @Override
        public int length() {
            return end - start;
        }

        @Override
        public char charAt(int index) {
            if (--left[0] < 0) {
                throw new TooCostly();
            }
            return text.charAt(start + index);
        }

        @Override
        public CharSequence subSequence(int from, int to) {
            return new Counted(text, left, start + from, start + to);
        }

        @Override
        public String toString() {
            return text.substring(start, end);
        }
    }
}
