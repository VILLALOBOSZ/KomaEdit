package com.komainu.moxia.blueprint;

import com.komainu.moxia.brush.ez.BrushOptions;

import java.util.Locale;
import java.util.Set;

/**
 * Ajustes del pincel «estampar» ({@code /komaedit pincel stamp <planos> [radio] [banderas]}).
 *
 * <ul>
 *     <li>{@code <planos>}: {@code roca_1,roca_2:3,#arbustos}; nombres o etiquetas, con peso opcional.</li>
 *     <li>{@code [radio]}: 0 (de serie) pone un plano donde miras; con más, reparte varios en ese círculo.</li>
 *     <li>{@code -n %}: probabilidad de cada hueco (100 de serie).</li>
 *     <li>{@code -d bloques}: separación mínima entre planos; de serie, la que evita que se toquen.</li>
 *     <li>{@code -c n}: planos por clic como mucho (16 de serie).</li>
 *     <li>{@code -r azar|0|90|180|270|mirada}: giro (al azar de serie).</li>
 *     <li>{@code -f xz|x|z|no}: volteos al azar permitidos ({@code xz} de serie).</li>
 *     <li>{@code -g apoyar|centro|no}: ajuste al suelo. {@code apoyar} lo deja caer hasta que toca;
 *     {@code centro} lo pone sobre el suelo de su centro; {@code no} lo deja a la altura del clic.</li>
 *     <li>{@code -y n}: subir o hundir (negativo) después de apoyarlo.</li>
 *     <li>{@code -m máscara}: solo sobre ese suelo ({@code grass_block,moss_block}…).</li>
 *     <li>{@code -a}: coloca también el aire del plano; {@code -p}: vista previa.</li>
 * </ul>
 */
public record StampSettings(
        String source,
        int radius,
        double probability,
        double spacing,
        int maxStamps,
        Rotation rotation,
        int fixedTurns,
        boolean flipX,
        boolean flipZ,
        Gravity gravity,
        int offsetY,
        String mask,
        boolean placeAir,
        boolean preview
) {
    public static final int MAX_RADIUS = 32;
    public static final int MAX_STAMPS = 64;
    public static final Set<Character> SWITCHES = Set.of('a', 'p');
    public static final Set<Character> VALUED = Set.of('n', 'd', 'c', 'r', 'f', 'g', 'y', 'm');

    public enum Rotation {
        RANDOM, FIXED, LOOK
    }

    public enum Gravity {
        /** Cae hasta tocar el suelo con alguna columna: no se mete en el terreno. */
        REST,
        /** El fondo del plano sobre el suelo que hay bajo su centro. */
        CENTER,
        /** A la altura del bloque que se mira, sin buscar el suelo. */
        NONE
    }

    /** Separación automática: la que deja libre la huella de cada plano. */
    public boolean autoSpacing() {
        return Double.isNaN(spacing);
    }

    public static StampSettings parse(String input) {
        BrushOptions options = BrushOptions.parse(input, SWITCHES, VALUED);
        String source = options.required(0, "los planos (nombres o #etiqueta), por ejemplo #rocas");
        BlueprintSelection.parse(source);
        int radius = options.integer(1, 0, 0, MAX_RADIUS, "El radio");
        if (options.count() > 2) {
            throw new IllegalArgumentException("Sobra «" + options.positional(2) + "»: tras los planos solo va el radio");
        }
        double probability = options.flagDecimal('n', 100, 0, 100, "La probabilidad (-n)") / 100.0;
        double spacing = options.has('d') ? options.flagDecimal('d', 0, 0, 64, "La separación (-d)") : Double.NaN;
        int maxStamps = (int) Math.round(options.flagDecimal('c', 16, 1, MAX_STAMPS, "El máximo por clic (-c)"));
        Rotation rotation = Rotation.RANDOM;
        int fixedTurns = 0;
        if (options.has('r')) {
            String value = options.flag('r').toLowerCase(Locale.ROOT);
            switch (value) {
                case "azar", "random", "aleatorio" -> rotation = Rotation.RANDOM;
                case "mirada", "look", "aim" -> rotation = Rotation.LOOK;
                case "0", "90", "180", "270", "-90" -> {
                    rotation = Rotation.FIXED;
                    fixedTurns = Math.floorMod(Integer.parseInt(value) / 90, 4);
                }
                default -> throw new IllegalArgumentException("-r espera azar, mirada, 0, 90, 180 o 270");
            }
        }
        boolean flipX = true;
        boolean flipZ = true;
        if (options.has('f')) {
            String value = options.flag('f').toLowerCase(Locale.ROOT);
            switch (value) {
                case "no", "none", "ninguno" -> {
                    flipX = false;
                    flipZ = false;
                }
                case "x" -> flipZ = false;
                case "z" -> flipX = false;
                case "xz", "zx" -> {
                }
                default -> throw new IllegalArgumentException("-f espera xz, x, z o no");
            }
        }
        Gravity gravity = Gravity.REST;
        if (options.has('g')) {
            gravity = switch (options.flag('g').toLowerCase(Locale.ROOT)) {
                case "apoyar", "rest", "caer", "si" -> Gravity.REST;
                case "centro", "center" -> Gravity.CENTER;
                case "no", "none", "off" -> Gravity.NONE;
                default -> throw new IllegalArgumentException("-g espera apoyar, centro o no");
            };
        }
        int offsetY = (int) Math.round(options.flagDecimal('y', 0, -16, 16, "El desplazamiento vertical (-y)"));
        return new StampSettings(source, radius, probability, spacing, maxStamps, rotation, fixedTurns, flipX, flipZ,
                gravity, offsetY, options.has('m') ? options.flag('m') : null, options.has('a'), options.has('p'));
    }

    /** Para el chat: «#rocas · radio 8 · 60 % · al azar». */
    public String describe() {
        StringBuilder text = new StringBuilder(source);
        if (radius > 0) {
            text.append(" · radio ").append(radius);
        }
        if (probability < 1.0) {
            text.append(" · ").append(Math.round(probability * 100)).append(" %");
        }
        if (!autoSpacing()) {
            text.append(" · separación ").append(spacing == Math.rint(spacing) ? Long.toString((long) spacing)
                    : Double.toString(spacing));
        }
        return text.toString();
    }
}
