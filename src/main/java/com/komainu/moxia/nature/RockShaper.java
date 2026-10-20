package com.komainu.moxia.nature;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.noise.FastNoiseLite;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Rocas, peñascos y agujas. El cuerpo es un campo de distancia: un elipsoide (o un huso que se estrecha, en las
 * agujas) cortado por caras planas al azar, deformado con ruido sobre coordenadas distorsionadas y surcado por grietas
 * (los bordes de unas celdas de Voronoi) y, en los peñascos, por estratos. Se hunde en el terreno una parte de su alto.
 *
 * <p>El tono de cada bloque visible sale de su altura, de hacia dónde mira (luz desde arriba y el sur), de lo
 * encajonado que está (oclusión) y de las grietas: más oscuro abajo y en los huecos, más claro en lo alto. Con musgo,
 * lo cubre por arriba y en los rincones húmedos.</p>
 */
public final class RockShaper {
    private static final double LIGHT_X = 0.32;
    private static final double LIGHT_Y = 0.9;
    private static final double LIGHT_Z = 0.28;

    private RockShaper() {
    }

    /** Radio horizontal que puede ocupar desde el centro, para saber cuánto capturar. */
    public static int reach(RockSettings settings) {
        double radius = settings.size() * Math.sqrt(settings.elongation()) * 1.25;
        double tall = height(settings);
        double lean = tall * Math.sin(Math.toRadians(settings.lean()));
        return (int) Math.ceil(radius + lean + settings.size() * 0.25 + 4.0);
    }

    /** Alto total del cuerpo (sin hundir), en bloques. */
    public static double height(RockSettings settings) {
        return settings.size() * settings.aspect() * 2.0;
    }

    /**
     * @param x,y,z celda sobre la que se apoya: {@code y} es la primera celda libre encima del suelo
     */
    public static NatureVoxels shape(RockSettings settings, NatureTerrain terrain, Bounds bounds, int x, int y, int z,
                                     long seed, int limit, BooleanSupplier cancelled) {
        Body body = new Body(settings, terrain, x, y, z, seed);
        NatureVoxels voxels = new NatureVoxels(limit);
        LongOpenHashSet cells = new LongOpenHashSet();
        int minX = (int) Math.floor(body.centerX - body.extent);
        int maxX = (int) Math.floor(body.centerX + body.extent);
        int minY = (int) Math.floor(body.centerY - body.extent);
        int maxY = (int) Math.floor(body.centerY + body.extent);
        int minZ = (int) Math.floor(body.centerZ - body.extent);
        int maxZ = (int) Math.floor(body.centerZ + body.extent);
        for (int cx = minX; cx <= maxX; cx++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            for (int cz = minZ; cz <= maxZ; cz++) {
                for (int cy = minY; cy <= maxY; cy++) {
                    if (!bounds.contains(cx, cy, cz) || !allowsRock(terrain.kind(cx, cy, cz))) {
                        continue;
                    }
                    if (body.field(cx + 0.5, cy + 0.5, cz + 0.5) <= 0.0) {
                        cells.add(PackedBlockPlan.pack(cx, cy, cz));
                        if (cells.size() > limit) {
                            throw new com.komainu.moxia.shape.GeometryLimitException(limit);
                        }
                    }
                }
            }
        }
        LongArrayList body6 = largestComponent(cells);
        LongOpenHashSet rock = new LongOpenHashSet(body6);
        int top = Integer.MIN_VALUE;
        for (int index = 0; index < body6.size(); index++) {
            top = Math.max(top, PackedBlockPlan.unpackY(body6.getLong(index)));
        }
        int bottom = body.groundLevel;
        double span = Math.max(1.0, top - bottom + 1.0);
        boolean mossy = settings.moss() != null && settings.mossAmount() > 0.0;
        for (int index = 0; index < body6.size(); index++) {
            if ((index & 1_023) == 0 && cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            long key = body6.getLong(index);
            int cx = PackedBlockPlan.unpackX(key);
            int cy = PackedBlockPlan.unpackY(key);
            int cz = PackedBlockPlan.unpackZ(key);
            if (!exposed(rock, terrain, cx, cy, cz)) {
                voxels.set(key, NatureVoxels.Role.ROCK, 0, 0, 40, true);
                continue;
            }
            double px = cx + 0.5;
            double py = cy + 0.5;
            double pz = cz + 0.5;
            double gradientX = body.field(px + 0.6, py, pz) - body.field(px - 0.6, py, pz);
            double gradientY = body.field(px, py + 0.6, pz) - body.field(px, py - 0.6, pz);
            double gradientZ = body.field(px, py, pz + 0.6) - body.field(px, py, pz - 0.6);
            double length = Math.sqrt(gradientX * gradientX + gradientY * gradientY + gradientZ * gradientZ);
            double normalY = length < 1.0E-9 ? 0.0 : gradientY / length;
            double light = length < 1.0E-9 ? 0.5
                    : (gradientX * LIGHT_X + gradientY * LIGHT_Y + gradientZ * LIGHT_Z) / length;
            double occlusion = occlusion(rock, cx, cy, cz);
            double height = Math.clamp((cy - bottom) / span, 0.0, 1.0);
            double crack = body.crack(px, py, pz);
            double grain = body.grain(px, py, pz);
            double shade = 0.5 + 0.42 * (height - 0.45) + 0.34 * (light - 0.35) - 1.3 * (occlusion - 0.46)
                    - 0.45 * crack + 0.1 * grain;
            shade = 0.5 + (shade - 0.5) * 1.35;
            if (mossy && moss(rock, terrain, body, settings, cx, cy, cz, normalY, height, occlusion)) {
                voxels.set(key, NatureVoxels.Role.MOSS, 0, 0, (int) Math.round(Math.clamp(shade, 0.0, 1.0) * 255),
                        true);
                continue;
            }
            voxels.set(key, NatureVoxels.Role.ROCK, 0, 0, (int) Math.round(Math.clamp(shade, 0.0, 1.0) * 255),
                    true);
        }
        return voxels;
    }

    static boolean allowsRock(NatureTerrain.Kind kind) {
        return switch (kind) {
            case FREE, LIQUID, SOIL, STONE -> true;
            case LEAVES, HARD -> false;
        };
    }

    private static boolean exposed(LongOpenHashSet rock, NatureTerrain terrain, int x, int y, int z) {
        for (int face = 0; face < 6; face++) {
            int nx = x + TreeBuilder.DX[face];
            int ny = y + TreeBuilder.DY[face];
            int nz = z + TreeBuilder.DZ[face];
            if (rock.contains(PackedBlockPlan.pack(nx, ny, nz))) {
                continue;
            }
            NatureTerrain.Kind kind = terrain.kind(nx, ny, nz);
            if (kind == NatureTerrain.Kind.FREE || kind == NatureTerrain.Kind.LIQUID
                    || kind == NatureTerrain.Kind.LEAVES) {
                return true;
            }
        }
        return false;
    }

    /** Parte de las 124 celdas vecinas (5×5×5) que son roca: alto en los rincones, bajo en las aristas. */
    private static double occlusion(LongOpenHashSet rock, int x, int y, int z) {
        int count = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((dx != 0 || dy != 0 || dz != 0) && rock.contains(PackedBlockPlan.pack(x + dx, y + dy,
                            z + dz))) {
                        count++;
                    }
                }
            }
        }
        return count / 124.0;
    }

    private static boolean moss(LongOpenHashSet rock, NatureTerrain terrain, Body body, RockSettings settings,
                                int x, int y, int z, double normalY, double height, double occlusion) {
        if (rock.contains(PackedBlockPlan.pack(x, y + 1, z)) || terrain.kind(x, y + 1, z) != NatureTerrain.Kind.FREE) {
            return false;
        }
        double patches = body.moss(x + 0.5, y + 0.5, z + 0.5);
        double score = normalY * 0.6 + height * 0.25 + (occlusion - 0.45) * 0.6 + patches * 0.55
                + (settings.mossAmount() - 0.5) * 1.2;
        return score > 0.72;
    }

    private static LongArrayList largestComponent(LongOpenHashSet cells) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList best = new LongArrayList();
        long[] keys = cells.toLongArray();
        java.util.Arrays.sort(keys);
        for (long start : keys) {
            if (!seen.add(start)) {
                continue;
            }
            LongArrayList component = new LongArrayList();
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            queue.enqueue(start);
            while (!queue.isEmpty()) {
                long key = queue.dequeueLong();
                component.add(key);
                int x = PackedBlockPlan.unpackX(key);
                int y = PackedBlockPlan.unpackY(key);
                int z = PackedBlockPlan.unpackZ(key);
                for (int face = 0; face < 6; face++) {
                    long next = PackedBlockPlan.pack(x + TreeBuilder.DX[face], y + TreeBuilder.DY[face],
                            z + TreeBuilder.DZ[face]);
                    if (cells.contains(next) && seen.add(next)) {
                        queue.enqueue(next);
                    }
                }
            }
            if (component.size() > best.size()) {
                best = component;
            }
        }
        best.sort(null);
        return best;
    }

    /** El cuerpo de la roca como campo de distancia aproximado (negativo dentro, en bloques). */
    private static final class Body {
        final RockType type;
        final double centerX;
        final double centerY;
        final double centerZ;
        final double extent;
        final int groundLevel;
        // Base local: columnas = ejes de la roca en coordenadas de mundo.
        final double[] axisX = new double[3];
        final double[] axisY = new double[3];
        final double[] axisZ = new double[3];
        final double radiusX;
        final double radiusY;
        final double radiusZ;
        final double[] facetX;
        final double[] facetY;
        final double[] facetZ;
        final double[] facetOffset;
        final double amplitude;
        final double warpAmount;
        final double crackDepth;
        final double strata;
        final double layer;
        final FastNoiseLite shape;
        final FastNoiseLite warp;
        final FastNoiseLite cells;
        final FastNoiseLite detail;

        Body(RockSettings settings, NatureTerrain terrain, int x, int y, int z, long seed) {
            type = settings.type();
            SplittableRandom random = new SplittableRandom(seed);
            double size = settings.size();
            double stretch = Math.sqrt(settings.elongation());
            radiusX = size * stretch;
            radiusZ = size / stretch;
            radiusY = size * settings.aspect();
            double rough = settings.roughness();
            amplitude = rough * (0.12 * size + 0.35);
            warpAmount = 0.25 * size * rough;
            crackDepth = settings.cracks() * (0.45 + 0.1 * size);
            strata = type == RockType.CRAG ? settings.cracks() : 0.0;
            layer = Math.max(1.6, size * 0.3);
            int noiseSeed = (int) (seed ^ (seed >>> 32));
            shape = noise(noiseSeed, FastNoiseLite.NoiseType.OpenSimplex2, 1.1 / size, 3);
            warp = noise(noiseSeed + 1, FastNoiseLite.NoiseType.OpenSimplex2, 0.55 / size, 2);
            detail = noise(noiseSeed + 2, FastNoiseLite.NoiseType.OpenSimplex2, 0.35, 2);
            cells = new FastNoiseLite(noiseSeed + 3);
            cells.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
            cells.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
            cells.SetFrequency((float) (0.75 / size));

            // Giro en planta y, si la hay, inclinación hacia un rumbo al azar.
            double yaw = random.nextDouble() * Math.PI * 2.0;
            double tilt = Math.toRadians(settings.lean());
            double heading = random.nextDouble() * Math.PI * 2.0;
            double[] up = {Math.sin(tilt) * Math.cos(heading), Math.cos(tilt), Math.sin(tilt) * Math.sin(heading)};
            double[] side = {Math.cos(yaw), 0.0, Math.sin(yaw)};
            double along = side[0] * up[0] + side[1] * up[1] + side[2] * up[2];
            for (int axis = 0; axis < 3; axis++) {
                side[axis] -= up[axis] * along;
            }
            normalize(side);
            double[] front = cross(side, up);
            System.arraycopy(side, 0, axisX, 0, 3);
            System.arraycopy(up, 0, axisY, 0, 3);
            System.arraycopy(front, 0, axisZ, 0, 3);

            int facets = settings.facets();
            facetX = new double[facets];
            facetY = new double[facets];
            facetZ = new double[facets];
            facetOffset = new double[facets];
            for (int facet = 0; facet < facets; facet++) {
                double nx;
                double ny;
                double nz;
                if (type == RockType.SPIRE) {
                    double angle = random.nextDouble() * Math.PI * 2.0;
                    ny = random.nextDouble() * 0.5 - 0.15;
                    double horizontal = Math.sqrt(1.0 - ny * ny);
                    nx = Math.cos(angle) * horizontal;
                    nz = Math.sin(angle) * horizontal;
                } else if (type == RockType.CRAG && facet < 2) {
                    // Mesetas: los peñascos tienen la cima cortada casi plana.
                    nx = (random.nextDouble() - 0.5) * 0.3;
                    ny = 1.0;
                    nz = (random.nextDouble() - 0.5) * 0.3;
                } else {
                    double[] direction = sphere(random);
                    nx = direction[0];
                    ny = direction[1];
                    nz = direction[2];
                }
                double norm = Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= norm;
                ny /= norm;
                nz /= norm;
                double support = type == RockType.SPIRE
                        ? Math.sqrt(sq(radiusX * nx) + sq(radiusZ * nz)) + Math.abs(ny) * radiusY * 0.3
                        : Math.sqrt(sq(radiusX * nx) + sq(radiusY * ny) + sq(radiusZ * nz));
                double cut = type == RockType.CRAG && facet < 2 ? 0.78 + random.nextDouble() * 0.14
                        : 0.8 + random.nextDouble() * 0.17;
                facetX[facet] = nx;
                facetY[facet] = ny;
                facetZ[facet] = nz;
                facetOffset[facet] = support * cut;
            }

            // Apoyo: la altura media del suelo bajo la planta, para que no resbale en una ladera.
            int sum = 0;
            int samples = 0;
            int reach = (int) Math.max(1, Math.round(size * 0.6));
            for (int dx = -reach; dx <= reach; dx += reach) {
                for (int dz = -reach; dz <= reach; dz += reach) {
                    int surface = terrain.surface(x + dx, z + dz, y + 6, y - 16);
                    if (surface != Integer.MIN_VALUE) {
                        sum += surface;
                        samples++;
                    }
                }
            }
            groundLevel = samples == 0 ? y : (int) Math.round(sum / (double) samples) + 1;
            centerX = x + 0.5;
            centerZ = z + 0.5;
            centerY = groundLevel + radiusY * (1.0 - 2.0 * settings.sink());
            extent = Math.max(radiusX, Math.max(radiusY, radiusZ)) * 1.15 + amplitude + crackDepth + 2.0;
        }

        double field(double px, double py, double pz) {
            double dx = px - centerX;
            double dy = py - centerY;
            double dz = pz - centerZ;
            double qx = dx * axisX[0] + dy * axisX[1] + dz * axisX[2];
            double qy = dx * axisY[0] + dy * axisY[1] + dz * axisY[2];
            double qz = dx * axisZ[0] + dy * axisZ[1] + dz * axisZ[2];
            // Coordenadas distorsionadas: el ruido no sale en rejilla.
            double wx = qx + warpAmount * warp.GetNoise(qx, qy, qz);
            double wy = qy + warpAmount * warp.GetNoise(qx + 31.7, qy, qz);
            double wz = qz + warpAmount * warp.GetNoise(qx, qy, qz + 57.1);
            double distance = type == RockType.SPIRE ? spire(qx, qy, qz) : ellipsoid(qx, qy, qz);
            for (int facet = 0; facet < facetX.length; facet++) {
                distance = Math.max(distance, qx * facetX[facet] + qy * facetY[facet] + qz * facetZ[facet]
                        - facetOffset[facet]);
            }
            distance -= amplitude * shape.GetNoise(wx, wy, wz);
            distance += crackDepth * crackAt(wx, wy, wz);
            if (strata > 0.0) {
                double band = Math.abs(fraction(qy / layer + 0.35 * warp.GetNoise(wx * 0.5, 0.0, wz * 0.5)) - 0.5)
                        * 2.0;
                distance += strata * 0.9 * smooth(0.78, 1.0, band);
            }
            return distance;
        }

        private double ellipsoid(double qx, double qy, double qz) {
            double scaled = Math.sqrt(sq(qx / radiusX) + sq(qy / radiusY) + sq(qz / radiusZ));
            double length = Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (scaled < 1.0E-9) {
                return -Math.min(radiusX, Math.min(radiusY, radiusZ));
            }
            return length * (1.0 - 1.0 / scaled);
        }

        /** Huso: radio que se estrecha con la altura, cima redondeada y base que sigue bajo tierra. */
        private double spire(double qx, double qy, double qz) {
            double t = Math.clamp((qy + radiusY) / (2.0 * radiusY), 0.0, 1.0);
            double taper = 1.0 - 0.8 * Math.pow(t, 0.9);
            double sideX = radiusX * taper;
            double sideZ = radiusZ * taper;
            double radial = Math.sqrt(sq(qx / sideX) + sq(qz / sideZ));
            double side = (radial - 1.0) * Math.min(sideX, sideZ);
            double cap = qy - radiusY + Math.sqrt(sq(qx) + sq(qz)) * 0.6;
            return Math.max(side, Math.max(cap, -qy - radiusY * 1.4));
        }

        /** Grieta en [0, 1]: 1 sobre los bordes de las celdas de Voronoi. */
        double crack(double px, double py, double pz) {
            double dx = px - centerX;
            double dy = py - centerY;
            double dz = pz - centerZ;
            double qx = dx * axisX[0] + dy * axisX[1] + dz * axisX[2];
            double qy = dx * axisY[0] + dy * axisY[1] + dz * axisY[2];
            double qz = dx * axisZ[0] + dy * axisZ[1] + dz * axisZ[2];
            return crackAt(qx, qy, qz);
        }

        private double crackAt(double qx, double qy, double qz) {
            // En las agujas las grietas van a lo largo: columnas.
            double vertical = type == RockType.SPIRE ? 0.3 : 1.0;
            double edge = cells.GetNoise(qx, qy * vertical, qz) + 1.0;
            return 1.0 - smooth(0.0, 0.16, edge);
        }

        double grain(double px, double py, double pz) {
            return detail.GetNoise(px, py, pz);
        }

        double moss(double px, double py, double pz) {
            return detail.GetNoise(px * 0.6 + 101.0, py * 0.6, pz * 0.6);
        }

        private static FastNoiseLite noise(int seed, FastNoiseLite.NoiseType type, double frequency, int octaves) {
            FastNoiseLite noise = new FastNoiseLite(seed);
            noise.SetNoiseType(type);
            noise.SetFrequency((float) frequency);
            noise.SetFractalType(FastNoiseLite.FractalType.FBm);
            noise.SetFractalOctaves(octaves);
            return noise;
        }

        private static double[] sphere(SplittableRandom random) {
            while (true) {
                double x = random.nextDouble() * 2.0 - 1.0;
                double y = random.nextDouble() * 2.0 - 1.0;
                double z = random.nextDouble() * 2.0 - 1.0;
                double squared = x * x + y * y + z * z;
                if (squared > 0.01 && squared <= 1.0) {
                    return new double[]{x, y, z};
                }
            }
        }

        private static void normalize(double[] vector) {
            double length = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
            if (length < 1.0E-9) {
                vector[0] = 1.0;
                vector[1] = 0.0;
                vector[2] = 0.0;
                return;
            }
            vector[0] /= length;
            vector[1] /= length;
            vector[2] /= length;
        }

        private static double[] cross(double[] first, double[] second) {
            return new double[]{
                    first[1] * second[2] - first[2] * second[1],
                    first[2] * second[0] - first[0] * second[2],
                    first[0] * second[1] - first[1] * second[0]
            };
        }
    }

    private static double sq(double value) {
        return value * value;
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    private static double smooth(double from, double to, double value) {
        double t = Math.clamp((value - from) / (to - from), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
