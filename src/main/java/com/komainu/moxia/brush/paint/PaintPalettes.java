package com.komainu.moxia.brush.paint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Paletas de serie del pincel de pintura, ordenadas del primer tono al último (de lo llano a lo empinado, de lo bajo
 * a lo alto): con el degradado por pendiente salen bien sin tocar nada. Cualquier otra paleta vale igual
 * ({@code ##Nombre}, una guardada con {@code #nombre} o una lista {@code stone,andesite}).
 */
public final class PaintPalettes {
    public static final String DEFAULT = "hierba_piedra";
    private static final Map<String, String> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, String> DESCRIPTIONS = new LinkedHashMap<>();

    static {
        add("hierba_piedra", "Hierba en lo llano y piedra en lo empinado",
                "grass_block,moss_block,coarse_dirt,gravel,andesite,stone");
        add("bosque", "Hierba y podzol, tierra y piedra musgosa en las cuestas",
                "grass_block,podzol,coarse_dirt,rooted_dirt,mossy_cobblestone,tuff");
        add("musgo", "Musgo que acaba en piedra",
                "grass_block,moss_block,mossy_cobblestone,cobblestone,stone");
        add("nevado", "Nieve en lo llano y roca gris en las paredes",
                "snow_block,calcite,diorite,andesite,stone,tuff");
        add("cumbres", "Para pintar por altura: hierba al pie, roca y nieve en lo alto",
                "grass_block,coarse_dirt,stone,andesite,calcite,snow_block");
        add("costa", "De la arena de la orilla a la hierba",
                "sand,gravel,coarse_dirt,grass_block");
        add("desierto", "Arena y areniscas, hasta la terracota",
                "sand,sandstone,red_sand,red_sandstone,terracotta");
        add("mesa", "Estratos de terracota",
                "red_sand,orange_terracotta,yellow_terracotta,terracotta,white_terracotta,brown_terracotta,"
                        + "red_terracotta");
        add("volcanico", "Toba, basalto y piedra negra",
                "tuff,cobbled_deepslate,smooth_basalt,blackstone,basalt");
    }

    private PaintPalettes() {
    }

    private static void add(String name, String description, String blocks) {
        BLOCKS.put(name, blocks);
        DESCRIPTIONS.put(name, description);
    }

    /** Nombres de serie con su descripción, en orden. */
    public static Map<String, String> descriptions() {
        return Collections.unmodifiableMap(DESCRIPTIONS);
    }

    /** Bloques de una paleta de serie, o {@code null} si el nombre no es de serie. */
    public static String blocks(String name) {
        return name == null ? null : BLOCKS.get(name.strip().toLowerCase(Locale.ROOT));
    }

    /** Lo que hay que resolver: los bloques si es de serie, o lo escrito tal cual. */
    public static String expand(String input) {
        String blocks = blocks(input);
        return blocks != null ? blocks : input;
    }
}
