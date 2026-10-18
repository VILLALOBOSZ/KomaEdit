package com.komainu.moxia.placement;

import com.komainu.moxia.preview.PreviewFlags;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada ejemplo de colocación de la documentación tiene que leerse sin errores. */
class DocumentationExamplesTest {
    private static final Pattern EXAMPLE = Pattern.compile(
            "^/komaedit (place|colocar|array|hilera|scatter|dispersar) (.+)$", Pattern.MULTILINE);

    @Test
    void everyDocumentedPlacementCommandParses() throws IOException {
        String documentation = Files.readString(Path.of("docs", "MOD_DOCUMENTATION.md"));
        Matcher matcher = EXAMPLE.matcher(documentation);
        int examples = 0;
        while (matcher.find()) {
            String command = matcher.group(1);
            // La bandera de vista previa la quita la orden antes de llegar al analizador (ver PreviewFlags).
            String arguments = PreviewFlags.strip(matcher.group(2).strip(), true).text();
            if (arguments.startsWith("<") || arguments.equals("…")) {
                continue;
            }
            PlacementSettings.Kind kind = switch (command) {
                case "place", "colocar" -> PlacementSettings.Kind.PLACE;
                case "array", "hilera" -> PlacementSettings.Kind.ARRAY;
                default -> PlacementSettings.Kind.SCATTER;
            };
            assertDoesNotThrow(() -> {
                PlacementSettings settings = PlacementSettings.parse(kind, arguments);
                StructureSpec spec = StructureSpec.parse(settings.structure());
                if (spec.procedural()) {
                    spec.procedural(3).sampler(1L).sample(0, 0, 0);
                }
            }, () -> "/komaedit " + command + " " + arguments);
            examples++;
        }
        assertTrue(examples >= 25, "solo se encontraron " + examples + " ejemplos");
    }
}
