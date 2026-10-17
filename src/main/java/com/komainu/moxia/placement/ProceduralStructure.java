package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

/**
 * Estructuras de expresión de ezEdits en el cubo [-1, 1]³:
 *
 * <ul>
 *     <li>{@code Shape}: la forma decide qué se coloca y un patrón de WorldEdit la pinta.</li>
 *     <li>{@code Expression}: una fórmula decide forma y material; por debajo de 0 es aire, entre 0 y 1 elige el
 *     bloque de la paleta y de 1 en adelante el último.</li>
 *     <li>{@code TexturedShape}: una forma decide qué se coloca y otra (o una fórmula) reparte la paleta.</li>
 *     <li>{@code Icosphere}: roca deformada, pintada con un patrón.</li>
 * </ul>
 *
 * <p>El origen es el centro de la caja ({@code Origin:CENTER}, como en ezEdits) o el centro de su cara de abajo
 * ({@code Origin:BOTTOM}), para apoyar la forma en el suelo.</p>
 */
public final class ProceduralStructure implements Structure {
    public static final Vec3 DEFAULT_SIZE = new Vec3(20, 20, 20);
    /** Los bordes con un margen mínimo: los pasos acumulados del voxelizador no deben decidir un bloque entero. */
    private static final double LOW = -1.0 - 1.0E-9;
    private static final double HIGH = 1.0 - 1.0E-9;

    private final Kind kind;
    private final ProceduralShapes.Spec shape;
    private final ProceduralShapes.Spec texture;
    private final int paletteSize;
    private final IcosphereSettings icosphere;
    private final boolean bottom;
    private final String name;

    private ProceduralStructure(
            Kind kind,
            ProceduralShapes.Spec shape,
            ProceduralShapes.Spec texture,
            int paletteSize,
            IcosphereSettings icosphere,
            boolean bottom,
            String name
    ) {
        this.kind = kind;
        this.shape = shape;
        this.texture = texture;
        this.paletteSize = paletteSize;
        this.icosphere = icosphere;
        this.bottom = bottom;
        this.name = name;
    }

    public static ProceduralStructure shape(ProceduralShapes.Spec shape, boolean bottom, String name) {
        return new ProceduralStructure(Kind.SHAPE, shape, null, 1, null, bottom, name);
    }

    public static ProceduralStructure expression(ProceduralShapes.Spec expression, int paletteSize, boolean bottom,
                                                 String name) {
        return new ProceduralStructure(Kind.EXPRESSION, expression, null, requirePalette(paletteSize), null, bottom,
                name);
    }

    public static ProceduralStructure textured(ProceduralShapes.Spec shape, ProceduralShapes.Spec texture,
                                               int paletteSize, boolean bottom, String name) {
        return new ProceduralStructure(Kind.TEXTURED, shape, texture, requirePalette(paletteSize), null, bottom, name);
    }

    public static ProceduralStructure icosphere(IcosphereSettings settings, boolean bottom, String name) {
        return new ProceduralStructure(Kind.ICOSPHERE, null, null, 1, settings, bottom, name);
    }

    private static int requirePalette(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("La paleta está vacía");
        }
        return size;
    }

    public Kind kind() {
        return kind;
    }

    public int paletteSize() {
        return paletteSize;
    }

    @Override
    public Vec3 min() {
        return new Vec3(-1, bottom ? 0 : -1, -1);
    }

    @Override
    public Vec3 max() {
        return new Vec3(1, bottom ? 2 : 1, 1);
    }

    @Override
    public Vec3 naturalSize() {
        return DEFAULT_SIZE;
    }

    @Override
    public Vec3 blocksPerUnit() {
        return DEFAULT_SIZE.multiply(0.5);
    }

    @Override
    public Sampler sampler(long seed) {
        double shift = bottom ? -1.0 : 0.0;
        switch (kind) {
            case SHAPE -> {
                ProceduralShapes.Implicit implicit = shape.create(seed);
                return (x, y, z) -> inBox(x, y + shift, z)
                        ? (implicit.inside(x, y + shift, z) ? 0 : AIR)
                        : EMPTY;
            }
            case ICOSPHERE -> {
                Icosphere rock = new Icosphere(seed, icosphere.randomness(), icosphere.subdivisions(),
                        icosphere.platform());
                return (x, y, z) -> inBox(x, y + shift, z)
                        ? (rock.inside(x, y + shift, z) ? 0 : AIR)
                        : EMPTY;
            }
            case EXPRESSION -> {
                ProceduralShapes.Implicit implicit = shape.create(seed);
                return (x, y, z) -> inBox(x, y + shift, z) ? index(implicit.value(x, y + shift, z)) : EMPTY;
            }
            default -> {
                ProceduralShapes.Implicit implicit = shape.create(seed);
                ProceduralShapes.Implicit paint = texture.create(seed);
                return (x, y, z) -> {
                    double localY = y + shift;
                    if (!inBox(x, localY, z)) {
                        return EMPTY;
                    }
                    if (!implicit.inside(x, localY, z)) {
                        return AIR;
                    }
                    int index = index(paint.value(x, localY, z));
                    return index == AIR ? 0 : index;
                };
            }
        }
    }

    /** Valor a bloque de la paleta: (0, 1) se reparte entre los bloques, 1 o más es el último. */
    int index(double value) {
        if (!(value > 0.0)) {
            return AIR;
        }
        if (value >= 1.0) {
            return paletteSize - 1;
        }
        return Math.min(paletteSize - 1, (int) (value * paletteSize));
    }

    /** Semiabierta, [-1, 1): así una caja de 20 bloques da exactamente 20 y no 21. */
    private static boolean inBox(double x, double y, double z) {
        return x >= LOW && x < HIGH && y >= LOW && y < HIGH && z >= LOW && z < HIGH;
    }

    @Override
    public boolean usesPattern() {
        return kind == Kind.SHAPE || kind == Kind.ICOSPHERE;
    }

    @Override
    public String describe() {
        return name;
    }

    public enum Kind {
        SHAPE, EXPRESSION, TEXTURED, ICOSPHERE
    }

    public record IcosphereSettings(double randomness, int subdivisions, double platform) {
    }
}
