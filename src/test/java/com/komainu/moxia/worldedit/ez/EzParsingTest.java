package com.komainu.moxia.worldedit.ez;

import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lectura de máscaras y patrones: argumentos, nombres, direcciones. */
class EzParsingTest {
    @Test
    void readsNestedBracketArguments() {
        EzArguments arguments = EzArguments.parse("#Palette[[##Ice,oak_stairs[facing=east]](1:2)][True]");

        assertEquals("palette", arguments.name());
        assertEquals(List.of("[##Ice,oak_stairs[facing=east]](1:2)", "True"), arguments.arguments());
        assertTrue(arguments.bool(1, false));
        assertEquals(List.of(), EzArguments.parse("#fullblock").arguments());
        assertEquals(List.of("", "4"), EzArguments.parse("#near[][4]").arguments());
    }

    @Test
    void leavesOtherInputToWorldEdit() {
        assertNull(EzArguments.parse("stone"));
        assertNull(EzArguments.parse("##wool"));
        assertNull(EzArguments.parse("#"));
        assertNull(EzArguments.parse("#[1]"));
    }

    @Test
    void rejectsBrokenArguments() {
        assertThrows(IllegalArgumentException.class, () -> EzArguments.parse("#near[stone][4"));
        assertThrows(IllegalArgumentException.class, () -> EzArguments.parse("#near[stone]x"));
        EzArguments arguments = EzArguments.parse("#blocklight[x][maybe]");
        assertThrows(IllegalArgumentException.class, () -> arguments.number(0, 0));
        assertThrows(IllegalArgumentException.class, () -> arguments.bool(1, false));
        assertThrows(IllegalArgumentException.class, () -> arguments.atMost(1));
        assertThrows(IllegalArgumentException.class, () -> arguments.require(3, "algo"));
        assertEquals(2.5, EzArguments.parse("#x[2.5]").number(0, 0));
    }

    @Test
    void everyDocumentedNameAndAliasIsRegistered() {
        for (String name : List.of("aim", "attached", "blocklight", "clipblocks", "cb", "current", "c",
                "eznoisemask", "eznm", "fullblock", "fuzzypalette", "fpalette", "infested", "lightsource", "near",
                "palette", "radialgradientmask", "rgradientm", "schemblocks", "sb", "truelight",
                "vectorgradientmask", "vgradientm")) {
            assertTrue(EzMaskParser.NAMES.containsKey(name), "máscara " + name);
        }
        for (String name : List.of("aim", "eznoisepattern", "eznp", "ridged", "smoothcells", "voronoiedge",
                "palette", "radialgradientpattern", "rgradientp", "vectorgradient", "vectorgradientpattern",
                "vgradientp", "selection", "sel")) {
            assertTrue(EzPatternParser.NAMES.containsKey(name), "patrón " + name);
        }
    }

    @Test
    void parsersIgnoreForeignInputAndReportSyntaxErrors() throws InputParseException {
        EzMaskParser masks = new EzMaskParser(null);
        EzPatternParser patterns = new EzPatternParser(null);
        ParserContext context = new ParserContext();

        assertNull(masks.parseFromInput("stone", context));
        assertNull(masks.parseFromInput("#existing", context));
        assertNull(patterns.parseFromInput("#clipboard", context));
        // Fuera del juego WorldEdit no puede traducir el mensaje del error, así que solo se comprueba que falla.
        assertThrows(Exception.class, () -> masks.parseFromInput("#near[stone", context));
        assertThrows(Exception.class, () -> masks.parseFromInput("#fullblock[1]x", context));
        assertTrue(masks.getSuggestions("#ne", context).anyMatch("#near["::equals));
        assertTrue(patterns.getSuggestions("#vec", context).anyMatch("#vectorgradientpattern["::equals));
        assertFalse(masks.getSuggestions("st", context).findAny().isPresent());
    }

    @Test
    void directionsFollowMinecraftAngles() {
        assertEquals(BlockVector3.UNIT_Z, EzDirections.facing(0f, 0f));
        assertEquals(BlockVector3.UNIT_MINUS_X, EzDirections.facing(90f, 10f));
        assertEquals(BlockVector3.UNIT_MINUS_Z, EzDirections.facing(-180f, 0f));
        assertEquals(BlockVector3.UNIT_X, EzDirections.facing(-90f, 0f));
        assertEquals(BlockVector3.UNIT_Y, EzDirections.facing(0f, -80f));
        assertEquals(BlockVector3.UNIT_MINUS_Y, EzDirections.facing(45f, 70f));
        // Mirando al sur, el este queda a la izquierda.
        assertEquals(BlockVector3.UNIT_X, EzDirections.parse("left", 0f, 0f));
        assertEquals(BlockVector3.UNIT_MINUS_X, EzDirections.parse("derecha", 0f, 0f));
        assertEquals(BlockVector3.UNIT_MINUS_Z, EzDirections.parse("back", 0f, 0f));
        assertEquals(BlockVector3.UNIT_Y, EzDirections.parse("Up", 0f, 0f));
        assertThrows(IllegalArgumentException.class, () -> EzDirections.parse("sideways", 0f, 0f));
    }

    @Test
    void vectorsAreNormalized() {
        Vector3 vector = EzDirections.vector("3,0,4", 0f, 0f);
        assertEquals(0.6, vector.x(), 1.0E-9);
        assertEquals(0.8, vector.z(), 1.0E-9);
        assertEquals(1.0, EzDirections.vector("north", 0f, 0f).length(), 1.0E-9);
        assertEquals(1.0, EzDirections.vector("look", 33f, -21f).length(), 1.0E-9);
        assertThrows(IllegalArgumentException.class, () -> EzDirections.vector("0,0,0", 0f, 0f));
    }

    @Test
    void rayStopsAtTheFirstMatchingBlock() {
        Set<BlockVector3> solid = Set.of(BlockVector3.at(5, 0, 0), BlockVector3.at(9, 0, 0));
        assertEquals(BlockVector3.at(5, 0, 0), VoxelRay.first(0.5, 0.5, 0.5, 1, 0, 0, 20, solid::contains));
        assertNull(VoxelRay.first(0.5, 0.5, 0.5, 1, 0, 0, 3, solid::contains));
        assertNull(VoxelRay.first(0.5, 0.5, 0.5, -1, 0, 0, 20, solid::contains));
        BlockVector3 diagonal = VoxelRay.first(0.5, 0.5, 0.5, 1, 1, 1, 50,
                position -> position.equals(BlockVector3.at(7, 7, 7)));
        assertEquals(BlockVector3.at(7, 7, 7), diagonal);
    }

    @Test
    void nearSearchFindsTheClosestMatch() {
        BlockVector3 origin = BlockVector3.at(10, 64, 10);
        Set<BlockVector3> targets = Set.of(BlockVector3.at(13, 64, 10), BlockVector3.at(11, 65, 10));

        assertEquals(2, NearSearch.nearestSquared(origin, 5, targets::contains));
        assertEquals(-1, NearSearch.nearestSquared(origin, 1, targets::contains));
        assertEquals(-1, NearSearch.nearestSquared(origin, 3, position -> position.equals(origin)));
        int[] offsets = NearSearch.offsets(2);
        for (int index = 4; index < offsets.length; index += 4) {
            assertTrue(offsets[index + 3] >= offsets[index - 1]);
        }
        assertThrows(IllegalArgumentException.class, () -> NearSearch.offsets(NearSearch.MAX_DISTANCE + 1));
    }

    @Test
    void gradientsMoveFromNearToFar() {
        assertEquals(0.0, GradientMath.fraction(2, 3, 10));
        assertEquals(0.5, GradientMath.fraction(8, 3, 10));
        assertEquals(1.0, GradientMath.fraction(30, 3, 10));
        assertEquals(5.0, GradientMath.DistanceMode.SPHERE.distance(3, 4, 0));
        assertEquals(4.0, GradientMath.DistanceMode.CUBE.distance(3, -4, 1));
        assertEquals(8.0, GradientMath.DistanceMode.DIAMOND.distance(3, -4, 1));
        assertEquals(5.0, GradientMath.DistanceMode.CYLINDER.distance(3, 99, 4));
        assertEquals(GradientMath.DistanceMode.CUBE, GradientMath.DistanceMode.byName("Cubo"));
        assertEquals(0.25, GradientMath.along(Vector3.ZERO, Vector3.UNIT_Y, 7, 5, 7, 20));
        assertTrue(GradientMath.passes(0.0, 0.0));
        assertFalse(GradientMath.passes(1.0, 0.999));
        assertEquals(0, GradientMath.paletteIndex(0.0, 0.0, 4));
        assertEquals(3, GradientMath.paletteIndex(1.0, 0.99, 4));
        assertEquals(2, GradientMath.paletteIndex(0.5, 0.5, 4));
    }

    @Test
    void positionalRandomIsStableAndSpread() {
        double first = GradientMath.hash01(4, 70, -9, 11L);
        assertEquals(first, GradientMath.hash01(4, 70, -9, 11L));
        double total = 0;
        for (int index = 0; index < 10_000; index++) {
            double value = GradientMath.hash01(index, index * 3, -index, 5L);
            assertTrue(value >= 0.0 && value < 1.0);
            total += value;
        }
        assertEquals(0.5, total / 10_000, 0.02);
    }
}
