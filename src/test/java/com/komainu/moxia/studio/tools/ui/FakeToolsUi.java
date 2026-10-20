package com.komainu.moxia.studio.tools.ui;

import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * {@link ToolsUi} de las pruebas: apunta cada zona con su texto para poder pulsarla («Construir», «Tejado»…) y, si
 * tiene imagen, pinta como {@code StudioUi} (mismas medidas y colores de {@code StudioTheme}) para las maquetas.
 */
final class FakeToolsUi implements ToolsUi {
    static final int PANEL = 0xFF101117;
    static final int SURFACE = 0xFF16171F;
    static final int CARD = 0xFF20222B;
    static final int CARD_HOVER = 0xFF2B2E39;
    static final int CARD_ACTIVE = 0xFF3A1E29;
    static final int FIELD = 0xFF0B0C10;
    static final int DIVIDER = 0xFF2A2C36;
    static final int TRACK = 0xFF2E303B;
    static final int ACCENT = 0xFFE24B6A;
    static final int ACCENT_HOVER = 0xFFF06684;
    static final int ACCENT_DIM = 0xFF7D2438;
    static final int SECTION = 0xFFE88EA2;
    static final int TEXT = 0xFFF4F0F3;
    static final int TEXT_SECONDARY = 0xFFBDB8C2;
    static final int MUTED = 0xFF8B8794;
    static final int DISABLED = 0xFF5E5B66;
    static final int SUCCESS = 0xFF67DFA3;
    static final int WARNING = 0xFFF4C86A;
    static final int DANGER = 0xFFFF6B6B;
    static final int DANGER_DIM = 0xFF4A1F24;
    static final int FAVORITE = 0xFFF2C14B;
    private static final int STEP_BUTTON = 16;
    private static final int VALUE_WIDTH = 34;

    /** Una zona registrada en el último dibujo, con el texto que la nombra. */
    static final class Recorded implements Zone {
        final Rect bounds;
        final String text;
        /** Los textos que se pintaron dentro antes de registrarla. */
        final List<String> texts = new ArrayList<>();
        Runnable click;
        Click clickAt;
        IntConsumer scroll;
        Runnable reset;
        String tooltip = "";
        String group;
        Object key;

        Recorded(Rect bounds, String text) {
            this.bounds = bounds;
            this.text = text == null ? "" : text;
        }

        @Override
        public Zone onClick(Runnable action) {
            click = action;
            return this;
        }

        @Override
        public Zone onClick(Click action) {
            clickAt = action;
            return this;
        }

        @Override
        public Zone onScroll(IntConsumer action) {
            scroll = action;
            return this;
        }

        @Override
        public Zone onReset(Runnable action) {
            reset = action;
            return this;
        }

        @Override
        public Zone tooltip(String text) {
            tooltip = text == null ? "" : text;
            return this;
        }

        @Override
        public Zone group(String value) {
            group = value;
            return this;
        }

        @Override
        public Zone key(Object value) {
            key = value;
            return this;
        }

        @Override
        public Zone valueZone(Rect zone) {
            return this;
        }

        /** Por su texto o por la primera línea de su ayuda (las celdas de herramienta se nombran ahí). */
        boolean matches(String label) {
            return text.equals(label) || texts.contains(label) || tooltip.split("\n", 2)[0].equals(label);
        }

        void press() {
            if (click != null) {
                click.run();
            } else if (clickAt != null) {
                clickAt.run(bounds.centerX(), bounds.centerY());
            }
        }

        @Override
        public String toString() {
            return "«" + text + "» " + bounds;
        }
    }

    private final BufferedImage image;
    private final int scale;
    private final MinecraftFont font;
    private final Deque<Rect> clips = new ArrayDeque<>();
    final List<Recorded> zones = new ArrayList<>();
    final List<String> stamps = new ArrayList<>();
    int mouseX = -1;
    int mouseY = -1;
    private final List<String> pendingTexts = new ArrayList<>();

    /** Sin imagen: solo apunta zonas (para las pruebas de comportamiento). */
    FakeToolsUi(MinecraftFont font) {
        this(null, 1, font);
    }

    FakeToolsUi(BufferedImage image, int scale, MinecraftFont font) {
        this.image = image;
        this.scale = scale;
        this.font = font;
    }

    void reset() {
        zones.clear();
        stamps.clear();
        clips.clear();
    }

    void pushClip(Rect clip) {
        clips.push(clip);
    }

    void popClip() {
        clips.pop();
    }

    /** La zona cuyo texto es {@code text} (la primera registrada si hay varias: la celda antes que su estrella). */
    Recorded zone(String text) {
        for (Recorded zone : zones) {
            if (zone.matches(text)) {
                return zone;
            }
        }
        throw new AssertionError("no hay ninguna zona «" + text + "»: " + zones.stream().map(zone -> zone.text)
                .filter(value -> !value.isEmpty()).toList());
    }

    /** La zona con ese texto dentro de un grupo de controles ({@code tools:stamp}…). */
    Recorded zone(String text, String group) {
        for (Recorded zone : zones) {
            if (group.equals(zone.group) && zone.matches(text)) {
                return zone;
            }
        }
        throw new AssertionError("no hay ninguna zona «" + text + "» en " + group);
    }

    boolean has(String text) {
        return zones.stream().anyMatch(zone -> zone.matches(text));
    }

    Recorded zoneWithKey(Object key) {
        for (int index = zones.size() - 1; index >= 0; index--) {
            if (key.equals(zones.get(index).key)) {
                return zones.get(index);
            }
        }
        throw new AssertionError("no hay ninguna zona con la clave " + key);
    }

    private static int color(Ink ink) {
        return switch (ink) {
            case TEXT -> TEXT;
            case TEXT_SECONDARY -> TEXT_SECONDARY;
            case MUTED -> MUTED;
            case DISABLED -> DISABLED;
            case ACCENT -> ACCENT;
            case ACCENT_HOVER -> ACCENT_HOVER;
            case ACCENT_DIM -> ACCENT_DIM;
            case SECTION -> SECTION;
            case SUCCESS -> SUCCESS;
            case WARNING -> WARNING;
            case DANGER -> DANGER;
            case DANGER_DIM -> DANGER_DIM;
            case FAVORITE -> FAVORITE;
            case CARD -> CARD;
            case CARD_HOVER -> CARD_HOVER;
            case CARD_ACTIVE -> CARD_ACTIVE;
            case FIELD -> FIELD;
            case DIVIDER -> DIVIDER;
            case TRACK -> TRACK;
            case SURFACE -> SURFACE;
            case WHITE -> 0xFFFFFFFF;
        };
    }

    // ── Medidas ─────────────────────────────────────────────────────────────────────────────────────────

    @Override
    public int width(String text) {
        return font.width(text);
    }

    @Override
    public String fit(String text, int maximumWidth) {
        if (font.width(text) <= maximumWidth) {
            return text;
        }
        int limit = Math.max(0, maximumWidth - font.width("…"));
        StringBuilder builder = new StringBuilder();
        for (int code : text.codePoints().toArray()) {
            String next = builder + new String(Character.toChars(code));
            if (font.width(next) > limit) {
                break;
            }
            builder.appendCodePoint(code);
        }
        return builder + "…";
    }

    @Override
    public List<String> wrap(String text, int maximumWidth) {
        int width = Math.max(20, maximumWidth);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (font.width(candidate) <= width || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
                while (font.width(line.toString()) > width && line.length() > 1) {
                    // Una palabra más larga que la línea se parte, como hace el juego.
                    int cut = line.length() - 1;
                    while (cut > 1 && font.width(line.substring(0, cut)) > width) {
                        cut--;
                    }
                    lines.add(line.substring(0, cut));
                    line.delete(0, cut);
                }
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    @Override
    public boolean hovered(Rect rect) {
        return rect.contains(mouseX, mouseY) && (clips.isEmpty() || clips.peek().contains(mouseX, mouseY));
    }

    @Override
    public boolean visible(Rect rect) {
        return clips.isEmpty() || rect.intersects(clips.peek());
    }

    // ── Pintar ──────────────────────────────────────────────────────────────────────────────────────────

    private Rectangle clip() {
        Rect clip = clips.peek();
        return clip == null ? null : new Rectangle(clip.x(), clip.y(), clip.width(), clip.height());
    }

    void fillArgb(Rect rect, int argb) {
        if (image == null || rect.isEmpty()) {
            return;
        }
        Rect clip = clips.peek();
        int left = rect.x();
        int top = rect.y();
        int right = rect.right();
        int bottom = rect.bottom();
        if (clip != null) {
            left = Math.max(left, clip.x());
            top = Math.max(top, clip.y());
            right = Math.min(right, clip.right());
            bottom = Math.min(bottom, clip.bottom());
        }
        int alpha = argb >>> 24;
        for (int y = top * scale; y < bottom * scale; y++) {
            for (int x = left * scale; x < right * scale; x++) {
                if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
                    continue;
                }
                image.setRGB(x, y, alpha == 255 ? argb : blend(image.getRGB(x, y), argb));
            }
        }
    }

    private static int blend(int under, int over) {
        double alpha = (over >>> 24) / 255.0;
        int red = (int) Math.round(((over >> 16) & 0xFF) * alpha + ((under >> 16) & 0xFF) * (1 - alpha));
        int green = (int) Math.round(((over >> 8) & 0xFF) * alpha + ((under >> 8) & 0xFF) * (1 - alpha));
        int blue = (int) Math.round((over & 0xFF) * alpha + (under & 0xFF) * (1 - alpha));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    void text(String value, int x, int y, int argb, boolean bold) {
        pendingTexts.add(value);
        if (image != null) {
            font.draw(image, value, x, y, argb, bold, scale, clip());
        }
    }

    @Override
    public void fill(Rect rect, Ink ink) {
        fillArgb(rect, color(ink));
    }

    @Override
    public void fill(Rect rect, int argb) {
        fillArgb(rect, argb);
    }

    void outline(Rect rect, int argb) {
        fillArgb(new Rect(rect.x(), rect.y(), rect.width(), 1), argb);
        fillArgb(new Rect(rect.x(), rect.bottom() - 1, rect.width(), 1), argb);
        fillArgb(new Rect(rect.x(), rect.y(), 1, rect.height()), argb);
        fillArgb(new Rect(rect.right() - 1, rect.y(), 1, rect.height()), argb);
    }

    @Override
    public void border(Rect rect, Ink ink) {
        outline(rect, color(ink));
    }

    @Override
    public void label(String text, int x, int y, int maximumWidth, Ink ink) {
        if (maximumWidth > 0) {
            text(fit(text, maximumWidth), x, y, color(ink), false);
        }
    }

    @Override
    public void bold(String text, int x, int y, int maximumWidth, Ink ink) {
        text(fit(text, maximumWidth - 1), x, y, color(ink), true);
    }

    @Override
    public void centered(String text, Rect rect, Ink ink) {
        centered(text, rect, color(ink));
    }

    void centered(String text, Rect rect, int argb) {
        String fitted = fit(text, rect.width() - 8);
        text(fitted, rect.centerX() - font.width(fitted) / 2, rect.centerY() - 4, argb, false);
    }

    @Override
    public void item(String blockId, int x, int y) {
        // Un cuadrado con el color del bloque «a ojo» basta para ver dónde va el icono.
        int hash = blockId.hashCode();
        int argb = 0xFF000000 | (0x50 + (hash & 0x7F)) << 16 | (0x50 + (hash >> 7 & 0x7F)) << 8
                | (0x50 + (hash >> 14 & 0x7F));
        fillArgb(new Rect(x + 1, y + 1, 14, 14), argb);
        outline(new Rect(x + 1, y + 1, 14, 14), 0x66000000);
        fillArgb(new Rect(x + 1, y + 1, 14, 2), 0x33FFFFFF);
    }

    @Override
    public void stamp(String name, Rect rect) {
        stamps.add(name);
        if (image == null) {
            return;
        }
        int size = rect.width() * scale;
        int[] pixels = StampPreview.argb(name, size);
        Rect clip = clips.peek();
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int x = rect.x() * scale + column;
                int y = rect.y() * scale + row;
                if (clip != null && !clip.contains((double) x / scale, (double) y / scale)) {
                    continue;
                }
                if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
                    image.setRGB(x, y, pixels[row * size + column]);
                }
            }
        }
    }

    @Override
    public void lock(int x, int y, Ink ink) {
        int argb = color(ink);
        fillArgb(new Rect(x + 2, y, 4, 1), argb);
        fillArgb(new Rect(x + 1, y + 1, 1, 3), argb);
        fillArgb(new Rect(x + 6, y + 1, 1, 3), argb);
        fillArgb(new Rect(x, y + 4, 8, 5), argb);
        fillArgb(new Rect(x + 3, y + 5, 2, 2), CARD);
    }

    @Override
    public Zone register(Rect rect) {
        return record(rect, pendingTexts.isEmpty() ? "" : pendingTexts.getFirst());
    }

    private Recorded record(Rect rect, String text) {
        Rect visible = rect;
        Rect clip = clips.peek();
        if (clip != null) {
            int left = Math.max(rect.x(), clip.x());
            int top = Math.max(rect.y(), clip.y());
            int right = Math.min(rect.right(), clip.right());
            int bottom = Math.min(rect.bottom(), clip.bottom());
            visible = new Rect(left, top, right - left, bottom - top);
        }
        Recorded zone = new Recorded(visible, text);
        zone.texts.addAll(pendingTexts);
        if (!visible.isEmpty()) {
            zones.add(zone);
        }
        pendingTexts.clear();
        return zone;
    }

    // ── Componentes (como StudioUi) ─────────────────────────────────────────────────────────────────────

    @Override
    public void section(Column column, String title) {
        column.section();
        Rect row = column.next(11);
        String fitted = fit(title, row.width() - 12);
        text(fitted, row.x(), row.y() + 1, SECTION, false);
        int lineX = row.x() + font.width(fitted) + 6;
        if (lineX < row.right()) {
            fillArgb(new Rect(lineX, row.y() + 5, row.right() - lineX, 1), DIVIDER);
        }
        pendingTexts.clear();
    }

    @Override
    public void paragraph(Column column, String text, Ink ink, int maximumLines) {
        List<String> lines = wrap(text, column.width());
        if (maximumLines > 0 && lines.size() > maximumLines) {
            lines = lines.subList(0, maximumLines);
        }
        Rect area = column.next(lines.size() * 10);
        for (int index = 0; index < lines.size(); index++) {
            text(lines.get(index), area.x(), area.y() + index * 10, color(ink), false);
        }
        pendingTexts.clear();
    }

    @Override
    public Zone button(Rect rect, String label, Tone tone) {
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
            case SELECTED, SECONDARY -> TEXT;
            case DANGER -> DANGER;
            case WARNING -> WARNING;
            case DISABLED -> DISABLED;
        };
        fillArgb(rect, background);
        if (tone == Tone.SELECTED) {
            outline(rect, ACCENT_DIM);
            fillArgb(new Rect(rect.x(), rect.bottom() - 2, rect.width(), 2), ACCENT);
        }
        centered(label, rect, color);
        return record(rect, label);
    }

    @Override
    public Zone chip(Rect rect, String label, boolean selected, Ink ink) {
        boolean hovered = hovered(rect);
        fillArgb(rect, selected ? ACCENT_DIM : hovered ? CARD_HOVER : CARD);
        centered(label, rect, selected ? 0xFFFFFFFF : color(ink));
        return record(rect, label);
    }

    @Override
    public int chipWidth(String label) {
        return font.width(label) + 12;
    }

    @Override
    public Zone field(Rect rect, String value, String placeholder, boolean editing, boolean error) {
        boolean hovered = hovered(rect);
        fillArgb(rect, FIELD);
        outline(rect, editing ? (error ? WARNING : ACCENT) : hovered ? 0xFF4A4C58 : DIVIDER);
        int inner = rect.width() - 12;
        int y = rect.centerY() - 4;
        if (editing) {
            String shown = value + "_";
            while (font.width(shown) > inner && shown.length() > 1) {
                shown = shown.substring(1);
            }
            text(shown, rect.x() + 6, y, TEXT, false);
        } else if (value.isEmpty()) {
            text(fit(placeholder, inner), rect.x() + 6, y, MUTED, false);
        } else {
            text(fit(value, inner), rect.x() + 6, y, TEXT, false);
        }
        return record(rect, value.isEmpty() ? placeholder : value);
    }

    @Override
    public Zone listItem(Rect rect, String title, String subtitle, boolean selected, int rightReserve) {
        boolean hovered = hovered(rect);
        fillArgb(rect, selected ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
        if (selected) {
            fillArgb(rect.left(2), ACCENT);
        }
        int textWidth = rect.width() - 16 - rightReserve;
        text(fit(title, textWidth), rect.x() + 8, rect.y() + 5, selected ? ACCENT_HOVER : TEXT, false);
        text(fit(subtitle, textWidth), rect.x() + 8, rect.y() + 16, MUTED, false);
        return record(rect, title);
    }

    @Override
    public Zone stepper(Rect row, Stepper model) {
        boolean hovered = hovered(row);
        fillArgb(row, hovered ? CARD_HOVER : CARD);
        int barY = row.bottom() - 2;
        fillArgb(new Rect(row.x(), barY, row.width(), 2), TRACK);
        fillArgb(new Rect(row.x(), barY, (int) Math.round(row.width() * Math.clamp(model.fraction(), 0.0, 1.0)), 2),
                ACCENT_DIM);
        Rect plus = new Rect(row.right() - 2 - STEP_BUTTON, row.y() + 2, STEP_BUTTON, row.height() - 6);
        Rect value = new Rect(plus.x() - VALUE_WIDTH, plus.y(), VALUE_WIDTH, plus.height());
        Rect minus = new Rect(value.x() - STEP_BUTTON, plus.y(), STEP_BUTTON, plus.height());
        text(fit(model.label(), minus.x() - row.x() - 12), row.x() + 7, row.centerY() - 5, TEXT_SECONDARY, false);
        centered("−", minus, TEXT_SECONDARY);
        centered("+", plus, TEXT_SECONDARY);
        if (model.editing()) {
            fillArgb(value, FIELD);
            outline(value, ACCENT);
        }
        centered(model.editing() ? model.input() + "_" : model.value(), value, model.editing() ? TEXT : ACCENT_HOVER);
        Recorded zone = record(row, model.label());
        zone.onClick((x, y) -> {
            if (minus.contains(x, y)) {
                model.step().accept(-1);
            } else if (plus.contains(x, y)) {
                model.step().accept(1);
            } else if (value.contains(x, y)) {
                model.press().run();
            }
        });
        zone.onScroll(model.step()).onReset(model.reset()).tooltip(model.help()).group(model.group())
                .key(model.key());
        return zone;
    }

    @Override
    public Zone cycle(Rect row, String label, String value, IntConsumer step) {
        boolean hovered = hovered(row);
        fillArgb(row, hovered ? CARD_HOVER : CARD);
        int valueWidth = Math.min(Math.max(90, row.width() * 3 / 5), row.width() - 60);
        Rect next = new Rect(row.right() - 2 - STEP_BUTTON, row.y() + 2, STEP_BUTTON, row.height() - 4);
        Rect shown = new Rect(next.x() - (valueWidth - 2 * STEP_BUTTON), next.y(), valueWidth - 2 * STEP_BUTTON,
                next.height());
        Rect previous = new Rect(shown.x() - STEP_BUTTON, next.y(), STEP_BUTTON, next.height());
        text(fit(label, previous.x() - row.x() - 12), row.x() + 7, row.centerY() - 4, TEXT_SECONDARY, false);
        centered("‹", previous, TEXT_SECONDARY);
        centered("›", next, TEXT_SECONDARY);
        centered(value, shown, TEXT);
        Recorded zone = record(row, label);
        zone.onClick((x, y) -> step.accept(previous.contains(x, y) ? -1 : 1));
        zone.onScroll(step);
        return zone;
    }

    @Override
    public Zone toggle(Rect row, String label, boolean on) {
        boolean hovered = hovered(row);
        fillArgb(row, hovered ? CARD_HOVER : CARD);
        Rect track = new Rect(row.right() - 28, row.centerY() - 5, 22, 10);
        fillArgb(track, on ? ACCENT : TRACK);
        Rect knob = on ? new Rect(track.right() - 9, track.y() + 1, 8, 8) : new Rect(track.x() + 1, track.y() + 1, 8, 8);
        fillArgb(knob, on ? 0xFFFFFFFF : TEXT_SECONDARY);
        text(fit(label, track.x() - row.x() - 12), row.x() + 7, row.centerY() - 4, on ? TEXT : TEXT_SECONDARY, false);
        return record(row, label);
    }
}
