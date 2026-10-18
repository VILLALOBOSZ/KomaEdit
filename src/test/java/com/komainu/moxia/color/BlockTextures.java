package com.komainu.moxia.color;

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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Texturas de bloque sacadas del jar de cliente de Minecraft, solo para los renders de comprobación: por cada bloque,
 * la de arriba y la de los lados de su primer modelo (siguiendo los padres). Sin jar, cada cara es su color medio.
 */
final class BlockTextures implements AutoCloseable {
    private static final int GRASS = 0x91BD59;
    private static final int FOLIAGE = 0x77AB2F;

    private final ZipFile jar;
    private final Map<String, BufferedImage[]> faces = new HashMap<>();
    private final Map<String, BufferedImage> images = new HashMap<>();

    private BlockTextures(ZipFile jar) {
        this.jar = jar;
    }

    /** Con {@code KOMAEDIT_MC_JAR} apuntando al jar de cliente usa sus texturas; si no, colores lisos. */
    static BlockTextures open() throws IOException {
        String path = System.getenv("KOMAEDIT_MC_JAR");
        return new BlockTextures(path == null || path.isBlank() ? null : new ZipFile(Path.of(path).toFile()));
    }

    boolean real() {
        return jar != null;
    }

    /** {arriba, lado} de 16×16; con color liso si no hay jar o no se encuentra la textura. */
    BufferedImage[] faces(String id, int fallback) {
        return faces.computeIfAbsent(id, key -> {
            BufferedImage[] found = jar == null ? null : load(key);
            if (found == null) {
                BufferedImage flat = flat(fallback);
                return new BufferedImage[]{flat, flat};
            }
            return found;
        });
    }

    private BufferedImage[] load(String id) {
        try {
            JsonObject state = json("assets/minecraft/blockstates/" + id.substring(id.indexOf(':') + 1) + ".json");
            String model = state == null ? null : firstModel(state);
            if (model == null) {
                return null;
            }
            Map<String, String> textures = new HashMap<>();
            collect(model, textures, 0);
            String top = resolve(textures, "top", "up", "end", "all", "texture", "side", "particle");
            String side = resolve(textures, "side", "all", "texture", "front", "north", "end", "particle");
            BufferedImage topImage = top == null ? null : image(top);
            BufferedImage sideImage = side == null ? null : image(side);
            if (topImage == null && sideImage == null) {
                return null;
            }
            return new BufferedImage[]{topImage == null ? sideImage : topImage, sideImage == null ? topImage
                    : sideImage};
        } catch (IOException | RuntimeException exception) {
            return null;
        }
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

    private void collect(String model, Map<String, String> textures, int depth) throws IOException {
        if (depth > 16) {
            return;
        }
        JsonObject data = json("assets/minecraft/models/" + model.substring(model.indexOf(':') + 1) + ".json");
        if (data == null) {
            return;
        }
        if (data.has("textures")) {
            for (Map.Entry<String, JsonElement> entry : data.getAsJsonObject("textures").entrySet()) {
                textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
            }
        }
        if (data.has("parent")) {
            collect(data.get("parent").getAsString(), textures, depth + 1);
        }
    }

    private static String resolve(Map<String, String> textures, String... keys) {
        for (String key : keys) {
            String value = textures.get(key);
            for (int depth = 0; value != null && value.startsWith("#") && depth < 16; depth++) {
                value = textures.get(value.substring(1));
            }
            if (value != null && !value.startsWith("#")) {
                return value;
            }
        }
        return null;
    }

    private BufferedImage image(String texture) throws IOException {
        String name = texture.substring(texture.indexOf(':') + 1);
        if (images.containsKey(name)) {
            return images.get(name);
        }
        ZipEntry entry = jar.getEntry("assets/minecraft/textures/" + name + ".png");
        BufferedImage image = null;
        if (entry != null) {
            try (InputStream stream = jar.getInputStream(entry)) {
                BufferedImage read = ImageIO.read(stream);
                int size = read.getWidth();
                image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                image.getGraphics().drawImage(read, 0, 0, null);
                String last = name.substring(name.lastIndexOf('/') + 1);
                if (last.equals("grass_block_top")) {
                    tint(image, GRASS);
                } else if (last.endsWith("_leaves") && !last.startsWith("cherry") && !last.contains("azalea")) {
                    tint(image, FOLIAGE);
                }
            }
        }
        images.put(name, image);
        return image;
    }

    private JsonObject json(String path) throws IOException {
        ZipEntry entry = jar.getEntry(path);
        if (entry == null) {
            return null;
        }
        try (InputStream stream = jar.getInputStream(entry)) {
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static BufferedImage flat(int rgb) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, 0xFF000000 | rgb);
            }
        }
        return image;
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

    @Override
    public void close() throws IOException {
        if (jar != null) {
            jar.close();
        }
    }
}
