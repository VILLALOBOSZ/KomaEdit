package com.komainu.moxia.placement;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementCopiesTest {
    /** Un poste de 1×3×1 con la punta distinta. */
    private static VoxelStructure post() {
        return VoxelStructure.builder(1, 3, 1, 0, 0, 0).set(0, 0, 0, 0).set(0, 1, 0, 0).set(0, 2, 0, 1)
                .build(false, 1, 0, "poste");
    }

    /** Una barra de 3×1×1 a lo largo de +x. */
    private static VoxelStructure bar() {
        return VoxelStructure.builder(3, 1, 1, 0, 0, 0).set(0, 0, 0, 0).set(1, 0, 0, 0).set(2, 0, 0, 0)
                .build(false, 1, 0, "barra");
    }

    private static Placement base(Structure structure) {
        return new Placement(structure, new Vec3(0.5, 0.5, 0.5), Mat3.IDENTITY, new Vec3(1, 1, 1), 1L);
    }

    @Test
    void flagsAreReadOnlyByPlace() {
        PlacementSettings settings = PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Clipboard -copias 3 -desplazar 0,1,0 -revolver 90 -pivote 4,0,0 -torcer 15");
        PlacementCopies copies = settings.copies();
        assertEquals(3, copies.count());
        assertEquals(new Vec3(0, 1, 0), copies.offset());
        assertEquals(90.0, copies.revolve());
        assertEquals(new Vec3(4, 0, 0), copies.pivot());
        assertEquals(15.0, copies.twist());
        assertFalse(PlacementSettings.parse(PlacementSettings.Kind.PLACE, "Clipboard").copies().active());
        assertThrows(IllegalArgumentException.class, () -> PlacementSettings.parse(PlacementSettings.Kind.ARRAY,
                "Clipboard -copias 3 -desplazar 0,1,0"));
        assertThrows(IllegalArgumentException.class, () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Clipboard -desplazar 0,1,0"), "sin -copias");
        assertThrows(IllegalArgumentException.class, () -> PlacementSettings.parse(PlacementSettings.Kind.PLACE,
                "Clipboard -copias 4"), "todas en el mismo sitio");
        // Con la semilla fijada se conservan.
        assertEquals(3, settings.withSeed(9).copies().count());
    }

    @Test
    void translateStacksCopies() {
        PlacementCopies copies = new PlacementCopies(4, new Vec3(0, 3, 0), 0, new Vec3(0, 0, 0), 0);
        List<Placement> placements = copies.expand(base(post()));
        assertEquals(5, placements.size());
        PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(placements,
                PlacementVoxelizer.Options.defaults());
        assertEquals(15, result.size(), "cinco postes de tres, uno encima de otro");
        assertTrue(result.cells().containsKey(PackedBlockPlan.pack(0, 14, 0)));
    }

    @Test
    void revolveTurnsAroundThePivotLikeRotate() {
        PlacementCopies copies = new PlacementCopies(3, new Vec3(0, 0, 0), 90, new Vec3(4, 0, 0), 0);
        List<Placement> placements = copies.expand(base(post()));
        // De oeste a norte, este y sur del pivote, en el sentido de //rotate (de este a sur).
        Vec3[] expected = {new Vec3(0.5, 0.5, 0.5), new Vec3(4.5, 0.5, -3.5), new Vec3(8.5, 0.5, 0.5),
                new Vec3(4.5, 0.5, 4.5)};
        for (int index = 0; index < expected.length; index++) {
            Vec3 anchor = placements.get(index).anchor();
            assertTrue(anchor.distance(expected[index]) < 1.0E-9, "copia " + index + " en " + anchor);
        }
        PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(placements,
                PlacementVoxelizer.Options.defaults());
        assertEquals(12, result.size());
    }

    @Test
    void twistTurnsEachCopyOnItsOwnAxis() {
        // Barras apiladas que giran 45° cada una: una escalera de caracol.
        PlacementCopies copies = new PlacementCopies(2, new Vec3(0, 1, 0), 0, new Vec3(0, 0, 0), 90);
        List<Placement> placements = copies.expand(base(bar()));
        Vec3 first = placements.get(1).rotation().apply(1, 0, 0);
        Vec3 second = placements.get(2).rotation().apply(1, 0, 0);
        assertTrue(first.distance(new Vec3(0, 0, 1)) < 1.0E-9, "la primera copia apunta al sur: " + first);
        assertTrue(second.distance(new Vec3(-1, 0, 0)) < 1.0E-9, "la segunda, al oeste: " + second);
        PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(placements,
                PlacementVoxelizer.Options.defaults());
        assertTrue(result.cells().containsKey(PackedBlockPlan.pack(0, 1, 2)));
        assertTrue(result.cells().containsKey(PackedBlockPlan.pack(-2, 2, 0)));
        assertEquals(9, result.size());
    }
}
