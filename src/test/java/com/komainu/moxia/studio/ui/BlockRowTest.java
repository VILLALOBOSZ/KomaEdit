package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.layout.Rect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La fila de bloque reparte el sitio entre la etiqueta y el nombre del bloque sin cortar lo que cabe. */
class BlockRowTest {
    /** Lo pintado en una fila de {@code width} píxeles. */
    private static List<String> row(int width, String label, String name) {
        ImageCanvas canvas = new ImageCanvas();
        StudioUi ui = new StudioUi(canvas);
        ui.begin(-1, -1, true);
        ui.blockRow(new Rect(0, 0, width, 20), new StudioUi.BlockRow(label, "minecraft:oak_log", name, false, false,
                "", true, ""));
        return canvas.texts().stream().map(ImageCanvas.Drawn::text).toList();
    }

    /** Icono, etiqueta, hueco, nombre, hueco y cuentagotas: lo justo para las dos cosas enteras. */
    private static int exactly(String label, String name) {
        return 23 + ImageCanvas.font().width(label) + 6 + ImageCanvas.font().width(name) + 4 + 18;
    }

    @Test
    void aShortBlockNameLeavesTheLongLabelWhole() {
        // «Material de raíces» del árbol con el panel estrecho: antes la etiqueta no pasaba de la mitad de la fila.
        String label = "Material de raíces";
        String name = "Roble";
        assertEquals(List.of(label, name), row(exactly(label, name), label, name));
    }

    @Test
    void aShortLabelLeavesTheLongBlockNameWhole() {
        String label = "Losas";
        String name = "De la familia del material";
        assertEquals(List.of(label, name), row(exactly(label, name), label, name));
    }

    @Test
    void whenNeitherFitsTheLabelKeepsHalfAndTheNameIsCut() {
        String label = "Muros y hastiales";
        String name = "El de los muros de la selección";
        List<String> texts = row(150, label, name);
        assertTrue(texts.get(0).endsWith("…") && texts.get(1).endsWith("…"), texts.toString());
    }
}
