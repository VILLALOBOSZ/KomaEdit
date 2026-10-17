package com.komainu.moxia.gradient;

import java.util.Arrays;
import java.util.Locale;

public enum GradientPalette {
    GRAYSCALE("grayscale", "GrayScale",
            "black_concrete;polished_blackstone;blackstone;deepslate_tiles;deepslate_bricks;deepslate;gray_concrete;tuff;andesite;stone;clay;light_gray_concrete;diorite;calcite;white_concrete",
            "grises", "gris", "gray"),
    GRAY_ROUGH("grayrough", "GrayRough",
            "black_concrete;polished_blackstone_bricks;blackstone;basalt;polished_basalt;cobbled_deepslate;deepslate_tiles;deepslate_bricks;tuff_bricks;tuff;mud_bricks;gravel;cobblestone;andesite;calcite;diorite;white_concrete",
            "roughgray", "gris_rugoso"),
    GRAY_WARM("graywarm", "GrayWarm",
            "black_concrete;blackstone;deepslate_bricks;mud;packed_mud;mud_bricks;brown_terracotta;gray_terracotta;dripstone_block;granite;polished_granite;stone;calcite;smooth_quartz;white_concrete",
            "warmgray", "gris_calido"),
    GRAY_COLD("graycold", "GrayCold",
            "black_concrete;polished_blackstone;deepslate_tiles;deepslate;gray_concrete;cyan_terracotta;prismarine;clay;light_blue_terracotta;packed_ice;calcite;diorite;sea_lantern;white_concrete",
            "coldgray", "gris_frio"),
    BLUE_WHITE_RED("bluewhitered", "BlueWhiteRed",
            "blue_concrete;lapis_block;blue_terracotta;light_blue_concrete;packed_ice;blue_ice;white_concrete;calcite;pink_concrete;magenta_concrete;red_concrete;redstone_block",
            "bwr", "azulblancorojo"),
    INFRARED("infrared", "Infrared",
            "black_concrete;crying_obsidian;purple_concrete;magenta_concrete;red_terracotta;red_concrete;orange_terracotta;orange_concrete;shroomlight;honeycomb_block;yellow_concrete;gold_block;ochre_froglight;white_concrete",
            "infrarrojo"),
    MAGMA("magma", "Magma",
            "black_concrete;blackstone;polished_blackstone_bricks;nether_bricks;red_nether_bricks;crimson_hyphae;magma_block;red_concrete;orange_concrete;shroomlight;glowstone;yellow_concrete;gold_block",
            "lava", "inferno"),
    GLOW_RED("glowred", "GlowRed",
            "black_concrete;blackstone;nether_bricks;red_nether_bricks;red_terracotta;red_concrete;redstone_block;shroomlight;pink_concrete;pearlescent_froglight;white_concrete",
            "brillo_rojo"),
    GLOW_ORANGE("gloworange", "GlowOrange",
            "black_concrete;blackstone;brown_terracotta;mud_bricks;orange_terracotta;orange_concrete;shroomlight;honeycomb_block;glowstone;ochre_froglight;white_concrete",
            "brillo_naranja"),
    GLOW_YELLOW("glowyellow", "GlowYellow",
            "black_concrete;blackstone;brown_terracotta;dripstone_block;yellow_terracotta;yellow_concrete;honeycomb_block;glowstone;gold_block;ochre_froglight;white_concrete",
            "brillo_amarillo"),
    GLOW_LIME("glowlime", "GlowLime",
            "black_concrete;deepslate;gray_concrete;green_terracotta;green_concrete;moss_block;lime_terracotta;lime_concrete;verdant_froglight;sea_lantern;white_concrete",
            "brillo_lima"),
    GLOW_CYAN("glowcyan", "GlowCyan",
            "black_concrete;deepslate;gray_concrete;warped_hyphae;prismarine_bricks;cyan_terracotta;cyan_concrete;prismarine;sea_lantern;verdant_froglight;white_concrete",
            "brillo_cian"),
    GLOW_BLUE("glowblue", "GlowBlue",
            "black_concrete;obsidian;crying_obsidian;blue_terracotta;blue_concrete;lapis_block;light_blue_concrete;packed_ice;blue_ice;sea_lantern;white_concrete",
            "brillo_azul"),
    GLOW_PURPLE("glowpurple", "GlowPurple",
            "black_concrete;obsidian;crying_obsidian;purple_terracotta;purple_concrete;amethyst_block;magenta_terracotta;magenta_concrete;pink_concrete;pearlescent_froglight;white_concrete",
            "brillo_morado"),
    GLOW_MAGENTA("glowmagenta", "GlowMagenta",
            "black_concrete;obsidian;nether_bricks;purple_terracotta;magenta_terracotta;magenta_concrete;amethyst_block;pink_concrete;pink_wool;pearlescent_froglight;white_concrete",
            "brillo_magenta"),
    FADE_RED("fadered", "FadeRed",
            "black_concrete;blackstone;nether_bricks;red_nether_bricks;red_terracotta;red_concrete;redstone_block;shroomlight",
            "desvanece_rojo"),
    FADE_ORANGE("fadeorange", "FadeOrange",
            "black_concrete;blackstone;brown_terracotta;mud_bricks;orange_terracotta;orange_concrete;shroomlight;honeycomb_block",
            "desvanece_naranja"),
    FADE_GOLD("fadegold", "FadeGold",
            "black_concrete;blackstone;brown_terracotta;dripstone_block;yellow_terracotta;yellow_concrete;honeycomb_block;gold_block",
            "desvanece_dorado"),
    FADE_LIME("fadelime", "FadeLime",
            "black_concrete;deepslate;gray_concrete;green_terracotta;green_concrete;moss_block;lime_terracotta;lime_concrete",
            "desvanece_lima"),
    FADE_CYAN("fadecyan", "FadeCyan",
            "black_concrete;deepslate;gray_concrete;warped_hyphae;cyan_terracotta;prismarine_bricks;cyan_concrete;prismarine",
            "desvanece_cian"),
    FADE_BLUE("fadeblue", "FadeBlue",
            "black_concrete;obsidian;crying_obsidian;blue_terracotta;blue_concrete;lapis_block;light_blue_concrete;packed_ice",
            "desvanece_azul"),
    FADE_PURPLE("fadepurple", "FadePurple",
            "black_concrete;obsidian;crying_obsidian;purple_terracotta;purple_concrete;amethyst_block;magenta_terracotta;magenta_concrete",
            "desvanece_morado"),
    FADE_MAGENTA("fademagenta", "FadeMagenta",
            "black_concrete;obsidian;nether_bricks;purple_terracotta;magenta_terracotta;magenta_concrete;pink_terracotta;pink_concrete",
            "desvanece_magenta");

    private final String id;
    private final String displayName;
    private final String patterns;
    private final String[] aliases;

    GradientPalette(String id, String displayName, String patterns, String... aliases) {
        this.id = id;
        this.displayName = displayName;
        this.patterns = patterns;
        this.aliases = aliases;
    }

    public String id() {
        return id;
    }

    /** Otros nombres que acepta {@link #byId(String)}. */
    public java.util.List<String> aliases() {
        return java.util.List.of(aliases);
    }

    public String displayName() {
        return displayName;
    }

    public String patterns() {
        return patterns;
    }

    public static GradientPalette byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(palette -> palette.id.equals(normalized)
                        || Arrays.asList(palette.aliases).contains(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Paleta profesional desconocida: " + input));
    }
}
