package com.komainu.moxia.shape.smooth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Qué losa, escalera, muro, capa o panel corresponde a un bloque ({@code glass} → {@code glass_pane},
 * {@code iron_block} → {@code iron_bars}).
 *
 * <ol>
 *     <li><b>Por nombre.</b> Casi todos los bloques siguen la convención de Minecraft ({@code stone} →
 *     {@code stone_slab}, {@code stone_bricks} → {@code stone_brick_stairs}, {@code oak_planks} → {@code oak_slab},
 *     {@code snow_block} → {@code snow}). Esta clase genera, por orden de preferencia, las "raíces" posibles de un
 *     bloque y la tabla resuelve los casos sin convención (troncos, pilares, cobre, piedra pulida sin
 *     escaleras…).</li>
 *     <li><b>Por color.</b> Si ninguna raíz tiene esa pieza (hormigón, lana, terracota…), se usa la del color más
 *     parecido con la ΔE de CIELAB ({@link ColorMatch}). Los colores son la media de las texturas vanilla
 *     ({@code assets/komaedit/smooth/colors.txt}, generada con {@code tools/smooth_colors.py}); los bloques de
 *     mods que no están en la tabla usan su color de mapa.</li>
 * </ol>
 *
 * <p>La búsqueda es pura: el servidor le pasa un {@link Catalog} con lo que hay registrado.</p>
 */
public final class SmoothMaterials {
    private static final int MAX_STEMS = 12;
    /** Recurso de la tabla de colores de textura; también la lee {@code color.BlockColorCatalog}. */
    public static final String COLORS = "/assets/komaedit/smooth/colors.txt";
    /** Bloques cuya familia tiene otro nombre. */
    private static final Map<String, String> TABLE = Map.ofEntries(
            Map.entry("deepslate", "cobbled_deepslate"),
            Map.entry("chiseled_deepslate", "deepslate_tile"),
            Map.entry("reinforced_deepslate", "deepslate_tile"),
            Map.entry("end_stone", "end_stone_brick"),
            Map.entry("netherrack", "nether_brick"),
            Map.entry("nether_wart_block", "red_nether_brick"),
            Map.entry("packed_mud", "mud_brick"),
            Map.entry("mud", "mud_brick"),
            Map.entry("quartz_bricks", "quartz"),
            Map.entry("sand", "sandstone"),
            Map.entry("red_sand", "red_sandstone"),
            Map.entry("suspicious_sand", "sandstone"),
            Map.entry("gilded_blackstone", "blackstone"),
            Map.entry("magma_block", "nether_brick"),
            Map.entry("powder_snow", "snow"),
            Map.entry("dripstone_block", "tuff"),
            Map.entry("smooth_basalt", "polished_deepslate")
    );
    /** Prefijos que no cambian la familia; también se quitan en medio del nombre ({@code waxed_chiseled_copper}). */
    private static final String[] PREFIXES = {
            "chiseled_", "cracked_", "infested_", "stripped_", "smooth_", "polished_", "cut_", "gilded_"
    };
    private static final String[] WOOD_SUFFIXES = {"_planks", "_log", "_wood", "_stem", "_hyphae", "_block",
            "_pillar"};
    /** Sufijos de cada pieza por orden de preferencia; las capas de nieve son el propio {@code snow}. */
    private static final Map<PieceVariant, String[]> SUFFIXES = Map.of(
            PieceVariant.SLAB, new String[]{"_slab"},
            PieceVariant.STAIR, new String[]{"_stairs"},
            PieceVariant.WALL, new String[]{"_wall", "_fence"},
            PieceVariant.LAYER, new String[]{"", "_layer", "_layers"},
            PieceVariant.PANE, new String[]{"_pane", "_bars"}
    );
    private static volatile Map<String, Integer> textureColors;

    private SmoothMaterials() {
    }

    /** Lo que hay registrado en el juego, para que la búsqueda no dependa de Minecraft. */
    public interface Catalog {
        /** El identificador es un bloque registrado. */
        boolean exists(String id);

        /** El identificador es una pieza de esa variante (losa, escalera, muro o valla, bloque con capas). */
        boolean is(String id, PieceVariant variant);

        /** Todas las piezas de la variante que pueden elegirse por color. */
        List<String> pieces(PieceVariant variant);

        /** Color {@code 0xRRGGBB} del bloque, o {@code -1} si no se conoce. */
        int color(String id);
    }

    public static List<String> slabIds(String blockId) {
        return withSuffix(blockId, "_slab");
    }

    public static List<String> stairsIds(String blockId) {
        return withSuffix(blockId, "_stairs");
    }

    /**
     * Candidatos a la pieza de un bloque con cualquier sufijo de forma ({@code _wall}, {@code _fence}…), por orden
     * de preferencia; quien la usa se queda con el primero que exista.
     */
    public static List<String> shapeIds(String blockId, String suffix) {
        return withSuffix(blockId, suffix);
    }

    /** Candidatos por nombre a la pieza de un bloque, en orden de preferencia. */
    public static List<String> candidates(String blockId, PieceVariant variant) {
        List<String> ids = new ArrayList<>();
        for (String suffix : SUFFIXES.get(variant)) {
            for (String id : withSuffix(blockId, suffix)) {
                if (!id.equals(normalize(blockId)) || !suffix.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        // Primero todas las raíces con el sufijo preferido, luego el siguiente sufijo.
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    /**
     * La pieza de un bloque: por nombre o, si no tiene, la del color más parecido.
     *
     * @return el identificador, o {@code null} si no hay ninguna pieza de esa variante con color conocido
     */
    public static String resolve(String blockId, PieceVariant variant, Catalog catalog) {
        String id = normalize(blockId);
        if (catalog.is(id, variant)) {
            return id;
        }
        for (String candidate : candidates(id, variant)) {
            if (catalog.is(candidate, variant)) {
                return candidate;
            }
        }
        return nearest(catalog.color(id), variant, catalog);
    }

    /** La pieza de la variante con el color más parecido; {@code null} si no se conoce el color. */
    public static String nearest(int color, PieceVariant variant, Catalog catalog) {
        if (color < 0) {
            return null;
        }
        String best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (String candidate : catalog.pieces(variant)) {
            int candidateColor = catalog.color(candidate);
            if (candidateColor < 0) {
                continue;
            }
            double distance = ColorMatch.distanceSquared(color, candidateColor);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * Material escrito por el jugador para una variante ({@code Slab:acacia}): la propia pieza
     * ({@code acacia_slab}), el nombre corto de su familia ({@code acacia}) o cualquier bloque, que se convierte
     * en su pieza como si lo pusiera el patrón ({@code oak_planks}, {@code white_wool}).
     */
    public static String override(String text, PieceVariant variant, Catalog catalog) {
        String id = normalize(text);
        if (catalog.is(id, variant)) {
            return id;
        }
        for (String candidate : candidates(id, variant)) {
            if (catalog.is(candidate, variant)) {
                return candidate;
            }
        }
        if (catalog.exists(id)) {
            String resolved = nearest(catalog.color(id), variant, catalog);
            if (resolved != null) {
                return resolved;
            }
        }
        throw new IllegalArgumentException("No hay " + variant.displayName() + " de «" + text + "»: escribe la "
                + "pieza (" + example(variant) + "), su familia (stone, oak…) o un bloque con color");
    }

    /** Colores medios de textura de los bloques vanilla, {@code id → 0xRRGGBB}. */
    public static Map<String, Integer> textureColors() {
        Map<String, Integer> colors = textureColors;
        if (colors == null) {
            try (InputStream stream = SmoothMaterials.class.getResourceAsStream(COLORS)) {
                colors = stream == null ? Map.of() : readColors(stream);
            } catch (IOException exception) {
                throw new IllegalStateException("No se pudo leer la tabla de colores de piezas", exception);
            }
            textureColors = colors;
        }
        return colors;
    }

    static Map<String, Integer> readColors(InputStream stream) throws IOException {
        Map<String, Integer> result = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            // La quinta columna, si la hay, son banderas para /komaedit color.
            if (parts.length >= 4 && !parts[0].startsWith("#")) {
                result.put(parts[0], ColorMatch.rgb(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                        Integer.parseInt(parts[3])));
            }
        }
        return Map.copyOf(result);
    }

    static String normalize(String blockId) {
        String text = blockId.trim().toLowerCase(Locale.ROOT);
        return text.indexOf(':') < 0 ? "minecraft:" + text : text;
    }

    private static String example(PieceVariant variant) {
        return switch (variant) {
            case SLAB -> "acacia_slab";
            case STAIR -> "stone_brick_stairs";
            case WALL -> "cobblestone_wall";
            case LAYER -> "snow";
            case PANE -> "glass_pane";
        };
    }

    /** Raíces por orden de preferencia: primero el propio bloque, después sus parientes más cercanos. */
    static List<String> stems(String path) {
        Set<String> found = new LinkedHashSet<>();
        List<String> frontier = new ArrayList<>(List.of(path));
        while (!frontier.isEmpty() && found.size() < MAX_STEMS) {
            List<String> next = new ArrayList<>();
            for (String stem : frontier) {
                if (stem.isEmpty() || !found.add(stem)) {
                    continue;
                }
                next.addAll(relatives(stem));
            }
            frontier = next;
        }
        return List.copyOf(found);
    }

    private static List<String> relatives(String stem) {
        List<String> relatives = new ArrayList<>();
        String mapped = TABLE.get(stem);
        if (mapped != null) {
            relatives.add(mapped);
        }
        // stone_bricks → stone_brick, deepslate_tiles → deepslate_tile, bricks → brick.
        if (stem.endsWith("bricks") || stem.endsWith("tiles")) {
            relatives.add(stem.substring(0, stem.length() - 1));
        }
        for (String suffix : WOOD_SUFFIXES) {
            if (stem.endsWith(suffix) && stem.length() > suffix.length()) {
                relatives.add(stem.substring(0, stem.length() - suffix.length()));
            }
        }
        // El cobre entero no tiene losas; sí su versión cortada con el mismo grado de oxidación.
        if (stem.endsWith("copper") && !stem.endsWith("cut_copper")) {
            relatives.add(stem.substring(0, stem.length() - "copper".length()) + "cut_copper");
        }
        for (String prefix : PREFIXES) {
            if (stem.startsWith(prefix) && stem.length() > prefix.length()) {
                relatives.add(stem.substring(prefix.length()));
            }
            int infix = stem.indexOf("_" + prefix);
            if (infix > 0) {
                relatives.add(stem.substring(0, infix + 1) + stem.substring(infix + 1 + prefix.length()));
            }
        }
        return relatives;
    }

    private static List<String> withSuffix(String blockId, String suffix) {
        String id = normalize(blockId);
        int colon = id.indexOf(':');
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1);
        List<String> ids = new ArrayList<>();
        for (String stem : stems(path)) {
            ids.add(namespace + ":" + stem + suffix);
        }
        return ids;
    }
}
