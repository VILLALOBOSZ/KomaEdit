package com.komainu.moxia.image;

import com.komainu.moxia.glass.GlassColors;
import com.komainu.moxia.shape.smooth.SmoothMaterials;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paletas de serie de {@code /komaedit image}. Los colores son la media de la textura vanilla de cada bloque, la
 * misma tabla que eligen los smoothblocks ({@link SmoothMaterials#textureColors()}).
 */
public final class ImagePalettes {
    /** Los 16 tintes en el orden del juego. */
    public static final List<String> DYES = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    public enum Preset {
        GLASS("glass", "vidrio tintado", List.of("vidrio", "cristal", "stainedglass", "stained_glass")),
        PANES("panes", "paneles de vidrio tintado", List.of("paneles", "panel", "pane", "glasspanes")),
        WOOL("wool", "lana", List.of("lana")),
        CONCRETE("concrete", "hormigón", List.of("hormigon", "cemento")),
        TERRACOTTA("terracotta", "terracota", List.of("terracota", "arcilla")),
        SOLID("solid", "bloques enteros", List.of("solidos", "solido", "solids", "full", "enteros", "all", "todos"));

        private final String id;
        private final String displayName;
        private final List<String> aliases;

        Preset(String id, String displayName, List<String> aliases) {
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

        public List<String> aliases() {
            return aliases;
        }

        /** {@code null} si el nombre no es de una paleta de serie. */
        public static Preset find(String name) {
            String key = name.toLowerCase(Locale.ROOT).replace("ó", "o");
            for (Preset preset : values()) {
                if (preset.id.equals(key) || preset.aliases.contains(key)) {
                    return preset;
                }
            }
            return null;
        }
    }

    private ImagePalettes() {
    }

    public static BlockPalette preset(Preset preset) {
        return new BlockPalette(entries(ids(preset), SmoothMaterials.textureColors()));
    }

    /** Bloques de la paleta, en orden. */
    public static List<String> ids(Preset preset) {
        List<String> ids = new ArrayList<>();
        switch (preset) {
            case GLASS -> DYES.forEach(dye -> ids.add("minecraft:" + dye + "_stained_glass"));
            case PANES -> DYES.forEach(dye -> ids.add("minecraft:" + dye + "_stained_glass_pane"));
            case WOOL -> DYES.forEach(dye -> ids.add("minecraft:" + dye + "_wool"));
            case CONCRETE -> DYES.forEach(dye -> ids.add("minecraft:" + dye + "_concrete"));
            case TERRACOTTA -> {
                ids.add("minecraft:terracotta");
                DYES.forEach(dye -> ids.add("minecraft:" + dye + "_terracotta"));
            }
            case SOLID -> {
                // Opacos con la misma textura en todas las caras (los fondos de la vidriera degradada), sin el coral
                // vivo, que fuera del agua muere y cambia de color.
                for (GlassColors.Swatch swatch : GlassColors.backgrounds()) {
                    String id = swatch.blockId();
                    if (id.endsWith("_coral_block") && !id.startsWith("minecraft:dead_")) {
                        continue;
                    }
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /** Las entradas con color conocido; las que no están en la tabla se descartan. */
    public static List<BlockPalette.Entry> entries(List<String> ids, Map<String, Integer> colors) {
        List<BlockPalette.Entry> entries = new ArrayList<>(ids.size());
        for (String id : ids) {
            Integer color = colors.get(id);
            if (color != null) {
                entries.add(new BlockPalette.Entry(id, color));
            }
        }
        return entries;
    }

    /** ¿Son paneles (necesitan conexiones y solo valen en vertical)? */
    public static boolean isPane(String blockId) {
        return blockId.endsWith("_pane") || blockId.equals("minecraft:iron_bars");
    }
}
