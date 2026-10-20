package com.komainu.moxia.studio;

import com.komainu.moxia.recipe.RecipeExtensions;
import com.komainu.moxia.recipe.SocketPlacement;
import com.komainu.moxia.recipe.SurfaceMode;
import com.komainu.moxia.shape.PathShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Las variaciones de la figura (lo que era Modificadores): cada una se pone con un valor que se ve y se quita sola. */
class FigureVariationTest {
    private static final RecipeExtensions CLEAN = RecipeExtensions.DEFAULT;

    @Test
    void eachOneTurnsOnWithAVisibleValueAndOffLeavingTheRestAsItWas() {
        for (FigureVariation variation : FigureVariation.values()) {
            assertFalse(variation.on(CLEAN), variation.id());
            RecipeExtensions on = variation.enable(CLEAN, PathShape.BRAID);
            assertTrue(variation.on(on), variation.id());
            assertEquals(1, FigureVariation.active(on), variation.id() + ": solo ella");
            // Quitarla deja la figura limpia (lo copiado recuerda dónde iba, por si se vuelve a poner).
            RecipeExtensions off = variation.disable(on);
            assertFalse(variation.on(off), variation.id());
            assertEquals(variation == FigureVariation.COPIED ? CLEAN.withSockets(false, on.socketPlacement(),
                    on.socketInterval()) : CLEAN, off, variation.id());
            assertEquals(on, variation.enable(on, PathShape.BRAID), variation.id() + ": ponerla otra vez no cambia nada");
            assertEquals(off, variation.toggle(on, PathShape.BRAID));
        }
        RecipeExtensions taper = FigureVariation.TAPER.enable(CLEAN, PathShape.BRAID);
        assertEquals(100, taper.startScalePercent(), "el principio ya es el radio de la figura");
        assertEquals(FigureVariation.TAPER_END, taper.endScalePercent());
        assertEquals(FigureVariation.TWIST_DEGREES, FigureVariation.TWIST.enable(CLEAN, PathShape.BRAID).twistDegrees());
        assertEquals(SurfaceMode.FLOOR, FigureVariation.GROUND.enable(CLEAN, PathShape.BRAID).surfaceMode());
        assertEquals(SocketPlacement.INTERVAL, FigureVariation.COPIED.enable(CLEAN, PathShape.BRAID).socketPlacement(),
                "lo copiado se repite a lo largo, no solo en las puntas");
    }

    @Test
    void theyStackAndRemovingOneKeepsTheOthers() {
        RecipeExtensions both = FigureVariation.TWIST.enable(FigureVariation.TAPER.enable(CLEAN, PathShape.COIL),
                PathShape.COIL);
        assertEquals(2, FigureVariation.active(both));
        RecipeExtensions twistOnly = FigureVariation.TAPER.disable(both);
        assertTrue(FigureVariation.TWIST.on(twistOnly));
        assertFalse(FigureVariation.TAPER.on(twistOnly));
        assertEquals(FigureVariation.TWIST_DEGREES, twistOnly.twistDegrees());
    }

    @Test
    void theFigureOnTopIsAnotherOne() {
        assertEquals(PathShape.RINGS, FigureVariation.ACCENT.enable(CLEAN, PathShape.BRAID).accentShape());
        assertEquals(PathShape.BEADS, FigureVariation.ACCENT.enable(CLEAN, PathShape.RINGS).accentShape(),
                "anillos sobre anillos no se verían");
        RecipeExtensions chosen = CLEAN.withRhythm(false, PathShape.KNOTS, 6);
        assertEquals(PathShape.KNOTS, FigureVariation.ACCENT.enable(chosen, PathShape.BRAID).accentShape(),
                "la que ya estaba elegida se conserva");
    }

    @Test
    void recipesOfBeforeAreReadTheSame() {
        // Una receta de antes con todo tocado sigue siendo la misma receta: las variaciones solo la leen.
        RecipeExtensions old = new RecipeExtensions(150, 60, 80, 120, 90, CLEAN.easing(), true, PathShape.KNOTS, 5,
                SurfaceMode.NEAREST, 9, 2, CLEAN.materialMetric(), true, SocketPlacement.BEND, 10, true, true);
        assertEquals(5, FigureVariation.active(old));
        assertTrue(FigureVariation.TAPER.on(CLEAN.withCurve(130, 100, 100, 100, 0, CLEAN.easing())),
                "cambiar solo el principio también cuenta como afinar");
        assertTrue(FigureVariation.TAPER.on(CLEAN.withCurve(100, 100, 100, 70, 0, CLEAN.easing())),
                "y la separación al final");
        assertNotEquals(old, FigureVariation.TAPER.disable(old));
    }

    @Test
    void structuresOnlyStickToTheGroundUnlessTheRecipeAlreadyHasMore() {
        for (FigureVariation variation : FigureVariation.values()) {
            assertTrue(variation.fits(PathShape.BRAID, CLEAN), variation.id());
            assertEquals(variation == FigureVariation.GROUND, variation.fits(PathShape.TORII, CLEAN), variation.id());
        }
        RecipeExtensions twisted = FigureVariation.TWIST.enable(CLEAN, PathShape.TORII);
        assertTrue(FigureVariation.TWIST.fits(PathShape.TORII, twisted), "puesta, se enseña para poder quitarla");
    }

    @Test
    void whatTheHologramShowsRightAway() {
        assertTrue(FigureVariation.TAPER.immediate(CLEAN));
        assertTrue(FigureVariation.TWIST.immediate(CLEAN));
        assertTrue(FigureVariation.GROUND.immediate(CLEAN.withSurface(SurfaceMode.FLOOR, 12, 0)));
        assertFalse(FigureVariation.GROUND.immediate(CLEAN.withSurface(SurfaceMode.NEAREST, 12, 0)),
                "lo más cercano busca en todas direcciones: lo hace el servidor");
        assertFalse(FigureVariation.COPIED.immediate(CLEAN), "el portapapeles es del servidor");
    }
}
