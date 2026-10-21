package com.komainu.moxia.notes;

import com.komainu.moxia.feedback.LangWords;
import com.komainu.moxia.feedback.RenderScene;
import com.komainu.moxia.feedback.SketchRaster;
import com.komainu.moxia.feedback.WorldSketch;
import com.komainu.moxia.gizmo.PerspectiveRaster;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Renders de las notas en el mundo (solo con {@code KOMAEDIT_UTILITIES_RENDER}; con {@code KOMAEDIT_MC_JAR}, con las
 * texturas del juego): ancladas a un bloque y a un punto, privadas y del equipo, de cerca (con autor y fecha), de
 * lejos, apagándose en su distancia y una resuelta con «ver resueltas». Todo sale de {@link NoteSketch}, como en el
 * cliente.
 */
class NoteRenderTest {
    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final long NOW = LocalDateTime.of(2026, 10, 21, 19, 0).toInstant(ZoneOffset.ofHours(2))
            .toEpochMilli();
    private static final UUID ANA = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID BEA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    private static final int UP = WorldNote.UP;
    private static final int NORTH = 2;
    private static final int WEST = 4;

    private static WorldNote note(String id, String text, NoteColor color, NoteIcon icon, double x, double y, double z,
                                  boolean block, int side, int distance, boolean team, UUID author, String name,
                                  long ago, boolean resolved) {
        return new WorldNote(id, text, color.rgb(), icon, "minecraft:overworld", x, y, z, block, side, distance, team,
                author, name, NOW - ago, resolved ? "Bea" : "", resolved ? NOW - 60_000 : 0);
    }

    private static List<WorldNote> notes() {
        long hour = 3_600_000L;
        return List.of(
                note("aaa234", "Falta el tejado del ala norte: pizarra, no madera", NoteColor.ROJO, NoteIcon.AVISO,
                        11, 69, 7, true, UP, 96, true, ANA, "Ana", 2 * hour, false),
                note("bbb234", "Ventanas de cristal tintado aquí", NoteColor.AZUL, NoteIcon.TAREA, 3, 66, 0, true,
                        NORTH, 48, true, BEA, "Bea", 26 * hour, false),
                note("ccc234", "¿Plaza redonda o cuadrada?", NoteColor.MORADO, NoteIcon.PREGUNTA, 6, 64.4, 15, false,
                        UP, 64, false, ANA, "Ana", 3 * hour, false),
                note("ddd234", "Puerta: dos de alto", NoteColor.VERDE, NoteIcon.IDEA, 0, 66, 3, true, WEST, 32, true,
                        BEA, "Bea", 5 * hour, true),
                note("eee234", "Árbol grande al fondo", NoteColor.AMARILLO, NoteIcon.ESTRELLA, 16, 70, 12, true, UP,
                        40, true, ANA, "Ana", 30 * hour, false));
    }

    @Test
    void notesInTheWorld() throws IOException {
        String output = System.getenv("KOMAEDIT_UTILITIES_RENDER");
        Assumptions.assumeTrue(output != null && !output.isBlank(), "KOMAEDIT_UTILITIES_RENDER sin definir");
        Path folder = Path.of(output);
        Files.createDirectories(folder);
        RenderScene scene = RenderScene.village();
        List<String> report = new ArrayList<>();
        shot(folder, "1_de_cerca", scene, new Vec3(-7.5, 70.0, -8.5), new Vec3(6, 66, 4), false, report);
        shot(folder, "2_de_lejos", scene, new Vec3(-30.5, 78.0, -34.5), new Vec3(6, 66, 4), false, report);
        shot(folder, "3_con_resueltas", scene, new Vec3(-8.5, 67.0, 2.5), new Vec3(0, 65.5, 3.5), true, report);
        shot(folder, "4_en_su_distancia", scene, new Vec3(20.5, 73.0, 44.5), new Vec3(14, 69, 10), false, report);
        report.add("Texturas de Minecraft: " + (System.getenv("KOMAEDIT_MC_JAR") == null ? "no" : "sí"));
        Files.writeString(folder.resolve("notas.txt"), String.join("\n", report) + "\n");
    }

    private static void shot(Path folder, String name, RenderScene scene, Vec3 eye, Vec3 target,
                             boolean showResolved, List<String> report) throws IOException {
        try (PerspectiveRaster raster = RenderScene.camera(WIDTH, HEIGHT, eye, target)) {
            scene.draw(raster);
            WorldSketch.Builder sketch = WorldSketch.builder();
            List<String> seen = new ArrayList<>();
            for (WorldNote note : notes()) {
                WorldSketch one = NoteSketch.of(note, eye, MADRID, NOW, showResolved, LangWords.spanish());
                sketch.add(one);
                for (WorldSketch.Label label : one.labels()) {
                    seen.add(note.id() + " (alfa " + Math.round(label.alpha() * 100) + " %, "
                            + label.rows().size() + " renglones)");
                }
            }
            SketchRaster.draw(raster, sketch.build());
            raster.write(folder.resolve("notas_" + name + ".png"));
            report.add(name + ": " + String.join(", ", seen));
        }
    }
}
