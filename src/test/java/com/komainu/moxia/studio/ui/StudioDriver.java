package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.layout.Rect;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * El Studio entero en una prueba: la vista común, un anfitrión de mentira y un lienzo. Pulsa los controles por lo
 * que dicen (como lo haría un jugador), cuenta los clics y pinta maquetas con el mismo código que el juego.
 */
public final class StudioDriver {
    public final StudioView view;
    public final FakeStudioHost host;
    private final int width;
    private final int height;
    private ImageCanvas canvas = new ImageCanvas();
    private StudioUi ui = new StudioUi(canvas);
    private int clicks;
    private int keys;
    public int mouseX = -1;
    public int mouseY = -1;

    /** @param width,height pantalla en píxeles de GUI (ya escalados) */
    public StudioDriver(String language, int width, int height) {
        this(new StudioView(), new FakeStudioHost(language), width, height);
    }

    public StudioDriver(StudioView view, FakeStudioHost host, int width, int height) {
        this.view = view;
        this.host = host;
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Clics de ratón que se han hecho con {@link #click} (para medir tareas). */
    public int clicks() {
        return clicks;
    }

    /** Teclas pulsadas con {@link #key} y {@link #type}. */
    public int keys() {
        return keys;
    }

    public void resetCounts() {
        clicks = 0;
        keys = 0;
    }

    public ImageCanvas canvas() {
        return canvas;
    }

    public StudioUi ui() {
        return ui;
    }

    /** Dibuja un fotograma sin imagen (lo que ve la prueba). */
    public void render() {
        canvas.reset();
        view.render(ui, width, height, mouseX, mouseY, host);
    }

    /** Pinta el Studio sobre {@code background} a {@code scale} píxeles por píxel de GUI. */
    public BufferedImage paint(int scale, java.util.function.Consumer<StudioUi> background) {
        BufferedImage image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_ARGB);
        ImageCanvas painter = new ImageCanvas(image, scale);
        StudioUi paintUi = new StudioUi(painter);
        if (background != null) {
            background.accept(paintUi);
        }
        view.render(paintUi, width, height, mouseX, mouseY, host);
        // Lo que la prueba pulse después tiene que estar donde se acaba de pintar.
        canvas = painter;
        ui = paintUi;
        return image;
    }

    /** Si algún texto visible dice exactamente {@code text}. */
    public boolean has(String text) {
        render();
        return canvas.texts().stream().anyMatch(drawn -> drawn.text().equals(text));
    }

    /** Si algún texto visible contiene {@code part}. */
    public boolean shows(String part) {
        render();
        return canvas.texts().stream().anyMatch(drawn -> drawn.text().contains(part));
    }

    public List<String> texts() {
        render();
        return canvas.texts().stream().map(ImageCanvas.Drawn::text).toList();
    }

    /**
     * El punto donde está escrito {@code label} (o el centro del control cuya ayuda empieza por él). Busca desde lo
     * último pintado: lo de encima gana.
     */
    public int[] where(String label) {
        render();
        List<ImageCanvas.Drawn> texts = new ArrayList<>(canvas.texts());
        for (int index = texts.size() - 1; index >= 0; index--) {
            ImageCanvas.Drawn drawn = texts.get(index);
            if (drawn.text().equals(label)) {
                int x = drawn.x() + Math.max(1, ImageCanvas.font().width(label) / 2);
                int y = drawn.y() + 4;
                if (ui.at(x, y) != null) {
                    return new int[]{x, y};
                }
            }
        }
        List<StudioUi.Widget> widgets = ui.widgets();
        for (int index = widgets.size() - 1; index >= 0; index--) {
            StudioUi.Widget widget = widgets.get(index);
            if (widget.tooltip().split("\n", 2)[0].equals(label)) {
                Rect bounds = widget.bounds();
                return new int[]{bounds.centerX(), bounds.centerY()};
            }
        }
        throw new AssertionError("no hay nada que diga «" + label + "»: " + canvas.texts().stream()
                .map(ImageCanvas.Drawn::text).filter(text -> !text.isBlank()).distinct().toList());
    }

    /**
     * El valor (el número con su unidad) de la fila numérica cuya etiqueta es {@code label}: el texto de la mitad
     * derecha de la fila que no es − ni +.
     */
    public int[] valuePoint(String label) {
        int[] point = where(label);
        StudioUi.Widget row = ui.at(point[0], point[1]);
        Rect bounds = row.bounds();
        for (ImageCanvas.Drawn drawn : canvas.texts()) {
            int x = drawn.x() + ImageCanvas.font().width(drawn.text()) / 2;
            int y = drawn.y() + 4;
            if (bounds.contains(x, y) && x > bounds.centerX() && !drawn.text().equals("−")
                    && !drawn.text().equals("+")) {
                return new int[]{x, y};
            }
        }
        throw new AssertionError("la fila «" + label + "» no tiene valor");
    }

    /** Pulsa el valor de la fila numérica {@code label} (sin arrastrar: abre el campo para escribir). */
    public void clickValue(String label) {
        int[] point = valuePoint(label);
        clicks++;
        view.mouseClicked(point[0], point[1], 0, host);
        view.mouseReleased(point[0], point[1], 0, host);
    }

    /** La rueda sobre el valor de la fila numérica {@code label}. */
    public void wheelValue(String label, int amount) {
        int[] point = valuePoint(label);
        view.mouseScrolled(point[0], point[1], amount, host);
    }

    /** Arrastra el valor de la fila numérica {@code label}. */
    public void dragValue(String label, int distance, boolean fine) {
        int[] point = valuePoint(label);
        host.shift = fine;
        view.mouseClicked(point[0], point[1], 0, host);
        for (int step = 1; step <= Math.abs(distance); step++) {
            view.mouseDragged(point[0] + Integer.signum(distance) * step, point[1], 0, host);
        }
        view.mouseReleased(point[0] + distance, point[1], 0, host);
        host.shift = false;
    }

    /** Pulsa lo que dice {@code label} dentro de un grupo de controles ({@code tools:stamp}…). */
    public void click(String label, String group) {
        render();
        for (ImageCanvas.Drawn drawn : canvas.texts()) {
            int x = drawn.x() + Math.max(1, ImageCanvas.font().width(drawn.text()) / 2);
            int y = drawn.y() + 4;
            StudioUi.Widget widget = ui.at(x, y);
            if (drawn.text().equals(label) && widget != null && group.equals(widget.group())) {
                clicks++;
                view.mouseClicked(x, y, 0, host);
                view.mouseReleased(x, y, 0, host);
                return;
            }
        }
        throw new AssertionError("no hay «" + label + "» en " + group);
    }

    /** Pulsa el control con la clave {@code key} (los campos de texto). */
    public void clickKey(Object key) {
        render();
        for (StudioUi.Widget widget : ui.widgets()) {
            if (key.equals(widget.key())) {
                clicks++;
                view.mouseClicked(widget.bounds().centerX(), widget.bounds().centerY(), 0, host);
                view.mouseReleased(widget.bounds().centerX(), widget.bounds().centerY(), 0, host);
                return;
            }
        }
        throw new AssertionError("no hay ningún control con la clave " + key);
    }

    public StudioUi.Widget widget(String label) {
        int[] point = where(label);
        return ui.at(point[0], point[1]);
    }

    /** Pulsa lo que dice {@code label} con el botón izquierdo. */
    public StudioView.Click click(String label) {
        int[] point = where(label);
        clicks++;
        StudioView.Click result = view.mouseClicked(point[0], point[1], 0, host);
        view.mouseReleased(point[0], point[1], 0, host);
        return result;
    }

    /** Clic derecho (restablecer) sobre lo que dice {@code label}. */
    public void reset(String label) {
        int[] point = where(label);
        clicks++;
        view.mouseClicked(point[0], point[1], 1, host);
    }

    /** Pulsa en un punto de la pantalla (el mundo, por ejemplo). */
    public StudioView.Click clickAt(int x, int y) {
        render();
        clicks++;
        StudioView.Click result = view.mouseClicked(x, y, 0, host);
        view.mouseReleased(x, y, 0, host);
        return result;
    }

    /** Arrastra desde lo que dice {@code label} {@code distance} píxeles en horizontal. */
    public void drag(String label, int distance, boolean fine) {
        int[] point = where(label);
        host.shift = fine;
        view.mouseClicked(point[0], point[1], 0, host);
        for (int step = 1; step <= Math.abs(distance); step++) {
            view.mouseDragged(point[0] + Integer.signum(distance) * step, point[1], 0, host);
        }
        view.mouseReleased(point[0] + distance, point[1], 0, host);
        host.shift = false;
    }

    public void wheel(String label, int amount) {
        int[] point = where(label);
        view.mouseScrolled(point[0], point[1], amount, host);
    }

    public boolean key(StudioKey key) {
        render();
        keys++;
        return view.key(key, host);
    }

    public void type(String text) {
        for (char character : text.toCharArray()) {
            keys++;
            view.charTyped(character, host);
        }
    }

    public void tick() {
        view.tick(host);
    }

    /** Pasa el tiempo (y un tick) para los retardos. */
    public void advance(long millis) {
        host.now += millis;
        view.tick(host);
    }
}
