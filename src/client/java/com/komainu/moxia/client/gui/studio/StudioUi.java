package com.komainu.moxia.client.gui.studio;

import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;

import static com.komainu.moxia.client.gui.studio.StudioTheme.*;

/**
 * Componentes del Studio en modo inmediato: cada control se dibuja y, en la misma llamada, deja registrada su
 * zona con lo que hace al pulsarlo, al girar la rueda o al restablecerlo con clic derecho. La pantalla solo
 * pregunta qué hay bajo el ratón, así que dibujo y clic no pueden desalinearse y añadir un control es una línea.
 */
public final class StudioUi {
    public static final int ROW = Column.ROW;
    public static final int SMALL_ROW = 16;
    public static final int LINE = 10;
    private static final int STEP_BUTTON = 16;
    private static final int VALUE_WIDTH = 34;

    public enum Tone {
        /** La acción principal de la zona: relleno de acento. */
        PRIMARY,
        /** Acción corriente. */
        SECONDARY,
        /** Opción elegida dentro de un grupo. */
        SELECTED,
        /** Borra o descarta. */
        DANGER,
        /** Pide una segunda pulsación (obra grande). */
        WARNING,
        /** Aún no se puede: se ve apagada, pero el clic explica qué falta. */
        DISABLED
    }

    private final TextRenderer text;
    private final List<Widget> widgets = new ArrayList<>();
    private final Deque<Rect> clips = new ArrayDeque<>();
    private DrawContext context;
    private int mouseX;
    private int mouseY;
    private boolean hoverEnabled = true;

    public StudioUi(TextRenderer text) {
        this.text = text;
    }

    public void begin(DrawContext context, int mouseX, int mouseY, boolean hoverEnabled) {
        this.context = context;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.hoverEnabled = hoverEnabled;
        widgets.clear();
        clips.clear();
    }

    /** Desde aquí los controles responden al ratón (tras dibujar el fondo de una ventana modal). */
    public void enableHover() {
        hoverEnabled = true;
    }

    public TextRenderer textRenderer() {
        return text;
    }

    public DrawContext context() {
        return context;
    }

    // ── Zonas activas ───────────────────────────────────────────────────────────────────────────────────

    /** El control más alto bajo el punto, o {@code null}. */
    public Widget at(double x, double y) {
        for (int index = widgets.size() - 1; index >= 0; index--) {
            if (widgets.get(index).bounds().contains(x, y)) {
                return widgets.get(index);
            }
        }
        return null;
    }

    /** Registra una zona; lo que queda fuera del recorte actual no responde. */
    public Widget register(Rect bounds) {
        Rect visible = clips.isEmpty() ? bounds : intersection(bounds, clips.peek());
        Widget widget = new Widget(visible);
        if (!visible.isEmpty()) {
            widgets.add(widget);
        }
        return widget;
    }

    public void pushClip(Rect clip) {
        clips.push(clip);
        context.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
    }

    public void popClip() {
        clips.pop();
        context.disableScissor();
        if (!clips.isEmpty()) {
            Rect outer = clips.peek();
            context.enableScissor(outer.x(), outer.y(), outer.right(), outer.bottom());
        }
    }

    /** Si el ratón está sobre {@code rect} y se ve (dentro del recorte y sin ventana modal encima). */
    public boolean hovered(Rect rect) {
        return hoverEnabled && rect.contains(mouseX, mouseY) && (clips.isEmpty() || clips.peek().contains(mouseX, mouseY));
    }

    /** Si {@code rect} se ve aunque sea en parte; sirve para no dibujar listas largas enteras. */
    public boolean visible(Rect rect) {
        return clips.isEmpty() || rect.intersects(clips.peek());
    }

    // ── Primitivas ──────────────────────────────────────────────────────────────────────────────────────

    public void fill(Rect rect, int color) {
        context.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color);
    }

    public void border(Rect rect, int color) {
        context.drawBorder(rect.x(), rect.y(), rect.width(), rect.height(), color);
    }

    public int width(String value) {
        return text.getWidth(value);
    }

    public void label(String value, int x, int y, int color) {
        context.drawText(text, value, x, y, color, false);
    }

    /** Texto recortado con «…» si no cabe en {@code maximumWidth}. */
    public void label(String value, int x, int y, int maximumWidth, int color) {
        if (maximumWidth <= 0) {
            return;
        }
        context.drawText(text, fit(value, maximumWidth), x, y, color, false);
    }

    public void bold(String value, int x, int y, int maximumWidth, int color) {
        Text styled = Text.literal(fit(value, maximumWidth - 1)).setStyle(Style.EMPTY.withBold(true));
        context.drawText(text, styled, x, y, color, false);
    }

    public void centered(String value, Rect rect, int color) {
        String fitted = fit(value, rect.width() - 8);
        context.drawText(text, fitted, rect.centerX() - text.getWidth(fitted) / 2, rect.centerY() - 4, color, false);
    }

    public void rightAligned(String value, int right, int y, int color) {
        context.drawText(text, value, right - text.getWidth(value), y, color, false);
    }

    public String fit(String value, int maximumWidth) {
        if (text.getWidth(value) <= maximumWidth) {
            return value;
        }
        String ellipsis = "…";
        return text.trimToWidth(value, Math.max(0, maximumWidth - text.getWidth(ellipsis))) + ellipsis;
    }

    public List<OrderedText> wrap(String value, int maximumWidth) {
        return text.wrapLines(Text.literal(value), Math.max(20, maximumWidth));
    }

    public void icon(StudioIcons.Icon icon, int x, int y, int color) {
        StudioIcons.draw(context, icon, x, y, color);
    }

    public void item(ItemStack stack, int x, int y) {
        context.drawItem(stack, x, y);
    }

    // ── Componentes sueltos ─────────────────────────────────────────────────────────────────────────────

    public Widget button(Rect rect, String label, Tone tone) {
        boolean hovered = hovered(rect);
        int background = switch (tone) {
            case PRIMARY -> hovered ? ACCENT_HOVER : ACCENT;
            case SELECTED -> CARD_ACTIVE;
            case DANGER -> hovered ? DANGER_DIM : CARD;
            case WARNING -> hovered ? 0xFF6B5420 : 0xFF54421A;
            case DISABLED -> CARD;
            case SECONDARY -> hovered ? CARD_HOVER : CARD;
        };
        int color = switch (tone) {
            case PRIMARY -> 0xFFFFFFFF;
            case SELECTED -> TEXT;
            case DANGER -> DANGER;
            case WARNING -> WARNING;
            case DISABLED -> DISABLED;
            case SECONDARY -> TEXT;
        };
        fill(rect, background);
        if (tone == Tone.SELECTED) {
            border(rect, ACCENT_DIM);
            fill(new Rect(rect.x(), rect.bottom() - 2, rect.width(), 2), ACCENT);
        }
        centered(label, rect, color);
        return register(rect);
    }

    /** Botón cuadrado con icono para la barra de herramientas. */
    public Widget toolButton(Rect rect, StudioIcons.Icon icon, boolean active, boolean enabled) {
        boolean hovered = hovered(rect) && enabled;
        fill(rect, active ? CARD_ACTIVE : hovered ? CARD_HOVER : SURFACE);
        if (active) {
            fill(new Rect(rect.x(), rect.bottom() - 2, rect.width(), 2), ACCENT);
        }
        int color = !enabled ? DISABLED : active ? ACCENT : hovered ? TEXT : TEXT_SECONDARY;
        icon(icon, rect.centerX() - StudioIcons.SIZE / 2, rect.centerY() - StudioIcons.SIZE / 2, color);
        return register(rect);
    }

    /** Pastilla pequeña para filtros y recientes. */
    public Widget chip(Rect rect, String label, boolean selected, int color) {
        boolean hovered = hovered(rect);
        fill(rect, selected ? ACCENT_DIM : hovered ? CARD_HOVER : CARD);
        centered(label, rect, selected ? 0xFFFFFFFF : color);
        return register(rect);
    }

    /** Ancho natural de una pastilla con {@code label}. */
    public int chipWidth(String label) {
        return text.getWidth(label) + 12;
    }

    /** Campo de texto: el valor (o el texto de ejemplo en gris) y el cursor mientras se escribe. */
    public Widget field(Rect rect, String value, String placeholder, boolean editing, boolean error) {
        boolean hovered = hovered(rect);
        fill(rect, FIELD);
        border(rect, editing ? (error ? WARNING : ACCENT) : hovered ? 0xFF4A4C58 : DIVIDER);
        int inner = rect.width() - 12;
        int y = rect.centerY() - 4;
        if (editing) {
            String shown = text.trimToWidth(value + "_", inner, true);
            label(shown, rect.x() + 6, y, TEXT);
        } else if (value.isEmpty()) {
            label(placeholder, rect.x() + 6, y, inner, MUTED);
        } else {
            label(value, rect.x() + 6, y, inner, TEXT);
        }
        return register(rect);
    }

    /** Tarjeta de lista: título y subtítulo, resaltada si está elegida. */
    public Widget listItem(Rect rect, String title, String subtitle, boolean selected, int rightReserve) {
        boolean hovered = hovered(rect);
        fill(rect, selected ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
        if (selected) {
            fill(rect.left(2), ACCENT);
        }
        int textWidth = rect.width() - 16 - rightReserve;
        label(title, rect.x() + 8, rect.y() + 5, textWidth, selected ? ACCENT_HOVER : TEXT);
        label(subtitle, rect.x() + 8, rect.y() + 16, textWidth, MUTED);
        return register(rect);
    }

    public void card(Rect rect) {
        fill(rect, CARD);
    }

    // ── Componentes de columna ──────────────────────────────────────────────────────────────────────────

    /** Título de sección con una línea fina a la derecha. */
    public void section(Column column, String title) {
        column.section();
        Rect row = column.next(11);
        String fitted = fit(title, row.width() - 12);
        label(fitted, row.x(), row.y() + 1, SECTION);
        int lineX = row.x() + text.getWidth(fitted) + 6;
        if (lineX < row.right()) {
            fill(new Rect(lineX, row.y() + 5, row.right() - lineX, 1), DIVIDER);
        }
    }

    /** Párrafo envuelto al ancho de la columna; {@code maximumLines} 0 = sin límite. */
    public void paragraph(Column column, String value, int color, int maximumLines) {
        List<OrderedText> lines = wrap(value, column.width());
        if (maximumLines > 0 && lines.size() > maximumLines) {
            lines = lines.subList(0, maximumLines);
        }
        Rect area = column.next(lines.size() * LINE);
        for (int index = 0; index < lines.size(); index++) {
            context.drawText(text, lines.get(index), area.x(), area.y() + index * LINE, color, false);
        }
    }

    /**
     * Fila numérica: etiqueta, − valor + y una barra fina que enseña dónde cae el valor en su rango.
     * Rueda o − + cambian el valor; arrastrar el número lo desliza y un clic deja escribirlo.
     */
    public Widget stepper(Rect row, Stepper model) {
        boolean hovered = hovered(row);
        fill(row, hovered ? CARD_HOVER : CARD);
        int barY = row.bottom() - 2;
        fill(new Rect(row.x(), barY, row.width(), 2), TRACK);
        fill(new Rect(row.x(), barY, (int) Math.round(row.width() * Math.clamp(model.fraction(), 0.0, 1.0)), 2),
                ACCENT_DIM);

        Rect plus = new Rect(row.right() - 2 - STEP_BUTTON, row.y() + 2, STEP_BUTTON, row.height() - 6);
        Rect value = new Rect(plus.x() - VALUE_WIDTH, plus.y(), VALUE_WIDTH, plus.height());
        Rect minus = new Rect(value.x() - STEP_BUTTON, plus.y(), STEP_BUTTON, plus.height());
        label(model.label(), row.x() + 7, row.centerY() - 5, minus.x() - row.x() - 12, TEXT_SECONDARY);
        stepButton(minus, "−");
        stepButton(plus, "+");
        boolean valueHovered = hovered(value);
        if (model.editing() || model.dragging() || valueHovered) {
            fill(value, model.editing() ? FIELD : CARD_ACTIVE);
            if (model.editing()) {
                border(value, ACCENT);
            }
        }
        String shown = model.editing() ? text.trimToWidth(model.input() + "_", VALUE_WIDTH - 4, true) : model.value();
        centered(shown, value, model.editing() ? TEXT : ACCENT_HOVER);

        Rect zone = new Rect(minus.x(), row.y(), row.right() - minus.x(), row.height());
        return register(row)
                .valueZone(zone)
                .onClick((x, y) -> {
                    if (minus.contains(x, y)) {
                        model.step().accept(-1);
                    } else if (plus.contains(x, y)) {
                        model.step().accept(1);
                    } else if (value.contains(x, y)) {
                        model.press().run();
                    }
                })
                .onScroll(model.step())
                .onReset(model.reset())
                .tooltip(model.help())
                .group(model.group())
                .key(model.key());
    }

    /** Fila de opciones que se recorren: etiqueta y ‹ valor ›. Pulsar el valor avanza. */
    public Widget cycle(Rect row, String label, String value, IntConsumer step) {
        boolean hovered = hovered(row);
        fill(row, hovered ? CARD_HOVER : CARD);
        int valueWidth = Math.min(Math.max(90, row.width() * 3 / 5), row.width() - 60);
        Rect next = new Rect(row.right() - 2 - STEP_BUTTON, row.y() + 2, STEP_BUTTON, row.height() - 4);
        Rect shown = new Rect(next.x() - (valueWidth - 2 * STEP_BUTTON), next.y(), valueWidth - 2 * STEP_BUTTON,
                next.height());
        Rect previous = new Rect(shown.x() - STEP_BUTTON, next.y(), STEP_BUTTON, next.height());
        label(label, row.x() + 7, row.centerY() - 4, previous.x() - row.x() - 12, TEXT_SECONDARY);
        stepButton(previous, "‹");
        stepButton(next, "›");
        centered(value, shown, TEXT);
        Rect zone = new Rect(previous.x(), row.y(), row.right() - previous.x(), row.height());
        return register(row)
                .valueZone(zone)
                .onClick((x, y) -> {
                    if (previous.contains(x, y)) {
                        step.accept(-1);
                    } else if (zone.contains(x, y)) {
                        step.accept(1);
                    }
                })
                .onScroll(step);
    }

    /** Interruptor: etiqueta y un pulsador a la derecha. Toda la fila lo cambia. */
    public Widget toggle(Rect row, String label, boolean on) {
        boolean hovered = hovered(row);
        fill(row, hovered ? CARD_HOVER : CARD);
        Rect track = new Rect(row.right() - 28, row.centerY() - 5, 22, 10);
        fill(track, on ? ACCENT : TRACK);
        Rect knob = on ? new Rect(track.right() - 9, track.y() + 1, 8, 8) : new Rect(track.x() + 1, track.y() + 1, 8, 8);
        fill(knob, on ? 0xFFFFFFFF : TEXT_SECONDARY);
        label(label, row.x() + 7, row.centerY() - 4, track.x() - row.x() - 12, on ? TEXT : TEXT_SECONDARY);
        return register(row);
    }

    private void stepButton(Rect rect, String glyph) {
        boolean hovered = hovered(rect);
        if (hovered) {
            fill(rect, CARD_ACTIVE);
        }
        centered(glyph, rect, hovered ? ACCENT_HOVER : TEXT_SECONDARY);
    }

    private static Rect intersection(Rect a, Rect b) {
        int left = Math.max(a.x(), b.x());
        int top = Math.max(a.y(), b.y());
        int right = Math.min(a.right(), b.right());
        int bottom = Math.min(a.bottom(), b.bottom());
        return new Rect(left, top, right - left, bottom - top);
    }

    /** Lo que necesita una fila numérica; la pantalla pone el valor y qué hacer con cada gesto. */
    public record Stepper(
            String label,
            String value,
            double fraction,
            boolean editing,
            boolean dragging,
            String input,
            IntConsumer step,
            Runnable press,
            Runnable reset,
            String help,
            String group,
            Object key
    ) {
    }

    /** Clic sobre una zona, con la posición para las filas que tienen varias partes. */
    public interface Click {
        void run(double mouseX, double mouseY);
    }

    /** Zona activa registrada en el último fotograma. */
    public static final class Widget {
        private final Rect bounds;
        private Rect valueZone;
        private Click click;
        private IntConsumer scroll;
        private Runnable reset;
        private String tooltip = "";
        private String group;
        private Object key;

        private Widget(Rect bounds) {
            this.bounds = bounds;
        }

        public Widget onClick(Runnable action) {
            this.click = (x, y) -> action.run();
            return this;
        }

        public Widget onClick(Click action) {
            this.click = action;
            return this;
        }

        public Widget onScroll(IntConsumer action) {
            this.scroll = action;
            return this;
        }

        public Widget onReset(Runnable action) {
            this.reset = action;
            return this;
        }

        public Widget tooltip(String text) {
            this.tooltip = text == null ? "" : text;
            return this;
        }

        public Widget group(String value) {
            this.group = value;
            return this;
        }

        public Widget key(Object value) {
            this.key = value;
            return this;
        }

        public Widget valueZone(Rect zone) {
            this.valueZone = zone;
            return this;
        }

        public Rect bounds() {
            return bounds;
        }

        public Click click() {
            return click;
        }

        public IntConsumer scroll() {
            return scroll;
        }

        public Runnable reset() {
            return reset;
        }

        public String tooltip() {
            return tooltip;
        }

        public String group() {
            return group;
        }

        public Object key() {
            return key;
        }

        /** Si el punto cae en los botones o el valor de la fila: ahí la rueda cambia el valor. */
        public boolean overValue(double x, double y) {
            return valueZone != null && valueZone.contains(x, y);
        }
    }
}
