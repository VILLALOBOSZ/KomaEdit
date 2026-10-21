package com.komainu.moxia.share;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El bloque de este servidor más parecido a uno que no existe aquí (de un mod que falta o de otra versión), por su
 * nombre: {@code biomesoplenty:fir_stairs} → {@code minecraft:oak_stairs}, {@code create:andesite_casing} →
 * {@code minecraft:andesite}. Primero el mismo nombre en Minecraft; si no, la misma forma (escalera, losa, muro…)
 * con más palabras en común y menos de sobra. Si no se parece a nada, aire.
 */
public final class BlockSubstitutes {
    public static final String AIR = "minecraft:air";
    /**
     * Bloques de administración que nunca entran por un paquete: guardan órdenes o estructuras. Se cambian por aire y
     * tampoco se proponen como parecidos.
     */
    public static final Set<String> RESTRICTED = Set.of("minecraft:command_block", "minecraft:chain_command_block",
            "minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:jigsaw",
            "minecraft:structure_void");
    /** Formas de bloque: lo que decide si encaja en el mismo sitio. Las de dos palabras van antes. */
    private static final List<String> FAMILIES = List.of("fence_gate", "pressure_plate", "hanging_sign",
            "wall_hanging_sign", "wall_sign", "glass_pane", "wall_banner", "stairs", "slab", "wall", "fence", "door",
            "trapdoor", "button", "sign", "banner", "log", "wood", "stem", "hyphae", "planks", "leaves", "sapling",
            "pane", "glass", "carpet", "wool", "concrete", "powder", "terracotta", "bricks", "tiles", "pillar", "lamp",
            "torch", "lantern", "candle", "bed", "ore", "flower", "mushroom", "grass", "vine", "chest", "barrel");
    /** Si nada más se parece, el de serie de cada forma (el mismo que un jugador pondría a ojo). */
    private static final Map<String, String> DEFAULTS = Map.ofEntries(
            Map.entry("stairs", "oak_stairs"), Map.entry("slab", "oak_slab"), Map.entry("wall", "cobblestone_wall"),
            Map.entry("fence", "oak_fence"), Map.entry("fence_gate", "oak_fence_gate"),
            Map.entry("door", "oak_door"), Map.entry("trapdoor", "oak_trapdoor"), Map.entry("button", "oak_button"),
            Map.entry("pressure_plate", "oak_pressure_plate"), Map.entry("sign", "oak_sign"),
            Map.entry("wall_sign", "oak_wall_sign"), Map.entry("hanging_sign", "oak_hanging_sign"),
            Map.entry("wall_hanging_sign", "oak_wall_hanging_sign"), Map.entry("log", "oak_log"),
            Map.entry("wood", "oak_wood"), Map.entry("planks", "oak_planks"), Map.entry("leaves", "oak_leaves"),
            Map.entry("sapling", "oak_sapling"), Map.entry("glass_pane", "glass_pane"), Map.entry("pane", "glass_pane"),
            Map.entry("glass", "glass"), Map.entry("carpet", "white_carpet"), Map.entry("wool", "white_wool"),
            Map.entry("concrete", "white_concrete"), Map.entry("terracotta", "terracotta"),
            Map.entry("bricks", "stone_bricks"), Map.entry("tiles", "deepslate_tiles"), Map.entry("ore", "stone"),
            Map.entry("lantern", "lantern"), Map.entry("torch", "torch"), Map.entry("chest", "chest"),
            Map.entry("barrel", "barrel"));

    /** Palabras que no dicen nada del material ({@code moss_block}, {@code block_of_x}). */
    private static final Set<String> GENERIC = Set.of("block", "of", "the");

    private BlockSubstitutes() {
    }

    /** ¿Es un bloque que no puede entrar por un paquete? */
    public static boolean restricted(String id) {
        return RESTRICTED.contains(id);
    }

    /** El más parecido a {@code missing} (con espacio de nombres) que existe en {@code catalog}, o aire. */
    public static String best(String missing, BlockCatalog catalog) {
        int colon = missing.indexOf(':');
        String path = colon < 0 ? missing : missing.substring(colon + 1);
        String vanilla = "minecraft:" + path;
        if (!vanilla.equals(missing) && catalog.has(vanilla) && !restricted(vanilla)) {
            return vanilla;
        }
        String family = family(path);
        Set<String> words = words(path, family);
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (String candidate : catalog.ids()) {
            if (candidate.equals(missing) || restricted(candidate) || air(candidate)) {
                continue;
            }
            int candidateColon = candidate.indexOf(':');
            String candidatePath = candidateColon < 0 ? candidate : candidate.substring(candidateColon + 1);
            String candidateFamily = family(candidatePath);
            Set<String> candidateWords = words(candidatePath, candidateFamily);
            int shared = 0;
            for (String word : candidateWords) {
                if (words.contains(word)) {
                    shared++;
                }
            }
            boolean sameFamily = family != null && family.equals(candidateFamily);
            if (shared == 0 && !sameFamily) {
                continue;
            }
            int score = shared * 10 - (candidateWords.size() - shared);
            if (sameFamily) {
                score += 100;
            } else if (family != null || candidateFamily != null) {
                // Otra forma (una losa por un bloque entero) cambia lo que se ve: solo si no hay nada mejor.
                score -= 50;
            }
            if (candidate.startsWith("minecraft:")) {
                score += 1;
            }
            if (shared == 0 && sameFamily && candidatePath.equals(DEFAULTS.get(family))) {
                score += 5;
            }
            if (score > bestScore || score == bestScore && candidate.compareTo(best) < 0) {
                best = candidate;
                bestScore = score;
            }
        }
        return best == null ? AIR : best;
    }

    /** La forma del bloque por la última o las dos últimas palabras, o {@code null}. */
    static String family(String path) {
        for (String family : FAMILIES) {
            if (path.equals(family) || path.endsWith("_" + family)) {
                return family;
            }
        }
        return null;
    }

    private static Set<String> words(String path, String family) {
        String body = family == null ? path : path.equals(family) ? "" : path.substring(0,
                path.length() - family.length() - 1);
        Set<String> words = new HashSet<>();
        for (String word : body.split("[_/.-]+")) {
            if (!word.isEmpty() && !GENERIC.contains(word)) {
                words.add(word);
            }
        }
        return words;
    }

    private static boolean air(String id) {
        return id.equals(AIR) || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    /** Las propiedades de {@code properties} que existen en {@code id} con ese valor; las demás, a {@code dropped}. */
    static Map<String, String> keepValid(String id, Map<String, String> properties, BlockCatalog catalog,
                                         List<String> dropped) {
        Map<String, List<String>> valid = catalog.properties(id);
        Map<String, String> kept = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            List<String> values = valid.get(entry.getKey());
            if (values != null && values.contains(entry.getValue())) {
                kept.put(entry.getKey(), entry.getValue());
            } else if (dropped != null) {
                dropped.add(entry.getKey() + "=" + entry.getValue());
            }
        }
        return kept;
    }
}
