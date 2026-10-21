package com.komainu.moxia.studio.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ningún color del Studio se escribe a mano fuera de las fichas ({@link StudioTheme}) y los temas
 * ({@link StudioPalette}): así un tema nuevo o un retoque de color se hace en un solo sitio y no quedan rincones con
 * el color de antes. Busca literales de color ({@code 0xFF…} de 6 u 8 cifras) en el código del Studio.
 */
class StudioThemeTokensTest {
    /**
     * El código que pinta el Studio: la vista común, la etapa de herramientas, la pantalla del cliente y las guías que
     * el ratón del Studio deja en el mundo (la caja de selección con sus caras).
     */
    private static final List<Path> FOLDERS = List.of(
            Path.of("src/main/java/com/komainu/moxia/studio/ui"),
            Path.of("src/main/java/com/komainu/moxia/studio/tools/ui"),
            Path.of("src/client/java/com/komainu/moxia/client/gui"),
            Path.of("src/client/java/com/komainu/moxia/client/world/WorldOverlayRenderer.java"));
    /** Donde viven los colores. */
    private static final Set<String> THEME = Set.of("StudioTheme.java", "StudioPalette.java");
    /**
     * Miniaturas que pintan el mundo (hojas, tierra, cristal, el relieve de un sello, un farol, los bloques de una
     * paleta), no la interfaz: sus colores no cambian con el tema, pero tienen que ir con nombre, en una constante.
     */
    private static final Set<String> WORLD_PICTURES = Set.of("ChoiceThumbnails.java", "StampPreview.java",
            "VariationThumbnails.java", "PaintPreview.java");
    /** Máscaras de bits: el alfa opaco, los tres canales y el cambio de orden de canales de una textura. */
    private static final Set<String> MASKS = Set.of("0xFF000000", "0xFFFFFF", "0x00FFFFFF", "0xFF00FF00");
    /** Un alfa solo ({@code 0xF0000000}), que se pone sobre un color con {@code |}: no es un color. */
    private static final Pattern ALPHA = Pattern.compile("0x[0-9A-F]{2}000000");
    private static final Pattern LITERAL = Pattern.compile("0[xX](?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})(?![0-9A-Fa-fLl])");
    private static final Pattern NAMED = Pattern.compile("^\\s*(?:private |public |protected )?static final int(?:\\[])?\\s+[A-Z_]+\\s*=");

    @Test
    void noColorIsWrittenOutsideTheTokens() throws IOException {
        List<String> found = new ArrayList<>();
        int files = 0;
        for (Path folder : FOLDERS) {
            assertTrue(Files.exists(folder), "no encuentro " + folder.toAbsolutePath());
            try (Stream<Path> paths = Files.walk(folder)) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    files++;
                    String name = file.getFileName().toString();
                    if (THEME.contains(name)) {
                        continue;
                    }
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int index = 0; index < lines.size(); index++) {
                        String line = code(lines.get(index));
                        Matcher matcher = LITERAL.matcher(line);
                        while (matcher.find()) {
                            String literal = matcher.group().toUpperCase().replace("0X", "0x");
                            if (MASKS.contains(literal) || ALPHA.matcher(literal).matches()
                                    && line.indexOf('|', matcher.end()) >= 0) {
                                continue;
                            }
                            if (WORLD_PICTURES.contains(name) && NAMED.matcher(line).find()) {
                                continue;
                            }
                            found.add(file + ":" + (index + 1) + "  " + lines.get(index).strip());
                        }
                    }
                }
            }
        }
        assertTrue(files > 20, "solo " + files + " archivos: ¿han cambiado las carpetas del Studio?");
        assertTrue(found.isEmpty(), "colores escritos a mano; usa una ficha de StudioTheme (o crea una, con su valor"
                + " en cada tema de StudioPalette):\n" + String.join("\n", found));
    }

    /** La línea sin el comentario del final (un color en un comentario no pinta nada). */
    private static String code(String line) {
        int comment = line.indexOf("//");
        String code = comment >= 0 ? line.substring(0, comment) : line;
        String trimmed = code.strip();
        return trimmed.startsWith("*") || trimmed.startsWith("/*") ? "" : code;
    }
}
