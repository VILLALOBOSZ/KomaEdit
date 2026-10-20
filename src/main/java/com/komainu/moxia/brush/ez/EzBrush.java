package com.komainu.moxia.brush.ez;

import com.komainu.moxia.biome.BiomeService;
import com.komainu.moxia.blueprint.BlueprintStamps;
import com.komainu.moxia.blueprint.StampSettings;
import com.komainu.moxia.brush.BrushInput;
import com.komainu.moxia.brush.BrushMarkers;
import com.komainu.moxia.brush.place.Alignment;
import com.komainu.moxia.brush.place.PlacementOperation;
import com.komainu.moxia.brush.place.PlacementTemplate;
import com.komainu.moxia.brush.place.StructureSource;
import com.komainu.moxia.brush.place.StructureSources;
import com.komainu.moxia.brush.paint.PaintBrush;
import com.komainu.moxia.brush.paint.PaintOperation;
import com.komainu.moxia.brush.paint.PaintStrokes;
import com.komainu.moxia.noise.NoiseSpec;
import com.komainu.moxia.nature.NaturePresets;
import com.komainu.moxia.nature.NatureService;
import com.komainu.moxia.nature.RockSettings;
import com.komainu.moxia.nature.TreeSettings;
import com.komainu.moxia.placement.PlacementService;
import com.komainu.moxia.placement.PlacementSettings;
import com.komainu.moxia.sculpt.BrushFalloff;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.komainu.moxia.sculpt.model.ModelMaterial;
import com.komainu.moxia.sculpt.model.ModelMode;
import com.komainu.moxia.sculpt.model.ModelOperation;
import com.komainu.moxia.sculpt.model.ModelSettings;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.terrain.HeightmapBrush;
import com.komainu.moxia.terrain.StampBrush;
import com.komainu.moxia.noise.SavedNoises;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
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

    /**
     * El pincel que sustituye a {@code previous} al cambiar los ajustes desde el Studio ({@code pincel actualizar});
     * cada pincel decide qué conserva del anterior. De serie, nada.
     */
    public EzBrush adopt(EzBrush previous) {
        return this;
    }

    public static EzBrush create(ServerPlayerEntity player, EzBrushType type, String input) {
        if (type == EzBrushType.TREE) {
            return new Tree(NaturePresets.tree(player.getUuid(), input));
        }
        if (type == EzBrushType.ROCK) {
            return new Rock(NaturePresets.rock(player.getUuid(), input));
        }
        if (type == EzBrushType.STAMP) {
            return new Stamp(new BlueprintStamps(player, StampSettings.parse(input)));
        }
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
                String mask = options.has('m') ? options.flag('m') : null;
                BrushMasks.parse(player, mask);
                int maxAmount = maxAmount(block);
                yield new Petals(block, radius, density, noise, scale, mask, maxAmount, preview);
            }
            case SCATTER, ARRAY, TREE, ROCK, STAMP -> throw new IllegalStateException(type.id());
            case BIOME -> {
                BiomeType biome = BiomeService.parse(
                        options.required(0, "el bioma (cherry_grove, snowy_plains…)"));
                int radius = options.integer(1, DEFAULT_RADIUS, 1, MAX_RADIUS, "El radio");
                yield new Biome(biome, radius, options.has('v'), options.has('m') ? options.flag('m') : null);
            }
            case HEIGHTMAP -> {
                HeightmapBrush.Config config = HeightmapBrush.parse(options);
                if (config.mask() != null) {
                    // Que una máscara mal escrita falle al elegir el pincel y no en el primer clic.
                    BrushMasks.parse(player, config.mask());
                }
                yield new Relief(config, preview);
            }
            case SCULPT -> Sculpt.parse(player, options);
            case PAINT -> new Paint(PaintBrush.create(player, options));
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
        /** Texto de {@code -m} o {@code null}; si es de WorldEdit se vuelve a leer en cada clic. */
        private final String mask;
        private final BrushMask fixedMask;
        private final int maxAmount;

        Petals(BlockType block, int radius, double density, NoiseSpec noise, double scale, String mask,
               int maxAmount, boolean preview) {
            super(EzBrushType.PETAL_PAINTER, radius, preview);
            this.block = block;
            this.density = density;
            this.noise = noise;
            this.scale = scale;
            this.mask = mask;
            this.fixedMask = BrushMasks.fixed(mask);
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
            PetalPainter.Settings settings = new PetalPainter.Settings(block.id(), maxAmount, density, field,
                    BrushMasks.forClick(fixedMask, session.player(), mask), seed);
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
     * Pintar biomas: clic derecho pinta una esfera de celdas de 4×4×4 alrededor del bloque mirado; clic izquierdo
     * toma el bioma de ese bloque (cuentagotas). Cada clic es una edición de //undo.
     */
    static final class Biome extends EzBrush {
        private BiomeType biome;
        private final boolean vertical;
        private final String mask;

        Biome(BiomeType biome, int radius, boolean vertical, String mask) {
            super(EzBrushType.BIOME, radius, false);
            this.biome = biome;
            this.vertical = vertical;
            this.mask = mask;
        }

        @Override
        public String controls() {
            return "clic derecho: pinta " + biome.id() + (vertical ? " en toda la columna" : "")
                    + " · clic izquierdo: toma el bioma del bloque mirado";
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input.swap()) {
                return;
            }
            if (input.left()) {
                var hit = session.hit();
                String picked = BiomeService.biomeAt(session.player().getServerWorld(), hit.getBlockPos());
                biome = BiomeService.parse(picked);
                session.info("Bioma del pincel: " + picked);
                return;
            }
            BiomeService.paintSphere(session.player(), biome, session.target(), radius(), vertical, mask);
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

    /** Árboles: uno en cada clic derecho, con una variante nueva cada vez salvo que fije la semilla. */
    static final class Tree extends EzBrush {
        private final TreeSettings settings;

        Tree(TreeSettings settings) {
            super(EzBrushType.TREE, 0, settings.preview());
            this.settings = settings;
        }

        @Override
        public String controls() {
            return "clic derecho: " + settings.describe() + " en el suelo que miras"
                    + (settings.seed().isPresent() ? "" : " (una variante nueva en cada clic)");
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input.right() && !input.swap()) {
                NatureService.brushTree(session.player(), settings, session.hit());
            }
        }
    }

    /** Rocas: una en cada clic derecho, con una variante nueva cada vez salvo que fije la semilla. */
    static final class Rock extends EzBrush {
        private final RockSettings settings;

        Rock(RockSettings settings) {
            super(EzBrushType.ROCK, 0, settings.preview());
            this.settings = settings;
        }

        @Override
        public String controls() {
            return "clic derecho: " + settings.describe() + " hundida en el suelo que miras"
                    + (settings.seed().isPresent() ? "" : " (una variante nueva en cada clic)");
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input.right() && !input.swap()) {
                NatureService.brushRock(session.player(), settings, session.hit());
            }
        }
    }

    /**
     * Estampar: planos de la biblioteca al azar alrededor del clic, girados, volteados y apoyados en el suelo. Clic
     * izquierdo: olvida dónde estampó, para que la separación vuelva a empezar.
     */
    static final class Stamp extends EzBrush {
        private final BlueprintStamps stamps;

        Stamp(BlueprintStamps stamps) {
            super(EzBrushType.STAMP, stamps.settings().radius(), stamps.settings().preview());
            this.stamps = stamps;
        }

        @Override
        public String controls() {
            return stamps.controls();
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input.swap()) {
                return;
            }
            if (input.left()) {
                stamps.forget();
                session.info("Estampar: la separación vuelve a empezar");
                return;
            }
            session.run(stamps.operation(session.player(), session.hit()));
        }
    }

    static String text(BlockVector3 point) {
        return String.format(Locale.ROOT, "%d, %d, %d", point.x(), point.y(), point.z());
    }

    /**
     * Relieve: un sello de alturas en cada clic, cada vez con su variación (giro, escala, espejo y ruido fino según la
     * semilla y el número de clic). Derecho: el modo; izquierdo: el contrario; F: cambia de modo; Mayús+F: semilla
     * nueva.
     */
    static final class Relief extends EzBrush {
        private final HeightmapBrush.Config config;
        private final BrushMask fixedMask;
        private StampBrush.Mode mode;
        private long seed;
        private int clicks;

        Relief(HeightmapBrush.Config config, boolean preview) {
            super(EzBrushType.HEIGHTMAP, config.radius(), preview);
            this.config = config;
            this.fixedMask = config.mask() == null ? null : BrushMasks.fixed(config.mask());
            this.mode = config.mode();
            this.seed = config.seed();
        }

        @Override
        public String controls() {
            return HeightmapBrush.controls(config, mode, seed);
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input == BrushInput.SNEAK_SWAP) {
                seed = ThreadLocalRandom.current().nextInt(1_000_000);
                clicks = 0;
                session.info("Relieve: semilla nueva " + seed + (config.locked()
                        ? " (bloqueada: todos los clics saldrán iguales)" : ""));
                return;
            }
            if (input.swap()) {
                mode = mode.next();
                session.info("Relieve: " + mode.displayName().toLowerCase(Locale.ROOT) + " con el clic derecho");
                return;
            }
            StampBrush.Mode pass = input.left() ? mode.opposite() : mode;
            BrushMask mask = config.mask() == null ? null
                    : BrushMasks.forClick(fixedMask, session.player(), config.mask());
            session.run(HeightmapBrush.operation(config, pass, session.target(), session.player().getYaw(), seed,
                    clicks++, mask));
        }
    }

    /**
     * Modelado: esculpir, roca, soldar, fundir, rugosidad y grietas ({@code sculpt.model}). Clic derecho: el modo;
     * clic izquierdo: el contrario (hundir, tallar la roca, fundir en vez de soldar y al revés); {@code -n} los
     * cambia. Cada clic es una pasada del motor común, con su vista previa ({@code -p}) y su //undo.
     */
    static final class Sculpt extends EzBrush {
        private final ModelSettings settings;
        private final ModelMaterial material;
        private final ModelMaterial fill;
        /** Texto de {@code -m} o {@code null}; si es de WorldEdit se vuelve a leer en cada clic. */
        private final String mask;
        private final BrushMask fixedMask;
        private final boolean freshSeed;
        private final boolean swapClicks;

        private Sculpt(ModelSettings settings, ModelMaterial material, ModelMaterial fill, String mask,
                       boolean freshSeed, boolean swapClicks, boolean preview) {
            super(EzBrushType.SCULPT, settings.radius(), preview);
            this.settings = settings;
            this.material = material;
            this.fill = fill;
            this.mask = mask;
            this.fixedMask = mask == null ? null : BrushMasks.fixed(mask);
            this.freshSeed = freshSeed;
            this.swapClicks = swapClicks;
        }

        static Sculpt parse(ServerPlayerEntity player, BrushOptions options) {
            ModelMode mode = ModelMode.byId(options.required(0,
                    "el modo: draw, rock, weld, melt, roughen o shatter (esculpir, roca, soldar, fundir, rugosidad,"
                            + " grietas)"));
            int radius = options.integer(1, mode == ModelMode.SHATTER ? 10 : 6, 1, ModelSettings.MAX_RADIUS,
                    "El radio");
            ModelSettings settings = ModelSettings.defaults(mode, 0, 0, 0, radius);
            settings = switch (mode) {
                case DRAW -> settings.withStrength(options.decimal(2, 2.0, 0.5, 16.0, "La altura"));
                case ROCK -> settings.withRoughness(options.decimal(2, settings.roughness(), 0.0, 1.0,
                        "La rugosidad"));
                case WELD, MELT -> settings.withThreshold(options.decimal(2, settings.threshold(), 0.01, 0.99,
                        "El umbral"));
                case ROUGHEN -> settings.withStrength(options.decimal(2, settings.strength(), 0.5, 8.0,
                        "La amplitud"));
                case SHATTER -> settings.withScale(options.decimal(2, settings.scale(), 2.0, 32.0,
                        "El tamaño de las celdas"));
            };
            if (options.has('f')) {
                settings = settings.withFalloff(BrushFalloff.byId(options.flag('f')));
            }
            settings = settings
                    .withSmoothing((int) options.flagDecimal('s', 0, 0, 4, "El suavizado (-s)"))
                    .withSigma(options.flagDecimal('g', settings.sigma(), 0.3, 8.0, "El desenfoque (-g)"))
                    .withBlend(options.flagDecimal('k', settings.blend(), 0.0, 16.0, "El fundido (-k)"))
                    .withWidth(options.flagDecimal('w', settings.width(), 0.1, 8.0, "El ancho (-w)"))
                    .withDepth((int) options.flagDecimal('d', settings.depth(), 0, 64, "La profundidad (-d)"));
            if (options.has('z')) {
                settings = settings.withScale(options.flagDecimal('z', settings.scale(), 0.5, 64.0,
                        "La escala del ruido (-z)"));
            }
            if (options.has('e')) {
                settings = settings.withAxis(ModelMode.Axis.byId(options.flag('e')));
            }
            // La roca cambia en cada clic salvo con -i; la rugosidad y las grietas siguen el mismo dibujo al
            // arrastrar.
            boolean freshSeed = !options.has('i') && mode == ModelMode.ROCK;
            long seed = options.has('i')
                    ? (long) options.flagDecimal('i', 0, -9.0E15, 9.0E15, "La semilla (-i)")
                    : ThreadLocalRandom.current().nextLong();
            settings = settings.withSeed(seed);
            String materialText = options.flag('b');
            ModelMaterial material = ModelMaterial.SURROUNDING;
            ModelMaterial fill = ModelMaterial.AIR;
            if (materialText != null && !ModelMaterial.surrounding(materialText)) {
                ModelMaterial chosen = ModelMaterial.air(materialText) ? ModelMaterial.AIR
                        : ModelMaterial.palette(states(ResolvedPalette.resolve(player, materialText, 1)));
                if (mode == ModelMode.SHATTER) {
                    fill = chosen;
                } else if (chosen.air()) {
                    throw new IllegalArgumentException("-b air solo vale con shatter (grietas vacías)");
                } else {
                    material = chosen;
                }
            }
            String mask = options.has('m') ? options.flag('m') : null;
            BrushMasks.parse(player, mask);
            return new Sculpt(settings, material, fill, mask, freshSeed, options.has('n'), options.has('p'));
        }

        private static List<BlockState> states(ResolvedPalette palette) {
            List<BlockState> states = new ArrayList<>(palette.size());
            for (int index = 0; index < palette.size(); index++) {
                states.add(palette.place(index, null, PaletteShift.Kind.FULL));
            }
            return states;
        }

        @Override
        public String controls() {
            String right = settings.mode().displayName().toLowerCase(Locale.ROOT);
            String left = switch (settings.mode()) {
                case DRAW -> "hundir";
                case ROCK -> "tallar la roca";
                case WELD -> "fundir";
                case MELT -> "soldar";
                case ROUGHEN, SHATTER -> right;
            };
            if (swapClicks) {
                String swap = right;
                right = left;
                left = swap;
            }
            return "clic derecho: " + right + " · clic izquierdo: " + left + " · material " + material.describe()
                    + (settings.mode() == ModelMode.SHATTER ? " · relleno " + fill.describe() : "");
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            if (input.swap()) {
                return;
            }
            boolean inverse = input.left() ^ swapClicks;
            BlockHitResult hit = session.hit();
            var side = hit.getSide();
            ModelSettings pass = settings.at(hit.getBlockPos().getX(), hit.getBlockPos().getY(),
                            hit.getBlockPos().getZ())
                    .withHint(side.getOffsetX(), side.getOffsetY(), side.getOffsetZ());
            if (freshSeed) {
                pass = pass.withSeed(ThreadLocalRandom.current().nextLong());
            }
            if (inverse) {
                pass = switch (settings.mode()) {
                    case DRAW, ROCK -> pass.withInvert(true);
                    case WELD -> pass.withMode(ModelMode.MELT).withThreshold(1.0 - settings.threshold());
                    case MELT -> pass.withMode(ModelMode.WELD).withThreshold(1.0 - settings.threshold());
                    case ROUGHEN, SHATTER -> pass;
                };
            }
            session.run(new ModelOperation(pass, material, fill, cellMask(session.player())));
        }

        /** La {@code -m} decide qué bloques se pueden quitar o cambiar; lo libre siempre se puede llenar. */
        private ModelOperation.CellMask cellMask(ServerPlayerEntity player) {
            if (mask == null) {
                return ModelOperation.CellMask.ALL;
            }
            BrushMask parsed = BrushMasks.forClick(fixedMask, player, mask);
            return new ModelOperation.CellMask() {
                private SnapshotWorld world;

                @Override
                public void bind(WorldSnapshot snapshot) {
                    parsed.bind(snapshot);
                    world = new SnapshotWorld(snapshot);
                }

                @Override
                public boolean allows(WorldSnapshot snapshot, int x, int y, int z, BlockState state, boolean solid) {
                    return !solid || parsed.matches(world, x, y, z);
                }
            };
        }
    }

    /**
     * Pintar: recolorea la superficie con un degradado de la paleta, sin cambiar alturas (paquete
     * {@code brush.paint}). Clic derecho, o mantenerlo y arrastrar: un trazo, una sola edición de //undo; clic
     * izquierdo: un toque con la paleta al revés; F: semilla nueva; Mayús + F: bloquea o libera la semilla.
     */
    static final class Paint extends EzBrush {
        private final PaintBrush brush;

        Paint(PaintBrush brush) {
            super(EzBrushType.PAINT, brush.config().radius(), brush.config().preview());
            this.brush = brush;
        }

        @Override
        public String controls() {
            return brush.controls();
        }

        /** Sin semilla escrita, sigue con la del pincel de antes: mover otro ajuste no cambia el dibujo. */
        @Override
        public EzBrush adopt(EzBrush previous) {
            if (previous instanceof Paint old) {
                brush.adopt(old.brush);
            }
            return this;
        }

        @Override
        void handle(EzBrushManager.Session session, BrushInput input) {
            switch (input) {
                case SWAP -> session.info("Pintar: semilla " + brush.reroll()
                        + (brush.locked() ? " (bloqueada: cada clic pinta igual)" : ""));
                case SNEAK_SWAP -> session.info(brush.toggleLock()
                        ? "Pintar: semilla " + brush.seed() + " bloqueada; cada clic pinta igual"
                        : "Pintar: semilla " + brush.seed() + " libre; cada clic gira la huella");
                default -> {
                    BlockHitResult hit = session.hit();
                    if (preview()) {
                        session.run(PaintOperation.single(brush, hit, input.left(), session.player()));
                        return;
                    }
                    PaintStrokes.input(session.player(), brush, hit, input.left());
                }
            }
        }
    }
}
