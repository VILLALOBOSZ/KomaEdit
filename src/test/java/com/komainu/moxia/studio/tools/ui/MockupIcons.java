package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.studio.layout.Rect;

/** Los iconos de 12×12 de {@code StudioIcons}, con los mismos rectángulos, para las maquetas. */
final class MockupIcons {
    private MockupIcons() {
    }

    static void draw(FakeToolsUi ui, String icon, int x, int y, int color) {
        switch (icon) {
            case "path" -> {
                box(ui, x, y + 1, 3, 3, color);
                box(ui, x + 5, y + 5, 3, 3, color);
                box(ui, x + 9, y + 8, 3, 3, color);
                box(ui, x + 3, y + 3, 2, 1, color);
                box(ui, x + 4, y + 4, 1, 1, color);
                box(ui, x + 8, y + 7, 1, 1, color);
            }
            case "figure" -> {
                outline(ui, x, y + 3, 9, 9, color);
                outline(ui, x + 3, y, 9, 9, color);
            }
            case "structures" -> {
                box(ui, x, y + 1, 12, 2, color);
                box(ui, x + 1, y + 3, 2, 8, color);
                box(ui, x + 9, y + 3, 2, 8, color);
                box(ui, x + 2, y + 5, 8, 1, color);
                box(ui, x, y + 11, 4, 1, color);
                box(ui, x + 8, y + 11, 4, 1, color);
            }
            case "modifiers" -> {
                box(ui, x, y + 2, 12, 1, color);
                box(ui, x, y + 6, 12, 1, color);
                box(ui, x, y + 10, 12, 1, color);
                box(ui, x + 3, y, 2, 5, color);
                box(ui, x + 8, y + 4, 2, 5, color);
                box(ui, x + 5, y + 8, 2, 4, color);
            }
            case "material" -> {
                box(ui, x, y + 1, 4, 10, 0xFFE24B6A);
                box(ui, x + 4, y + 1, 4, 10, 0xFFF2B94B);
                box(ui, x + 8, y + 1, 4, 10, 0xFF4BC3E2);
            }
            case "weather" -> {
                box(ui, x, y + 10, 12, 2, color);
                box(ui, x + 1, y + 4, 3, 6, color);
                box(ui, x + 1, y + 1, 2, 3, color);
                box(ui, x + 6, y + 7, 2, 3, color);
                box(ui, x + 8, y + 8, 3, 2, color);
                box(ui, x + 6, y + 4, 1, 1, color);
            }
            case "color" -> {
                box(ui, x, y + 1, 3, 10, color & 0x66FFFFFF);
                box(ui, x + 4, y + 1, 3, 10, color & 0xAAFFFFFF);
                box(ui, x + 8, y + 1, 3, 10, color);
            }
            case "instances" -> {
                outline(ui, x, y, 8, 8, color);
                outline(ui, x + 4, y + 4, 8, 8, color);
            }
            case "recipes" -> {
                outline(ui, x + 1, y, 10, 12, color);
                box(ui, x + 3, y + 3, 6, 1, color);
                box(ui, x + 3, y + 6, 6, 1, color);
                box(ui, x + 3, y + 9, 4, 1, color);
            }
            case "undo" -> {
                box(ui, x + 3, y + 3, 6, 1, color);
                box(ui, x + 9, y + 4, 1, 4, color);
                box(ui, x + 3, y + 8, 6, 1, color);
                box(ui, x + 1, y + 3, 2, 1, color);
                box(ui, x + 2, y + 2, 1, 3, color);
                box(ui, x + 3, y + 1, 1, 5, color);
            }
            case "redo" -> {
                box(ui, x + 3, y + 3, 6, 1, color);
                box(ui, x + 2, y + 4, 1, 4, color);
                box(ui, x + 3, y + 8, 6, 1, color);
                box(ui, x + 9, y + 3, 2, 1, color);
                box(ui, x + 9, y + 2, 1, 3, color);
                box(ui, x + 8, y + 1, 1, 5, color);
            }
            case "hologram" -> {
                outline(ui, x + 1, y + 3, 10, 6, color);
                box(ui, x + 5, y + 5, 2, 2, color);
                box(ui, x + 3, y + 2, 6, 1, color);
                box(ui, x + 3, y + 9, 6, 1, color);
            }
            case "help" -> {
                box(ui, x + 3, y + 1, 6, 1, color);
                box(ui, x + 2, y + 2, 1, 2, color);
                box(ui, x + 9, y + 2, 1, 3, color);
                box(ui, x + 7, y + 5, 2, 1, color);
                box(ui, x + 5, y + 6, 2, 2, color);
                box(ui, x + 5, y + 10, 2, 2, color);
            }
            case "close" -> {
                for (int step = 1; step < 11; step++) {
                    box(ui, x + step, y + step, 1, 1, color);
                    box(ui, x + 11 - step, y + step, 1, 1, color);
                }
            }
            case "tools" -> {
                box(ui, x + 1, y + 1, 8, 3, color);
                box(ui, x + 9, y + 2, 2, 1, color);
                box(ui, x + 10, y + 3, 1, 1, color);
                box(ui, x + 4, y + 4, 2, 8, color);
            }
            default -> outline(ui, x, y, 12, 12, color);
        }
    }

    private static void box(FakeToolsUi ui, int x, int y, int width, int height, int color) {
        ui.fill(new Rect(x, y, width, height), color);
    }

    private static void outline(FakeToolsUi ui, int x, int y, int width, int height, int color) {
        ui.outline(new Rect(x, y, width, height), color);
    }
}
