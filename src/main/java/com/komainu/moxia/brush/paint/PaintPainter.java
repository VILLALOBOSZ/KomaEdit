package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushNoise;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El pincel de pintura en Java puro: recolorea la superficie bajo cada toque con un degradado de la paleta, sin
 * cambiar ninguna altura.
 *
 * <p>Cada bloque recibe un tono objetivo {@code P} (de 0 al último de la paleta) según el modo
 * ({@link PaintGradient}): la intensidad de la huella, su altura, la pendiente de su columna o un ruido, movido un
 * poco con otro ruido para que las fronteras no salgan rectas. Un trazo lo acerca a ese tono con la fuerza del pincel
 * por la intensidad de la huella ({@code alfa}): un bloque de la paleta que estaba en el tono {@code p} al empezar el
 * trazo pasa a {@code p + (P - p)·alfa}, redondeado al azar entre los dos tonos vecinos (la trama, que evita las
 * bandas). Un bloque que no es de la paleta (la hierba de alrededor) se cubre con la probabilidad {@code alfa}: el
 * borde de la pincelada queda salpicado y se funde con el terreno.</p>
 *
 * <p>La fuerza es la de un trazo, como la opacidad de un pincel de dibujo: los toques que se solapan al arrastrar no
 * se suman, cada bloque se queda con el {@code alfa} del toque más fuerte que lo pisó ({@link Memory}). Así arrastrar
 * despacio o deprisa pinta lo mismo, y son las pasadas (otro trazo encima) las que empujan un poco más hacia el tono
 * sin saltar del primero al último.</p>
 *
 * <p>El azar sale de un hash de la posición y la sal del trazo: con las mismas entradas sale siempre lo mismo.</p>
 */
public final class PaintPainter {
    /** Con menos intensidad, la huella no pinta. */
    private static final double MIN_INTENSITY = 1.0E-3;
    /** En el modo por la huella, intensidad a partir de la que el toque cubre del todo (el resto es su borde). */
    private static final double INTENSITY_COVER = 0.15;
    private static final long COVER_SALT = 0x434F_5645_52L;
    private static final int NONE = Integer.MIN_VALUE;

    private PaintPainter() {
    }

    /**
     * Un toque del pincel.
     *
     * @param x      centro en el mundo (el centro del bloque pulsado es {@code bloque + 0.5})
     * @param y      altura del clic; se ajusta a la superficie de la columna del centro si está cerca
     * @param radius radio ya escalado
     * @param angle  giro de la huella en radianes (0 = la parte de arriba de la huella hacia el norte)
     */
    public record Dab(double x, double y, double z, double radius, double angle) {
        public Dab {
            if (!(radius >= 0.5)) {
                throw new IllegalArgumentException("El radio del toque es demasiado pequeño");
            }
        }
    }

    /**
     * Cómo pinta una pasada.
     *
     * @param vertical    bloques por encima y por debajo del centro que mira cada columna
     * @param heightLow   Y del primer tono por altura; {@code NaN}: se mide en el primer toque
     * @param noise       el campo del degradado por ruido, de 0 a 1 (solo {@link PaintGradient#NOISE})
     * @param jitterNoise el campo que mueve las fronteras, de 0 a 1
     * @param invert      la paleta al revés (el clic izquierdo)
     */
    public record Settings(int paletteSize, PaintGradient gradient, PaintFootprint footprint, double strength,
                           double hardness, double jitter, int layers, boolean surfaceOnly, boolean shaped,
                           int vertical, double heightLow, double heightHigh, double slopeLow, double slopeHigh,
                           BrushNoise noise, BrushNoise jitterNoise, boolean invert) {
        public Settings {
            if (paletteSize < 1) {
                throw new IllegalArgumentException("La paleta está vacía");
            }
            if (layers < 1) {
                throw new IllegalArgumentException("Hay que pintar al menos una capa");
            }
            if (!(slopeHigh > slopeLow)) {
                throw new IllegalArgumentException(
                        "La pendiente del último tono tiene que ser mayor que la del primero");
            }
        }

        /** Los mismos ajustes con la franja de alturas ya medida. */
        public Settings withHeights(double low, double high) {
            return new Settings(paletteSize, gradient, footprint, strength, hardness, jitter, layers, surfaceOnly,
                    shaped, vertical, low, high, slopeLow, slopeHigh, noise, jitterNoise, invert);
        }

        /** Los mismos ajustes con la paleta al revés (o del derecho). */
        public Settings inverted(boolean value) {
            return new Settings(paletteSize, gradient, footprint, strength, hardness, jitter, layers, surfaceOnly,
                    shaped, vertical, heightLow, heightHigh, slopeLow, slopeHigh, noise, jitterNoise, value);
        }
    }

    @FunctionalInterface
    public interface Sink {
        void paint(int x, int y, int z, int paletteIndex);
    }

    /**
     * @param changed   bloques que cambian de tono
     * @param heightLow la franja de alturas usada (la medida, si era automática), para los toques siguientes
     */
    public record Result(int changed, double heightLow, double heightHigh) {
    }

    /**
     * Lo que recuerda un trazo de cada bloque que pisa: su tono al empezar, el {@code alfa} más fuerte que recibió y
     * el tono al que va. Se crea uno por trazo y pasa de una tanda de toques a la siguiente; un trazo nuevo empieza
     * de lo ya pintado. Lo usa un solo hilo cada vez.
     */
    public static final class Memory {
        /** Bloques recordados como mucho; con más, el trazo sigue como si empezara otro. */
        static final int MAX_BLOCKS = 600_000;
        private final long salt;
        private final Map<Long, Mark> marks = new HashMap<>();

        /** @param salt la sal del trazo: la trama y lo que se cubre salen de aquí */
        public Memory(long salt) {
            this.salt = salt;
        }

        public long salt() {
            return salt;
        }

        private Mark mark(long key, int origin) {
            Mark mark = marks.get(key);
            if (mark == null) {
                if (marks.size() >= MAX_BLOCKS) {
                    marks.clear();
                }
                mark = new Mark(origin);
                marks.put(key, mark);
            }
            return mark;
        }
    }

    /** Un bloque en un trazo. */
    private static final class Mark {
        private final int origin;
        private double alpha;
        private int painted;

        private Mark(int origin) {
            this.origin = origin;
            this.painted = origin;
        }
    }

    /** Hasta dónde llega un toque desde su centro, en bloques. */
    public static int reach(Dab dab, PaintFootprint footprint) {
        return (int) Math.ceil(dab.radius() * footprint.extent()) + 1;
    }

    /** Un trazo de un solo golpe (un clic, o una pasada entera en las pruebas). */
    public static Result paint(PaintWorld world, List<Dab> dabs, Settings settings, long salt, Sink sink,
                               Runnable checkCancelled) {
        return paint(world, dabs, settings, new Memory(salt), sink, checkCancelled);
    }

    /**
     * Pinta una tanda de toques de un trazo y entrega cada bloque que acaba con otro tono (una sola vez, con el
     * último).
     *
     * @param memory         lo que lleva pintado el trazo (las tandas anteriores)
     * @param checkCancelled se llama de vez en cuando; lanza para cortar
     */
    public static Result paint(PaintWorld world, List<Dab> dabs, Settings settings, Memory memory, Sink sink,
                               Runnable checkCancelled) {
        if (dabs.isEmpty()) {
            return new Result(0, settings.heightLow(), settings.heightHigh());
        }
        Surface surface = Surface.around(world, dabs, settings.footprint());
        Settings pass = settings;
        if (pass.gradient() == PaintGradient.HEIGHT && Double.isNaN(pass.heightLow())) {
            double[] measured = surface.heights(dabs.getFirst(), settings.footprint());
            pass = pass.withHeights(measured[0], measured[1]);
        }
        Set<Long> touched = new LinkedHashSet<>();
        for (Dab dab : dabs) {
            checkCancelled.run();
            paintDab(world, surface, dab, pass, memory, touched);
        }
        int changed = 0;
        for (long key : touched) {
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            int painted = memory.marks.get(key).painted;
            if (painted >= 0 && world.paletteIndex(x, y, z) != painted) {
                sink.paint(x, y, z, painted);
                changed++;
            }
        }
        return new Result(changed, pass.heightLow(), pass.heightHigh());
    }

    private static void paintDab(PaintWorld world, Surface surface, Dab dab, Settings settings, Memory memory,
                                 Set<Long> touched) {
        int reach = reach(dab, settings.footprint());
        int centerX = (int) Math.floor(dab.x());
        int centerZ = (int) Math.floor(dab.z());
        int centerY = (int) Math.floor(dab.y());
        int ground = surface.top(centerX, centerZ);
        if (ground != NONE && Math.abs(ground - centerY) <= settings.vertical()) {
            centerY = ground;
        }
        double cos = Math.cos(dab.angle());
        double sin = Math.sin(dab.angle());
        int last = settings.paletteSize() - 1;
        int bottom = Math.max(world.minimumY(), centerY - settings.vertical());
        int top = Math.min(world.maximumY(), centerY + settings.vertical());
        for (int z = centerZ - reach; z <= centerZ + reach; z++) {
            for (int x = centerX - reach; x <= centerX + reach; x++) {
                double dx = x + 0.5 - dab.x();
                double dz = z + 0.5 - dab.z();
                // Del mundo a la huella, como los sellos de relieve: deshacer el giro.
                double u = (dx * cos + dz * sin) / dab.radius();
                double v = (-dx * sin + dz * cos) / dab.radius();
                double intensity = PaintFootprint.intensity(settings.footprint().shape(u, v), settings.hardness());
                if (intensity < MIN_INTENSITY) {
                    continue;
                }
                double alpha = settings.strength() * (settings.gradient() == PaintGradient.INTENSITY
                        ? Math.min(1.0, intensity / INTENSITY_COVER) : intensity);
                for (int y = top; y >= bottom; y--) {
                    PaintWorld.Kind kind = world.kind(x, y, z);
                    if (kind != PaintWorld.Kind.GROUND && !(kind == PaintWorld.Kind.SHAPED && settings.shaped())) {
                        continue;
                    }
                    if (settings.surfaceOnly() && !exposed(world, x, y, z, settings.layers())) {
                        continue;
                    }
                    if (!world.allowed(x, y, z)) {
                        continue;
                    }
                    long key = pack(x, y, z);
                    Mark mark = memory.mark(key, world.paletteIndex(x, y, z));
                    if (alpha <= mark.alpha) {
                        // Un toque más flojo que otro del mismo trazo no cambia nada.
                        continue;
                    }
                    mark.alpha = alpha;
                    double target = target(surface, settings, x, y, z, intensity) * last;
                    // La trama es fija dentro del trazo: el bloque solo avanza al subir su alfa.
                    double dither = BrushNoise.unit(x, y, z, memory.salt());
                    int next;
                    if (mark.origin < 0) {
                        if (BrushNoise.unit(x, y, z, memory.salt() ^ COVER_SALT) >= alpha) {
                            continue;
                        }
                        next = (int) Math.floor(target + dither);
                    } else {
                        next = (int) Math.floor(mark.origin + (target - mark.origin) * alpha + dither);
                    }
                    next = Math.clamp(next, 0, last);
                    if (next != mark.painted) {
                        mark.painted = next;
                        touched.add(key);
                    }
                }
            }
        }
    }

    /** Posición en el degradado de 0 a 1 para el bloque, con la variación de las fronteras. */
    static double target(Surface surface, Settings settings, int x, int y, int z, double intensity) {
        double t = switch (settings.gradient()) {
            case INTENSITY -> 1.0 - intensity;
            case HEIGHT -> (y - settings.heightLow()) / (settings.heightHigh() - settings.heightLow());
            case SLOPE -> (surface.slope(x, z) - settings.slopeLow()) / (settings.slopeHigh() - settings.slopeLow());
            case NOISE -> settings.noise() == null ? 0.5 : settings.noise().sample(x, y, z);
        };
        if (settings.invert()) {
            t = 1.0 - t;
        }
        if (settings.jitter() > 0.0 && settings.jitterNoise() != null) {
            t += settings.jitter() * (settings.jitterNoise().sample(x, y, z) * 2.0 - 1.0);
        }
        return Math.clamp(t, 0.0, 1.0);
    }

    /** Si hay aire (o algo abierto) a {@code layers} bloques o menos en alguna de las seis direcciones. */
    static boolean exposed(PaintWorld world, int x, int y, int z, int layers) {
        for (int step = 1; step <= layers; step++) {
            if (!world.kind(x, y + step, z).covers() || !world.kind(x + step, y, z).covers()
                    || !world.kind(x - step, y, z).covers() || !world.kind(x, y, z + step).covers()
                    || !world.kind(x, y, z - step).covers() || !world.kind(x, y - step, z).covers()) {
                return true;
            }
        }
        return false;
    }

    // ── Posiciones empaquetadas (26 bits X, 12 Y, 26 Z, como PackedBlockPlan) ─────────────────────────────

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FF_FFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FF_FFFF);
    }

    static int unpackX(long key) {
        return (int) (key >> 38);
    }

    static int unpackY(long key) {
        return (int) (key << 26 >> 52);
    }

    static int unpackZ(long key) {
        return (int) (key << 38 >> 38);
    }

    /**
     * Las columnas bajo los toques: la altura del suelo más alto de cada una (lo que no es {@link PaintWorld.Kind#OPEN}
     * ni bloqueado) y su pendiente, calculadas al pedirlas y guardadas.
     */
    static final class Surface {
        private final PaintWorld world;
        private final int minimumX;
        private final int minimumZ;
        private final int sizeX;
        private final int sizeZ;
        private final int[] tops;
        private final double[] slopes;

        private Surface(PaintWorld world, int minimumX, int minimumZ, int maximumX, int maximumZ) {
            this.world = world;
            this.minimumX = minimumX;
            this.minimumZ = minimumZ;
            this.sizeX = maximumX - minimumX + 1;
            this.sizeZ = maximumZ - minimumZ + 1;
            this.tops = new int[Math.multiplyExact(sizeX, sizeZ)];
            this.slopes = new double[tops.length];
            Arrays.fill(tops, Integer.MAX_VALUE);
            Arrays.fill(slopes, Double.NaN);
        }

        static Surface around(PaintWorld world, List<Dab> dabs, PaintFootprint footprint) {
            int minimumX = Integer.MAX_VALUE;
            int minimumZ = Integer.MAX_VALUE;
            int maximumX = Integer.MIN_VALUE;
            int maximumZ = Integer.MIN_VALUE;
            for (Dab dab : dabs) {
                // Dos más por cada lado para la pendiente del borde.
                int reach = reach(dab, footprint) + 2;
                minimumX = Math.min(minimumX, (int) Math.floor(dab.x()) - reach);
                maximumX = Math.max(maximumX, (int) Math.floor(dab.x()) + reach);
                minimumZ = Math.min(minimumZ, (int) Math.floor(dab.z()) - reach);
                maximumZ = Math.max(maximumZ, (int) Math.floor(dab.z()) + reach);
            }
            return new Surface(world, minimumX, minimumZ, maximumX, maximumZ);
        }

        private int index(int x, int z) {
            int localX = x - minimumX;
            int localZ = z - minimumZ;
            return localX < 0 || localZ < 0 || localX >= sizeX || localZ >= sizeZ ? -1 : localZ * sizeX + localX;
        }

        /** Y del suelo de la columna, o {@code NONE}. */
        int top(int x, int z) {
            int index = index(x, z);
            if (index < 0) {
                return find(x, z);
            }
            if (tops[index] == Integer.MAX_VALUE) {
                tops[index] = find(x, z);
            }
            return tops[index];
        }

        private int find(int x, int z) {
            for (int y = world.maximumY(); y >= world.minimumY(); y--) {
                PaintWorld.Kind kind = world.kind(x, y, z);
                if (kind == PaintWorld.Kind.GROUND || kind == PaintWorld.Kind.SHAPED) {
                    return y;
                }
            }
            return NONE;
        }

        /** Pendiente de la columna en grados, por diferencias de la altura del suelo a dos bloques. */
        double slope(int x, int z) {
            int index = index(x, z);
            if (index >= 0 && !Double.isNaN(slopes[index])) {
                return slopes[index];
            }
            double gradientX = difference(x, z, 1, 0);
            double gradientZ = difference(x, z, 0, 1);
            double degrees = Math.toDegrees(Math.atan(Math.hypot(gradientX, gradientZ)));
            if (index >= 0) {
                slopes[index] = degrees;
            }
            return degrees;
        }

        private double difference(int x, int z, int stepX, int stepZ) {
            int center = top(x, z);
            for (int distance = 2; distance >= 1; distance--) {
                int ahead = top(x + stepX * distance, z + stepZ * distance);
                int behind = top(x - stepX * distance, z - stepZ * distance);
                if (ahead != NONE && behind != NONE) {
                    return (ahead - behind) / (2.0 * distance);
                }
                if (center != NONE && ahead != NONE) {
                    return (ahead - center) / (double) distance;
                }
                if (center != NONE && behind != NONE) {
                    return (center - behind) / (double) distance;
                }
            }
            return 0.0;
        }

        /**
         * La franja de alturas del degradado por altura: del 5 % al 95 % de los suelos bajo el toque, con al menos 8
         * bloques de alto, para que pintar una colina reparta toda la paleta entre su pie y su cima.
         */
        double[] heights(Dab dab, PaintFootprint footprint) {
            int reach = reach(dab, footprint);
            int centerX = (int) Math.floor(dab.x());
            int centerZ = (int) Math.floor(dab.z());
            int[] found = new int[(reach * 2 + 1) * (reach * 2 + 1)];
            int count = 0;
            for (int z = centerZ - reach; z <= centerZ + reach; z++) {
                for (int x = centerX - reach; x <= centerX + reach; x++) {
                    int height = top(x, z);
                    if (height != NONE) {
                        found[count++] = height;
                    }
                }
            }
            return heightRange(found, count, Math.floor(dab.y()));
        }
    }

    /**
     * La franja de alturas del degradado por altura con unas alturas de suelo: del 5 % al 95 %, con al menos 8
     * bloques de alto. Sin muestras, 4 bloques arriba y abajo de {@code fallback}. Ordena {@code heights}.
     */
    public static double[] heightRange(int[] heights, int count, double fallback) {
        double low;
        double high;
        if (count == 0) {
            low = fallback - 4.0;
            high = fallback + 4.0;
        } else {
            Arrays.sort(heights, 0, count);
            low = heights[(int) Math.floor((count - 1) * 0.05)];
            high = heights[(int) Math.ceil((count - 1) * 0.95)];
        }
        if (high - low < 8.0) {
            double middle = (low + high) / 2.0;
            low = middle - 4.0;
            high = middle + 4.0;
        }
        return new double[]{low, high};
    }
}
