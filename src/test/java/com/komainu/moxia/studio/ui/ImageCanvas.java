package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.layout.Rect;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * El lienzo del Studio fuera del juego: pinta en una imagen con la fuente bitmap de Minecraft y los iconos de objeto
 * del juego, a {@code scale} píxeles por píxel de GUI (la escala de GUI). Sin imagen solo apunta dónde se escribió
 * cada texto, para que las pruebas pulsen los controles por lo que dicen.
 */
public final class ImageCanvas implements StudioCanvas {
    /** Un texto pintado: qué, dónde y de qué color. */
    public record Drawn(String text, int x, int y, int argb) {
    }

    private static final MinecraftFont FONT = new MinecraftFont();
    private static final ItemIcons ITEMS = new ItemIcons();

    private final BufferedImage image;
    private final int scale;
    private final Deque<Rect> clips = new ArrayDeque<>();
    private final List<Drawn> texts = new ArrayList<>();
    private final List<String> items = new ArrayList<>();
    private final List<String> images = new ArrayList<>();

    /** Sin imagen: solo mide y apunta (pruebas de comportamiento). */
    public ImageCanvas() {
        this(null, 1);
    }

    public ImageCanvas(BufferedImage image, int scale) {
        this.image = image;
        this.scale = scale;
    }

    public static MinecraftFont font() {
        return FONT;
    }

    public BufferedImage image() {
        return image;
    }

    /** Lo escrito en el último dibujo. */
    public List<Drawn> texts() {
        return texts;
    }

    /** Los iconos de objeto pintados en el último dibujo. */
    public List<String> items() {
        return items;
    }

    /** Las imágenes (miniaturas) pintadas en el último dibujo, por clave. */
    public List<String> images() {
        return images;
    }

    public void reset() {
        texts.clear();
        items.clear();
        images.clear();
        clips.clear();
    }

    @Override
    public int width(String text) {
        return FONT.width(text);
    }

    @Override
    public int boldWidth(String text) {
        return FONT.width(text, true);
    }

    @Override
    public void text(String text, int x, int y, int argb) {
        draw(text, x, y, argb, false);
    }

    @Override
    public void bold(String text, int x, int y, int argb) {
        draw(text, x, y, argb, true);
    }

    private void draw(String text, int x, int y, int argb, boolean bold) {
        Rect clip = clips.peek();
        if (clip == null || clip.contains(x, y + 4)) {
            texts.add(new Drawn(text, x, y, argb));
        }
        if (image != null) {
            FONT.draw(image, text, x, y, argb, bold, scale, clip == null ? null
                    : new Rectangle(clip.x(), clip.y(), clip.width(), clip.height()));
        }
    }

    @Override
    public void fill(Rect rect, int argb) {
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
        int startX = Math.max(0, left * scale);
        int startY = Math.max(0, top * scale);
        int endX = Math.min(image.getWidth(), right * scale);
        int endY = Math.min(image.getHeight(), bottom * scale);
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                image.setRGB(x, y, alpha == 255 ? argb : blend(image.getRGB(x, y), argb));
            }
        }
    }

    private static int blend(int under, int over) {
        double alpha = (over >>> 24) / 255.0;
        if (alpha <= 0.0) {
            return under;
        }
        int red = (int) Math.round(((over >> 16) & 0xFF) * alpha + ((under >> 16) & 0xFF) * (1 - alpha));
        int green = (int) Math.round(((over >> 8) & 0xFF) * alpha + ((under >> 8) & 0xFF) * (1 - alpha));
        int blue = (int) Math.round((over & 0xFF) * alpha + (under & 0xFF) * (1 - alpha));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    @Override
    public void item(String id, int x, int y) {
        items.add(id);
        if (image == null) {
            return;
        }
        int[] pixels = ITEMS.icon(id);
        blit(pixels, 16, 16, new Rect(x, y, 16, 16));
    }

    @Override
    public void image(StudioImage studioImage, Rect target) {
        images.add(studioImage.key());
        if (image == null) {
            return;
        }
        int[] pixels = studioImage.pixels();
        if (pixels.length == 0) {
            return;
        }
        blit(pixels, studioImage.width(), studioImage.height(), target);
    }

    /** Pinta {@code pixels} estirados a {@code target} (muestreo por área, para que las miniaturas no se rompan). */
    private void blit(int[] pixels, int width, int height, Rect target) {
        Rect clip = clips.peek();
        int outWidth = target.width() * scale;
        int outHeight = target.height() * scale;
        for (int row = 0; row < outHeight; row++) {
            int y = target.y() * scale + row;
            for (int column = 0; column < outWidth; column++) {
                int x = target.x() * scale + column;
                if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
                    continue;
                }
                if (clip != null && !clip.contains((double) x / scale, (double) y / scale)) {
                    continue;
                }
                int argb = sample(pixels, width, height, column, row, outWidth, outHeight);
                if ((argb >>> 24) != 0) {
                    image.setRGB(x, y, (argb >>> 24) == 255 ? argb : blend(image.getRGB(x, y), argb));
                }
            }
        }
    }

    private static int sample(int[] pixels, int width, int height, int column, int row, int outWidth, int outHeight) {
        int x0 = column * width / outWidth;
        int y0 = row * height / outHeight;
        int x1 = Math.max(x0 + 1, (column + 1) * width / outWidth);
        int y1 = Math.max(y0 + 1, (row + 1) * height / outHeight);
        long alpha = 0;
        long red = 0;
        long green = 0;
        long blue = 0;
        int count = 0;
        for (int y = y0; y < Math.min(height, y1); y++) {
            for (int x = x0; x < Math.min(width, x1); x++) {
                int argb = pixels[y * width + x];
                int a = argb >>> 24;
                alpha += a;
                red += (long) ((argb >> 16) & 0xFF) * a;
                green += (long) ((argb >> 8) & 0xFF) * a;
                blue += (long) (argb & 0xFF) * a;
                count++;
            }
        }
        if (alpha == 0 || count == 0) {
            return 0;
        }
        return (int) (alpha / count) << 24 | (int) (red / alpha) << 16 | (int) (green / alpha) << 8
                | (int) (blue / alpha);
    }

    @Override
    public void pushClip(Rect clip) {
        clips.push(clip);
    }

    @Override
    public void popClip() {
        if (!clips.isEmpty()) {
            clips.pop();
        }
    }

    @Override
    public void beginOverlay() {
        // En la imagen se pinta en orden: lo último queda encima.
    }

    @Override
    public void endOverlay() {
    }
}
