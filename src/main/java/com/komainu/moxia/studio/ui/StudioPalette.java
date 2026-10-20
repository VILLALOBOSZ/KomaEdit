package com.komainu.moxia.studio.ui;

import com.komainu.moxia.studio.ui.StudioTheme.Token;

import java.util.Locale;
import java.util.Map;

import static com.komainu.moxia.studio.ui.StudioTheme.Token.*;
import static java.util.Map.entry;

/**
 * Los temas de color del Studio: un color para cada ficha de {@link StudioTheme}. Todos siguen las mismas reglas
 * (las comprueba {@code StudioThemeContrastTest}):
 * <ul>
 *     <li>texto con contraste WCAG AA (4,5:1) sobre cada fondo en el que sale, y 3:1 en marcas y bordes de
 *     selección;</li>
 *     <li>el rojo solo para peligro; éxito en verde o turquesa y aviso en ámbar, los tres lejos del acento;</li>
 *     <li>superficies neutras y apagadas, para que al lado del mundo de Minecraft no compitan con él.</li>
 * </ul>
 * Los oscuros llevan un acento claro con el texto oscuro encima: un acento oscuro con texto blanco no se ve como marca
 * de selección sobre las tarjetas.
 */
public enum StudioPalette {
    /**
     * Pizarra, el de serie: grises azulados y acento azul lapislázuli. El azul no se confunde con ningún estado (rojo,
     * ámbar, verde), se aleja de los ocres, la madera y la hierba del mundo y es el que se espera en un editor.
     */
    SLATE("slate", Map.ofEntries(
            entry(PANEL, 0xFF12151B), entry(SURFACE, 0xFF181C24), entry(CARD, 0xFF1F2430),
            entry(CARD_HOVER, 0xFF2A3140), entry(CARD_ACTIVE, 0xFF1D2C48), entry(FIELD, 0xFF0C0F14), entry(THUMBNAIL, 0xFF3A4458),
            entry(DIVIDER, 0xFF2C3342), entry(TRACK, 0xFF323A4A), entry(OUTLINE, 0xFF5A6680), entry(FOCUS, 0xFF8DBAFF),
            entry(ACCENT, 0xFF6EA4F7), entry(ACCENT_HOVER, 0xFF88B5FA), entry(ACCENT_TEXT, 0xFF8DBAFF),
            entry(ACCENT_DIM, 0xFF2B4F8A), entry(ON_ACCENT, 0xFF0A1A33),
            entry(TEXT_STRONG, 0xFFF7F9FD), entry(TEXT, 0xFFE2E7F0), entry(TEXT_SECONDARY, 0xFFB0BACB),
            entry(MUTED, 0xFF939EB2), entry(DISABLED, 0xFF5F687A),
            entry(SUCCESS, 0xFF5ED69B), entry(WARNING, 0xFFF2B84B), entry(WARNING_DIM, 0xFF3D2F12),
            entry(WARNING_HOVER, 0xFF54411A), entry(DANGER, 0xFFFF7B7B), entry(DANGER_DIM, 0xFF46202A),
            entry(FAVORITE, 0xFFF5C84C), entry(SCRIM, 0xC0000000))),
    /** Prismarina: azul de mar profundo y acento turquesa, como los templos del océano. Éxito en verde. */
    PRISMARINE("prismarine", Map.ofEntries(
            entry(PANEL, 0xFF0D1718), entry(SURFACE, 0xFF122022), entry(CARD, 0xFF18292B),
            entry(CARD_HOVER, 0xFF223739), entry(CARD_ACTIVE, 0xFF123B39), entry(FIELD, 0xFF081112), entry(THUMBNAIL, 0xFF384A4D),
            entry(DIVIDER, 0xFF22383A), entry(TRACK, 0xFF284244), entry(OUTLINE, 0xFF4D7174), entry(FOCUS, 0xFF5EDCCB),
            entry(ACCENT, 0xFF2EC4B0), entry(ACCENT_HOVER, 0xFF48D3C0), entry(ACCENT_TEXT, 0xFF5EDCCB),
            entry(ACCENT_DIM, 0xFF17524C), entry(ON_ACCENT, 0xFF062420),
            entry(TEXT_STRONG, 0xFFF3FBFA), entry(TEXT, 0xFFDDEBEA), entry(TEXT_SECONDARY, 0xFFA7BFBE),
            entry(MUTED, 0xFF8AA5A4), entry(DISABLED, 0xFF55706F),
            entry(SUCCESS, 0xFF9AD873), entry(WARNING, 0xFFF2B84B), entry(WARNING_DIM, 0xFF3B2F12),
            entry(WARNING_HOVER, 0xFF52411A), entry(DANGER, 0xFFFF7B72), entry(DANGER_DIM, 0xFF45201F),
            entry(FAVORITE, 0xFFF5C84C), entry(SCRIM, 0xC0000000))),
    /** Bosque: verdes de musgo y abeto, y acento verde salvia. Éxito en turquesa, para no confundirse con él. */
    FOREST("forest", Map.ofEntries(
            entry(PANEL, 0xFF121713), entry(SURFACE, 0xFF171E18), entry(CARD, 0xFF1E2720),
            entry(CARD_HOVER, 0xFF28342A), entry(CARD_ACTIVE, 0xFF22371F), entry(FIELD, 0xFF0C100C), entry(THUMBNAIL, 0xFF454A46),
            entry(DIVIDER, 0xFF29352B), entry(TRACK, 0xFF303D31), entry(OUTLINE, 0xFF566A58), entry(FOCUS, 0xFFA9D48C),
            entry(ACCENT, 0xFF8DBE6E), entry(ACCENT_HOVER, 0xFF9DCB7F), entry(ACCENT_TEXT, 0xFFA9D48C),
            entry(ACCENT_DIM, 0xFF34502A), entry(ON_ACCENT, 0xFF122010),
            entry(TEXT_STRONG, 0xFFF5F8F1), entry(TEXT, 0xFFE2E8DC), entry(TEXT_SECONDARY, 0xFFB3BEAB),
            entry(MUTED, 0xFF96A38E), entry(DISABLED, 0xFF5E6A5A),
            entry(SUCCESS, 0xFF4FD1C1), entry(WARNING, 0xFFF0B64A), entry(WARNING_DIM, 0xFF3C2F12),
            entry(WARNING_HOVER, 0xFF53411A), entry(DANGER, 0xFFFF7A6E), entry(DANGER_DIM, 0xFF46201C),
            entry(FAVORITE, 0xFFF5C84C), entry(SCRIM, 0xC0000000))),
    /**
     * Clásico: claro, con la piedra gris y el texto oscuro del inventario, y el morado de los encantamientos y de los
     * bordes de los tooltips como acento. La madera queda fuera a propósito: es de la familia del ámbar del aviso.
     */
    CLASSIC("classic", Map.ofEntries(
            entry(PANEL, 0xFFD0D0D0), entry(SURFACE, 0xFFC4C4C4), entry(CARD, 0xFFE0E0E0),
            entry(CARD_HOVER, 0xFFEFEFEF), entry(CARD_ACTIVE, 0xFFE4DCF4), entry(FIELD, 0xFFF6F6F6), entry(THUMBNAIL, 0xFFF3F3F3),
            entry(DIVIDER, 0xFFA6A6A6), entry(TRACK, 0xFFADADAD), entry(OUTLINE, 0xFF3A3A3A), entry(FOCUS, 0xFF5634A6),
            entry(ACCENT, 0xFF6A45BE), entry(ACCENT_HOVER, 0xFF7752C9), entry(ACCENT_TEXT, 0xFF5634A6),
            entry(ACCENT_DIM, 0xFFC9B8EC), entry(ON_ACCENT, 0xFFFFFFFF),
            entry(TEXT_STRONG, 0xFF111111), entry(TEXT, 0xFF222222), entry(TEXT_SECONDARY, 0xFF383838),
            entry(MUTED, 0xFF505050), entry(DISABLED, 0xFF7A7A7A),
            entry(SUCCESS, 0xFF155A24), entry(WARNING, 0xFF6E4700), entry(WARNING_DIM, 0xFFF1DEAF),
            entry(WARNING_HOVER, 0xFFE9CF8E), entry(DANGER, 0xFF921A1A), entry(DANGER_DIM, 0xFFF2D2D2),
            entry(FAVORITE, 0xFF634800), entry(SCRIM, 0xA0000000)));

    /** El tema con el que arranca el Studio mientras el jugador no elija otro. */
    public static final StudioPalette DEFAULT = SLATE;

    private final String id;
    private final int[] colors;

    StudioPalette(String id, Map<Token, Integer> colors) {
        this.id = id;
        this.colors = new int[Token.values().length];
        for (Token token : Token.values()) {
            Integer color = colors.get(token);
            if (color == null) {
                throw new IllegalStateException("Al tema " + id + " le falta la ficha " + token);
            }
            this.colors[token.ordinal()] = color;
        }
    }

    /** El nombre con el que se guarda en {@code studio.json}. */
    public String id() {
        return id;
    }

    public String translationKey() {
        return "gui.komaedit.theme." + id;
    }

    public int color(Token token) {
        return colors[token.ordinal()];
    }

    public StudioPalette next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** El tema con ese nombre, o el de serie si no hay ninguno (vacío, de otra versión o mal escrito). */
    public static StudioPalette byId(String id) {
        if (id != null) {
            String wanted = id.strip().toLowerCase(Locale.ROOT);
            for (StudioPalette palette : values()) {
                if (palette.id.equals(wanted)) {
                    return palette;
                }
            }
        }
        return DEFAULT;
    }
}
