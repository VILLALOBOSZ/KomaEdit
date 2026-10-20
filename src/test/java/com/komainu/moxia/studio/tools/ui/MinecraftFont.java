package com.komainu.moxia.studio.tools.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * La fuente bitmap de Minecraft (ascii, acentos y latín extendido) leída del jar del juego que hay en el classpath
 * de las pruebas, para que las maquetas midan y pinten el texto como el juego. Lo que no está en esos bitmaps (★,
 * flechas) se pinta con una fuente del sistema, como hace el juego con unifont.
 */
final class MinecraftFont {
    private record Glyph(BufferedImage sheet, int x, int y, int cellWidth, int cellHeight, double scale, int ascent,
                         int advance) {
    }

    private final Map<Integer, Glyph> glyphs = new HashMap<>();
    private final Font fallback = new Font(Font.SANS_SERIF, Font.PLAIN, 8);
    private final boolean loaded;

    MinecraftFont() {
        boolean ok;
        try {
            load();
            ok = !glyphs.isEmpty();
        } catch (IOException | RuntimeException exception) {
            ok = false;
        }
        loaded = ok;
    }

    boolean loaded() {
        return loaded;
    }

    private void load() throws IOException {
        JsonObject root;
        try (InputStream input = resource("assets/minecraft/font/include/default.json")) {
            root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        // Los primeros proveedores mandan sobre los siguientes, como en el juego.
        for (JsonElement element : root.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            if (!"bitmap".equals(provider.get("type").getAsString())) {
                continue;
            }
            String file = provider.get("file").getAsString().replace("minecraft:", "");
            BufferedImage sheet;
            try (InputStream input = resource("assets/minecraft/textures/" + file)) {
                sheet = ImageIO.read(input);
            }
            int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
            int ascent = provider.get("ascent").getAsInt();
            JsonArray rows = provider.getAsJsonArray("chars");
            int rowCount = rows.size();
            int columnCount = rows.get(0).getAsString().codePointCount(0, rows.get(0).getAsString().length());
            int cellWidth = sheet.getWidth() / columnCount;
            int cellHeight = sheet.getHeight() / rowCount;
            double scale = (double) height / cellHeight;
            for (int row = 0; row < rowCount; row++) {
                int[] codes = rows.get(row).getAsString().codePoints().toArray();
                for (int column = 0; column < codes.length; column++) {
                    int code = codes[column];
                    if (code == 0 || glyphs.containsKey(code)) {
                        continue;
                    }
                    int x = column * cellWidth;
                    int y = row * cellHeight;
                    int width = rightmost(sheet, x, y, cellWidth, cellHeight);
                    int advance = (int) (0.5 + width * scale) + 1;
                    glyphs.put(code, new Glyph(sheet, x, y, cellWidth, cellHeight, scale, ascent, advance));
                }
            }
        }
    }

    private static InputStream resource(String path) throws IOException {
        InputStream input = MinecraftFont.class.getClassLoader().getResourceAsStream(path);
        if (input == null) {
            throw new IOException("no está en el classpath: " + path);
        }
        return input;
    }

    /** Ancho del glifo: hasta la última columna con algún píxel, como el juego. */
    private static int rightmost(BufferedImage sheet, int x, int y, int width, int height) {
        for (int column = width - 1; column >= 0; column--) {
            for (int row = 0; row < height; row++) {
                if ((sheet.getRGB(x + column, y + row) >>> 24) != 0) {
                    return column + 1;
                }
            }
        }
        return 0;
    }

    int advance(int code, boolean bold) {
        int extra = bold ? 1 : 0;
        if (code == ' ') {
            return 4 + extra;
        }
        Glyph glyph = glyphs.get(code);
        if (glyph != null) {
            return glyph.advance() + extra;
        }
        // unifont: unos 8 px por carácter en el juego.
        return 8 + extra;
    }

    int width(String text) {
        return width(text, false);
    }

    int width(String text, boolean bold) {
        int total = 0;
        for (int code : text.codePoints().toArray()) {
            total += advance(code, bold);
        }
        return total;
    }

    /**
     * Pinta {@code text} con la esquina superior izquierda en (x, y) de GUI, a {@code scale} píxeles por punto, sin
     * salirse de {@code clip} (en coordenadas de GUI; {@code null} sin recorte).
     */
    void draw(BufferedImage target, String text, int x, int y, int argb, boolean bold, int scale,
              java.awt.Rectangle clip) {
        int cursor = x;
        for (int code : text.codePoints().toArray()) {
            Glyph glyph = glyphs.get(code);
            if (code != ' ') {
                if (glyph != null) {
                    blit(target, glyph, cursor, y, argb, scale, clip);
                    if (bold) {
                        blit(target, glyph, cursor + 1, y, argb, scale, clip);
                    }
                } else {
                    drawFallback(target, code, cursor, y, argb, scale, clip);
                }
            }
            cursor += advance(code, bold);
        }
    }

    private void blit(BufferedImage target, Glyph glyph, int x, int y, int argb, int scale, java.awt.Rectangle clip) {
        int top = y + 7 - glyph.ascent();
        for (int row = 0; row < glyph.cellHeight(); row++) {
            for (int column = 0; column < glyph.cellWidth(); column++) {
                if ((glyph.sheet().getRGB(glyph.x() + column, glyph.y() + row) >>> 24) == 0) {
                    continue;
                }
                double gx = x + column * glyph.scale();
                double gy = top + row * glyph.scale();
                int px0 = (int) Math.round(gx * scale);
                int py0 = (int) Math.round(gy * scale);
                int px1 = (int) Math.round((gx + glyph.scale()) * scale);
                int py1 = (int) Math.round((gy + glyph.scale()) * scale);
                for (int py = py0; py < Math.max(py1, py0 + 1); py++) {
                    for (int px = px0; px < Math.max(px1, px0 + 1); px++) {
                        if (px >= 0 && py >= 0 && px < target.getWidth() && py < target.getHeight()
                                && (clip == null || clip.contains(px / scale, py / scale))) {
                            target.setRGB(px, py, argb);
                        }
                    }
                }
            }
        }
    }

    private void drawFallback(BufferedImage target, int code, int x, int y, int argb, int scale,
                              java.awt.Rectangle clip) {
        Graphics2D graphics = target.createGraphics();
        if (clip != null) {
            graphics.setClip(clip.x * scale, clip.y * scale, clip.width * scale, clip.height * scale);
        }
        graphics.setFont(fallback.deriveFont(8.0f * scale));
        graphics.setColor(new java.awt.Color(argb, true));
        graphics.drawString(new String(Character.toChars(code)), x * scale, (y + 8) * scale);
        graphics.dispose();
    }
}
