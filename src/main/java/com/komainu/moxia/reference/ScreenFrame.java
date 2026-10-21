package com.komainu.moxia.reference;

/**
 * Dónde está una imagen de referencia en pantalla: una ventana flotante que se arrastra por su barra, se agranda por
 * su esquina o con la rueda y puede dejarse «a través» para que el ratón la ignore.
 *
 * <p>La posición y la anchura van en fracciones de la pantalla, así la ventana sigue en su sitio al cambiar el tamaño
 * de la ventana del juego o la escala de la interfaz. El alto sale de la proporción de la imagen. Cuentas en unidades
 * de la interfaz de Minecraft, las mismas que el ratón de una pantalla.</p>
 *
 * @param x            borde izquierdo de la imagen, en fracción del ancho de la pantalla
 * @param y            borde de arriba de la imagen, en fracción del alto
 * @param width        anchura de la imagen, en fracción del ancho de la pantalla
 * @param clickThrough el ratón la ignora (sin barra ni esquina, y los clics van a lo que haya debajo)
 */
public record ScreenFrame(double x, double y, double width, boolean clickThrough) {
    public static final double MIN_WIDTH = 0.04;
    public static final double MAX_WIDTH = 3.0;
    private static final double DEFAULT_X = 0.62;
    private static final double DEFAULT_Y = 0.1;
    private static final double DEFAULT_WIDTH = 0.34;
    public static final ScreenFrame DEFAULT = new ScreenFrame(DEFAULT_X, DEFAULT_Y, DEFAULT_WIDTH, false);
    /** Alto de la barra de arriba (título y botones). */
    public static final int BAR = 12;
    /** Lado de la esquina para agrandar. */
    public static final int GRIP = 8;
    /** Lo que tiene que quedar dentro de la pantalla para poder agarrarla siempre. */
    public static final int KEEP = 24;
    /** Lado mínimo de la imagen en pantalla. */
    public static final int MIN_SIDE = 16;

    public ScreenFrame {
        x = finite(x, DEFAULT_X);
        y = finite(y, DEFAULT_Y);
        width = Math.clamp(finite(width, DEFAULT_WIDTH), MIN_WIDTH, MAX_WIDTH);
        x = Math.clamp(x, -MAX_WIDTH, 1.0);
        y = Math.clamp(y, 0.0, 1.0);
    }

    /** Un rectángulo en unidades de la interfaz. */
    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }
    }

    /** Las partes de la ventana que se pueden pulsar. */
    public enum Part {
        NONE,
        /** La imagen: arrastrarla la mueve; la rueda la agranda. */
        IMAGE,
        /** La barra de arriba: arrastrarla la mueve. */
        BAR,
        /** Botón de la barra: dejarla «a través». */
        THROUGH,
        /** Botón de la barra: ocultarla. */
        HIDE,
        /** La esquina de abajo a la derecha: agranda o encoge sin deformar. */
        GRIP
    }

    /**
     * La ventana colocada en una pantalla de {@code screenWidth}×{@code screenHeight} para una imagen de
     * {@code pixelWidth}×{@code pixelHeight}: la imagen, su barra (encima) y los botones.
     */
    public record Window(Rect image, Rect bar, Rect through, Rect hide, Rect grip) {
        /** Qué parte hay en un punto; con {@code chrome} falso (en el mundo o «a través») solo cuenta la imagen. */
        public Part hit(double pointX, double pointY, boolean chrome) {
            if (chrome) {
                if (hide.contains(pointX, pointY)) {
                    return Part.HIDE;
                }
                if (through.contains(pointX, pointY)) {
                    return Part.THROUGH;
                }
                if (bar.contains(pointX, pointY)) {
                    return Part.BAR;
                }
                if (grip.contains(pointX, pointY)) {
                    return Part.GRIP;
                }
            }
            return image.contains(pointX, pointY) ? Part.IMAGE : Part.NONE;
        }

        /** El píxel de la imagen bajo un punto de la pantalla ({@code {u, v}}). */
        public double[] pixel(double pointX, double pointY, int pixelWidth, int pixelHeight) {
            return new double[]{(pointX - image.x()) * Math.max(1, pixelWidth) / (double) image.width(),
                    (pointY - image.y()) * Math.max(1, pixelHeight) / (double) image.height()};
        }

        /** Unidades de pantalla por píxel de la imagen. */
        public double scale(int pixelWidth) {
            return image.width() / (double) Math.max(1, pixelWidth);
        }
    }

    public Window layout(int screenWidth, int screenHeight, int pixelWidth, int pixelHeight) {
        int screenW = Math.max(1, screenWidth);
        int screenH = Math.max(1, screenHeight);
        double aspect = Math.max(1, pixelHeight) / (double) Math.max(1, pixelWidth);
        int imageWidth = (int) Math.round(width * screenW);
        int imageHeight = (int) Math.round(imageWidth * aspect);
        if (Math.min(imageWidth, imageHeight) < MIN_SIDE) {
            double grow = MIN_SIDE / (double) Math.max(1, Math.min(imageWidth, imageHeight));
            imageWidth = (int) Math.ceil(imageWidth * grow);
            imageHeight = (int) Math.ceil(imageHeight * grow);
        }
        int keepX = Math.min(imageWidth, KEEP);
        int keepY = Math.min(imageHeight, KEEP);
        int left = Math.clamp(Math.round(x * screenW), keepX - imageWidth, Math.max(keepX - imageWidth,
                screenW - keepX));
        int top = Math.clamp(Math.round(y * screenH), BAR, Math.max(BAR, screenH - keepY));
        Rect image = new Rect(left, top, imageWidth, imageHeight);
        Rect bar = new Rect(left, top - BAR, imageWidth, BAR);
        Rect hide = new Rect(bar.right() - BAR, bar.y(), BAR, BAR);
        Rect through = new Rect(hide.x() - BAR, bar.y(), BAR, BAR);
        Rect grip = new Rect(image.right() - GRIP, image.bottom() - GRIP, GRIP, GRIP);
        return new Window(image, bar, through, hide, grip);
    }

    /** La ventana con su imagen empezando en ({@code left}, {@code top}) de la pantalla. */
    public ScreenFrame movedTo(int left, int top, int screenWidth, int screenHeight) {
        return new ScreenFrame(left / (double) Math.max(1, screenWidth), top / (double) Math.max(1, screenHeight),
                width, clickThrough);
    }

    /** La ventana con la imagen de {@code imageWidth} unidades de ancho (el alto sigue la proporción). */
    public ScreenFrame withImageWidth(int imageWidth, int screenWidth) {
        return new ScreenFrame(x, y, imageWidth / (double) Math.max(1, screenWidth), clickThrough);
    }

    /**
     * Agrandada {@code factor} veces dejando quieto el punto ({@code anchorX}, {@code anchorY}) de la pantalla, como
     * la rueda sobre un visor de fotos.
     */
    public ScreenFrame zoomed(double factor, double anchorX, double anchorY, int screenWidth, int screenHeight,
                              int pixelWidth, int pixelHeight) {
        Window window = layout(screenWidth, screenHeight, pixelWidth, pixelHeight);
        double next = Math.clamp(width * factor, MIN_WIDTH, MAX_WIDTH);
        double applied = next / width;
        double left = anchorX - (anchorX - window.image().x()) * applied;
        double top = anchorY - (anchorY - window.image().y()) * applied;
        int screenW = Math.max(1, screenWidth);
        int screenH = Math.max(1, screenHeight);
        return new ScreenFrame(left / screenW, top / screenH, next, clickThrough);
    }

    public ScreenFrame withClickThrough(boolean value) {
        return new ScreenFrame(x, y, width, value);
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }
}
