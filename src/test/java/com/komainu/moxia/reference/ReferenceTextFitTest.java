package com.komainu.moxia.reference;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.studio.ui.ImageCanvas;
import com.komainu.moxia.studio.ui.MinecraftFont;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los textos de las imágenes de referencia caben donde van, medidos con la fuente del juego en los dos idiomas: la
 * action bar al editar (a 1080p con escala 3 la pantalla mide 640 de ancho), y las etiquetas de los botones de la
 * lista en la ventana más pequeña (854×480 con escala 2: columnas de unos 105).
 */
class ReferenceTextFitTest {
    /** Lo que deja la action bar sin pegarse a los bordes a 640 de ancho. */
    private static final int ACTION_BAR = 600;
    /** Media columna de ajustes a 427 de ancho, menos el margen del botón. */
    private static final int BUTTON_LABEL = 98;

    private static JsonObject lang(String name) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of("src", "main", "resources", "assets", "komaedit",
                "lang", name + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static String tr(JsonObject lang, String key, Object... arguments) {
        String value = lang.get(key).getAsString();
        return arguments.length == 0 ? value.replace("%%", "%") : String.format(value, arguments);
    }

    @Test
    void theActionBarWhileEditingFits() throws IOException {
        MinecraftFont font = ImageCanvas.font();
        assertTrue(font.loaded(), "la fuente del juego tiene que estar en el classpath");
        for (String language : List.of("es_es", "en_us")) {
            JsonObject lang = lang(language);
            String start = Feedback.PREFIX + " · castillo_de_noche · " + tr(lang,
                    "gui.komaedit.reference.size_blocks", "128.25", "72.06") + " · ";
            for (String end : List.of(tr(lang, "gui.komaedit.reference.hud.hint"),
                    tr(lang, "gui.komaedit.reference.hud.turning", "-135"),
                    tr(lang, "gui.komaedit.reference.hud.moving", "+12.5, -3, +0.06"),
                    tr(lang, "gui.komaedit.reference.hud.scaling"),
                    tr(lang, "gui.komaedit.reference.hud.ghost"))) {
                int width = font.width(start + end);
                assertTrue(width <= ACTION_BAR, language + ": " + width + " «" + start + end + "»");
            }
        }
    }

    @Test
    void theSettingButtonsFitTheSmallestScreen() throws IOException {
        MinecraftFont font = ImageCanvas.font();
        for (String language : List.of("es_es", "en_us")) {
            JsonObject lang = lang(language);
            String on = language.equals("es_es") ? "Sí" : "ON";
            String off = language.equals("es_es") ? "No" : "OFF";
            List<String> labels = List.of(
                    tr(lang, "gui.komaedit.reference.mode") + ": " + tr(lang, "gui.komaedit.reference.mode.screen"),
                    tr(lang, "gui.komaedit.reference.mode") + ": " + tr(lang, "gui.komaedit.reference.mode.world"),
                    tr(lang, "gui.komaedit.reference.visible") + ": " + off,
                    tr(lang, "gui.komaedit.reference.opacity", 100),
                    tr(lang, "gui.komaedit.reference.filter") + ": " + tr(lang, "gui.komaedit.reference.filter.sharp"),
                    tr(lang, "gui.komaedit.reference.filter") + ": "
                            + tr(lang, "gui.komaedit.reference.filter.smooth"),
                    tr(lang, "gui.komaedit.reference.grid") + ": " + on,
                    tr(lang, "gui.komaedit.reference.grid.pixels", 512),
                    tr(lang, "gui.komaedit.reference.grid.color") + ": "
                            + tr(lang, "gui.komaedit.reference.grid.color.white"),
                    tr(lang, "gui.komaedit.reference.grid.major") + ": "
                            + tr(lang, "gui.komaedit.reference.grid.major.every", 16),
                    tr(lang, "gui.komaedit.reference.grid.offset_x", 511),
                    tr(lang, "gui.komaedit.reference.grid.offset_y", 511),
                    tr(lang, "gui.komaedit.reference.through") + ": " + off,
                    tr(lang, "gui.komaedit.reference.arrange"),
                    tr(lang, "gui.komaedit.reference.reset"),
                    tr(lang, "gui.komaedit.reference.remove"),
                    tr(lang, "gui.komaedit.reference.depth") + ": " + off,
                    tr(lang, "gui.komaedit.reference.cell_block"),
                    tr(lang, "gui.komaedit.reference.bring"),
                    tr(lang, "gui.komaedit.reference.floor"),
                    tr(lang, "gui.komaedit.reference.gizmo"));
            for (String label : labels) {
                assertTrue(font.width(label) <= BUTTON_LABEL, language + ": " + font.width(label) + " «" + label
                        + "»");
            }
        }
    }
}
