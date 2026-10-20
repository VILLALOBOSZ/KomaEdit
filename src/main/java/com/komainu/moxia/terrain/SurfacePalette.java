package com.komainu.moxia.terrain;

import com.komainu.moxia.noise.NoiseEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Material del terreno según la pendiente y la altura: hierba en lo llano, piedra en las pendientes, nieve en las
 * cumbres, arena en la orilla y grava en el lecho. Las fronteras llevan algo de ruido para que no salgan en línea
 * recta.
 *
 * <p>Se escribe como un preajuste ({@code natural}, {@code desierto}…) seguido, si hace falta, de cambios
 * {@code clave:bloques@valor} separados por comas: {@code natural,cumbre:powder_snow@170,pendiente:stone|tuff@35}.
 * Varios bloques con {@code |} se mezclan a manchas.</p>
 *
 * <ul>
 *     <li>{@code llano}: lo de arriba en lo llano.</li>
 *     <li>{@code pendiente@grados}: arriba cuando la pendiente pasa de esos grados.</li>
 *     <li>{@code cumbre@altura}: arriba por encima de esa Y, si no es pendiente. Con {@code %} ({@code cumbre@70%})
 *     es relativa a lo que se cambia: por encima del 70 % entre lo más bajo y lo más alto (la nieve de una montaña
 *     estampada sale en su tercio de arriba, esté a la altura que esté).</li>
 *     <li>{@code relleno@capas}: bajo la superficie, tantas capas.</li>
 *     <li>{@code fondo}: más abajo.</li>
 *     <li>{@code lecho}: bajo el agua.</li>
 *     <li>{@code orilla@altura}: hasta esa altura por encima del agua.</li>
 *     <li>{@code estratos}: bandas por altura en las pendientes (estilo badlands).</li>
 * </ul>
 */
public final class SurfacePalette {
    public static final String DEFAULT = "natural";
    private static final long SALT = 0x5355_5246_4143_45L;
    private static final Map<String, String> PRESETS = new LinkedHashMap<>();
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        preset("natural", "Hierba, piedra en las pendientes y nieve por encima de Y 150",
                "llano:grass_block,pendiente:stone|stone|andesite@50,cumbre:snow_block@150,relleno:dirt@3,"
                        + "fondo:stone,lecho:gravel|sand|clay|gravel,orilla:sand@1");
        preset("pradera", "Hierba hasta en las cuestas; tierra gruesa y piedra solo en lo muy empinado",
                "llano:grass_block,pendiente:coarse_dirt|stone|grass_block@58,relleno:dirt@4,fondo:stone,"
                        + "lecho:gravel|clay|dirt,orilla:sand@0");
        preset("bosque", "Hierba con podzol y tierra gruesa, piedra musgosa en las pendientes",
                "llano:grass_block|grass_block|podzol|coarse_dirt,pendiente:stone|andesite|mossy_cobblestone@52,"
                        + "relleno:dirt@3,fondo:stone,lecho:gravel|mud|clay,orilla:mud|coarse_dirt@0");
        preset("desierto", "Arena y arenisca",
                "llano:sand,pendiente:sandstone|smooth_sandstone@42,relleno:sand@3,fondo:sandstone,lecho:sand,"
                        + "orilla:sand@2");
        preset("mesa", "Arena roja arriba y estratos de terracota en las pendientes",
                "llano:red_sand,pendiente:terracotta@38,estratos:terracotta|orange_terracotta|yellow_terracotta|"
                        + "terracotta|white_terracotta|red_terracotta|brown_terracotta|light_gray_terracotta,"
                        + "relleno:terracotta@3,fondo:stone,lecho:red_sand,orilla:red_sand@1");
        preset("nevado", "Nieve sobre todo lo llano y piedra en las pendientes",
                "llano:snow_block,pendiente:stone|stone|andesite@46,cumbre:snow_block@0,relleno:dirt@2,fondo:stone,"
                        + "lecho:gravel,orilla:gravel|stone@0");
        preset("volcanico", "Toba, piedra negra y basalto; magma en el lecho de un río de lava",
                "llano:tuff|blackstone|smooth_basalt,pendiente:basalt|blackstone@42,relleno:blackstone@3,"
                        + "fondo:stone,lecho:magma_block|blackstone,orilla:basalt|blackstone@1");
        preset("playa", "Arena con arenisca en lo empinado",
                "llano:sand,pendiente:sandstone@44,relleno:sand@4,fondo:sandstone,lecho:sand|gravel,orilla:sand@3");
        preset("alpino", "Hierba, roca en las pendientes y nieve en lo más alto de lo que se cambia",
                "llano:grass_block,pendiente:stone|stone|andesite|tuff@38,cumbre:snow_block@70%,relleno:dirt@3,"
                        + "fondo:stone,lecho:gravel|stone,orilla:gravel|sand@0");
        preset("costa", "Hierba arriba, paredes de roca y arena al pie",
                "llano:grass_block,pendiente:stone|andesite|diorite|stone@45,relleno:dirt@3,fondo:stone,"
                        + "lecho:sand|gravel,orilla:sand@2");
        preset("rocoso", "Piedra, andesita y grava con algo de hierba en lo llano",
                "llano:grass_block|coarse_dirt|grass_block,pendiente:stone|andesite|cobblestone|tuff@32,"
                        + "relleno:dirt@2,fondo:stone,lecho:gravel,orilla:gravel@0");
        preset("isla", "Hierba con playa de arena alrededor y roca en lo empinado",
                "llano:grass_block,pendiente:stone|andesite@44,relleno:dirt@3,fondo:stone,lecho:sand|sand|gravel,"
                        + "orilla:sand@3");
        preset("bancal", "Hierba en los bancales y muros de piedra en los escalones",
                "llano:grass_block,pendiente:cobblestone|mossy_cobblestone|stone@40,relleno:dirt@3,fondo:stone,"
                        + "lecho:gravel|clay,orilla:sand@0");
        preset("glaciar", "Nieve y hielo; roca y grava en las pendientes",
                "llano:snow_block|snow_block|packed_ice,pendiente:stone|gravel|packed_ice@38,relleno:packed_ice@3,"
                        + "fondo:stone,lecho:packed_ice|blue_ice,orilla:gravel@0");
        preset("piedra", "Todo de piedra, para texturizarlo después",
                "llano:stone,pendiente:stone@90,relleno:stone@1,fondo:stone,lecho:stone");
        preset("conservar", "Los mismos bloques que ya tenía cada columna", null);
    }

    private final String source;
    private final boolean keep;
    private final Materials flat;
    private final Materials steep;
    private final double steepAngle;
    private final Materials peak;
    private final int peakY;
    /** Altura de la cumbre relativa (0 a 1) o NaN si es una Y fija. */
    private final double peakFraction;
    private final Materials fill;
    private final int fillDepth;
    private final Materials deep;
    private final Materials bed;
    private final Materials shore;
    private final int shoreHeight;
    private final Materials strata;

    private SurfacePalette(String source, Builder builder) {
        this.source = source;
        this.keep = builder.keep;
        this.flat = builder.flat;
        this.steep = builder.steep;
        this.steepAngle = builder.steepAngle;
        this.peak = builder.peak;
        this.peakY = builder.peakY;
        this.peakFraction = builder.peakFraction;
        this.fill = builder.fill;
        this.fillDepth = builder.fillDepth;
        this.deep = builder.deep;
        this.bed = builder.bed;
        this.shore = builder.shore;
        this.shoreHeight = builder.shoreHeight;
        this.strata = builder.strata;
    }

    private SurfacePalette(SurfacePalette other, int peakY) {
        this.source = other.source;
        this.keep = other.keep;
        this.flat = other.flat;
        this.steep = other.steep;
        this.steepAngle = other.steepAngle;
        this.peak = other.peak;
        this.peakY = peakY;
        this.peakFraction = Double.NaN;
        this.fill = other.fill;
        this.fillDepth = other.fillDepth;
        this.deep = other.deep;
        this.bed = other.bed;
        this.shore = other.shore;
        this.shoreHeight = other.shoreHeight;
        this.strata = other.strata;
    }

    /** Si la cumbre va en porcentaje de lo que se cambia ({@code cumbre@70%}). */
    public boolean relativePeak() {
        return peak != null && !Double.isNaN(peakFraction);
    }

    /** La misma paleta con la cumbre relativa ya puesta en una Y, entre lo más bajo y lo más alto de la zona. */
    public SurfacePalette forRange(double lowest, double highest) {
        if (!relativePeak() || Double.isNaN(lowest) || Double.isNaN(highest)) {
            return this;
        }
        return new SurfacePalette(this, (int) Math.round(lowest + (highest - lowest) * peakFraction));
    }

    private static void preset(String name, String description, String definition) {
        PRESETS.put(name, definition);
        DESCRIPTIONS.put(name, description);
    }

    /** Preajustes y lo que hace cada uno, en orden. */
    public static Map<String, String> presets() {
        return java.util.Collections.unmodifiableMap(DESCRIPTIONS);
    }

    public static SurfacePalette natural() {
        return parse(DEFAULT);
    }

    public static SurfacePalette parse(String input) {
        String text = input == null || input.isBlank() ? DEFAULT : input.strip();
        Builder builder = new Builder();
        boolean first = true;
        for (String raw : text.split(",")) {
            String token = raw.strip();
            if (token.isEmpty()) {
                continue;
            }
            String name = normalizeName(token);
            if (PRESETS.containsKey(name)) {
                if (!first) {
                    throw new IllegalArgumentException("El preajuste " + name + " va al principio de la paleta");
                }
                builder = new Builder();
                if (PRESETS.get(name) == null) {
                    builder.keep = true;
                } else {
                    for (String part : PRESETS.get(name).split(",")) {
                        builder.apply(part);
                    }
                }
            } else if (token.indexOf(':') > 0 && isKey(token.substring(0, token.indexOf(':')))) {
                builder.apply(token);
            } else {
                throw new IllegalArgumentException("Paleta de superficie desconocida: «" + token + "» (preajustes: "
                        + String.join(", ", PRESETS.keySet()) + "; o clave:bloques@valor con llano, pendiente, "
                        + "cumbre, relleno, fondo, lecho, orilla o estratos)");
            }
            first = false;
        }
        return new SurfacePalette(text, builder);
    }

    private static String normalizeName(String text) {
        return text.toLowerCase(Locale.ROOT).replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u");
    }

    private static boolean isKey(String text) {
        return Builder.key(text) != null;
    }

    public String source() {
        return source;
    }

    /** Si copia los bloques que ya había en lugar de elegirlos. */
    public boolean keepsExisting() {
        return keep;
    }

    /** Capas de relleno bajo la superficie que se repintan. */
    public int fillDepth() {
        return keep ? 1 : fillDepth;
    }

    /** Todos los bloques que puede escribir, para resolverlos antes de calcular. */
    public Set<String> blockIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Materials materials : new Materials[]{flat, steep, peak, fill, deep, bed, shore, strata}) {
            if (materials != null) {
                ids.addAll(materials.ids);
            }
        }
        return ids;
    }

    /**
     * Lo que va en una posición de una columna.
     *
     * @param depth bloques por debajo de la superficie (0 es la superficie)
     */
    public String material(int x, int y, int z, int depth, Column column) {
        if (keep) {
            String original = depth == 0 ? column.originalTop() : column.originalBelow();
            return original == null ? (depth == 0 ? "minecraft:grass_block" : "minecraft:dirt") : original;
        }
        int top = column.top();
        boolean submerged = column.waterLevel() != TerrainColumns.NONE && top < column.waterLevel();
        boolean nearWater = shore != null && column.waterLevel() != TerrainColumns.NONE && !submerged
                && top <= column.waterLevel() + shoreHeight;
        double jitter = NoiseEngine.fractalBrownianMotion2D(x * 0.09, z * 0.09, SALT, 3, 2.0, 0.5);
        boolean steepHere = steep != null && column.slope() >= steepAngle + jitter * 8.0;
        boolean high = peak != null && top >= peakY + Math.round(jitter * 6.0);
        if (depth == 0) {
            if (submerged) {
                return (bed != null ? bed : flat).pick(x, y, z);
            }
            if (steepHere) {
                return strata != null ? strata.band(y, x, z) : steep.pick(x, y, z);
            }
            if (high) {
                return peak.pick(x, y, z);
            }
            if (nearWater) {
                return shore.pick(x, y, z);
            }
            return flat.pick(x, y, z);
        }
        if (depth <= fillDepth) {
            if (submerged) {
                return depth <= 1 && bed != null ? bed.pick(x, y, z) : (fill != null ? fill : deep).pick(x, y, z);
            }
            if (steepHere) {
                return strata != null ? strata.band(y, x, z) : steep.pick(x, y, z);
            }
            if (nearWater) {
                return shore.pick(x, y, z);
            }
            return (fill != null ? fill : flat).pick(x, y, z);
        }
        if (strata != null && steepHere) {
            return strata.band(y, x, z);
        }
        return (deep != null ? deep : fill != null ? fill : flat).pick(x, y, z);
    }

    @Override
    public String toString() {
        return source;
    }

    /**
     * Lo que se sabe de la columna al elegir su material.
     *
     * @param top           altura nueva de la superficie
     * @param slope         pendiente en grados
     * @param waterLevel    altura del agua o la lava sobre la columna, o {@link TerrainColumns#NONE}
     * @param originalTop   bloque de superficie que tenía (para {@code conservar})
     * @param originalBelow bloque bajo la superficie que tenía
     */
    public record Column(int top, double slope, int waterLevel, String originalTop, String originalBelow) {
    }

    /** Lista de bloques que se reparten a manchas. */
    static final class Materials {
        private final List<String> ids;

        Materials(List<String> ids) {
            this.ids = List.copyOf(ids);
        }

        static Materials parse(String text) {
            List<String> ids = new ArrayList<>();
            for (String raw : text.split("\\|")) {
                String id = raw.strip().toLowerCase(Locale.ROOT);
                if (id.isEmpty()) {
                    continue;
                }
                if (!id.matches("[a-z0-9_.\\-]+(:[a-z0-9_./\\-]+)?")) {
                    throw new IllegalArgumentException("Bloque no válido en la paleta de superficie: " + raw.strip());
                }
                ids.add(id.contains(":") ? id : "minecraft:" + id);
            }
            if (ids.isEmpty()) {
                throw new IllegalArgumentException("Falta el bloque en «" + text + "»");
            }
            return new Materials(ids);
        }

        String pick(int x, int y, int z) {
            if (ids.size() == 1) {
                return ids.getFirst();
            }
            double patches = 0.5 + 0.5 * NoiseEngine.fractalBrownianMotion2D(x * 0.17 + y * 0.05, z * 0.17,
                    SALT ^ 0x7A11L, 2, 2.0, 0.5);
            double white = NoiseEngine.deterministicUnit(x, y, z, SALT);
            double amount = Math.clamp(patches * 0.7 + white * 0.3, 0.0, 0.999_999);
            return ids.get((int) (amount * ids.size()));
        }

        /** Bandas horizontales de dos o tres bloques que ondulan un poco. */
        String band(int y, int x, int z) {
            double wave = NoiseEngine.fractalBrownianMotion2D(x * 0.03, z * 0.03, SALT ^ 0xBA4DL, 2, 2.0, 0.5) * 3.0;
            int band = Math.floorDiv((int) Math.round(y + wave), 3);
            return ids.get(Math.floorMod(band * 7 + Math.floorMod(band, 3), ids.size()));
        }
    }

    private static final class Builder {
        private boolean keep;
        private Materials flat = Materials.parse("grass_block");
        private Materials steep;
        private double steepAngle = 50.0;
        private Materials peak;
        private int peakY = 150;
        private double peakFraction = Double.NaN;
        private Materials fill = Materials.parse("dirt");
        private int fillDepth = 3;
        private Materials deep = Materials.parse("stone");
        private Materials bed;
        private Materials shore;
        private int shoreHeight = 1;
        private Materials strata;

        static String key(String text) {
            return switch (normalizeName(text.strip())) {
                case "llano", "flat", "top", "arriba" -> "llano";
                case "pendiente", "steep", "slope", "cuesta" -> "pendiente";
                case "cumbre", "peak", "snow", "nieve", "cima" -> "cumbre";
                case "relleno", "fill", "sub", "subsuelo" -> "relleno";
                case "fondo", "deep", "base" -> "fondo";
                case "lecho", "bed", "cauce" -> "lecho";
                case "orilla", "shore", "playa" -> "orilla";
                case "estratos", "strata", "bandas" -> "estratos";
                default -> null;
            };
        }

        void apply(String token) {
            int colon = token.indexOf(':');
            String key = key(token.substring(0, colon));
            String rest = token.substring(colon + 1).strip();
            Double value = null;
            boolean percent = false;
            int at = rest.lastIndexOf('@');
            if (at >= 0) {
                String number = rest.substring(at + 1).strip();
                percent = number.endsWith("%");
                if (percent) {
                    number = number.substring(0, number.length() - 1).strip();
                }
                try {
                    value = Double.parseDouble(number);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("«" + number + "» no es un número en " + token);
                }
                rest = rest.substring(0, at);
            }
            if (percent && !"cumbre".equals(key)) {
                throw new IllegalArgumentException("Solo la cumbre admite porcentaje: " + token);
            }
            if (key == null) {
                throw new IllegalArgumentException("Clave desconocida en la paleta de superficie: " + token);
            }
            keep = false;
            Materials materials = rest.isBlank() ? null : Materials.parse(rest);
            switch (key) {
                case "llano" -> flat = required(materials, token);
                case "pendiente" -> {
                    steep = materials != null ? materials : steep;
                    if (steep == null) {
                        throw new IllegalArgumentException("Falta el bloque en " + token);
                    }
                    if (value != null) {
                        steepAngle = range(value, 0.0, 90.0, "El ángulo de la pendiente");
                    }
                }
                case "cumbre" -> {
                    peak = materials != null ? materials : peak;
                    if (peak == null) {
                        throw new IllegalArgumentException("Falta el bloque en " + token);
                    }
                    if (value != null && percent) {
                        peakFraction = range(value, 0.0, 100.0, "El porcentaje de la cumbre") / 100.0;
                    } else if (value != null) {
                        peakY = (int) Math.round(range(value, -2_048.0, 2_048.0, "La altura de la cumbre"));
                        peakFraction = Double.NaN;
                    }
                }
                case "relleno" -> {
                    fill = materials != null ? materials : fill;
                    if (value != null) {
                        fillDepth = (int) Math.round(range(value, 0.0, 16.0, "Las capas de relleno"));
                    }
                }
                case "fondo" -> deep = required(materials, token);
                case "lecho" -> bed = required(materials, token);
                case "orilla" -> {
                    shore = materials != null ? materials : shore;
                    if (shore == null) {
                        throw new IllegalArgumentException("Falta el bloque en " + token);
                    }
                    if (value != null) {
                        shoreHeight = (int) Math.round(range(value, 0.0, 16.0, "La altura de la orilla"));
                    }
                }
                case "estratos" -> strata = required(materials, token);
                default -> throw new IllegalStateException(key);
            }
        }

        private static Materials required(Materials materials, String token) {
            if (materials == null) {
                throw new IllegalArgumentException("Falta el bloque en " + token);
            }
            return materials;
        }

        private static double range(double value, double minimum, double maximum, String name) {
            if (value < minimum || value > maximum) {
                throw new IllegalArgumentException(name + " debe estar entre " + (long) minimum + " y "
                        + (long) maximum);
            }
            return value;
        }
    }
}
