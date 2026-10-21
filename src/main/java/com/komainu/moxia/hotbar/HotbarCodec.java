package com.komainu.moxia.hotbar;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Barras como texto para compartirlas ({@code /komaedit hotbar export}) y al revés. El formato es propio de KomaEdit,
 * como el {@code kp1:} de las paletas: {@code kh1:} seguido del JSON de las barras (los huecos con la forma de
 * {@code ItemStack.CODEC}, sin {@code minecraft:} ni la cantidad cuando es 1) comprimido con Deflate y en Base64 apto
 * para URL. Lleva una barra o varias.
 *
 * <p>Leer un código no comprueba que los objetos existan: eso lo hace el servidor al importarlo, con el registro de
 * Minecraft. Aquí solo se comprueba la forma y se limita lo que se descomprime.</p>
 */
public final class HotbarCodec {
    public static final String PREFIX = "kh1:";
    /** Lo más largo que admite un paquete del cliente (32 767 bytes) con margen; en el chat caben 256 letras. */
    public static final int MAX_TEXT = 32_000;
    public static final int CHAT_LIMIT = 256;
    private static final int MAX_DECODED = HotbarBook.MAX_TOTAL_BYTES + 64 * 1024;

    private HotbarCodec() {
    }

    public static String encode(List<SavedHotbar> hotbars) {
        if (hotbars == null || hotbars.isEmpty()) {
            throw new IllegalArgumentException("No hay ninguna barra que exportar");
        }
        JsonArray array = new JsonArray();
        for (SavedHotbar hotbar : hotbars) {
            array.add(HotbarJson.hotbar(hotbar, true));
        }
        JsonObject root = new JsonObject();
        root.add("hotbars", array);
        byte[] raw = HotbarJson.compact(root).getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(64, raw.length / 2));
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            output.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    public static String encode(SavedHotbar hotbar) {
        return encode(List.of(hotbar));
    }

    /** Las barras de un código {@code kh1:}. Los espacios y saltos de línea pegados por el camino se ignoran. */
    public static List<SavedHotbar> decode(String code) {
        String text = code == null ? "" : code.replaceAll("\\s+", "");
        if (!text.startsWith(PREFIX)) {
            throw new IllegalArgumentException("El código de barra empieza por " + PREFIX);
        }
        if (text.length() > MAX_TEXT) {
            throw new IllegalArgumentException("El código de barra es demasiado largo (" + text.length()
                    + " letras; el máximo es " + MAX_TEXT + ")");
        }
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(text.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("El código de barra está dañado");
        }
        String json = inflate(compressed);
        List<SavedHotbar> hotbars = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray array = root.getAsJsonObject().getAsJsonArray("hotbars");
            if (array == null) {
                throw new IllegalArgumentException("El código no lleva barras");
            }
            for (JsonElement element : array) {
                hotbars.add(HotbarJson.hotbar(element.getAsJsonObject()));
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("El código de barra está dañado");
        }
        if (hotbars.isEmpty()) {
            throw new IllegalArgumentException("El código no lleva barras");
        }
        if (hotbars.size() > HotbarBook.MAX_HOTBARS) {
            throw new IllegalArgumentException("El código lleva más de " + HotbarBook.MAX_HOTBARS + " barras");
        }
        return List.copyOf(hotbars);
    }

    private static String inflate(byte[] compressed) {
        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("El código de barra está incompleto");
                }
                output.write(buffer, 0, read);
                if (output.size() > MAX_DECODED) {
                    throw new IllegalArgumentException("El código de barra lleva demasiado contenido");
                }
            }
        } catch (DataFormatException exception) {
            throw new IllegalArgumentException("El código de barra está dañado");
        } finally {
            inflater.end();
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
