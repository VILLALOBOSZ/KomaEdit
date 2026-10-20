package com.komainu.moxia.nature;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Colonización del espacio (Runions, Lane y Prusinkiewicz, 2007): se reparten puntos de atracción por la copa y las
 * ramas crecen paso a paso hacia los que tienen más cerca, hasta consumirlos. Da ramificaciones naturales que se
 * reparten solas por el volumen y no se cruzan, mejor que un L-system para copas de frondosas.
 *
 * <p>Cada punto atrae solo al nodo más cercano; el nodo crece en la media de las direcciones que le tiran, más el
 * tropismo (hacia arriba o hacia abajo), un giro opcional alrededor del tronco y un poco de ruido.</p>
 */
final class SpaceColonization {
    private static final int MAX_ITERATIONS = 900;

    private SpaceColonization() {
    }

    /** Nodos del árbol. Un hijo siempre tiene un índice mayor que su padre. */
    static final class Graph {
        double[] x = new double[256];
        double[] y = new double[256];
        double[] z = new double[256];
        int[] parent = new int[256];
        int[] firstChild = new int[256];
        int[] nextSibling = new int[256];
        boolean[] sprout = new boolean[256];
        int count;

        int add(double px, double py, double pz, int parentIndex, boolean canSprout) {
            if (count == x.length) {
                int size = count * 2;
                x = Arrays.copyOf(x, size);
                y = Arrays.copyOf(y, size);
                z = Arrays.copyOf(z, size);
                parent = Arrays.copyOf(parent, size);
                firstChild = Arrays.copyOf(firstChild, size);
                nextSibling = Arrays.copyOf(nextSibling, size);
                sprout = Arrays.copyOf(sprout, size);
            }
            int index = count++;
            x[index] = px;
            y[index] = py;
            z[index] = pz;
            parent[index] = parentIndex;
            firstChild[index] = -1;
            nextSibling[index] = -1;
            sprout[index] = canSprout;
            if (parentIndex >= 0) {
                // Al final de la lista de hijos, para que el orden no dependa de nada más que de la creación.
                int child = firstChild[parentIndex];
                if (child < 0) {
                    firstChild[parentIndex] = index;
                } else {
                    while (nextSibling[child] >= 0) {
                        child = nextSibling[child];
                    }
                    nextSibling[child] = index;
                }
            }
            return index;
        }

        boolean leaf(int index) {
            return firstChild[index] < 0;
        }
    }

    /**
     * @param segment   longitud de cada paso de crecimiento
     * @param kill      distancia a la que una rama consume un punto
     * @param tropism   empuje vertical por paso (positivo hacia arriba)
     * @param twist     empuje en espiral alrededor del eje del tronco
     * @param wander    ruido por paso
     * @param centerX   eje del tronco, para el giro
     * @param centerZ   eje del tronco, para el giro
     * @param maxNodes  tope de nodos
     */
    record Settings(double segment, double kill, double tropism, double twist, double wander, double centerX,
                    double centerZ, int maxNodes) {
    }

    static void grow(Graph graph, double[] ax, double[] ay, double[] az, Settings settings, SplittableRandom random,
                     BooleanSupplier cancelled) {
        int points = ax.length;
        boolean[] alive = new boolean[points];
        int[] closest = new int[points];
        double[] best = new double[points];
        Arrays.fill(alive, true);
        Arrays.fill(closest, -1);
        Arrays.fill(best, Double.MAX_VALUE);
        refresh(graph, 0, graph.count, ax, ay, az, alive, closest, best);
        double killSquared = settings.kill() * settings.kill();
        double duplicate = settings.segment() * settings.segment() * 0.16;
        for (int iteration = 0; iteration < MAX_ITERATIONS && graph.count < settings.maxNodes(); iteration++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            int nodes = graph.count;
            double[] sumX = new double[nodes];
            double[] sumY = new double[nodes];
            double[] sumZ = new double[nodes];
            int[] hits = new int[nodes];
            boolean any = false;
            for (int point = 0; point < points; point++) {
                if (!alive[point]) {
                    continue;
                }
                if (best[point] <= killSquared) {
                    alive[point] = false;
                    continue;
                }
                int node = closest[point];
                if (node < 0) {
                    continue;
                }
                double distance = Math.sqrt(best[point]);
                sumX[node] += (ax[point] - graph.x[node]) / distance;
                sumY[node] += (ay[point] - graph.y[node]) / distance;
                sumZ[node] += (az[point] - graph.z[node]) / distance;
                hits[node]++;
                any = true;
            }
            if (!any) {
                return;
            }
            int before = graph.count;
            for (int node = 0; node < nodes; node++) {
                if (hits[node] == 0) {
                    continue;
                }
                double length = Math.sqrt(sumX[node] * sumX[node] + sumY[node] * sumY[node]
                        + sumZ[node] * sumZ[node]);
                // Tirones opuestos: el nodo espera a que otro consuma esos puntos en vez de crecer al azar.
                if (length < 0.12 * hits[node] && hits[node] > 1) {
                    continue;
                }
                double dx = sumX[node] / Math.max(1.0E-9, length);
                double dy = sumY[node] / Math.max(1.0E-9, length);
                double dz = sumZ[node] / Math.max(1.0E-9, length);
                dy += settings.tropism();
                if (settings.twist() != 0.0) {
                    double radialX = graph.x[node] - settings.centerX();
                    double radialZ = graph.z[node] - settings.centerZ();
                    double radial = Math.sqrt(radialX * radialX + radialZ * radialZ);
                    if (radial > 1.0E-6) {
                        dx += -radialZ / radial * settings.twist();
                        dz += radialX / radial * settings.twist();
                    }
                }
                double wander = settings.wander();
                dx += (random.nextDouble() * 2.0 - 1.0) * wander;
                dy += (random.nextDouble() * 2.0 - 1.0) * wander;
                dz += (random.nextDouble() * 2.0 - 1.0) * wander;
                double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (norm < 1.0E-9) {
                    continue;
                }
                double step = settings.segment() / norm;
                double nx = graph.x[node] + dx * step;
                double ny = graph.y[node] + dy * step;
                double nz = graph.z[node] + dz * step;
                if (hasChildNear(graph, node, nx, ny, nz, duplicate)) {
                    continue;
                }
                graph.add(nx, ny, nz, node, true);
                if (graph.count >= settings.maxNodes()) {
                    break;
                }
            }
            if (graph.count == before) {
                return;
            }
            refresh(graph, before, graph.count, ax, ay, az, alive, closest, best);
        }
    }

    private static boolean hasChildNear(Graph graph, int node, double x, double y, double z, double squared) {
        for (int child = graph.firstChild[node]; child >= 0; child = graph.nextSibling[child]) {
            double dx = graph.x[child] - x;
            double dy = graph.y[child] - y;
            double dz = graph.z[child] - z;
            if (dx * dx + dy * dy + dz * dz < squared) {
                return true;
            }
        }
        return false;
    }

    /** Compara los puntos vivos con los nodos nuevos {@code [from, to)}. */
    private static void refresh(Graph graph, int from, int to, double[] ax, double[] ay, double[] az,
                                boolean[] alive, int[] closest, double[] best) {
        for (int point = 0; point < ax.length; point++) {
            if (!alive[point]) {
                continue;
            }
            double px = ax[point];
            double py = ay[point];
            double pz = az[point];
            double current = best[point];
            int chosen = closest[point];
            for (int node = from; node < to; node++) {
                if (!graph.sprout[node]) {
                    continue;
                }
                double dx = graph.x[node] - px;
                double dy = graph.y[node] - py;
                double dz = graph.z[node] - pz;
                double squared = dx * dx + dy * dy + dz * dz;
                if (squared < current) {
                    current = squared;
                    chosen = node;
                }
            }
            best[point] = current;
            closest[point] = chosen;
        }
    }
}
