package com.komainu.moxia.weathering;

import java.util.Locale;

/** Clima en el que ha envejecido la ruina: decide el musgo, la vida que crece y de qué está hecho el escombro. */
public enum WeatherStyle {
    /** Musgo, hierba, helechos, enredaderas y telarañas. */
    TEMPERATE("templado", "temperate", 1.0),
    /** Sin musgo: arena acumulada contra los muros, madera blanqueada por el sol, terracota y arbustos secos. */
    DESERT("desierto", "desert", 0.0),
    /** Nieve sobre lo que mira al cielo, más grietas por la helada, hielo en los charcos. */
    SNOWY("nevado", "snowy", 0.35),
    /** Ruina sumergida: el aire de la selección se vuelve agua, con algas, kelp y pepinos de mar. */
    SUNKEN("hundido", "sunken", 1.35);

    private final String id;
    private final String alias;
    private final double moss;

    WeatherStyle(String id, String alias, double moss) {
        this.id = id;
        this.alias = alias;
        this.moss = moss;
    }

    public String id() {
        return id;
    }

    /** Cuánto musgo prende en este clima (1 = templado). */
    public double moss() {
        return moss;
    }

    public static WeatherStyle byId(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        for (WeatherStyle style : values()) {
            if (style.id.equals(key) || style.alias.equals(key)) {
                return style;
            }
        }
        throw new IllegalArgumentException("Clima desconocido «" + value + "»: templado, desierto, nevado o hundido");
    }

    public static boolean isStyle(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        for (WeatherStyle style : values()) {
            if (style.id.equals(key) || style.alias.equals(key)) {
                return true;
            }
        }
        return false;
    }
}
