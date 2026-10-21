package com.komainu.moxia.ruler;

import com.komainu.moxia.feedback.LangWords;
import com.komainu.moxia.feedback.RenderScene;
import com.komainu.moxia.feedback.SketchRaster;
import com.komainu.moxia.gizmo.PerspectiveRaster;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders de la regla en el mundo (solo con {@code KOMAEDIT_UTILITIES_RENDER} apuntando a una carpeta; con
 * {@code KOMAEDIT_MC_JAR}, con las texturas del juego). Usan lo mismo que el cliente: {@link RulerSketch} y el
 * resumen de la barra de acción ({@link RulerReport#summary}); el informe del portapapeles va en {@code regla.txt}.
 */
class RulerRenderTest {
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;

    @Test
    void rulerInTheWorld() throws IOException {
        String output = System.getenv("KOMAEDIT_UTILITIES_RENDER");
        Assumptions.assumeTrue(output != null && !output.isBlank(), "KOMAEDIT_UTILITIES_RENDER sin definir");
        Path folder = Path.of(output);
        Files.createDirectories(folder);
        RenderScene scene = RenderScene.village();
        LangWords words = LangWords.spanish();
        List<String> report = new ArrayList<>();

        // Recorrido: de la esquina de la casa a un tronco y subiendo por él.
        Ruler path = new Ruler();
        path.add(RulerPoint.block(11, 64, 0));
        path.add(RulerPoint.block(16, 64, 2));
        path.add(RulerPoint.block(16, 70, 12));
        shot(folder, "1_recorrido", scene, path, null, new Vec3(26.5, 75.0, -9.5), new Vec3(13, 66, 5), words,
                report);
        // Lo que mediría el siguiente clic, mientras se apunta.
        shot(folder, "2_recorrido_apuntando", scene, path, RulerPoint.block(6, 69, 7), new Vec3(26.5, 75.0, -9.5),
                new Vec3(13, 66, 5), words, report);

        // Caja: la casa entera de esquina a esquina.
        Ruler box = new Ruler();
        box.mode(RulerMode.BOX);
        box.add(RulerPoint.block(0, 64, 0));
        box.add(RulerPoint.block(11, 69, 7));
        shot(folder, "3_caja_casa", scene, box, null, new Vec3(-9.5, 76.0, -10.5), new Vec3(6, 66, 4), words,
                report);

        // Círculo en el suelo: una plaza de radio 6 delante de la casa.
        Ruler circle = new Ruler();
        circle.mode(RulerMode.CIRCLE);
        circle.add(RulerPoint.block(6, 63, 15));
        circle.add(RulerPoint.block(12, 63, 15));
        shot(folder, "4_circulo_plaza", scene, circle, null, new Vec3(-4.5, 75.0, 25.5), new Vec3(6, 63, 15),
                words, report);

        // Un arco en la fachada: el círculo va de pie, en el plano de la pared.
        Ruler arch = new Ruler();
        arch.mode(RulerMode.CIRCLE);
        arch.add(RulerPoint.block(6, 66, -1));
        arch.add(RulerPoint.block(9, 68, -1));
        shot(folder, "5_circulo_de_pie", scene, arch, null, new Vec3(4.5, 68.0, -12.5), new Vec3(6, 66, 0), words,
                report);

        report.add("");
        report.add("Portapapeles del recorrido:");
        report.add(RulerReport.text(path, words));
        report.add("");
        report.add("Portapapeles de la caja:");
        report.add(RulerReport.text(box, words));
        report.add("");
        report.add("Texturas de Minecraft: " + (System.getenv("KOMAEDIT_MC_JAR") == null ? "no" : "sí"));
        Files.writeString(folder.resolve("regla.txt"), String.join("\n", report) + "\n");
    }

    private static void shot(Path folder, String name, RenderScene scene, Ruler ruler, RulerPoint hover, Vec3 eye,
                             Vec3 target, LangWords words, List<String> report) throws IOException {
        try (PerspectiveRaster raster = RenderScene.camera(WIDTH, HEIGHT, eye, target)) {
            scene.draw(raster);
            SketchRaster.draw(raster, RulerSketch.of(ruler, eye, hover, words));
            String summary = RulerReport.summary(ruler, words);
            raster.actionBar("KE · " + summary);
            raster.write(folder.resolve("regla_" + name + ".png"));
            report.add(name + ": " + summary);
        }
    }
}
