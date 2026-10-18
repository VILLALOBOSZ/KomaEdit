package com.komainu.moxia.symmetry;

import com.komainu.moxia.symmetry.SymmetryGeometry.Copy;
import com.komainu.moxia.symmetry.SymmetryGeometry.Point;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymmetryGeometryTest {
    private static final SymmetryTransform MIRROR_X = new SymmetryTransform(true, false, false, 0, 1);
    private static final SymmetryTransform MIRROR_Z = new SymmetryTransform(false, true, false, 0, 1);
    private static final SymmetryTransform MIRROR_Y = new SymmetryTransform(false, false, true, 0, 1);

    @Test
    void mirrorThroughABlockKeepsThatBlockAndSwapsItsNeighbours() {
        SymmetryCenter center = SymmetryCenter.block(10, 64, -5);
        assertEquals(new Point(10, 64, 3), SymmetryGeometry.apply(MIRROR_X, center, 10, 64, 3));
        assertEquals(new Point(9, 64, 3), SymmetryGeometry.apply(MIRROR_X, center, 11, 64, 3));
        assertEquals(new Point(4, 70, 3), SymmetryGeometry.apply(MIRROR_X, center, 16, 70, 3));
        assertEquals(new Point(16, 70, -12), SymmetryGeometry.apply(MIRROR_Z, center, 16, 70, 2));
    }

    @Test
    void halfBlockCenterMirrorsBetweenTwoBlocks() {
        // Entre el bloque 10 y el 11 en X; entre el -6 y el -5 en Z.
        SymmetryCenter center = SymmetryCenter.of(10, 64, -6, true, false, true);
        assertTrue(center.halfX());
        assertFalse(center.halfY());
        assertEquals(new Point(11, 64, 0), SymmetryGeometry.apply(MIRROR_X, center, 10, 64, 0));
        assertEquals(new Point(10, 64, 0), SymmetryGeometry.apply(MIRROR_X, center, 11, 64, 0));
        assertEquals(new Point(0, 64, -6), SymmetryGeometry.apply(MIRROR_Z, center, 0, 64, -5));
        assertEquals(new Point(0, 64, -10), SymmetryGeometry.apply(MIRROR_Z, center, 0, 64, -1));
        assertEquals("x 10|11, y 64, z -6|-5", center.describe());
    }

    @Test
    void mirrorInYFlipsAroundTheCenterHeight() {
        SymmetryCenter onBlock = SymmetryCenter.block(0, 64, 0);
        assertEquals(new Point(3, 60, 3), SymmetryGeometry.apply(MIRROR_Y, onBlock, 3, 68, 3));
        assertEquals(new Point(3, 64, 3), SymmetryGeometry.apply(MIRROR_Y, onBlock, 3, 64, 3));
        SymmetryCenter between = SymmetryCenter.of(0, 63, 0, false, true, false);
        assertEquals(new Point(0, 63, 0), SymmetryGeometry.apply(MIRROR_Y, between, 0, 64, 0));
        assertEquals(new Point(0, -10, 0), SymmetryGeometry.apply(MIRROR_Y, between, 0, 137, 0));
    }

    @Test
    void everyTransformIsItsOwnInverseOrClosesTheCircle() {
        SymmetryCenter center = SymmetryCenter.of(-7, 40, 13, true, false, true);
        for (SymmetryMode mode : List.of(SymmetryMode.mirror(true, true, true, 4), SymmetryMode.radial(4, true))) {
            for (SymmetryTransform transform : SymmetryGeometry.transforms(mode)) {
                for (int x = -20; x <= 5; x += 5) {
                    for (int z = 0; z <= 30; z += 6) {
                        Point point = new Point(x, 50, z);
                        Point image = point;
                        int cycle = transform.turn() == 0 ? 2 : transform.arms() / gcd(transform.turn(), transform.arms());
                        for (int step = 0; step < cycle; step++) {
                            image = SymmetryGeometry.apply(transform, center, image.x(), image.y(), image.z());
                        }
                        if (transform.mirrorY() && cycle % 2 == 1) {
                            image = SymmetryGeometry.apply(MIRROR_Y, center, image.x(), image.y(), image.z());
                        }
                        assertEquals(point, image, transform + " desde " + point);
                    }
                }
            }
        }
    }

    @Test
    void quarterTurnsAreExactAroundABlockOrACorner() {
        SymmetryCenter block = SymmetryCenter.block(0, 0, 0);
        SymmetryTransform quarter = new SymmetryTransform(false, false, false, 1, 4);
        // De este a sur: (3, 0) → (0, 3) → (-3, 0) → (0, -3).
        assertEquals(new Point(0, 5, 3), SymmetryGeometry.apply(quarter, block, 3, 5, 0));
        assertEquals(new Point(-3, 5, 0), SymmetryGeometry.apply(quarter, block, 0, 5, 3));
        SymmetryCenter corner = SymmetryCenter.of(0, 0, 0, true, false, true);
        // Alrededor de la esquina (1, 1): los cuatro bloques que la tocan giran entre sí.
        Set<Point> around = new HashSet<>();
        Point image = new Point(0, 0, 0);
        for (int step = 0; step < 4; step++) {
            around.add(image);
            image = SymmetryGeometry.apply(quarter, corner, image.x(), image.y(), image.z());
        }
        assertEquals(Set.of(new Point(0, 0, 0), new Point(1, 0, 0), new Point(1, 0, 1), new Point(0, 0, 1)), around);
    }

    @Test
    void radialSpreadsCopiesEvenlyAroundTheAxis() {
        SymmetryCenter center = SymmetryCenter.block(100, 64, 100);
        SymmetryMode six = SymmetryMode.radial(6, false);
        List<Copy> copies = SymmetryGeometry.copies(six, center, List.of(new Point(110, 64, 100)));
        assertEquals(5, copies.size());
        for (Copy copy : copies) {
            Point point = copy.points().getFirst();
            double distance = Math.hypot(point.x() + 0.5 - center.x(), point.z() + 0.5 - center.z());
            assertEquals(10.0, distance, 0.75, "copia " + point);
            assertEquals(64, point.y());
        }
        // La de 180° es exacta.
        assertTrue(copies.stream().anyMatch(copy -> copy.points().getFirst().equals(new Point(90, 64, 100))));
    }

    @Test
    void blocksOnTheMirrorOrTheAxisAreNotCopiedOntoThemselves() {
        SymmetryCenter center = SymmetryCenter.block(0, 64, 0);
        assertTrue(SymmetryGeometry.copies(SymmetryMode.mirror(true, false, false, 4), center,
                List.of(new Point(0, 70, 5))).isEmpty());
        assertTrue(SymmetryGeometry.copies(SymmetryMode.radial(8, false), center,
                List.of(new Point(0, 70, 0))).isEmpty());
        // En el plano X de un espejo X+Z quedan dos copias distintas: el reflejo en Z y nada más.
        List<Copy> copies = SymmetryGeometry.copies(SymmetryMode.mirror(true, true, false, 4), center,
                List.of(new Point(0, 70, 5)));
        assertEquals(1, copies.size());
        assertEquals(new Point(0, 70, -5), copies.getFirst().points().getFirst());
        assertTrue(copies.getFirst().transform().mirrorZ());
    }

    @Test
    void aDoorStraddlingTheMirrorInYIsLeftAlone() {
        SymmetryCenter center = SymmetryCenter.of(0, 64, 0, false, true, false);
        List<Point> door = List.of(new Point(3, 64, 3), new Point(3, 65, 3));
        assertTrue(SymmetryGeometry.copies(SymmetryMode.mirror(false, false, true, 4), center, door).isEmpty());
        List<Copy> mirrored = SymmetryGeometry.copies(SymmetryMode.mirror(true, false, true, 4), center, door);
        assertEquals(1, mirrored.size());
        assertEquals(List.of(new Point(-3, 64, 3), new Point(-3, 65, 3)), mirrored.getFirst().points());
    }

    @Test
    void looseBlocksAreCopiedOneByOneWithoutDroppingTheRest() {
        // Una fila que cruza el espejo: solo el bloque que sobresale por un lado tiene copia libre.
        SymmetryCenter center = SymmetryCenter.block(0, 64, 0);
        List<Point> row = List.of(new Point(-1, 64, 0), new Point(0, 64, 0), new Point(1, 64, 0), new Point(2, 64, 0));
        List<SymmetryGeometry.Image> images = SymmetryGeometry.images(SymmetryMode.mirror(true, false, false, 4),
                center, row);
        assertEquals(1, images.size());
        assertEquals(3, images.getFirst().source());
        assertEquals(new Point(-2, 64, 0), images.getFirst().point());
        // Como pieza, la misma fila no tendría ninguna copia.
        assertTrue(SymmetryGeometry.copies(SymmetryMode.mirror(true, false, false, 4), center, row).isEmpty());
        // Radial de 4: cada bloque fuera del eje sale tres veces más, sin repetir sitio.
        List<Point> square = List.of(new Point(3, 64, 1), new Point(0, 64, 0));
        List<SymmetryGeometry.Image> turned = SymmetryGeometry.images(SymmetryMode.radial(4, false), center, square);
        assertEquals(3, turned.size());
        assertEquals(3, turned.stream().map(SymmetryGeometry.Image::point).distinct().count());
        turned.forEach(image -> assertEquals(0, image.source()));
    }

    @Test
    void modesListTheirTransformsWithTheIdentityFirst() {
        assertEquals(2, SymmetryGeometry.transforms(SymmetryMode.mirror(true, false, false, 4)).size());
        assertEquals(4, SymmetryGeometry.transforms(SymmetryMode.mirror(true, true, false, 4)).size());
        assertEquals(8, SymmetryGeometry.transforms(SymmetryMode.mirror(true, true, true, 4)).size());
        assertEquals(32, SymmetryGeometry.transforms(SymmetryMode.radial(16, true)).size());
        for (SymmetryMode mode : List.of(SymmetryMode.mirror(false, false, true, 4), SymmetryMode.radial(5, false))) {
            List<SymmetryTransform> transforms = SymmetryGeometry.transforms(mode);
            assertTrue(transforms.getFirst().identity());
            assertEquals(mode.images(), transforms.size());
            assertEquals(transforms.size(), new HashSet<>(transforms).size());
        }
    }

    @Test
    void centersFromASelectionOrThePlayer() {
        // Caja de 5 de ancho en X (centro de bloque), 4 en Z (entre bloques).
        SymmetryCenter box = SymmetryCenter.box(10, 60, 0, 14, 70, 3);
        assertEquals(new SymmetryCenter(25, 131, 4), box);
        assertFalse(box.halfX());
        assertTrue(box.halfZ());
        assertFalse(box.radialFriendly());
        assertEquals(SymmetryCenter.box(14, 70, 3, 10, 60, 0), box);
        assertEquals(SymmetryCenter.block(-4, 63, 7), SymmetryCenter.nearest(-3.2, 63.0, 7.9, false, false, false));
        assertEquals(new SymmetryCenter(-6, 126, 16), SymmetryCenter.nearest(-3.2, 63.0, 7.9, true, true, true));
        assertThrows(IllegalArgumentException.class, () -> SymmetryCenter.block(40_000_000, 0, 0));
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
