package com.komainu.moxia.studio.ui;

/**
 * Una tecla pulsada en el Studio, ya sin GLFW (que el servidor no tiene): la pantalla traduce el código del juego y
 * la vista común decide qué hace.
 *
 * @param letter la letra o cifra en mayúscula para {@link Code#LETTER} y {@link Code#DIGIT}; 0 en las demás
 */
public record StudioKey(Code code, char letter, boolean control, boolean shift) {
    public enum Code {
        ENTER, ESCAPE, BACKSPACE, DELETE, TAB, UP, DOWN, LEFT, RIGHT, PAGE_UP, PAGE_DOWN, HOME, END, F1, LETTER,
        DIGIT, OTHER
    }

    public static StudioKey of(Code code) {
        return new StudioKey(code, '\0', false, false);
    }

    public static StudioKey letter(char letter, boolean control, boolean shift) {
        return new StudioKey(Code.LETTER, Character.toUpperCase(letter), control, shift);
    }

    public static StudioKey digit(char digit) {
        return new StudioKey(Code.DIGIT, digit, false, false);
    }

    public boolean is(char key) {
        return (code == Code.LETTER || code == Code.DIGIT) && letter == Character.toUpperCase(key);
    }
}
