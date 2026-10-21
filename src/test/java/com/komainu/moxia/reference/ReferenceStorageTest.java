package com.komainu.moxia.reference;

import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cada referencia y el conjunto de un mundo: valores de serie, límites y guardado y carga en JSON. */
class ReferenceStorageTest {
    @Test
    void newReferenceDefaultsDependOnTheImage() {
        ReferenceImage art = ReferenceImage.open("a1", "pixelart/espada.png", 32, 32, new Vec3(1, 70, 2));
        assertEquals(ReferenceImage.Mode.SCREEN, art.mode());
        assertTrue(art.visible());
        assertEquals(60, art.opacityPercent());
        // Pequeña: nítida, rejilla de un píxel por celda y un píxel por bloque en el mundo.
        assertFalse(art.smooth());
        assertEquals(1, art.grid().pixels());
        assertFalse(art.grid().enabled());
        assertEquals(1.0, art.world().pixelsPerBlock(), 1.0E-9);
        assertEquals(new Vec3(1, 70, 2), art.world().center());
        assertEquals("espada", art.displayName());
        ReferenceImage photo = ReferenceImage.open("b2", "fachada.jpg", 1600, 1200, null);
        assertTrue(photo.smooth());
        assertEquals(16, photo.grid().pixels());
        assertEquals(32.0, photo.world().widthBlocks(1600), 1.0E-9);
    }

    @Test
    void valuesAreClampedAndCellPerBlockMatchesTheGrid() {
        ReferenceImage reference = ReferenceImage.open("c3", "plano.png", 640, 320, null);
        assertEquals(ReferenceImage.MIN_OPACITY, reference.withOpacity(-4).opacity(), 1.0E-9);
        assertEquals(ReferenceImage.MAX_OPACITY, reference.withOpacity(7).opacity(), 1.0E-9);
        assertEquals(ReferenceImage.DEFAULT_OPACITY, reference.withOpacity(Double.NaN).opacity(), 1.0E-9);
        ReferenceImage cells = reference.withGrid(reference.grid().withPixels(10)).cellPerBlock();
        assertEquals(64.0, cells.world().widthBlocks(640), 1.0E-9);
        assertEquals(64, cells.grid().columns(640));
        // Si el archivo cambia de medidas, el plano conserva su anchura en bloques.
        ReferenceImage resized = cells.withPixelSize(1280, 640);
        assertEquals(64.0, resized.world().widthBlocks(1280), 1.0E-9);
        assertSame(cells, cells.withPixelSize(640, 320));
    }

    @Test
    void aReferenceSurvivesSavingAndLoading() {
        ReferenceImage reference = ReferenceImage.open("d4", "carpeta/castillo.jpg", 1920, 1080, null)
                .withMode(ReferenceImage.Mode.WORLD)
                .withVisible(false)
                .withOpacity(0.35)
                .withSmooth(false)
                .withGrid(new ReferenceGrid(true, 24, 5, 3, 7, ReferenceGrid.Color.CYAN))
                .withScreen(new ScreenFrame(0.12, 0.4, 0.5, true))
                .withWorld(new WorldFrame(new Vec3(-120.5, 71.0625, 33.25),
                        ReferenceRotation.facing(-37.0).turned(com.komainu.moxia.gizmo.Orientation.X, 15.0), 6.5,
                        true));
        ReferenceImage loaded = ReferenceImage.parse(reference.toJson()).orElseThrow();
        assertEquals(reference, loaded);
    }

    @Test
    void brokenReferencesAreSkippedOrFilledIn() {
        assertTrue(ReferenceImage.parse(null).isEmpty());
        assertTrue(ReferenceImage.parse(com.google.gson.JsonParser.parseString("[1, 2]")).isEmpty());
        assertTrue(ReferenceImage.parse(com.google.gson.JsonParser.parseString("{\"id\": \"x\"}")).isEmpty());
        ReferenceImage partial = ReferenceImage.parse(com.google.gson.JsonParser.parseString(
                "{\"id\": \"x\", \"file\": \"a.png\", \"width\": \"mucho\", \"opacity\": 3, \"mode\": \"lunar\","
                        + " \"grid\": {\"pixels\": -2, \"color\": \"rosa\"}, \"world\": {\"qx\": 0, \"qw\": 0}}"))
                .orElseThrow();
        assertEquals(1, partial.pixelWidth());
        assertEquals(1.0, partial.opacity(), 1.0E-9);
        assertEquals(ReferenceImage.Mode.SCREEN, partial.mode());
        assertEquals(1, partial.grid().pixels());
        assertEquals(ReferenceGrid.Color.WHITE, partial.grid().color());
        assertEquals(ReferenceRotation.IDENTITY, partial.world().rotation());
        assertEquals(ScreenFrame.DEFAULT, partial.screen());
    }

    @Test
    void theSetOfAWorldSurvivesSavingAndLoading() {
        ReferenceSet set = ReferenceSet.EMPTY
                .add(ReferenceImage.open("aaaa0001", "uno.png", 16, 16, null))
                .add(ReferenceImage.open("aaaa0002", "dos.jpg", 800, 600, null).withMode(ReferenceImage.Mode.WORLD))
                .withShown(false);
        ReferenceSet loaded = ReferenceSet.parse(set.toJson());
        assertEquals(set, loaded);
        assertFalse(loaded.shown());
        assertEquals(ReferenceSet.EMPTY, ReferenceSet.parse("{esto no es json"));
        assertEquals(ReferenceSet.EMPTY, ReferenceSet.parse(null));
        // Una referencia rota se salta y las demás se quedan.
        ReferenceSet mixed = ReferenceSet.parse("{\"shown\": true, \"references\": [{\"id\": \"q\"},"
                + " {\"id\": \"r\", \"file\": \"r.png\"}]}");
        assertEquals(List.of("r"), mixed.references().stream().map(ReferenceImage::id).toList());
    }

    @Test
    void addingReplacingAndOrdering() {
        ReferenceSet set = ReferenceSet.EMPTY;
        for (int index = 0; index < ReferenceSet.MAX + 3; index++) {
            set = set.add(ReferenceImage.open("id" + index, "f" + index + ".png", 8, 8, null));
        }
        assertEquals(ReferenceSet.MAX, set.references().size());
        assertTrue(set.full());
        // El mismo identificador dos veces solo cuenta una.
        ReferenceImage one = ReferenceImage.open("x", "x.png", 8, 8, null);
        assertEquals(1, new ReferenceSet(true, List.of(one, one.withOpacity(0.2))).references().size());
        ReferenceSet small = ReferenceSet.EMPTY.add(one).add(ReferenceImage.open("y", "y.png", 8, 8, null));
        ReferenceSet changed = small.replace(one.withOpacity(0.9));
        assertEquals(0.9, changed.byId("x").orElseThrow().opacity(), 1.0E-9);
        assertSame(small, small.replace(ReferenceImage.open("nadie", "z.png", 8, 8, null)));
        assertEquals(List.of("y", "x"), small.toFront("x").references().stream().map(ReferenceImage::id).toList());
        assertEquals(List.of("y"), small.remove("x").references().stream().map(ReferenceImage::id).toList());
    }

    @Test
    void drawnFollowsTheGlobalToggleVisibilityAndMode() {
        ReferenceImage screen = ReferenceImage.open("s", "s.png", 8, 8, null);
        ReferenceImage hidden = ReferenceImage.open("h", "h.png", 8, 8, null).withVisible(false);
        ReferenceImage world = ReferenceImage.open("w", "w.png", 8, 8, null).withMode(ReferenceImage.Mode.WORLD);
        ReferenceSet set = new ReferenceSet(true, List.of(screen, hidden, world));
        assertEquals(List.of(screen), set.drawn(ReferenceImage.Mode.SCREEN));
        assertEquals(List.of(world), set.drawn(ReferenceImage.Mode.WORLD));
        assertTrue(set.withShown(false).drawn(ReferenceImage.Mode.SCREEN).isEmpty());
        assertTrue(set.withShown(false).drawn(ReferenceImage.Mode.WORLD).isEmpty());
    }

    @Test
    void newIdsNeverRepeat() {
        AtomicInteger next = new AtomicInteger(0x1234);
        ReferenceSet set = ReferenceSet.EMPTY.add(ReferenceImage.open("00001234", "a.png", 8, 8, null));
        assertEquals("00001235", set.newId(next::getAndIncrement));
        // Con un azar que siempre repite, sale otro igualmente.
        String fallback = set.newId(() -> 0x1234);
        assertNotEquals("00001234", fallback);
    }

    @Test
    void worldAndServerKeys() {
        String world = ReferenceSet.key(true, "Mi Mundo");
        assertTrue(world.startsWith("mundo_mi_mundo_"));
        assertTrue(ReferenceSet.key(false, "play.ejemplo.net:25565").startsWith("servidor_play.ejemplo.net_25565_"));
        // Nombres que se escriben igual tras limpiarlos no comparten archivo.
        assertNotEquals(ReferenceSet.key(true, "mi_mundo"), world);
        assertEquals(world, ReferenceSet.key(true, "Mi Mundo"));
        assertNotEquals(ReferenceSet.key(true, "x"), ReferenceSet.key(false, "x"));
        // Nada que pueda salir de la carpeta ni dejar el nombre vacío.
        String odd = ReferenceSet.key(false, "../../etc/passwd");
        assertFalse(odd.contains("/"));
        assertFalse(odd.contains("\\"));
        assertTrue(ReferenceSet.key(true, "").startsWith("mundo_sin_nombre_"));
        assertTrue(ReferenceSet.key(true, "x".repeat(500)).length() < 80);
    }
}
