package com.komainu.moxia.studio.live;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Dónde cae la vista previa en vivo: el Construir apunta el sitio y lo que se rehace después sale ahí aunque el
 * jugador ya mire a otra parte. Así se usa en {@code StudioAim.target}: primero lo fijado y, si no hay, lo que se mire.
 */
class LiveAnchorsTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void theBuildPinsWhereTheRefreshesLand() {
        LiveAnchors<String> anchors = new LiveAnchors<>();
        String[] looking = {"120,64,-40"};
        assertEquals("120,64,-40", anchors.run(PLAYER, 1, true, () -> aim(anchors, looking[0])));
        looking[0] = "el cielo";
        assertEquals("120,64,-40", anchors.run(PLAYER, 1, false, () -> aim(anchors, looking[0])),
                "mire donde mire, rehace en el sitio del Construir");
        assertEquals("120,64,-40", anchors.anchor(PLAYER, 1));
        assertEquals("el cielo", aim(anchors, looking[0]), "fuera de la vista en vivo, lo que se mire");
    }

    @Test
    void anotherBuildLooksAgain() {
        LiveAnchors<String> anchors = new LiveAnchors<>();
        anchors.run(PLAYER, 1, true, () -> aim(anchors, "a"));
        assertEquals("b", anchors.run(PLAYER, 2, true, () -> aim(anchors, "b")));
        assertEquals("b", anchors.run(PLAYER, 2, false, () -> aim(anchors, "c")));
        assertNull(anchors.anchor(PLAYER, 1));
        assertEquals("x", anchors.run(PLAYER, 7, false, () -> aim(anchors, "x")), "sesión desconocida: busca");
    }

    @Test
    void eachPlayerHasTheirOwn() {
        LiveAnchors<String> anchors = new LiveAnchors<>();
        anchors.run(PLAYER, 1, true, () -> aim(anchors, "a"));
        anchors.run(PLAYER, 1, false, () -> {
            // Otro jugador que resuelva «donde miras» en medio no usa ni pisa lo fijado de este.
            assertNull(anchors.pinned(OTHER));
            anchors.captured(OTHER, "lo del otro");
            return null;
        });
        assertEquals("a", anchors.run(PLAYER, 1, false, () -> aim(anchors, "otra")));
        assertNull(anchors.anchor(OTHER, 1));
        anchors.forget(PLAYER);
        assertNull(anchors.anchor(PLAYER, 1));
    }

    @Test
    void aBuildThatDoesNotLookPinsNothing() {
        LiveAnchors<String> anchors = new LiveAnchors<>();
        anchors.run(PLAYER, 1, true, () -> "una selección");
        assertNull(anchors.anchor(PLAYER, 1));
        assertEquals("ahora", anchors.run(PLAYER, 1, false, () -> aim(anchors, "ahora")));
    }

    /** Lo que hace {@code StudioAim.target}: lo fijado si lo hay; si no, lo que se mira, y se apunta. */
    private static String aim(LiveAnchors<String> anchors, String looking) {
        String pinned = anchors.pinned(PLAYER);
        if (pinned != null) {
            return pinned;
        }
        anchors.captured(PLAYER, looking);
        return looking;
    }
}
