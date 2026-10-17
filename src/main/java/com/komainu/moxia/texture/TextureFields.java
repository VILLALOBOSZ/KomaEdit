package com.komainu.moxia.texture;

import com.komainu.moxia.noise.NoiseSpec;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Los modos de {@code /komaedit texture}, reimplementados a partir de la documentación pública de
 * {@code //eztexture} de ezEdits. Cada uno es un {@link TextureField} puro sobre {@link TextureContext}.
 *
 * <p>Convención de valores: 0 es el principio de la paleta y 1 el final. Lo oscuro, ocluido, cóncavo, de cara a
 * la luz o poco iluminado según el modo va hacia el principio (con {@code ##Grayscale}, que empieza en negro, las
 * grietas salen oscuras). Los modos de superficie (ambient, curvature, luces, flow, blocklight) solo dan valor a
 * los bloques con alguna cara al aire; los de volumen (axisgradient, noise, cells) a todos.</p>
 */
public final class TextureFields {
    private static final double[][] SPHERE = Vectors.sphere(64);

    private TextureFields() {
    }

    /** Oclusión ambiental: qué parte del cielo ve cada bloque en un radio, suavizada como un campo continuo. */
    public record Ambient(int radius, double brightness, double contrast) implements TextureField {
        public Ambient {
            radius = Math.clamp(radius, 1, 12);
        }

        @Override
        public int padding() {
            return 2 * radius + 2;
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[][] normals = context.normals(1);
            double reach = 2.0 * radius + 1.0;
            double[] local = context.empty();
            for (int index = 0; index < context.size(); index++) {
                context.checkCancelled();
                if (!context.surface(index)) {
                    continue;
                }
                local[index] = openness(context, index, normals[index], reach);
            }
            double[] smooth = context.smoothSurface(local, radius / 2.0);
            double[] values = context.empty();
            for (int index = 0; index < values.length; index++) {
                if (!Double.isNaN(local[index])) {
                    values[index] = smooth[index] + (local[index] - smooth[index]) * contrast;
                }
            }
            values = TextureField.stretch(values);
            return TextureField.adjust(values, brightness, 0.0);
        }

        /** Rayos por el hemisferio de la normal; un rayo que choca pronto tapa más que uno que choca lejos. */
        private static double openness(TextureContext context, int index, double[] normal, double reach) {
            int bx = context.x(index);
            int by = context.y(index);
            int bz = context.z(index);
            double cx = bx + 0.5;
            double cy = by + 0.5;
            double cz = bz + 0.5;
            double total = 0.0;
            double open = 0.0;
            for (double[] direction : SPHERE) {
                double weight = Vectors.dot(direction, normal);
                if (weight <= 0.05) {
                    continue;
                }
                total += weight;
                double reached = 1.0;
                for (double step = 0.75; step <= reach; step += 0.5) {
                    int x = (int) Math.floor(cx + direction[0] * step);
                    int y = (int) Math.floor(cy + direction[1] * step);
                    int z = (int) Math.floor(cz + direction[2] * step);
                    if ((x != bx || y != by || z != bz) && context.solid(x, y, z)) {
                        reached = step / reach;
                        break;
                    }
                }
                open += weight * reached;
            }
            return total > 0.0 ? open / total : 1.0;
        }
    }

    /** Curvatura: proporción de material alrededor; en un plano es la mitad, en una arista menos y en un rincón más. */
    public record Curvature(int radius, double brightness, double contrast) implements TextureField {
        public Curvature {
            radius = Math.clamp(radius, 1, 16);
        }

        @Override
        public int padding() {
            return radius + 1;
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[] values = context.empty();
            double volume = Math.pow(2 * radius + 1, 3);
            double flat = (radius + 1.0) / (2.0 * radius + 1.0);
            for (int index = 0; index < context.size(); index++) {
                context.checkCancelled();
                if (!context.surface(index)) {
                    continue;
                }
                double solid = context.solidInBox(context.x(index), context.y(index), context.z(index), radius)
                        / volume;
                values[index] = Math.clamp(0.5 + (flat - solid) * 2.0, 0.0, 1.0);
            }
            return TextureField.adjust(values, brightness, contrast);
        }
    }

    /** Degradado a lo largo de un eje (0 = x, 1 = y, 2 = z); relativo: cada columna estira la paleta entera. */
    public record AxisGradient(int axis, boolean relative) implements TextureField {
        @Override
        public int padding() {
            return 0;
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[] values = context.empty();
            if (context.size() == 0) {
                return values;
            }
            if (!relative) {
                int minimum = Integer.MAX_VALUE;
                int maximum = Integer.MIN_VALUE;
                for (int index = 0; index < context.size(); index++) {
                    int coordinate = coordinate(context, index);
                    minimum = Math.min(minimum, coordinate);
                    maximum = Math.max(maximum, coordinate);
                }
                for (int index = 0; index < context.size(); index++) {
                    values[index] = ratio(coordinate(context, index), minimum, maximum);
                }
                return values;
            }
            Long2ObjectOpenHashMap<int[]> columns = new Long2ObjectOpenHashMap<>();
            for (int index = 0; index < context.size(); index++) {
                context.checkCancelled();
                int coordinate = coordinate(context, index);
                int[] range = columns.computeIfAbsent(column(context, index), key -> new int[]{
                        Integer.MAX_VALUE, Integer.MIN_VALUE});
                range[0] = Math.min(range[0], coordinate);
                range[1] = Math.max(range[1], coordinate);
            }
            for (int index = 0; index < context.size(); index++) {
                int[] range = columns.get(column(context, index));
                values[index] = ratio(coordinate(context, index), range[0], range[1]);
            }
            return values;
        }

        private int coordinate(TextureContext context, int index) {
            return switch (axis) {
                case 0 -> context.x(index);
                case 2 -> context.z(index);
                default -> context.y(index);
            };
        }

        private long column(TextureContext context, int index) {
            long first = axis == 0 ? context.y(index) : context.x(index);
            long second = axis == 2 ? context.y(index) : context.z(index);
            return (first << 32) ^ (second & 0xFFFFFFFFL);
        }

        private static double ratio(int value, int minimum, int maximum) {
            return maximum == minimum ? 0.5 : (value - minimum) / (double) (maximum - minimum);
        }
    }

    /** Ruido de {@link NoiseSpec} muestreado en cada bloque; {@code scale} agranda el dibujo. */
    public record Noise(NoiseSpec noise, double scale, long seed) implements TextureField {
        @Override
        public int padding() {
            return 0;
        }

        @Override
        public double[] evaluate(TextureContext context) {
            NoiseSpec.NoiseField field = noise.build(seed);
            double factor = 1.0 / Math.max(1.0E-3, scale);
            double[] values = context.empty();
            for (int index = 0; index < context.size(); index++) {
                context.checkCancelled();
                values[index] = Math.clamp(field.sample(context.x(index) * factor, context.y(index) * factor,
                        context.z(index) * factor), 0.0, 1.0);
            }
            return values;
        }
    }

    /**
     * Celdas de Voronoi: {@code amount} centros repartidos por los bloques de superficie, cada uno con un valor al
     * azar. La repulsión elige cada centro entre varios candidatos, el más alejado de los demás, así que con más
     * repulsión las celdas salen más regulares.
     */
    public record Cells(int amount, double brightness, double contrast, long seed, int repulsion)
            implements TextureField {
        public Cells {
            amount = Math.clamp(amount, 1, 4_096);
            repulsion = Math.clamp(repulsion, 1, 64);
        }

        @Override
        public int padding() {
            return 0;
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[] values = context.empty();
            if (context.size() == 0) {
                return values;
            }
            int[] candidates = candidates(context);
            SplittableRandom random = new SplittableRandom(seed);
            int count = Math.min(amount, candidates.length);
            int[][] points = new int[count][];
            double[] cellValues = new double[count];
            for (int point = 0; point < count; point++) {
                context.checkCancelled();
                int[] best = null;
                long bestDistance = -1;
                for (int attempt = 0; attempt < repulsion; attempt++) {
                    int candidate = candidates[random.nextInt(candidates.length)];
                    int[] position = {context.x(candidate), context.y(candidate), context.z(candidate)};
                    long distance = Long.MAX_VALUE;
                    for (int other = 0; other < point; other++) {
                        distance = Math.min(distance, distanceSquared(points[other], position));
                    }
                    if (distance > bestDistance) {
                        bestDistance = distance;
                        best = position;
                    }
                }
                points[point] = best;
                cellValues[point] = random.nextDouble();
            }
            CellGrid grid = new CellGrid(points);
            for (int index = 0; index < context.size(); index++) {
                context.checkCancelled();
                int nearest = grid.nearest(context.x(index), context.y(index), context.z(index));
                values[index] = cellValues[nearest];
            }
            return TextureField.adjust(values, brightness, contrast);
        }

        private static int[] candidates(TextureContext context) {
            int[] result = new int[context.surfaceCount() > 0 ? context.surfaceCount() : context.size()];
            int position = 0;
            for (int index = 0; index < context.size(); index++) {
                if (context.surfaceCount() == 0 || context.surface(index)) {
                    result[position++] = index;
                }
            }
            return result;
        }

        private static long distanceSquared(int[] first, int[] second) {
            long dx = first[0] - second[0];
            long dy = first[1] - second[1];
            long dz = first[2] - second[2];
            return dx * dx + dy * dy + dz * dz;
        }
    }

    /**
     * Luz puntual o del sol: el ángulo entre la normal de cada bloque y la dirección hacia la luz, recortado al
     * intervalo {@code from}–{@code to} en grados. De cara a la luz (0°) es el principio de la paleta; lo que queda
     * por debajo o por encima del intervalo se lleva el primer o el último bloque. La luz puntual se atenúa con la
     * distancia y las dos pueden proyectar sombras ({@code occlusion}, suavizadas con {@code occlusionRadius}).
     *
     * @param position         posición de la luz puntual (sus coordenadas); {@code null} = el jugador
     * @param direction        dirección en la que alumbra el sol; {@code null} si es luz puntual
     * @param falloffRange     alcance de la luz puntual; 0 = la distancia del jugador al centro de la selección
     */
    public record Light(
            double[] position,
            double[] direction,
            double falloffRange,
            boolean falloff,
            int normalRadius,
            double from,
            double to,
            double occlusion,
            double occlusionRadius
    ) implements TextureField {
        public static Light point(double[] position, double falloffRange, boolean falloff, int radius, double from,
                                  double to, double occlusion, double occlusionRadius) {
            return new Light(position, null, falloffRange, falloff, radius, from, to, occlusion, occlusionRadius);
        }

        public static Light sun(double[] direction, int radius, double from, double to, double occlusion,
                                double occlusionRadius) {
            return new Light(null, direction, 0.0, false, radius, from, to, occlusion, occlusionRadius);
        }

        @Override
        public int padding() {
            return Math.clamp(normalRadius, 1, 6) + 2;
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[] values = context.empty();
            if (context.surfaceCount() == 0) {
                return values;
            }
            double[][] normals = context.normals(normalRadius);
            boolean isPoint = direction == null;
            double[] source = isPoint ? lightPosition(context) : null;
            double[] towardsSun = isPoint ? null : new double[]{-direction[0], -direction[1], -direction[2]};
            double range = isPoint ? range(context, source) : 0.0;
            double[] lit = context.empty();
            double[] shadow = occlusion > 0.0 ? context.empty() : null;
            double minimumAngle = Math.min(from, to);
            double maximumAngle = Math.max(from, to);
            for (int index = 0; index < context.size(); index++) {
                context.checkCancelled();
                if (!context.surface(index)) {
                    continue;
                }
                double cx = context.x(index) + 0.5;
                double cy = context.y(index) + 0.5;
                double cz = context.z(index) + 0.5;
                double[] towards;
                double distance;
                if (isPoint) {
                    towards = new double[]{source[0] - cx, source[1] - cy, source[2] - cz};
                    distance = Vectors.normalize(towards);
                } else {
                    towards = towardsSun;
                    distance = Double.POSITIVE_INFINITY;
                }
                double angle = Math.toDegrees(Math.acos(Math.clamp(Vectors.dot(normals[index], towards), -1.0, 1.0)));
                double position = maximumAngle - minimumAngle < 1.0E-9
                        ? (angle < minimumAngle ? 0.0 : 1.0)
                        : Math.clamp((angle - minimumAngle) / (maximumAngle - minimumAngle), 0.0, 1.0);
                double brightness = 1.0 - position;
                if (isPoint && falloff) {
                    double ratio = distance / (2.0 * range);
                    brightness *= Math.clamp(1.0 - ratio * ratio, 0.0, 1.0);
                }
                lit[index] = brightness;
                if (shadow != null) {
                    shadow[index] = blocked(context, index, normals[index], towards, distance) ? 1.0 : 0.0;
                }
            }
            if (shadow != null) {
                shadow = context.smoothSurface(shadow, occlusionRadius / 2.0);
            }
            double strength = Math.clamp(occlusion, 0.0, 1.0);
            for (int index = 0; index < values.length; index++) {
                if (Double.isNaN(lit[index])) {
                    continue;
                }
                double light = lit[index] * (shadow == null ? 1.0 : 1.0 - strength * shadow[index]);
                values[index] = 1.0 - light;
            }
            return values;
        }

        private double[] lightPosition(TextureContext context) {
            if (position != null) {
                return position;
            }
            TextureContext.Settings settings = context.settings();
            return new double[]{settings.playerX(), settings.playerY(), settings.playerZ()};
        }

        private double range(TextureContext context, double[] source) {
            if (falloffRange > 0.0) {
                return falloffRange;
            }
            double sx = 0.0;
            double sy = 0.0;
            double sz = 0.0;
            for (int index = 0; index < context.size(); index++) {
                sx += context.x(index) + 0.5;
                sy += context.y(index) + 0.5;
                sz += context.z(index) + 0.5;
            }
            int count = Math.max(1, context.size());
            double dx = sx / count - source[0];
            double dy = sy / count - source[1];
            double dz = sz / count - source[2];
            return Math.max(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }

        /** Rayo de sombra desde la cara del bloque hasta la luz (o hasta salir de la foto, para el sol). */
        private static boolean blocked(TextureContext context, int index, double[] normal, double[] towards,
                                       double distance) {
            int bx = context.x(index);
            int by = context.y(index);
            int bz = context.z(index);
            double cx = bx + 0.5 + normal[0] * 0.6;
            double cy = by + 0.5 + normal[1] * 0.6;
            double cz = bz + 0.5 + normal[2] * 0.6;
            double limit = Math.min(distance, 512.0);
            for (double step = 0.0; step <= limit; step += 0.5) {
                int x = (int) Math.floor(cx + towards[0] * step);
                int y = (int) Math.floor(cy + towards[1] * step);
                int z = (int) Math.floor(cz + towards[2] * step);
                if (!context.snapshot().contains(x, y, z)) {
                    return false;
                }
                if ((x != bx || y != by || z != bz) && context.solid(x, y, z)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Luz de bloque del mundo (antorchas, lámparas…), la que llega a las caras al aire de cada bloque.
     *
     * @param vertical     mirar solo la luz del bloque de encima
     * @param skylight     contar también la luz del cielo
     * @param keepSources  no cambiar los bloques que emiten luz
     */
    public record BlockLight(boolean vertical, boolean skylight, boolean keepSources) implements TextureField {
        private static final int[][] FACES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

        @Override
        public boolean needsLight() {
            return true;
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[] values = context.empty();
            var snapshot = context.snapshot();
            for (int index = 0; index < context.size(); index++) {
                context.checkCancelled();
                if (!context.surface(index)) {
                    continue;
                }
                int x = context.x(index);
                int y = context.y(index);
                int z = context.z(index);
                if (keepSources && snapshot.luminance(x, y, z) > 0) {
                    continue;
                }
                int level = 0;
                for (int[] face : FACES) {
                    if (vertical && face[1] != 1) {
                        continue;
                    }
                    int nx = x + face[0];
                    int ny = y + face[1];
                    int nz = z + face[2];
                    if (snapshot.isSolid(nx, ny, nz)) {
                        continue;
                    }
                    int light = snapshot.blockLight(nx, ny, nz);
                    if (skylight) {
                        light = Math.max(light, snapshot.skyLight(nx, ny, nz));
                    }
                    level = Math.max(level, light);
                }
                values[index] = level / 15.0;
            }
            return values;
        }
    }

    /**
     * Campo de flujo: líneas que nacen en bloques de superficie al azar y avanzan pegadas a ella siguiendo la
     * dirección que marca el ruido. Cada bloque recorrido toma el avance de la línea (0 al nacer, 1 al final) por
     * {@code scalar}; los que ninguna línea pisa se quedan como están salvo con {@code fill}, que les da el primer
     * bloque de la paleta.
     *
     * @param exposure   densidad de líneas: más o menos la parte de la superficie que acaban cubriendo
     * @param momentum   0–1, cuánto pesa la dirección anterior en cada paso
     * @param gravity    dirección hacia la que tiran los puntos, o {@code null}
     */
    public record Flow(
            double exposure,
            int iterations,
            double velocity,
            double scalar,
            NoiseSpec noise,
            double momentum,
            double[] gravity,
            boolean fill,
            long seed
    ) implements TextureField {
        public Flow {
            exposure = Math.clamp(exposure, 0.0, 8.0);
            iterations = Math.clamp(iterations, 1, 4_096);
            velocity = Math.clamp(velocity, 0.05, 4.0);
            momentum = Math.clamp(momentum, 0.0, 0.95);
        }

        public Flow withFill() {
            return fill ? this : new Flow(exposure, iterations, velocity, scalar, noise, momentum, gravity, true, seed);
        }

        @Override
        public int padding() {
            return 2;
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[] values = context.empty();
            int[] surfaces = new int[context.surfaceCount()];
            int position = 0;
            for (int index = 0; index < context.size(); index++) {
                if (context.surface(index)) {
                    surfaces[position++] = index;
                }
            }
            if (surfaces.length == 0) {
                return values;
            }
            double[][] normals = context.normals(1);
            NoiseSpec.NoiseField field = noise.build(seed);
            SplittableRandom random = new SplittableRandom(seed ^ 0x5DEECE66DL);
            long lines = Math.round(exposure * surfaces.length / Math.max(1.0, iterations * velocity));
            lines = Math.clamp(lines, 1L, 250_000L);
            for (long line = 0; line < lines; line++) {
                trace(context, normals, field, surfaces[random.nextInt(surfaces.length)], values);
            }
            if (fill) {
                for (int index = 0; index < values.length; index++) {
                    if (context.surface(index) && Double.isNaN(values[index])) {
                        values[index] = 0.0;
                    }
                }
            }
            return values;
        }

        private void trace(TextureContext context, double[][] normals, NoiseSpec.NoiseField field, int start,
                           double[] values) {
            int current = start;
            double px = context.x(current) + 0.5;
            double py = context.y(current) + 0.5;
            double pz = context.z(current) + 0.5;
            double[] previous = null;
            for (int step = 0; step < iterations; step++) {
                context.checkCancelled();
                double progress = iterations > 1 ? step / (double) (iterations - 1) : 0.0;
                double value = Math.clamp(progress * scalar, 0.0, 1.0);
                if (Double.isNaN(values[current]) || values[current] < value) {
                    values[current] = value;
                }
                double[] normal = normals[current];
                double[][] tangents = Vectors.tangents(normal);
                double angle = field.sample(px, py, pz) * Math.PI * 4.0;
                double[] direction = {
                        tangents[0][0] * Math.cos(angle) + tangents[1][0] * Math.sin(angle),
                        tangents[0][1] * Math.cos(angle) + tangents[1][1] * Math.sin(angle),
                        tangents[0][2] * Math.cos(angle) + tangents[1][2] * Math.sin(angle)
                };
                if (gravity != null) {
                    direction[0] += gravity[0];
                    direction[1] += gravity[1];
                    direction[2] += gravity[2];
                }
                if (previous != null && momentum > 0.0) {
                    for (int axis = 0; axis < 3; axis++) {
                        direction[axis] = previous[axis] * momentum + direction[axis] * (1.0 - momentum);
                    }
                }
                double along = Vectors.dot(direction, normal);
                for (int axis = 0; axis < 3; axis++) {
                    direction[axis] -= normal[axis] * along;
                }
                if (Vectors.normalize(direction) < 1.0E-6) {
                    return;
                }
                previous = direction;
                px += direction[0] * velocity;
                py += direction[1] * velocity;
                pz += direction[2] * velocity;
                int next = nearestSurface(context, px, py, pz);
                if (next < 0) {
                    return;
                }
                if (next != current) {
                    // Se pega al plano de la cara nueva para no despegarse de la superficie.
                    double[] nextNormal = normals[next];
                    double offset = (px - context.x(next) - 0.5) * nextNormal[0]
                            + (py - context.y(next) - 0.5) * nextNormal[1]
                            + (pz - context.z(next) - 0.5) * nextNormal[2];
                    px -= nextNormal[0] * offset;
                    py -= nextNormal[1] * offset;
                    pz -= nextNormal[2] * offset;
                    current = next;
                }
            }
        }

        private static int nearestSurface(TextureContext context, double px, double py, double pz) {
            int bx = (int) Math.floor(px);
            int by = (int) Math.floor(py);
            int bz = (int) Math.floor(pz);
            int direct = context.index(bx, by, bz);
            if (direct >= 0 && context.surface(direct)) {
                return direct;
            }
            int best = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int candidate = context.index(bx + dx, by + dy, bz + dz);
                        if (candidate < 0 || !context.surface(candidate)) {
                            continue;
                        }
                        double ox = bx + dx + 0.5 - px;
                        double oy = by + dy + 0.5 - py;
                        double oz = bz + dz + 0.5 - pz;
                        double distance = ox * ox + oy * oy + oz * oz;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = candidate;
                        }
                    }
                }
            }
            return best;
        }
    }

    /** Operaciones entre dos texturas de {@code advanced}. */
    public enum Operation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE, WEIGHTED_AVERAGE, DARKEN, LIGHTEN, DIFFERENCE, SCREEN;

        double apply(double first, double second, double weight) {
            return switch (this) {
                case ADD -> Math.min(1.0, first + second);
                case SUBTRACT -> Math.max(0.0, first - second);
                case MULTIPLY -> first * second;
                case DIVIDE -> second <= 1.0E-9 ? 1.0 : Math.min(1.0, first / second);
                case WEIGHTED_AVERAGE -> first * (1.0 - weight) + second * weight;
                case DARKEN -> Math.min(first, second);
                case LIGHTEN -> Math.max(first, second);
                case DIFFERENCE -> Math.abs(first - second);
                case SCREEN -> 1.0 - (1.0 - first) * (1.0 - second);
            };
        }
    }

    /** Combina dos texturas bloque a bloque; donde a una le falta valor, el bloque se queda como está. */
    public record Combine(Operation operation, TextureField first, TextureField second, double weight)
            implements TextureField {
        @Override
        public int padding() {
            return Math.max(first.padding(), second.padding());
        }

        @Override
        public boolean needsLight() {
            return first.needsLight() || second.needsLight();
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[] a = first.evaluate(context);
            double[] b = second.evaluate(context);
            double[] values = context.empty();
            for (int index = 0; index < values.length; index++) {
                if (!Double.isNaN(a[index]) && !Double.isNaN(b[index])) {
                    values[index] = Math.clamp(operation.apply(a[index], b[index], weight), 0.0, 1.0);
                }
            }
            return values;
        }
    }

    /** {@code Adjust(T, Brightness, Contrast)}. */
    public record Adjust(TextureField texture, double brightness, double contrast) implements TextureField {
        @Override
        public int padding() {
            return texture.padding();
        }

        @Override
        public boolean needsLight() {
            return texture.needsLight();
        }

        @Override
        public double[] evaluate(TextureContext context) {
            return TextureField.adjust(texture.evaluate(context), brightness, contrast);
        }
    }

    /** {@code Invert(T)}: da la vuelta a la paleta. */
    public record Invert(TextureField texture) implements TextureField {
        @Override
        public int padding() {
            return texture.padding();
        }

        @Override
        public boolean needsLight() {
            return texture.needsLight();
        }

        @Override
        public double[] evaluate(TextureContext context) {
            double[] values = texture.evaluate(context);
            double[] result = new double[values.length];
            for (int index = 0; index < values.length; index++) {
                result[index] = 1.0 - values[index];
            }
            return result;
        }
    }

    /** {@code Blend(T, Radius)}: suaviza otra textura entre bloques vecinos. */
    public record Smooth(TextureField texture, double radius) implements TextureField {
        @Override
        public int padding() {
            return texture.padding() + (int) Math.ceil(Math.min(radius, 4.0));
        }

        @Override
        public boolean needsLight() {
            return texture.needsLight();
        }

        @Override
        public double[] evaluate(TextureContext context) {
            return context.smoothSurface(texture.evaluate(context), radius);
        }
    }

    /** Busca el centro más cercano con una rejilla uniforme (unos pocos centros por celda). */
    private static final class CellGrid {
        private final int[][] points;
        private final int minimumX;
        private final int minimumY;
        private final int minimumZ;
        private final int cell;
        private final int cellsX;
        private final int cellsY;
        private final int cellsZ;
        private final int[][] buckets;

        CellGrid(int[][] points) {
            this.points = points;
            int[] minimum = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
            int[] maximum = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (int[] point : points) {
                for (int axis = 0; axis < 3; axis++) {
                    minimum[axis] = Math.min(minimum[axis], point[axis]);
                    maximum[axis] = Math.max(maximum[axis], point[axis]);
                }
            }
            minimumX = minimum[0];
            minimumY = minimum[1];
            minimumZ = minimum[2];
            double volume = (maximum[0] - minimum[0] + 1.0) * (maximum[1] - minimum[1] + 1.0)
                    * (maximum[2] - minimum[2] + 1.0);
            cell = Math.max(1, (int) Math.ceil(Math.cbrt(volume / Math.max(1, points.length) * 2.0)));
            cellsX = (maximum[0] - minimum[0]) / cell + 1;
            cellsY = (maximum[1] - minimum[1]) / cell + 1;
            cellsZ = (maximum[2] - minimum[2]) / cell + 1;
            int[] counts = new int[cellsX * cellsY * cellsZ];
            for (int[] point : points) {
                counts[bucket(point[0], point[1], point[2])]++;
            }
            buckets = new int[counts.length][];
            for (int bucket = 0; bucket < counts.length; bucket++) {
                buckets[bucket] = new int[counts[bucket]];
            }
            Arrays.fill(counts, 0);
            for (int point = 0; point < points.length; point++) {
                int bucket = bucket(points[point][0], points[point][1], points[point][2]);
                buckets[bucket][counts[bucket]++] = point;
            }
        }

        int nearest(int x, int y, int z) {
            int cx = Math.clamp(Math.floorDiv(x - minimumX, cell), 0, cellsX - 1);
            int cy = Math.clamp(Math.floorDiv(y - minimumY, cell), 0, cellsY - 1);
            int cz = Math.clamp(Math.floorDiv(z - minimumZ, cell), 0, cellsZ - 1);
            int best = 0;
            long bestDistance = Long.MAX_VALUE;
            int maximumRing = Math.max(cellsX, Math.max(cellsY, cellsZ));
            for (int ring = 0; ring <= maximumRing; ring++) {
                for (int gy = cy - ring; gy <= cy + ring; gy++) {
                    for (int gz = cz - ring; gz <= cz + ring; gz++) {
                        for (int gx = cx - ring; gx <= cx + ring; gx++) {
                            boolean shell = Math.abs(gx - cx) == ring || Math.abs(gy - cy) == ring
                                    || Math.abs(gz - cz) == ring;
                            if (!shell || gx < 0 || gy < 0 || gz < 0 || gx >= cellsX || gy >= cellsY
                                    || gz >= cellsZ) {
                                continue;
                            }
                            for (int point : buckets[(gy * cellsZ + gz) * cellsX + gx]) {
                                long dx = points[point][0] - x;
                                long dy = points[point][1] - y;
                                long dz = points[point][2] - z;
                                long distance = dx * dx + dy * dy + dz * dz;
                                if (distance < bestDistance) {
                                    bestDistance = distance;
                                    best = point;
                                }
                            }
                        }
                    }
                }
                // Lo que quede por mirar está al menos a ring·cell bloques del punto.
                if (bestDistance != Long.MAX_VALUE) {
                    long reach = (long) ring * cell;
                    if (reach * reach >= bestDistance) {
                        return best;
                    }
                }
            }
            return best;
        }

        private int bucket(int x, int y, int z) {
            int bx = Math.clamp((x - minimumX) / cell, 0, cellsX - 1);
            int by = Math.clamp((y - minimumY) / cell, 0, cellsY - 1);
            int bz = Math.clamp((z - minimumZ) / cell, 0, cellsZ - 1);
            return (by * cellsZ + bz) * cellsX + bx;
        }
    }
}
