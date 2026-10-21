package com.komainu.moxia.display;

import java.util.Locale;

/**
 * Los ajustes avanzados de un display, todos a la vez (los que manda «Aplicar ajustes» del Studio):
 *
 * <pre>brillo=auto|0-15 sombra=0-64 fuerza=0-1 vista=0.05-16 billboard=fixed|vertical|horizontal|center
 * contorno=on|off color=#RRGGBB</pre>
 *
 * Lo que no se escribe queda como en Minecraft de serie. Lógica pura.
 *
 * @param brightness brillo fijo de bloque y de cielo, o {@code null} para el de su sitio
 * @param glowColor  color del contorno, o −1 para el de serie (blanco)
 */
public record DisplayLook(Integer brightness, float shadowRadius, float shadowStrength, float viewRange,
                          DisplayPose.Billboard billboard, boolean glow, int glowColor) {
    public static final DisplayLook DEFAULT = new DisplayLook(null, 0.0F, 1.0F, 1.0F, DisplayPose.Billboard.FIXED,
            false, -1);

    public DisplayLook {
        if (brightness != null && (brightness < 0 || brightness > 15)) {
            throw new IllegalArgumentException("El brillo va de 0 a 15 (o auto)");
        }
        if (!(shadowRadius >= 0.0F && shadowRadius <= 64.0F)) {
            throw new IllegalArgumentException("La sombra va de 0 a 64 bloques");
        }
        if (!(shadowStrength >= 0.0F && shadowStrength <= 1.0F)) {
            throw new IllegalArgumentException("La fuerza de la sombra va de 0 a 1");
        }
        if (!(viewRange >= 0.05F && viewRange <= 16.0F)) {
            throw new IllegalArgumentException("La distancia de vista va de 0.05 a 16");
        }
        if (billboard == null) {
            throw new IllegalArgumentException("Falta el billboard");
        }
    }

    public static DisplayLook parse(String text) {
        Integer brightness = DEFAULT.brightness;
        float shadow = DEFAULT.shadowRadius;
        float strength = DEFAULT.shadowStrength;
        float view = DEFAULT.viewRange;
        DisplayPose.Billboard billboard = DEFAULT.billboard;
        boolean glow = DEFAULT.glow;
        int color = DEFAULT.glowColor;
        for (String token : (text == null ? "" : text.strip()).split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int equals = token.indexOf('=');
            if (equals <= 0 || equals == token.length() - 1) {
                throw new IllegalArgumentException("Ajuste no válido: " + token + " (clave=valor)");
            }
            String key = token.substring(0, equals).toLowerCase(Locale.ROOT);
            String value = token.substring(equals + 1).toLowerCase(Locale.ROOT);
            try {
                switch (key) {
                    case "brillo", "brightness" -> brightness = value.equals("auto") ? null : Integer.parseInt(value);
                    case "sombra", "shadow" -> shadow = Float.parseFloat(value);
                    case "fuerza", "strength" -> strength = Float.parseFloat(value);
                    case "vista", "distancia", "view", "viewrange" -> view = Float.parseFloat(value);
                    case "billboard" -> billboard = DisplayPose.Billboard.parse(value);
                    case "contorno", "glow" -> glow = switch (value) {
                        case "on", "si", "sí", "true", "1" -> true;
                        case "off", "no", "false", "0" -> false;
                        default -> throw new IllegalArgumentException("Contorno: on u off, no " + value);
                    };
                    case "color" -> color = value.equals("auto") || value.equals("serie") ? -1
                            : DisplayText.color(value);
                    default -> throw new IllegalArgumentException("Ajuste desconocido: " + key
                            + " (brillo, sombra, fuerza, vista, billboard, contorno o color)");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Número no válido en " + token);
            }
        }
        return new DisplayLook(brightness, shadow, strength, view, billboard, glow, color);
    }

    /** La forma que lee {@link #parse}. */
    public String format() {
        StringBuilder text = new StringBuilder()
                .append("brillo=").append(brightness == null ? "auto" : brightness.toString())
                .append(" sombra=").append(number(shadowRadius))
                .append(" fuerza=").append(number(shadowStrength))
                .append(" vista=").append(number(viewRange))
                .append(" billboard=").append(billboard.id())
                .append(" contorno=").append(glow ? "on" : "off");
        if (glowColor >= 0) {
            text.append(" color=#").append(String.format(Locale.ROOT, "%06x", glowColor & 0xFFFFFF));
        }
        return text.toString();
    }

    private static String number(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Float.toString(value);
    }
}
