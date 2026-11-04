package com.komainu.moxia.terrain;

import com.komainu.moxia.brush.ez.BrushOptions;
import com.komainu.moxia.terrain.stamp.StampCategory;
import com.komainu.moxia.terrain.stamp.StampInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * La orden {@code /komaedit terreno cordillera|costa|valle} ya leída, sin Minecraft:
 * {@code [ancho] [altura] [sumar|maximo|aplanar] [v|u|canon] [paleta|auto] [-p perfil] [-x variación] [-s semilla]
 * [-l lado] [-h sellos] [-r erosión] [-f agua|seco] [-m máscara] [-e]}. La lee igual el servidor y las pruebas de la
 * tarjeta del Studio.
 *
 * @param width     ancho total en bloques (el doble del medio ancho del planificador)
 * @param height    altura de las cumbres, del acantilado o hondura del valle
 * @param palette   paleta escrita, o {@link #AUTO}
 * @param variation de 0 a 1
 * @param seed      semilla escrita, o {@code null} para una al azar
 * @param erosion   de 0 a 1
 * @param stamps    {@link #AUTO}, {@link #NONE}, una categoría o sellos separados por comas
 */
public record LandformRequest(LandformPlanner.Kind kind, double width, double height, String palette,
                              LandformPlanner.Profile profile, double variation, Long seed,
                              LandformPlanner.Side side, LandformPlanner.Form form, LandformPlanner.Blend blend,
                              double erosion, boolean water, String stamps, String mask, boolean estimate) {
    public static final String AUTO = "auto";
    public static final String NONE = "ninguno";
    public static final Set<Character> SWITCHES = Set.of('e');
    public static final Set<Character> VALUED = Set.of('p', 'x', 's', 'l', 'h', 'r', 'f', 'm');
    public static final int MAX_WIDTH = (int) (LandformPlanner.MAX_HALF_WIDTH * 2.0);
    public static final int MIN_WIDTH = (int) (LandformPlanner.MIN_HALF_WIDTH * 2.0);
    /** Sellos de detalle que se usan como mucho a la vez. */
    public static final int MAX_STAMPS = 12;

    /** Ancho de serie de cada relieve. */
    public static int defaultWidth(LandformPlanner.Kind kind) {
        return switch (kind) {
            case RANGE -> 100;
            case COAST -> 70;
            case VALLEY -> 80;
        };
    }

    /** Altura (u hondura) de serie. */
    public static int defaultHeight(LandformPlanner.Kind kind) {
        return switch (kind) {
            case RANGE -> 40;
            case COAST -> 24;
            case VALLEY -> 24;
        };
    }

    /** Variación de serie, en %. */
    public static final int DEFAULT_VARIATION = 65;

    /** Erosión de serie, en %. */
    public static final int DEFAULT_EROSION = 40;

    public static LandformRequest parse(LandformPlanner.Kind kind, String text) {
        BrushOptions options = BrushOptions.parse(text, SWITCHES, VALUED);
        Double width = null;
        Double height = null;
        String palette = null;
        LandformPlanner.Blend blend = kind == LandformPlanner.Kind.VALLEY ? LandformPlanner.Blend.MAX
                : LandformPlanner.Blend.ADD;
        LandformPlanner.Form form = LandformPlanner.Form.V;
        String usage = kind.id() + " [ancho] [" + (kind == LandformPlanner.Kind.VALLEY ? "hondura" : "altura")
                + "] [sumar|maximo|aplanar]" + (kind == LandformPlanner.Kind.VALLEY ? " [v|u|canon]" : "")
                + " [paleta]";
        for (int index = 0; index < options.count(); index++) {
            String token = options.positional(index);
            if (BrushOptions.isNumber(token)) {
                if (width == null) {
                    width = options.decimal(index, defaultWidth(kind), MIN_WIDTH, MAX_WIDTH, "El ancho");
                } else if (height == null) {
                    height = options.decimal(index, defaultHeight(kind), 2.0, LandformPlanner.MAX_HEIGHT,
                            kind == LandformPlanner.Kind.VALLEY ? "La hondura" : "La altura");
                } else {
                    throw new IllegalArgumentException("Sobra el número " + token + " (" + usage + ")");
                }
            } else if (isBlend(token)) {
                blend = LandformPlanner.Blend.byId(token);
            } else if (kind == LandformPlanner.Kind.VALLEY && isForm(token)) {
                form = LandformPlanner.Form.byId(token);
            } else if (palette == null) {
                palette = token;
            } else {
                throw new IllegalArgumentException("No entiendo «" + token + "» (" + usage + ")");
            }
        }
        LandformPlanner.Profile profile = options.has('p') ? LandformPlanner.Profile.byId(options.flag('p'))
                : kind == LandformPlanner.Kind.RANGE ? LandformPlanner.Profile.BELL : LandformPlanner.Profile.FLAT;
        double variation = options.flagDecimal('x', DEFAULT_VARIATION, 0.0, 100.0, "La variación") / 100.0;
        Long seed = options.has('s') ? seed(options.flag('s')) : null;
        LandformPlanner.Side side = options.has('l') ? LandformPlanner.Side.byId(options.flag('l'))
                : LandformPlanner.Side.AUTO;
        double erosion = options.flagDecimal('r', DEFAULT_EROSION, 0.0, 100.0, "La erosión") / 100.0;
        boolean water = options.has('f') ? water(options.flag('f')) : kind == LandformPlanner.Kind.COAST;
        String stamps = options.has('h') ? options.flag('h').strip().toLowerCase(Locale.ROOT) : AUTO;
        return new LandformRequest(kind, width != null ? width : defaultWidth(kind),
                height != null ? height : defaultHeight(kind), palette == null ? AUTO : palette, profile, variation,
                seed, side, form, blend, erosion, water, stamps, options.has('m') ? options.flag('m') : null,
                options.has('e'));
    }

    private static boolean water(String text) {
        return switch (LandformPlanner.fold(text)) {
            case "agua", "water", "si", "mar", "rio" -> true;
            case "seco", "none", "no", "ninguno", "dry" -> false;
            default -> throw new IllegalArgumentException("Agua desconocida: " + text + " (agua o seco)");
        };
    }

    private static long seed(String text) {
        try {
            long value = Long.parseLong(text.strip());
            if (value < 0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("La semilla debe ser un número entero positivo: " + text);
        }
    }

    private static boolean isBlend(String token) {
        try {
            LandformPlanner.Blend.byId(token);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isForm(String token) {
        try {
            LandformPlanner.Form.byId(token);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /** La familia de sellos de serie de cada relieve (y sección del valle). */
    public StampCategory defaultFamily() {
        return switch (kind) {
            case RANGE -> StampCategory.RANGES;
            case COAST -> StampCategory.CLIFFS;
            case VALLEY -> form == LandformPlanner.Form.U ? StampCategory.GLACIAL : StampCategory.CANYONS;
        };
    }

    /** La categoría de los sellos pedidos, si se pidió una (o la de serie en automático). */
    public StampCategory family() {
        if (stamps.equals(AUTO)) {
            return defaultFamily();
        }
        try {
            return StampCategory.byId(stamps);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Los nombres de los sellos que ponen el detalle: los de la familia, los escritos (de serie o propios) o ninguno.
     */
    public List<String> stampNames() {
        if (stamps.equals(NONE) || stamps.equals("no") || stamps.equals("none")) {
            return List.of();
        }
        StampCategory family = family();
        if (family != null) {
            return StampLibrary.of(family).stream().map(StampInfo::name).toList();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String part : stamps.split(",")) {
            if (!part.isBlank()) {
                names.add(StampCatalog.normalize(part));
            }
        }
        if (names.size() > MAX_STAMPS) {
            throw new IllegalArgumentException("Como mucho " + MAX_STAMPS + " sellos a la vez");
        }
        return List.copyOf(names);
    }

    /** Carga los sellos con el catálogo (los propios y los de serie). */
    public List<HeightStamp> loadStamps(StampCatalog catalog) {
        List<HeightStamp> loaded = new ArrayList<>();
        for (String name : stampNames()) {
            try {
                loaded.add(catalog == null ? StampLibrary.get(name) : catalog.load(name));
            } catch (IOException exception) {
                throw new IllegalArgumentException("No se pudo leer el sello " + name + ": " + exception.getMessage(),
                        exception);
            }
        }
        return loaded;
    }

    /** La paleta que se usa: la escrita o, en automático, la de la familia de sellos o la de cada relieve. */
    public String paletteName() {
        if (!palette.equalsIgnoreCase(AUTO)) {
            return palette;
        }
        StampCategory family = stamps.equals(AUTO) ? null : family();
        if (family != null && family != StampCategory.DETAIL) {
            return family.palette();
        }
        return switch (kind) {
            case RANGE -> "alpino";
            case COAST -> "costa";
            case VALLEY -> switch (form) {
                case V -> "pradera";
                case U -> "glaciar";
                case CANYON -> "mesa";
            };
        };
    }

    public LandformPlanner.Settings settings(List<HeightStamp> details, long chosenSeed) {
        return new LandformPlanner.Settings(kind, width / 2.0, height, profile, variation, chosenSeed, side, form,
                blend, erosion, water, details);
    }

    /** Lo que se anuncia: «Cordillera de 100 de ancho y 40 de alto». */
    public String description() {
        return switch (kind) {
            case RANGE -> "Cordillera";
            case COAST -> "Costa";
            case VALLEY -> switch (form) {
                case V -> "Valle";
                case U -> "Valle glaciar";
                case CANYON -> "Cañón";
            };
        };
    }
}
