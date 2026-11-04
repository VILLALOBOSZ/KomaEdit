package com.komainu.moxia.performance;

import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El rayo de la mirada del servidor se corta antes del primer chunk sin cargar: leer un bloque de un chunk sin cargar lo
 * carga (o lo genera) en el hilo del servidor.
 */
class LoadedRayTest {
    /** Chunks cargados, por sus coordenadas de chunk. */
    private static LoadedRay.Loaded loaded(Set<Long> chunks) {
        return (x, z) -> chunks.contains(key(x, z));
    }

    private static long key(int x, int z) {
        return (long) x << 32 ^ (z & 0xFFFFFFFFL);
    }

    private static int chunk(double coordinate) {
        return Math.floorDiv((int) Math.floor(coordinate), 16);
    }

    @Test
    void aRayInsideLoadedChunksIsNotTouched() {
        Vec3d start = new Vec3d(8.5, 70.6, 8.5);
        Vec3d end = new Vec3d(40.2, 20.0, -30.7);
        assertEquals(end, LoadedRay.clip(start, end, (x, z) -> true));
    }

    @Test
    void lookingAtTheHorizonStopsAtTheEdgeOfWhatIsLoaded() {
        // Distancia de visión de 2 chunks: de 0 a 2 cargados, el rayo de 192 bloques hacia el este se para en x = 48.
        Set<Long> chunks = Set.of(key(0, 0), key(1, 0), key(2, 0));
        Vec3d clipped = LoadedRay.clip(new Vec3d(8.5, 70.0, 8.5), new Vec3d(200.5, 70.0, 8.5), loaded(chunks));
        assertTrue(clipped.x < 48.0 && clipped.x > 47.9, "se para en " + clipped);
        assertEquals(70.0, clipped.y, 1e-9);
        Vec3d west = LoadedRay.clip(new Vec3d(8.5, 70.0, 8.5), new Vec3d(-183.5, 70.0, 8.5), loaded(chunks));
        assertTrue(west.x >= 0.0 && west.x < 0.01, "hacia el oeste se para en " + west);
    }

    @Test
    void throughACornerBothNeighboursMustBeLoaded() {
        // Por la esquina (16, 16): Minecraft puede pasar por (1, 0) o por (0, 1); si uno no está cargado, se para.
        Set<Long> chunks = Set.of(key(0, 0), key(0, 1), key(1, 1), key(2, 2));
        Vec3d clipped = LoadedRay.clip(new Vec3d(8.0, 64.0, 8.0), new Vec3d(40.0, 64.0, 40.0), loaded(chunks));
        assertTrue(clipped.x < 16.0 && clipped.z < 16.0, "pasa la esquina: " + clipped);
    }

    @Test
    void fromAnUnloadedChunkTheRayGoesNowhere() {
        Vec3d start = new Vec3d(8.0, 64.0, 8.0);
        assertEquals(start, LoadedRay.clip(start, new Vec3d(100.0, 64.0, 8.0), loaded(Set.of())));
    }

    @Test
    void whatIsLeftOfTheRayNeverEntersAnUnloadedChunk() {
        Random random = new Random(42L);
        for (int trial = 0; trial < 2_000; trial++) {
            Set<Long> chunks = new HashSet<>();
            for (int x = -14; x <= 14; x++) {
                for (int z = -14; z <= 14; z++) {
                    if (Math.abs(x) + Math.abs(z) <= 1 || random.nextInt(100) < 80) {
                        chunks.add(key(x, z));
                    }
                }
            }
            Vec3d start = new Vec3d(random.nextDouble() * 16.0, 64.0, random.nextDouble() * 16.0);
            double yaw = random.nextDouble() * Math.PI * 2.0;
            Vec3d end = start.add(Math.cos(yaw) * 192.0, random.nextDouble() * 40.0 - 20.0, Math.sin(yaw) * 192.0);
            if (trial % 50 == 0) {
                end = new Vec3d(start.x + 64.0, 64.0, start.z + 64.0);
            }
            Vec3d clipped = LoadedRay.clip(start, end, loaded(chunks));
            // Lo que recorrería Minecraft hasta el final recortado (con su 1e-7 de más) está en chunks cargados.
            double length = start.distanceTo(clipped);
            int steps = (int) Math.ceil(length / 0.01);
            for (int step = 0; step <= steps; step++) {
                double t = steps == 0 ? 0.0 : (double) step / steps * (1.0 + 1e-7);
                Vec3d point = start.add(clipped.subtract(start).multiply(t));
                assertTrue(chunks.contains(key(chunk(point.x), chunk(point.z))),
                        "el rayo " + start + " → " + end + " recortado en " + clipped + " entra en " + point);
            }
            if (!clipped.equals(end)) {
                Vec3d direction = end.subtract(start).normalize();
                Vec3d beyond = clipped.add(direction.multiply(0.01));
                // Delante hay un chunk sin cargar (o, por una esquina, uno de los dos de al lado).
                boolean blocked = !chunks.contains(key(chunk(beyond.x), chunk(beyond.z)))
                        || !chunks.contains(key(chunk(beyond.x), chunk(clipped.z)))
                        || !chunks.contains(key(chunk(clipped.x), chunk(beyond.z)));
                assertTrue(blocked, "se paró en " + clipped + " sin un chunk sin cargar delante");
            }
        }
    }
}
