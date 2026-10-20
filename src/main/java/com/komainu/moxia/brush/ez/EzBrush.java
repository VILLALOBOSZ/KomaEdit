package com.komainu.moxia.brush.ez;

import com.komainu.moxia.brush.BrushInput;
import com.komainu.moxia.brush.BrushMarkers;
import com.komainu.moxia.brush.place.Alignment;
import com.komainu.moxia.brush.place.PlacementOperation;
import com.komainu.moxia.brush.place.PlacementTemplate;
import com.komainu.moxia.brush.place.StructureSource;
import com.komainu.moxia.brush.place.StructureSources;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.placement.PlacementService;
import com.komainu.moxia.placement.PlacementSettings;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.terrain.HeightmapBrush;
import com.komainu.moxia.terrain.StampBrush;
import com.komainu.moxia.noise.SavedNoises;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Un pincel configurado y ligado a una herramienta. Se crea en el hilo del servidor (resuelve paletas y ruido) y
 * traduce cada clic en una pasada de {@link com.komainu.moxia.sculpt.BrushOperation}.
 */
public abstract sealed class EzBrush {
    public static final int DEFAULT_RADIUS = 8;
    public static final int MAX_RADIUS = 32;

    private final EzBrushType type;
    private final int radius;
    private final boolean preview;

    private EzBrush(EzBrushType type, int radius, boolean preview) {
        this.type = type;
        this.radius = radius;
        this.preview = preview;
    }

    public EzBrushType type() {
        return type;
    }

    public int radius() {
        return radius;
    }

    public boolean preview() {
        return preview;
    }

    /** Resumen de los controles para el chat. */
    public abstract String controls();

    abstract void handle(EzBrushManager.Session session, BrushInput input);

    public static EzBrush create(ServerPlayerEntity player, EzBrushType type, String input) {
        if (type.placement() && !(type == EzBrushType.PLACE && legacyPreview(input))) {
            PlacementSettings.Kind kind = switch (type) {
                case SCATTER -> PlacementSettings.Kind.SCATTER;
                case ARRAY -> PlacementSettings.Kind.ARRAY;
                default -> PlacementSettings.Kind.PLACE;
            };
            return new Placing(type, PlacementSettings.parse(kind, input, true));
        }
        BrushOptions options = type.options(input);
        boolean preview = options.has('p');
        return switch (type) {
            case PALETTE_SHIFT -> {
                ResolvedPalette palette = ResolvedPalette.resolve(player, options.required(0, "la paleta"), 2);
                int radius = options.integer(1, DEFAULT_RADIUS, 1, MAX_RADIUS, "El radio");
                int shift = options.integer(2, 1, -255, 255, "El desplazamiento");
                yield new Shift(palette, palette.shift(options.has('r')), radius, shift, preview);
            }
            case GRADIENT, GRADIENT_STROKE -> {
                boolean stroke = type == EzBrushType.GRADIENT_STROKE;
                ResolvedPalette palette = ResolvedPalette.resolve(player, options.required(0, "la paleta"), 2);
                int radius = options.integer(1, DEFAULT_RADIUS, 1, MAX_RADIUS, "El radio");
                String interpolationText = options.positional(2);
                GradientInterpolation interpolation = interpolationText == null
                        ? (stroke ? GradientInterpolation.LINEAR : GradientInterpolation.NONE)
                        : GradientInterpolation.byName(interpolationText);
                double bleed = options.decimal(3, 0.5, 0.0, 4.0, "El sangrado");
                double scale = options.flagDecimal('z', 1.0, 0.0001, 1000.0, "La escala del ruido");
                NoiseSpec noise = options.has('n') ? SavedNoises.parse(player.getUuid(), options.flag('n')) : null;
                long seed = ThreadLocalRandom.current().nextLong();
                GradientPainter.Settings settings = new GradientPainter.Settings(palette.size(), interpolation, bleed,
                        BrushNoise.of(noise, scale, seed), options.has('a'), options.has('w'));
                if (stroke) {
                    yield new Stroke(palette, settings, radius, options.has('d'), options.has('x'), !options.has('v'),
                            preview);
                }
                DistanceFunction distance = options.has('d') ? DistanceFunction.byName(options.flag('d')) : null;
                yield new Gradient(palette, settings, distance, radius, !options.has('v'), preview);
            }
            case PETAL_PAINTER -> {
                BlockType block = foliage(options.required(0, "el tipo de follaje (petals, wildflowers, leaves)"));
                int radius = options.integer(1, DEFAULT_RADIUS, 1, MAX_RADIUS, "El radio");
                double density = options.decimal(2, 0.3, 0.0, 1.0, "La densidad");
                double scale = options.flagDecimal('z', 0.03, 0.0001, 1000.0, "La escala del ruido");
                NoiseSpec noise = options.has('n') ? SavedNoises.parse(player.getUuid(), options.flag('n')) : null;
                BlockFilter mask = options.has('m') ? BlockFilter.parse(options.flag('m')) : BlockFilter.FULL_BLOCK;
                int maxAmount = maxAmount(block);
                yield new Petals(block, radius, density, noise, scale, mask, maxAmount, preview);
            }
            case SCATTER, ARRAY -> throw new IllegalStateException(type.id());
            case HEIGHTMAP -> new Relief(HeightmapBrush.parse(options), preview);
            case PLACE -> {
                StructureSource source = StructureSources.parse(options.required(0, "la estructura (Clipboard)"));
                Vec3d look = player.getRotationVector();
                double[] aim = {look.x, look.y, look.z};
                Alignment.Mode primary = Alignment.parse(options.positional(1), true, aim);
                Alignment.Mode secondary = Alignment.parse(options.positional(2), false, aim);
                yield new Place(source, primary, secondary, options.has('a'), options.has('x'), preview);
            }
        };
    }

    /**
     * ¿Pide el pincel de colocación la vista previa ({@code -p})? Entonces usa la colocación por giros de 90° con
     * holograma; sin ella, el motor de {@code /komaedit place} con todas sus banderas.
     */
    static boolean legacyPreview(String input) {
        for (String word : input.trim().split("\\s+")) {
            if (word.equalsIgnoreCase("-p")) {
                return true;
            }
        }
        return false;
    }

    /** Bloque del follaje: alias de ezEdits o cualquier bloque con piezas (flower_amount, segment_amount). */
    static BlockType foliage(String input) {
        String id = switch (BrushOptions.normalize(input)) {
            case "petals", "pinkpetals", "petalos", "petalosrosas" -> "minecraft:pink_petals";
            case "wildflowers", "flores", "floressilvestres" -> "minecraft:wildflowers";
            case "leaves", "leaflitter", "hojas", "hojarasca" -> "minecraft:leaf_litter";
            default -> PaletteShift.normalize(input);
        };
        BlockType type = BlockTypes.get(id);
        if (type == null) {
            throw new IllegalArgumentException("El bloque " + id + " no existe en esta versión de Minecraft");
        }
        if (PaintOperations.amountProperty(type) == null) {
            throw new IllegalArgumentException(id + " no es un follaje en capas (le falta flower_amount o"
                    + " segment_amount)");
        }
        return type;
    }

    static int maxAmount(BlockType type) {
        int maximum = 1;
        for (Object value : PaintOperations.amountProperty(type).getValues()) {
            if (value instanceof Integer number) {
                maximum = Math.max(maximum, number);
            }
        }
        return maximum;
    }

    /** Desplazar paleta: clic derecho hacia delante, izquierdo hacia atrás. */
    static final class Shift extends EzBrush {
        private final ResolvedPalette palette;
        private final PaletteShift shift;
        private final int amount;

        Shift(ResolvedPalette palette, PaletteShift shift, int radius, int amount, boolean preview) {
            super(EzBrushType.PALETTE_SHIFT, radius, preview);
            this.palette = palette;
            this.shift = shift;
            this.amount = amount;
        }

        @Override
        public String controls() {
            return "clic derecho: desplaza " + amount + " · clic izquierdo: al revés";
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input.swap()) {
                return;
            }
            int direction = input.right() ? amount : -amount;
            session.run(new PaintOperations.Shift(palette, shift, session.target(), radius(), direction));
        }
    }

    /** Degradado entre dos puntos (o desde el primero con una distancia). */
    static final class Gradient extends EzBrush {
        private final ResolvedPalette palette;
        private final GradientPainter.Settings settings;
        private final DistanceFunction distance;
        private final boolean markers;

        Gradient(ResolvedPalette palette, GradientPainter.Settings settings, DistanceFunction distance, int radius,
                 boolean markers, boolean preview) {
            super(EzBrushType.GRADIENT, radius, preview);
            this.palette = palette;
            this.settings = settings;
            this.distance = distance;
            this.markers = markers;
        }

        @Override
        public String controls() {
            return "clic izquierdo: inicio (Mayús: en tus pies) · clic derecho: final y luego pintar"
                    + " · F: degradado global o de esta herramienta";
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            EzBrushManager.Anchor anchor = session.anchor();
            switch (input) {
                case SWAP, SNEAK_SWAP -> session.toggleScope();
                case LEFT, SNEAK_LEFT -> {
                    anchor.start = input.sneaking() ? session.feet() : session.target();
                    anchor.end = null;
                    session.info("Inicio del degradado en " + text(anchor.start) + "; clic derecho marca el final");
                    if (markers) {
                        BrushMarkers.point(session.player(), anchor.start, BrushMarkers.START);
                    }
                }
                case RIGHT, SNEAK_RIGHT -> {
                    if (anchor.start == null) {
                        throw new IllegalArgumentException("Marca primero el inicio con clic izquierdo");
                    }
                    if (anchor.end == null) {
                        BlockVector3 end = input.sneaking() ? session.feet() : session.target();
                        if (end.equals(anchor.start)) {
                            throw new IllegalArgumentException("El final no puede coincidir con el inicio");
                        }
                        anchor.end = end;
                        session.info("Degradado de " + text(anchor.start) + " a " + text(end)
                                + "; clic derecho para pintar");
                        if (markers) {
                            BrushMarkers.line(session.player(), anchor.start, end, BrushMarkers.POINT);
                            BrushMarkers.point(session.player(), end, BrushMarkers.END);
                        }
                        return;
                    }
                    GradientPainter.Ramp ramp = distance == null
                            ? GradientPainter.plane(anchor.start, anchor.end)
                            : GradientPainter.distance(anchor.start, anchor.end, distance);
                    session.run(new PaintOperations.Gradient(palette, ramp, settings, session.target(), radius()));
                }
            }
        }
    }

    /** Degradado a lo largo de un trazo de puntos. */
    static final class Stroke extends EzBrush {
        private final ResolvedPalette palette;
        private final GradientPainter.Settings settings;
        private final boolean distanceToCenter;
        private final boolean clearAfter;
        private final boolean markers;

        Stroke(ResolvedPalette palette, GradientPainter.Settings settings, int radius, boolean distanceToCenter,
               boolean clearAfter, boolean markers, boolean preview) {
            super(EzBrushType.GRADIENT_STROKE, radius, preview);
            this.palette = palette;
            this.settings = settings;
            this.distanceToCenter = distanceToCenter;
            this.clearAfter = clearAfter;
            this.markers = markers;
        }

        @Override
        public String controls() {
            return "clic izquierdo: añade punto (Mayús: quita el último) · clic derecho: pinta el trazo"
                    + " (Mayús: lo borra) · F: trazo global o de esta herramienta";
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            List<BlockVector3> points = session.strokePoints();
            switch (input) {
                case SWAP, SNEAK_SWAP -> session.toggleScope();
                case LEFT -> {
                    if (points.size() >= 256) {
                        throw new IllegalArgumentException("El trazo admite como mucho 256 puntos");
                    }
                    points.add(session.target());
                    session.info("Trazo: " + points.size() + " punto(s)");
                    showPath(session, points);
                }
                case SNEAK_LEFT -> {
                    if (!points.isEmpty()) {
                        points.removeLast();
                    }
                    session.info("Trazo: " + points.size() + " punto(s)");
                    showPath(session, points);
                }
                case SNEAK_RIGHT -> {
                    points.clear();
                    session.info("Trazo borrado");
                }
                case RIGHT -> {
                    if (points.size() < 2) {
                        throw new IllegalArgumentException("Marca al menos dos puntos con clic izquierdo");
                    }
                    StrokePath path = new StrokePath(List.copyOf(points));
                    session.run(new PaintOperations.Stroke(palette, path, settings, radius(), distanceToCenter));
                    if (clearAfter) {
                        points.clear();
                    }
                }
            }
        }

        private void showPath(EzBrushManager.Session session, List<BlockVector3> points) {
            if (markers) {
                BrushMarkers.path(session.player(), points, BrushMarkers.POINT);
            }
        }
    }

    /** Pintor de pétalos: clic derecho esparce, clic izquierdo quita. */
    static final class Petals extends EzBrush {
        private final BlockType block;
        private final double density;
        private final NoiseSpec noise;
        private final double scale;
        private final BlockFilter mask;
        private final int maxAmount;

        Petals(BlockType block, int radius, double density, NoiseSpec noise, double scale, BlockFilter mask,
               int maxAmount, boolean preview) {
            super(EzBrushType.PETAL_PAINTER, radius, preview);
            this.block = block;
            this.density = density;
            this.noise = noise;
            this.scale = scale;
            this.mask = mask;
            this.maxAmount = maxAmount;
        }

        @Override
        public String controls() {
            return "clic derecho: esparce " + block.id() + " · clic izquierdo: lo quita";
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input.swap()) {
                return;
            }
            // El ruido blanco cambia en cada clic para que repasar espese; un ruido con forma se mantiene.
            long seed = ThreadLocalRandom.current().nextLong();
            BrushNoise field = noise == null ? BrushNoise.white(seed) : BrushNoise.of(noise, scale, 0x5045_54A1L);
            PetalPainter.Settings settings = new PetalPainter.Settings(block.id(), maxAmount, density, field, mask,
                    seed);
            BlockVector3 center = session.target().add(0, 1, 0);
            session.run(new PaintOperations.Petals(block, settings, center, radius(), input.left()));
        }
    }

    /** Colocar: una estructura en cada clic derecho, orientada con primaria y secundaria. */
    static final class Place extends EzBrush {
        private final StructureSource source;
        private final Alignment.Mode primary;
        private final Alignment.Mode secondary;
        private final boolean placeAir;
        private final boolean perturb;

        Place(StructureSource source, Alignment.Mode primary, Alignment.Mode secondary, boolean placeAir,
              boolean perturb, boolean preview) {
            super(EzBrushType.PLACE, 0, preview);
            this.source = source;
            this.primary = primary;
            this.secondary = secondary;
            this.placeAir = placeAir;
            this.perturb = perturb;
        }

        @Override
        public String controls() {
            return "clic derecho: coloca el " + source.describe() + " donde miras";
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (!input.right()) {
                return;
            }
            BlockHitResult hit = session.hit();
            var side = hit.getSide();
            BlockVector3 target = BlockVector3.at(
                    hit.getBlockPos().getX() + side.getOffsetX(),
                    hit.getBlockPos().getY() + side.getOffsetY(),
                    hit.getBlockPos().getZ() + side.getOffsetZ()
            );
            ServerPlayerEntity player = session.player();
            Vec3d look = player.getRotationVector();
            Vec3d eye = player.getEyePos();
            long seed = ThreadLocalRandom.current().nextLong();
            Alignment.Context context = new Alignment.Context(
                    look.x, look.y, look.z,
                    side.getOffsetX(), side.getOffsetY(), side.getOffsetZ(),
                    eye.x - (target.x() + 0.5), eye.y - (target.y() + 0.5), eye.z - (target.z() + 0.5),
                    seed
            );
            int[] basis = Alignment.basis(
                    Alignment.resolve(primary, context, 1L),
                    Alignment.resolve(secondary, context, 2L),
                    perturb
            );
            basis = Alignment.rotateAroundUp(basis, source.quarterTurns());
            PlacementTemplate template = source.prepare(player, seed);
            session.run(new PlacementOperation(source.describe(), template, basis, target, placeAir));
        }
    }

    /**
     * Pinceles de colocación con el motor de {@code /komaedit place}, {@code scatter} y {@code array}: todas sus
     * estructuras, alineaciones y banderas. Cada pasada es una edición de //undo.
     */
    static final class Placing extends EzBrush {
        private final PlacementSettings settings;
        /** En {@code ViewDiff}, la colocación que espera su segundo clic. */
        private PlacementService.BrushTarget pending;

        Placing(EzBrushType type, PlacementSettings settings) {
            super(type, 0, false);
            this.settings = settings;
        }

        PlacementSettings settings() {
            return settings;
        }

        @Override
        public String controls() {
            return switch (type()) {
                case SCATTER -> "clic derecho: dispersa alrededor del bloque que miras";
                case ARRAY -> "clic izquierdo: añade punto (Mayús: quita el último) · clic derecho: coloca a lo"
                        + " largo del trazo (Mayús: lo borra) · F: trazo global o de esta herramienta";
                default -> settings.needsViewDiff()
                        ? "clic derecho: dónde · clic izquierdo: hacia dónde (ViewDiff) y coloca"
                        : "clic derecho: coloca donde miras";
            };
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            switch (type()) {
                case ARRAY -> stroke(session, input);
                case SCATTER -> {
                    if (input.right() && !input.swap()) {
                        start(session, target(session));
                    }
                }
                default -> place(session, input);
            }
        }

        private void place(EzBrushManager.Session session, BrushInput input) {
            if (input.swap()) {
                return;
            }
            if (!settings.needsViewDiff()) {
                if (input.right()) {
                    start(session, target(session));
                }
                return;
            }
            if (input.right()) {
                pending = target(session);
                session.info("Posición marcada; clic izquierdo donde quieras que apunte");
                BrushMarkers.point(session.player(), pending.surface(), BrushMarkers.START);
                return;
            }
            if (pending == null) {
                throw new IllegalArgumentException("Primero clic derecho donde va la estructura");
            }
            BlockVector3 second = session.target();
            Vec3 direction = new Vec3(second.x() - pending.surface().x(), second.y() - pending.surface().y(),
                    second.z() - pending.surface().z());
            if (direction.length() < 1.0E-6) {
                throw new IllegalArgumentException("El segundo clic tiene que ir a otro bloque");
            }
            PlacementService.BrushTarget first = pending;
            pending = null;
            start(session, new PlacementService.BrushTarget(first.anchor(), first.surface(), first.faceNormal(),
                    List.of(), direction.normalized()));
        }

        private void stroke(EzBrushManager.Session session, BrushInput input) {
            List<BlockVector3> points = session.strokePoints();
            switch (input) {
                case SWAP, SNEAK_SWAP -> session.toggleScope();
                case LEFT -> {
                    if (points.size() >= 256) {
                        throw new IllegalArgumentException("El trazo admite como mucho 256 puntos");
                    }
                    points.add(session.target());
                    session.info("Trazo: " + points.size() + " punto(s)");
                    BrushMarkers.path(session.player(), points, BrushMarkers.POINT);
                }
                case SNEAK_LEFT -> {
                    if (!points.isEmpty()) {
                        points.removeLast();
                    }
                    session.info("Trazo: " + points.size() + " punto(s)");
                    BrushMarkers.path(session.player(), points, BrushMarkers.POINT);
                }
                case SNEAK_RIGHT -> {
                    points.clear();
                    session.info("Trazo borrado");
                }
                case RIGHT -> {
                    if (points.size() < 2) {
                        throw new IllegalArgumentException("Marca al menos dos puntos con clic izquierdo");
                    }
                    // Los puntos son los bloques mirados; las estructuras van encima, en el aire.
                    List<BlockVector3> lifted = points.stream().map(point -> point.add(0, 1, 0)).toList();
                    start(session, new PlacementService.BrushTarget(lifted.getFirst(), points.getFirst(),
                            new Vec3(0, 1, 0), lifted, null));
                }
            }
        }

        private static PlacementService.BrushTarget target(EzBrushManager.Session session) {
            BlockHitResult hit = session.hit();
            var side = hit.getSide();
            BlockVector3 surface = BlockVector3.at(hit.getBlockPos().getX(), hit.getBlockPos().getY(),
                    hit.getBlockPos().getZ());
            BlockVector3 anchor = surface.add(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
            return new PlacementService.BrushTarget(anchor, surface,
                    new Vec3(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ()), List.of(), null);
        }

        private void start(EzBrushManager.Session session, PlacementService.BrushTarget target) {
            try {
                session.info(PlacementService.startBrush(session.player(), settings, target));
            } catch (com.sk89q.worldedit.IncompleteRegionException exception) {
                throw new IllegalArgumentException("-h Active necesita una selección de WorldEdit");
            }
        }
    }

    static String text(BlockVector3 point) {
        return String.format(Locale.ROOT, "%d, %d, %d", point.x(), point.y(), point.z());
    }

    /** Relieve: un sello de alturas en cada clic. Derecho: el modo; izquierdo: el contrario; F: cambia de modo. */
    static final class Relief extends EzBrush {
        private final HeightmapBrush.Config config;
        private StampBrush.Mode mode;

        Relief(HeightmapBrush.Config config, boolean preview) {
            super(EzBrushType.HEIGHTMAP, config.radius(), preview);
            this.config = config;
            this.mode = config.mode();
        }

        @Override
        public String controls() {
            return HeightmapBrush.controls(config, mode);
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input.swap()) {
                mode = mode.next();
                session.info("Relieve: " + mode.displayName().toLowerCase(Locale.ROOT) + " con el clic derecho");
                return;
            }
            StampBrush.Mode pass = input.left() ? mode.opposite() : mode;
            session.run(HeightmapBrush.operation(config, pass, session.target(), session.player().getYaw(),
                    ThreadLocalRandom.current().nextDouble()));
        }
    }
}
