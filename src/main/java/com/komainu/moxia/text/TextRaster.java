package com.komainu.moxia.text;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/**
 * Texto dibujado con una fuente TrueType sobre una rejilla de bloques, sin Minecraft.
 *
 * <p>Cada bloque se divide en {@value #SAMPLES}×{@value #SAMPLES} muestras: la letra ocupa una muestra si su centro
 * cae dentro del contorno. Con eso se sabe qué parte de cada bloque cubre la letra ({@link #coverage}) y, para las
 * losas y escaleras de {@code -w}, si un punto cualquiera está dentro ({@link #solid}).</p>
 *
 * <p>Coordenadas del plano del texto: {@code u} hacia la derecha y {@code v} hacia arriba, en bloques. La línea base
 * de la primera línea está en {@code v = 0} y las demás bajan.</p>
 */
public final class TextRaster {
    /** Muestras por bloque en cada eje. */
    public static final int SAMPLES = 8;
    /** Tope de muestras de una imagen, unos 32 MB de memoria temporal. */
    public static final long MAX_SAMPLES = 32_000_000L;
    public static final int MAX_LENGTH = 256;
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, false, true);

    /** Primera muestra de la rejilla (múltiplos de {@link #SAMPLES}: los bloques empiezan en una muestra). */
    private final int fineMinU;
    private final int fineMinV;
    private final int fineWidth;
    private final int fineHeight;
    private final BitSet samples;

    private TextRaster(int fineMinU, int fineMinV, int fineWidth, int fineHeight, BitSet samples) {
        this.fineMinU = fineMinU;
        this.fineMinV = fineMinV;
        this.fineWidth = fineWidth;
        this.fineHeight = fineHeight;
        this.samples = samples;
    }

    /** Alineación de las líneas entre sí. */
    public enum Align {
        LEFT, CENTER, RIGHT
    }

    /**
     * @param capHeight   altura de una mayúscula en bloques
     * @param tracking    espacio extra entre letras, en bloques
     * @param lineSpacing separación entre líneas, en múltiplos de la de la fuente
     */
    public record Settings(double capHeight, double tracking, double lineSpacing, Align align) {
        public Settings {
            if (!(capHeight >= 1.0 && capHeight <= 256.0)) {
                throw new IllegalArgumentException("El tamaño va de 1 a 256 bloques");
            }
            if (!(tracking >= -8.0 && tracking <= 32.0)) {
                throw new IllegalArgumentException("El espaciado entre letras va de -8 a 32 bloques");
            }
            if (!(lineSpacing >= 0.5 && lineSpacing <= 4.0)) {
                throw new IllegalArgumentException("El interlineado va de 0.5 a 4");
            }
            align = align == null ? Align.CENTER : align;
        }
    }

    /** Contorno del texto en muestras (y hacia abajo, como en Java2D) antes de rellenarlo. */
    public record Outline(Shape shape, Rectangle2D bounds) {
        /** Caja en bloques del plano del texto: {@code minU, minV, maxU, maxV}, con los extremos incluidos. */
        public int[] blockBounds() {
            int minU = Math.floorDiv((int) Math.floor(bounds.getMinX()), SAMPLES);
            int maxU = Math.floorDiv((int) Math.ceil(bounds.getMaxX()), SAMPLES);
            int minV = Math.floorDiv((int) Math.floor(-bounds.getMaxY()), SAMPLES);
            int maxV = Math.floorDiv((int) Math.ceil(-bounds.getMinY()), SAMPLES);
            return new int[]{minU, minV, maxU, maxV};
        }
    }

    /**
     * Calcula el contorno del texto: es rápido y sirve para saber la caja antes de leer el mundo. {@code \n} (o el
     * texto {@code \\n}) separa líneas.
     */
    public static Outline outline(Font font, String text, Settings settings) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El texto está vacío");
        }
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("El texto admite como mucho " + MAX_LENGTH + " caracteres");
        }
        List<String> lines = lines(text);
        int missing = font.canDisplayUpTo(String.join("", lines));
        if (missing >= 0) {
            String all = String.join("", lines);
            int codePoint = all.codePointAt(missing);
            throw new IllegalArgumentException("La fuente " + font.getFontName() + " no tiene el carácter «"
                    + new String(Character.toChars(codePoint)) + "»");
        }
        double capAtHundred = capHeight(font.deriveFont(100f));
        float size = (float) (100.0 * settings.capHeight() * SAMPLES / capAtHundred);
        Font sized = font.deriveFont(Map.of(
                TextAttribute.SIZE, size,
                TextAttribute.KERNING, TextAttribute.KERNING_ON,
                TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON,
                TextAttribute.TRACKING, (float) (settings.tracking() * SAMPLES / size)
        ));
        List<TextLayout> layouts = new ArrayList<>();
        double widest = 0.0;
        for (String line : lines) {
            TextLayout layout = line.isBlank() ? null : new TextLayout(line, sized, CONTEXT);
            layouts.add(layout);
            if (layout != null) {
                widest = Math.max(widest, layout.getAdvance());
            }
        }
        var metrics = sized.getLineMetrics("Hg", CONTEXT);
        double lineHeight = (metrics.getAscent() + metrics.getDescent() + metrics.getLeading())
                * settings.lineSpacing();
        Path2D.Double shape = new Path2D.Double(Path2D.WIND_NON_ZERO);
        double baseline = 0.0;
        for (TextLayout layout : layouts) {
            if (layout != null) {
                double offset = switch (settings.align()) {
                    case LEFT -> 0.0;
                    case CENTER -> (widest - layout.getAdvance()) / 2.0;
                    case RIGHT -> widest - layout.getAdvance();
                };
                shape.append(layout.getOutline(AffineTransform.getTranslateInstance(offset, baseline)), false);
            }
            baseline += lineHeight;
        }
        Rectangle2D bounds = shape.getBounds2D();
        if (bounds.isEmpty()) {
            throw new IllegalArgumentException("El texto no tiene ningún trazo visible");
        }
        return new Outline(shape, bounds);
    }

    /** Rellena el contorno en la rejilla de muestras; es lo costoso y se hace en un worker. */
    public static TextRaster fill(Outline outline) {
        int[] blocks = outline.blockBounds();
        int fineMinU = blocks[0] * SAMPLES;
        int fineMinV = blocks[1] * SAMPLES;
        int width = (blocks[2] - blocks[0] + 1) * SAMPLES;
        int height = (blocks[3] - blocks[1] + 1) * SAMPLES;
        if ((long) width * height > MAX_SAMPLES) {
            throw new IllegalArgumentException("El texto es demasiado grande: baja el tamaño o divídelo en líneas");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setColor(java.awt.Color.WHITE);
            // Fila de imagen r ↔ v fino = fineMinV + height - 1 - r; columna c ↔ u fino = fineMinU + c.
            graphics.translate(-fineMinU, fineMinV + height);
            graphics.fill(outline.shape());
        } finally {
            graphics.dispose();
        }
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        BitSet samples = new BitSet(width * height);
        for (int row = 0; row < height; row++) {
            int v = height - 1 - row;
            for (int column = 0; column < width; column++) {
                if ((pixels[row * width + column] & 0xFF) >= 128) {
                    samples.set(v * width + column);
                }
            }
        }
        return new TextRaster(fineMinU, fineMinV, width, height, samples);
    }

    /** Atajo para tests y herramientas: contorno y relleno a la vez. */
    public static TextRaster render(Font font, String text, Settings settings) {
        return fill(outline(font, text, settings));
    }

    /** Rejilla hecha a mano (tests): {@code rows[0]} es la fila de arriba y cada carácter distinto de espacio o
     * punto es un bloque lleno. La esquina de abajo a la izquierda es el bloque (0, 0). */
    public static TextRaster ofBlocks(String... rows) {
        int height = rows.length * SAMPLES;
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length() * SAMPLES);
        }
        BitSet samples = new BitSet(width * height);
        for (int index = 0; index < rows.length; index++) {
            int v = rows.length - 1 - index;
            for (int u = 0; u < rows[index].length(); u++) {
                char character = rows[index].charAt(u);
                if (character == ' ' || character == '.') {
                    continue;
                }
                for (int sv = 0; sv < SAMPLES; sv++) {
                    for (int su = 0; su < SAMPLES; su++) {
                        samples.set((v * SAMPLES + sv) * width + u * SAMPLES + su);
                    }
                }
            }
        }
        return new TextRaster(0, 0, Math.max(width, SAMPLES), Math.max(height, SAMPLES), samples);
    }

    public int minU() {
        return Math.floorDiv(fineMinU, SAMPLES);
    }

    public int minV() {
        return Math.floorDiv(fineMinV, SAMPLES);
    }

    public int maxU() {
        return minU() + fineWidth / SAMPLES - 1;
    }

    public int maxV() {
        return minV() + fineHeight / SAMPLES - 1;
    }

    /** Si el punto del plano está dentro de la letra. */
    public boolean solid(double u, double v) {
        int column = (int) Math.floor(u * SAMPLES) - fineMinU;
        int row = (int) Math.floor(v * SAMPLES) - fineMinV;
        return sample(column, row);
    }

    /** Parte del bloque cubierta por la letra, de 0 a 1. */
    public double coverage(int u, int v) {
        int column = u * SAMPLES - fineMinU;
        int row = v * SAMPLES - fineMinV;
        if (column < 0 || row < 0 || column >= fineWidth || row >= fineHeight) {
            return 0.0;
        }
        int inside = 0;
        for (int sv = 0; sv < SAMPLES; sv++) {
            int base = (row + sv) * fineWidth + column;
            for (int su = 0; su < SAMPLES; su++) {
                if (samples.get(base + su)) {
                    inside++;
                }
            }
        }
        return inside / (double) (SAMPLES * SAMPLES);
    }

    /** El bloque cuenta como letra si la cubre al menos a la mitad. */
    public boolean filled(int u, int v) {
        return coverage(u, v) >= 0.5;
    }

    /** Bloques llenos (para el recuento y los tests). */
    public int filledCount() {
        int count = 0;
        for (int v = minV(); v <= maxV(); v++) {
            for (int u = minU(); u <= maxU(); u++) {
                if (filled(u, v)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Borde alrededor de la letra: todo lo que queda a menos de {@code width} bloques de ella, sin la letra. Se
     * calcula con la distancia euclídea exacta sobre las muestras, así el borde redondea las esquinas.
     */
    public TextRaster border(double width) {
        if (!(width > 0.0 && width <= 16.0)) {
            throw new IllegalArgumentException("El borde va de 1 a 16 bloques");
        }
        int margin = (int) Math.ceil(width) * SAMPLES;
        int newWidth = fineWidth + 2 * margin;
        int newHeight = fineHeight + 2 * margin;
        if ((long) newWidth * newHeight > MAX_SAMPLES) {
            throw new IllegalArgumentException("El borde hace el texto demasiado grande");
        }
        double[] distance = new double[newWidth * newHeight];
        for (int row = 0; row < newHeight; row++) {
            for (int column = 0; column < newWidth; column++) {
                boolean inside = sample(column - margin, row - margin);
                distance[row * newWidth + column] = inside ? 0.0 : Double.POSITIVE_INFINITY;
            }
        }
        DistanceTransform2D.squared(distance, newWidth, newHeight);
        double limit = width * SAMPLES;
        double limitSquared = limit * limit;
        BitSet ring = new BitSet(newWidth * newHeight);
        for (int index = 0; index < distance.length; index++) {
            if (distance[index] > 0.0 && distance[index] <= limitSquared) {
                ring.set(index);
            }
        }
        return new TextRaster(fineMinU - margin, fineMinV - margin, newWidth, newHeight, ring);
    }

    private boolean sample(int column, int row) {
        if (column < 0 || row < 0 || column >= fineWidth || row >= fineHeight) {
            return false;
        }
        return samples.get(row * fineWidth + column);
    }

    static List<String> lines(String text) {
        String normalized = text.replace("\r", "").replace("\\n", "\n");
        List<String> lines = new ArrayList<>(List.of(normalized.split("\n", -1)));
        while (!lines.isEmpty() && lines.getLast().isBlank()) {
            lines.removeLast();
        }
        if (lines.size() > 16) {
            throw new IllegalArgumentException("El texto admite como mucho 16 líneas");
        }
        return lines;
    }

    /** Altura de la «H» en la fuente; si no la tiene, una aproximación a partir del ascendente. */
    private static double capHeight(Font font) {
        if (font.canDisplay('H')) {
            Rectangle2D bounds = new TextLayout("H", font, CONTEXT).getBounds();
            if (bounds.getHeight() > 1.0) {
                return bounds.getHeight();
            }
        }
        return font.getLineMetrics("H", CONTEXT).getAscent() * 0.7;
    }
}
