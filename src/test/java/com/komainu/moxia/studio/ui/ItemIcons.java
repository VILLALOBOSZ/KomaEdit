package com.komainu.moxia.studio.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Iconos de objeto de 16×16 como los del inventario, sacados de los modelos y texturas del juego que hay en el
 * classpath de las pruebas: los objetos planos con su textura y los bloques como un cubo en perspectiva con su cara
 * de arriba y la de los lados. Solo para las maquetas; sin texturas, un cuadrado del color del id.
 */
public final class ItemIcons {
    private static final int GRASS = 0x91BD59;
    private static final int FOLIAGE = 0x77AB2F;
    private final Map<String, int[]> icons = new HashMap<>();
    private final Map<String, BufferedImage> textures = new HashMap<>();

    /** Los 16×16 píxeles ARGB del icono de {@code id}. */
    public int[] icon(String id) {
        return icons.computeIfAbsent(id.contains(":") ? id : "minecraft:" + id, this::render);
    }

    private int[] render(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        try {
            Map<String, String> itemTextures = new HashMap<>();
            String parent = collect("item/" + path, itemTextures, 0);
            String layer = resolve(itemTextures, "layer0");
            if (layer != null && (parent == null || !parent.startsWith("block/"))) {
                BufferedImage flat = texture(layer);
                if (flat != null) {
                    return pixels(flat);
                }
            }
            BufferedImage[] faces = blockFaces(path);
            if (faces != null) {
                return cube(faces[0], faces[1]);
            }
        } catch (IOException | RuntimeException ignored) {
            // Sin modelo: el cuadrado de color.
        }
        return square(id.hashCode());
    }

    /** Lee el modelo y sus padres, apuntando las texturas; devuelve el primer padre «block/…» o el último. */
    private String collect(String model, Map<String, String> found, int depth) throws IOException {
        if (depth > 16) {
            return null;
        }
        JsonObject data = json("assets/minecraft/models/" + model.substring(model.indexOf(':') + 1) + ".json");
        if (data == null) {
            return null;
        }
        if (data.has("textures")) {
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("textures").entrySet()) {
                found.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
            }
        }
        if (!data.has("parent")) {
            return model;
        }
        String parent = data.get("parent").getAsString();
        String clean = parent.substring(parent.indexOf(':') + 1);
        if (clean.startsWith("block/") && !clean.equals("block/block")) {
            return clean;
        }
        String deeper = collect(parent, found, depth + 1);
        return deeper == null ? clean : deeper;
    }

    private BufferedImage[] blockFaces(String path) throws IOException {
        JsonObject state = json("assets/minecraft/blockstates/" + path + ".json");
        String model = state == null ? "block/" + path : firstModel(state);
        if (model == null) {
            return null;
        }
        Map<String, String> found = new HashMap<>();
        collect(model, found, 0);
        String top = resolve(found, "top", "up", "end", "all", "texture", "side", "particle");
        String side = resolve(found, "side", "all", "texture", "front", "north", "end", "particle");
        BufferedImage topImage = top == null ? null : texture(top);
        BufferedImage sideImage = side == null ? null : texture(side);
        if (topImage == null && sideImage == null) {
            return null;
        }
        return new BufferedImage[]{topImage == null ? sideImage : topImage, sideImage == null ? topImage : sideImage};
    }

    private static String firstModel(JsonObject state) {
        JsonElement variant = null;
        if (state.has("variants")) {
            variant = state.getAsJsonObject("variants").entrySet().iterator().next().getValue();
        } else if (state.has("multipart")) {
            variant = state.getAsJsonArray("multipart").get(0).getAsJsonObject().get("apply");
        }
        if (variant instanceof JsonArray array) {
            variant = array.get(0);
        }
        return variant == null ? null : variant.getAsJsonObject().get("model").getAsString();
    }

    private static String resolve(Map<String, String> found, String... keys) {
        for (String key : keys) {
            String value = found.get(key);
            for (int depth = 0; value != null && value.startsWith("#") && depth < 16; depth++) {
                value = found.get(value.substring(1));
            }
            if (value != null && !value.startsWith("#")) {
                return value;
            }
        }
        return null;
    }

    private BufferedImage texture(String name) throws IOException {
        String clean = name.substring(name.indexOf(':') + 1);
        if (textures.containsKey(clean)) {
            return textures.get(clean);
        }
        BufferedImage image = null;
        try (InputStream stream = stream("assets/minecraft/textures/" + clean + ".png")) {
            if (stream != null) {
                BufferedImage read = ImageIO.read(stream);
                int size = read.getWidth();
                image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                image.getGraphics().drawImage(read, 0, 0, null);
                String last = clean.substring(clean.lastIndexOf('/') + 1);
                if (last.equals("grass_block_top")) {
                    tint(image, GRASS);
                } else if (last.endsWith("_leaves") && !last.startsWith("cherry") && !last.contains("azalea")) {
                    tint(image, FOLIAGE);
                }
            }
        }
        textures.put(clean, image);
        return image;
    }

    private static InputStream stream(String path) {
        return ItemIcons.class.getClassLoader().getResourceAsStream(path);
    }

    private static JsonObject json(String path) throws IOException {
        try (InputStream stream = stream(path)) {
            if (stream == null) {
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static int[] pixels(BufferedImage image) {
        int[] out = new int[256];
        int size = image.getWidth();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                out[y * 16 + x] = image.getRGB(x * size / 16, y * size / 16);
            }
        }
        return out;
    }

    /**
     * El cubo del inventario: la cara de arriba en rombo y las dos de los lados debajo, la izquierda algo más oscura
     * y la derecha más, como la luz de los iconos del juego.
     */
    private static int[] cube(BufferedImage top, BufferedImage side) {
        int[] out = new int[256];
        int size = side.getWidth();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                // Arriba: rombo con centro (8, 4), semiejes 8 y 4.
                double u = (px - 8) / 8.0 + (py - 4) / 4.0;
                double v = -(px - 8) / 8.0 + (py - 4) / 4.0;
                int argb = 0;
                if (Math.abs(u) <= 1 && Math.abs(v) <= 1) {
                    int tx = (int) Math.clamp((u + 1) / 2 * size, 0, size - 1);
                    int ty = (int) Math.clamp((v + 1) / 2 * size, 0, size - 1);
                    argb = top.getRGB(tx, ty);
                } else if (px <= 8) {
                    double faceY = py - 4 - px / 2.0;
                    if (faceY >= 0 && faceY <= 8 && py <= 16) {
                        int tx = (int) Math.clamp(px / 8.0 * size, 0, size - 1);
                        int ty = (int) Math.clamp(faceY / 8.0 * size, 0, size - 1);
                        argb = shade(side.getRGB(tx, ty), 0.80);
                    }
                } else {
                    double faceY = py - 8 + (px - 8) / 2.0;
                    if (faceY >= 0 && faceY <= 8 && py <= 16) {
                        int tx = (int) Math.clamp((px - 8) / 8.0 * size, 0, size - 1);
                        int ty = (int) Math.clamp(faceY / 8.0 * size, 0, size - 1);
                        argb = shade(side.getRGB(tx, ty), 0.62);
                    }
                }
                out[y * 16 + x] = argb;
            }
        }
        return out;
    }

    private static int shade(int argb, double factor) {
        int red = (int) ((argb >> 16 & 0xFF) * factor);
        int green = (int) ((argb >> 8 & 0xFF) * factor);
        int blue = (int) ((argb & 0xFF) * factor);
        return argb & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int[] square(int hash) {
        int argb = 0xFF000000 | (0x50 + (hash & 0x7F)) << 16 | (0x50 + (hash >> 7 & 0x7F)) << 8
                | (0x50 + (hash >> 14 & 0x7F));
        int[] out = new int[256];
        for (int y = 1; y < 15; y++) {
            for (int x = 1; x < 15; x++) {
                out[y * 16 + x] = argb;
            }
        }
        return out;
    }

    private static void tint(BufferedImage image, int tint) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int red = (argb >> 16 & 0xFF) * (tint >> 16 & 0xFF) / 255;
                int green = (argb >> 8 & 0xFF) * (tint >> 8 & 0xFF) / 255;
                int blue = (argb & 0xFF) * (tint & 0xFF) / 255;
                image.setRGB(x, y, argb & 0xFF000000 | red << 16 | green << 8 | blue);
            }
        }
    }
}
