package com.komainu.moxia.ruler;

import com.komainu.moxia.feedback.LangWords;
import com.komainu.moxia.feedback.WorldSketch;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lo que se lee de la regla: la barra de acción, el portapapeles y los rótulos del mundo, con los idiomas reales. */
class RulerReportTest {
    private static final LangWords SPANISH = LangWords.spanish();
    private static final LangWords ENGLISH = LangWords.english();

    private static Ruler path() {
        Ruler ruler = new Ruler();
        ruler.add(RulerPoint.block(0, 64, 0));
        ruler.add(RulerPoint.block(10, 64, 0));
        ruler.add(RulerPoint.block(10, 66, 5));
        return ruler;
    }

    @Test
    void thePathReportHasEverySegmentTheTotalsAndThePoints() {
        String text = RulerReport.text(path(), SPANISH);
        List<String> lines = text.lines().toList();
        assertEquals("Regla · recorrido · 3 puntos", lines.get(0));
        assertEquals("1 → 2: 10 · Manhattan 10 · X 10 · Y 0 · Z 0 · 11 bloques", lines.get(1));
        assertEquals("2 → 3: 5,4 · Manhattan 7 · X 0 · Y 2 · Z 5 · 6 bloques", lines.get(2));
        assertEquals("Recorrido: 15,4 · Manhattan 17 · X 10 · Y 2 · Z 5", lines.get(3));
        assertEquals("En línea recta (1 → 3): 11,4 · Manhattan 17 · X 10 · Y 2 · Z 5", lines.get(4));
        assertEquals("Puntos: (0, 64, 0) (10, 64, 0) (10, 66, 5)", lines.get(5));
        assertEquals(6, lines.size());
        // En inglés, con punto decimal.
        assertTrue(RulerReport.text(path(), ENGLISH).contains("Path: 15.4 · Manhattan 17"));
    }

    @Test
    void circleAndBoxReports() {
        Ruler circle = new Ruler();
        circle.mode(RulerMode.CIRCLE);
        circle.add(RulerPoint.block(0, 64, 0));
        circle.add(RulerPoint.block(6, 64, 0));
        assertEquals(String.join("\n",
                "Regla · círculo",
                "Centro (0, 64, 0) · borde (6, 64, 0) · plano XZ",
                "Radio 6 · diámetro 12 · 13 bloques de lado a lado",
                "Circunferencia 37,7 · área 113,1"), RulerReport.text(circle, SPANISH));

        Ruler box = new Ruler();
        box.mode(RulerMode.BOX);
        box.add(RulerPoint.block(10, 64, 5));
        box.add(RulerPoint.block(21, 68, 12));
        assertEquals(String.join("\n",
                "Regla · caja",
                "De (10, 64, 5) a (21, 68, 12)",
                "12 × 5 × 8 (ancho × alto × largo)",
                "480 bloques · diagonal 15,3"), RulerReport.text(box, SPANISH));
        assertEquals("Ruler · box · 12 × 5 × 8 · 480", RulerReport.summary(box, ENGLISH));
    }

    @Test
    void theSummarySaysWhatIsMissing() {
        Ruler ruler = new Ruler();
        assertEquals("Regla · recorrido · clic derecho en un bloque: primer punto",
                RulerReport.summary(ruler, SPANISH));
        ruler.mode(RulerMode.CIRCLE);
        ruler.add(RulerPoint.block(0, 0, 0));
        assertEquals("Regla · círculo · ahora un punto del borde", RulerReport.summary(ruler, SPANISH));
        ruler.mode(RulerMode.BOX);
        assertEquals("Regla · caja · ahora la esquina opuesta", RulerReport.summary(ruler, SPANISH));
        assertEquals("Regla · recorrido · 3 puntos · 15,4 · Manhattan 17", RulerReport.summary(path(), SPANISH));
    }

    @Test
    void theWorldShowsEachSegmentTheLegsOfTheLastOneAndTheTotal() {
        WorldSketch sketch = RulerSketch.of(path(), new Vec3(-5, 70, -5), null, SPANISH);
        List<String> texts = sketch.labels().stream().flatMap(label -> label.rows().stream())
                .map(WorldSketch.Row::text).toList();
        assertTrue(texts.containsAll(List.of("1", "2", "3")), texts.toString());
        assertTrue(texts.containsAll(List.of("10", "5,4")), "cada tramo con su medida: " + texts);
        assertTrue(texts.containsAll(List.of("Y 2", "Z 5")), "las patas del último tramo: " + texts);
        assertFalse(texts.contains("X 0"), "una pata de largo 0 no se dibuja");
        WorldSketch.Label last = sketch.labels().stream()
                .filter(label -> label.rows().getFirst().text().equals("3")).findFirst().orElseThrow();
        assertEquals(List.of("3", "Total 15,4 · Manhattan 17"), last.rows().stream().map(WorldSketch.Row::text)
                .toList(), "el total va con el número del último punto, para que no se tapen");
        assertTrue(sketch.lines().stream().anyMatch(WorldSketch.Line::seeThrough),
                "lo medido se ve a través de los bloques");
    }

    @Test
    void whileAimingTheNextClickIsPreviewed() {
        Ruler box = new Ruler();
        box.mode(RulerMode.BOX);
        box.add(RulerPoint.block(0, 64, 0));
        WorldSketch sketch = RulerSketch.of(box, new Vec3(-5, 70, -5), RulerPoint.block(3, 65, 9), SPANISH);
        List<String> texts = sketch.labels().stream().map(label -> label.rows().getFirst().text()).toList();
        assertTrue(texts.contains("4 × 2 × 10 · 80 bloques"), texts.toString());

        Ruler circle = new Ruler();
        circle.mode(RulerMode.CIRCLE);
        circle.add(RulerPoint.block(0, 64, 0));
        texts = RulerSketch.of(circle, new Vec3(0, 70, -10), RulerPoint.block(0, 64, 5), SPANISH).labels().stream()
                .map(label -> label.rows().getFirst().text()).toList();
        assertTrue(texts.contains("r 5"), texts.toString());
        assertTrue(texts.contains("Ø 10 · 11 bloques"), texts.toString());
    }

    @Test
    void theBoxSidesGoOnTheEdgesNearestTheCamera() {
        Ruler box = new Ruler();
        box.mode(RulerMode.BOX);
        box.add(RulerPoint.block(0, 64, 0));
        box.add(RulerPoint.block(9, 67, 4));
        WorldSketch fromNorthWest = RulerSketch.of(box, new Vec3(-10, 80, -10), null, SPANISH);
        WorldSketch.Label width = fromNorthWest.labels().stream()
                .filter(label -> label.rows().getFirst().text().equals("10")).findFirst().orElseThrow();
        assertEquals(68.0, width.at().y(), 1.0E-9, "arriba, cerca de la cámara");
        assertEquals(0.0, width.at().z(), 1.0E-9, "la cara norte");
        WorldSketch fromSouthEast = RulerSketch.of(box, new Vec3(20, 50, 20), null, SPANISH);
        WorldSketch.Label moved = fromSouthEast.labels().stream()
                .filter(label -> label.rows().getFirst().text().equals("10")).findFirst().orElseThrow();
        assertEquals(64.0, moved.at().y(), 1.0E-9);
        assertEquals(5.0, moved.at().z(), 1.0E-9);
    }
}
