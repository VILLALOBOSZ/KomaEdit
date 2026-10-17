package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

import java.util.List;
import java.util.SplittableRandom;

/**
 * La cadena de cada colocación, en el orden de ezEdits: dimensiones ({@code -s}), escala aleatoria ({@code -o}),
 * orientación ({@code -k}/{@code -c}), simetrías aleatorias ({@code -f}), giros de 90° aleatorios ({@code -r}) y
 * alineación (primaria y secundaria). Después se suman los desplazamientos de la estructura ({@code RO}, {@code WO}
 * y {@code OJ}).
 */
public final class PlacementBuilder {
    private final PlacementSettings settings;
    private final List<Structure> structures;
    private final Offsets offsets;

    /**
     * @param structures una o varias (schematics que casan con el patrón de nombre); cada colocación elige una
     */
    public PlacementBuilder(PlacementSettings settings, List<Structure> structures, Offsets offsets) {
        if (structures.isEmpty()) {
            throw new IllegalArgumentException("No hay ninguna estructura que colocar");
        }
        this.settings = settings;
        this.structures = List.copyOf(structures);
        this.offsets = offsets;
    }

    /** Desplazamientos comunes: {@code RO} sigue la orientación de la colocación, {@code WO} los ejes del mundo. */
    public record Offsets(Vec3 relative, Vec3 world, double jitter) {
        public static final Offsets NONE = new Offsets(new Vec3(0, 0, 0), new Vec3(0, 0, 0), 0.0);
    }

    public PlacementSettings settings() {
        return settings;
    }

    /**
     * @param anchor      centro del bloque donde cae el origen de la estructura
     * @param progressive factor de escala progresiva ({@code -q} en array; 1 en lo demás)
     */
    public Placement build(Vec3 anchor, Alignment.Context context, double progressive) {
        SplittableRandom random = context.random();
        Structure structure = structures.size() == 1 ? structures.getFirst()
                : structures.get(random.nextInt(structures.size()));
        Vec3 natural = structure.naturalSize();
        Vec3 target = settings.dimensions() == null ? natural : settings.dimensions();
        double randomScale = settings.minimumScale() == settings.maximumScale()
                ? settings.minimumScale()
                : settings.minimumScale() + random.nextDouble() * (settings.maximumScale() - settings.minimumScale());
        double factor = randomScale * progressive;
        Vec3 perUnit = structure.blocksPerUnit();
        Vec3 scale = new Vec3(
                perUnit.x() * target.x() / natural.x() * factor,
                perUnit.y() * target.y() / natural.y() * factor,
                perUnit.z() * target.z() / natural.z() * factor
        );

        Mat3 input = Mat3.rotation(Alignment.UP, structure.inputRotation());
        Mat3 orientation = Mat3.rotation(settings.orientationAxis().evaluate(context.view()),
                settings.orientationAngle());
        boolean[] flips = settings.flips();
        Mat3 mirror = Mat3.diagonal(
                flips[0] && random.nextBoolean() ? -1 : 1,
                flips[1] && random.nextBoolean() ? -1 : 1,
                flips[2] && random.nextBoolean() ? -1 : 1
        );
        Mat3 turns = settings.rotationAxis() < 0 ? Mat3.IDENTITY
                : Mat3.quarterTurns(settings.rotationAxis(), random.nextInt(4));
        Vec3 primary = settings.primary().direction(context);
        Vec3 secondary = settings.secondary().direction(context);
        Mat3 alignment = AlignmentFrame.frame(primary, secondary, settings.snap(), settings.perturb());
        Mat3 rotation = alignment.multiply(turns).multiply(mirror).multiply(orientation).multiply(input);

        double strength = offsets.jitter() > 0.0 ? 1.0 - offsets.jitter() * random.nextDouble() : 1.0;
        Vec3 shifted = anchor
                .add(rotation.apply(offsets.relative()).multiply(strength))
                .add(offsets.world().multiply(strength));
        return new Placement(structure, shifted, rotation, scale, random.nextLong());
    }
}
