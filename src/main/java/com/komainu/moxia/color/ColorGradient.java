package com.komainu.moxia.color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Degradados de bloques en OKLab: se reparten los pasos en línea recta entre dos colores y se elige un bloque para
 * cada paso, sin repetir ninguno.
 *
 * <p>No es un «el más cercano a cada paso» suelto: eso repite bloques y a veces da un paso atrás (un bloque más
 * oscuro en mitad de un degradado que aclara). Se resuelve la fila entera con programación dinámica minimizando la
 * suma de distancias al cuadrado con dos reglas: cada bloque avanza a lo largo del degradado más que el anterior (así
 * no se repiten ni retroceden) y, si los extremos tienen distinta luminosidad, la luminosidad no da marcha atrás.
 * Si con esas reglas no hay solución (dos extremos casi iguales, un filtro muy estricto), se van soltando.</p>
 *
 * <p>Las alternativas ({@code variante} 1, 2…) penalizan los bloques que ya salieron en las anteriores, así cada una
 * prueba otros bloques sin alejarse mucho del color.</p>
 */
public final class ColorGradient {
    /** Pasos como mucho (lo que cabe en una paleta larga). */
    public static final int MAX_STEPS = 32;
    /** Candidatos por paso en la primera pasada. */
    private static final int NEAREST = 40;
    /** Candidatos por paso cuando la primera pasada no encuentra solución. */
    private static final int WIDE = 400;
    /** Diferencia de luminosidad a partir de la cual se exige que no dé marcha atrás. */
    private static final double LIGHTNESS_STEP = 0.04;
    /** Coste extra por cada vez que un bloque salió en una variante anterior (0,05 de distancia OKLab). */
    private static final double REPEAT_PENALTY = 0.05 * 0.05;
    /**
     * En una rampa el tono pesa más que la luminosidad: un bloque un poco más claro u oscuro de lo pedido pasa, uno
     * de otro color (cobre en una rampa de arenisca) no.
     */
    static final double RAMP_HUE_WEIGHT = 4.0;
    /** Cuánto baja y cuánto sube la rampa desde el bloque: más sombras que brillos, como al sombrear a mano. */
    static final double RAMP_DOWN = 0.22;
    static final double RAMP_UP = 0.10;

    /** Lo que cuesta cada bloque: veces que salió en variantes anteriores y peso del tono frente a la luz. */
    private record Costs(Map<String, Integer> penalties, double hueWeight) {
        double of(BlockColor block, double[] target) {
            double dl = block.l() - target[0];
            double da = block.a() - target[1];
            double db = block.b() - target[2];
            return dl * dl + hueWeight * (da * da + db * db)
                    + REPEAT_PENALTY * penalties.getOrDefault(block.id(), 0);
        }
    }

    private ColorGradient() {
    }

    /** Un extremo: un bloque concreto (se queda fijo) o solo un color. */
    public record Anchor(BlockColor block, double[] lab) {
        public static Anchor of(BlockColor block) {
            return new Anchor(block, block.lab());
        }

        public static Anchor of(int rgb) {
            return new Anchor(null, OkLab.of(rgb));
        }

        public String label() {
            return block != null ? block.shortName() : OkLab.hex(OkLab.rgb(lab));
        }
    }

    /**
     * Degradado de {@code steps} bloques de {@code from} a {@code to}. Si un extremo es un bloque, va tal cual en su
     * punta; si es un color, se elige el bloque más cercano.
     *
     * @param variant 0 para el degradado más fiel; 1, 2… para alternativas
     */
    public static List<BlockColor> between(BlockColorCatalog catalog, Anchor from, Anchor to, int steps,
                                           ColorFilter filter, int variant) {
        if (steps < 2 || steps > MAX_STEPS) {
            throw new IllegalArgumentException("Los pasos van de 2 a " + MAX_STEPS);
        }
        List<double[]> targets = new ArrayList<>(steps);
        for (int step = 0; step < steps; step++) {
            targets.add(OkLab.lerp(from.lab(), to.lab(), step / (double) (steps - 1)));
        }
        Map<Integer, BlockColor> fixed = new HashMap<>();
        if (from.block() != null) {
            fixed.put(0, from.block());
        }
        if (to.block() != null) {
            fixed.put(steps - 1, to.block());
        }
        List<BlockColor> candidates = catalog.candidates(filter);
        return withVariants(candidates, targets, fixed, variant);
    }

    /**
     * Rampa de luz alrededor de un bloque: {@code size} bloques de oscuro a claro con su tono, con él en el sitio que
     * le toca por luminosidad. Es la paleta que usa el sombreado cuando la selección es de un solo material. Baja más
     * de lo que sube ({@link #RAMP_DOWN}, {@link #RAMP_UP}) y elige por tono antes que por luminosidad.
     */
    public static List<BlockColor> ramp(BlockColorCatalog catalog, BlockColor base, int size, ColorFilter filter) {
        if (size < 2 || size > MAX_STEPS) {
            throw new IllegalArgumentException("La rampa va de 2 a " + MAX_STEPS + " bloques");
        }
        double low = Math.max(0.12, base.l() - RAMP_DOWN);
        double high = Math.min(0.95, base.l() + RAMP_UP);
        if (high - low < 0.2) {
            // Un bloque casi blanco o casi negro: la rampa se abre hacia el otro lado.
            low = Math.max(0.05, Math.min(low, high - 0.25));
            high = Math.min(0.98, Math.max(high, low + 0.25));
        }
        int middle = (int) Math.round((base.l() - low) / (high - low) * (size - 1));
        middle = Math.clamp(middle, 0, size - 1);
        List<double[]> targets = new ArrayList<>(size);
        for (int step = 0; step < size; step++) {
            double t = step / (double) (size - 1);
            double lightness = low + (high - low) * t;
            // Los extremos pierden algo de saturación, como una sombra o un brillo de verdad.
            double saturation = 1.0 - 0.35 * Math.abs(step - middle) / Math.max(1.0, size - 1.0);
            targets.add(new double[]{lightness, base.a() * saturation, base.b() * saturation});
        }
        targets.set(middle, base.lab());
        Map<Integer, BlockColor> fixed = Map.of(middle, base);
        return solve(catalog.candidates(filter), targets, fixed, new Costs(Map.of(), RAMP_HUE_WEIGHT));
    }

    private static List<BlockColor> withVariants(List<BlockColor> candidates, List<double[]> targets,
                                                 Map<Integer, BlockColor> fixed, int variant) {
        Map<String, Integer> used = new HashMap<>();
        List<BlockColor> result = solve(candidates, targets, fixed, new Costs(used, 1.0));
        for (int round = 0; round < Math.max(0, variant); round++) {
            for (int step = 0; step < result.size(); step++) {
                if (!fixed.containsKey(step)) {
                    used.merge(result.get(step).id(), 1, Integer::sum);
                }
            }
            result = solve(candidates, targets, fixed, new Costs(used, 1.0));
        }
        return result;
    }

    /**
     * Elige un bloque por objetivo sin repetir, con los fijos en su sitio.
     *
     * @param penalties veces que salió cada bloque en variantes anteriores
     */
    static List<BlockColor> solve(List<BlockColor> candidates, List<double[]> targets, Map<Integer, BlockColor> fixed,
                                  Map<String, Integer> penalties) {
        return solve(candidates, targets, fixed, new Costs(penalties, 1.0));
    }

    private static List<BlockColor> solve(List<BlockColor> candidates, List<double[]> targets,
                                          Map<Integer, BlockColor> fixed, Costs penalties) {
        if (candidates.isEmpty() && fixed.size() < targets.size()) {
            throw new IllegalArgumentException("Ningún bloque pasa el filtro");
        }
        double[] first = targets.getFirst();
        double[] last = targets.getLast();
        double[] axis = {last[0] - first[0], last[1] - first[1], last[2] - first[2]};
        double axisLength = axis[0] * axis[0] + axis[1] * axis[1] + axis[2] * axis[2];
        boolean ordered = axisLength > 1.0E-6;
        int lightness = Math.abs(last[0] - first[0]) >= LIGHTNESS_STEP ? (int) Math.signum(last[0] - first[0]) : 0;
        if (ordered) {
            for (int nearest : new int[]{NEAREST, WIDE}) {
                List<BlockColor> found = path(candidates, targets, fixed, penalties, nearest, axis, axisLength,
                        lightness);
                if (found != null) {
                    return found;
                }
            }
        }
        List<BlockColor> greedy = greedy(candidates, targets, fixed, penalties);
        if (lightness == 0) {
            return greedy;
        }
        // Sin fila posible (pocos bloques en ese tramo de luz): los más cercanos, ordenados para que la luminosidad
        // no dé marcha atrás aunque algún bloque fijo cambie de sitio.
        List<BlockColor> sorted = new ArrayList<>(greedy);
        sorted.sort(Comparator.comparingDouble(block -> block.l() * lightness));
        return List.copyOf(sorted);
    }

    /** Programación dinámica por pasos; {@code null} si las reglas no dejan ninguna fila posible. */
    private static List<BlockColor> path(List<BlockColor> candidates, List<double[]> targets,
                                         Map<Integer, BlockColor> fixed, Costs penalties, int nearest,
                                         double[] axis, double axisLength, int lightness) {
        int steps = targets.size();
        BlockColor[][] options = new BlockColor[steps][];
        double[][] cost = new double[steps][];
        double[][] position = new double[steps][];
        Set<String> fixedIds = new HashSet<>();
        for (BlockColor block : fixed.values()) {
            fixedIds.add(block.id());
        }
        for (int step = 0; step < steps; step++) {
            double[] target = targets.get(step);
            BlockColor pinned = fixed.get(step);
            List<BlockColor> chosen;
            if (pinned != null) {
                chosen = List.of(pinned);
            } else {
                chosen = nearest(candidates, target, penalties, fixedIds, nearest);
            }
            options[step] = chosen.toArray(BlockColor[]::new);
            cost[step] = new double[options[step].length];
            position[step] = new double[options[step].length];
            for (int option = 0; option < options[step].length; option++) {
                BlockColor block = options[step][option];
                cost[step][option] = pinned != null ? 0.0 : penalties.of(block, target);
                position[step][option] = project(block.lab(), targets.getFirst(), axis, axisLength);
            }
        }
        double[][] best = new double[steps][];
        int[][] previous = new int[steps][];
        best[0] = cost[0].clone();
        previous[0] = new int[options[0].length];
        Arrays.fill(previous[0], -1);
        for (int step = 1; step < steps; step++) {
            best[step] = new double[options[step].length];
            previous[step] = new int[options[step].length];
            for (int option = 0; option < options[step].length; option++) {
                BlockColor block = options[step][option];
                double bestCost = Double.POSITIVE_INFINITY;
                int bestPrevious = -1;
                for (int before = 0; before < options[step - 1].length; before++) {
                    if (!Double.isFinite(best[step - 1][before])) {
                        continue;
                    }
                    BlockColor earlier = options[step - 1][before];
                    if (earlier.id().equals(block.id())
                            || position[step][option] <= position[step - 1][before] + 1.0E-9
                            || lightness != 0 && (block.l() - earlier.l()) * lightness < -1.0E-9) {
                        continue;
                    }
                    double total = best[step - 1][before] + cost[step][option];
                    if (total < bestCost) {
                        bestCost = total;
                        bestPrevious = before;
                    }
                }
                best[step][option] = bestCost;
                previous[step][option] = bestPrevious;
            }
        }
        int end = -1;
        double endCost = Double.POSITIVE_INFINITY;
        for (int option = 0; option < best[steps - 1].length; option++) {
            if (best[steps - 1][option] < endCost) {
                endCost = best[steps - 1][option];
                end = option;
            }
        }
        if (end < 0) {
            return null;
        }
        BlockColor[] result = new BlockColor[steps];
        for (int step = steps - 1, option = end; step >= 0; step--) {
            result[step] = options[step][option];
            option = previous[step][option];
        }
        return List.of(result);
    }

    /** Último recurso: el más cercano a cada paso que no se haya usado. */
    private static List<BlockColor> greedy(List<BlockColor> candidates, List<double[]> targets,
                                           Map<Integer, BlockColor> fixed, Costs penalties) {
        Set<String> used = new HashSet<>();
        for (BlockColor block : fixed.values()) {
            used.add(block.id());
        }
        List<BlockColor> result = new ArrayList<>(targets.size());
        for (int step = 0; step < targets.size(); step++) {
            BlockColor pinned = fixed.get(step);
            if (pinned != null) {
                result.add(pinned);
                continue;
            }
            BlockColor best = null;
            double bestCost = Double.POSITIVE_INFINITY;
            for (BlockColor block : candidates) {
                if (used.contains(block.id())) {
                    continue;
                }
                double value = penalties.of(block, targets.get(step));
                if (value < bestCost) {
                    bestCost = value;
                    best = block;
                }
            }
            if (best == null) {
                throw new IllegalArgumentException("No hay bloques suficientes para " + targets.size()
                        + " pasos con este filtro");
            }
            used.add(best.id());
            result.add(best);
        }
        return List.copyOf(result);
    }

    private static List<BlockColor> nearest(List<BlockColor> candidates, double[] target,
                                            Costs penalties, Set<String> excluded, int count) {
        List<BlockColor> sorted = new ArrayList<>(candidates.size());
        for (BlockColor block : candidates) {
            if (!excluded.contains(block.id())) {
                sorted.add(block);
            }
        }
        sorted.sort(Comparator.comparingDouble((BlockColor block) -> penalties.of(block, target))
                .thenComparing(BlockColor::id));
        return sorted.subList(0, Math.min(count, sorted.size()));
    }

    private static double project(double[] lab, double[] origin, double[] axis, double axisLength) {
        return ((lab[0] - origin[0]) * axis[0] + (lab[1] - origin[1]) * axis[1] + (lab[2] - origin[2]) * axis[2])
                / axisLength;
    }
}
