package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Los controles del Studio vistos desde la etapa Herramientas. En el juego los pinta el {@code StudioUi} del cliente
 * (mismos colores, mismos gestos: rueda, arrastre, clic derecho, tooltips); fuera del juego, las maquetas y las
 * pruebas. Así la etapa entera se puede dibujar y probar sin Minecraft.
 */
public interface ToolsUi {
    /** Colores del tema del Studio por su papel (los mismos nombres que {@code StudioTheme}). */
    enum Ink {
        TEXT, TEXT_SECONDARY, MUTED, DISABLED, ACCENT, ACCENT_HOVER, ACCENT_DIM, SECTION, SUCCESS, WARNING, DANGER,
        DANGER_DIM, FAVORITE, CARD, CARD_HOVER, CARD_ACTIVE, FIELD, DIVIDER, TRACK, SURFACE, WHITE
    }

    /** Los mismos tonos de botón que {@code StudioUi.Tone}. */
    enum Tone {
        PRIMARY, SECONDARY, SELECTED, DANGER, WARNING, DISABLED
    }

    /** Clic con la posición, para filas con varias partes. */
    interface Click {
        void run(double mouseX, double mouseY);
    }

    /** La zona activa de un control: qué hace al pulsarlo, con la rueda, con clic derecho y qué explica. */
    interface Zone {
        Zone onClick(Runnable action);

        Zone onClick(Click action);

        Zone onScroll(IntConsumer action);

        Zone onReset(Runnable action);

        Zone tooltip(String text);

        Zone group(String group);

        Zone key(Object key);

        Zone valueZone(Rect zone);
    }

    /** Lo que necesita una fila numérica (como {@code StudioUi.Stepper}). */
    record Stepper(String label, String value, double fraction, boolean editing, boolean dragging, String input,
                   IntConsumer step, Runnable press, Runnable reset, String help, String group, Object key) {
    }

    int width(String text);

    String fit(String text, int maximumWidth);

    List<String> wrap(String text, int maximumWidth);

    boolean hovered(Rect rect);

    boolean visible(Rect rect);

    void fill(Rect rect, Ink ink);

    void fill(Rect rect, int argb);

    void border(Rect rect, Ink ink);

    void label(String text, int x, int y, int maximumWidth, Ink ink);

    void bold(String text, int x, int y, int maximumWidth, Ink ink);

    void centered(String text, Rect rect, Ink ink);

    /** Icono del objeto del bloque (16×16), o una barrera si no existe. */
    void item(String blockId, int x, int y);

    /** Miniatura de un sello de relieve ({@code montana}…) que llena {@code rect}. */
    void stamp(String name, Rect rect);

    /** Candado pequeño (8×9) de las tarjetas sin permiso. */
    void lock(int x, int y, Ink ink);

    Zone register(Rect rect);

    void section(Column column, String title);

    void paragraph(Column column, String text, Ink ink, int maximumLines);

    Zone button(Rect rect, String label, Tone tone);

    Zone chip(Rect rect, String label, boolean selected, Ink ink);

    int chipWidth(String label);

    Zone field(Rect rect, String value, String placeholder, boolean editing, boolean error);

    Zone listItem(Rect rect, String title, String subtitle, boolean selected, int rightReserve);

    Zone stepper(Rect row, Stepper model);

    Zone cycle(Rect row, String label, String value, IntConsumer step);

    Zone toggle(Rect row, String label, boolean on);
}
