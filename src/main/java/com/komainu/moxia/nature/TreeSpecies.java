package com.komainu.moxia.nature;

import java.util.List;
import java.util.Locale;

/**
 * Especies de serie de {@code /komaedit tree}. Cada una fija su arquitectura (cómo se ramifica), las proporciones
 * de tronco y copa en función de la altura, sus raíces y los materiales por defecto. Todo lo que aquí es un número se
 * puede ajustar después con las banderas del comando.
 */
public enum TreeSpecies {
    OAK("oak", "Roble", 18, "oak_log", "oak_leaves", new Form(Architecture.BROADLEAF)
            .trunk(0.09, 1.2, 0.8, 0.6).crown(Envelope.DOME, 0.24, 0.42, 0.6, 0.48, 0.42)
            .colonize(7, 0.12, 1.7, 0.1).lobed(0.92).foliage(2.8, 0.8, 0.35).roots(6, 2.3),
            "roble", "encina", "quercus"),
    SPRUCE("spruce", "Abeto", 24, "spruce_log", "spruce_leaves", new Form(Architecture.CONIFER)
            .trunk(0.05, 0.8, 0.25, 0.35).crown(Envelope.ELLIPSOID, 0.14, 1.0, 0.5, 0.25, 0.5)
            .foliage(1.6, 0.6, 0.9).roots(4, 1.8).wander(0.25),
            "abeto", "pino", "conifera", "pine", "fir"),
    BIRCH("birch", "Abedul", 17, "birch_log", "birch_leaves", new Form(Architecture.BROADLEAF)
            .trunk(0.045, 0.75, 0.55, 0.3).crown(Envelope.ELLIPSOID, 0.28, 0.82, 0.62, 0.28, 0.42)
            .colonize(6, 0.16, 1.5, 0.3).lobed(0.8).foliage(2.3, 0.85, 0.45).roots(2, 1.6).wander(0.6),
            "abedul", "betula"),
    JUNGLE("jungle", "Jungla", 30, "jungle_log", "jungle_leaves", new Form(Architecture.BROADLEAF)
            .trunk(0.06, 1.4, 0.7, 0.8).crown(Envelope.UMBRELLA, 0.62, 0.74, 0.84, 0.4, 0.18)
            .colonize(8, 0.11, 1.8, 0.05).lobed(0.85).foliage(3.0, 0.6, 0.4).roots(8, 2.8).wander(0.7)
            .sideClumps(4),
            "jungla", "selva", "tropical", "ceiba"),
    ACACIA("acacia", "Acacia", 14, "acacia_log", "acacia_leaves", new Form(Architecture.ACACIA)
            .trunk(0.06, 0.8, 0.7, 0.35).crown(Envelope.UMBRELLA, 0.42, 0.42, 0.88, 0.5, 0.1)
            .foliage(3.8, 0.42, 0.7).roots(3, 2.0).wander(0.8).lean(LeanStyle.STRAIGHT, 6.0),
            "sabana", "savanna"),
    CHERRY("cherry", "Cerezo", 13, "cherry_log", "cherry_leaves", new Form(Architecture.BROADLEAF)
            .trunk(0.09, 0.9, 0.7, 0.5).crown(Envelope.DOME, 0.26, 0.34, 0.68, 0.62, 0.32)
            .colonize(7, 0.12, 1.7, 0.2).lobed(0.85).foliage(2.9, 0.7, 0.35).hanging(0.18, 0.2).roots(5, 2.0)
            .wander(1.1),
            "cerezo", "sakura"),
    WILLOW("willow", "Sauce llorón", 16, "oak_log", "oak_leaves,azalea_leaves", new Form(Architecture.BROADLEAF)
            .trunk(0.09, 1.0, 0.7, 0.6).crown(Envelope.DOME, 0.34, 0.45, 0.7, 0.56, 0.26)
            .colonize(7, 0.12, 1.7, 0.1).lobed(0.7).foliage(2.5, 0.7, 0.4).hanging(0.42, 0.52).roots(6, 2.4)
            .wander(0.8),
            "sauce", "saucelloron", "lloron", "weepingwillow"),
    DARK_OAK("dark_oak", "Roble oscuro", 15, "dark_oak_log", "dark_oak_leaves", new Form(Architecture.BROADLEAF)
            .trunk(0.13, 1.6, 0.8, 0.8).crown(Envelope.DOME, 0.3, 0.46, 0.72, 0.6, 0.27)
            .colonize(8, 0.12, 1.8, 0.06).lobed(0.75).foliage(3.0, 0.62, 0.45).roots(8, 2.0).wander(0.7),
            "robleoscuro", "oscuro", "darkoak"),
    DEAD("dead", "Árbol muerto", 15, "spruce_log", "no", new Form(Architecture.BROADLEAF)
            .trunk(0.08, 1.0, 0.6, 0.45).crown(Envelope.ELLIPSOID, 0.26, 0.62, 0.62, 0.46, 0.4)
            .colonize(6, 0.07, 1.6, 0.22).lobed(0.6).foliage(0.0, 0.7, 0.4).roots(5, 2.4).wander(1.6)
            .broken(0.25),
            "muerto", "seco", "arbolmuerto", "deadtree", "snag"),
    PALM("palm", "Palmera", 16, "jungle_log", "jungle_leaves", new Form(Architecture.PALM)
            .trunk(0.05, 0.7, 0.75, 0.3).crown(Envelope.ELLIPSOID, 0.9, 1.0, 1.0, 0.55, 0.1)
            .foliage(1.6, 0.5, 0.8).roots(0, 0.0).wander(0.3).lean(LeanStyle.BANANA, 16.0),
            "palmera", "palma", "cocotero"),
    BAOBAB("baobab", "Baobab", 20, "acacia_log", "acacia_leaves", new Form(Architecture.BAOBAB)
            .trunk(0.16, 2.0, 0.55, 0.3).crown(Envelope.UMBRELLA, 0.8, 0.8, 0.95, 0.42, 0.12)
            .foliage(1.6, 0.75, 0.6).roots(5, 1.4).wander(0.4),
            "adansonia"),
    FANTASY("fantasy", "Fantástico", 22, "mangrove_log", "flowering_azalea_leaves,azalea_leaves",
            new Form(Architecture.BROADLEAF)
                    .trunk(0.08, 1.0, 0.6, 0.6).crown(Envelope.ELLIPSOID, 0.46, 0.6, 0.74, 0.44, 0.26)
                    .colonize(6, 0.11, 1.7, 0.08).lobed(0.9).foliage(2.5, 0.85, 0.35).hanging(0.16, 0.22)
                    .roots(6, 2.6).wander(1.3).twist(0.6).lean(LeanStyle.SERPENT, 0.0),
            "fantastico", "retorcido", "curvo", "magico", "twisted");

    /** Cómo se ramifica cada especie. */
    public enum Architecture {
        /** Frondosa: colonización del espacio dentro de la copa. */
        BROADLEAF,
        /** Conífera: guía central y verticilos de ramas que se acortan hacia arriba. */
        CONIFER,
        /** Acacia: el tronco se abre en varias guías que acaban en copas planas. */
        ACACIA,
        /** Palmera: tronco curvo sin ramas y una corona de frondas. */
        PALM,
        /** Baobab: tronco en botella y una corona corta de ramas gruesas. */
        BAOBAB
    }

    /** Volumen donde se reparten los puntos de atracción de la copa. */
    public enum Envelope {
        ELLIPSOID,
        /** Cúpula con la base plana. */
        DOME,
        /** Sombrilla: ancha y aplanada. */
        UMBRELLA
    }

    /** Cómo se inclina el tronco. */
    public enum LeanStyle {
        /** Recto e inclinado entero. */
        STRAIGHT,
        /** Inclinado en la base y enderezándose hacia la luz. */
        RECOVER,
        /** Cada vez más inclinado, como una palmera. */
        BANANA,
        /** Ondulado en S. */
        SERPENT
    }

    /**
     * Proporciones de una especie. Las longitudes van en fracción de la altura salvo que se diga otra cosa; se fija
     * una vez al crear la especie y no cambia después.
     */
    public static final class Form {
        private final Architecture architecture;
        double trunkRadius;
        double trunkMinimum;
        double topRatio;
        double flare;
        Envelope envelope = Envelope.ELLIPSOID;
        double crownBase;
        double leader;
        double crownCenter;
        double crownWidth;
        double crownHeight;
        int lobes = 6;
        double attractors = 0.1;
        double kill = 3.0;
        double tropism;
        double twist;
        double wander = 0.5;
        double clump = 2.5;
        double flatten = 0.7;
        double inner = 0.5;
        double hanging;
        double hangingLength;
        int roots = 5;
        double rootLength = 3.0;
        double broken;
        double lobeShare = 0.72;
        int sideClumps;
        LeanStyle leanStyle = LeanStyle.RECOVER;
        double defaultLean;

        Form(Architecture architecture) {
            this.architecture = architecture;
        }

        /**
         * @param radius  radio del tronco por bloque de altura
         * @param minimum radio mínimo del tronco en bloques
         * @param top     radio en lo alto de la guía respecto al de la base
         * @param flare   ensanchamiento de la base (0.4 = un 40 % más ancho a ras de suelo)
         */
        Form trunk(double radius, double minimum, double top, double flare) {
            trunkRadius = radius;
            trunkMinimum = minimum;
            topRatio = top;
            this.flare = flare;
            return this;
        }

        /**
         * @param base   altura desde la que salen ramas
         * @param leader hasta dónde sube la guía del tronco
         * @param center altura del centro de la copa
         * @param width  radio horizontal de la copa
         * @param height radio vertical de la copa
         */
        Form crown(Envelope shape, double base, double leader, double center, double width, double height) {
            envelope = shape;
            crownBase = base;
            this.leader = leader;
            crownCenter = center;
            crownWidth = width;
            crownHeight = height;
            return this;
        }

        /**
         * @param lobes       bultos de la copa
         * @param density     puntos de atracción por bloque cúbico de copa
         * @param kill        a qué distancia una rama consume un punto
         * @param tropism     tendencia de las ramas a subir (negativa: a caer)
         */
        Form colonize(int lobes, double density, double kill, double tropism) {
            this.lobes = lobes;
            attractors = density;
            this.kill = kill;
            this.tropism = tropism;
            return this;
        }

        /**
         * @param clump   radio de los grupos de hojas en bloques (0 sin hojas)
         * @param flatten alto de un grupo respecto a su ancho
         * @param inner   densidad de las hojas del interior de la copa (la superficie va casi llena)
         */
        Form foliage(double clump, double flatten, double inner) {
            this.clump = clump;
            this.flatten = flatten;
            this.inner = inner;
            return this;
        }

        /** Parte de los puntos de atracción que va a los bultos de la copa (el resto, repartida). */
        Form lobed(double share) {
            lobeShare = share;
            return this;
        }

        Form hanging(double probability, double length) {
            hanging = probability;
            hangingLength = length;
            return this;
        }

        Form roots(int count, double length) {
            roots = count;
            rootLength = length;
            return this;
        }

        Form wander(double value) {
            wander = value;
            return this;
        }

        Form twist(double value) {
            twist = value;
            return this;
        }

        Form broken(double value) {
            broken = value;
            return this;
        }

        Form sideClumps(int value) {
            sideClumps = value;
            return this;
        }

        Form lean(LeanStyle style, double degrees) {
            leanStyle = style;
            defaultLean = degrees;
            return this;
        }

        public Architecture architecture() {
            return architecture;
        }
    }

    private final String id;
    private final String displayName;
    private final int defaultHeight;
    private final String trunk;
    private final String leaves;
    private final Form form;
    private final List<String> aliases;

    TreeSpecies(String id, String displayName, int defaultHeight, String trunk, String leaves, Form form,
                String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.defaultHeight = defaultHeight;
        this.trunk = trunk;
        this.leaves = leaves;
        this.form = form;
        this.aliases = List.of(aliases);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int defaultHeight() {
        return defaultHeight;
    }

    /** Material del tronco por defecto (también el de las raíces). */
    public String trunk() {
        return trunk;
    }

    /** Hojas por defecto; {@code no} en el árbol muerto. */
    public String leaves() {
        return leaves;
    }

    public Form form() {
        return form;
    }

    public List<String> aliases() {
        return aliases;
    }

    /** Radio horizontal máximo que puede ocupar, por bloque de altura, sin contar la inclinación. */
    double reach() {
        return switch (form.architecture) {
            case PALM -> 0.62;
            case CONIFER -> form.crownWidth + 0.12;
            default -> form.crownWidth + 0.08 + form.clump / 18.0;
        };
    }

    public static TreeSpecies byName(String input) {
        TreeSpecies species = find(input);
        if (species == null) {
            throw new IllegalArgumentException("Especie desconocida: " + input + " (" + names() + ")");
        }
        return species;
    }

    /** La especie con ese id o alias, o {@code null}. */
    public static TreeSpecies find(String input) {
        String normalized = normalize(input);
        for (TreeSpecies species : values()) {
            if (normalize(species.id).equals(normalized) || normalize(species.displayName).equals(normalized)) {
                return species;
            }
            for (String alias : species.aliases) {
                if (normalize(alias).equals(normalized)) {
                    return species;
                }
            }
        }
        return null;
    }

    public static String names() {
        StringBuilder text = new StringBuilder();
        for (TreeSpecies species : values()) {
            if (!text.isEmpty()) {
                text.append(", ");
            }
            text.append(species.id);
        }
        return text.toString();
    }

    static String normalize(String input) {
        return input.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "")
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
    }
}
