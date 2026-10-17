package com.komainu.moxia.region;

import java.util.Locale;
import java.util.Set;

/**
 * Reglas de los retoques de supervivencia (como {@code //eztweak} de ezEdits), sin depender de Minecraft para
 * poder probarlas: trabajan con ids de bloque.
 */
public final class TweakRules {
    private static final String NAMESPACE = "minecraft:";
    /** Bloques con «copper» en el nombre que no tienen versión encerada. */
    private static final Set<String> UNWAXABLE = Set.of(
            "copper_ore", "deepslate_copper_ore", "raw_copper_block", "lightning_rod"
    );

    private TweakRules() {
    }

    /** {@code minecraft:infested_stone_bricks} → {@code minecraft:stone_bricks}; {@code null} si no está infestado. */
    public static String deinfested(String blockId) {
        String path = path(blockId);
        if (!path.startsWith("infested_")) {
            return null;
        }
        return NAMESPACE + path.substring("infested_".length());
    }

    /**
     * Versión encerada o desencerada de un bloque de cobre; {@code null} si no cambia (no es de cobre, ya está en
     * ese estado o no tiene variante encerada).
     */
    public static String waxed(String blockId, boolean wax) {
        String path = path(blockId);
        if (!path.contains("copper") || UNWAXABLE.contains(path)) {
            return null;
        }
        boolean isWaxed = path.startsWith("waxed_");
        if (wax == isWaxed) {
            return null;
        }
        return NAMESPACE + (wax ? "waxed_" + path : path.substring("waxed_".length()));
    }

    public static boolean isLeaves(String blockId) {
        return path(blockId).endsWith("_leaves");
    }

    private static String path(String blockId) {
        String id = blockId.toLowerCase(Locale.ROOT);
        return id.startsWith(NAMESPACE) ? id.substring(NAMESPACE.length()) : id;
    }

    public enum Tweak {
        DEINFEST("deinfest", "Quitar infestación", "desinfestar"),
        PERSIST("persist", "Hojas persistentes", "persistir", "hojas"),
        WATERLOG("waterlog", "Anegar", "anegar", "agua"),
        WAX("wax", "Encerar cobre", "encerar", "cera");

        private final String id;
        private final String displayName;
        private final String[] aliases;

        Tweak(String id, String displayName, String... aliases) {
            this.id = id;
            this.displayName = displayName;
            this.aliases = aliases;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static Tweak byId(String input) {
            String normalized = input.toLowerCase(Locale.ROOT);
            for (Tweak tweak : values()) {
                if (tweak.id.equals(normalized) || Set.of(tweak.aliases).contains(normalized)) {
                    return tweak;
                }
            }
            throw new IllegalArgumentException("Retoque desconocido: " + input + " (deinfest, persist, waterlog, wax)");
        }
    }

    /** {@code on}/{@code off} y sus equivalentes; por defecto, activar. */
    public static boolean enabled(String input) {
        if (input == null || input.isBlank()) {
            return true;
        }
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "on", "true", "si", "sí", "yes", "activar", "1" -> true;
            case "off", "false", "no", "desactivar", "0" -> false;
            default -> throw new IllegalArgumentException("Usa on u off, no «" + input + "»");
        };
    }
}
