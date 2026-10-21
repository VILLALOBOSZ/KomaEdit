package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.layout.Column;
import com.komainu.moxia.studio.layout.Rect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

import static com.komainu.moxia.studio.ui.StudioTheme.*;

/**
 * Componentes del Studio en modo inmediato: cada control se dibuja y, en la misma llamada, deja registrada su
 * zona con lo que hace al pulsarlo, al girar la rueda o al restablecerlo con clic derecho. La pantalla solo
 * pregunta qué hay bajo el ratón, así que dibujo y clic no pueden desalinearse y añadir un control es una línea.
 *
 * <p>Pinta sobre un {@link StudioCanvas}: el juego y las maquetas usan esta misma clase.</p>
 */
public final class StudioUi {
    public static final int ROW = Column.ROW;
    public static final int SMALL_ROW = 16;
    public static final int LINE = 10;
    private static final int STEP_BUTTON = 14;
    private static final int TOOLTIP_WIDTH = 200;
    private static final String ELLIPSIS = "…";

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

    private final StudioCanvas canvas;
    private final List<Widget> widgets = new ArrayList<>();
    private final Deque<Rect> clips = new ArrayDeque<>();
    private final Map<String, Anchor> anchors = new LinkedHashMap<>();
    private int mouseX = -1;
    private int mouseY = -1;
    private boolean hoverEnabled = true;

    public StudioUi(StudioCanvas canvas) {
        this.canvas = canvas;
    }

    public void begin(int mouseX, int mouseY, boolean hoverEnabled) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.hoverEnabled = hoverEnabled;
        widgets.clear();
        clips.clear();
        anchors.clear();
    }

    /** Desde aquí los controles responden al ratón (tras dibujar el fondo de una ventana modal). */
    public void enableHover() {
        hoverEnabled = true;
    }

    /** Hasta {@link #enableHover()} nada se resalta (lo que queda debajo de una lista desplegada). */
    public void disableHover() {
        hoverEnabled = false;
    }

    public StudioCanvas canvas() {
        return canvas;
    }

    public int mouseX() {
        return mouseX;
    }

    public int mouseY() {
        return mouseY;
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

    /** Las zonas del último fotograma, en el orden en que se registraron. */
    public List<Widget> widgets() {
        return Collections.unmodifiableList(widgets);
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

    /** Una zona con nombre: la entera ({@code bounds}) y lo que se ve de ella con el recorte de ahora. */
    public record Anchor(Rect bounds, Rect visible) {
    }

    /**
     * Pone nombre a una zona del fotograma ({@code tool:tree}, {@code footer:build}…), aunque el recorte la tape: la
     * bienvenida guiada señala con ellas y desplaza el panel hasta la que no se ve.
     */
    public void anchor(String id, Rect bounds) {
        anchors.put(id, new Anchor(bounds, clips.isEmpty() ? bounds : intersection(bounds, clips.peek())));
    }

    /** La zona con ese nombre en el último fotograma, o {@code null}. */
    public Anchor anchor(String id) {
        return anchors.get(id);
    }

    /** El nombre de la última zona que se ve bajo el punto, o {@code null}. */
    public String anchorAt(double x, double y) {
        String found = null;
        for (Map.Entry<String, Anchor> entry : anchors.entrySet()) {
            if (entry.getValue().visible().contains(x, y)) {
                found = entry.getKey();
            }
        }
        return found;
    }

    public void pushClip(Rect clip) {
        Rect effective = clips.isEmpty() ? clip : intersection(clip, clips.peek());
        clips.push(effective);
        canvas.pushClip(effective);
    }

    public void popClip() {
        clips.pop();
        canvas.popClip();
    }

    /** Si el ratón está sobre {@code rect} y se ve (dentro del recorte y sin ventana modal encima). */
    public boolean hovered(Rect rect) {
        return hoverEnabled && rect.contains(mouseX, mouseY)
                && (clips.isEmpty() || clips.peek().contains(mouseX, mouseY));
    }

    /** Si {@code rect} se ve aunque sea en parte; sirve para no dibujar listas largas enteras. */
    public boolean visible(Rect rect) {
        return clips.isEmpty() || rect.intersects(clips.peek());
    }

    // ── Primitivas ──────────────────────────────────────────────────────────────────────────────────────

    public void fill(Rect rect, int color) {
        canvas.fill(rect, color);
    }

    public void border(Rect rect, int color) {
        fill(new Rect(rect.x(), rect.y(), rect.width(), 1), color);
        fill(new Rect(rect.x(), rect.bottom() - 1, rect.width(), 1), color);
        fill(new Rect(rect.x(), rect.y() + 1, 1, rect.height() - 2), color);
        fill(new Rect(rect.right() - 1, rect.y() + 1, 1, rect.height() - 2), color);
    }

    public int width(String value) {
        return canvas.width(value);
    }

    public int boldWidth(String value) {
        return canvas.boldWidth(value);
    }

    public void label(String value, int x, int y, int color) {
        canvas.text(value, x, y, color);
    }

    /** Texto recortado con «…» si no cabe en {@code maximumWidth}. */
    public void label(String value, int x, int y, int maximumWidth, int color) {
        if (maximumWidth > 0) {
            canvas.text(fit(value, maximumWidth), x, y, color);
        }
    }

    public void bold(String value, int x, int y, int maximumWidth, int color) {
        if (maximumWidth <= 0) {
            return;
        }
        String fitted = value;
        if (canvas.boldWidth(value) > maximumWidth) {
            int limit = Math.max(0, maximumWidth - canvas.boldWidth(ELLIPSIS));
            fitted = trim(value, limit, true) + ELLIPSIS;
        }
        canvas.bold(fitted, x, y, color);
    }

    public void centered(String value, Rect rect, int color) {
        String fitted = fit(value, rect.width() - 8);
        canvas.text(fitted, rect.centerX() - canvas.width(fitted) / 2, rect.centerY() - 4, color);
    }

    public void rightAligned(String value, int right, int y, int color) {
        canvas.text(value, right - canvas.width(value), y, color);
    }

    public String fit(String value, int maximumWidth) {
        if (canvas.width(value) <= maximumWidth) {
            return value;
        }
        return trim(value, Math.max(0, maximumWidth - canvas.width(ELLIPSIS)), false) + ELLIPSIS;
    }

    /** El principio más largo de {@code value} que cabe en {@code width}. */
    private String trim(String value, int width, boolean bold) {
        int[] codes = value.codePoints().toArray();
        int low = 0;
        int high = codes.length;
        while (low < high) {
            int middle = (low + high + 1) / 2;
            String candidate = new String(codes, 0, middle);
            int measured = bold ? canvas.boldWidth(candidate) : canvas.width(candidate);
            if (measured <= width) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return new String(codes, 0, low).stripTrailing();
    }

    /** Párrafo partido por palabras al ancho dado; los saltos de línea se respetan. */
    public List<String> wrap(String value, int maximumWidth) {
        int width = Math.max(20, maximumWidth);
        List<String> lines = new ArrayList<>();
        for (String paragraph : value.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (canvas.width(candidate) <= width) {
                    line.setLength(0);
                    line.append(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                String rest = word;
                while (canvas.width(rest) > width && rest.length() > 1) {
                    // Una palabra más larga que la línea se parte, como hace el juego.
                    String head = trim(rest, width, false);
                    if (head.isEmpty()) {
                        head = rest.substring(0, 1);
                    }
                    lines.add(head);
                    rest = rest.substring(head.length());
                }
                line.append(rest);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    public void icon(StudioIcons.Icon icon, int x, int y, int color) {
        StudioIcons.draw(canvas, icon, x, y, color);
    }

    public void icon(StudioIcons.Icon icon, int x, int y, int color, int scale) {
        StudioIcons.draw(canvas, icon, x, y, color, scale);
    }

    /** Icono de 16×16 del bloque u objeto {@code id}. */
    public void item(String id, int x, int y) {
        canvas.item(id, x, y);
    }

    public void image(StudioImage image, Rect target) {
        canvas.image(image, target);
    }

    // ── Componentes sueltos ─────────────────────────────────────────────────────────────────────────────

    public Widget button(Rect rect, String label, Tone tone) {
        boolean hovered = hovered(rect);
        int color = switch (tone) {
            case PRIMARY -> ON_ACCENT;
            case SELECTED -> TEXT_STRONG;
            case DANGER -> DANGER;
            case WARNING -> WARNING;
            case DISABLED -> DISABLED;
            case SECONDARY -> TEXT;
        };
        if (tone == Tone.DISABLED) {
            // Desactivado: hueco, solo el contorno; así no se confunde con uno normal aunque no se distinga el color.
            border(rect, hovered ? OUTLINE : DIVIDER);
        } else {
            fill(rect, switch (tone) {
                case PRIMARY -> hovered ? ACCENT_HOVER : ACCENT;
                case SELECTED -> CARD_ACTIVE;
                case DANGER -> hovered ? DANGER_DIM : CARD;
                case WARNING -> hovered ? WARNING_HOVER : WARNING_DIM;
                default -> hovered ? CARD_HOVER : CARD;
            });
            if (tone == Tone.SELECTED) {
                fill(new Rect(rect.x(), rect.bottom() - 2, rect.width(), 2), ACCENT);
            } else if (hovered && tone != Tone.PRIMARY) {
                border(rect, OUTLINE);
            }
        }
        centered(label, rect, color);
        return register(rect);
    }

    /** Botón con icono a la izquierda y texto; el texto se recorta antes que el icono. */
    public Widget iconButton(Rect rect, StudioIcons.Icon icon, String label, Tone tone) {
        Widget widget = button(rect, "", tone);
        boolean hovered = hovered(rect);
        int color = switch (tone) {
            case PRIMARY -> ON_ACCENT;
            case DISABLED -> DISABLED;
            case DANGER -> DANGER;
            case WARNING -> WARNING;
            default -> hovered ? TEXT : TEXT_SECONDARY;
        };
        int textWidth = label.isEmpty() ? 0 : Math.min(width(label), rect.width() - StudioIcons.SIZE - 12);
        int contentWidth = StudioIcons.SIZE + (label.isEmpty() ? 0 : 4 + textWidth);
        int x = rect.centerX() - contentWidth / 2;
        icon(icon, x, rect.centerY() - StudioIcons.SIZE / 2, color);
        if (!label.isEmpty()) {
            label(label, x + StudioIcons.SIZE + 4, rect.centerY() - 4, textWidth, tone == Tone.PRIMARY ? ON_ACCENT
                    : tone == Tone.DISABLED ? DISABLED : TEXT);
        }
        return widget;
    }

    /** Botón cuadrado con icono para la barra de herramientas. */
    public Widget toolButton(Rect rect, StudioIcons.Icon icon, boolean active, boolean enabled) {
        boolean hovered = hovered(rect) && enabled;
        fill(rect, active ? CARD_ACTIVE : hovered ? CARD_HOVER : SURFACE);
        if (active) {
            fill(new Rect(rect.x(), rect.bottom() - 2, rect.width(), 2), ACCENT);
        }
        int color = !enabled ? DISABLED : active ? ACCENT_TEXT : hovered ? TEXT : TEXT_SECONDARY;
        icon(icon, rect.centerX() - StudioIcons.SIZE / 2, rect.centerY() - StudioIcons.SIZE / 2, color);
        return register(rect);
    }

    /** Pastilla pequeña para filtros, etiquetas y presets. */
    public Widget chip(Rect rect, String label, boolean selected, int color) {
        boolean hovered = hovered(rect);
        fill(rect, selected ? ACCENT_DIM : hovered ? CARD_HOVER : CARD);
        if (selected) {
            fill(new Rect(rect.x(), rect.bottom() - 2, rect.width(), 2), ACCENT);
        } else if (hovered) {
            border(rect, OUTLINE);
        }
        centered(label, rect, selected ? TEXT_STRONG : hovered ? TEXT : color);
        return register(rect);
    }

    /** Ancho natural de una pastilla con {@code label}. */
    public int chipWidth(String label) {
        return width(label) + 12;
    }

    /** Campo de texto: el valor (o el texto de ejemplo en gris) y el cursor mientras se escribe. */
    public Widget field(Rect rect, String value, String placeholder, boolean editing, boolean error) {
        boolean hovered = hovered(rect);
        fill(rect, FIELD);
        border(rect, editing ? (error ? WARNING : FOCUS) : hovered ? OUTLINE : DIVIDER);
        int inner = rect.width() - 12;
        int y = rect.centerY() - 4;
        if (editing) {
            String shown = value + "_";
            while (width(shown) > inner && shown.length() > 1) {
                shown = shown.substring(1);
            }
            label(shown, rect.x() + 6, y, TEXT);
        } else if (value.isEmpty()) {
            label(placeholder, rect.x() + 6, y, inner, MUTED);
        } else {
            label(value, rect.x() + 6, y, inner, TEXT);
        }
        return register(rect);
    }

    /** Campo de búsqueda: lupa, texto y, si hay algo escrito, cuántos resultados y una × para borrar. */
    public Widget searchField(Rect rect, String value, String placeholder, boolean editing, int results,
                              Runnable clear) {
        boolean hovered = hovered(rect);
        fill(rect, FIELD);
        border(rect, editing ? FOCUS : hovered ? OUTLINE : DIVIDER);
        icon(StudioIcons.Icon.SEARCH, rect.x() + 4, rect.centerY() - 6, editing ? TEXT_SECONDARY : MUTED);
        int textX = rect.x() + 20;
        int right = rect.right() - 4;
        Rect clearZone = Rect.EMPTY;
        if (!value.isEmpty()) {
            clearZone = new Rect(rect.right() - 16, rect.y(), 16, rect.height());
            centered("×", clearZone, hovered(clearZone) ? TEXT : MUTED);
            String count = Integer.toString(results);
            right = clearZone.x() - 2;
            rightAligned(count, right, rect.centerY() - 4, MUTED);
            right -= width(count) + 4;
        }
        int inner = right - textX;
        int y = rect.centerY() - 4;
        if (editing) {
            String shown = value + "_";
            while (width(shown) > inner && shown.length() > 1) {
                shown = shown.substring(1);
            }
            label(shown, textX, y, TEXT);
        } else if (value.isEmpty()) {
            label(placeholder, textX, y, inner, MUTED);
        } else {
            label(value, textX, y, inner, TEXT);
        }
        Widget widget = register(rect);
        if (!clearZone.isEmpty()) {
            register(clearZone).onClick(clear);
        }
        return widget;
    }

    /** Tarjeta de lista: título y subtítulo, resaltada si está elegida. */
    public Widget listItem(Rect rect, String title, String subtitle, boolean selected, int rightReserve) {
        boolean hovered = hovered(rect);
        fill(rect, selected ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
        if (selected) {
            fill(rect.left(2), ACCENT);
        }
        int textWidth = rect.width() - 16 - rightReserve;
        label(title, rect.x() + 8, rect.y() + 5, textWidth, selected ? TEXT_STRONG : TEXT);
        label(subtitle, rect.x() + 8, rect.y() + 16, textWidth, MUTED);
        return register(rect);
    }

    public void card(Rect rect) {
        fill(rect, CARD);
    }

    /** Alto de cada línea del nombre bajo una miniatura. */
    public static final int TILE_LINE = 9;

    /**
     * Miniatura con su nombre debajo, en una o más líneas ({@link #nameLines}), para elegir lo que se entiende mejor
     * viéndolo (especies, tejados, sellos). La imagen se ajusta al hueco que queda sobre el nombre; sin imagen, el
     * icono de reserva. La elegida lleva marco de acento y el nombre resaltado.
     */
    public Widget tile(Rect rect, StudioImage image, StudioIcons.Icon fallback, List<String> lines, boolean selected) {
        boolean hovered = hovered(rect);
        fill(rect, selected ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
        int caption = lines.isEmpty() ? 0 : lines.size() * TILE_LINE + 2;
        Rect picture = new Rect(rect.x() + 2, rect.y() + 2, rect.width() - 4, rect.height() - 4 - caption);
        if (image != null) {
            fill(picture, THUMBNAIL);
            int side = Math.min(picture.width(), picture.height());
            image(image, new Rect(picture.centerX() - side / 2, picture.centerY() - side / 2, side, side));
        } else if (fallback != null) {
            int scale = picture.height() >= 28 ? 2 : 1;
            int size = StudioIcons.SIZE * scale;
            icon(fallback, picture.centerX() - size / 2, picture.centerY() - size / 2,
                    selected ? ACCENT_TEXT : TEXT_SECONDARY, scale);
        }
        int y = rect.bottom() - caption;
        for (String line : lines) {
            String fitted = fit(line, rect.width() - 2);
            label(fitted, rect.centerX() - width(fitted) / 2, y, selected ? TEXT_STRONG : hovered ? TEXT : TEXT_SECONDARY);
            y += TILE_LINE;
        }
        if (selected) {
            border(rect, ACCENT);
            border(rect.inset(1, 1), ACCENT);
        } else if (hovered) {
            border(rect, OUTLINE);
        }
        return register(rect);
    }

    /** Alto de una celda de cuadrícula con el nombre en una línea y en dos. */
    public static final int GRID_CELL = 18;
    public static final int GRID_CELL_TALL = 22;

    /** Columnas de una cuadrícula de nombres y si sus celdas necesitan dos líneas. */
    public record NameGrid(int columns, boolean tall) {
        public int cellHeight() {
            return tall ? GRID_CELL_TALL : GRID_CELL;
        }
    }

    /**
     * Las más columnas (hasta {@code maxColumns}) con las que cada nombre cabe entero en dos líneas como mucho en una
     * celda de la que {@code inset} píxeles no son para el nombre (icono, estrella).
     */
    public NameGrid nameGrid(List<String> names, int width, int maxColumns, int inset) {
        for (int columns = Math.max(1, maxColumns); columns >= 1; columns--) {
            int nameWidth = (width - (columns - 1) * Column.GAP) / columns - inset;
            boolean tall = false;
            boolean fits = true;
            for (String name : names) {
                if (canvas.width(name) <= nameWidth) {
                    continue;
                }
                List<String> lines = nameLines(name, nameWidth, 2);
                if (lines == null) {
                    fits = false;
                    break;
                }
                tall |= lines.size() > 1;
            }
            if (fits || columns == 1) {
                return new NameGrid(columns, tall);
            }
        }
        return new NameGrid(1, false);
    }

    /**
     * Un nombre centrado en vertical en {@code row}: en una línea si cabe, si no en dos partidas por palabras, y solo
     * si ni así cabe, cortado con «…».
     */
    public void nameLabel(String text, int x, Rect row, int width, int color) {
        if (canvas.width(text) <= width) {
            label(text, x, row.centerY() - 4, width, color);
            return;
        }
        List<String> lines = nameLines(text, width, 2);
        if (lines == null || lines.size() < 2 || row.height() < GRID_CELL_TALL - 2) {
            label(text, x, row.centerY() - 4, width, color);
            return;
        }
        label(lines.get(0), x, row.centerY() - 9, width, color);
        label(lines.get(1), x, row.centerY(), width, color);
    }

    /**
     * El nombre de una miniatura partido por palabras en {@code width}, sin cortar ninguna: {@code null} si una
     * palabra no cabe entera o hacen falta más de {@code maxLines} líneas.
     */
    public List<String> nameLines(String value, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.strip().split("\\s+")) {
            if (canvas.width(word) > width) {
                return null;
            }
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (canvas.width(candidate) <= width) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            lines.add(line.toString());
            line.setLength(0);
            line.append(word);
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines.size() <= maxLines ? lines : null;
    }

    // ── Componentes de columna ──────────────────────────────────────────────────────────────────────────

    /** Título de sección: gris, en mayúsculas y con una línea fina a la derecha. */
    public void section(Column column, String title) {
        section(column, title, "");
    }

    /** Título de sección con un dato a la derecha (cuántos hay, cuántos cambiados). */
    public void section(Column column, String title, String detail) {
        column.section();
        Rect row = column.next(10);
        drawSectionTitle(row, title, detail, null);
    }

    /**
     * Sección que se pliega: el título con su flecha y, a la derecha, {@code detail}. Devuelve la zona del título;
     * el que llama decide qué hacer al pulsarla.
     */
    public Widget foldable(Column column, String title, String detail, boolean open) {
        column.section();
        Rect row = column.next(12);
        boolean hovered = hovered(row);
        if (hovered) {
            fill(row, CARD);
        }
        drawSectionTitle(new Rect(row.x(), row.y() + 1, row.width(), 10), title, detail,
                open ? StudioIcons.Icon.CHEVRON_DOWN : StudioIcons.Icon.CHEVRON_RIGHT);
        return register(row);
    }

    private void drawSectionTitle(Rect row, String title, String detail, StudioIcons.Icon chevron) {
        int x = row.x();
        if (chevron != null) {
            icon(chevron, x - 3, row.y() - 2, MUTED);
            x += 9;
        }
        String upper = title.toUpperCase(Locale.ROOT);
        int detailWidth = detail.isEmpty() ? 0 : width(detail) + 6;
        String fitted = fit(upper, row.right() - x - detailWidth - 4);
        label(fitted, x, row.y() + 1, MUTED);
        int lineX = x + width(fitted) + 5;
        int lineRight = row.right() - detailWidth;
        if (lineX < lineRight) {
            fill(new Rect(lineX, row.y() + 4, lineRight - lineX, 1), DIVIDER);
        }
        if (!detail.isEmpty()) {
            rightAligned(detail, row.right(), row.y() + 1, MUTED);
        }
    }

    /** Párrafo envuelto al ancho de la columna; {@code maximumLines} 0 = sin límite. */
    public void paragraph(Column column, String value, int color, int maximumLines) {
        List<String> lines = wrap(value, column.width());
        if (maximumLines > 0 && lines.size() > maximumLines) {
            lines = new ArrayList<>(lines.subList(0, maximumLines));
            int last = lines.size() - 1;
            lines.set(last, fit(lines.get(last) + ELLIPSIS, column.width()));
        }
        Rect area = column.next(lines.size() * LINE);
        for (int index = 0; index < lines.size(); index++) {
            label(lines.get(index), area.x(), area.y() + index * LINE, color);
        }
    }

    /**
     * Fila numérica: etiqueta a la izquierda, − valor con su unidad + a la derecha, y debajo una barra fina con
     * el rango (lleno hasta el valor y una marca en el de serie). Un punto de acento delante de la etiqueta dice que
     * el valor ya no es el de serie. Rueda o − + cambian el valor; arrastrar el número lo desliza (Mayús: fino),
     * un clic deja escribirlo y el clic derecho lo devuelve al de serie.
     */
    public Widget stepper(Rect row, Stepper model) {
        boolean hovered = hovered(row);
        fill(row, hovered ? CARD_HOVER : CARD);
        int barY = row.bottom() - 2;
        fill(new Rect(row.x(), barY, row.width(), 2), TRACK);
        int filled = (int) Math.round(row.width() * Math.clamp(model.fraction(), 0.0, 1.0));
        fill(new Rect(row.x(), barY, filled, 2), model.modified() ? ACCENT : ACCENT_DIM);
        if (!Double.isNaN(model.defaultFraction())) {
            int tick = row.x() + (int) Math.round((row.width() - 1) * Math.clamp(model.defaultFraction(), 0.0, 1.0));
            fill(new Rect(tick, barY - 2, 1, 4), TEXT_SECONDARY);
        }

        String shown = model.editing() ? model.input() + "_" : model.value();
        // Si la etiqueta y el valor con su unidad no caben, la unidad va en corto («18 bl.»).
        int room = row.width() - 2 * STEP_BUTTON - 16;
        if (!model.editing() && width(model.label()) + width(shown) + 12 > room) {
            shown = model.shortValue();
        }
        int valueWidth = Math.max(26, width(shown) + 8);
        Rect plus = new Rect(row.right() - 2 - STEP_BUTTON, row.y() + 2, STEP_BUTTON, row.height() - 6);
        Rect value = new Rect(plus.x() - valueWidth, plus.y(), valueWidth, plus.height());
        Rect minus = new Rect(value.x() - STEP_BUTTON, plus.y(), STEP_BUTTON, plus.height());
        int labelX = row.x() + 8;
        if (model.modified()) {
            fill(new Rect(row.x() + 3, row.centerY() - 2, 2, 2), ACCENT);
        }
        if (model.dragging() && !model.low().isEmpty()) {
            // Al arrastrar, los extremos en palabras: hacia dónde va cada lado.
            int right = minus.x() - 6;
            label(model.low(), labelX, row.centerY() - 5, (right - labelX) / 2 - 4, ACCENT_TEXT);
            rightAligned(fit(model.high(), (right - labelX) / 2 - 4), right, row.centerY() - 5, ACCENT_TEXT);
        } else {
            String label = model.editing() && !model.range().isEmpty() ? model.label() + " · " + model.range()
                    : model.label();
            label(label, labelX, row.centerY() - 5, minus.x() - labelX - 4,
                    model.editing() ? MUTED : hovered ? TEXT : TEXT_SECONDARY);
        }
        stepButton(minus, "−");
        stepButton(plus, "+");
        boolean valueHovered = hovered(value);
        if (model.editing() || model.dragging() || valueHovered) {
            fill(value, model.editing() ? FIELD : CARD_ACTIVE);
            if (model.editing()) {
                border(value, FOCUS);
            }
        }
        if (model.editing()) {
            String typed = shown;
            while (width(typed) > value.width() - 4 && typed.length() > 1) {
                typed = typed.substring(1);
            }
            centered(typed, value, TEXT);
        } else {
            centered(shown, value, model.modified() || valueHovered ? TEXT_STRONG : TEXT);
        }

        Rect zone = new Rect(minus.x(), row.y(), row.right() - minus.x(), row.height());
        return register(row)
                .valueZone(zone)
                .onClick((x, y) -> {
                    if (minus.contains(x, y)) {
                        model.step().accept(-1);
                    } else if (plus.contains(x, y)) {
                        model.step().accept(1);
                    } else if (value.contains(x, y)) {
                        model.press().run(x, y);
                    }
                })
                .onScroll(model.step())
                .onReset(model.reset())
                .tooltip(model.help())
                .brief(model.brief())
                .hint(model.hint())
                .group(model.group())
                .key(model.key());
    }

    /** Fila de opciones que se recorren: etiqueta y ‹ valor ›. Pulsar el valor avanza. */
    public Widget cycle(Rect row, String label, String value, IntConsumer step) {
        return cycle(row, label, value, false, step);
    }

    /** Como {@link #cycle(Rect, String, String, IntConsumer)}; {@code modified} pone el punto de «cambiado». */
    public Widget cycle(Rect row, String label, String value, boolean modified, IntConsumer step) {
        boolean hovered = hovered(row);
        fill(row, hovered ? CARD_HOVER : CARD);
        int valueWidth = Math.min(Math.max(90, row.width() * 3 / 5), row.width() - 60);
        Rect next = new Rect(row.right() - 2 - STEP_BUTTON, row.y() + 2, STEP_BUTTON, row.height() - 4);
        Rect shown = new Rect(next.x() - (valueWidth - 2 * STEP_BUTTON), next.y(), valueWidth - 2 * STEP_BUTTON,
                next.height());
        Rect previous = new Rect(shown.x() - STEP_BUTTON, next.y(), STEP_BUTTON, next.height());
        if (modified) {
            fill(new Rect(row.x() + 3, row.centerY() - 1, 2, 2), ACCENT);
        }
        label(label, row.x() + 8, row.centerY() - 4, previous.x() - row.x() - 12, hovered ? TEXT : TEXT_SECONDARY);
        stepButton(previous, "‹");
        stepButton(next, "›");
        centered(fit(value, shown.width() - 2), shown, modified ? TEXT_STRONG : TEXT);
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
        return toggle(row, label, on, false);
    }

    public Widget toggle(Rect row, String label, boolean on, boolean modified) {
        boolean hovered = hovered(row);
        fill(row, hovered ? CARD_HOVER : CARD);
        Rect track = new Rect(row.right() - 26, row.centerY() - 5, 20, 10);
        fill(track, on ? ACCENT : TRACK);
        Rect knob = on ? new Rect(track.right() - 9, track.y() + 1, 8, 8)
                : new Rect(track.x() + 1, track.y() + 1, 8, 8);
        fill(knob, on ? ON_ACCENT : TEXT_SECONDARY);
        if (modified) {
            fill(new Rect(row.x() + 3, row.centerY() - 1, 2, 2), ACCENT);
        }
        nameLabel(label, row.x() + 8, row, track.x() - row.x() - 12, on || hovered ? TEXT : TEXT_SECONDARY);
        return register(row);
    }

    /**
     * Fila con dibujo e interruptor: la miniatura a la izquierda (o un hueco mientras se pinta), el nombre en negrita
     * y debajo, en gris, qué hace; a la derecha el pulsador. Toda la fila lo cambia. Para lo que se enciende viéndolo
     * (las variaciones de la figura).
     */
    public Widget pictureToggle(Rect row, StudioImage image, String title, String subtitle, boolean on) {
        boolean hovered = hovered(row);
        fill(row, on ? CARD_ACTIVE : hovered ? CARD_HOVER : CARD);
        if (on) {
            fill(row.left(2), ACCENT);
        }
        int side = row.height() - 4;
        Rect picture = new Rect(row.x() + 4, row.y() + 2, side, side);
        // El fondo de miniatura detrás del dibujo: los materiales oscuros se leen igual sobre la tarjeta.
        fill(picture, THUMBNAIL);
        if (image != null) {
            image(image, picture);
        } else {
            border(picture, DIVIDER);
        }
        Rect track = new Rect(row.right() - 26, row.centerY() - 5, 20, 10);
        fill(track, on ? ACCENT : TRACK);
        fill(on ? new Rect(track.right() - 9, track.y() + 1, 8, 8) : new Rect(track.x() + 1, track.y() + 1, 8, 8),
                on ? ON_ACCENT : TEXT_SECONDARY);
        int textX = picture.right() + 6;
        int room = track.x() - textX - 4;
        // Qué hace, en dos líneas como mucho: se lee entero sin pasar el ratón.
        List<String> lines = wrap(subtitle, room);
        if (lines.size() > 2) {
            lines = new ArrayList<>(lines.subList(0, 2));
            lines.set(1, fit(lines.get(1) + ELLIPSIS, room));
        }
        int top = row.centerY() - (10 + lines.size() * LINE) / 2;
        bold(title, textX, top, room, on || hovered ? TEXT_STRONG : TEXT);
        for (int index = 0; index < lines.size(); index++) {
            label(lines.get(index), textX, top + 11 + index * LINE, on ? TEXT_SECONDARY : MUTED);
        }
        return register(row);
    }

    /**
     * Fila de bloque: el icono del bloque, la etiqueta y el nombre del bloque a la derecha. Sin bloque elegido se
     * ve el automático con el icono a media luz. El cuentagotas de la derecha toma el bloque que se mira.
     *
     * @return {fila, cuentagotas}
     */
    public Widget[] blockRow(Rect row, BlockRow model) {
        boolean hovered = hovered(row);
        fill(row, hovered ? CARD_HOVER : CARD);
        Rect dropper = new Rect(row.right() - 18, row.y() + 2, 16, row.height() - 4);
        int iconY = row.centerY() - 8;
        if (model.blockId() != null) {
            item(model.blockId(), row.x() + 3, iconY);
        } else {
            Rect empty = new Rect(row.x() + 4, iconY + 1, 14, 14);
            border(empty, DISABLED);
        }
        if (model.automatic() && model.blockId() != null) {
            fill(new Rect(row.x() + 3, iconY, 16, 16), fade(PANEL, 0.4));
        }
        if (model.modified()) {
            fill(new Rect(row.x() + 1, row.centerY() - 1, 2, 2), ACCENT);
        }
        int textX = row.x() + 23;
        int labelWidth = Math.min(width(model.label()), (dropper.x() - textX) / 2);
        label(model.label(), textX, row.centerY() - 4, labelWidth, hovered ? TEXT : TEXT_SECONDARY);
        int nameRight = dropper.x() - 4;
        int nameLeft = textX + labelWidth + 6;
        String name = fit(model.name(), nameRight - nameLeft);
        rightAligned(name, nameRight, row.centerY() - 4, model.automatic() ? MUTED : TEXT);
        Widget main = register(row).tooltip(model.help());
        Widget eyedropper = null;
        if (model.eyedropper()) {
            boolean dropperHovered = hovered(dropper);
            if (dropperHovered) {
                fill(dropper, CARD_ACTIVE);
            }
            icon(StudioIcons.Icon.EYEDROPPER, dropper.centerX() - 6, dropper.centerY() - 6,
                    dropperHovered ? ACCENT_TEXT : MUTED);
            eyedropper = register(dropper).tooltip(model.eyedropperHelp());
        }
        return new Widget[]{main, eyedropper};
    }

    private void stepButton(Rect rect, String glyph) {
        boolean hovered = hovered(rect);
        if (hovered) {
            fill(rect, CARD_ACTIVE);
        }
        centered(glyph, rect, hovered ? ACCENT_TEXT : MUTED);
    }

    // ── Tooltips ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Tooltip del tema junto al ratón, siempre dentro de {@code screen}: la primera línea en el color del texto y el
     * resto en el secundario. Se pinta por encima de todo.
     */
    public void tooltip(String text, int x, int y, Rect screen) {
        if (text == null || text.isBlank()) {
            return;
        }
        Tooltip tip = tooltipLines(text, TOOLTIP_WIDTH);
        int left = x + 10;
        int top = y - 6;
        if (left + tip.width() > screen.right() - 2) {
            left = Math.max(screen.x() + 2, x - 10 - tip.width());
        }
        top = Math.clamp(top, screen.y() + 2, Math.max(screen.y() + 2, screen.bottom() - tip.height() - 2));
        drawTooltip(tip, left, top);
    }

    /**
     * Tooltip pegado a un control y dentro de {@code bounds} (el panel): debajo de {@code anchor} o, si no cabe, encima,
     * así no tapa la fila donde está el ratón.
     */
    public void tooltip(String text, Rect anchor, Rect bounds) {
        if (text == null || text.isBlank()) {
            return;
        }
        Tooltip tip = tooltipLines(text, Math.max(60, Math.min(TOOLTIP_WIDTH, bounds.width() - 14)));
        int left = Math.clamp(anchor.x(), bounds.x() + 2,
                Math.max(bounds.x() + 2, bounds.right() - tip.width() - 2));
        int top = anchor.bottom() + 2;
        if (top + tip.height() > bounds.bottom() - 2) {
            top = Math.max(bounds.y() + 2, anchor.y() - tip.height() - 2);
        }
        drawTooltip(tip, left, top);
    }

    private record Tooltip(List<String> lines, List<Integer> colors, int width, int height) {
    }

    private Tooltip tooltipLines(String text, int wrapWidth) {
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        String[] paragraphs = text.split("\n");
        for (int index = 0; index < paragraphs.length; index++) {
            for (String line : wrap(paragraphs[index], wrapWidth)) {
                lines.add(line);
                colors.add(index == 0 ? TEXT : TEXT_SECONDARY);
            }
        }
        int textWidth = 0;
        for (String line : lines) {
            textWidth = Math.max(textWidth, width(line));
        }
        return new Tooltip(lines, colors, textWidth + 10, lines.size() * LINE + 7);
    }

    private void drawTooltip(Tooltip tip, int left, int top) {
        canvas.beginOverlay();
        Rect box = new Rect(left, top, tip.width(), tip.height());
        fill(box, SURFACE);
        border(box, OUTLINE);
        fill(new Rect(box.x(), box.y(), 2, box.height()), ACCENT);
        for (int index = 0; index < tip.lines().size(); index++) {
            label(tip.lines().get(index), box.x() + 6, box.y() + 4 + index * LINE, tip.colors().get(index));
        }
        canvas.endOverlay();
    }

    private static Rect intersection(Rect a, Rect b) {
        int left = Math.max(a.x(), b.x());
        int top = Math.max(a.y(), b.y());
        int right = Math.min(a.right(), b.right());
        int bottom = Math.min(a.bottom(), b.bottom());
        return new Rect(left, top, right - left, bottom - top);
    }

    /**
     * Lo que necesita una fila numérica; la pantalla pone el valor ya con su unidad y qué hacer con cada gesto.
     *
     * @param shortValue      el valor con la unidad en corto, para cuando no cabe la larga
     * @param fraction        dónde cae el valor en su rango, de 0 a 1
     * @param defaultFraction dónde cae el de serie ({@code NaN} si no tiene, como un automático)
     * @param range           el rango escrito («3–64 bloques»), que se enseña al escribir
     * @param help            la ayuda entera: qué cambia, los extremos y el rango
     * @param brief           la ayuda corta sobre el número: qué cambia y los extremos
     * @param hint            los gestos, para la línea del pie
     * @param low             el extremo de la izquierda en palabras («fino»), o vacío
     * @param high            el de la derecha («grueso»)
     */
    public record Stepper(
            String label,
            String value,
            String shortValue,
            double fraction,
            double defaultFraction,
            boolean modified,
            String range,
            boolean editing,
            boolean dragging,
            String input,
            IntConsumer step,
            Click press,
            Runnable reset,
            String help,
            String brief,
            String hint,
            String low,
            String high,
            String group,
            Object key
    ) {
    }

    /**
     * Lo que necesita una fila de bloque.
     *
     * @param blockId   el bloque que se ve (el elegido o el automático), o {@code null} si no se sabe
     * @param name      su nombre ya traducido, o lo que se usará en automático
     * @param automatic si no hay bloque elegido y vale el de serie
     */
    public record BlockRow(String label, String blockId, String name, boolean automatic, boolean modified,
                           String help, boolean eyedropper, String eyedropperHelp) {
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
        private Rect tooltipAnchor;
        private String brief = "";
        private String hint = "";
        private String group;
        private Object key;

        private Widget(Rect bounds) {
            this.bounds = bounds;
        }

        public Widget onClick(Runnable action) {
            this.click = action == null ? null : (x, y) -> action.run();
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

        /** El tooltip sale pegado a {@code anchor} (debajo o encima) en vez de junto al ratón. */
        public Widget tooltipBelow(Rect anchor) {
            this.tooltipAnchor = anchor;
            return this;
        }

        /** Dónde se pega el tooltip, o {@code null} para sacarlo junto al ratón. */
        public Rect tooltipAnchor() {
            return tooltipAnchor;
        }

        /** La ayuda corta que sale sobre el valor (donde la entera taparía lo que se está cambiando). */
        public Widget brief(String text) {
            this.brief = text == null ? "" : text;
            return this;
        }

        /** Lo que dice la línea del pie mientras el ratón está encima: qué gestos tiene. */
        public Widget hint(String text) {
            this.hint = text == null ? "" : text;
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

        public String brief() {
            return brief;
        }

        public String hint() {
            return hint;
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

        /** Pulsa la zona por su centro (o en {@code x, y}). */
        public void press(double x, double y) {
            if (click != null) {
                click.run(x, y);
            }
        }

        public void press() {
            press(bounds.centerX(), bounds.centerY());
        }
    }
}
