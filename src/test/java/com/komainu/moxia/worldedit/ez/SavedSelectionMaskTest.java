package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code #savedselection[nombre]}: deja pasar los bloques de dentro de la forma guardada, sea cual sea. */
class SavedSelectionMaskTest {
    @Test
    void cuboidKeepsItsInsideAndEdges() {
        Mask mask = EzMaskParser.insideOf(SelectionShape.cuboid(BlockVector3.at(14, 62, 11),
                BlockVector3.at(10, 60, 10), "minecraft:overworld"));
        assertTrue(mask.test(BlockVector3.at(12, 61, 10)));
        assertTrue(mask.test(BlockVector3.at(10, 60, 11)), "las esquinas cuentan");
        assertTrue(mask.test(BlockVector3.at(14, 62, 10)));
        assertFalse(mask.test(BlockVector3.at(15, 61, 10)));
        assertFalse(mask.test(BlockVector3.at(12, 63, 10)));
    }

    @Test
    void roundShapesFollowTheirCurve() {
        Mask sphere = EzMaskParser.insideOf(new SelectionShape(SelectionShape.Kind.SPHERE,
                List.of(BlockVector3.at(0, 64, 0)), 4, 4, 4, 0, 0, null));
        assertTrue(sphere.test(BlockVector3.at(0, 67, 0)));
        assertTrue(sphere.test(BlockVector3.at(2, 64, 2)));
        assertFalse(sphere.test(BlockVector3.at(3, 67, 3)), "la esquina de la caja queda fuera");

        Mask cylinder = EzMaskParser.insideOf(new SelectionShape(SelectionShape.Kind.CYLINDER,
                List.of(BlockVector3.at(0, 64, 0)), 5, 0, 5, 60, 70, null));
        assertTrue(cylinder.test(BlockVector3.at(3, 70, 3)));
        assertFalse(cylinder.test(BlockVector3.at(4, 65, 4)));
        assertFalse(cylinder.test(BlockVector3.at(0, 71, 0)));
    }

    @Test
    void polygonUsesItsOutlineAndHeights() {
        Mask polygon = EzMaskParser.insideOf(new SelectionShape(SelectionShape.Kind.POLYGON,
                List.of(BlockVector3.at(0, 60, 0), BlockVector3.at(10, 60, 0), BlockVector3.at(0, 60, 10)), 0, 0, 0,
                60, 65, null));
        assertTrue(polygon.test(BlockVector3.at(2, 62, 2)));
        assertFalse(polygon.test(BlockVector3.at(8, 62, 8)), "fuera del triángulo");
        assertFalse(polygon.test(BlockVector3.at(2, 66, 2)), "por encima");
    }

    @Test
    void isRegisteredAndSuggested() {
        assertTrue(EzMaskParser.NAMES.containsKey("savedselection"));
        assertTrue(EzMaskParser.NAMES.containsKey("savedsel"));
        EzMaskParser masks = new EzMaskParser(null);
        ParserContext context = new ParserContext();
        assertTrue(masks.getSuggestions("#sav", context).anyMatch("#savedselection["::equals));
        // Sin jugador no hay selecciones guardadas que leer.
        assertThrows(Exception.class, () -> masks.parseFromInput("#savedselection[casa]", context));
    }
}
