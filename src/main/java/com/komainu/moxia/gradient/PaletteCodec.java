package com.komainu.moxia.gradient;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Convierte una paleta en un texto corto para compartirla ({@code /komaedit palette encode}) y al revés. El formato es
 * propio de KomaEdit: {@code kp1:} seguido de la lista {@code a;b;c} (sin el espacio de nombres {@code minecraft:})
 * comprimida con Deflate y en Base64 apto para URL. Admite bloques de cualquier mod.
 *
 * <p>No lee los códigos de ezEdits: su wiki no documenta el formato, sus notas de versión dicen que depende de la
 * versión de Minecraft (lo cambiaron para 1.21.5 y otra vez para 1.21.11) y averiguarlo exigiría descompilar el
 * plugin. Su {@code //ezpalette decode} da la lista de bloques, que sí se pega tal cual en cualquier paleta.</p>
 */
public final class PaletteCodec {
    public static final String PREFIX = "kp1:";
    private static final String VANILLA = "minecraft:";
    private static final int MAX_DECODED = 16_384;

    private PaletteCodec() {
    }

    public static String encode(List<String> entries) {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("La paleta está vacía");
        }
        List<String> compact = new ArrayList<>(entries.size());
        for (String entry : entries) {
            String trimmed = entry.trim();
            compact.add(trimmed.startsWith(VANILLA) ? trimmed.substring(VANILLA.length()) : trimmed);
        }
        byte[] raw = String.join(";", compact).getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream output = new ByteArrayOutputStream(raw.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            output.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
    }

    /** Devuelve las entradas de la paleta codificada, sin comprobar que existan los bloques. */
    public static List<String> decode(String encoded) {
        if (encoded == null || !encoded.trim().startsWith(PREFIX)) {
            // Los de ezEdits no se pueden leer: su formato no está publicado y cambia con la versión de Minecraft.
            throw new IllegalArgumentException("El código de paleta de KomaEdit empieza por " + PREFIX
                    + "; los de ezEdits no se pueden leer (su formato no está publicado): pega la lista de bloques"
                    + " de su //ezpalette decode");
        }
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(encoded.trim().substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("El código de paleta está dañado");
        }
        Inflater inflater = new Inflater(true);
        inflater.setInput(compressed);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int read = inflater.inflate(buffer);
                if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalArgumentException("El código de paleta está incompleto");
                }
                output.write(buffer, 0, read);
                if (output.size() > MAX_DECODED) {
                    throw new IllegalArgumentException("El código de paleta es demasiado largo");
                }
            }
        } catch (DataFormatException exception) {
            throw new IllegalArgumentException("El código de paleta está dañado");
        } finally {
            inflater.end();
        }
        return PaletteSpec.parse(output.toString(StandardCharsets.UTF_8));
    }
}
