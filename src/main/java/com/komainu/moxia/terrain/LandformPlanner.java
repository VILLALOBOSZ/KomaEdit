package com.komainu.moxia.terrain;

import com.komainu.moxia.terrain.stamp.StampNoise;
import com.komainu.moxia.terrain.stamp.StampPlacement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Relieve a lo largo de una trayectoria: una cordillera, una costa con acantilado o un valle que siguen la curva.
 *
 * <p>Nada se estampa por trozos. La forma grande es un solo campo continuo: la distancia exacta a los tramos de la
 * curva (no a sus puntos, que dejarían muescas) con alturas y anchos que cambian suave a lo largo del recorrido. Lo
 * que varía a lo largo sale de ruido en una dimensión (cerrado sobre sí mismo si la trayectoria es cerrada) y lo que
 * varía de un sitio a otro, de ruido en coordenadas del mundo, así no hay costuras ni en las curvas cerradas, donde
 * dos partes del recorrido se tocan.</p>
 *
 * <ul>
 *     <li><b>Cordillera</b>: una cresta que serpentea alrededor de la curva con cumbres y collados, ramales que bajan
 *     a los lados (con valles entre ellos) y un pie ancho y suave que se funde con el terreno; baja al acercarse a los
 *     extremos.</li>
 *     <li><b>Costa</b>: tierra alta con acantilado a un lado de la línea, con calas y cabos, un canchal al pie,
 *     farallones en el agua y el fondo del mar al otro lado; el mar se llena solo si no se escapa.</li>
 *     <li><b>Valle</b>: un cauce en V, en U o en cañón escalonado que siempre baja en el sentido del agua, con valles
 *     laterales que llegan colgados.</li>
 * </ul>
 *
 * <p>Los sellos de la biblioteca ponen el detalle: de cada uno solo se toma lo fino (el relieve menos su versión
 * desenfocada: cárcavas, crestas, derrubios), mezclado entre sellos vecinos con pesos suaves, así que su forma redonda
 * nunca se ve. Después, si se pide, la erosión del terraformado y la paleta por pendiente.</p>
 *
 * <p>Es Java puro sobre un {@link HeightField}: el juego le da la foto y las pruebas, un mundo de texto.</p>
 */
public final class LandformPlanner {
    /** Separación de los puntos del recorrido, en bloques. */
    static final double SPACING = 1.0;
    /** Largo de los tramos con los que se miden las distancias. */
    private static final double SEGMENT = 2.0;
    public static final double MIN_HALF_WIDTH = 4.0;
    public static final double MAX_HALF_WIDTH = 128.0;
    public static final double MAX_HEIGHT = 160.0;
    /**
     * La versión desenfocada de cada sello de detalle, mientras se use el sello. El valor no guarda el sello: si lo
     * guardara, el {@code WeakHashMap} nunca podría soltarlo (cada sello recapturado o importado que se usara de detalle
     * se quedaba en memoria, con su copia desenfocada, hasta cerrar el juego).
     */
    private static final Map<HeightStamp, Blur> DETAIL = Collections.synchronizedMap(new WeakHashMap<>());

    private LandformPlanner() {
    }

    public enum Kind {
        RANGE("cordillera", "Cordillera"),
        COAST("costa", "Costa"),
        VALLEY("valle", "Valle");

        private final String id;
        private final String displayName;

        Kind(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id() {
            return id;
        }

        public String displayName() {
            return displayName;
        }

        public static Kind byId(String input) {
            return switch (fold(input)) {
                case "cordillera", "range", "sierra", "mountains", "montanas" -> RANGE;
                case "costa", "coast", "acantilado", "cliff", "litoral" -> COAST;
                case "valle", "valley", "canon", "canyon", "garganta" -> VALLEY;
                default -> throw new IllegalArgumentException("Relieve desconocido: " + input
                        + " (cordillera, costa o valle)");
            };
        }
    }

    /** Cómo cambia la altura (o la hondura) a lo largo del recorrido. */
    public enum Profile {
        /** Sube desde los extremos y es más alta por el medio. */
        BELL("campana"),
        /** Igual en todo el recorrido; solo se redondean los extremos. */
        FLAT("plano"),
        /** Baja al principio y crece hacia el final. */
        RISING("sube"),
        /** Alta al principio y baja hacia el final. */
        FALLING("baja");

        private final String id;

        Profile(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Profile byId(String input) {
            return switch (fold(input)) {
                case "campana", "bell", "medio", "centro" -> BELL;
                case "plano", "flat", "igual", "constante" -> FLAT;
                case "sube", "rising", "creciente", "up", "subir" -> RISING;
                case "baja", "falling", "decreciente", "down", "bajar" -> FALLING;
                default -> throw new IllegalArgumentException("Perfil desconocido: " + input
                        + " (campana, plano, sube o baja)");
            };
        }
    }

    /**
     * A qué lado de la línea queda la tierra de la costa, mirando en el sentido de los puntos. En automático, donde el
     * terreno ya es más alto (o, si es llano, a la derecha).
     */
    public enum Side {
        AUTO("auto"),
        LEFT("izquierda"),
        RIGHT("derecha");

        private final String id;

        Side(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Side byId(String input) {
            return switch (fold(input)) {
                case "auto", "automatico", "solo" -> AUTO;
                case "izquierda", "izq", "left", "l" -> LEFT;
                case "derecha", "der", "right", "r" -> RIGHT;
                default -> throw new IllegalArgumentException("Lado desconocido: " + input
                        + " (auto, izquierda o derecha)");
            };
        }
    }

    /** Sección del valle. */
    public enum Form {
        /** De río: laderas de pendiente seguida y fondo estrecho. */
        V("v"),
        /** Glaciar: fondo ancho y paredes empinadas con hombros redondos. */
        U("u"),
        /** Cañón: paredes a escalones como estratos. */
        CANYON("canon");

        private final String id;

        Form(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Form byId(String input) {
            return switch (fold(input)) {
                case "v", "rio", "fluvial" -> V;
                case "u", "glaciar", "glacial" -> U;
                case "canon", "canyon", "escalones", "estratos" -> CANYON;
                default -> throw new IllegalArgumentException("Sección de valle desconocida: " + input
                        + " (v, u o canon)");
            };
        }
    }

    /** Cómo se junta con el terreno: los modos del pincel de relieve. */
    public enum Blend {
        /** Suma la forma al terreno: sigue sus subidas y bajadas. */
        ADD("sumar"),
        /** Sube hasta la forma sin hundir lo que ya está más alto (en lo que hunde, baja sin rellenar lo hondo). */
        MAX("maximo"),
        /** Deja el terreno a la altura de la forma, suba o baje. */
        FLATTEN("aplanar");

        private final String id;

        Blend(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Blend byId(String input) {
            return switch (fold(input)) {
                case "sumar", "add", "raise", "elevar", "subir" -> ADD;
                case "maximo", "max", "maximum", "tope" -> MAX;
                case "aplanar", "flatten", "flat", "llano", "ajustar" -> FLATTEN;
                default -> throw new IllegalArgumentException("Modo desconocido: " + input
                        + " (sumar, maximo o aplanar)");
            };
        }
    }

    /**
     * @param halfWidth de la línea al pie (cordillera y valle) o hasta donde llega la tierra alta y el fondo del mar
     * @param height    altura de las cumbres, del acantilado o hondura del valle, en bloques
     * @param variation de 0 (regular) a 1: cumbres y collados, serpenteo, ramales, calas y detalle
     * @param seed      la misma semilla y el mismo recorrido dan siempre lo mismo
     * @param erosion   de 0 (sin) a 1: gotas por columna de la erosión hidráulica al final
     * @param water     en la costa, llena el mar; en el valle, un río por el fondo
     * @param stamps    sellos de los que sale el detalle (vacío: solo ruido)
     */
    public record Settings(Kind kind, double halfWidth, double height, Profile profile, double variation, long seed,
                           Side side, Form form, Blend blend, double erosion, boolean water,
                           List<HeightStamp> stamps) {
        public Settings {
            if (kind == null || profile == null || side == null || form == null || blend == null) {
                throw new IllegalArgumentException("Faltan ajustes del relieve");
            }
            check(halfWidth, MIN_HALF_WIDTH, MAX_HALF_WIDTH, "El medio ancho");
            check(height, 2.0, MAX_HEIGHT, "La altura");
            check(variation, 0.0, 1.0, "La variación");
            check(erosion, 0.0, 1.0, "La erosión");
            stamps = List.copyOf(stamps);
        }

        private static void check(double value, double minimum, double maximum, String name) {
            if (Double.isNaN(value) || value < minimum || value > maximum) {
                throw new IllegalArgumentException(name + " debe estar entre " + format(minimum) + " y "
                        + format(maximum));
            }
        }

        /** Hasta dónde llega desde la línea, con lo que serpentea y las calas. */
        public double reach() {
            return switch (kind) {
                case RANGE -> halfWidth * 1.25 + 4.0;
                case COAST -> halfWidth * 1.5 + 6.0;
                case VALLEY -> halfWidth * 1.75 + 6.0;
            };
        }

        /** Bloques por encima del punto más alto del recorrido que pueden cambiar. */
        public int above() {
            return switch (kind) {
                case RANGE -> (int) Math.ceil(height * 1.25) + 12;
                case COAST -> (int) Math.ceil(height * 1.35) + 12;
                case VALLEY -> 24 + (int) Math.ceil(halfWidth * 0.35);
            };
        }

        /** Bloques por debajo del punto más bajo del recorrido que pueden cambiar. */
        public int below() {
            return switch (kind) {
                case RANGE -> 16;
                case COAST -> (int) Math.ceil(Math.min(24.0, height * 0.35)) + 16;
                case VALLEY -> (int) Math.ceil(height) + 16;
            };
        }
    }

    /**
     * Lo que ha salido: el mapa ya cambiado, el agua que se pone y unas cifras para el chat.
     *
     * @param seaShare en la costa con agua, la parte del lado del mar que se ha podido llenar (de 0 a 1)
     */
    /**
     * @param gentleRise lo más que puede subir una columna tendida sin repintarse (el pie de una sierra, el fondo y
     *                   las laderas suaves de un valle conservan su suelo), o menos infinito para no conservarla
     */
    public record Result(HeightField field, HeightField original, int[] fluidLevels, int changedColumns,
                         double highest, double lowest, int branches, double seaShare, long seed, double gentleRise)
            implements TerrainWriter.Fluids {
        @Override
        public int level(int x, int z) {
            int index = field.index(x, z);
            return index < 0 || fluidLevels == null ? TerrainColumns.NONE : fluidLevels[index];
        }

        @Override
        public String blockId() {
            return "minecraft:water";
        }

        public boolean hasWater() {
            if (fluidLevels == null) {
                return false;
            }
            for (int level : fluidLevels) {
                if (level != TerrainColumns.NONE) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Si la columna se queda con los bloques que tenía: donde la forma apenas la cambia (en el pie, en el borde),
         * con una frontera movida por un ruido, para que la pintura nueva no deje una costura recta.
         */
        public boolean keepSurface(int x, int z) {
            int index = field.index(x, z);
            if (index < 0 || Double.isNaN(field.at(index)) || Double.isNaN(original.at(index))) {
                return true;
            }
            if (fluidLevels != null && fluidLevels[index] != TerrainColumns.NONE) {
                return false;
            }
            double change = Math.abs(Math.round(field.at(index)) - Math.round(original.at(index)));
            // Una franja ancha y tramada: manchas grandes de ruido y, dentro, bloque a bloque al azar.
            double patch = 0.5 + 0.5 * StampNoise.fbm(x / 9.0, z / 9.0, seed + 300L, 2);
            double grain = StampNoise.unit(seed + 301L, x * 73_856_093 ^ z * 19_349_663);
            double threshold = 1.5 + 6.0 * patch * (0.4 + 0.6 * grain);
            if (change < threshold) {
                return true;
            }
            // Lo que queda tendido conserva su suelo (hierba, podzol, arena...): la paleta pinta las paredes, lo que
            // sube mucho y el lecho. La frontera va por una pendiente movida por el mismo ruido.
            double rise = Math.round(field.at(index)) - Math.round(original.at(index));
            double gentle = 20.0 + 14.0 * patch * (0.4 + 0.6 * grain);
            return rise <= gentleRise * (0.6 + 0.8 * patch) && field.slopeDegrees(x, z) < gentle;
        }
    }

    /**
     * Cambia {@code field} con el relieve.
     *
     * @param path   puntos de la trayectoria muestreada {x, y, z}, como mucho a un bloque unos de otros
     * @param closed si la trayectoria vuelve al principio
     */
    public static Result plan(HeightField field, List<double[]> path, boolean closed, Settings settings,
                              Runnable checkCancelled) {
        return plan(field, path, closed, settings, index -> TerrainColumns.NONE, checkCancelled);
    }

    /** Dónde hay ya agua o lava: la altura de su superficie en cada columna del mapa, o {@link TerrainColumns#NONE}. */
    @FunctionalInterface
    public interface WaterTable {
        int level(int index);
    }

    /**
     * Cambia {@code field} con el relieve.
     *
     * @param path   puntos de la trayectoria muestreada {x, y, z}, como mucho a un bloque unos de otros
     * @param closed si la trayectoria vuelve al principio
     * @param water  el agua que ya hay (la costa la usa como nivel del mar)
     */
    public static Result plan(HeightField field, List<double[]> path, boolean closed, Settings settings,
                              WaterTable water, Runnable checkCancelled) {
        Track track = Track.of(path, closed);
        HeightField original = field.copy();
        int columns = field.columns();
        double[] base = regional(original, Math.clamp(settings.halfWidth / 3.0, 2.0, 24.0), checkCancelled);
        double[] relief = new double[columns];
        double[] cover = new double[columns];
        Shape shape = switch (settings.kind) {
            case RANGE -> range(field, track, base, settings, relief, cover, checkCancelled);
            case COAST -> coast(field, track, base, settings, relief, cover, water, checkCancelled);
            case VALLEY -> valley(field, track, base, settings, relief, cover, checkCancelled);
        };
        if (!settings.stamps.isEmpty() && settings.variation > 0.0) {
            stampDetail(field, track, settings, shape, relief, checkCancelled);
        }
        // Nada pasa del alcance: lo que llegue al borde (un ramal, un valle lateral) se apaga antes, sin escalón.
        double reach = settings.reach();
        double[] distance = pathDistance(field, track, reach, checkCancelled);
        double[] fade = new double[columns];
        for (int index = 0; index < columns; index++) {
            fade[index] = 1.0 - smooth((distance[index] - reach * 0.82) / (reach * 0.18));
            relief[index] *= fade[index];
            cover[index] *= fade[index];
        }
        boolean[] touched = new boolean[columns];
        boolean[] strong = new boolean[columns];
        double ramp = Math.max(1.0, settings.height * 0.12);
        for (int index = 0; index < columns; index++) {
            if (!field.editableAt(index) || Double.isNaN(original.at(index))) {
                continue;
            }
            double now = original.at(index);
            double wanted;
            if (shape.absolute != null && !Double.isNaN(shape.absolute[index])) {
                // Lo que tiene que quedar a una altura dada (el fondo del mar), sea cual sea el modo.
                wanted = now + (shape.absolute[index] - now) * fade[index];
            } else {
                wanted = blend(settings.blend, now, base[index], relief[index], cover[index], ramp,
                        settings.height);
            }
            if (Math.abs(wanted - now) > 1e-6) {
                field.setAt(index, wanted);
                touched[index] = true;
                strong[index] = Math.abs(wanted - now) >= 1.5;
            }
        }
        if (settings.erosion > 0.0) {
            // Solo donde la forma cambia de verdad: en el pie, el terreno de antes sigue con sus bultos.
            erode(field, strong, settings, checkCancelled);
            for (int index = 0; index < columns; index++) {
                touched[index] |= strong[index];
            }
        }
        int[] levels = null;
        int wet = 0;
        int sea = 0;
        if (settings.water && settings.kind == Kind.COAST) {
            levels = sea(field, shape, settings, water, checkCancelled);
            for (int index = 0; index < columns; index++) {
                if (shape.seaSide != null && shape.seaSide[index]) {
                    sea++;
                    if (levels[index] != TerrainColumns.NONE) {
                        wet++;
                    }
                }
            }
        } else if (settings.water && settings.kind == Kind.VALLEY && shape.axis != null) {
            levels = river(field, shape, settings, touched, checkCancelled);
        }
        double highest = Double.NEGATIVE_INFINITY;
        double lowest = Double.POSITIVE_INFINITY;
        int changed = 0;
        for (int index = 0; index < columns; index++) {
            if (!touched[index]) {
                continue;
            }
            double height = field.at(index);
            if (Math.round(height) != Math.round(original.at(index))) {
                changed++;
            }
            highest = Math.max(highest, height);
            lowest = Math.min(lowest, height);
        }
        double seaShare = sea == 0 ? 0.0 : wet / (double) sea;
        double gentleRise = switch (settings.kind) {
            case RANGE -> settings.height * 0.2;
            case VALLEY -> settings.height * 0.1;
            case COAST -> Double.NEGATIVE_INFINITY;
        };
        return new Result(field, original, levels, changed, highest, lowest, shape.branches, seaShare,
                settings.seed, gentleRise);
    }

    /**
     * La altura de una columna según el modo; en lo que sube y en lo que hunde, al revés. En máximo la forma entra
     * poco a poco en su falda ({@code ramp} bloques), como en el pincel: donde no asoma, el terreno no cambia; y
     * mientras la forma es baja se suma al terreno, así el pie conserva las lomas que había y solo lo alto deja de
     * apilarse.
     */
    static double blend(Blend blend, double current, double base, double relief, double cover, double ramp,
                        double height) {
        return switch (blend) {
            case ADD -> current + relief;
            case MAX -> {
                if (relief < 0.0) {
                    // Lo que se talla queda a la altura de la forma: sin los bultos de antes en las paredes.
                    double target = smoothMin(current, base + relief, 1.5);
                    yield current + (target - current) * smooth(-relief / ramp);
                }
                double share = smooth(relief / Math.max(1.0, height * 0.4));
                double shape = base + relief + (current - base) * (1.0 - share);
                double target = smoothMax(current, shape, 1.5);
                yield current + (target - current) * smooth(relief / ramp);
            }
            case FLATTEN -> current + (base + relief - current) * cover;
        };
    }

    // ---------------------------------------------------------------- el recorrido

    /** El recorrido a paso fijo con su tangente y su largo acumulado. */
    static final class Track {
        final double[] x;
        final double[] y;
        final double[] z;
        final double[] s;
        final double[] tx;
        final double[] tz;
        final int count;
        final double length;
        final boolean closed;

        private Track(double[] x, double[] y, double[] z, double[] s, double[] tx, double[] tz, boolean closed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.s = s;
            this.tx = tx;
            this.tz = tz;
            this.count = x.length;
            this.length = s[s.length - 1];
            this.closed = closed;
        }

        static Track of(List<double[]> path, boolean closed) {
            List<double[]> points = new ArrayList<>();
            for (double[] point : path) {
                if (points.isEmpty() || Math.hypot(point[0] - points.getLast()[0], point[2] - points.getLast()[2])
                        > 1e-6) {
                    points.add(point);
                }
            }
            if (points.size() < 2) {
                throw new IllegalArgumentException("El relieve necesita una trayectoria de al menos dos puntos"
                        + " distintos");
            }
            boolean loop = closed && points.size() > 3
                    && Math.hypot(points.getFirst()[0] - points.getLast()[0],
                    points.getFirst()[2] - points.getLast()[2]) < 2.0;
            if (closed && !loop) {
                points.add(points.getFirst());
                loop = points.size() > 3;
            }
            double[] cumulative = new double[points.size()];
            for (int index = 1; index < points.size(); index++) {
                cumulative[index] = cumulative[index - 1] + Math.hypot(points.get(index)[0] - points.get(index - 1)[0],
                        points.get(index)[2] - points.get(index - 1)[2]);
            }
            double length = cumulative[cumulative.length - 1];
            if (length < 4.0) {
                throw new IllegalArgumentException("La trayectoria mide menos de 4 bloques");
            }
            int count = Math.max(2, (int) Math.ceil(length / SPACING)) + 1;
            double[] x = new double[count];
            double[] y = new double[count];
            double[] z = new double[count];
            double[] s = new double[count];
            int segment = 0;
            for (int index = 0; index < count; index++) {
                double distance = length * index / (count - 1);
                while (segment < cumulative.length - 2 && cumulative[segment + 1] < distance) {
                    segment++;
                }
                double span = cumulative[segment + 1] - cumulative[segment];
                double t = span <= 0.0 ? 0.0 : Math.clamp((distance - cumulative[segment]) / span, 0.0, 1.0);
                double[] a = points.get(segment);
                double[] b = points.get(segment + 1);
                x[index] = a[0] + (b[0] - a[0]) * t;
                y[index] = a[1] + (b[1] - a[1]) * t;
                z[index] = a[2] + (b[2] - a[2]) * t;
                s[index] = distance;
            }
            double[] tx = new double[count];
            double[] tz = new double[count];
            int reach = 3;
            for (int index = 0; index < count; index++) {
                int from = loop ? index - reach : Math.max(0, index - reach);
                int to = loop ? index + reach : Math.min(count - 1, index + reach);
                double dx = x[wrap(to, count, loop)] - x[wrap(from, count, loop)];
                double dz = z[wrap(to, count, loop)] - z[wrap(from, count, loop)];
                double norm = Math.hypot(dx, dz);
                tx[index] = norm < 1e-9 ? 1.0 : dx / norm;
                tz[index] = norm < 1e-9 ? 0.0 : dz / norm;
            }
            return new Track(x, y, z, s, tx, tz, loop);
        }

        /** En una trayectoria cerrada el último punto es el primero: los índices dan la vuelta sin repetirlo. */
        private static int wrap(int index, int count, boolean loop) {
            if (!loop) {
                return Math.clamp(index, 0, count - 1);
            }
            int period = count - 1;
            return Math.floorMod(index, period);
        }

        /** A la derecha de la marcha, vista desde arriba (con +Z al sur). */
        double rightX(int index) {
            return -tz[index];
        }

        double rightZ(int index) {
            return tx[index];
        }

        /**
         * Ruido de -1 a 1 a lo largo del recorrido con una longitud de onda en bloques; en una trayectoria cerrada da
         * la vuelta entera sin salto.
         */
        double along(double distance, double wavelength, long seed, int octaves) {
            if (closed) {
                double radius = length / (2.0 * Math.PI) / wavelength;
                double angle = distance / length * 2.0 * Math.PI;
                return StampNoise.fbm(radius * Math.cos(angle) + 11.3, radius * Math.sin(angle) - 4.7, seed, octaves);
            }
            return StampNoise.fbm(distance / wavelength, 23.17, seed, octaves);
        }

        /** De 0 a 1 según el perfil a lo largo; con extremos que bajan suave (salvo si es cerrada). */
        double envelope(double distance, Profile profile, double halfWidth) {
            if (closed) {
                return 1.0;
            }
            double u = Math.clamp(distance / length, 0.0, 1.0);
            double ends = switch (profile) {
                case BELL -> Math.min(0.5 * length, Math.max(1.3 * halfWidth, 0.24 * length));
                default -> Math.min(0.5 * length, 0.8 * halfWidth);
            };
            double ramp = smoother(distance / ends) * smoother((length - distance) / ends);
            return switch (profile) {
                case BELL -> ramp * (0.8 + 0.2 * Math.sin(Math.PI * u));
                case FLAT -> ramp;
                case RISING -> ramp * (0.22 + 0.78 * Math.pow(u, 1.1));
                case FALLING -> ramp * (0.22 + 0.78 * Math.pow(1.0 - u, 1.1));
            };
        }
    }

    // ---------------------------------------------------------------- campos de distancia

    /** Un tramo de cresta o de cauce con su altura y su medio ancho en cada extremo. */
    /**
     * @param skewA cuánto más ancho es el lado derecho que el izquierdo en el principio del tramo (de -1 a 1): una
     *              ladera tendida y la otra empinada
     */
    private record Ridge(double ax, double az, double bx, double bz, double ha, double hb, double wa, double wb,
                         double sa, double sb, double skewA, double skewB) {
        Ridge(double ax, double az, double bx, double bz, double ha, double hb, double wa, double wb, double sa,
              double sb) {
            this(ax, az, bx, bz, ha, hb, wa, wb, sa, sb, 0.0, 0.0);
        }

        double reach() {
            return Math.max(wa, wb) * (1.0 + Math.max(Math.abs(skewA), Math.abs(skewB)));
        }
    }

    /** Lo que deja cada forma para el detalle y el agua. */
    private static final class Shape {
        /** Distancia de cada columna a la línea (NaN fuera de alcance). */
        double[] distance;
        /** Lado de cada columna: positivo en la tierra de la costa. */
        double[] lateral;
        /** Altura de referencia del mar en la costa. */
        double seaLevel = Double.NaN;
        int branches;
        /** Cuánto pesa el detalle de los sellos en cada columna (bloques por unidad de detalle). */
        double[] detailScale;
        /** El eje del valle, por donde va el río. */
        List<double[]> axis;
        /** Alturas que tienen que quedar tal cual, sea cual sea el modo (el fondo del mar), o NaN. */
        double[] absolute;
        /** Las columnas del lado del mar de la costa. */
        boolean[] seaSide;
        /** Medio ancho del fondo del valle. */
        double floorHalfWidth;
    }

    /**
     * Para cada tramo, la columna toma {@code altura · perfil(distancia / ancho)} y se queda con el mayor: la
     * envolvente de todos los tramos es una cresta continua aunque la curva se cierre sobre sí misma.
     */
    private static void ridges(HeightField field, double[] warpX, double[] warpZ, List<Ridge> ridges, double power,
                               double[] into, double[] nearest, Runnable checkCancelled) {
        ridges(field, warpX, warpZ, ridges, power, into, nearest, null, checkCancelled);
    }

    /** @param stretch lo que se alarga la distancia en cada columna (las curvas de nivel del pie serpentean), o null */
    private static void ridges(HeightField field, double[] warpX, double[] warpZ, List<Ridge> ridges, double power,
                               double[] into, double[] nearest, double[] stretch, Runnable checkCancelled) {
        for (Ridge ridge : ridges) {
            checkCancelled.run();
            double reach = ridge.reach() + 2.0;
            int lowX = (int) Math.floor(Math.min(ridge.ax, ridge.bx) - reach);
            int highX = (int) Math.ceil(Math.max(ridge.ax, ridge.bx) + reach);
            int lowZ = (int) Math.floor(Math.min(ridge.az, ridge.bz) - reach);
            int highZ = (int) Math.ceil(Math.max(ridge.az, ridge.bz) + reach);
            double dx = ridge.bx - ridge.ax;
            double dz = ridge.bz - ridge.az;
            double lengthSquared = dx * dx + dz * dz;
            for (int z = Math.max(lowZ, field.minimumZ()); z <= Math.min(highZ, field.maximumZ()); z++) {
                for (int x = Math.max(lowX, field.minimumX()); x <= Math.min(highX, field.maximumX()); x++) {
                    int index = field.index(x, z);
                    double px = warpX[index];
                    double pz = warpZ[index];
                    double t = lengthSquared < 1e-12 ? 0.0
                            : Math.clamp(((px - ridge.ax) * dx + (pz - ridge.az) * dz) / lengthSquared, 0.0, 1.0);
                    double cx = ridge.ax + dx * t;
                    double cz = ridge.az + dz * t;
                    double distance = Math.hypot(px - cx, pz - cz);
                    if (nearest != null && distance < nearest[index]) {
                        nearest[index] = distance;
                    }
                    if (stretch != null) {
                        distance *= stretch[index];
                    }
                    double width = ridge.wa + (ridge.wb - ridge.wa) * t;
                    double skew = ridge.skewA + (ridge.skewB - ridge.skewA) * t;
                    if (skew != 0.0) {
                        double side = (px - ridge.ax) * (-dz) + (pz - ridge.az) * dx;
                        width *= 1.0 + skew * Math.signum(side);
                    }
                    if (distance >= width) {
                        continue;
                    }
                    double height = ridge.ha + (ridge.hb - ridge.ha) * t;
                    double value = height * Math.pow(1.0 - distance / width, power);
                    if (value > into[index]) {
                        into[index] = value;
                    }
                }
            }
        }
    }

    /** La distancia de cada columna a una línea de puntos (infinita más allá de {@code reach}). */
    private static double[] axisDistance(HeightField field, double[] lineX, double[] lineZ, double reach,
                                         Runnable checkCancelled) {
        int columns = field.columns();
        double[] plainX = new double[columns];
        double[] plainZ = new double[columns];
        for (int index = 0; index < columns; index++) {
            plainX[index] = field.xOf(index) + 0.5;
            plainZ[index] = field.zOf(index) + 0.5;
        }
        double[] nearest = new double[columns];
        Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        List<Ridge> line = new ArrayList<>();
        int step = (int) Math.max(1, Math.round(SEGMENT / SPACING));
        for (int index = 0; index < lineX.length - 1; index += step) {
            int next = Math.min(lineX.length - 1, index + step);
            line.add(new Ridge(lineX[index], lineZ[index], lineX[next], lineZ[next], 0.0, 0.0, reach, reach, 0.0,
                    0.0));
        }
        ridges(field, plainX, plainZ, line, 1.0, new double[columns], nearest, checkCancelled);
        return nearest;
    }

    /** La distancia de cada columna a la trayectoria (infinita más allá de {@code reach}). */
    private static double[] pathDistance(HeightField field, Track track, double reach, Runnable checkCancelled) {
        int columns = field.columns();
        double[] plainX = new double[columns];
        double[] plainZ = new double[columns];
        for (int index = 0; index < columns; index++) {
            plainX[index] = field.xOf(index) + 0.5;
            plainZ[index] = field.zOf(index) + 0.5;
        }
        double[] nearest = new double[columns];
        Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        List<Ridge> line = new ArrayList<>();
        int step = (int) Math.max(1, Math.round(SEGMENT / SPACING));
        for (int index = 0; index < track.count - 1; index += step) {
            int next = Math.min(track.count - 1, index + step);
            line.add(new Ridge(track.x[index], track.z[index], track.x[next], track.z[next], 0.0, 0.0, reach, reach,
                    track.s[index], track.s[next]));
        }
        ridges(field, plainX, plainZ, line, 1.0, new double[columns], nearest, checkCancelled);
        return nearest;
    }

    /** La posición de cada columna movida por un ruido suave: las crestas y los cauces dejan de ser de compás. */
    private static double[][] warp(HeightField field, double amount, double wavelength, long seed) {
        int columns = field.columns();
        double[] warpX = new double[columns];
        double[] warpZ = new double[columns];
        for (int index = 0; index < columns; index++) {
            double x = field.xOf(index) + 0.5;
            double z = field.zOf(index) + 0.5;
            if (amount <= 0.0) {
                warpX[index] = x;
                warpZ[index] = z;
                continue;
            }
            warpX[index] = x + amount * StampNoise.fbm(x / wavelength, z / wavelength, seed + 51L, 3);
            warpZ[index] = z + amount * StampNoise.fbm(x / wavelength + 37.1, z / wavelength - 12.9, seed + 52L, 3);
        }
        return new double[][]{warpX, warpZ};
    }

    // ---------------------------------------------------------------- cordillera

    private static Shape range(HeightField field, Track track, double[] base, Settings settings, double[] relief,
                               double[] cover, Runnable checkCancelled) {
        double halfWidth = settings.halfWidth;
        double height = settings.height;
        double variation = settings.variation;
        long seed = settings.seed;
        int count = track.count;
        double[] crestX = new double[count];
        double[] crestZ = new double[count];
        double[] crestH = new double[count];
        double[] crestW = new double[count];
        double strongest = 0.0;
        for (int index = 0; index < count; index++) {
            double distance = track.s[index];
            double envelope = track.envelope(distance, settings.profile, halfWidth);
            // Cumbres y collados: picos de un ruido de crestas en una dimensión sobre macizos más anchos.
            double ridged = 1.0 - Math.abs(track.along(distance, halfWidth * 1.1, seed + 1L, 3));
            double broad = 0.5 + 0.5 * track.along(distance, halfWidth * 3.0, seed + 2L, 2);
            double peaks = 0.55 * ridged * ridged + 0.45 * broad;
            double crest = envelope * (1.0 - 0.7 * variation + 0.7 * variation * peaks);
            crestH[index] = crest;
            strongest = Math.max(strongest, crest);
            crestW[index] = halfWidth * (0.55 + 0.45 * Math.sqrt(envelope))
                    * (1.0 + 0.18 * variation * track.along(distance, halfWidth * 1.9, seed + 3L, 2));
            // La cresta no va por la línea: serpentea a su alrededor con quiebros cortos sobre curvas largas.
            double meander = variation * halfWidth * (0.3 * track.along(distance, halfWidth * 1.8, seed + 4L, 2)
                    + 0.12 * track.along(distance, halfWidth * 0.45, seed + 6L, 2)) * Math.sqrt(envelope);
            crestX[index] = track.x[index] + track.rightX(index) * meander;
            crestZ[index] = track.z[index] + track.rightZ(index) * meander;
        }
        double scale = strongest <= 0.0 ? 0.0 : height / strongest;
        for (int index = 0; index < count; index++) {
            crestH[index] *= scale;
        }
        List<Ridge> core = new ArrayList<>();
        int step = (int) Math.max(1, Math.round(SEGMENT / SPACING));
        double[] skew = new double[count];
        for (int index = 0; index < count; index++) {
            // Una vertiente más tendida que la otra, y cambia de lado a lo largo de la sierra.
            skew[index] = variation * 0.4 * track.along(track.s[index], halfWidth * 2.6, seed + 140L, 2);
        }
        for (int index = 0; index < count - 1; index += step) {
            int next = Math.min(count - 1, index + step);
            core.add(new Ridge(crestX[index], crestZ[index], crestX[next], crestZ[next], crestH[index], crestH[next],
                    crestW[index], crestW[next], track.s[index], track.s[next], skew[index], skew[next]));
        }
        Spurs spurs = spurs(track, crestX, crestZ, crestH, crestW, settings);
        List<Ridge> summits = summits(track, crestX, crestZ, crestH, crestW, settings);
        double[][] warped = warp(field, variation * 0.1 * halfWidth, halfWidth * 0.6, seed);
        int columns = field.columns();
        double[] nearest = new double[columns];
        Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        double[] envelope = new double[columns];
        // La caída del pie con ruido: las curvas de nivel serpentean en lugar de dar anillos paralelos.
        double[] stretch = new double[columns];
        for (int index = 0; index < columns; index++) {
            double x = warped[0][index];
            double z = warped[1][index];
            stretch[index] = 1.0 + variation * (0.22 * StampNoise.fbm(x / (halfWidth * 0.3), z / (halfWidth * 0.3),
                    seed + 141L, 3) + 0.07 * StampNoise.fbm(x / (halfWidth * 0.09), z / (halfWidth * 0.09),
                    seed + 142L, 2));
        }
        ridges(field, warped[0], warped[1], core, PROFILE, envelope, nearest, stretch, checkCancelled);
        ridges(field, warped[0], warped[1], spurs.ridges, PROFILE, envelope, null, stretch, checkCancelled);
        ridges(field, warped[0], warped[1], foothills(track, crestX, crestZ, crestH, crestW, settings), 1.7,
                envelope, null, checkCancelled);
        ridges(field, warped[0], warped[1], summits, 1.25, envelope, null, checkCancelled);
        Frames frames = frames(field, warped, track, crestX, crestZ, crestW, halfWidth * 1.35 + 4.0,
                halfWidth * 2.5, checkCancelled);
        double detailWave = Math.max(5.0, halfWidth * 0.16);
        double[] detailScale = new double[columns];
        for (int index = 0; index < columns; index++) {
            double value = envelope[index];
            if (value <= 0.0) {
                continue;
            }
            checkCancelledEvery(index, checkCancelled);
            double x = warped[0][index];
            double z = warped[1][index];
            double share = Math.clamp(value / Math.max(1.0, height), 0.0, 1.0);
            // Costillas y barrancos que bajan por la ladera, en las coordenadas del recorrido (a lo largo y a lo ancho):
            // siempre de la cresta al pie, también en las curvas. Donde dos partes del recorrido se tocan, se mezclan.
            double ribs = 1.0;
            if (variation > 0.0 && frames.has(index)) {
                ribs = ribs(frames.s1[index], frames.t1[index], widthAt(track, crestW, frames.s1[index]), settings,
                        track);
                if (frames.hasSecond(index)) {
                    double other = ribs(frames.s2[index], frames.t2[index], widthAt(track, crestW, frames.s2[index]),
                            settings, track);
                    double mix = 0.5 * (1.0 - smooth((frames.d2[index] - frames.d1[index]) / (halfWidth * 0.3)));
                    ribs += (other - ribs) * mix;
                }
            }
            double ridgedNoise = StampNoise.ridged(x / detailWave, z / detailWave, seed + 7L, 3);
            double flank = 4.0 * share * (1.0 - share);
            double foot = 1.0 - smooth(share / 0.3);
            double rolling = StampNoise.fbm(x / (halfWidth * 0.4), z / (halfWidth * 0.4), seed + 9L, 3);
            value *= ribs * (1.0 + variation * (0.12 * (ridgedNoise - 0.4) * flank + 0.4 * rolling * foot));
            // Lomas sueltas al pie, en bloques y no en proporción: rompen los anillos que deja una falda lisa.
            value += variation * height * 0.07 * rolling * foot * smooth(share / 0.05);
            relief[index] = Math.max(0.0, value);
            detailScale[index] = relief[index] * variation;
            cover[index] = 1.0 - smooth((nearest[index] - crestReach(halfWidth) * 0.8) / (crestReach(halfWidth)
                    * 0.2));
        }
        Shape shape = new Shape();
        shape.distance = nearest;
        shape.branches = spurs.count;
        shape.detailScale = detailScale;
        return shape;
    }

    /** Lomas sueltas en el pie a los dos lados, de tamaños distintos: el pie no es una falda lisa. */
    private static List<Ridge> foothills(Track track, double[] crestX, double[] crestZ, double[] crestH,
                                         double[] crestW, Settings settings) {
        List<Ridge> result = new ArrayList<>();
        double variation = settings.variation;
        if (variation <= 0.05) {
            return result;
        }
        double halfWidth = settings.halfWidth;
        int serial = 0;
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            double side = sideIndex == 0 ? 1.0 : -1.0;
            double position = halfWidth * 0.4 * StampNoise.unit(settings.seed + 150L, sideIndex);
            while (position < track.length) {
                int index = (int) Math.min(track.count - 1, Math.round(position / track.length
                        * (track.count - 1)));
                double unit = StampNoise.unit(settings.seed + 151L, serial++);
                position += halfWidth * (0.25 + 0.6 * unit) / Math.max(0.3, variation);
                double crest = crestH[index];
                if (crest < settings.height * 0.1) {
                    continue;
                }
                double out = crestW[index] * (0.6 + 0.45 * StampNoise.unit(settings.seed + 152L, serial));
                double along = (StampNoise.unit(settings.seed + 153L, serial) - 0.5) * halfWidth * 0.4;
                double x = crestX[index] + side * track.rightX(index) * out + track.tx[index] * along;
                double z = crestZ[index] + side * track.rightZ(index) * out + track.tz[index] * along;
                double radius = halfWidth * (0.12 + 0.18 * StampNoise.unit(settings.seed + 154L, serial));
                double tall = crest * (0.06 + 0.12 * StampNoise.unit(settings.seed + 155L, serial)) * variation;
                result.add(new Ridge(x, z, x, z, tall, tall, radius, radius, 0.0, 0.0));
            }
        }
        return result;
    }

    /** Perfil de la ladera: cóncavo, empinado arriba y tendido en el pie, que llega a cero sin escalón. */
    private static final double PROFILE = 1.8;

    private static double crestReach(double halfWidth) {
        return halfWidth * 1.1;
    }

    /**
     * Las cumbres: en cada máximo de la cresta una pirámide algo más alta y más afilada que la ladera, así se ven picos
     * con collados entre ellos y no una arista de la misma altura.
     */
    private static List<Ridge> summits(Track track, double[] crestX, double[] crestZ, double[] crestH,
                                       double[] crestW, Settings settings) {
        List<Ridge> result = new ArrayList<>();
        if (settings.variation <= 0.0) {
            return result;
        }
        int window = (int) Math.max(3, Math.round(settings.halfWidth * 0.35 / SPACING));
        int serial = 0;
        for (int index = 0; index < track.count; index++) {
            double here = crestH[index];
            if (here < settings.height * 0.35) {
                continue;
            }
            boolean peak = true;
            for (int offset = -window; offset <= window && peak; offset++) {
                int other = index + offset;
                if (offset == 0 || other < 0 || other >= track.count) {
                    continue;
                }
                peak = crestH[other] < here || crestH[other] == here && offset > 0;
            }
            if (!peak) {
                continue;
            }
            double extra = settings.height * settings.variation
                    * (0.12 + 0.14 * StampNoise.unit(settings.seed + 130L, serial++));
            double radius = crestW[index] * (0.38 + 0.12 * StampNoise.unit(settings.seed + 131L, serial));
            result.add(new Ridge(crestX[index], crestZ[index], crestX[index], crestZ[index], here + extra,
                    here + extra, radius, radius, track.s[index], track.s[index]));
        }
        return result;
    }

    private static double widthAt(Track track, double[] widths, double distance) {
        int index = (int) Math.round(Math.clamp(distance / track.length, 0.0, 1.0) * (track.count - 1));
        return widths[index];
    }

    /**
     * Cuánto queda de la ladera en un punto a {@code s} bloques por el recorrido y {@code t} a un lado: crestas de un
     * ruido estirado a lo ancho (costillas que bajan) con barrancos entre ellas. Nada en la cresta, para que siga
     * entera, ni en el pie, que se funde liso con el terreno. Cada lado con su semilla.
     */
    private static double ribs(double s, double t, double width, Settings settings, Track track) {
        double u = Math.abs(t) / Math.max(1.0, width);
        double strength = smooth((u - 0.04) / 0.3) * (1.0 - smooth((u - 0.78) / 0.22));
        if (strength <= 0.0) {
            return 1.0;
        }
        long seed = settings.seed + (t >= 0.0 ? 300L : 400L);
        double along = Math.max(4.0, settings.halfWidth * 0.26);
        double across = settings.halfWidth * 1.05;
        double eta = Math.abs(t) / across;
        // Dos tamaños: barrancos grandes cada poco más de media ladera y costillas finas entre ellos.
        double ridged = 0.62 * pathNoise(track, s, along * 2.3, eta * 0.8, seed + 7L, true)
                + 0.38 * pathNoise(track, s, along, eta * 1.8, seed, true);
        double amount = settings.variation * 0.95;
        return 1.0 - amount * strength + amount * strength * (0.34 + 0.95 * ridged);
    }

    /**
     * Ruido en coordenadas del recorrido ({@code s / along}, {@code eta}). En una trayectoria cerrada da la vuelta sin
     * costura: se mezcla con el de una vuelta antes según lo recorrido.
     */
    private static double pathNoise(Track track, double s, double along, double eta, long seed, boolean ridged) {
        // Las costillas se tuercen al bajar y se cortan a trozos: no salen rectas ni de compás.
        double bend = 0.85 * StampNoise.fbm(s / (along * 2.3), eta * 1.6, seed + 1L, 2);
        double value = sample(s / along + bend, eta, seed, ridged);
        if (track.closed) {
            double turn = (s - track.length) / along + bend;
            double weight = Math.clamp(s / track.length, 0.0, 1.0);
            value = value * (1.0 - weight) + sample(turn, eta, seed, ridged) * weight;
        }
        return value;
    }

    private static double sample(double xi, double eta, long seed, boolean ridged) {
        return ridged ? StampNoise.ridged(xi, eta, seed, 3) : 0.5 + 0.5 * StampNoise.fbm(xi, eta, seed, 3);
    }

    /**
     * Para cada columna, el punto más cercano de la línea (por dónde va del recorrido y a qué lado, con signo) y, si lo
     * hay, el de otra parte del recorrido que esté lejos a lo largo (dentro de una curva cerrada), para mezclar los
     * dos sin costura donde las dos distancias se igualan.
     */
    private static final class Frames {
        final double[] d1;
        final double[] s1;
        final double[] t1;
        final double[] d2;
        final double[] s2;
        final double[] t2;

        Frames(int columns) {
            d1 = new double[columns];
            s1 = new double[columns];
            t1 = new double[columns];
            d2 = new double[columns];
            s2 = new double[columns];
            t2 = new double[columns];
            Arrays.fill(d1, Double.POSITIVE_INFINITY);
            Arrays.fill(d2, Double.POSITIVE_INFINITY);
        }

        boolean has(int index) {
            return !Double.isInfinite(d1[index]);
        }

        boolean hasSecond(int index) {
            return !Double.isInfinite(d2[index]);
        }
    }

    private static Frames frames(HeightField field, double[][] warped, Track track, double[] lineX, double[] lineZ,
                                 double[] widths, double reach, double separation, Runnable checkCancelled) {
        Frames frames = new Frames(field.columns());
        int step = (int) Math.max(1, Math.round(SEGMENT / SPACING));
        for (int index = 0; index < track.count - 1; index += step) {
            checkCancelled.run();
            int next = Math.min(track.count - 1, index + step);
            double ax = lineX[index];
            double az = lineZ[index];
            double dx = lineX[next] - ax;
            double dz = lineZ[next] - az;
            double lengthSquared = dx * dx + dz * dz;
            double length = Math.sqrt(lengthSquared);
            int lowX = (int) Math.floor(Math.min(ax, lineX[next]) - reach);
            int highX = (int) Math.ceil(Math.max(ax, lineX[next]) + reach);
            int lowZ = (int) Math.floor(Math.min(az, lineZ[next]) - reach);
            int highZ = (int) Math.ceil(Math.max(az, lineZ[next]) + reach);
            for (int z = Math.max(lowZ, field.minimumZ()); z <= Math.min(highZ, field.maximumZ()); z++) {
                for (int x = Math.max(lowX, field.minimumX()); x <= Math.min(highX, field.maximumX()); x++) {
                    int column = field.index(x, z);
                    double px = warped[0][column];
                    double pz = warped[1][column];
                    double t = lengthSquared < 1e-12 ? 0.0
                            : Math.clamp(((px - ax) * dx + (pz - az) * dz) / lengthSquared, 0.0, 1.0);
                    double distance = Math.hypot(px - (ax + dx * t), pz - (az + dz * t));
                    if (distance > reach) {
                        continue;
                    }
                    double along = track.s[index] + (track.s[next] - track.s[index]) * t;
                    double side = length < 1e-9 ? 0.0 : ((px - ax) * (-dz) + (pz - az) * dx) / length;
                    double lateral = Math.copySign(distance, side == 0.0 ? 1.0 : side);
                    if (distance < frames.d1[column]) {
                        if (frames.has(column) && apart(track, along, frames.s1[column], separation)) {
                            frames.d2[column] = frames.d1[column];
                            frames.s2[column] = frames.s1[column];
                            frames.t2[column] = frames.t1[column];
                        } else if (frames.hasSecond(column)
                                && !apart(track, along, frames.s2[column], separation)) {
                            frames.d2[column] = Double.POSITIVE_INFINITY;
                        }
                        frames.d1[column] = distance;
                        frames.s1[column] = along;
                        frames.t1[column] = lateral;
                    } else if (distance < frames.d2[column] && apart(track, along, frames.s1[column], separation)) {
                        frames.d2[column] = distance;
                        frames.s2[column] = along;
                        frames.t2[column] = lateral;
                    }
                }
            }
        }
        return frames;
    }

    private static boolean apart(Track track, double a, double b, double separation) {
        double gap = Math.abs(a - b);
        if (track.closed) {
            gap = Math.min(gap, track.length - gap);
        }
        return gap > separation;
    }

    /**
     * Ramales grandes a los dos lados de la cresta: salen de ella con un ángulo al azar, bajan hacia el pie más
     * despacio que la ladera (así sobresalen del cuerpo de la sierra) y se tuercen un poco; a media bajada pueden abrir
     * otro más pequeño. Entre dos ramales queda un valle. Un ramal que se acercaría a otra parte de la cresta (dentro de
     * una curva cerrada) se corta antes.
     */
    private static Spurs spurs(Track track, double[] crestX, double[] crestZ, double[] crestH, double[] crestW,
                               Settings settings) {
        List<Ridge> result = new ArrayList<>();
        double halfWidth = settings.halfWidth;
        double variation = settings.variation;
        if (variation <= 0.0) {
            return new Spurs(result, 0);
        }
        double height = settings.height;
        int count = track.count;
        int[] serial = {0};
        int made = 0;
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            double side = sideIndex == 0 ? 1.0 : -1.0;
            double position = halfWidth * (0.4 + 0.8 * StampNoise.unit(settings.seed + 90L, sideIndex));
            while (position < track.length) {
                int index = (int) Math.min(count - 1, Math.round(position / track.length * (count - 1)));
                double unit = StampNoise.unit(settings.seed + 91L, serial[0]++);
                position += halfWidth * (0.6 + 0.9 * unit) * (1.5 - 0.6 * variation);
                double crest = crestH[index];
                if (crest < height * 0.25) {
                    continue;
                }
                double angle = Math.toRadians((StampNoise.unit(settings.seed + 92L, serial[0]) - 0.5) * 70.0);
                double dirX = side * track.rightX(index) * Math.cos(angle) + track.tx[index] * Math.sin(angle);
                double dirZ = side * track.rightZ(index) * Math.cos(angle) + track.tz[index] * Math.sin(angle);
                double length = crestW[index] * (0.95 + 0.55 * StampNoise.unit(settings.seed + 93L, serial[0]))
                        * (0.7 + 0.3 * variation);
                double start = crest * (0.8 + 0.12 * StampNoise.unit(settings.seed + 94L, serial[0]));
                double width = crestW[index] * (0.36 + 0.14 * StampNoise.unit(settings.seed + 95L, serial[0]));
                double bend = (StampNoise.unit(settings.seed + 96L, serial[0]) - 0.5) * 1.0;
                branch(result, track, crestX, crestZ, index, crestX[index], crestZ[index], dirX, dirZ, length, start,
                        width, bend, 0, settings, serial);
                made++;
            }
        }
        return new Spurs(result, made);
    }

    /** Un ramal en tramos, con su altura y su ancho bajando hasta el pie; en el primer nivel, ramales hijos. */
    private static void branch(List<Ridge> into, Track track, double[] crestX, double[] crestZ, int origin,
                               double startX, double startZ, double dirX, double dirZ, double length, double start,
                               double width, double bend, int level, Settings settings, int[] serial) {
        int pieces = Math.max(3, (int) Math.ceil(length / 2.5));
        double px = startX;
        double pz = startZ;
        double ph = start;
        double pw = width;
        long seed = settings.seed;
        int children = 0;
        for (int piece = 1; piece <= pieces; piece++) {
            double t = piece / (double) pieces;
            double turn = bend / pieces + (StampNoise.unit(seed + 97L, serial[0]++) - 0.5) * 0.18;
            double cos = Math.cos(turn);
            double sin = Math.sin(turn);
            double nextX = dirX * cos - dirZ * sin;
            double nextZ = dirX * sin + dirZ * cos;
            dirX = nextX;
            dirZ = nextZ;
            double qx = px + dirX * length / pieces;
            double qz = pz + dirZ * length / pieces;
            if (closerToOtherCrest(track, crestX, crestZ, origin, qx, qz, settings.halfWidth)) {
                break;
            }
            double qh = start * Math.pow(1.0 - t, 1.15);
            double qw = width * (1.0 - 0.3 * t);
            into.add(new Ridge(px, pz, qx, qz, ph, qh, pw, qw, (piece - 1) / (double) pieces, t));
            if (level == 0 && children < 2 && t > 0.28 && t < 0.7
                    && StampNoise.unit(seed + 98L, serial[0]++) < 0.22 * settings.variation) {
                double sideTurn = (StampNoise.unit(seed + 99L, serial[0]++) < 0.5 ? -1.0 : 1.0)
                        * Math.toRadians(40.0 + 35.0 * StampNoise.unit(seed + 100L, serial[0]++));
                double childX = dirX * Math.cos(sideTurn) - dirZ * Math.sin(sideTurn);
                double childZ = dirX * Math.sin(sideTurn) + dirZ * Math.cos(sideTurn);
                double rest = length * (1.0 - t);
                branch(into, track, crestX, crestZ, origin, qx, qz, childX, childZ,
                        rest * (0.5 + 0.3 * StampNoise.unit(seed + 101L, serial[0]++)), qh * 0.97, qw * 0.7,
                        -sideTurn * 0.3, level + 1, settings, serial);
                children++;
            }
            px = qx;
            pz = qz;
            ph = qh;
            pw = qw;
        }
    }

    private record Spurs(List<Ridge> ridges, int count) {
    }

    /** Si el punto está más cerca de un trozo de cresta lejano en el recorrido que del suyo. */
    private static boolean closerToOtherCrest(Track track, double[] crestX, double[] crestZ, int origin, double x,
                                              double z, double halfWidth) {
        double own = Math.hypot(x - crestX[origin], z - crestZ[origin]);
        double far = Math.max(3.0, halfWidth * 1.5);
        for (int index = 0; index < track.count; index += 2) {
            double along = Math.abs(track.s[index] - track.s[origin]);
            if (track.closed) {
                along = Math.min(along, track.length - along);
            }
            if (along < far) {
                continue;
            }
            if (Math.hypot(x - crestX[index], z - crestZ[index]) < own * 0.85) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- costa

    private static Shape coast(HeightField field, Track track, double[] base, Settings settings, double[] relief,
                               double[] cover, WaterTable water, Runnable checkCancelled) {
        double halfWidth = settings.halfWidth;
        double height = settings.height;
        double variation = settings.variation;
        long seed = settings.seed;
        int columns = field.columns();
        double[] nearest = new double[columns];
        double[] lateral = new double[columns];
        double[] along = new double[columns];
        double[] beyond = new double[columns];
        nearestLine(field, track, settings.reach(), nearest, lateral, along, beyond, checkCancelled);
        double sideSign = switch (settings.side) {
            case RIGHT -> 1.0;
            case LEFT -> -1.0;
            case AUTO -> landSide(field, base, lateral, halfWidth);
        };
        Coves coves = Coves.of(track, settings);
        double seaLevel = seaLevel(field, base, lateral, nearest, beyond, sideSign, settings, water);
        double depth = Math.clamp(height * 0.35, 4.0, 18.0);
        double[] detailScale = new double[columns];
        double[] absolute = new double[columns];
        Arrays.fill(absolute, Double.NaN);
        boolean[] seaSide = new boolean[columns];
        for (int index = 0; index < columns; index++) {
            if (Double.isNaN(nearest[index]) || Double.isNaN(base[index])) {
                continue;
            }
            checkCancelledEvery(index, checkCancelled);
            double x = field.xOf(index) + 0.5;
            double z = field.zOf(index) + 0.5;
            double s = along[index];
            double envelope = track.envelope(s, settings.profile, halfWidth);
            double ends = 1.0 - smooth(beyond[index] / (halfWidth * 0.8));
            double inlet = coves.inlet(s);
            // La línea de costa entra y sale por su cuenta: calas que se meten, cabos que salen y un borde roto.
            double shore = coves.offset(s) + variation * (halfWidth * 0.12 * StampNoise.fbm(x / (halfWidth * 0.9),
                    z / (halfWidth * 0.9), seed + 11L, 3) + 1.8 * StampNoise.fbm(x / 5.0, z / 5.0, seed + 12L, 2));
            double t = lateral[index] * sideSign - shore;
            // La altura cambia de verdad: cabos altos y tramos bajos que bajan a la cala.
            double local = height * envelope * ends * coves.height(s) * (1.0 - 0.75 * inlet)
                    * (1.0 + 0.15 * variation * StampNoise.fbm(x / (halfWidth * 0.7), z / (halfWidth * 0.7),
                    seed + 13L, 2));
            if (t >= 0.0) {
                double steepness = 0.5 + 0.5 * StampNoise.fbm(x / (halfWidth * 0.8), z / (halfWidth * 0.8),
                        seed + 16L, 2);
                // En la cala la ladera es tendida hasta la playa; en el cabo, un cortado.
                double cliffHere = Math.clamp(2.0 + height * (0.06 + 0.3 * variation * steepness)
                        + inlet * halfWidth * 0.45, 2.0, halfWidth * 0.6);
                // Empinado abajo (roca hasta el agua) y redondeado arriba.
                double rise = 1.0 - Math.pow(1.0 - Math.clamp(t / cliffHere, 0.0, 1.0), 2.2);
                // Arriba la tierra sigue a su nivel o sube un poco hacia dentro: nada de reborde en el borde.
                double plateau = 0.9 + 0.1 * smooth(t / (halfWidth * 0.45));
                double inland = 1.0 - smooth((t - halfWidth * 0.75) / (halfWidth * 0.65));
                double rolling = variation * (0.06 * StampNoise.fbm(x / (halfWidth * 0.5), z / (halfWidth * 0.5),
                        seed + 14L, 3) + 0.04 * (StampNoise.ridged(x / 12.0, z / 12.0, seed + 15L, 3) - 0.4));
                double cleftNoise = pathNoise(track, s, 7.0 + 6.0 * variation, t / (halfWidth * 0.5), seed + 17L,
                        false);
                double cleft = Math.max(0.0, 1.0 - Math.abs(cleftNoise - 0.5) * 8.0)
                        * (1.0 - smooth((t - halfWidth * 0.1) / (halfWidth * 0.3)));
                double raised = local * rise * (plateau + rolling * smooth(t / (halfWidth * 0.2))) * inland
                        * (1.0 - 0.5 * variation * cleft);
                relief[index] = raised;
                detailScale[index] = raised * variation * 0.8;
                cover[index] = inland * ends;
            } else {
                double off = -t;
                seaSide[index] = nearest[index] < settings.reach() * 0.95;
                // El fondo, a una altura dada bajo el mar; al pie, el canchal y las rocas caídas, y en las calas una
                // playa que sale del agua.
                // El fondo es una cubeta: baja desde la orilla y vuelve a subir suave hacia fuera, sin muro.
                double bowl = smooth(off / (halfWidth * 0.4)) * (1.0 - smooth((off - halfWidth * 0.8)
                        / (halfWidth * 0.55)));
                double floor = seaLevel - 1.0 - depth * envelope * ends * bowl;
                // Al pie, una franja estrecha de canchal a ras del agua con rocas caídas que asoman.
                double foot = Math.exp(-Math.pow(off / (1.5 + local * 0.08), 2.0));
                double screeTop = seaLevel - 0.6 + 1.4 * StampNoise.fbm(x / 3.0, z / 3.0, seed + 18L, 2)
                        + rocks(x, z, seed) * (1.5 + local * 0.1);
                double scree = floor + (screeTop - floor) * foot;
                double beachTop = seaLevel + 1.0 - off * 0.2;
                double beach = floor + (beachTop - floor) * (1.0 - smooth(off / (halfWidth * 0.3)));
                double feature = scree * (1.0 - inlet) + beach * inlet;
                double carved = Math.min(field.at(index), floor);
                double target = feature - floor > 0.3 ? Math.max(carved, feature) : carved;
                // Pasados los extremos de una costa abierta, el fondo vuelve al terreno: nada de foso que siga.
                absolute[index] = field.at(index) + (target - field.at(index)) * ends;
                seaSide[index] &= ends > 0.05;
                cover[index] = ends;
            }
        }
        int stacks = stacks(field, track, settings, sideSign, coves, seaLevel, absolute, checkCancelled);
        Shape shape = new Shape();
        shape.distance = nearest;
        shape.lateral = lateral;
        shape.detailScale = detailScale;
        shape.branches = stacks;
        shape.absolute = absolute;
        shape.seaSide = seaSide;
        shape.seaLevel = seaLevel;
        for (int index = 0; index < columns; index++) {
            if (!Double.isNaN(lateral[index])) {
                lateral[index] *= sideSign;
            }
        }
        return shape;
    }

    /** Rocas sueltas al pie: bultos pequeños y desiguales donde un ruido celular cae cerca de su centro. */
    private static double rocks(double x, double z, long seed) {
        double cell = 3.5;
        int cellX = (int) Math.floor(x / cell);
        int cellZ = (int) Math.floor(z / cell);
        double best = 0.0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long key = seed * 31L + (cellX + dx) * 73_856_093L + (cellZ + dz) * 19_349_663L;
                double chance = StampNoise.unit(key, 0);
                if (chance > 0.45) {
                    continue;
                }
                double cx = (cellX + dx + StampNoise.unit(key, 1)) * cell;
                double cz = (cellZ + dz + StampNoise.unit(key, 2)) * cell;
                double radius = 0.8 + 1.6 * StampNoise.unit(key, 3);
                double distance = Math.hypot(x - cx, z - cz) / radius;
                if (distance < 1.0) {
                    best = Math.max(best, (0.5 + StampNoise.unit(key, 4)) * Math.pow(1.0 - distance, 0.6));
                }
            }
        }
        return best;
    }

    /**
     * El nivel del mar: el del agua que ya haya al lado del mar, si hay. Si no, un bloque por debajo del suelo de la
     * línea, pero nunca más alto que el terreno más bajo del borde de fuera del lado del mar: así el agua no tiene
     * por dónde escaparse y el acantilado sale algo más alto en lugar de quedar un foso seco.
     */
    private static double seaLevel(HeightField field, double[] base, double[] lateral, double[] nearest,
                                   double[] beyond, double sideSign, Settings settings, WaterTable water) {
        double halfWidth = settings.halfWidth;
        double reach = settings.reach();
        List<Integer> levels = new ArrayList<>();
        double sum = 0.0;
        int count = 0;
        double rim = Double.POSITIVE_INFINITY;
        for (int index = 0; index < field.columns(); index++) {
            double side = lateral[index];
            if (Double.isNaN(side) || Double.isNaN(base[index])) {
                continue;
            }
            double t = side * sideSign;
            if (t < 0.0 && t > -halfWidth) {
                int level = water.level(index);
                if (level != TerrainColumns.NONE && level > field.at(index)) {
                    levels.add(level);
                }
            }
            if (Math.abs(t) < 3.0 && beyond[index] <= 0.0) {
                sum += base[index];
                count++;
            }
            // El borde de fuera del lado del mar: lo que queda entre lo que cambia y lo que no.
            if (t < 0.0 && nearest[index] > reach * 0.84 && nearest[index] < reach * 0.98) {
                rim = Math.min(rim, Math.round(field.at(index)));
            }
        }
        if (levels.size() > 16) {
            Collections.sort(levels);
            return levels.get(levels.size() / 2);
        }
        double wanted = count == 0 ? 0.0 : Math.floor(sum / count) - 1.0;
        return Double.isInfinite(rim) ? wanted : Math.min(wanted, rim);
    }

    /**
     * Las calas y los cabos a lo largo de la costa: cuánto entra la línea en la tierra (o sale al mar) y cuánto baja el
     * acantilado al llegar a una cala, más la altura que cambia de cabo a cabo.
     */
    private record Coves(double[] at, double[] width, double[] depth, Track track, Settings settings) {
        static Coves of(Track track, Settings settings) {
            double halfWidth = settings.halfWidth;
            int wanted = settings.variation <= 0.05 ? 0
                    : Math.max(1, (int) Math.round(track.length / (halfWidth * 1.6) * settings.variation));
            double[] at = new double[wanted];
            double[] width = new double[wanted];
            double[] depth = new double[wanted];
            for (int index = 0; index < wanted; index++) {
                at[index] = (index + 0.25 + 0.5 * StampNoise.unit(settings.seed + 170L, index)) / wanted
                        * track.length;
                width[index] = halfWidth * (0.22 + 0.35 * StampNoise.unit(settings.seed + 171L, index));
                // Casi todas entran (calas); alguna sale (un cabo).
                double unit = StampNoise.unit(settings.seed + 172L, index);
                depth[index] = halfWidth * (unit < 0.25 ? -(0.15 + 0.2 * unit / 0.25) : 0.2 + 0.4 * unit);
            }
            return new Coves(at, width, depth, track, settings);
        }

        /** Cuánto se mete la línea en la tierra en ese punto del recorrido (negativo: sale al mar). */
        double offset(double s) {
            double total = 0.0;
            for (int index = 0; index < at.length; index++) {
                total += depth[index] * bump(s, index);
            }
            return total * settings.variation;
        }

        /** De 0 a 1: lo metido que está ese punto en una cala. */
        double inlet(double s) {
            double total = 0.0;
            for (int index = 0; index < at.length; index++) {
                if (depth[index] > 0.0) {
                    total = Math.max(total, bump(s, index) * Math.min(1.0, depth[index] / (settings.halfWidth
                            * 0.35)));
                }
            }
            return total * Math.min(1.0, settings.variation * 1.4);
        }

        /** La altura del acantilado a lo largo: de 0,45 a 1,15 de la pedida. */
        double height(double s) {
            double wave = track.along(s, settings.halfWidth * 1.8, settings.seed + 173L, 3);
            return 1.0 + settings.variation * 0.55 * wave;
        }

        private double bump(double s, int index) {
            double gap = Math.abs(s - at[index]);
            if (track.closed) {
                gap = Math.min(gap, track.length - gap);
            }
            double u = gap / width[index];
            return u >= 1.0 ? 0.0 : Math.pow(1.0 - u * u, 2.0);
        }
    }

    /**
     * Farallones de tamaños distintos frente a la costa, más junto a los cabos: agujas altas, torres anchas de lo alto
     * plano, pares que quedaron de un arco caído y montones de rocas.
     */
    private static int stacks(HeightField field, Track track, Settings settings, double sideSign, Coves coves,
                              double seaLevel, double[] absolute, Runnable checkCancelled) {
        double variation = settings.variation;
        if (variation < 0.15) {
            return 0;
        }
        double halfWidth = settings.halfWidth;
        int wanted = (int) Math.round(track.length / Math.max(8.0, halfWidth * 0.7) * variation);
        int placed = 0;
        for (int serial = 0; serial < wanted; serial++) {
            checkCancelled.run();
            double position = StampNoise.unit(settings.seed + 120L, serial) * track.length;
            int index = (int) Math.min(track.count - 1, Math.round(position / track.length * (track.count - 1)));
            double envelope = track.envelope(track.s[index], settings.profile, halfWidth);
            if (envelope < 0.4 || coves.inlet(track.s[index]) > 0.4) {
                continue;
            }
            double kind = StampNoise.unit(settings.seed + 125L, serial);
            double off = coves.offset(track.s[index]) - halfWidth * (0.04 + 0.22 * StampNoise.unit(settings.seed
                    + 121L, serial));
            double cx = track.x[index] + track.rightX(index) * sideSign * off;
            double cz = track.z[index] + track.rightZ(index) * sideSign * off;
            double tall = settings.height * envelope * coves.height(track.s[index]);
            if (kind < 0.35) {
                pillar(field, absolute, cx, cz, 1.4 + 1.8 * StampNoise.unit(settings.seed + 122L, serial),
                        seaLevel + tall * (0.55 + 0.45 * StampNoise.unit(settings.seed + 123L, serial)), 0.35,
                        settings.seed + serial);
            } else if (kind < 0.6) {
                pillar(field, absolute, cx, cz, 3.0 + 3.5 * StampNoise.unit(settings.seed + 122L, serial),
                        seaLevel + tall * (0.3 + 0.35 * StampNoise.unit(settings.seed + 123L, serial)), 0.18,
                        settings.seed + serial);
            } else if (kind < 0.8) {
                // Un arco caído: dos patas con un hueco entre ellas.
                double gapX = -track.tz[index] * 0.0 + track.tx[index] * 3.5;
                double gapZ = track.tz[index] * 3.5;
                double top = seaLevel + tall * (0.4 + 0.3 * StampNoise.unit(settings.seed + 123L, serial));
                pillar(field, absolute, cx - gapX, cz - gapZ, 2.2, top, 0.3, settings.seed + serial);
                pillar(field, absolute, cx + gapX, cz + gapZ, 1.8, top * 0.97 + seaLevel * 0.03, 0.3,
                        settings.seed + serial + 7L);
            } else {
                // Un montón de rocas de lo que se derrumbó.
                for (int piece = 0; piece < 4; piece++) {
                    double angle = StampNoise.unit(settings.seed + 126L, serial * 8 + piece) * 2.0 * Math.PI;
                    double spread = 1.5 + 3.0 * StampNoise.unit(settings.seed + 127L, serial * 8 + piece);
                    pillar(field, absolute, cx + Math.cos(angle) * spread, cz + Math.sin(angle) * spread,
                            1.2 + 1.6 * StampNoise.unit(settings.seed + 128L, serial * 8 + piece),
                            seaLevel + 1.0 + tall * 0.12 * StampNoise.unit(settings.seed + 129L, serial * 8 + piece),
                            0.5, settings.seed + serial * 8 + piece);
                }
            }
            placed++;
        }
        return placed;
    }

    /** Una aguja o una torre en el mar: sube del fondo hasta {@code top} con el borde roto por un ruido. */
    private static void pillar(HeightField field, double[] absolute, double cx, double cz, double radius, double top,
                               double power, long seed) {
        int reach = (int) Math.ceil(radius * 1.3) + 1;
        for (int z = (int) Math.floor(cz) - reach; z <= (int) Math.floor(cz) + reach; z++) {
            for (int x = (int) Math.floor(cx) - reach; x <= (int) Math.floor(cx) + reach; x++) {
                int column = field.index(x, z);
                if (column < 0) {
                    continue;
                }
                double distance = Math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                        * (1.0 + 0.3 * StampNoise.noise(x * 0.45, z * 0.45, seed));
                if (distance >= radius) {
                    continue;
                }
                double floor = Double.isNaN(absolute[column]) ? field.at(column) : absolute[column];
                double value = floor + (top - floor) * Math.pow(1.0 - distance / radius, power);
                absolute[column] = Double.isNaN(absolute[column]) ? Math.max(field.at(column), value)
                        : Math.max(absolute[column], value);
            }
        }
    }

    /** El lado donde el terreno ya es más alto (la tierra); a la derecha si los dos están igual. */
    private static double landSide(HeightField field, double[] base, double[] lateral, double halfWidth) {
        double right = 0.0;
        double left = 0.0;
        int rightCount = 0;
        int leftCount = 0;
        for (int index = 0; index < field.columns(); index++) {
            double side = lateral[index];
            if (Double.isNaN(side) || Double.isNaN(base[index]) || Math.abs(side) > halfWidth
                    || Math.abs(side) < 2.0) {
                continue;
            }
            if (side > 0.0) {
                right += base[index];
                rightCount++;
            } else {
                left += base[index];
                leftCount++;
            }
        }
        if (rightCount == 0 || leftCount == 0) {
            return 1.0;
        }
        return left / leftCount > right / rightCount + 0.5 ? -1.0 : 1.0;
    }

    /**
     * La distancia exacta de cada columna a la línea, a qué lado queda (positivo a la derecha de la marcha), por qué
     * punto del recorrido pasa y cuánto se sale por los extremos de una trayectoria abierta.
     */
    private static void nearestLine(HeightField field, Track track, double reach, double[] nearest, double[] lateral,
                                    double[] along, double[] beyond, Runnable checkCancelled) {
        Arrays.fill(nearest, Double.NaN);
        Arrays.fill(lateral, Double.NaN);
        int step = (int) Math.max(1, Math.round(SEGMENT / SPACING));
        for (int index = 0; index < track.count - 1; index += step) {
            checkCancelled.run();
            int next = Math.min(track.count - 1, index + step);
            double ax = track.x[index];
            double az = track.z[index];
            double bx = track.x[next];
            double bz = track.z[next];
            double dx = bx - ax;
            double dz = bz - az;
            double lengthSquared = dx * dx + dz * dz;
            int lowX = (int) Math.floor(Math.min(ax, bx) - reach);
            int highX = (int) Math.ceil(Math.max(ax, bx) + reach);
            int lowZ = (int) Math.floor(Math.min(az, bz) - reach);
            int highZ = (int) Math.ceil(Math.max(az, bz) + reach);
            boolean first = index == 0 && !track.closed;
            boolean last = next == track.count - 1 && !track.closed;
            for (int z = Math.max(lowZ, field.minimumZ()); z <= Math.min(highZ, field.maximumZ()); z++) {
                for (int x = Math.max(lowX, field.minimumX()); x <= Math.min(highX, field.maximumX()); x++) {
                    int column = field.index(x, z);
                    double px = x + 0.5;
                    double pz = z + 0.5;
                    double raw = lengthSquared < 1e-12 ? 0.0
                            : ((px - ax) * dx + (pz - az) * dz) / lengthSquared;
                    double t = Math.clamp(raw, 0.0, 1.0);
                    double cx = ax + dx * t;
                    double cz = az + dz * t;
                    double distance = Math.hypot(px - cx, pz - cz);
                    if (distance > reach || !Double.isNaN(nearest[column]) && distance >= nearest[column]) {
                        continue;
                    }
                    double length = Math.sqrt(lengthSquared);
                    // El lado se mide con la perpendicular del tramo; pasado un extremo, con la de ese extremo.
                    double side = length < 1e-9 ? 0.0 : ((px - ax) * (-dz) + (pz - az) * dx) / length;
                    double out = 0.0;
                    if (first && raw < 0.0) {
                        out = -raw * length;
                        side = (px - ax) * track.rightX(0) + (pz - az) * track.rightZ(0);
                    } else if (last && raw > 1.0) {
                        out = (raw - 1.0) * length;
                        side = (px - bx) * track.rightX(track.count - 1) + (pz - bz) * track.rightZ(track.count - 1);
                    }
                    nearest[column] = distance;
                    lateral[column] = Math.signum(side) * Math.sqrt(Math.max(0.0, distance * distance - out * out));
                    along[column] = track.s[index] + (track.s[next] - track.s[index]) * t;
                    beyond[column] = out;
                }
            }
        }
    }

    /**
     * El mar del lado bajo de la costa, como se llena un hueco: hasta el nivel del mar donde nada lo deja escapar y,
     * donde el borde tiene un paso más bajo, solo hasta ese paso (y si el paso da al borde de lo leído, nada). El agua
     * que ya hubiera cuenta como pared.
     */
    private static int[] sea(HeightField field, Shape shape, Settings settings, WaterTable water,
                             Runnable checkCancelled) {
        int columns = field.columns();
        int[] levels = new int[columns];
        Arrays.fill(levels, TerrainColumns.NONE);
        if (Double.isNaN(shape.seaLevel) || shape.seaSide == null) {
            return levels;
        }
        int sizeX = field.sizeX();
        int level = (int) Math.round(shape.seaLevel);
        // El nivel no pasa del punto más bajo del borde de lo que se cambió (ya cambiado): por ahí se escaparía.
        for (int index = 0; index < columns; index++) {
            if (!shape.seaSide[index]) {
                continue;
            }
            int x = index % sizeX;
            int[] around = {x > 0 ? index - 1 : -1, x < sizeX - 1 ? index + 1 : -1, index - sizeX,
                    index + sizeX < columns ? index + sizeX : -1};
            for (int neighbour : around) {
                if (neighbour < 0 || shape.seaSide[neighbour] || Double.isNaN(field.at(neighbour))) {
                    continue;
                }
                int wall = top(field, neighbour);
                int existing = water.level(neighbour);
                if (existing != TerrainColumns.NONE) {
                    wall = Math.max(wall, existing + 1);
                }
                level = Math.min(level, wall);
            }
        }
        ArrayDeque<int[]> work = new ArrayDeque<>();
        List<Integer> start = new ArrayList<>();
        for (int index = 0; index < columns; index++) {
            if (shape.seaSide[index] && field.editableAt(index) && top(field, index) < level) {
                start.add(index);
            }
        }
        work.add(new int[]{level});
        List<List<Integer>> groups = new ArrayList<>();
        groups.add(start);
        boolean[] member = new boolean[columns];
        boolean[] seen = new boolean[columns];
        int rounds = 0;
        while (!work.isEmpty() && rounds++ < 4_096) {
            checkCancelled.run();
            int current = work.poll()[0];
            List<Integer> group = groups.removeFirst();
            for (int index : group) {
                member[index] = top(field, index) < current;
            }
            for (int origin : group) {
                if (!member[origin] || seen[origin]) {
                    continue;
                }
                // Una zona de agua seguida con este nivel y el paso más bajo de su borde.
                List<Integer> zone = new ArrayList<>();
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(origin);
                seen[origin] = true;
                int rim = Integer.MAX_VALUE;
                while (!queue.isEmpty()) {
                    int index = queue.poll();
                    zone.add(index);
                    int x = index % sizeX;
                    int[] around = {x > 0 ? index - 1 : -1, x < sizeX - 1 ? index + 1 : -1,
                            index - sizeX, index + sizeX < columns ? index + sizeX : -1};
                    for (int neighbour : around) {
                        if (neighbour < 0) {
                            rim = Integer.MIN_VALUE;
                            continue;
                        }
                        if (member[neighbour]) {
                            if (!seen[neighbour]) {
                                seen[neighbour] = true;
                                queue.add(neighbour);
                            }
                            continue;
                        }
                        double ground = field.at(neighbour);
                        int wall = Double.isNaN(ground) ? Integer.MIN_VALUE : (int) Math.round(ground);
                        int existing = water.level(neighbour);
                        if (existing != TerrainColumns.NONE) {
                            wall = Math.max(wall, existing + 1);
                        }
                        rim = Math.min(rim, wall);
                    }
                }
                if (rim >= current) {
                    for (int index : zone) {
                        levels[index] = current;
                    }
                } else if (rim > Integer.MIN_VALUE) {
                    // Se escaparía por el paso: el agua se queda a su altura, en lo que siga por debajo.
                    for (int index : zone) {
                        seen[index] = false;
                        member[index] = false;
                    }
                    groups.add(zone);
                    work.add(new int[]{rim});
                } else {
                    for (int index : zone) {
                        member[index] = false;
                    }
                }
            }
            for (int index : group) {
                if (levels[index] == TerrainColumns.NONE) {
                    member[index] = false;
                }
            }
        }
        return levels;
    }

    private static int top(HeightField field, int index) {
        return (int) Math.round(field.at(index));
    }

    // ---------------------------------------------------------------- valle

    private static Shape valley(HeightField field, Track track, double[] base, Settings settings, double[] relief,
                                double[] cover, Runnable checkCancelled) {
        double halfWidth = settings.halfWidth;
        double depth = settings.height;
        double variation = settings.variation;
        long seed = settings.seed;
        int count = track.count;
        double[] axisX = new double[count];
        double[] axisZ = new double[count];
        double[] axisW = new double[count];
        double[] ground = new double[count];
        double[] raw = new double[count];
        for (int index = 0; index < count; index++) {
            double distance = track.s[index];
            double envelope = valleyEnvelope(track, distance, settings);
            double meander = variation * (settings.form == Form.CANYON ? 0.3 : 0.16) * halfWidth
                    * track.along(distance, halfWidth * 2.0, seed + 31L, 2);
            axisX[index] = track.x[index] + track.rightX(index) * meander;
            axisZ[index] = track.z[index] + track.rightZ(index) * meander;
            // En los extremos el valle se abre en un circo o embudo que se funde con el terreno, no en una pared.
            axisW[index] = halfWidth * (1.0 + 0.45 * (1.0 - envelope))
                    * (1.0 + 0.22 * variation * track.along(distance, halfWidth * 1.7, seed + 32L, 2))
                    * (settings.form == Form.U ? 1.0 + 0.16 * variation * track.along(distance, halfWidth * 0.6,
                    seed + 36L, 2) : 1.0);
            double here = regionalAt(field, base, axisX[index], axisZ[index]);
            ground[index] = Double.isNaN(here) ? track.y[index] - 1.0 : here;
            double carve = depth * envelope * (1.0 - 0.4 * variation
                    + 0.4 * variation * (0.5 + 0.5 * track.along(distance, halfWidth * 1.4, seed + 33L, 2)));
            raw[index] = ground[index] - carve;
        }
        double[] bed;
        if (track.closed) {
            bed = movingAverage(raw, Math.max(1, (int) Math.round(halfWidth * 0.5)), true);
        } else {
            boolean reversed = average(raw, 0, count / 10 + 1) < average(raw, count - 1 - count / 10, count);
            if (reversed) {
                reverse(raw);
                reverse(ground);
            }
            List<double[]> axis = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                axis.add(new double[]{axisX[index], 0.0, axisZ[index]});
            }
            bed = RiverPlanner.monotoneBed(axis, raw, ground, Math.max(4.0, halfWidth * 0.6));
            if (reversed) {
                reverse(bed);
                reverse(ground);
            }
        }
        Form form = settings.form;
        double floor = switch (form) {
            case V -> 0.07;
            case U -> 0.3;
            case CANYON -> 0.22;
        };
        int columns = field.columns();
        double[] target = new double[columns];
        Arrays.fill(target, Double.POSITIVE_INFINITY);
        double[] nearest = new double[columns];
        Arrays.fill(nearest, Double.POSITIVE_INFINITY);
        // El cañón con contrafuertes y entrantes en las paredes; el valle, solo ondulado.
        double[][] warped = settings.form == Form.CANYON
                ? warp(field, variation * 0.2 * halfWidth, halfWidth * 0.35, seed + 40L)
                : warp(field, variation * 0.1 * halfWidth, halfWidth * 0.8, seed + 40L);
        // El fondo no se mueve: así el río va por lo más bajo y no por media ladera.
        double[] fromAxis = axisDistance(field, axisX, axisZ, halfWidth * 2.0, checkCancelled);
        for (int index = 0; index < columns; index++) {
            double keep = smooth(fromAxis[index] / (halfWidth * 0.45));
            double x = field.xOf(index) + 0.5;
            double z = field.zOf(index) + 0.5;
            warped[0][index] = x + (warped[0][index] - x) * keep;
            warped[1][index] = z + (warped[1][index] - z) * keep;
        }
        double outside = 0.32;
        int step = (int) Math.max(1, Math.round(SEGMENT / SPACING));
        double[] skew = new double[count];
        double[] steep = new double[count];
        for (int index = 0; index < count; index++) {
            // Una ladera más tendida que la otra, y pendiente que cambia a lo largo.
            double strong = form == Form.U ? 1.6 : 1.0;
            skew[index] = variation * 0.32 * strong * track.along(track.s[index], halfWidth * 2.4, seed + 34L, 2);
            steep[index] = Math.max(0.45, 1.0 + variation * 0.45 * strong
                    * track.along(track.s[index], halfWidth * 1.3, seed + 35L, 2));
        }
        for (int index = 0; index < count - 1; index += step) {
            int next = Math.min(count - 1, index + step);
            carve(field, warped, axisX[index], axisZ[index], axisX[next], axisZ[next], bed[index], bed[next],
                    ground[index], ground[next], axisW[index], axisW[next], form, floor, outside, target, nearest,
                    skew[index], skew[next], steep[index], steep[next], checkCancelled);
        }
        int tributaries = tributaries(field, warped, track, axisX, axisZ, axisW, bed, ground, settings, target,
                checkCancelled);
        List<double[]> outlets = new ArrayList<>();
        double[] gullies = gullies(field, warped, track, axisX, axisZ, axisW, bed, ground, settings, floor, outlets,
                checkCancelled);
        // En el glaciar, un cono de derrubios a la salida de cada canal: el canchal al pie de la pared.
        double[] cones = form == Form.U ? new double[columns] : null;
        if (cones != null) {
            List<Ridge> heaps = new ArrayList<>();
            for (double[] outlet : outlets) {
                double radius = outlet[3] * (0.1 + 0.05 * StampNoise.unit(seed + 191L, heaps.size()));
                double tall = depth * (0.1 + 0.12 * StampNoise.unit(seed + 192L, heaps.size()));
                heaps.add(new Ridge(outlet[0], outlet[1], outlet[0], outlet[1], tall, tall, radius, radius, 0.0,
                        0.0));
            }
            ridges(field, warped[0], warped[1], heaps, 1.6, cones, null, checkCancelled);
        }
        double[] detailScale = new double[columns];
        for (int index = 0; index < columns; index++) {
            if (Double.isInfinite(target[index]) || Double.isNaN(base[index])) {
                continue;
            }
            double value = Math.min(0.0, target[index] - base[index]);
            double x = warped[0][index];
            double z = warped[1][index];
            double share = Math.clamp(-value / Math.max(1.0, depth), 0.0, 1.0);
            double wall = 4.0 * share * (1.0 - share);
            // Barrancos que se ramifican por las laderas, cada uno con su largo y su hondura; apenas muerden el borde.
            if (gullies != null && gullies[index] > 0.0) {
                value -= gullies[index] * smooth((-value + 1.0) / Math.max(1.0, depth * 0.15));
            }
            // Un poco de textura que no se repite.
            double fine = StampNoise.fbm(x / 7.0, z / 7.0, seed + 41L, 3);
            value += variation * wall * depth * 0.05 * fine;
            if (cones != null && cones[index] > 0.0) {
                value = Math.min(0.0, value + cones[index] * smooth((share - 0.55) / 0.3));
            }
            double carved = Math.min(0.0, value);
            double shoulder = 0.0;
            if (form == Form.U && variation > 0.05) {
                // El glaciar no deja un canal: las paredes suben a hombros y crestas que crecen y se apagan a lo
                // largo, así que la pared es alta en unos sitios y baja en otros.
                double u = nearest[index] / halfWidth;
                double rise = smooth((u - 0.7) / 0.35) * (1.0 - smooth((u - 1.12) / 0.38));
                double crest = 0.5 + 0.5 * StampNoise.fbm(x / (halfWidth * 0.9), z / (halfWidth * 0.9),
                        seed + 43L, 3);
                double rough = 0.8 + 0.2 * StampNoise.fbm(x / 9.0, z / 9.0, seed + 44L, 2);
                shoulder = depth * variation * 1.1 * Math.max(0.0, crest - 0.32) * rise * rough;
            }
            relief[index] = carved + shoulder;
            detailScale[index] = (-carved * wall + shoulder * 0.5) * variation;
            double width = halfWidth * 1.2;
            cover[index] = 1.0 - smooth((nearest[index] - width * 0.85) / (width * 0.3));
        }
        Shape shape = new Shape();
        shape.distance = nearest;
        shape.branches = tributaries;
        shape.detailScale = detailScale;
        List<double[]> axis = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            axis.add(new double[]{axisX[index], bed[index], axisZ[index]});
        }
        shape.axis = axis;
        shape.floorHalfWidth = halfWidth * floor;
        return shape;
    }

    /**
     * Un río por el fondo del valle con el tallado de {@link RiverPlanner}: su cauce baja siempre y el agua no se
     * escapa. Ancho y hondura a la medida del fondo.
     */
    private static int[] river(HeightField field, Shape shape, Settings settings, boolean[] touched,
                               Runnable checkCancelled) {
        double half = Math.clamp(shape.floorHalfWidth * 0.55, 1.0, 6.0);
        // Nivel de bloque y medio: con el fondo en decimales, un bloque justo dejaría la mitad del cauce seco.
        RiverPlanner.Settings river = new RiverPlanner.Settings(half, 2.0, Math.clamp(half, 1.0, 3.0), 1.0, 3.0,
                RiverPlanner.Fluid.WATER, 1.5, false, false, Math.max(4.0, settings.halfWidth * 0.3));
        // El río compara alturas ya en bloques: con decimales, la mitad del cauce quedaría seca.
        for (int index = 0; index < field.columns(); index++) {
            if (touched[index]) {
                field.setAt(index, Math.round(field.at(index)));
            }
        }
        HeightField before = field.copy();
        RiverPlanner.Result result = RiverPlanner.plan(field, shape.axis, river, checkCancelled);
        for (int index = 0; index < field.columns(); index++) {
            if (field.at(index) != before.at(index) || result.fluidLevels()[index] != TerrainColumns.NONE) {
                touched[index] = true;
            }
        }
        return result.fluidLevels();
    }

    /** De 0 a 1 a lo largo del valle: como la de siempre, pero con los extremos largos para que se abran en embudo. */
    private static double valleyEnvelope(Track track, double distance, Settings settings) {
        if (track.closed) {
            return 1.0;
        }
        double ends = Math.min(0.5 * track.length, settings.halfWidth * 1.4);
        double ramp = smoother(distance / ends) * smoother((track.length - distance) / ends);
        double u = Math.clamp(distance / track.length, 0.0, 1.0);
        return switch (settings.profile) {
            case BELL -> ramp * (0.8 + 0.2 * Math.sin(Math.PI * u));
            case FLAT -> ramp;
            case RISING -> ramp * (0.22 + 0.78 * Math.pow(u, 1.1));
            case FALLING -> ramp * (0.22 + 0.78 * Math.pow(1.0 - u, 1.1));
        };
    }

    /**
     * Barrancos en las laderas del valle, como los que deja el agua: salen del pie de la pared con espaciado, largo,
     * hondura y rumbo al azar, se tuercen al subir y se abren en ramas hacia arriba (dendríticos). Da lo que hunde
     * cada columna (0 donde no hay barranco).
     */
    private static double[] gullies(HeightField field, double[][] warped, Track track, double[] axisX,
                                    double[] axisZ, double[] axisW, double[] bed, double[] ground, Settings settings,
                                    double floor, List<double[]> outlets, Runnable checkCancelled) {
        double variation = settings.variation;
        if (variation <= 0.05) {
            return null;
        }
        double halfWidth = settings.halfWidth;
        int count = track.count;
        List<Ridge> ridges = new ArrayList<>();
        int[] serial = {0};
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            double side = sideIndex == 0 ? 1.0 : -1.0;
            double position = halfWidth * 0.3 * StampNoise.unit(settings.seed + 180L, sideIndex);
            while (position < track.length) {
                checkCancelled.run();
                int index = (int) Math.min(count - 1, Math.round(position / track.length * (count - 1)));
                double unit = StampNoise.unit(settings.seed + 181L, serial[0]++);
                // Espaciado irregular de verdad: unos juntos, otros con mucha ladera lisa en medio.
                position += halfWidth * (0.25 + 1.2 * Math.pow(unit, 1.6)) * (1.3 - 0.4 * variation);
                double drop = ground[index] - bed[index];
                if (drop < 4.0) {
                    continue;
                }
                double angle = Math.toRadians((StampNoise.unit(settings.seed + 182L, serial[0]) - 0.5) * 80.0);
                double dirX = side * track.rightX(index) * Math.cos(angle) + track.tx[index] * Math.sin(angle);
                double dirZ = side * track.rightZ(index) * Math.cos(angle) + track.tz[index] * Math.sin(angle);
                double startX = axisX[index] + side * track.rightX(index) * axisW[index] * (floor + 0.06);
                double startZ = axisZ[index] + side * track.rightZ(index) * axisW[index] * (floor + 0.06);
                double length = axisW[index] * (1.0 - floor) * (0.45 + 0.6 * StampNoise.unit(settings.seed + 183L,
                        serial[0]));
                double hollow = drop * variation * (0.08 + 0.36 * Math.pow(StampNoise.unit(settings.seed + 184L,
                        serial[0]), 1.5));
                double width = 1.5 + axisW[index] * (0.035 + 0.06 * StampNoise.unit(settings.seed + 185L,
                        serial[0]));
                gully(ridges, startX, startZ, dirX, dirZ, length, hollow, width, 0, settings, serial);
                if (outlets != null) {
                    outlets.add(new double[]{startX, startZ, hollow, axisW[index]});
                }
            }
        }
        double[] into = new double[field.columns()];
        ridges(field, warped[0], warped[1], ridges, 1.3, into, null, checkCancelled);
        return into;
    }

    /** Un barranco en tramos: hondo en la parte baja, nada arriba; a media subida puede abrir ramas. */
    private static void gully(List<Ridge> into, double startX, double startZ, double dirX, double dirZ,
                              double length, double hollow, double width, int level, Settings settings,
                              int[] serial) {
        int pieces = Math.max(3, (int) Math.ceil(length / 2.5));
        double px = startX;
        double pz = startZ;
        long seed = settings.seed;
        double previous = hollow * profile(0.0);
        double pw = width;
        for (int piece = 1; piece <= pieces; piece++) {
            double t = piece / (double) pieces;
            double turn = (StampNoise.unit(seed + 186L, serial[0]++) - 0.5) * 0.5;
            double cos = Math.cos(turn);
            double sin = Math.sin(turn);
            double nextX = dirX * cos - dirZ * sin;
            double nextZ = dirX * sin + dirZ * cos;
            dirX = nextX;
            dirZ = nextZ;
            double qx = px + dirX * length / pieces;
            double qz = pz + dirZ * length / pieces;
            double here = hollow * profile(t);
            double qw = width * (1.0 - 0.45 * t);
            into.add(new Ridge(px, pz, qx, qz, previous, here, pw, qw, 0.0, 0.0));
            if (level < 2 && t > 0.25 && t < 0.8 && StampNoise.unit(seed + 187L, serial[0]++) < 0.55) {
                double branch = (StampNoise.unit(seed + 188L, serial[0]++) < 0.5 ? -1.0 : 1.0)
                        * Math.toRadians(25.0 + 35.0 * StampNoise.unit(seed + 189L, serial[0]++));
                double childX = dirX * Math.cos(branch) - dirZ * Math.sin(branch);
                double childZ = dirX * Math.sin(branch) + dirZ * Math.cos(branch);
                gully(into, qx, qz, childX, childZ, length * (1.0 - t) * (0.45 + 0.35 * StampNoise.unit(seed + 190L,
                        serial[0]++)), here * 0.8, qw * 0.8, level + 1, settings, serial);
            }
            px = qx;
            pz = qz;
            previous = here;
            pw = qw;
        }
    }

    /** La hondura de un barranco a lo largo: de poco en la salida a lo más hondo pronto y a nada arriba. */
    private static double profile(double t) {
        return smooth(t / 0.2) * Math.pow(1.0 - t, 0.8) + 0.25 * (1.0 - smooth(t / 0.2));
    }

    /**
     * Un tramo del cauce: el fondo a la altura del lecho y las paredes hasta el terreno, y por fuera un talud suave.
     * Cada columna se queda con el mínimo de todos los tramos. Más allá de las puntas, el lecho, el borde y el ancho
     * siguen la recta del tramo en lugar de quedarse en los del extremo: si no, la punta redonda del tramo siguiente,
     * con el lecho más bajo, mordería un poco el anterior y dejaría un diente cada tramo, que al redondear a bloques
     * se ve como costillas por las paredes.
     */
    private static void carve(HeightField field, double[][] warped, double ax, double az, double bx, double bz,
                              double bedA, double bedB, double groundA, double groundB, double widthA, double widthB,
                              Form form, double floor, double outside, double[] target, double[] nearest,
                              double skewA, double skewB, double steepA, double steepB, Runnable checkCancelled) {
        checkCancelled.run();
        double reach = Math.max(widthA, widthB) * 1.5 + 2.0;
        int lowX = (int) Math.floor(Math.min(ax, bx) - reach);
        int highX = (int) Math.ceil(Math.max(ax, bx) + reach);
        int lowZ = (int) Math.floor(Math.min(az, bz) - reach);
        int highZ = (int) Math.ceil(Math.max(az, bz) + reach);
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        for (int z = Math.max(lowZ, field.minimumZ()); z <= Math.min(highZ, field.maximumZ()); z++) {
            for (int x = Math.max(lowX, field.minimumX()); x <= Math.min(highX, field.maximumX()); x++) {
                int index = field.index(x, z);
                double px = warped[0][index];
                double pz = warped[1][index];
                double along = lengthSquared < 1e-12 ? 0.0
                        : ((px - ax) * dx + (pz - az) * dz) / lengthSquared;
                double t = Math.clamp(along, 0.0, 1.0);
                double distance = Math.hypot(px - (ax + dx * t), pz - (az + dz * t));
                if (distance < nearest[index]) {
                    nearest[index] = distance;
                }
                double e = Math.clamp(along, -0.5, 1.5);
                double side = Math.signum((px - ax) * (-dz) + (pz - az) * dx);
                double skew = Math.clamp(skewA + (skewB - skewA) * e, -0.9, 0.9);
                double steep = Math.max(0.45, steepA + (steepB - steepA) * e);
                double width = Math.max(1.0, widthA + (widthB - widthA) * e) * (1.0 + skew * side);
                double u = distance / width;
                if (u > 1.5) {
                    continue;
                }
                double bed = bedA + (bedB - bedA) * e;
                double rim = Math.max(bed, groundA + (groundB - groundA) * e);
                double shaped = u <= floor ? 0.0 : floor + (1.0 - floor) * Math.pow((u - floor) / (1.0 - floor),
                        steep);
                double value = u <= 1.0 ? bed + (rim - bed) * section(shaped, form, floor)
                        : rim + (u - 1.0) * width * outside;
                if (value < target[index]) {
                    target[index] = value;
                }
            }
        }
    }

    /** De 0 en el fondo a 1 en el borde, según la sección; con pendiente nula en el borde para fundirse. */
    static double section(double u, Form form, double floor) {
        if (u <= floor) {
            return 0.0;
        }
        double q = Math.clamp((u - floor) / (1.0 - floor), 0.0, 1.0);
        return switch (form) {
            case V -> {
                double straight = Math.pow(q, 1.15);
                yield straight + (1.0 - Math.pow(1.0 - q, 2.0) - straight) * q * q;
            }
            case U -> Math.pow(smooth(Math.pow(q, 0.85)), 0.75);
            case CANYON -> {
                double steps = 4.0;
                double shaped = Math.pow(q, 0.8);
                double level = Math.floor(shaped * steps);
                double within = shaped * steps - level;
                // Cada escalón: un cortado casi vertical y una repisa casi llana.
                double riser = smooth(Math.clamp((within - 0.62) / 0.38, 0.0, 1.0));
                yield Math.min(1.0, (level + riser) / steps * 0.97 + 0.03 * shaped);
            }
        };
    }

    /**
     * Valles laterales que llegan al principal colgados (su fondo más alto que el del valle) y apuntan aguas arriba,
     * como los afluentes.
     */
    private static int tributaries(HeightField field, double[][] warped, Track track, double[] axisX, double[] axisZ,
                                   double[] axisW, double[] bed, double[] ground, Settings settings, double[] target,
                                   Runnable checkCancelled) {
        double variation = settings.variation;
        if (variation <= 0.05) {
            return 0;
        }
        double halfWidth = settings.halfWidth;
        int count = track.count;
        boolean downstreamForward = track.closed || bed[count - 1] <= bed[0];
        int made = 0;
        int serial = 0;
        double[] own = new double[field.columns()];
        double[] ownNearest = new double[field.columns()];
        for (int sideIndex = 0; sideIndex < 2; sideIndex++) {
            double side = sideIndex == 0 ? 1.0 : -1.0;
            double position = halfWidth * (0.9 + 0.8 * StampNoise.unit(settings.seed + 140L, sideIndex));
            while (position < track.length - halfWidth) {
                checkCancelled.run();
                int index = (int) Math.min(count - 1, Math.round(position / track.length * (count - 1)));
                double unit = StampNoise.unit(settings.seed + 141L, serial++);
                position += halfWidth * (1.1 + 1.2 * unit) / Math.max(0.35, variation);
                double drop = ground[index] - bed[index];
                if (drop < 3.0) {
                    continue;
                }
                double angle = Math.toRadians(25.0 + 35.0 * StampNoise.unit(settings.seed + 142L, serial));
                double downstream = downstreamForward ? 1.0 : -1.0;
                double dirX = side * track.rightX(index) * Math.cos(angle)
                        - downstream * track.tx[index] * Math.sin(angle);
                double dirZ = side * track.rightZ(index) * Math.cos(angle)
                        - downstream * track.tz[index] * Math.sin(angle);
                double length = axisW[index] * (0.6 + 0.45 * StampNoise.unit(settings.seed + 143L, serial));
                boolean glacial = settings.form == Form.U;
                double width = axisW[index] * (glacial ? 0.32 + 0.12 * StampNoise.unit(settings.seed + 144L, serial)
                        : 0.22 + 0.1 * StampNoise.unit(settings.seed + 144L, serial));
                double hanging = bed[index] + drop * (glacial
                        ? 0.45 + 0.25 * StampNoise.unit(settings.seed + 145L, serial)
                        : 0.25 + 0.3 * StampNoise.unit(settings.seed + 145L, serial));
                double startX = axisX[index] + dirX * axisW[index] * 0.35;
                double startZ = axisZ[index] + dirZ * axisW[index] * 0.35;
                int pieces = Math.max(3, (int) Math.ceil(length / 3.0));
                double px = startX;
                double pz = startZ;
                Arrays.fill(own, Double.POSITIVE_INFINITY);
                Arrays.fill(ownNearest, Double.POSITIVE_INFINITY);
                for (int piece = 1; piece <= pieces; piece++) {
                    double t0 = (piece - 1) / (double) pieces;
                    double t1 = piece / (double) pieces;
                    double qx = px + dirX * length / pieces;
                    double qz = pz + dirZ * length / pieces;
                    double rim0 = regionalAtOr(field, px, pz, ground[index]);
                    double rim1 = regionalAtOr(field, qx, qz, ground[index]);
                    double bed0 = hanging + (rim0 - hanging) * Math.pow(t0, 1.6);
                    double bed1 = hanging + (rim1 - hanging) * Math.pow(t1, 1.6);
                    double w0 = width * (1.0 - 0.6 * t0);
                    double w1 = width * (1.0 - 0.6 * t1);
                    carve(field, warped, px, pz, qx, qz, bed0, bed1, rim0, rim1, w0, w1,
                            settings.form == Form.U ? Form.U : Form.V, settings.form == Form.U ? 0.2 : 0.05, 0.35,
                            own, ownNearest, 0.0, 0.0, 1.0, 1.0, checkCancelled);
                    px = qx;
                    pz = qz;
                }
                for (int column = 0; column < own.length; column++) {
                    target[column] = Math.min(target[column], own[column]);
                }
                made++;
            }
        }
        return made;
    }

    private static double regionalAtOr(HeightField field, double x, double z, double fallback) {
        double value = field.sample(x, z);
        return Double.isNaN(value) ? fallback : value;
    }

    // ---------------------------------------------------------------- detalle de los sellos

    /** El sello, su versión desenfocada y cuánto se aparta uno de otro de media (para normalizar el detalle). */
    private record DetailStamp(HeightStamp stamp, HeightStamp blurred, double spread) {
    }

    /** Lo que se guarda de cada sello: su versión desenfocada y cuánto se aparta, sin el sello. */
    record Blur(HeightStamp blurred, double spread) {
    }

    private static DetailStamp detail(HeightStamp stamp) {
        Blur blur = blur(stamp);
        return new DetailStamp(stamp, blur.blurred(), blur.spread());
    }

    static Blur blur(HeightStamp stamp) {
        return DETAIL.computeIfAbsent(stamp, LandformPlanner::prepare);
    }

    private static Blur prepare(HeightStamp stamp) {
        int width = stamp.width();
        int height = stamp.height();
        float[] values = new float[width * height];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                values[row * width + column] = (float) stamp.pixel(column, row);
            }
        }
        double sigma = Math.max(1.5, Math.min(width, height) / 28.0);
        float[] blurred = gaussian(values, width, height, sigma);
        HeightStamp soft = new HeightStamp(stamp.name(), width, height, blurred, stamp.neutral());
        double sum = 0.0;
        int count = 0;
        for (int row = height / 8; row < height * 7 / 8; row++) {
            for (int column = width / 8; column < width * 7 / 8; column++) {
                double difference = values[row * width + column] - blurred[row * width + column];
                sum += difference * difference;
                count++;
            }
        }
        double spread = count == 0 ? 0.0 : Math.sqrt(sum / count);
        return new Blur(soft, Math.max(1e-4, spread));
    }

    private static float[] gaussian(float[] values, int width, int height, double sigma) {
        int radius = (int) Math.ceil(sigma * 2.5);
        double[] kernel = new double[radius * 2 + 1];
        double total = 0.0;
        for (int offset = -radius; offset <= radius; offset++) {
            kernel[offset + radius] = Math.exp(-offset * offset / (2.0 * sigma * sigma));
            total += kernel[offset + radius];
        }
        for (int index = 0; index < kernel.length; index++) {
            kernel[index] /= total;
        }
        float[] horizontal = new float[values.length];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                double sum = 0.0;
                for (int offset = -radius; offset <= radius; offset++) {
                    sum += values[row * width + Math.clamp(column + offset, 0, width - 1)] * kernel[offset + radius];
                }
                horizontal[row * width + column] = (float) sum;
            }
        }
        float[] result = new float[values.length];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                double sum = 0.0;
                for (int offset = -radius; offset <= radius; offset++) {
                    sum += horizontal[Math.clamp(row + offset, 0, height - 1) * width + column]
                            * kernel[offset + radius];
                }
                result[row * width + column] = (float) Math.clamp(sum, 0.0, 1.0);
            }
        }
        return result;
    }

    /**
     * Sellos repartidos a lo largo de la línea, girados con ella y con espejo y giro al azar. De cada uno se toma solo
     * lo fino; donde se pisan, se mezclan con su huella como peso, así no hay costuras. El detalle se suma con el peso
     * que dejó cada forma (más en las laderas altas, nada en el pie).
     */
    private static void stampDetail(HeightField field, Track track, Settings settings, Shape shape, double[] relief,
                                    Runnable checkCancelled) {
        List<DetailStamp> stamps = new ArrayList<>();
        for (HeightStamp stamp : settings.stamps) {
            stamps.add(detail(stamp));
        }
        int columns = field.columns();
        double[] sum = new double[columns];
        double[] weight = new double[columns];
        double halfWidth = settings.halfWidth;
        double spacing = Math.max(6.0, halfWidth * 0.95);
        int serial = 0;
        double position = 0.0;
        while (position <= track.length + 1e-9) {
            checkCancelled.run();
            int index = (int) Math.min(track.count - 1, Math.round(position / track.length * (track.count - 1)));
            DetailStamp detail = stamps.get((int) Math.floor(StampNoise.unit(settings.seed + 160L, serial)
                    * stamps.size()) % stamps.size());
            double heading = Math.atan2(track.tx[index], -track.tz[index]);
            double angle = heading + Math.toRadians((StampNoise.unit(settings.seed + 161L, serial) - 0.5) * 70.0);
            double radius = halfWidth * (1.05 + 0.25 * StampNoise.unit(settings.seed + 162L, serial));
            boolean mirrored = StampNoise.unit(settings.seed + 163L, serial) < 0.5;
            StampPlacement placement = new StampPlacement(radius, angle, 1.0, mirrored, 0L, 0.0);
            double cx = track.x[index];
            double cz = track.z[index];
            int reach = placement.reach();
            for (int z = (int) Math.floor(cz) - reach; z <= (int) Math.floor(cz) + reach; z++) {
                for (int x = (int) Math.floor(cx) - reach; x <= (int) Math.floor(cx) + reach; x++) {
                    int column = field.index(x, z);
                    if (column < 0 || shape.detailScale[column] <= 0.0) {
                        continue;
                    }
                    double dx = x + 0.5 - cx;
                    double dz = z + 0.5 - cz;
                    double footprint = placement.footprint(dx, dz);
                    if (footprint <= 0.0) {
                        continue;
                    }
                    double fine = (placement.relief(detail.stamp, dx, dz) - placement.relief(detail.blurred, dx, dz))
                            * (detail.stamp.neutral() >= 0.5 ? -1.0 : 1.0) / detail.spread;
                    sum[column] += fine * footprint;
                    weight[column] += footprint;
                }
            }
            serial++;
            position += spacing * (0.85 + 0.3 * StampNoise.unit(settings.seed + 164L, serial));
        }
        double strength = 0.075;
        for (int index = 0; index < columns; index++) {
            if (weight[index] <= 1e-6) {
                continue;
            }
            double fine = Math.clamp(sum[index] / weight[index], -3.0, 3.0);
            double amount = shape.detailScale[index] * strength * fine * Math.min(1.0, weight[index] * 2.0);
            if (relief[index] >= 0.0) {
                relief[index] = Math.max(0.0, relief[index] + amount);
            } else {
                relief[index] = Math.min(0.0, relief[index] - amount);
            }
        }
    }

    // ---------------------------------------------------------------- erosión

    /**
     * La erosión hidráulica solo sobre lo que ha cambiado (más un margen), y una pasada térmica corta que quita los
     * picos de un bloque que dejan las gotas.
     */
    private static void erode(HeightField field, boolean[] touched, Settings settings, Runnable checkCancelled) {
        int sizeX = field.sizeX();
        int sizeZ = field.sizeZ();
        int columns = field.columns();
        // Distancia (en pasos) a lo que no se ha tocado: las gotas corren un poco por fuera y lo que arrastran se
        // queda en ese margen, que se funde con lo de alrededor.
        int[] inside = new int[columns];
        Arrays.fill(inside, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < columns; index++) {
            if (touched[index]) {
                inside[index] = 0;
                queue.add(index);
            }
        }
        // Primero hacia fuera, hasta el margen; después se mide hacia dentro desde el borde del margen.
        int[] outward = new int[columns];
        Arrays.fill(outward, -1);
        for (int index : queue) {
            outward[index] = 0;
        }
        while (!queue.isEmpty()) {
            int index = queue.poll();
            if (outward[index] >= EROSION_MARGIN) {
                continue;
            }
            for (int neighbour : neighbours(index, sizeX, columns)) {
                if (neighbour >= 0 && outward[neighbour] < 0 && field.editableAt(neighbour)
                        && !Double.isNaN(field.at(neighbour))) {
                    outward[neighbour] = outward[index] + 1;
                    queue.add(neighbour);
                }
            }
        }
        int[] depth = new int[columns];
        Arrays.fill(depth, -1);
        for (int index = 0; index < columns; index++) {
            if (outward[index] < 0) {
                continue;
            }
            boolean edge = false;
            for (int neighbour : neighbours(index, sizeX, columns)) {
                edge |= neighbour < 0 || outward[neighbour] < 0;
            }
            if (edge) {
                depth[index] = 0;
                queue.add(index);
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.poll();
            for (int neighbour : neighbours(index, sizeX, columns)) {
                if (neighbour >= 0 && outward[neighbour] >= 0 && depth[neighbour] < 0) {
                    depth[neighbour] = depth[index] + 1;
                    queue.add(neighbour);
                }
            }
        }
        checkCancelled.run();
        HeightField work = new HeightField(field.minimumX(), field.minimumZ(), sizeX, sizeZ);
        int editable = 0;
        for (int index = 0; index < columns; index++) {
            boolean canEdit = outward[index] >= 0;
            work.define(field.xOf(index), field.zOf(index), field.at(index), canEdit,
                    canEdit ? field.lowestAt(index) : field.at(index),
                    canEdit ? field.highestAt(index) : field.at(index));
            if (canEdit) {
                editable++;
            }
        }
        if (editable == 0) {
            return;
        }
        HydraulicErosion.Settings drops = HydraulicErosion.Settings.of(editable, 1.0 + 4.0 * settings.erosion,
                0.8 + 0.6 * settings.erosion, settings.seed + 200L);
        HydraulicErosion.run(work, drops, checkCancelled);
        ThermalErosion.run(work, new ThermalErosion.Settings(3, 58.0, 0.5), checkCancelled);
        double pile = 3.0 + 0.06 * settings.height;
        // En el valle ya hay barrancos: la erosión solo los retoca, sin abrir surcos hondos propios.
        boolean valley = settings.kind == Kind.VALLEY;
        double dig = valley ? 2.5 + 0.1 * settings.height : 4.0 + 0.3 * settings.height;
        double patchScale = Math.max(8.0, settings.halfWidth * 0.45);
        for (int index = 0; index < columns; index++) {
            if (!work.editableAt(index)) {
                continue;
            }
            double before = field.at(index);
            // Las gotas en una ladera lisa abren surcos paralelos y a la misma distancia (como pana): por manchas,
            // y cada surco con su hondura, se quedan en unos sitios sí y en otros no, y unos más que otros.
            double x = field.xOf(index);
            double z = field.zOf(index);
            double patch = smooth((StampNoise.fbm(x / patchScale, z / patchScale, settings.seed + 210L, 3) + 0.15)
                    / 0.6);
            double grain = 0.35 + 0.65 * (0.5 + 0.5 * StampNoise.fbm(x / 5.0, z / 5.0, settings.seed + 211L, 2));
            double strength = (valley ? patch : 0.3 + 0.7 * patch) * grain;
            double change = Math.clamp(work.at(index) - before, -dig, pile) * strength;
            double fade = smooth(depth[index] / (double) EROSION_MARGIN);
            if (change * fade != 0.0) {
                field.setAt(index, before + change * fade);
                touched[index] = true;
            }
        }
    }

    /** Columnas de margen alrededor de lo tocado por las que corren las gotas de la erosión. */
    private static final int EROSION_MARGIN = 6;

    private static int[] neighbours(int index, int sizeX, int columns) {
        int x = index % sizeX;
        return new int[]{x > 0 ? index - 1 : -1, x < sizeX - 1 ? index + 1 : -1,
                index - sizeX >= 0 ? index - sizeX : -1, index + sizeX < columns ? index + sizeX : -1};
    }

    // ---------------------------------------------------------------- ayudas

    /**
     * El nivel de la zona: el terreno de partida desenfocado (dos cajas seguidas, casi una campana), sin contar las
     * columnas sin suelo. Es la base de máximo y aplanar y de dónde se mide la hondura del valle.
     */
    static double[] regional(HeightField field, double radius, Runnable checkCancelled) {
        int sizeX = field.sizeX();
        int sizeZ = field.sizeZ();
        int columns = field.columns();
        double[] values = new double[columns];
        double[] weights = new double[columns];
        for (int index = 0; index < columns; index++) {
            double height = field.at(index);
            if (!Double.isNaN(height)) {
                values[index] = height;
                weights[index] = 1.0;
            }
        }
        int box = Math.max(1, (int) Math.round(radius));
        for (int pass = 0; pass < 2; pass++) {
            checkCancelled.run();
            values = box(values, sizeX, sizeZ, box, true);
            weights = box(weights, sizeX, sizeZ, box, true);
            values = box(values, sizeX, sizeZ, box, false);
            weights = box(weights, sizeX, sizeZ, box, false);
        }
        double[] result = new double[columns];
        for (int index = 0; index < columns; index++) {
            result[index] = weights[index] <= 1e-9 ? Double.NaN : values[index] / weights[index];
        }
        return result;
    }

    /** Media de una caja a lo largo de X o de Z con sumas acumuladas; el borde repite el último valor. */
    private static double[] box(double[] source, int sizeX, int sizeZ, int radius, boolean alongX) {
        double[] result = new double[source.length];
        int lines = alongX ? sizeZ : sizeX;
        int length = alongX ? sizeX : sizeZ;
        double[] prefix = new double[length + 2 * radius + 2];
        for (int line = 0; line < lines; line++) {
            prefix[0] = 0.0;
            for (int position = -radius; position <= length - 1 + radius; position++) {
                int at = Math.clamp(position, 0, length - 1);
                double value = alongX ? source[line * sizeX + at] : source[at * sizeX + line];
                prefix[position + radius + 1] = prefix[position + radius] + value;
            }
            for (int position = 0; position < length; position++) {
                double total = prefix[position + 2 * radius + 1] - prefix[position];
                result[alongX ? line * sizeX + position : position * sizeX + line] = total / (2 * radius + 1);
            }
        }
        return result;
    }

    private static double regionalAt(HeightField field, double[] regional, double x, double z) {
        int column = field.index((int) Math.floor(x), (int) Math.floor(z));
        return column < 0 ? Double.NaN : regional[column];
    }

    private static double[] movingAverage(double[] values, int radius, boolean loop) {
        int count = values.length;
        double[] result = new double[count];
        int period = loop ? count - 1 : count;
        for (int index = 0; index < count; index++) {
            double sum = 0.0;
            int used = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int at = loop ? Math.floorMod(index + offset, period) : index + offset;
                if (at < 0 || at >= count) {
                    continue;
                }
                sum += values[at];
                used++;
            }
            result[index] = sum / Math.max(1, used);
        }
        return result;
    }

    private static double average(double[] values, int from, int to) {
        double sum = 0.0;
        int count = 0;
        for (int index = Math.max(0, from); index < Math.min(values.length, to); index++) {
            sum += values[index];
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static void reverse(double[] values) {
        for (int left = 0, right = values.length - 1; left < right; left++, right--) {
            double swap = values[left];
            values[left] = values[right];
            values[right] = swap;
        }
    }

    private static void checkCancelledEvery(int index, Runnable checkCancelled) {
        if ((index & 4095) == 0) {
            checkCancelled.run();
        }
    }

    static double smooth(double t) {
        double clamped = Math.clamp(t, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double smoother(double t) {
        double clamped = Math.clamp(t, 0.0, 1.0);
        return clamped * clamped * clamped * (clamped * (clamped * 6.0 - 15.0) + 10.0);
    }

    static double smoothMax(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.max(a, b) + h * h * k * 0.25;
    }

    static double smoothMin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0.0) / k;
        return Math.min(a, b) - h * h * k * 0.25;
    }

    static String fold(String input) {
        return input == null ? "" : input.strip().toLowerCase(Locale.ROOT).replace("ñ", "n").replace("á", "a")
                .replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
