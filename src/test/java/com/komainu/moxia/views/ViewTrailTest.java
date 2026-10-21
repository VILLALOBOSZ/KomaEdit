package com.komainu.moxia.views;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** «Volver» y la tecla de la vista fija: ir a la vista, volver a donde estabas y, si te mueves, otra vez a la vista. */
class ViewTrailTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final SavedView PINNED = SavedView.here("Mirador", OVERWORLD, 100, 90, 100, 45, 20, 0)
            .withPinned(true);

    private static ViewTeleport.Target at(double x, double y, double z) {
        return new ViewTeleport.Target(OVERWORLD, x, y, z, 0, 0);
    }

    @Test
    void thePinnedViewGoesAndComesBack() {
        ViewTrail trail = new ViewTrail();
        ViewTeleport.Target work = at(0, 64, 0);
        ViewTeleport.Target first = trail.pinned(work, PINNED);
        assertEquals(ViewTeleport.Target.of(PINNED), first, "de la obra a la vista fija");
        // Mirando un poco desde allí (sin alejarse), la tecla devuelve a la obra.
        assertEquals(work, trail.pinned(at(101, 90, 99), PINNED));
        // Desde la obra otra vez a la vista.
        assertEquals(ViewTeleport.Target.of(PINNED), trail.pinned(work, PINNED));
    }

    @Test
    void afterMovingAwayThePinnedViewComesBackInsteadOfReturning() {
        ViewTrail trail = new ViewTrail();
        trail.pinned(at(0, 64, 0), PINNED);
        // Vuela lejos desde la vista: la tecla lleva de nuevo a la vista y apunta el sitio nuevo.
        ViewTeleport.Target elsewhere = at(300, 70, 300);
        assertEquals(ViewTeleport.Target.of(PINNED), trail.pinned(elsewhere, PINNED));
        assertEquals(elsewhere, trail.pinned(ViewTeleport.Target.of(PINNED), PINNED));
    }

    @Test
    void backReturnsBeforeEachJumpNewestFirst() {
        ViewTrail trail = new ViewTrail();
        assertFalse(trail.canGoBack());
        trail.left(at(1, 64, 1));
        trail.left(at(2, 64, 2));
        assertTrue(trail.canGoBack());
        assertEquals(at(2, 64, 2), trail.back().orElseThrow());
        assertEquals(at(1, 64, 1), trail.back().orElseThrow());
        assertTrue(trail.back().isEmpty());
        for (int index = 0; index < ViewTrail.MAX_BACK + 5; index++) {
            trail.left(at(index, 64, 0));
        }
        int count = 0;
        while (trail.back().isPresent()) {
            count++;
        }
        assertEquals(ViewTrail.MAX_BACK, count, "se recuerdan los últimos");
        trail.left(at(5, 5, 5));
        trail.clear();
        assertFalse(trail.canGoBack());
    }

    @Test
    void anotherDimensionIsNeverNear() {
        assertFalse(ViewTrail.near(at(0, 0, 0), new ViewTeleport.Target("minecraft:the_nether", 0, 0, 0, 0, 0)));
        assertTrue(ViewTrail.near(at(0, 0, 0), at(ViewTrail.NEAR, 0, 0)));
        assertFalse(ViewTrail.near(at(0, 0, 0), at(ViewTrail.NEAR + 0.1, 0, 0)));
    }
}
