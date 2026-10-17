package com.komainu.moxia.placement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProceduralShapesTest {
    private static final int GRID = 40;

    /** Fracción del cubo [-1, 1]³ ocupada por la forma, muestreada en una rejilla de 40³. */
    private static double fill(ProceduralShapes.Implicit shape) {
        int inside = 0;
        for (int i = 0; i < GRID; i++) {
            for (int j = 0; j < GRID; j++) {
                for (int k = 0; k < GRID; k++) {
                    if (shape.inside(coordinate(i), coordinate(j), coordinate(k))) {
                        inside++;
                    }
                }
            }
        }
        return inside / (double) (GRID * GRID * GRID);
    }

    private static double coordinate(int index) {
        return -1.0 + (index + 0.5) * 2.0 / GRID;
    }

    private static ProceduralShapes.Implicit shape(String text) {
        return ProceduralShapes.parse(text).create(7L);
    }

    @Test
    void geometricShapesHaveTheirTextbookVolumes() {
        assertEquals(1.0, fill(shape("Cuboid")), 1.0E-9);
        assertEquals(Math.PI / 6, fill(shape("Ellipsoid")), 0.01);
        assertEquals(Math.PI / 4, fill(shape("Cylinder")), 0.01);
        assertEquals(Math.PI / 12, fill(shape("Cone")), 0.01);
        assertEquals(1.0 / 3.0, fill(shape("Tetrahedron")), 0.01);
        assertEquals(1.0 / 3.0, fill(shape("Pyramid")), 0.01);
        assertEquals(1.0, fill(shape("Polygon(Sides:4)")), 0.01);
        // Hexágono estirado a su caja: ocupa 3/4 del cuadrado.
        assertEquals(0.75, fill(shape("Polygon")), 0.01);
        assertTrue(fill(shape("Supersphere(Exponent:8)")) > fill(shape("Supersphere(Exponent:2)")));
        assertEquals(Math.PI / 6, fill(shape("Supersphere(Exponent:2)")), 0.01);
    }

    @Test
    void coneAndPyramidNarrowUpwards() {
        ProceduralShapes.Implicit cone = shape("Cone");
        assertTrue(cone.inside(0.9, -0.95, 0));
        assertFalse(cone.inside(0.9, 0.5, 0));
        assertTrue(cone.inside(0, 0.95, 0));
        ProceduralShapes.Implicit pyramid = shape("Pyramid(Sides:4)");
        assertTrue(pyramid.inside(0.95, -0.98, 0.95), "la base es un cuadrado que llena la caja");
    }

    @Test
    void crystalHasPrismBodyAndPointedTips() {
        ProceduralShapes.Implicit crystal = shape("Crystal(Sides:6,Extrusion:0.5)");
        assertTrue(crystal.inside(0.8, 0.0, 0.0));
        assertTrue(crystal.inside(0.8, 0.45, 0.0));
        assertFalse(crystal.inside(0.8, 0.9, 0.0));
        assertTrue(crystal.inside(0.0, 0.97, 0.0));
        assertTrue(crystal.inside(0.0, -0.97, 0.0));
    }

    @Test
    void torusHasAHoleAndThicknessChangesTheTube() {
        ProceduralShapes.Implicit torus = shape("Torus(Thickness:0.4)");
        assertFalse(torus.inside(0, 0, 0), "el centro es el agujero");
        assertTrue(torus.inside(0.8, 0, 0));
        assertTrue(torus.inside(0, 0, -0.8));
        assertFalse(torus.inside(0.8, 0.5, 0), "el tubo mide 0.2 de radio");
        assertTrue(fill(shape("Torus(Thickness:0.8)")) > fill(torus));
    }

    @Test
    void organicShapesAreCenteredAndKeepTheirProportions() {
        for (String name : new String[]{"Heart", "Lemon", "Onion", "Jellybean", "Leaf", "Curl", "Fur"}) {
            double volume = fill(shape(name));
            assertTrue(volume > 0.005 && volume < 0.7, name + " ocupa " + volume);
        }
        ProceduralShapes.Implicit heart = shape("Heart");
        assertTrue(heart.inside(0, 0, 0.45), "los lóbulos del corazón van a los lados (z)");
        assertFalse(heart.inside(0.6, 0, 0), "el corazón es delgado de delante a atrás (x)");
        ProceduralShapes.Implicit leaf = shape("Leaf");
        assertFalse(leaf.inside(0.5, 0.1, 0.0), "la hoja es fina en x");
        assertTrue(leaf.inside(0.0, 0.1, 0.0));
        ProceduralShapes.Implicit lemon = shape("Lemon");
        assertTrue(lemon.inside(0.9, 0, 0) || lemon.inside(0.85, 0, 0), "el limón va tumbado a lo largo de x");
        assertFalse(lemon.inside(0, 0.9, 0));
    }

    @Test
    void furChangesWithTheSeedButNotWithinOne() {
        ProceduralShapes.Spec fur = ProceduralShapes.parse("Fur(Strands:12)");
        assertEquals(fill(fur.create(3L)), fill(fur.create(3L)), 0.0);
        assertNotEquals(fill(fur.create(3L)), fill(fur.create(4L)));
    }

    @Test
    void customFormulasSeeTheSeed() {
        ProceduralShapes.Spec ball = ProceduralShapes.parse("=x*x+y*y+z*z<0.5");
        assertTrue(ball.create(1).inside(0, 0, 0));
        assertFalse(ball.create(1).inside(0.9, 0, 0));
        ProceduralShapes.Spec seeded = ProceduralShapes.parse("=seed%2==0");
        assertTrue(seeded.create(4).inside(0, 0, 0));
        assertFalse(seeded.create(5).inside(0, 0, 0));
    }

    @Test
    void unknownShapesAndParametersFail() {
        assertThrows(IllegalArgumentException.class, () -> ProceduralShapes.parse("Banana"));
        assertThrows(IllegalArgumentException.class, () -> ProceduralShapes.parse("Torus(Grosorr:1)"));
        assertThrows(IllegalArgumentException.class, () -> ProceduralShapes.parse("Polygon(Sides:2)"));
    }

    @Test
    void perfectIcosphereIsAlmostASphere() {
        Icosphere smooth = new Icosphere(1L, 0.0, 4, 0.0);
        assertEquals(20 * 256, smooth.faces());
        assertEquals(2562, smooth.vertices());
        assertEquals(Math.PI / 6, fill(smooth), 0.015);
        Icosphere coarse = new Icosphere(1L, 0.0, 0, 0.0);
        // Un icosaedro llena menos que la esfera que lo envuelve.
        assertTrue(fill(coarse) < fill(smooth));
        assertTrue(coarse.inside(0, 0, 0));
    }

    @Test
    void icosphereRandomnessIsSeededAndPlatformFlattensTheTop() {
        Icosphere first = new Icosphere(11L, 1.0, 1, 0.0);
        Icosphere again = new Icosphere(11L, 1.0, 1, 0.0);
        Icosphere other = new Icosphere(12L, 1.0, 1, 0.0);
        assertEquals(fill(first), fill(again), 0.0);
        assertNotEquals(fill(first), fill(other));
        Icosphere flat = new Icosphere(11L, 0.0, 2, 0.6);
        assertTrue(flat.inside(0, -0.9, 0));
        assertFalse(flat.inside(0, 0.6, 0), "la mitad de arriba queda a 0.4 de altura");
        assertTrue(flat.inside(0, 0.3, 0));
        assertThrows(IllegalArgumentException.class, () -> new Icosphere(1L, 0.5, 5, 0));
    }

    @Test
    void icosphereFillsItsBoxOnEveryAxis() {
        Icosphere rock = new Icosphere(5L, 0.8, 0, 0.0);
        for (int axis = 0; axis < 3; axis++) {
            boolean low = false;
            boolean high = false;
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    double u = coordinate(i);
                    double v = coordinate(j);
                    double edge = 0.97;
                    low |= axis == 0 ? rock.inside(-edge, u, v) : axis == 1 ? rock.inside(u, -edge, v)
                            : rock.inside(u, v, -edge);
                    high |= axis == 0 ? rock.inside(edge, u, v) : axis == 1 ? rock.inside(u, edge, v)
                            : rock.inside(u, v, edge);
                }
            }
            assertTrue(low && high, "la roca toca las dos caras del eje " + axis);
        }
    }
}
