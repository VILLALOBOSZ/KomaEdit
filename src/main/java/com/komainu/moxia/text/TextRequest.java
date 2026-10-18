package com.komainu.moxia.text;

import com.komainu.moxia.deform.DeformArguments;

import java.util.Locale;
import java.util.Set;

/**
 * Lo que pide {@code /komaedit text "<texto>" [material] [banderas]}, ya validado y sin Minecraft.
 *
 * <ul>
 *     <li>{@code -f} fuente, {@code -s} tamaño (altura de una mayúscula en bloques), {@code -d} grosor.</li>
 *     <li>{@code -b} ancho del borde y {@code -o} su material.</li>
 *     <li>{@code -g v|h}: el material es una paleta en degradado (vertical u horizontal).</li>
 *     <li>{@code -w} losas y escaleras, {@code -m} máscara, {@code -k} espacio entre letras, {@code -l}
 *     interlineado y {@code -a} alineación de las líneas.</li>
 *     <li>{@code -v} de pie y {@code -h} tumbado; sin ellas, según la mirada.</li>
 * </ul>
 */
public record TextRequest(
        String text,
        String material,
        String font,
        double size,
        int depth,
        double border,
        String borderMaterial,
        TextModel.Gradient gradient,
        String smoothing,
        String mask,
        double tracking,
        double lineSpacing,
        TextRaster.Align align,
        Orientation orientation
) {
    public static final String DEFAULT_MATERIAL = "stone";
    public static final String USAGE = "text \"<texto>\" [material] [-f fuente] [-s tamaño] [-d grosor] [-b borde]"
            + " [-o material del borde] [-g v|h] [-w perfil] [-m máscara] [-k espaciado] [-l interlineado]"
            + " [-a izquierda|centro|derecha] [-v|-h]";
    private static final Set<String> VALUED = Set.of("f", "s", "d", "b", "o", "g", "w", "m", "k", "l", "a");

    /** De pie, tumbado o según la mirada. */
    public enum Orientation {
        LOOK, STANDING, LYING
    }

    /** Si el material es una paleta para degradado y no un patrón de WorldEdit. */
    public boolean palette() {
        return gradient != null;
    }

    public static TextRequest parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Falta el texto. Uso: " + USAGE);
        }
        String trimmed = input.strip();
        String text;
        String rest;
        if (trimmed.startsWith("\"")) {
            StringBuilder builder = new StringBuilder();
            int index = 1;
            boolean closed = false;
            while (index < trimmed.length()) {
                char character = trimmed.charAt(index);
                if (character == '\\' && index + 1 < trimmed.length() && trimmed.charAt(index + 1) == '"') {
                    builder.append('"');
                    index += 2;
                    continue;
                }
                if (character == '"') {
                    closed = true;
                    index++;
                    break;
                }
                builder.append(character);
                index++;
            }
            if (!closed) {
                throw new IllegalArgumentException("Falta la comilla de cierre del texto");
            }
            text = builder.toString();
            rest = trimmed.substring(index);
        } else {
            int space = trimmed.indexOf(' ');
            text = space < 0 ? trimmed : trimmed.substring(0, space);
            rest = space < 0 ? "" : trimmed.substring(space + 1);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("El texto está vacío");
        }
        DeformArguments args = DeformArguments.parse(rest, VALUED, "vh");
        args.expectAtMost(1, USAGE);
        String material = args.text(0) == null ? DEFAULT_MATERIAL : args.text(0);
        String borderMaterial = args.value("o");
        double border = args.value("b", borderMaterial == null ? 0.0 : 1.0, 0.0, 16.0, "El borde");
        if (borderMaterial != null && border <= 0.0) {
            throw new IllegalArgumentException("Con -o el borde necesita un ancho mayor que 0 (-b)");
        }
        TextModel.Gradient gradient = null;
        String gradientText = args.value("g");
        if (gradientText != null) {
            gradient = switch (gradientText.toLowerCase(Locale.ROOT)) {
                case "v", "y", "vertical", "arriba" -> TextModel.Gradient.VERTICAL;
                case "h", "x", "horizontal", "derecha" -> TextModel.Gradient.HORIZONTAL;
                default -> throw new IllegalArgumentException("El degradado es v (vertical) o h (horizontal)");
            };
        } else if (material.startsWith("#") || material.contains(";")) {
            gradient = TextModel.Gradient.VERTICAL;
        }
        if (args.has('v') && args.has('h')) {
            throw new IllegalArgumentException("Usa -v (de pie) o -h (tumbado), no las dos");
        }
        Orientation orientation = args.has('v') ? Orientation.STANDING
                : args.has('h') ? Orientation.LYING : Orientation.LOOK;
        return new TextRequest(
                text,
                material,
                args.value("f") == null ? TextFonts.DEFAULT : args.value("f"),
                args.value("s", 8.0, 1.0, 256.0, "El tamaño"),
                (int) Math.round(args.value("d", 1.0, 1.0, 64.0, "El grosor")),
                border,
                borderMaterial,
                gradient,
                args.value("w"),
                args.value("m"),
                args.value("k", 0.0, -8.0, 32.0, "El espaciado"),
                args.value("l", 1.0, 0.5, 4.0, "El interlineado"),
                align(args.value("a")),
                orientation
        );
    }

    public TextRaster.Settings rasterSettings() {
        return new TextRaster.Settings(size, tracking, lineSpacing, align);
    }

    private static TextRaster.Align align(String input) {
        if (input == null) {
            return TextRaster.Align.CENTER;
        }
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "l", "left", "izquierda", "izq" -> TextRaster.Align.LEFT;
            case "c", "center", "centro", "centrado" -> TextRaster.Align.CENTER;
            case "r", "right", "derecha", "der" -> TextRaster.Align.RIGHT;
            default -> throw new IllegalArgumentException("La alineación es izquierda, centro o derecha");
        };
    }
}
