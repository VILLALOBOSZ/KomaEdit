package com.komainu.moxia.studio.world;

import com.komainu.moxia.selection.LassoPolygon;
import com.komainu.moxia.selection.SelectionCombine;
import com.komainu.moxia.studio.tools.SelectionCard;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolValues;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** El lazo y el pincel de selección del ratón del Studio, y lo que manda la tarjeta Seleccionar. */
class LassoDrawTest {
    @Test
    void aDraggedOutlineClosesAndAClickDoesNot() {
        LassoDraw lasso = new LassoDraw();
        lasso.begin(100, 100, SelectionCombine.ADD);
        assertTrue(lasso.active());
        assertFalse(lasso.add(100.5, 100.5), "un temblor no es un punto");
        for (int step = 1; step <= 600; step++) {
            double angle = Math.PI * 2 * step / 600;
            lasso.add(100 + 60 * Math.sin(angle), 100 - 60 + 60 * Math.cos(angle));
        }
        assertEquals(SelectionCombine.ADD, lasso.mode(), "el modo es el de cuando se empezó");
        LassoPolygon polygon = lasso.finish();
        assertNotNull(polygon);
        assertFalse(lasso.active());
        assertTrue(polygon.size() <= LassoPolygon.MAX_POINTS);
        assertTrue(polygon.contains(100, 40), "el centro del círculo dibujado");

        lasso.begin(10, 10, SelectionCombine.REPLACE);
        lasso.add(13, 10);
        assertNull(lasso.finish(), "un clic con un temblor no rodea nada");
        lasso.begin(10, 10, SelectionCombine.REPLACE);
        lasso.add(40, 10);
        lasso.add(70, 10);
        assertNull(lasso.finish(), "una raya tampoco");
        lasso.begin(10, 10, SelectionCombine.REPLACE);
        lasso.cancel();
        assertNull(lasso.finish(), "Esc lo descarta");
    }

    @Test
    void theBrushStrokeStartsOnTheFirstBlockAndThrottles() {
        SelectionStroke stroke = new SelectionStroke();
        assertEquals(SelectionStroke.Send.NOTHING, stroke.press(null, SelectionCombine.SUBTRACT, 1_000),
                "en el cielo aún no empieza");
        assertEquals(SelectionCombine.SUBTRACT, stroke.mode());
        assertEquals(SelectionStroke.Send.START, stroke.drag(new int[]{1, 64, 1}, 1_010));
        assertEquals(SelectionStroke.Send.NOTHING, stroke.drag(new int[]{1, 64, 1}, 1_100), "el mismo bloque");
        assertEquals(SelectionStroke.Send.NOTHING, stroke.drag(new int[]{2, 64, 1}, 1_020), "demasiado pronto");
        assertEquals(SelectionStroke.Send.DAB, stroke.drag(new int[]{2, 64, 1}, 1_010
                + SelectionStroke.INTERVAL_MILLIS));
        assertEquals(2, stroke.dabs());
        assertTrue(stroke.release(), "hay que avisar del final");
        assertFalse(stroke.active());
        assertEquals(SelectionStroke.Send.NOTHING, stroke.press(null, SelectionCombine.ADD, 5_000));
        assertFalse(stroke.release(), "un trazo que no tocó nada no se avisa");
    }

    @Test
    void theCardAppliesTheChosenOperation() {
        ToolCard card = ToolCatalog.byId("select");
        ToolValues values = card.defaults();
        assertEquals("komaedit sel grow 1 sphere -s", card.command(values, false));
        assertEquals("komaedit sel shrink 3 cube", card.command(values.with(SelectionCard.OPERATION, "shrink")
                .with(SelectionCard.AMOUNT, 3).with(SelectionCard.SHAPE, "cube").with(SelectionCard.SURFACE, false), false));
        assertEquals("komaedit sel complement -s -n", card.command(values.with(SelectionCard.OPERATION, "complement")
                .with(SelectionCard.NON_SOLID, true), false));
        assertEquals("komaedit sel box", card.command(values.with(SelectionCard.OPERATION, "box"), false));
        assertEquals("komaedit sel clear", card.command(values.with(SelectionCard.OPERATION, "clear"), false));
        assertEquals("komaedit sel undo", card.actions().getFirst().command().apply(values));

        SelectionCard.Settings lasso = SelectionCard.settings(values.with(SelectionCard.DEPTH, "layers").with(SelectionCard.LAYERS, 5));
        assertTrue(lasso.lassoTool());
        assertEquals(5, lasso.lasso().reach());
        SelectionCard.Settings brush = SelectionCard.settings(values.with(SelectionCard.TOOL, "brush").with(SelectionCard.RADIUS, 4)
                .with(SelectionCard.SHAPE, "octahedron"));
        assertTrue(brush.brushTool());
        assertEquals(4, brush.radius());
        assertEquals("octahedron", brush.shape().id());
        assertFalse(card.params().stream().filter(param -> param.id().equals(SelectionCard.DEPTH)).findFirst().orElseThrow()
                .visible(values.with(SelectionCard.TOOL, "brush")), "la profundidad es solo del lazo");
    }
}
