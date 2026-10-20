package com.komainu.moxia.shape.tube;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.GeometryLimitException;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeOptions;
import com.komainu.moxia.shape.ShapeParameters;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Dobla una plantilla de bloques (el portapapeles de WorldEdit) a lo largo del recorrido.
 *
 * <p>Igual que las figuras de tubo, trabaja al revés: recorre los bloques del mundo cercanos al eje, calcula en qué
 * punto de la plantilla cae cada uno y copia ese bloque. Así la estructura no se rompe por fuera de las curvas.
 * Cada bloque colocado lleva cuántos cuartos de vuelta hay que girarlo para que escaleras, troncos o vallas sigan
 * la dirección del recorrido.</p>
 *
 * <p>Con una {@link Section} (la de {@code //ezspline structure}) la sección cambia a lo largo del recorrido como en
 * los tubos: el radio por fotogramas escala la plantilla en cada punto, {@code -r} la gira alrededor del eje y
 * {@code -t} la retuerce. La plantilla avanza en sus propias unidades, así donde el radio crece las copias también
 * se alargan y conservan sus proporciones.</p>
 */
public final class TubeBender {
    /** Peso vertical al buscar el eje en modo vertical: prima la columna sobre la distancia en altura. */
    private static final double VERTICAL_WEIGHT = 0.3;
    private static final long MAX_TEMPLATE_VOLUME = 8_000_000L;
    private static final long MAX_CANDIDATES = 64_000_000L;

    private TubeBender() {
    }

    /** Eje del portapapeles que avanzará con el recorrido: el pedido o, en automático, el lado horizontal más largo. */
    public static Axis resolvedAxis(Template template, Axis requested) {
        return requested == Axis.AUTO ? template.longestHorizontalAxis() : requested;
    }

    public static int paletteIndex(int encoded) {
        return encoded >>> 2;
    }

    public static int quarterTurns(int encoded) {
        return encoded & 3;
    }

    /**
     * Calcula los bloques de la estructura doblada.
     *
     * @return posición empaquetada → índice de paleta y cuartos de vuelta (ver {@link #paletteIndex(int)} y
     * {@link #quarterTurns(int)}); el giro es el ángulo que hay que pasar a {@code AffineTransform.rotateY}
     * dividido entre 90.
     */
    public static Long2IntOpenHashMap bend(SampledPath path, Template template, Settings settings, int limit) {
        return bend(path, template, settings, Section.NONE, limit);
    }

    /** Como {@link #bend(SampledPath, Template, Settings, int)}, con la sección variable de {@code section}. */
    public static Long2IntOpenHashMap bend(SampledPath path, Template template, Settings settings, Section section,
                                           int limit) {
        Grid grid = new Grid(template, settings.axis());
        double templateReach = grid.reach(settings.offsetUp());
        int baseRadius = section.scaled() ? section.baseRadius() : Math.clamp((int) Math.ceil(templateReach), 1, 128);
        // Radio de la plantilla en bloques del mundo cuando la escala es 1.
        double inherentRadius = section.scaled() ? section.inherentRadius() : baseRadius;
        double reach = templateReach * section.maximumRadius(baseRadius) / inherentRadius;
        if (reach > 128.0) {
            throw new IllegalArgumentException("La estructura es demasiado ancha o alta para doblarla: "
                    + "como mucho 128 bloques desde el eje del recorrido");
        }
        Map<String, Double> options = new java.util.LinkedHashMap<>(section.options());
        options.put("normal", (double) settings.normalMode().ordinal());
        options.put("tension", settings.tension());
        options.put("bias", settings.bias());
        options.put("continuity", settings.continuity());
        TubeSettings tubeSettings = TubeSettings.from(new ShapeParameters(
                PathShape.SWEEP,
                baseRadius,
                6,
                1,
                0,
                new ShapeOptions(options)
        ));
        TubeCenterline line = TubeCenterline.create(path, tubeSettings, TubeCurve.NONE);
        Layout layout = Layout.of(line, section.scaled() ? inherentRadius : Double.NaN, grid, settings);
        LongArrayList bricks = markBricks(line, templateReach / inherentRadius);
        if ((long) bricks.size() * 512L > MAX_CANDIDATES) {
            throw new IllegalArgumentException("La estructura doblada es demasiado voluminosa; acorta el recorrido");
        }

        boolean vertical = settings.normalMode() == TubeSettings.NormalMode.UPRIGHT;
        TubeCenterline.SegmentTree columnTree = vertical ? columnTree(line) : null;
        TubeCenterline.TubePoint point = new TubeCenterline.TubePoint();
        double forwardAngle = Math.atan2(grid.forwardZ, grid.forwardX);
        Long2IntOpenHashMap result = new Long2IntOpenHashMap();
        int checked = 0;
        for (int index = 0; index < bricks.size(); index++) {
            long brick = bricks.getLong(index);
            int originX = PackedBlockPlan.unpackX(brick) << 3;
            int originY = PackedBlockPlan.unpackY(brick) << 3;
            int originZ = PackedBlockPlan.unpackZ(brick) << 3;
            for (int localX = 0; localX < 8; localX++) {
                for (int localY = 0; localY < 8; localY++) {
                    int blockY = originY + localY;
                    if (blockY < -2048 || blockY > 2047) {
                        continue;
                    }
                    for (int localZ = 0; localZ < 8; localZ++) {
                        if ((checked++ & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
                            throw new CancellationException();
                        }
                        int blockX = originX + localX;
                        int blockZ = originZ + localZ;
                        double x = blockX + 0.5;
                        double y = blockY + 0.5;
                        double z = blockZ + 0.5;
                        Sample sample = vertical
                                ? columnSample(line, columnTree, point, x, y, z)
                                : frameSample(line, point, x, y, z);
                        if (sample == null) {
                            continue;
                        }
                        double along = layout.along(section.scaled() ? sample.longitudinal() * inherentRadius
                                : sample.distance());
                        if (Double.isNaN(along)) {
                            continue;
                        }
                        // Gira la sección como los perfiles de tubo y la lleva a unidades de la plantilla.
                        double cosine = Math.cos(sample.roll());
                        double sine = Math.sin(sample.roll());
                        double scale = inherentRadius / sample.radius();
                        double side = (sample.side() * cosine + sample.up() * sine) * scale;
                        double up = (-sample.side() * sine + sample.up() * cosine) * scale;
                        int cell = grid.cell(
                                (int) Math.floor(along),
                                nearestCell(up - settings.offsetUp()),
                                nearestCell(side)
                        );
                        if (cell < 0) {
                            continue;
                        }
                        int turns = Math.floorMod(
                                (int) Math.round((forwardAngle - sample.heading()) / (Math.PI * 0.5)), 4);
                        result.put(PackedBlockPlan.pack(blockX, blockY, blockZ), cell << 2 | turns);
                        if (result.size() > limit) {
                            throw new GeometryLimitException(limit);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Localización perpendicular al eje (modos continuo y horizontal): la sección se inclina con la pendiente. */
    private static Sample frameSample(TubeCenterline line, TubeCenterline.TubePoint point, double x, double y,
                                      double z) {
        line.locate(x, y, z, point);
        if (point.beyond > 0.0) {
            return null;
        }
        int segment = point.segment;
        double amount = point.amount;
        double tangentX = line.tx[segment] + (line.tx[segment + 1] - line.tx[segment]) * amount;
        double tangentZ = line.tz[segment] + (line.tz[segment + 1] - line.tz[segment]) * amount;
        return new Sample(point.distance, point.localX, point.localY, Math.atan2(tangentZ, tangentX), point.radius,
                point.longitudinal, point.roll);
    }

    /**
     * Localización por columnas (modo vertical): el bloque se asocia al punto del eje que tiene en su vertical,
     * de modo que en una cuesta los postes y muros siguen a plomo y la altura se mide en bloques del mundo.
     */
    private static Sample columnSample(
            TubeCenterline line,
            TubeCenterline.SegmentTree tree,
            TubeCenterline.TubePoint point,
            double x,
            double y,
            double z
    ) {
        tree.nearest(x, y * VERTICAL_WEIGHT, z, point);
        int segment = point.segment;
        int next = segment + 1;
        double amount = point.amount;
        double centerX = line.px[segment] + (line.px[next] - line.px[segment]) * amount;
        double centerY = line.py[segment] + (line.py[next] - line.py[segment]) * amount;
        double centerZ = line.pz[segment] + (line.pz[next] - line.pz[segment]) * amount;
        double sideX = line.sx[segment] + (line.sx[next] - line.sx[segment]) * amount;
        double sideZ = line.sz[segment] + (line.sz[next] - line.sz[segment]) * amount;
        double sideLength = Math.sqrt(sideX * sideX + sideZ * sideZ);
        if (sideLength < 1.0E-9) {
            return null;
        }
        sideX /= sideLength;
        sideZ /= sideLength;
        double headingX = -sideZ;
        double headingZ = sideX;
        double dx = x - centerX;
        double dz = z - centerZ;
        double tangentX = line.tx[segment] + (line.tx[next] - line.tx[segment]) * amount;
        double tangentY = line.ty[segment] + (line.ty[next] - line.ty[segment]) * amount;
        double tangentZ = line.tz[segment] + (line.tz[next] - line.tz[segment]) * amount;
        double horizontal = Math.max(0.2, Math.sqrt(tangentX * tangentX + tangentZ * tangentZ));
        // Desliza el punto del eje hasta la vertical del bloque: avanza en arco y sube con la pendiente.
        double shift = (dx * headingX + dz * headingZ) / horizontal;
        double distance = line.distance[segment] + (line.distance[next] - line.distance[segment]) * amount + shift;
        if (!line.closed && (distance < 0.0 || distance > line.length)) {
            return null;
        }
        double base = centerY + shift * tangentY;
        double radius = line.radius[segment] + (line.radius[next] - line.radius[segment]) * amount;
        double stretch = line.stretch[segment] + (line.stretch[next] - line.stretch[segment]) * amount;
        double longitudinal = line.longitudinal[segment]
                + (line.longitudinal[next] - line.longitudinal[segment]) * amount + shift / (radius * stretch);
        double roll = line.roll[segment] + (line.roll[next] - line.roll[segment]) * amount;
        return new Sample(distance, dx * sideX + dz * sideZ, y - base, Math.atan2(headingZ, headingX), radius,
                longitudinal, roll);
    }

    private static TubeCenterline.SegmentTree columnTree(TubeCenterline line) {
        double[] scaled = new double[line.count];
        for (int index = 0; index < line.count; index++) {
            scaled[index] = line.py[index] * VERTICAL_WEIGHT;
        }
        return new TubeCenterline.SegmentTree(line.px, scaled, line.pz);
    }

    /** @param reachPerRadius alcance de la plantilla por cada bloque de radio del eje */
    private static LongArrayList markBricks(TubeCenterline line, double reachPerRadius) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList ordered = new LongArrayList();
        int stride = Math.max(1, (int) Math.round(1.0 / TubeCenterline.STEP));
        double margin = 1.0 + stride * TubeCenterline.STEP;
        for (int index = 0; index < line.count; index += stride) {
            // El radio puede cambiar dentro del tramo: se toma el mayor de las muestras que cubre la esfera.
            double radius = line.radius[index];
            for (int inner = 1; inner < stride && index + inner < line.count; inner++) {
                radius = Math.max(radius, line.radius[index + inner]);
            }
            if (index > 0) {
                radius = Math.max(radius, line.radius[index - 1]);
            }
            TubeVoxelizer.markSphere(seen, ordered, line.px[index], line.py[index], line.pz[index],
                    reachPerRadius * radius + margin);
        }
        int last = line.count - 1;
        TubeVoxelizer.markSphere(seen, ordered, line.px[last], line.py[last], line.pz[last],
                reachPerRadius * line.radius[last] + margin);
        return ordered;
    }

    /**
     * Celda más cercana con los empates (justo en el borde entre dos bloques) siempre hacia abajo: si no, el ruido
     * de coma flotante mete a veces la celda de arriba y la de abajo en la misma columna.
     */
    private static int nearestCell(double offset) {
        return (int) Math.floor(offset + 0.5 - 1.0E-6);
    }

    /**
     * Un bloque visto desde el eje: distancia de arco, lado y altura en bloques del mundo, rumbo, radio del eje en ese
     * punto, avance longitudinal (en radios, como en los tubos) y giro de la sección.
     */
    private record Sample(double distance, double side, double up, double heading, double radius, double longitudinal,
                          double roll) {
    }

    /**
     * Reparte las copias a lo largo del recorrido y traduce el avance, en unidades de la plantilla, a su coordenada a
     * lo largo. Sin sección variable una unidad es un bloque de arco.
     */
    private record Layout(Mode mode, boolean closed, double length, double period, double templateLength,
                          double start, double used, double scale) {
        /** @param inherentRadius radio propio de la plantilla con sección variable; {@code NaN} sin ella */
        static Layout of(TubeCenterline line, double inherentRadius, Grid grid, Settings settings) {
            double templateLength = grid.lengthAlong;
            double period = Math.max(1.0, templateLength + settings.gap());
            double length = Double.isNaN(inherentRadius) ? line.length
                    : line.longitudinal[line.count - 1] * inherentRadius;
            if (settings.mode() == Mode.STRETCH) {
                return new Layout(Mode.STRETCH, line.closed, length, period, templateLength, 0.0, length,
                        templateLength / length);
            }
            if (line.closed) {
                int copies = Math.max(1, (int) Math.round(length / period));
                return new Layout(Mode.REPEAT, true, length, period, templateLength, 0.0, length,
                        copies * period / length);
            }
            int copies = (int) Math.floor((length + settings.gap()) / period + 1.0E-6);
            if (copies < 1) {
                throw new IllegalArgumentException(String.format(Locale.ROOT,
                        "El recorrido (%.0f bloques) es más corto que la estructura (%d bloques); "
                                + "alárgalo o usa el modo estirar", length, (int) templateLength));
            }
            double used = copies * period - settings.gap();
            return new Layout(Mode.REPEAT, false, length, period, templateLength, (length - used) * 0.5, used, 1.0);
        }

        /** Coordenada a lo largo de la plantilla en [0, longitud) o {@code NaN} si cae en un hueco o fuera. */
        double along(double distance) {
            if (Double.isNaN(distance)) {
                return Double.NaN;
            }
            if (mode == Mode.STRETCH) {
                return Math.clamp(distance * scale, 0.0, templateLength - 1.0E-9);
            }
            double position = closed ? distance * scale : distance - start;
            if (position < 0.0 || position >= (closed ? length * scale : used)) {
                return Double.NaN;
            }
            double local = position - Math.floor(position / period) * period;
            return local < templateLength ? local : Double.NaN;
        }
    }

    /** Plantilla reordenada en ejes del recorrido: a lo largo, altura y lado. */
    private static final class Grid {
        final int forwardX;
        final int forwardZ;
        final int lengthAlong;
        private final int minimumAlong;
        private final int minimumUp;
        private final int minimumSide;
        private final int height;
        private final int width;
        private final int[] cells;

        Grid(Template template, Axis requested) {
            if (template.size() == 0) {
                throw new IllegalArgumentException("El portapapeles no contiene bloques");
            }
            Axis axis = resolvedAxis(template, requested);
            forwardX = axis.x;
            forwardZ = axis.z;
            int minimumA = Integer.MAX_VALUE;
            int maximumA = Integer.MIN_VALUE;
            int minimumH = Integer.MAX_VALUE;
            int maximumH = Integer.MIN_VALUE;
            int minimumB = Integer.MAX_VALUE;
            int maximumB = Integer.MIN_VALUE;
            for (int index = 0; index < template.size(); index++) {
                int along = along(template.x(index), template.z(index));
                int side = side(template.x(index), template.z(index));
                minimumA = Math.min(minimumA, along);
                maximumA = Math.max(maximumA, along);
                minimumB = Math.min(minimumB, side);
                maximumB = Math.max(maximumB, side);
                minimumH = Math.min(minimumH, template.y(index));
                maximumH = Math.max(maximumH, template.y(index));
            }
            minimumAlong = minimumA;
            minimumUp = minimumH;
            minimumSide = minimumB;
            lengthAlong = maximumA - minimumA + 1;
            height = maximumH - minimumH + 1;
            width = maximumB - minimumB + 1;
            long volume = (long) lengthAlong * height * width;
            if (volume > MAX_TEMPLATE_VOLUME) {
                throw new IllegalArgumentException("El portapapeles es demasiado grande para doblarlo");
            }
            cells = new int[(int) volume];
            java.util.Arrays.fill(cells, -1);
            for (int index = 0; index < template.size(); index++) {
                int along = along(template.x(index), template.z(index)) - minimumAlong;
                int up = template.y(index) - minimumUp;
                int side = side(template.x(index), template.z(index)) - minimumSide;
                cells[(along * height + up) * width + side] = template.value(index);
            }
        }

        /** Lado = Y × adelante, el mismo convenio que el marco del tubo. */
        private int side(int x, int z) {
            return x * forwardZ - z * forwardX;
        }

        private int along(int x, int z) {
            return x * forwardX + z * forwardZ;
        }

        int cell(int along, int up, int side) {
            int a = along;
            int h = up - minimumUp;
            int b = side - minimumSide;
            if (a < 0 || a >= lengthAlong || h < 0 || h >= height || b < 0 || b >= width) {
                return -1;
            }
            return cells[(a * height + h) * width + b];
        }

        double reach(int offsetUp) {
            double side = Math.max(Math.abs(minimumSide), Math.abs(minimumSide + width - 1)) + 0.5;
            double up = Math.max(Math.abs(minimumUp + offsetUp), Math.abs(minimumUp + height - 1 + offsetUp)) + 0.5;
            return Math.sqrt(side * side + up * up) + 0.5;
        }
    }

    /** Bloques del portapapeles relativos a su origen, con el índice de paleta de cada uno. */
    public static final class Template {
        private final IntArrayList xs = new IntArrayList();
        private final IntArrayList ys = new IntArrayList();
        private final IntArrayList zs = new IntArrayList();
        private final IntArrayList values = new IntArrayList();

        public void add(int x, int y, int z, int paletteIndex) {
            if (paletteIndex < 0) {
                throw new IllegalArgumentException("Índice de paleta negativo");
            }
            xs.add(x);
            ys.add(y);
            zs.add(z);
            values.add(paletteIndex);
        }

        public int size() {
            return xs.size();
        }

        int x(int index) {
            return xs.getInt(index);
        }

        int y(int index) {
            return ys.getInt(index);
        }

        int z(int index) {
            return zs.getInt(index);
        }

        int value(int index) {
            return values.getInt(index);
        }

        Axis longestHorizontalAxis() {
            int minimumX = Integer.MAX_VALUE;
            int maximumX = Integer.MIN_VALUE;
            int minimumZ = Integer.MAX_VALUE;
            int maximumZ = Integer.MIN_VALUE;
            for (int index = 0; index < size(); index++) {
                minimumX = Math.min(minimumX, x(index));
                maximumX = Math.max(maximumX, x(index));
                minimumZ = Math.min(minimumZ, z(index));
                maximumZ = Math.max(maximumZ, z(index));
            }
            return maximumX - minimumX >= maximumZ - minimumZ ? Axis.X : Axis.Z;
        }
    }

    public enum Mode {
        REPEAT("repeat", "Repetir", "repetir", "doblar", "bend"),
        STRETCH("stretch", "Estirar", "estirar", "single", "una");

        private final String id;
        private final String displayName;
        private final String[] aliases;

        Mode(String id, String displayName, String... aliases) {
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

        static Mode byId(String input) {
            String normalized = input.toLowerCase(Locale.ROOT);
            for (Mode mode : values()) {
                if (mode.id.equals(normalized) || List.of(mode.aliases).contains(normalized)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Modo desconocido: " + input + " (repeat, stretch)");
        }
    }

    /** Dirección del portapapeles que se alinea con el avance del recorrido. */
    public enum Axis {
        AUTO("auto", 0, 0),
        X("x", 1, 0),
        Z("z", 0, 1),
        NEGATIVE_X("-x", -1, 0),
        NEGATIVE_Z("-z", 0, -1);

        private final String id;
        private final int x;
        private final int z;

        Axis(String id, int x, int z) {
            this.id = id;
            this.x = x;
            this.z = z;
        }

        public String id() {
            return id;
        }

        static Axis byId(String input) {
            String normalized = input.toLowerCase(Locale.ROOT);
            for (Axis axis : values()) {
                if (axis.id.equals(normalized)) {
                    return axis;
                }
            }
            throw new IllegalArgumentException("Eje desconocido: " + input + " (auto, x, z, -x, -z)");
        }
    }

    /**
     * Sección variable de {@code //ezspline structure}: radio por fotogramas, estiramiento a lo largo, giro inicial y
     * torsión, con las mismas opciones que los tubos ({@code k0…}, {@code p1…}, {@code stretch}, {@code twist},
     * {@code angle}, {@code rollend}).
     *
     * @param baseRadius     radio entero de referencia de los fotogramas (el mayor)
     * @param inherentRadius radio propio de la plantilla, el que corresponde a escala 1
     */
    public record Section(int baseRadius, double inherentRadius, Map<String, Double> options) {
        /** La plantilla a su tamaño, sin girar ni retorcer. */
        public static final Section NONE = new Section(0, Double.NaN, Map.of());

        public Section {
            options = Map.copyOf(options);
        }

        /**
         * @param radii     radios en la sintaxis de los tubos ({@code 6}, {@code 2,8,2}, {@code 0:4,0.3:10,1:4});
         *                  {@code null} para el tamaño propio de la plantilla
         * @param stretch   {@code -s}: estira la plantilla a lo largo
         * @param twist     {@code -t}: grados de torsión por diámetro recorrido
         * @param startRoll {@code -r}: giro inicial en grados
         * @param endRoll   giro final de {@code -r inicio,fin} ({@code NaN} si no hay); entonces {@code -t} no cuenta
         */
        public static Section of(String radii, double inherentRadius, double stretch, double twist, double startRoll,
                                 double endRoll) {
            if (!(inherentRadius > 0.0)) {
                throw new IllegalArgumentException("La estructura no tiene tamaño");
            }
            String written = radii != null ? radii : String.format(Locale.ROOT, "%.4f", inherentRadius);
            TubeSettings.RadiusProgression.Parsed parsed = TubeSettings.RadiusProgression.parse(written);
            Map<String, Double> options = new java.util.LinkedHashMap<>(parsed.options());
            options.put("stretch", stretch);
            if (twist != 0.0) {
                options.put("twist", twist);
            }
            if (startRoll != 0.0) {
                options.put("angle", startRoll);
            }
            if (!Double.isNaN(endRoll)) {
                options.put("rollend", endRoll);
            }
            return new Section(parsed.baseRadius(), inherentRadius, options);
        }

        boolean scaled() {
            return baseRadius > 0;
        }

        /** Radio máximo del eje a lo largo del recorrido. */
        double maximumRadius(int fallback) {
            if (!scaled()) {
                return fallback;
            }
            double maximum = 0.0;
            for (int index = 0; options.containsKey("k" + index); index++) {
                maximum = Math.max(maximum, options.get("k" + index) / 100.0);
            }
            return baseRadius * (maximum > 0.0 ? maximum : 1.0);
        }
    }

    public record Settings(
            Mode mode,
            Axis axis,
            TubeSettings.NormalMode normalMode,
            int gap,
            int offsetUp,
            double tension,
            double bias,
            double continuity
    ) {
        public static Settings defaults(Mode mode) {
            return new Settings(mode, Axis.AUTO, TubeSettings.NormalMode.UPRIGHT, 0, 0, 0.0, 0.0, 0.0);
        }

        /**
         * {@code [repeat|stretch] [-n normales] [-g hueco] [-a eje] [-y desplazamiento] [-p tensión:sesgo:continuidad]}.
         */
        public static Settings parse(String input) {
            Mode mode = Mode.REPEAT;
            Axis axis = Axis.AUTO;
            TubeSettings.NormalMode normal = TubeSettings.NormalMode.UPRIGHT;
            int gap = 0;
            int offset = 0;
            double tension = 0.0;
            double bias = 0.0;
            double continuity = 0.0;
            String[] tokens = input == null || input.isBlank() ? new String[0] : input.trim().split("\\s+");
            boolean modeSeen = false;
            for (int index = 0; index < tokens.length; index++) {
                String token = tokens[index].toLowerCase(Locale.ROOT);
                if (!token.startsWith("-") || token.length() < 2 || !Character.isLetter(token.charAt(1))) {
                    if (modeSeen) {
                        throw new IllegalArgumentException("Sobra el texto «" + tokens[index] + "»");
                    }
                    mode = Mode.byId(token);
                    modeSeen = true;
                    continue;
                }
                if (index + 1 >= tokens.length) {
                    throw new IllegalArgumentException("Falta el valor de " + tokens[index]);
                }
                String value = tokens[++index];
                switch (token) {
                    case "-n" -> normal = TubeSettings.NormalMode.byId(value);
                    case "-a" -> axis = Axis.byId(value);
                    case "-g" -> gap = integer(value, -64, 256, "-g");
                    case "-y" -> offset = integer(value, -128, 128, "-y");
                    case "-p" -> {
                        String[] parts = value.split(":");
                        if (parts.length != 3) {
                            throw new IllegalArgumentException("-p espera tensión:sesgo:continuidad, por ejemplo 0:0:0");
                        }
                        tension = unit(parts[0], "tensión");
                        bias = unit(parts[1], "sesgo");
                        continuity = unit(parts[2], "continuidad");
                    }
                    default -> throw new IllegalArgumentException("Bandera desconocida: " + tokens[index]);
                }
            }
            return new Settings(mode, axis, normal, gap, offset, tension, bias, continuity);
        }

        private static int integer(String value, int minimum, int maximum, String name) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < minimum || parsed > maximum) {
                    throw new IllegalArgumentException(name + " debe estar entre " + minimum + " y " + maximum);
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Valor inválido para " + name + ": " + value);
            }
        }

        private static double unit(String value, String name) {
            try {
                double parsed = Double.parseDouble(value);
                if (!(parsed >= -1.0 && parsed <= 1.0)) {
                    throw new IllegalArgumentException(name + " debe estar entre -1 y 1");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Valor inválido para " + name + ": " + value);
            }
        }
    }
}
