package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.NumericRange;
import com.komainu.moxia.studio.ValueUnit;
import com.komainu.moxia.studio.tools.ToolCard;
import com.komainu.moxia.studio.tools.ToolCatalog;
import com.komainu.moxia.studio.tools.ToolParam;
import org.junit.jupiter.api.Test;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El modelo de cada fila numérica: unidad, rango, de serie marcado, reinicio, arrastre (Mayús: fino) y escribir el
 * valor en su unidad.
 */
class NumberControlTest {
    private static final FakeStudioHost SPANISH = new FakeStudioHost("es_es");
    private static final FakeStudioHost ENGLISH = new FakeStudioHost("en_us");
    private static final BiFunction<String, Object[], String> ES = SPANISH::tr;
    private static final BiFunction<String, Object[], String> EN = ENGLISH::tr;

    @Test
    void unitsReadLikeWords() {
        assertEquals("18 bloques", ValueUnit.BLOCKS.format(18, 0, ES));
        assertEquals("1 bloque", ValueUnit.BLOCKS.format(1, 0, ES), "en singular");
        assertEquals("4.5 bloques", ValueUnit.BLOCKS.format(45, 1, ES));
        assertEquals("18 blocks", ValueUnit.BLOCKS.format(18, 0, EN));
        assertEquals("18 bl.", ValueUnit.BLOCKS.formatShort(18, 0, ES));
        assertEquals("150 %", ValueUnit.PERCENT.format(150, 2, ES), "1.5 es el 150 %");
        assertEquals("150%", ValueUnit.PERCENT.format(150, 2, EN));
        assertEquals("100 %", ValueUnit.PERCENT.format(10, 1, ES), "1.0 con un decimal");
        assertEquals("40 %", ValueUnit.PERCENT_POINTS.format(40, 0, ES));
        assertEquals("45°", ValueUnit.DEGREES.format(45, 0, ES));
        assertEquals("×1.3", ValueUnit.MULTIPLIER.format(13, 1, ES));
        assertEquals("1:1", ValueUnit.RATIO.format(10, 1, ES));
        assertEquals("3 capas", ValueUnit.LAYERS.format(3, 0, ES));
        assertEquals("1 capa", ValueUnit.LAYERS.format(1, 0, ES));
        assertEquals("40 pasadas", ValueUnit.TIMES.format(40, 0, ES));
        assertEquals("y 62", ValueUnit.Y.format(62, 0, ES));
        assertEquals("9", ValueUnit.NONE.format(9, 0, ES));
    }

    @Test
    void typedValuesAreReadInTheirUnit() {
        assertEquals(150, ValueUnit.PERCENT.parse("150", 2), "150 % son 1.5");
        assertEquals(150, ValueUnit.PERCENT.parse("150 %", 2));
        assertEquals(125, ValueUnit.PERCENT.parse("125,0", 2));
        assertEquals(45, ValueUnit.BLOCKS.parse("4,5", 1));
        assertEquals(-3, ValueUnit.BLOCKS.parse("−3", 0));
        assertEquals(90, ValueUnit.DEGREES.parse("90°", 0));
        assertNull(ValueUnit.BLOCKS.parse("", 0));
        assertNull(ValueUnit.BLOCKS.parse("-", 0));
        assertNull(ValueUnit.BLOCKS.parse("doce", 0));
        assertNull(ValueUnit.BLOCKS.parse("99999999999999", 0));
    }

    @Test
    void everyNumberOfTheToolsHasAUnitThatMakesSense() {
        for (ToolCard card : ToolCatalog.ALL) {
            for (ToolParam param : card.params()) {
                if (!(param instanceof ToolParam.Number number)) {
                    continue;
                }
                boolean count = switch (number.id()) {
                    case "seed", "dormers", "sides", "facets", "count", "lines", "arms", "spacing" -> true;
                    default -> false;
                };
                assertEquals(count, number.unit() == ValueUnit.NONE, card.id() + "/" + number.id()
                        + ": solo las cuentas van sin unidad");
                // Los multiplicadores de dos decimales se leen en tanto por ciento, nunca «1.0» suelto.
                if (number.decimals() == 2) {
                    assertTrue(number.unit() == ValueUnit.PERCENT || number.unit() == ValueUnit.MULTIPLIER,
                            card.id() + "/" + number.id());
                }
            }
        }
    }

    @Test
    void theViewMarksRangeDefaultAndModified() {
        NumericRange range = new NumericRange(25, 400, 5, 25);
        NumberView unchanged = NumberView.of(100, null, 100, 100, false, range, 2, ValueUnit.PERCENT, ES);
        assertEquals("100 %", unchanged.value());
        assertEquals("25–400 %", unchanged.range());
        assertFalse(unchanged.modified());
        assertEquals(unchanged.fraction(), unchanged.defaultFraction(), 1e-9, "la marca de serie, bajo el valor");
        NumberView changed = NumberView.of(160, null, 160, 100, false, range, 2, ValueUnit.PERCENT, ES);
        assertTrue(changed.modified(), "fuera de serie: punto rosa");
        assertTrue(changed.fraction() > changed.defaultFraction());
    }

    @Test
    void automaticNumbersSayWhatTheyWillUse() {
        NumericRange range = new NumericRange(3, 64, 1, 5);
        NumberView automatic = NumberView.of(null, "18", 18, 18, true, range, 0, ValueUnit.BLOCKS, ES);
        assertEquals("auto · 18 bloques", automatic.value());
        assertEquals(18, automatic.base(), "la rueda arranca del automático");
        assertTrue(Double.isNaN(automatic.defaultFraction()), "en automático no hay «de serie» que marcar");
        assertFalse(automatic.modified());
        NumberView unknown = NumberView.of(null, null, 0, 0, true, range, 0, ValueUnit.BLOCKS, ES);
        assertEquals("auto", unknown.value());
        NumberView span = NumberView.of(null, "15%", 3, 3, true, range, 0, ValueUnit.BLOCKS, ES);
        assertEquals("15%", span.value(), "un automático que no es de la unidad se enseña tal cual");
    }

    @Test
    void pressingWithoutMovingOpensTheFieldAndDraggingSlides() {
        NumberEdit edit = new NumberEdit();
        NumericRange range = NumericRange.of(1, 8, 1);
        edit.press("layers", 2, 100);
        assertTrue(edit.dragging("layers"));
        assertNull(edit.drag(102, range, false), "dos píxeles aún no son arrastre");
        assertTrue(edit.release("2"), "soltar sin mover abre el campo");
        assertTrue(edit.editing("layers"));
        edit.erase(true);
        edit.type('5');
        edit.type('x');
        assertEquals("5", edit.input(), "solo cifras, signo y coma");
        assertEquals("5", edit.commit());
        assertFalse(edit.editing());

        edit.press("layers", 2, 100);
        assertEquals(4, edit.drag(100 + 2 * NumericRange.PIXELS_PER_STEP, range, false));
        assertEquals(3, edit.drag(100 + NumericRange.FINE_PIXELS_PER_STEP, range, true), "con Mayús, fino");
        assertFalse(edit.release("3"), "tras arrastrar no se abre el campo");
        assertFalse(edit.editing());
    }

    @Test
    void escapeCancelsTheTypedValue() {
        NumberEdit edit = new NumberEdit();
        edit.open("height", "12");
        edit.type('3');
        edit.cancel();
        assertFalse(edit.editing());
        assertEquals("", edit.commit());
    }
}
