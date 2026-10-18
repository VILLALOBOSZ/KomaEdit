package com.komainu.moxia.color;

import java.util.ArrayList;
import java.util.List;

/**
 * Qué tiene que cumplir un bloque para entrar en una búsqueda de color: las banderas de {@link BlockColor} que se
 * exigen. Siempre hace falta que el bloque tenga objeto, porque la búsqueda sirve para llevarlo a la barra rápida.
 *
 * <p>En las órdenes: {@code -c} cubo entero, {@code -s} sólido, {@code -o} opaco, {@code -u} la misma textura en todas
 * las caras. Los materiales con dibujo, luz o función (menas, terracota esmaltada, hornos, piedra luminosa…) quedan
 * fuera salvo con {@code -x}.</p>
 */
public record ColorFilter(int required) {
    public static final ColorFilter ANY = new ColorFilter(0);
    /** Solo materiales lisos: lo que se usa si no se pide nada. */
    public static final ColorFilter PLAIN = new ColorFilter(BlockColor.PLAIN);
    /** Lo que sirve para sombrear una superficie: cubos enteros, sólidos, opacos y lisos. */
    public static final ColorFilter BUILDING = new ColorFilter(BlockColor.CUBE | BlockColor.SOLID | BlockColor.OPAQUE
            | BlockColor.PLAIN);

    /** Filtro de las órdenes; los materiales con dibujo, luz o función solo entran con {@code anything}. */
    public static ColorFilter of(boolean cube, boolean solid, boolean opaque, boolean uniform, boolean anything) {
        return new ColorFilter((cube ? BlockColor.CUBE : 0) | (solid ? BlockColor.SOLID : 0)
                | (opaque ? BlockColor.OPAQUE : 0) | (uniform ? BlockColor.UNIFORM : 0)
                | (anything ? 0 : BlockColor.PLAIN));
    }

    public static ColorFilter of(boolean cube, boolean solid, boolean opaque, boolean uniform) {
        return of(cube, solid, opaque, uniform, false);
    }

    public boolean test(BlockColor block) {
        return block.has(required | BlockColor.ITEM);
    }

    public ColorFilter and(ColorFilter other) {
        return new ColorFilter(required | other.required);
    }

    /** Banderas de la orden que reproducen el filtro ({@code -c -o}), o vacío. */
    public String flags() {
        StringBuilder text = new StringBuilder();
        append(text, BlockColor.CUBE, "-c");
        append(text, BlockColor.SOLID, "-s");
        append(text, BlockColor.OPAQUE, "-o");
        append(text, BlockColor.UNIFORM, "-u");
        if ((required & BlockColor.PLAIN) == 0) {
            text.append(text.isEmpty() ? "-x" : " -x");
        }
        return text.toString();
    }

    /** «lisos, cubo entero, opaco», o «todos los bloques». */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if ((required & BlockColor.PLAIN) != 0) {
            parts.add("lisos");
        }
        if ((required & BlockColor.CUBE) != 0) {
            parts.add("cubo entero");
        }
        if ((required & BlockColor.SOLID) != 0) {
            parts.add("sólido");
        }
        if ((required & BlockColor.OPAQUE) != 0) {
            parts.add("opaco");
        }
        if ((required & BlockColor.UNIFORM) != 0) {
            parts.add("misma textura en todas las caras");
        }
        return parts.isEmpty() ? "todos los bloques" : String.join(", ", parts);
    }

    private void append(StringBuilder text, int flag, String letter) {
        if ((required & flag) != 0) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            text.append(letter);
        }
    }
}
