package com.komainu.moxia.color;

import java.util.ArrayList;
import java.util.List;

/**
 * Un bloque con su color medio y lo que se sabe de su forma.
 *
 * @param id       identificador con espacio de nombres ({@code minecraft:stone})
 * @param rgb      color {@code 0xRRGGBB}: la media de su textura o, si no se midió, su color de mapa
 * @param l        luminosidad OKLab (0 negro, 1 blanco)
 * @param a        eje verde–rojo de OKLab
 * @param b        eje azul–amarillo de OKLab
 * @param flags    banderas {@link #CUBE}, {@link #SOLID}, {@link #OPAQUE}, {@link #UNIFORM}, {@link #ITEM} y
 *                 {@link #PLAIN}
 * @param measured el color sale de la textura (si no, del color de mapa, mucho más aproximado)
 */
public record BlockColor(String id, int rgb, double l, double a, double b, int flags, boolean measured) {
    /** Ocupa el bloque entero. */
    public static final int CUBE = 1;
    /** Tiene colisión (no es una flor, una antorcha, un cartel…). */
    public static final int SOLID = 2;
    /** No deja ver a través (no es cristal, hojas, hielo…). */
    public static final int OPAQUE = 4;
    /** Cubo con la misma textura en las seis caras (no troncos, arenisca, césped…). */
    public static final int UNIFORM = 8;
    /** Tiene objeto: se puede llevar en la barra rápida. */
    public static final int ITEM = 16;
    /**
     * Material liso: no es una mena, una terracota esmaltada, una máquina ni un bloque de función, no tiene una
     * textura con mucho dibujo y no da luz. Es lo que conviene en un degradado o un sombreado.
     */
    public static final int PLAIN = 32;

    public static BlockColor of(String id, int rgb, int flags, boolean measured) {
        double[] lab = OkLab.of(rgb);
        return new BlockColor(id, rgb & 0xFFFFFF, lab[0], lab[1], lab[2], flags, measured);
    }

    public double[] lab() {
        return new double[]{l, a, b};
    }

    public boolean has(int flag) {
        return (flags & flag) == flag;
    }

    public double chroma() {
        return Math.hypot(a, b);
    }

    public String hex() {
        return OkLab.hex(rgb);
    }

    /** Sin {@code minecraft:}, como se escribe en una paleta. */
    public String shortName() {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    /** «cubo entero, sólido, opaco…» para el texto emergente del chat. */
    public String describeFlags() {
        List<String> parts = new ArrayList<>();
        if (has(CUBE)) {
            parts.add("cubo entero");
        }
        if (has(SOLID)) {
            parts.add("sólido");
        }
        if (has(OPAQUE)) {
            parts.add("opaco");
        }
        if (has(UNIFORM)) {
            parts.add("misma textura en todas las caras");
        }
        if (!has(PLAIN)) {
            parts.add("con dibujo, luz o función");
        }
        parts.add(measured ? "color de su textura" : "color de mapa (aproximado)");
        return String.join(", ", parts);
    }
}
