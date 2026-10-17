package com.komainu.moxia.glass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Busca la pila de cristales tintados cuyo color, visto de frente sobre un fondo, más se acerca a cada color de un
 * degradado (como {@code //ezstainedglassgradient} de ezEdits).
 *
 * <p>El color se compone como lo pinta Minecraft: cada capa mezcla su tinte por encima de lo que hay detrás con su
 * alfa. De delante hacia atrás: {@code color += paso·α·tinte; paso *= 1 − α}, y al final {@code color += paso·fondo}.
 * La búsqueda va capa a capa desde la de delante y conserva las {@code 4·2^calidad} pilas más prometedoras (búsqueda
 * en haz); con pocas capas equivale a probarlas todas. La comparación es en OKLab.</p>
 */
public final class GlassGradientSolver {
    public static final int MAX_LAYERS = 16;
    public static final int MAX_LENGTH = 512;
    public static final int MAX_QUALITY = 10;

    private final List<GlassColors.Swatch> glass;
    private final double[] lower = {255, 255, 255};
    private final double[] upper = {0, 0, 0};

    public GlassGradientSolver(List<GlassColors.Swatch> glass) {
        this.glass = List.copyOf(glass);
    }

    public static GlassGradientSolver standard() {
        return new GlassGradientSolver(GlassColors.GLASS);
    }

    /**
     * @param start       color inicial (sRGB 0–255)
     * @param end         color final
     * @param backgrounds fondos posibles; con uno solo es el color de fondo fijo ({@code -c}), con varios se elige el
     *                    mejor bloque para cada paso ({@code -b})
     * @param unordered   probar cada mezcla de cristales una sola vez, sin importar el orden ({@code -s})
     */
    public List<Stack> gradient(double[] start, double[] end, int layers, int length, int quality,
                                List<GlassColors.Swatch> backgrounds, boolean unordered, BooleanSupplier cancelled) {
        if (layers < 1 || layers > MAX_LAYERS) {
            throw new IllegalArgumentException("Las capas van de 1 a " + MAX_LAYERS);
        }
        if (length < 1 || length > MAX_LENGTH) {
            throw new IllegalArgumentException("La longitud va de 1 a " + MAX_LENGTH);
        }
        if (quality < 1 || quality > MAX_QUALITY) {
            throw new IllegalArgumentException("La calidad va de 1 a " + MAX_QUALITY);
        }
        if (backgrounds.isEmpty()) {
            throw new IllegalArgumentException("Falta el fondo del degradado");
        }
        bounds(backgrounds);
        double[] startLab = GlassColors.oklab(start);
        double[] endLab = GlassColors.oklab(end);
        Map<String, Stack> cache = new HashMap<>();
        List<Stack> result = new ArrayList<>(length);
        for (int step = 0; step < length; step++) {
            if (cancelled.getAsBoolean()) {
                throw new java.util.concurrent.CancellationException();
            }
            double t = length == 1 ? 0.0 : step / (double) (length - 1);
            double[] lab = {
                    startLab[0] + (endLab[0] - startLab[0]) * t,
                    startLab[1] + (endLab[1] - startLab[1]) * t,
                    startLab[2] + (endLab[2] - startLab[2]) * t
            };
            double[] target = GlassColors.rgb(lab);
            result.add(cache.computeIfAbsent(GlassColors.toHex(target),
                    key -> solve(target, layers, quality, backgrounds, unordered)));
        }
        return result;
    }

    /** La mejor pila para un color. */
    public Stack solve(double[] target, int layers, int quality, List<GlassColors.Swatch> backgrounds,
                       boolean unordered) {
        bounds(backgrounds);
        int width = 4 << Math.clamp(quality, 1, MAX_QUALITY);
        double[] targetLab = GlassColors.oklab(target);
        List<Partial> beam = List.of(new Partial(new int[0], new double[3], 1.0));
        for (int depth = 0; depth < layers; depth++) {
            List<Partial> next = new ArrayList<>(beam.size() * glass.size());
            for (Partial partial : beam) {
                int first = unordered && partial.layers.length > 0 ? partial.layers[partial.layers.length - 1] : 0;
                for (int choice = first; choice < glass.size(); choice++) {
                    next.add(partial.with(choice, glass.get(choice)));
                }
            }
            // En la última capa con un solo fondo se miden todas exactas; con varios fondos se recorta antes.
            if (depth < layers - 1 || backgrounds.size() > 1) {
                for (Partial partial : next) {
                    partial.score = optimistic(partial, targetLab);
                }
                next.sort(Comparator.comparingDouble(partial -> partial.score));
                beam = dedupe(next, width);
            } else {
                beam = next;
            }
        }
        Stack best = null;
        for (Partial partial : beam) {
            for (GlassColors.Swatch background : backgrounds) {
                double[] color = {
                        partial.color[0] + partial.transmission * background.red(),
                        partial.color[1] + partial.transmission * background.green(),
                        partial.color[2] + partial.transmission * background.blue()
                };
                double error = distance(GlassColors.oklab(color), targetLab);
                if (best == null || error < best.error()) {
                    best = new Stack(ids(partial.layers), background.blockId(), color, target, error);
                }
            }
        }
        return best;
    }

    /** Error si el resto de la pila pudiera dar justo el color que falta (recortado a los colores posibles). */
    private double optimistic(Partial partial, double[] targetLab) {
        double[] target = GlassColors.rgb(targetLab);
        double[] color = new double[3];
        for (int channel = 0; channel < 3; channel++) {
            double needed = partial.transmission < 1.0E-9 ? 0.0
                    : (target[channel] - partial.color[channel]) / partial.transmission;
            color[channel] = partial.color[channel]
                    + partial.transmission * Math.clamp(needed, lower[channel], upper[channel]);
        }
        return distance(GlassColors.oklab(color), targetLab);
    }

    /** Las {@code width} mejores, sin repetir color (dos pilas que dan el mismo color valen lo mismo). */
    private static List<Partial> dedupe(List<Partial> sorted, int width) {
        List<Partial> kept = new ArrayList<>(width);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (Partial partial : sorted) {
            long key = Math.round(partial.color[0] * 4) * 1_000_003L * 1_000_003L
                    + Math.round(partial.color[1] * 4) * 1_000_003L + Math.round(partial.color[2] * 4)
                    + Math.round(partial.transmission * 4096) * 31L;
            if (seen.add(key)) {
                kept.add(partial);
                if (kept.size() >= width) {
                    break;
                }
            }
        }
        return kept;
    }

    private void bounds(List<GlassColors.Swatch> backgrounds) {
        Arrays.fill(lower, 255);
        Arrays.fill(upper, 0);
        List<GlassColors.Swatch> all = new ArrayList<>(backgrounds);
        for (GlassColors.Swatch swatch : glass) {
            if (swatch.alpha() > 0.0) {
                all.add(swatch);
            }
        }
        for (GlassColors.Swatch swatch : all) {
            double[] rgb = swatch.rgb();
            for (int channel = 0; channel < 3; channel++) {
                lower[channel] = Math.min(lower[channel], rgb[channel]);
                upper[channel] = Math.max(upper[channel], rgb[channel]);
            }
        }
    }

    private List<String> ids(int[] layers) {
        List<String> ids = new ArrayList<>(layers.length);
        for (int layer : layers) {
            ids.add(glass.get(layer).blockId());
        }
        return ids;
    }

    private static double distance(double[] first, double[] second) {
        double dl = first[0] - second[0];
        double da = first[1] - second[1];
        double db = first[2] - second[2];
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /**
     * Resultado de un paso: cristales de delante (lado del jugador) hacia atrás, el fondo, el color que se ve, el que
     * se buscaba y la distancia OKLab entre ambos.
     */
    public record Stack(List<String> layers, String background, double[] color, double[] target, double error) {
    }

    private static final class Partial {
        private final int[] layers;
        private final double[] color;
        private final double transmission;
        private double score;

        Partial(int[] layers, double[] color, double transmission) {
            this.layers = layers;
            this.color = color;
            this.transmission = transmission;
        }

        Partial with(int choice, GlassColors.Swatch swatch) {
            int[] grown = Arrays.copyOf(layers, layers.length + 1);
            grown[layers.length] = choice;
            double weight = transmission * swatch.alpha();
            return new Partial(grown, new double[]{
                    color[0] + weight * swatch.red(),
                    color[1] + weight * swatch.green(),
                    color[2] + weight * swatch.blue()
            }, transmission * (1.0 - swatch.alpha()));
        }
    }
}
