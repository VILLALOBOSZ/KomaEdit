package com.komainu.moxia.nature;

import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

/**
 * Ajustes de un árbol: {@code [especie|#preset] [altura] [banderas]}. Los factores valen 1 por defecto (lo que dicta la
 * especie) y los materiales {@code null} usan los de la especie.
 *
 * @param thickness  grosor del tronco y las ramas ({@code -g})
 * @param branches   cantidad de ramas ({@code -b})
 * @param density    densidad de la copa ({@code -d}); 0 la deja sin hojas
 * @param width      ancho de la copa ({@code -w})
 * @param lean       inclinación del tronco en grados ({@code -i grados[,rumbo]})
 * @param azimuth    rumbo de la inclinación en grados (0 = este, 90 = sur), o {@code NaN} para uno al azar
 * @param curvature  cuánto serpentean tronco y ramas ({@code -c})
 * @param variation  variación de cada clic del pincel o de cada punto marcado ({@code -x}), o {@code NaN} para la de serie
 */
public record TreeSettings(
        TreeSpecies species,
        int height,
        double thickness,
        double branches,
        double density,
        double width,
        double lean,
        double azimuth,
        double curvature,
        OptionalLong seed,
        String trunk,
        String leaves,
        String roots,
        String mask,
        double variation,
        boolean preview,
        String preset
) {
    public static final int MIN_HEIGHT = 4;
    public static final int MAX_HEIGHT = 96;
    static final Set<Character> SWITCHES = Set.of('p');
    static final Set<Character> VALUED = Set.of('g', 'b', 'd', 'w', 'i', 'c', 's', 't', 'h', 'r', 'm', 'x');

    public static TreeSettings parse(String input) {
        return parse(input, name -> null);
    }

    /** @param presets texto guardado de cada preset del jugador ({@code null} si no existe) */
    public static TreeSettings parse(String input, Function<String, String> presets) {
        NatureArgs args = NatureArgs.parse(input, SWITCHES, VALUED, presets,
                name -> TreeSpecies.find(name) != null);
        String word = args.word("una especie");
        TreeSpecies species = word == null ? TreeSpecies.OAK : TreeSpecies.byName(word);
        String heightText = args.number("una altura");
        int height = heightText == null ? species.defaultHeight()
                : (int) Math.round(NatureArgs.number(heightText, MIN_HEIGHT, MAX_HEIGHT, "La altura"));
        double lean = species.form().defaultLean;
        double azimuth = Double.NaN;
        String leanText = args.flag('i');
        if (leanText != null) {
            String[] parts = leanText.split("[,;/@]");
            if (parts.length > 2) {
                throw new IllegalArgumentException("La inclinación (-i) es grados o grados,rumbo: " + leanText);
            }
            lean = NatureArgs.number(parts[0], 0.0, 75.0, "La inclinación (-i)");
            if (parts.length == 2) {
                azimuth = Math.floorMod((long) Math.round(NatureArgs.number(parts[1], -360.0, 720.0,
                        "El rumbo de la inclinación")), 360L);
            }
        }
        return new TreeSettings(
                species,
                height,
                args.decimal('g', 1.0, 0.25, 4.0, "El grosor (-g)"),
                args.decimal('b', 1.0, 0.0, 3.0, "Las ramas (-b)"),
                args.decimal('d', 1.0, 0.0, 2.0, "La densidad de copa (-d)"),
                args.decimal('w', 1.0, 0.3, 3.0, "El ancho de copa (-w)"),
                lean,
                azimuth,
                args.decimal('c', 1.0, 0.0, 4.0, "La curvatura (-c)"),
                args.has('s') ? OptionalLong.of(NatureArgs.seed(args.flag('s'))) : OptionalLong.empty(),
                material(args.flag('t')),
                material(args.flag('h')),
                material(args.flag('r')),
                material(args.flag('m')),
                args.decimal('x', Double.NaN, 0.0, 1.0, "La variación (-x)"),
                args.has('p'),
                args.presetName()
        );
    }

    private static String material(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Material del tronco: el de {@code -t} o el de la especie. */
    public String trunkMaterial() {
        return trunk == null ? species.trunk() : trunk;
    }

    /** Hojas: las de {@code -h}, las de la especie o {@code null} si no lleva. */
    public String leafMaterial() {
        String value = leaves == null ? species.leaves() : leaves;
        return NatureArgs.disabled(value) || density <= 0.0 ? null : value;
    }

    /** Raíces: las de {@code -r}, el tronco o {@code null} si no lleva. */
    public String rootMaterial() {
        String value = roots == null ? trunkMaterial() : roots;
        return NatureArgs.disabled(value) ? null : value;
    }

    public TreeSettings withSeed(long value) {
        return new TreeSettings(species, height, thickness, branches, density, width, lean, azimuth, curvature,
                OptionalLong.of(value), trunk, leaves, roots, mask, variation, preview, preset);
    }

    /** Variante de un clic del pincel: otra altura, grosor e inclinación dentro de {@code amount}. */
    public TreeSettings vary(double amount, java.util.random.RandomGenerator random) {
        if (amount <= 0.0) {
            return this;
        }
        int varied = (int) Math.round(height * (1.0 + (random.nextDouble() * 2.0 - 1.0) * amount));
        double thick = thickness * (1.0 + (random.nextDouble() * 2.0 - 1.0) * amount * 0.5);
        double tilt = Math.clamp(lean + random.nextDouble() * amount * 14.0, 0.0, 75.0);
        double crown = width * (1.0 + (random.nextDouble() * 2.0 - 1.0) * amount * 0.4);
        return new TreeSettings(species, Math.clamp(varied, MIN_HEIGHT, MAX_HEIGHT), Math.clamp(thick, 0.25, 4.0),
                branches, density, Math.clamp(crown, 0.3, 3.0), tilt, Double.NaN, curvature, seed, trunk, leaves,
                roots, mask, variation, preview, preset);
    }

    /** Resumen corto para el chat. */
    public String describe() {
        StringBuilder text = new StringBuilder(species.displayName()).append(" de ").append(height).append(" bloques");
        if (thickness != 1.0) {
            text.append(" · grosor ").append(NatureArgs.format(thickness));
        }
        if (lean > 0.5) {
            text.append(" · inclinado ").append(Math.round(lean)).append("°");
        }
        if (preset != null) {
            text.append(" · preset ").append(preset);
        }
        return text.toString();
    }
}
