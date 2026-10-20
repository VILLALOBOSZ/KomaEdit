package com.komainu.moxia.recipe;

import com.komainu.moxia.path.PathSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** «Pegar al terreno» sin Minecraft: lo mismo para el servidor, el holograma del Studio y los dibujos. */
class SurfaceConformTest {
    /** Suelo a la altura 60 y un techo a partir de 80. */
    private static final SurfaceConform.Terrain CAVE = new SurfaceConform.Terrain() {
        @Override
        public boolean solid(int x, int y, int z) {
            return y <= 60 || y >= 80;
        }

        @Override
        public boolean empty(int x, int y, int z) {
            return !solid(x, y, z);
        }
    };

    private static PathSnapshot path(int y) {
        return new PathSnapshot(List.of(BlockVector3.at(0, y, 0), BlockVector3.at(10, y, 0)), false, "prueba");
    }

    @Test
    void pointsDropToTheGroundOrRiseToTheCeiling() {
        RecipeExtensions floor = RecipeExtensions.DEFAULT.withSurface(SurfaceMode.FLOOR, 12, 0);
        assertEquals(61, SurfaceConform.project(CAVE, path(70), floor).points().getFirst().y(),
                "sobre el suelo, no dentro");
        assertEquals(63, SurfaceConform.project(CAVE, path(70),
                RecipeExtensions.DEFAULT.withSurface(SurfaceMode.FLOOR, 12, 2)).points().getFirst().y(), "dos encima");
        assertEquals(79, SurfaceConform.project(CAVE, path(70),
                RecipeExtensions.DEFAULT.withSurface(SurfaceMode.CEILING, 12, 0)).points().getFirst().y());
        assertEquals(66, SurfaceConform.project(CAVE, path(66),
                RecipeExtensions.DEFAULT.withSurface(SurfaceMode.FLOOR, 4, 0)).points().getFirst().y(),
                "el suelo está más lejos que el alcance: el punto se queda");
    }

    @Test
    void theNearestSurfaceCanBeAWall() {
        SurfaceConform.Terrain wall = new SurfaceConform.Terrain() {
            @Override
            public boolean solid(int x, int y, int z) {
                return x >= 5;
            }

            @Override
            public boolean empty(int x, int y, int z) {
                return x < 5;
            }
        };
        PathSnapshot path = new PathSnapshot(List.of(BlockVector3.at(2, 70, 0), BlockVector3.at(2, 70, 9)), false,
                "pared");
        PathSnapshot stuck = SurfaceConform.project(wall, path,
                RecipeExtensions.DEFAULT.withSurface(SurfaceMode.NEAREST, 8, 0));
        assertEquals(4, stuck.points().getFirst().x(), "pegado a la pared, por fuera");
    }

    @Test
    void withoutGroundingThePathIsTheSameAndCollapsingIsAnError() {
        PathSnapshot path = path(70);
        assertSame(path, SurfaceConform.project(CAVE, path, RecipeExtensions.DEFAULT));
        PathSnapshot vertical = new PathSnapshot(List.of(BlockVector3.at(0, 70, 0), BlockVector3.at(0, 75, 0)), false,
                "vertical");
        assertThrows(IllegalArgumentException.class, () -> SurfaceConform.project(CAVE, vertical,
                RecipeExtensions.DEFAULT.withSurface(SurfaceMode.FLOOR, 20, 0)), "los dos al mismo bloque");
    }
}
