package com.komainu.moxia.gizmo;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Al rehacerse el fantasma con la vista previa en vivo, la malla de antes sigue a la vista hasta que la nueva está
 * lista: ningún fotograma sin fantasma, y cada malla se libera una vez.
 */
class MeshHandoverTest {
    @Test
    void theOldMeshStaysUntilTheNewOneIsReady() {
        List<String> freed = new ArrayList<>();
        MeshHandover<String> meshes = new MeshHandover<>(freed::add);
        meshes.install("árbol 1");
        assertEquals("árbol 1", meshes.current());
        meshes.retire(1_000);
        assertNull(meshes.current(), "el fantasma nuevo aún no tiene malla");
        assertEquals("árbol 1", meshes.previous(1_100), "pero se sigue viendo el de antes");
        assertEquals(List.of(), freed);
        meshes.install("árbol 2");
        assertEquals("árbol 2", meshes.current());
        assertNull(meshes.previous(1_200));
        assertEquals(List.of("árbol 1"), freed, "la vieja se libera al llegar la nueva");
    }

    @Test
    void ifTheNewOneNeverArrivesTheOldOneGoesAway() {
        List<String> freed = new ArrayList<>();
        MeshHandover<String> meshes = new MeshHandover<>(freed::add);
        meshes.install("roca");
        meshes.retire(0);
        assertEquals("roca", meshes.previous(MeshHandover.LIMIT_MILLIS));
        assertNull(meshes.previous(MeshHandover.LIMIT_MILLIS + 1));
        assertEquals(List.of("roca"), freed);
    }

    @Test
    void retiringTwiceAndClearingFreeEachMeshOnce() {
        List<String> freed = new ArrayList<>();
        MeshHandover<String> meshes = new MeshHandover<>(freed::add);
        meshes.install("a");
        meshes.retire(0);
        meshes.retire(10);
        assertEquals("a", meshes.previous(20), "sin malla nueva entre medias, la que se ve sigue siendo la misma");
        meshes.install("b");
        meshes.install("c");
        meshes.clear();
        assertEquals(List.of("a", "b", "c"), freed);
        assertNull(meshes.current());
        assertNull(meshes.previous(30));
    }
}
