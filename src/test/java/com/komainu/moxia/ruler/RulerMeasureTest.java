package com.komainu.moxia.ruler;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las medidas de la regla en cada modo, contadas como al construir: de centro a centro y cajas por bloques. */
class RulerMeasureTest {
    private static final double EPSILON = 1.0E-9;

    @Test
    void aLineBetweenTwoBlocksMeasuresCenterToCenter() {
        RulerMeasure.Line line = RulerMeasure.Line.of(java.util.List.of(RulerPoint.block(0, 64, 0),
                RulerPoint.block(3, 68, 0)));
        RulerMeasure.Segment segment = line.segments().getFirst();
        assertEquals(3.0, segment.delta().x(), EPSILON);
        assertEquals(4.0, segment.delta().y(), EPSILON);
        assertEquals(0.0, segment.delta().z(), EPSILON);
        assertEquals(5.0, line.length(), EPSILON, "3-4-5");
        assertEquals(7.0, line.manhattan(), EPSILON);
        // Una línea de WorldEdit entre los dos pone max(3, 4, 0) + 1 bloques.
        assertEquals(5, segment.blocks());
        // Del bloque al de al lado hay 1, no 0 ni 2.
        assertEquals(1.0, RulerMeasure.Line.of(java.util.List.of(RulerPoint.block(5, 5, 5),
                RulerPoint.block(6, 5, 5))).length(), EPSILON);
    }

    @Test
    void aPathAddsItsSegmentsPerAxisAndAlsoGivesTheStraightLine() {
        Ruler ruler = new Ruler();
        ruler.add(RulerPoint.block(0, 64, 0));
        ruler.add(RulerPoint.block(10, 64, 0));
        ruler.add(RulerPoint.block(10, 64, -6));
        ruler.add(RulerPoint.block(10, 70, -6));
        RulerMeasure.Line line = assertInstanceOf(RulerMeasure.Line.class, ruler.measure());
        assertEquals(3, line.segments().size());
        assertEquals(22.0, line.length(), EPSILON, "10 + 6 + 6");
        assertEquals(22.0, line.manhattan(), EPSILON);
        assertEquals(10.0, line.axes().x(), EPSILON);
        assertEquals(6.0, line.axes().y(), EPSILON);
        assertEquals(6.0, line.axes().z(), EPSILON);
        assertEquals(Math.sqrt(100 + 36 + 36), line.straight().euclidean(), EPSILON);
        assertEquals(22.0, line.straight().manhattan(), EPSILON);

        // Ida y vuelta: el recorrido suma, en línea recta queda en 0.
        Ruler back = new Ruler();
        back.add(RulerPoint.block(0, 0, 0));
        back.add(RulerPoint.block(4, 0, 0));
        back.add(RulerPoint.block(0, 0, 0));
        RulerMeasure.Line round = assertInstanceOf(RulerMeasure.Line.class, back.measure());
        assertEquals(8.0, round.length(), EPSILON);
        assertEquals(8.0, round.axes().x(), EPSILON);
        assertEquals(0.0, round.straight().euclidean(), EPSILON);
    }

    @Test
    void exactPointsMeasureAsTheyAre() {
        RulerMeasure.Line line = RulerMeasure.Line.of(java.util.List.of(
                RulerPoint.exact(new Vec3(0.25, 64.0, 0.0)), RulerPoint.exact(new Vec3(1.75, 64.0, 2.0))));
        assertEquals(Math.hypot(1.5, 2.0), line.length(), EPSILON);
        assertEquals(0, line.segments().getFirst().blocks(), "entre puntos sueltos no se cuentan bloques");
        RulerMeasure.Box box = RulerMeasure.Box.of(RulerPoint.exact(new Vec3(0, 0, 0)),
                RulerPoint.exact(new Vec3(2.5, 1, 4)));
        assertEquals(2.5, box.width(), EPSILON);
        assertEquals(1.0, box.height(), EPSILON);
        assertEquals(4.0, box.length(), EPSILON);
        assertFalse(box.blocks());
    }

    @Test
    void aBoxCountsTheBlocksOfBothEnds() {
        RulerMeasure.Box box = RulerMeasure.Box.of(RulerPoint.block(10, 64, 5), RulerPoint.block(21, 68, -2));
        assertEquals(12.0, box.width(), EPSILON, "de 10 a 21 son 12");
        assertEquals(5.0, box.height(), EPSILON, "de 64 a 68 son 5");
        assertEquals(8.0, box.length(), EPSILON, "de -2 a 5 son 8");
        assertEquals(480.0, box.volume(), EPSILON);
        assertEquals(Math.sqrt(144 + 25 + 64), box.diagonal(), EPSILON);
        assertEquals(new Vec3(10, 64, -2), box.min());
        assertEquals(new Vec3(22, 69, 6), box.max(), "las caras de fuera del último bloque");
        // Un solo bloque es 1 × 1 × 1.
        RulerMeasure.Box single = RulerMeasure.Box.of(RulerPoint.block(3, 3, 3), RulerPoint.block(3, 3, 3));
        assertEquals(1.0, single.volume(), EPSILON);
    }

    @Test
    void aFlatCircleGivesRadiusDiameterAndBlocksAcross() {
        RulerMeasure.Circle circle = RulerMeasure.Circle.of(RulerPoint.block(0, 64, 0), RulerPoint.block(6, 64, 0));
        assertEquals(1, circle.axis(), "a la misma altura, horizontal");
        assertEquals("XZ", circle.plane());
        assertEquals(6.0, circle.radius(), EPSILON);
        assertEquals(12.0, circle.diameter(), EPSILON);
        assertEquals(13, circle.blocksAcross(), "//cyl con radio 6 mide 13");
        assertEquals(2 * Math.PI * 6, circle.circumference(), EPSILON);
        assertEquals(Math.PI * 36, circle.area(), EPSILON);
        assertEquals(0.0, circle.offset(), EPSILON);
        // En diagonal también cuenta el radio en el plano.
        RulerMeasure.Circle diagonal = RulerMeasure.Circle.of(RulerPoint.block(0, 64, 0),
                RulerPoint.block(3, 64, 4));
        assertEquals(5.0, diagonal.radius(), EPSILON);
        assertEquals(11, diagonal.blocksAcross());
    }

    @Test
    void anUprightCircleTakesThePlaneOfTheTwoLargestDifferences() {
        // Un arco en la pared: misma Z, el círculo va en el plano XY.
        RulerMeasure.Circle arch = RulerMeasure.Circle.of(RulerPoint.block(0, 64, 0), RulerPoint.block(0, 72, 0));
        assertEquals(0, arch.axis(), "X y Z no cambian: a igualdad gana X tras el vertical");
        RulerMeasure.Circle wall = RulerMeasure.Circle.of(RulerPoint.block(0, 64, 0), RulerPoint.block(5, 69, 1));
        assertEquals(2, wall.axis());
        assertEquals("XY", wall.plane());
        assertEquals(Math.hypot(5, 5), wall.radius(), EPSILON);
        assertEquals(1.0, wall.offset(), EPSILON, "el borde se sale 1 del plano");
    }

    @Test
    void theRulerKeepsItsPointsPerMode() {
        Ruler ruler = new Ruler();
        assertEquals(RulerMode.LINE, ruler.mode());
        assertInstanceOf(RulerMeasure.Pending.class, ruler.measure());
        assertTrue(ruler.add(RulerPoint.block(0, 0, 0)));
        assertFalse(ruler.add(RulerPoint.block(0, 0, 0)), "el mismo punto dos veces no cuenta");
        assertTrue(ruler.add(RulerPoint.block(1, 0, 0)));
        assertTrue(ruler.add(RulerPoint.block(2, 0, 0)));
        // Al pasar a caja quedan los dos primeros.
        ruler.mode(RulerMode.BOX);
        assertEquals(2, ruler.points().size());
        assertInstanceOf(RulerMeasure.Box.class, ruler.measure());
        // El tercer clic de una caja empieza otra.
        assertTrue(ruler.add(RulerPoint.block(9, 9, 9)));
        assertEquals(1, ruler.points().size());
        assertEquals(RulerPoint.block(9, 9, 9), ruler.points().getFirst());
        assertTrue(ruler.removeLast());
        assertFalse(ruler.removeLast());
        assertEquals(RulerMode.LINE, ruler.cycleMode(), "tras la caja vuelve al recorrido");
        assertEquals(RulerMode.CIRCLE, ruler.cycleMode());
    }

    @Test
    void aPathStopsAtItsLimit() {
        Ruler ruler = new Ruler();
        for (int index = 0; index < Ruler.MAX_POINTS; index++) {
            assertTrue(ruler.add(RulerPoint.block(index, 0, 0)));
        }
        assertFalse(ruler.add(RulerPoint.block(99, 0, 0)));
        assertEquals(Ruler.MAX_POINTS, ruler.points().size());
        ruler.clear();
        assertTrue(ruler.isEmpty());
    }

    @Test
    void modesAreFoundBySpanishEnglishOrAlias() {
        assertEquals(RulerMode.CIRCLE, RulerMode.byName("círculo"));
        assertEquals(RulerMode.CIRCLE, RulerMode.byName("Circle"));
        assertEquals(RulerMode.BOX, RulerMode.byName("caja"));
        assertEquals(RulerMode.LINE, RulerMode.byName(" linea "));
        assertNull(RulerMode.byName("triángulo"));
    }

    @Test
    void numbersDropZeroDecimalsAndUseTheLanguageSeparator() {
        assertEquals("12", RulerFormat.number(12.0, ','));
        assertEquals("12,4", RulerFormat.number(12.44, ','));
        assertEquals("12.5", RulerFormat.number(12.46, '.'));
        assertEquals("13", RulerFormat.number(12.96, ','));
        assertEquals("1 250", RulerFormat.number(1250, ','));
        assertEquals("1 250,5", RulerFormat.number(1250.5, ','));
        assertEquals("-0,5", RulerFormat.number(-0.5, ','));
        assertEquals("0", RulerFormat.number(0.04, ','));
        assertEquals("(1, 2, 3)", RulerPoint.block(1, 2, 3).coordinates());
        assertEquals("(1.5, 2, -3.2)", RulerPoint.exact(new Vec3(1.5, 2, -3.25)).coordinates());
    }
}
