package com.komainu.moxia.nature;

import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Ajustes de una roca: {@code [tipo|#preset] [tamaño] [banderas]}.
 *
 * @param size       radio en bloques
 * @param aspect     alto respecto al ancho ({@code -a})
 * @param elongation planta alargada ({@code -e}, 1 = redonda)
 * @param roughness  rugosidad del ruido ({@code -r}, 0 a 1)
 * @param cracks     grietas y estratos ({@code -g}, 0 a 1)
 * @param facets     caras planas talladas ({@code -c})
 * @param sink       parte del alto enterrada ({@code -h})
 * @param lean       inclinación en grados ({@code -i})
 * @param palette    bloques de oscuro a claro ({@code -b}), o {@code null} para los de serie
 * @param moss       bloques del musgo, o {@code null} sin musgo ({@code -u})
 * @param mossAmount cuánto musgo, de 0 a 1
 */
public record RockSettings(
        RockType type,
        double size,
        double aspect,
        double elongation,
        double roughness,
        double cracks,
        int facets,
        double sink,
        double lean,
        OptionalLong seed,
        String palette,
        String moss,
        double mossAmount,
        String mask,
        double variation,
        boolean preview,
        String preset
) {
    public static final String DEFAULT_PALETTE = "deepslate,tuff,cobblestone,stone,andesite";
    public static final String DEFAULT_MOSS = "moss_block";
    public static final double MAX_SIZE = 32.0;
    static final Set<Character> SWITCHES = Set.of('p');
    static final Set<Character> VALUED = Set.of('t', 'b', 'u', 'a', 'e', 'r', 'g', 'c', 'h', 'i', 's', 'm', 'x');

    public static RockSettings parse(String input) {
        return parse(input, name -> null);
    }

    public static RockSettings parse(String input, Function<String, String> presets) {
        NatureArgs args = NatureArgs.parse(input, SWITCHES, VALUED, presets, name -> RockType.find(name) != null);
        String word = args.word("un tipo de roca");
        String typeFlag = args.flag('t');
        if (word != null && typeFlag != null) {
            throw new IllegalArgumentException("Indica el tipo una vez: «" + word + "» o -t " + typeFlag);
        }
        RockType type = word != null ? RockType.byName(word) : typeFlag != null ? RockType.byName(typeFlag)
                : RockType.BOULDER;
        String sizeText = args.number("un tamaño");
        double size = sizeText == null ? type.size() : NatureArgs.number(sizeText, 1.0, MAX_SIZE, "El tamaño");
        String mossText = args.flag('u');
        String moss = null;
        double mossAmount = 0.0;
        if (mossText != null && !NatureArgs.disabled(mossText)) {
            if (com.komainu.moxia.brush.ez.BrushOptions.isNumber(mossText)) {
                mossAmount = NatureArgs.number(mossText, 0.0, 1.0, "El musgo (-u)");
                moss = mossAmount > 0.0 ? DEFAULT_MOSS : null;
            } else {
                moss = mossText.strip();
                mossAmount = 0.5;
            }
        }
        return new RockSettings(
                type,
                size,
                args.decimal('a', type.aspect(), 0.2, 6.0, "El aspecto (-a)"),
                args.decimal('e', type.elongation(), 1.0, 4.0, "El alargado (-e)"),
                args.decimal('r', type.roughness(), 0.0, 1.0, "La rugosidad (-r)"),
                args.decimal('g', type.cracks(), 0.0, 1.0, "Las grietas (-g)"),
                (int) Math.round(args.decimal('c', type.facets(), 0.0, 32.0, "Las caras (-c)")),
                args.decimal('h', type.sink(), 0.0, 0.9, "El hundido (-h)"),
                args.decimal('i', 0.0, 0.0, 60.0, "La inclinación (-i)"),
                args.has('s') ? OptionalLong.of(NatureArgs.seed(args.flag('s'))) : OptionalLong.empty(),
                blank(args.flag('b')),
                moss,
                mossAmount,
                blank(args.flag('m')),
                args.decimal('x', Double.NaN, 0.0, 1.0, "La variación (-x)"),
                args.has('p'),
                args.presetName()
        );
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public String paletteMaterial() {
        return palette == null ? DEFAULT_PALETTE : palette;
    }

    public RockSettings withSeed(long value) {
        return new RockSettings(type, size, aspect, elongation, roughness, cracks, facets, sink, lean,
                OptionalLong.of(value), palette, moss, mossAmount, mask, variation, preview, preset);
    }

    /** Variante de un clic del pincel: otro tamaño, aspecto, alargado e inclinación dentro de {@code amount}. */
    public RockSettings vary(double amount, RandomGenerator random) {
        if (amount <= 0.0) {
            return this;
        }
        double varied = Math.clamp(size * (1.0 + (random.nextDouble() * 2.0 - 1.0) * amount), 1.0, MAX_SIZE);
        double tall = Math.clamp(aspect * (1.0 + (random.nextDouble() * 2.0 - 1.0) * amount * 0.5), 0.2, 6.0);
        double longer = Math.clamp(elongation * (1.0 + (random.nextDouble() * 2.0 - 1.0) * amount * 0.5), 1.0, 4.0);
        double tilt = Math.clamp(lean + random.nextDouble() * amount * 12.0, 0.0, 60.0);
        return new RockSettings(type, varied, tall, longer, roughness, cracks, facets, sink, tilt, seed, palette,
                moss, mossAmount, mask, variation, preview, preset);
    }

    public String describe() {
        StringBuilder text = new StringBuilder(type.displayName()).append(" de radio ")
                .append(NatureArgs.format(Math.round(size * 10.0) / 10.0));
        if (moss != null) {
            text.append(" · con musgo");
        }
        if (preset != null) {
            text.append(" · preset ").append(preset);
        }
        return text.toString();
    }
}
