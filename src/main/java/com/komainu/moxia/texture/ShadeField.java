package com.komainu.moxia.texture;

import java.util.Arrays;

/**
 * Luz de {@code /komaedit shade}: cuánta luz recibe cada bloque de superficie, de 0 (el más oscuro) a 1 (el más
 * claro), para elegir el bloque de una paleta ordenada de oscuro a claro.
 *
 * <p>Suma una luz de ambiente y una directa. La directa sigue la ley de Lambert (la cara que mira a la luz recibe
 * más que la que la recibe de refilón, y la de espaldas nada) y se apaga en las sombras que proyecta el propio
 * volumen. Las dos se oscurecen con la oclusión ambiental: rincones, grietas y huecos ven menos cielo. Al final los
 * niveles se reparten para usar la paleta entera sin exagerar diferencias pequeñas.</p>
 *
 * @param towards         dirección unitaria hacia la luz (sol); {@code null} si es una luz puntual
 * @param point           posición de la luz puntual; {@code null} si es direccional
 * @param occlusion       fuerza de la oclusión ambiental, de 0 a 1
 * @param occlusionRadius alcance de la oclusión en bloques
 * @param shadows         fuerza de las sombras proyectadas, de 0 a 1
 * @param normalRadius    radio para estimar la orientación de cada bloque (más radio, superficie más suave)
 * @param target          bloques que se recolorean; el resto da {@code NaN} y se queda como está
 * @param pivot           dónde cae la luz mediana (de 0 a 1): el sitio del material base en una rampa, para que siga
 *                        siendo el que más se ve; {@code NaN} reparte por igual
 */
public record ShadeField(
        double[] towards,
        double[] point,
        double occlusion,
        int occlusionRadius,
        double shadows,
        int normalRadius,
        TextureContext.Filter target,
        double pivot
) implements TextureField {
    /** Parte de la luz que llega aunque la cara no mire a la luz (el cielo). */
    static final double AMBIENT = 0.3;
    /** Rango mínimo de luz que se estira a la paleta entera: menos variación no se exagera. */
    static final double MINIMUM_RANGE = 0.3;
    private static final double[][] SPHERE = Vectors.sphere(48);

    public ShadeField {
        if ((towards == null) == (point == null)) {
            throw new IllegalArgumentException("La luz es direccional o puntual, no las dos cosas");
        }
        if (towards != null) {
            towards = towards.clone();
            if (Vectors.normalize(towards) < 1.0E-9) {
                throw new IllegalArgumentException("La dirección de la luz no puede ser nula");
            }
        } else {
            point = point.clone();
        }
        occlusion = Math.clamp(occlusion, 0.0, 1.0);
        occlusionRadius = Math.clamp(occlusionRadius, 1, 8);
        shadows = Math.clamp(shadows, 0.0, 1.0);
        normalRadius = Math.clamp(normalRadius, 1, 4);
        target = target == null ? TextureContext.Filter.ALL : target;
        pivot = Double.isNaN(pivot) ? Double.NaN : Math.clamp(pivot, 0.0, 1.0);
    }

    public static ShadeField sun(double[] towards, double occlusion, int occlusionRadius, double shadows,
                                 TextureContext.Filter target) {
        return new ShadeField(towards, null, occlusion, occlusionRadius, shadows, 2, target, Double.NaN);
    }

    public static ShadeField spot(double[] point, double occlusion, int occlusionRadius, double shadows,
                                  TextureContext.Filter target) {
        return new ShadeField(null, point, occlusion, occlusionRadius, shadows, 2, target, Double.NaN);
    }

    /** La misma luz con la mediana en {@code pivot} ({@code NaN}: sin pivote). */
    public ShadeField withPivot(double pivot) {
        return new ShadeField(towards, point, occlusion, occlusionRadius, shadows, normalRadius, target, pivot);
    }

    @Override
    public int padding() {
        return Math.max(normalRadius + 2, 2 * occlusionRadius + 2);
    }

    @Override
    public double[] evaluate(TextureContext context) {
        double[] light = brightness(context);
        return pivot(levels(light), pivot);
    }

    /** Luz sin repartir: la directa (0–1) por la ambiental, antes de estirarla a la paleta. */
    double[] brightness(TextureContext context) {
        double[][] normals = context.normals(normalRadius);
        double[] direct = context.empty();
        double[] shadow = shadows > 0.0 ? context.empty() : null;
        double[] openness = occlusion > 0.0 ? context.empty() : null;
        double reach = 2.0 * occlusionRadius + 1.0;
        for (int index = 0; index < context.size(); index++) {
            context.checkCancelled();
            int x = context.x(index);
            int y = context.y(index);
            int z = context.z(index);
            if (!context.surface(index) || !target.test(x, y, z)) {
                continue;
            }
            double[] normal = normals[index];
            double[] toLight;
            double distance;
            if (towards != null) {
                toLight = towards;
                distance = Double.POSITIVE_INFINITY;
            } else {
                toLight = new double[]{point[0] - (x + 0.5), point[1] - (y + 0.5), point[2] - (z + 0.5)};
                distance = Vectors.normalize(toLight);
            }
            direct[index] = Math.max(0.0, Vectors.dot(normal, toLight));
            if (shadow != null) {
                shadow[index] = direct[index] > 0.0 && blocked(context, x, y, z, normal, toLight, distance) ? 1.0 : 0.0;
            }
            if (openness != null) {
                openness[index] = openness(context, x, y, z, normal, reach);
            }
        }
        if (shadow != null) {
            // Penumbra de un bloque: el borde de la sombra no corta en seco.
            shadow = context.smoothSurface(shadow, 0.6);
        }
        double reference = openness == null ? 1.0 : percentile(openness, 0.9);
        double[] values = context.empty();
        for (int index = 0; index < values.length; index++) {
            if (Double.isNaN(direct[index])) {
                continue;
            }
            double sun = direct[index] * (shadow == null ? 1.0 : 1.0 - shadows * shadow[index]);
            double light = AMBIENT + (1.0 - AMBIENT) * sun;
            if (openness != null && reference > 1.0E-9) {
                double open = Math.min(1.0, openness[index] / reference);
                light *= 1.0 - occlusion * (1.0 - open);
            }
            values[index] = light;
        }
        return values;
    }

    /**
     * Reparte la luz por la paleta: el 2 % más oscuro va al primer bloque y el 2 % más claro al último. Si la
     * variación es menor que {@link #MINIMUM_RANGE}, se estira solo hasta ese rango alrededor de la media, así una
     * pared lisa no se llena de saltos por diferencias mínimas.
     */
    static double[] levels(double[] values) {
        int count = 0;
        double sum = 0.0;
        for (double value : values) {
            if (!Double.isNaN(value)) {
                count++;
                sum += value;
            }
        }
        if (count == 0) {
            return values;
        }
        double low = percentile(values, 0.02);
        double high = percentile(values, 0.98);
        if (high - low < MINIMUM_RANGE) {
            double middle = sum / count;
            low = middle - MINIMUM_RANGE / 2.0;
            high = middle + MINIMUM_RANGE / 2.0;
        }
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            double value = values[index];
            result[index] = Double.isNaN(value) ? value : Math.clamp((value - low) / (high - low), 0.0, 1.0);
        }
        return result;
    }

    /**
     * Lleva la mediana a {@code pivot} estirando por separado lo que queda por debajo y por encima: con una rampa,
     * la luz más corriente da el material base y las sombras y los brillos se reparten el resto.
     */
    static double[] pivot(double[] values, double pivot) {
        if (Double.isNaN(pivot)) {
            return values;
        }
        double median = percentile(values, 0.5);
        if (median <= 1.0E-6 || median >= 1.0 - 1.0E-6) {
            return values;
        }
        double[] result = new double[values.length];
        for (int index = 0; index < values.length; index++) {
            double value = values[index];
            if (Double.isNaN(value)) {
                result[index] = value;
            } else if (value <= median) {
                result[index] = value / median * pivot;
            } else {
                result[index] = pivot + (value - median) / (1.0 - median) * (1.0 - pivot);
            }
        }
        return result;
    }

    static double percentile(double[] values, double fraction) {
        double[] sorted = Arrays.stream(values).filter(value -> !Double.isNaN(value)).sorted().toArray();
        if (sorted.length == 0) {
            return 0.0;
        }
        return sorted[(int) Math.round((sorted.length - 1) * fraction)];
    }

    /** Parte del hemisferio de la normal que no choca pronto con material (1 = cielo abierto). */
    private static double openness(TextureContext context, int bx, int by, int bz, double[] normal, double reach) {
        double cx = bx + 0.5 + normal[0] * 0.5;
        double cy = by + 0.5 + normal[1] * 0.5;
        double cz = bz + 0.5 + normal[2] * 0.5;
        double total = 0.0;
        double open = 0.0;
        for (double[] direction : SPHERE) {
            double weight = Vectors.dot(direction, normal);
            if (weight <= 0.05) {
                continue;
            }
            total += weight;
            double reached = 1.0;
            for (double step = 0.5; step <= reach; step += 0.5) {
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

    /** Rayo de sombra desde la cara hacia la luz, hasta llegar a ella o salir de la foto. */
    private static boolean blocked(TextureContext context, int bx, int by, int bz, double[] normal,
                                   double[] toLight, double distance) {
        double cx = bx + 0.5 + normal[0] * 0.6;
        double cy = by + 0.5 + normal[1] * 0.6;
        double cz = bz + 0.5 + normal[2] * 0.6;
        double limit = Math.min(distance, 512.0);
        for (double step = 0.0; step <= limit; step += 0.5) {
            int x = (int) Math.floor(cx + toLight[0] * step);
            int y = (int) Math.floor(cy + toLight[1] * step);
            int z = (int) Math.floor(cz + toLight[2] * step);
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
