package com.komainu.moxia.client.gui.studio;

import net.minecraft.client.gui.DrawContext;

/**
 * Iconos de 12×12 píxeles dibujados con rectángulos, para no depender de glifos que el paquete de recursos
 * del servidor puede no tener (las flechas ↶ ↷ salían vacías con fuentes personalizadas).
 */
public final class StudioIcons {
    public static final int SIZE = 12;

    public enum Icon {
        PATH, FIGURE, STRUCTURES, MODIFIERS, MATERIAL, WEATHER, INSTANCES, RECIPES, COLOR,
        UNDO, REDO, HOLOGRAM, HOLOGRAM_OFF, HELP, CLOSE, TOOLS, LOCK
    }

    private StudioIcons() {
    }

    /** Dibuja {@code icon} con la esquina superior izquierda en ({@code x}, {@code y}). */
    public static void draw(DrawContext context, Icon icon, int x, int y, int color) {
        switch (icon) {
            case PATH -> {
                box(context, x, y + 1, 3, 3, color);
                box(context, x + 5, y + 5, 3, 3, color);
                box(context, x + 9, y + 8, 3, 3, color);
                box(context, x + 3, y + 3, 2, 1, color);
                box(context, x + 4, y + 4, 1, 1, color);
                box(context, x + 8, y + 7, 1, 1, color);
            }
            case FIGURE -> {
                outline(context, x, y + 3, 9, 9, color);
                outline(context, x + 3, y, 9, 9, color);
            }
            case STRUCTURES -> {
                box(context, x, y + 1, 12, 2, color);
                box(context, x + 1, y + 3, 2, 8, color);
                box(context, x + 9, y + 3, 2, 8, color);
                box(context, x + 2, y + 5, 8, 1, color);
                box(context, x, y + 11, 4, 1, color);
                box(context, x + 8, y + 11, 4, 1, color);
            }
            case MODIFIERS -> {
                box(context, x, y + 2, 12, 1, color);
                box(context, x, y + 6, 12, 1, color);
                box(context, x, y + 10, 12, 1, color);
                box(context, x + 3, y, 2, 5, color);
                box(context, x + 8, y + 4, 2, 5, color);
                box(context, x + 5, y + 8, 2, 4, color);
            }
            case MATERIAL -> {
                box(context, x, y + 1, 4, 10, 0xFFE24B6A);
                box(context, x + 4, y + 1, 4, 10, 0xFFF2B94B);
                box(context, x + 8, y + 1, 4, 10, 0xFF4BC3E2);
            }
            case WEATHER -> {
                box(context, x, y + 10, 12, 2, color);
                box(context, x + 1, y + 4, 3, 6, color);
                box(context, x + 1, y + 1, 2, 3, color);
                box(context, x + 6, y + 7, 2, 3, color);
                box(context, x + 8, y + 8, 3, 2, color);
                box(context, x + 6, y + 4, 1, 1, color);
            }
            case COLOR -> {
                // Tres muestras de oscuro a claro, como una paleta.
                box(context, x, y + 1, 3, 10, color & 0x66FFFFFF);
                box(context, x + 4, y + 1, 3, 10, color & 0xAAFFFFFF);
                box(context, x + 8, y + 1, 3, 10, color);
            }
            case INSTANCES -> {
                outline(context, x, y, 8, 8, color);
                outline(context, x + 4, y + 4, 8, 8, color);
            }
            case RECIPES -> {
                outline(context, x + 1, y, 10, 12, color);
                box(context, x + 3, y + 3, 6, 1, color);
                box(context, x + 3, y + 6, 6, 1, color);
                box(context, x + 3, y + 9, 4, 1, color);
            }
            case UNDO -> {
                box(context, x + 3, y + 3, 6, 1, color);
                box(context, x + 9, y + 4, 1, 4, color);
                box(context, x + 3, y + 8, 6, 1, color);
                box(context, x + 1, y + 3, 2, 1, color);
                box(context, x + 2, y + 2, 1, 3, color);
                box(context, x + 3, y + 1, 1, 5, color);
            }
            case REDO -> {
                box(context, x + 3, y + 3, 6, 1, color);
                box(context, x + 2, y + 4, 1, 4, color);
                box(context, x + 3, y + 8, 6, 1, color);
                box(context, x + 9, y + 3, 2, 1, color);
                box(context, x + 9, y + 2, 1, 3, color);
                box(context, x + 8, y + 1, 1, 5, color);
            }
            case HOLOGRAM, HOLOGRAM_OFF -> {
                outline(context, x + 1, y + 3, 10, 6, color);
                box(context, x + 5, y + 5, 2, 2, color);
                box(context, x + 3, y + 2, 6, 1, color);
                box(context, x + 3, y + 9, 6, 1, color);
                if (icon == Icon.HOLOGRAM_OFF) {
                    for (int step = 0; step < 12; step++) {
                        box(context, x + step, y + step, 1, 1, StudioTheme.DANGER);
                    }
                }
            }
            case HELP -> {
                box(context, x + 3, y + 1, 6, 1, color);
                box(context, x + 2, y + 2, 1, 2, color);
                box(context, x + 9, y + 2, 1, 3, color);
                box(context, x + 7, y + 5, 2, 1, color);
                box(context, x + 5, y + 6, 2, 2, color);
                box(context, x + 5, y + 10, 2, 2, color);
            }
            case CLOSE -> {
                for (int step = 1; step < 11; step++) {
                    box(context, x + step, y + step, 1, 1, color);
                    box(context, x + 11 - step, y + step, 1, 1, color);
                }
            }
            case TOOLS -> {
                // Martillo: cabeza con oreja y mango.
                box(context, x + 1, y + 1, 8, 3, color);
                box(context, x + 9, y + 2, 2, 1, color);
                box(context, x + 10, y + 3, 1, 1, color);
                box(context, x + 4, y + 4, 2, 8, color);
            }
            case LOCK -> {
                box(context, x + 4, y + 1, 4, 1, color);
                box(context, x + 3, y + 2, 1, 3, color);
                box(context, x + 8, y + 2, 1, 3, color);
                box(context, x + 2, y + 5, 8, 6, color);
            }
        }
    }

    private static void box(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x, y, x + width, y + height, color);
    }

    private static void outline(DrawContext context, int x, int y, int width, int height, int color) {
        context.drawBorder(x, y, width, height, color);
    }
}
