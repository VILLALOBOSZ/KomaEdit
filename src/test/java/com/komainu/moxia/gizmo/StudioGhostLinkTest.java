package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Qué fantasma es del Studio en el cliente. Es la lógica que antes vivía dentro de {@code GhostClient} (fuera del
 * alcance de las pruebas): las de la vista en vivo daban por hecho que el fantasma era del Studio.
 */
class StudioGhostLinkTest {
    private static final GhostSession.Rules ANY = orientation -> true;
    private static final GhostSession.Ground FLOATING = transform -> OptionalInt.empty();

    @Test
    void theServerSaysWhichGhostIsTheStudios() {
        StudioGhostLink link = new StudioGhostLink();
        link.claim(10, false);
        StudioGhostLink.Arrival first = link.arrive(null, info(10, 0, 64, 0), ANY, 0);
        assertEquals(StudioGhostLink.Kind.STUDIO, first.kind());
        assertTrue(link.fromStudio());
        first.session().nudge(3, 0, -2, FLOATING);
        first.session().rotate(1, 1, ANY, FLOATING);

        link.claim(11, true);
        StudioGhostLink.Arrival refresh = link.arrive(first.session(), info(11, 0, 64, 0), ANY, 100);
        assertEquals(StudioGhostLink.Kind.REFRESH, refresh.kind());
        assertSame(first.session(), refresh.previous(), "la malla de antes sigue a la vista hasta la nueva");
        assertEquals(first.session().transform(), refresh.session().transform(), "donde lo dejó y con su giro");
        assertFalse(refresh.announce(), "sin repetir el aviso del chat");
    }

    @Test
    void withoutTheNetworkEveryExpectationCounts() {
        // Un servidor anterior: el Studio avisa antes de cada petición y no hay respuesta del servidor.
        StudioGhostLink link = new StudioGhostLink();
        link.expect(false, 0);
        GhostSession built = link.arrive(null, info(1, 0, 64, 0), ANY, 50).session();
        built.nudge(5, 0, 0, FLOATING);
        // Dos cambios seguidos: los dos fantasmas llegan detrás. Antes el segundo se tomaba por ajeno.
        link.expect(true, 1_000);
        link.expect(true, 1_300);
        StudioGhostLink.Arrival second = link.arrive(built, info(2, 0, 64, 0), ANY, 1_500);
        StudioGhostLink.Arrival third = link.arrive(second.session(), info(3, 0, 64, 0), ANY, 1_800);
        assertEquals(StudioGhostLink.Kind.REFRESH, second.kind());
        assertEquals(StudioGhostLink.Kind.REFRESH, third.kind());
        assertEquals(5, third.session().transform().x(), "los dos siguen donde se movió");
        assertEquals(StudioGhostLink.Kind.FOREIGN, link.arrive(third.session(), info(4, 0, 64, 0), ANY, 2_000).kind(),
                "un //paste después no es del Studio");
        assertFalse(link.fromStudio());
    }

    @Test
    void aForeignGhostIsLeftAloneAndExpectationsExpire() {
        StudioGhostLink link = new StudioGhostLink();
        assertEquals(StudioGhostLink.Kind.FOREIGN, link.kind(5, false, 0));
        link.expect(true, 0);
        assertEquals(StudioGhostLink.Kind.FOREIGN, link.kind(6, false, StudioGhostLink.EXPECT_MILLIS));
        link.claim(7, true);
        assertEquals(StudioGhostLink.Kind.STUDIO, link.kind(7, true, 0),
                "rehacer solo sustituye a un fantasma del Studio");
    }

    @Test
    void placedOrCancelledNothingIsRefreshed() {
        StudioGhostLink link = new StudioGhostLink();
        link.claim(1, false);
        GhostSession built = link.arrive(null, info(1, 0, 64, 0), ANY, 0).session();
        link.closed();
        link.claim(2, true);
        StudioGhostLink.Arrival next = link.arrive(built, info(2, 0, 64, 0), ANY, 10);
        assertEquals(StudioGhostLink.Kind.STUDIO, next.kind());
        assertNull(next.previous());
    }

    private static GhostSession.Info info(int id, int x, int y, int z) {
        return new GhostSession.Info(id, "Roble", new BlockBounds(-3, 0, -3, 3, 17, 3), 400, 0,
                GhostTransform.at(x, y, z), GhostOptions.DEFAULT);
    }
}
