package com.komainu.moxia.analysis;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lo que cuesta en survival cada bloque colocado, sin Minecraft: trabaja con ids y propiedades. Quien la usa dice
 * qué bloques y objetos existen ({@link Registry}).
 *
 * <ul>
 *     <li>Losas, escaleras y muros se pasan a su bloque base: una losa es medio bloque (una doble, uno entero), un
 *     muro es un bloque y una escalera es un bloque con el cortapiedras o bloque y medio si es de madera o de
 *     mosaico de bambú, que solo se hacen en la mesa de trabajo (6 → 4).</li>
 *     <li>Puertas, plantas altas y camas cuentan una vez (la mitad de arriba y la cabecera no).</li>
 *     <li>Capas de nieve, velas, pepinos de mar, huevos de tortuga y pétalos cuentan cada unidad.</li>
 *     <li>Bloques que no son objeto (antorchas de pared, cables, cultivos, macetas con planta…) cuentan el objeto
 *     con el que se colocan.</li>
 *     <li>Aire, líquidos, fuego, portales y piezas técnicas no cuestan nada.</li>
 * </ul>
 */
public final class SurvivalCost {
    private static final String NAMESPACE = "minecraft:";
    private static final Set<String> FREE = Set.of(
            "air", "cave_air", "void_air", "water", "lava", "fire", "soul_fire", "nether_portal", "end_portal",
            "end_gateway", "piston_head", "moving_piston", "bubble_column", "frosted_ice"
    );
    private static final Map<String, String> ITEMS = Map.ofEntries(
            Map.entry("redstone_wire", "redstone"),
            Map.entry("tripwire", "string"),
            Map.entry("carrots", "carrot"),
            Map.entry("potatoes", "potato"),
            Map.entry("beetroots", "beetroot_seeds"),
            Map.entry("wheat", "wheat_seeds"),
            Map.entry("cocoa", "cocoa_beans"),
            Map.entry("sweet_berry_bush", "sweet_berries"),
            Map.entry("pumpkin_stem", "pumpkin_seeds"),
            Map.entry("attached_pumpkin_stem", "pumpkin_seeds"),
            Map.entry("melon_stem", "melon_seeds"),
            Map.entry("attached_melon_stem", "melon_seeds"),
            Map.entry("kelp_plant", "kelp"),
            Map.entry("cave_vines", "glow_berries"),
            Map.entry("cave_vines_plant", "glow_berries"),
            Map.entry("twisting_vines_plant", "twisting_vines"),
            Map.entry("weeping_vines_plant", "weeping_vines"),
            Map.entry("big_dripleaf_stem", "big_dripleaf"),
            Map.entry("bamboo_sapling", "bamboo"),
            Map.entry("powder_snow", "powder_snow_bucket"),
            Map.entry("water_cauldron", "cauldron"),
            Map.entry("lava_cauldron", "cauldron"),
            Map.entry("powder_snow_cauldron", "cauldron"),
            Map.entry("tall_seagrass", "seagrass"),
            Map.entry("dirt_path", "dirt"),
            Map.entry("farmland", "dirt"),
            Map.entry("torchflower_crop", "torchflower_seeds"),
            Map.entry("pitcher_crop", "pitcher_pod"),
            Map.entry("wall_torch", "torch"),
            Map.entry("soul_wall_torch", "soul_torch"),
            Map.entry("redstone_wall_torch", "redstone_torch"),
            Map.entry("potted_azalea_bush", "azalea"),
            Map.entry("potted_flowering_azalea_bush", "flowering_azalea")
    );
    /** Propiedades que dicen cuántas unidades hay en un bloque. */
    private static final List<String> UNITS = List.of("layers", "candles", "pickles", "eggs", "flower_amount",
            "segment_amount");

    private SurvivalCost() {
    }

    /** Qué existe en el juego; en el servidor lo responden los registros. */
    public interface Registry {
        boolean isBlock(String id);

        boolean isItem(String id);

        /** Tamaño de pila del objeto (64, 16 o 1). */
        int maxStack(String itemId);
    }

    /** Una parte del coste: {@code amount} unidades del objeto {@code item} (puede ser fraccionaria, una losa). */
    public record Cost(String item, double amount) {
    }

    /**
     * Coste de un bloque colocado.
     *
     * @param blockId    {@code minecraft:oak_stairs}
     * @param properties propiedades como las da WorldEdit ({@code facing=north,half=bottom}) o vacío
     */
    public static List<Cost> of(String blockId, String properties, Registry registry) {
        String id = normalize(blockId);
        String namespace = id.substring(0, id.indexOf(':') + 1);
        String path = id.substring(namespace.length());
        Map<String, String> props = properties(properties);
        if (namespace.equals(NAMESPACE) && FREE.contains(path)) {
            return List.of();
        }
        if ("upper".equals(props.get("half")) || "head".equals(props.get("part"))) {
            return List.of();
        }
        double units = 1.0;
        for (String key : UNITS) {
            String value = props.get(key);
            if (value != null) {
                try {
                    units = Math.max(1, Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                    // Valor raro: cuenta uno.
                }
                break;
            }
        }
        if (path.endsWith("_slab")) {
            String base = base(namespace, path.substring(0, path.length() - "_slab".length()), registry);
            boolean doubled = "double".equals(props.get("type"));
            return base == null
                    ? List.of(new Cost(id, doubled ? 2.0 : 1.0))
                    : List.of(new Cost(base, doubled ? 1.0 : 0.5));
        }
        if (path.endsWith("_stairs")) {
            String base = base(namespace, path.substring(0, path.length() - "_stairs".length()), registry);
            return base == null ? List.of(new Cost(id, 1.0)) : List.of(new Cost(base, craftedOnly(base) ? 1.5 : 1.0));
        }
        if (path.endsWith("_wall")) {
            String base = base(namespace, path.substring(0, path.length() - "_wall".length()), registry);
            if (base != null) {
                return List.of(new Cost(base, 1.0));
            }
        }
        if (path.startsWith("potted_")) {
            String plant = ITEMS.getOrDefault(path, path.substring("potted_".length()));
            return List.of(new Cost(NAMESPACE + "flower_pot", 1.0), new Cost(namespace + plant, 1.0));
        }
        return List.of(new Cost(item(namespace, path, registry), units));
    }

    /** Objeto con el que se coloca un bloque. */
    static String item(String namespace, String path, Registry registry) {
        String mapped = namespace.equals(NAMESPACE) ? ITEMS.get(path) : null;
        if (mapped != null) {
            return namespace + mapped;
        }
        if (registry.isItem(namespace + path)) {
            return namespace + path;
        }
        // oak_wall_sign → oak_sign, white_wall_banner → white_banner, tube_coral_wall_fan → tube_coral_fan…
        int wall = path.indexOf("_wall_");
        if (wall >= 0) {
            String candidate = namespace + path.substring(0, wall) + path.substring(wall + "_wall".length());
            if (registry.isItem(candidate)) {
                return candidate;
            }
        }
        return namespace + path;
    }

    /**
     * Bloque base de una losa, escalera o muro: {@code oak} → {@code oak_planks}, {@code stone_brick} →
     * {@code stone_bricks}, {@code quartz} → {@code quartz_block}; {@code null} si no se encuentra.
     */
    static String base(String namespace, String stem, Registry registry) {
        String[] candidates = {
                stem + "_planks",
                stem.endsWith("brick") || stem.endsWith("tile") ? stem + "s" : null,
                stem + "_block",
                stem
        };
        for (String candidate : candidates) {
            if (candidate != null && registry.isBlock(namespace + candidate)) {
                return namespace + candidate;
            }
        }
        return null;
    }

    /** Las escaleras de madera y de mosaico de bambú no se hacen con el cortapiedras. */
    static boolean craftedOnly(String base) {
        return base.endsWith("_planks") || base.endsWith(":bamboo_mosaic");
    }

    static String normalize(String blockId) {
        String id = blockId.strip().toLowerCase(Locale.ROOT);
        return id.indexOf(':') < 0 ? NAMESPACE + id : id;
    }

    static Map<String, String> properties(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new java.util.HashMap<>();
        for (String pair : text.split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                result.put(pair.substring(0, equals).strip(), pair.substring(equals + 1).strip());
            }
        }
        return result;
    }
}
