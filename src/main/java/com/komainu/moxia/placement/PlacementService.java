package com.komainu.moxia.placement;

import com.komainu.moxia.blueprint.BlueprintService;
import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.SmoothBlockStates;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.region.RegionOperationService;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.worldedit.WorldEditService;
import com.komainu.moxia.worldedit.ez.SavedSelections;
import com.komainu.moxia.worldedit.ez.SelectionBridge;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.extent.NullExtent;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.RegionOperationException;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.stream.Stream;

/**
 * {@code /komaedit place}, {@code array} y {@code scatter} en el servidor:
 *
 * <ol>
 *     <li>En el hilo del servidor se leen las banderas, el portapapeles o los schematics, las paletas, los patrones
 *     y la máscara, y se pide la foto del mundo cuando hace falta (superficies, normales, {@code -b}).</li>
 *     <li>En un worker se calculan las colocaciones y sus bloques, que pasan a un {@link PackedBlockPlan}: dentro de
 *     la foto cada escritura es condicional (no pisa lo que cambie mientras tanto) y fuera de ella se escribe sin
 *     más, porque las estructuras pueden asomar fuera de la región.</li>
 *     <li>{@link BatchEditService} aplica el plan por lotes y deja una sola entrada en //undo.</li>
 * </ol>
 */
public final class PlacementService {
    public static final int MAX_STRUCTURE_VOLUME = 2_000_000;
    public static final int MAX_SCHEMATICS = 32;
    /**
     * Radio de búsqueda de superficie de {@code -b}: {@code arraySnapRadius} de {@code config/komaedit/config.json}
     * (96 por defecto, como ezEdits). Se recorta si la caja de la trayectoria no cabe en el límite de lectura.
     */
    public static int snapRadius() {
        return KomaEditConfig.settings().arraySnapRadius();
    }
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private PlacementService() {
    }

    /**
     * Dónde actúa un pincel de colocación: el bloque de aire delante de la cara que se mira ({@code anchor}), el
     * bloque mirado ({@code surface}), la normal de esa cara, los puntos del trazo ({@code array}) y la dirección del
     * segundo clic ({@code ViewDiff}).
     */
    public record BrushTarget(BlockVector3 anchor, BlockVector3 surface, Vec3 faceNormal,
                              List<BlockVector3> stroke, Vec3 viewDiff) {
        public BrushTarget {
            stroke = stroke == null ? List.of() : List.copyOf(stroke);
        }
    }

    /** @return el texto de la confirmación inicial */
    public static String start(ServerPlayerEntity player, PlacementSettings.Kind kind, String input)
            throws IncompleteRegionException {
        return start(player, kind, PlacementSettings.parse(kind, input), null);
    }

    /**
     * Una pasada de un pincel de colocación: la misma cadena que los comandos, pero en el punto del clic. En
     * {@code scatter} la región por defecto es una caja de 40×40×40 centrada en el bloque mirado y {@code Active} o
     * {@code Saved} con {@code P:false} se llevan a él; en {@code array} la trayectoria es el trazo del pincel.
     */
    public static String startBrush(ServerPlayerEntity player, PlacementSettings settings, BrushTarget target)
            throws IncompleteRegionException {
        EditRequest previous = EditRequests.current();
        if (previous != null) {
            return start(player, settings.kind(), settings, target);
        }
        // Clic de pincel: se coloca al momento, sin la vista previa universal.
        String[] started = new String[1];
        IncompleteRegionException[] incomplete = new IncompleteRegionException[1];
        EditRequests.within(EditRequest.TOOL, () -> {
            try {
                started[0] = start(player, settings.kind(), settings, target);
            } catch (IncompleteRegionException exception) {
                incomplete[0] = exception;
            }
        });
        if (incomplete[0] != null) {
            throw incomplete[0];
        }
        return started[0];
    }

    private static String start(ServerPlayerEntity player, PlacementSettings.Kind kind, PlacementSettings parsed,
                                BrushTarget target) throws IncompleteRegionException {
        SmoothBlockStates.validate(parsed.smooth());
        PlacementSettings settings = parsed.randomSeed()
                ? parsed.withSeed(new SplittableRandom().nextLong() & Long.MAX_VALUE)
                : parsed;
        StructureSpec spec = StructureSpec.parse(settings.structure());
        Resolved resolved = resolve(player, spec);
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        if (settings.scatter().cover() != null) {
            WorldEditService.parsePatternUnchecked(context, settings.scatter().cover());
            if (settings.scatter().mask() == null) {
                throw new IllegalArgumentException("-l necesita una máscara -m: cubre las superficies que la cumplen");
            }
        }
        Vec3 eye = new Vec3(player.getEyePos().x, player.getEyePos().y, player.getEyePos().z);
        var look = player.getRotationVec(1.0F);
        DirectionExpr.Context view = new DirectionExpr.Context(new Vec3(look.x, look.y, look.z));
        BlockVector3 feet = target != null ? target.anchor()
                : BlockVector3.at(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        Job job = new Job(player.getUuid(), player.getServer(), kind, settings, spec, resolved, eye, view, feet);
        job.target = target;
        String what = resolved.structures().size() == 1 ? resolved.structures().getFirst().describe()
                : spec.type() == StructureSpec.Type.BLUEPRINT
                ? new java.util.HashSet<>(resolved.structures()).size() + " planos"
                : resolved.structures().size() + " schematics";
        switch (kind) {
            case PLACE -> {
                if (settings.needsNormal()) {
                    BlockVector3 middle = target != null ? target.surface() : feet;
                    Region around = new CuboidRegion(context.world(), middle.subtract(3, 3, 3), middle.add(3, 3, 3));
                    RegionOperationService.capture(player, around, 0, "Colocar", snapshot -> job.compute(snapshot,
                            around, null));
                } else {
                    job.compute(null, null, null);
                }
                return "Colocando " + what + "…";
            }
            case SCATTER -> {
                String regionText = settings.scatter().region() == null && target != null ? "Box"
                        : settings.scatter().region();
                Region region = region(player, context, regionText, target != null ? target.surface() : feet);
                SnapshotExtent extent = new SnapshotExtent();
                Mask mask = settings.scatter().mask() == null ? null : mask(context, settings.scatter().mask(), extent);
                RegionOperationService.capture(player, region, 3, "Dispersar",
                        snapshot -> job.compute(snapshot, region, mask == null ? null : new BoundMask(mask, extent)));
                return "Dispersando " + what + " · densidad " + ParamText.format(settings.scatter().density())
                        + "% · semilla " + settings.seed();
            }
            default -> {
                PathSnapshot path = target != null
                        ? new PathSnapshot(target.stroke(), false, "el trazo del pincel")
                        : WorldEditService.resolvePath(player);
                job.path = path;
                if (settings.array().snapToSurface()) {
                    Region box = pathBox(context.world(), path);
                    RegionOperationService.capture(player, box, 0, "Array", snapshot -> job.compute(snapshot, box,
                            null));
                } else {
                    job.compute(null, null, null);
                }
                return "Colocando " + what + " a lo largo de " + path.source() + "…";
            }
        }
    }

    // ------------------------------------------------------------------ Estructuras

    /** Estructuras listas y lo que necesitan para pintarse. */
    record Resolved(List<Structure> structures, List<List<BlockState>> palettes, String pattern) {
    }

    static Resolved resolve(ServerPlayerEntity player, StructureSpec spec) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        switch (spec.type()) {
            case CLIPBOARD -> {
                try {
                    Clipboard clipboard = WorldEditService.clipboard(player);
                    Captured captured = capture(clipboard, spec, "Portapapeles");
                    return new Resolved(List.of(captured.structure()), List.of(captured.palette()), null);
                } catch (EmptyClipboardException exception) {
                    throw new IllegalArgumentException("El portapapeles está vacío: copia algo con //copy", exception);
                }
            }
            case SCHEMATIC -> {
                List<Structure> structures = new ArrayList<>();
                List<List<BlockState>> palettes = new ArrayList<>();
                for (Path file : schematics(player, spec.filename())) {
                    Captured captured = capture(read(file, spec.format()), spec, file.getFileName().toString());
                    structures.add(captured.structure());
                    palettes.add(captured.palette());
                }
                return new Resolved(structures, palettes, null);
            }
            case BLUEPRINT -> {
                List<Structure> structures = new ArrayList<>();
                List<List<BlockState>> palettes = new ArrayList<>();
                // Un plano repetido (por su peso) se captura una vez y se repite tal cual.
                Map<Clipboard, Captured> captures = new java.util.IdentityHashMap<>();
                for (BlueprintService.Named named : BlueprintService.structures(player, spec.blueprints())) {
                    Captured captured = captures.computeIfAbsent(named.clipboard(),
                            clipboard -> capture(clipboard, spec, named.name()));
                    structures.add(captured.structure());
                    palettes.add(captured.palette());
                }
                return new Resolved(structures, palettes, null);
            }
            case SHAPE, ICOSPHERE -> {
                WorldEditService.parsePatternUnchecked(context, spec.pattern());
                return new Resolved(List.of(spec.procedural(1)), List.of(List.of()), spec.pattern());
            }
            default -> {
                List<BlockState> palette = palette(context, spec.palette());
                return new Resolved(List.of(spec.procedural(palette.size())), List.of(palette), null);
            }
        }
    }

    private record Captured(VoxelStructure structure, List<BlockState> palette) {
    }

    /** Copia los bloques (sin NBT) a una estructura de vóxeles con su origen (INHERENT o CENTER). */
    static Captured capture(Clipboard clipboard, StructureSpec spec, String name) {
        Region region = clipboard.getRegion();
        BlockVector3 minimum = region.getMinimumPoint();
        BlockVector3 maximum = region.getMaximumPoint();
        BlockVector3 size = maximum.subtract(minimum).add(1, 1, 1);
        if ((long) size.x() * size.y() * size.z() > MAX_STRUCTURE_VOLUME) {
            throw new IllegalArgumentException(name + " supera " + MAX_STRUCTURE_VOLUME + " bloques de caja");
        }
        StructureSpec.Origin anchor = spec.origin() != null ? spec.origin()
                : spec.centered() ? StructureSpec.Origin.CENTER : StructureSpec.Origin.INHERENT;
        BlockVector3 origin = switch (anchor) {
            case CENTER -> BlockVector3.at(Math.floorDiv(minimum.x() + maximum.x(), 2),
                    Math.floorDiv(minimum.y() + maximum.y(), 2), Math.floorDiv(minimum.z() + maximum.z(), 2));
            // Centro de la base: la capa de abajo cae en el bloque de colocación.
            case BOTTOM -> BlockVector3.at(Math.floorDiv(minimum.x() + maximum.x(), 2), minimum.y(),
                    Math.floorDiv(minimum.z() + maximum.z(), 2));
            case INHERENT -> clipboard.getOrigin();
        };
        BlockVector3 offset = minimum.subtract(origin);
        VoxelStructure.Builder builder = VoxelStructure.builder(size.x(), size.y(), size.z(), offset.x(), offset.y(),
                offset.z());
        Map<BlockState, Integer> indices = new HashMap<>();
        List<BlockState> palette = new ArrayList<>();
        for (BlockVector3 position : region) {
            BlockState state = clipboard.getBlock(position);
            if (state.getBlockType().getMaterial().isAir()) {
                continue;
            }
            Integer index = indices.get(state);
            if (index == null) {
                index = palette.size();
                palette.add(state);
                indices.put(state, index);
            }
            BlockVector3 local = position.subtract(origin);
            builder.set(local.x(), local.y(), local.z(), index);
        }
        if (palette.isEmpty()) {
            throw new IllegalArgumentException(name + " solo tiene aire");
        }
        return new Captured(builder.build(spec.smooth(), spec.fillBias(), spec.rotation(), name), List.copyOf(palette));
    }

    /**
     * Schematics de la carpeta de WorldEdit cuyo nombre (con o sin extensión) casa con la expresión regular. Puede
     * llevar subcarpetas ({@code arboles/roble_.*}) y {@code %p} se sustituye por el UUID del jugador.
     */
    static List<Path> schematics(ServerPlayerEntity player, String input) {
        WorldEdit worldEdit = WorldEdit.getInstance();
        Path root = worldEdit.getWorkingDirectoryPath(worldEdit.getConfiguration().saveDir).toAbsolutePath().normalize();
        String expression = input.replace("%p", player.getUuidAsString()).replace('\\', '/');
        int slash = expression.lastIndexOf('/');
        Path folder = slash < 0 ? root : root.resolve(expression.substring(0, slash)).normalize();
        if (!folder.startsWith(root)) {
            throw new IllegalArgumentException("La ruta del schematic se sale de la carpeta de schematics");
        }
        java.util.regex.Pattern pattern;
        try {
            pattern = java.util.regex.Pattern.compile(expression.substring(slash + 1),
                    java.util.regex.Pattern.CASE_INSENSITIVE);
        } catch (java.util.regex.PatternSyntaxException exception) {
            throw new IllegalArgumentException("Expresión regular inválida en el nombre: " + exception.getDescription());
        }
        List<Path> matches = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(Files::isRegularFile).sorted().forEach(file -> {
                    String name = file.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    String base = dot < 0 ? name : name.substring(0, dot);
                    if (pattern.matcher(name).matches() || pattern.matcher(base).matches()) {
                        matches.add(file);
                    }
                });
            } catch (IOException exception) {
                throw new IllegalArgumentException("No se pudo leer la carpeta de schematics: " + exception.getMessage());
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Ningún schematic casa con «" + input + "» en " + root.getFileName());
        }
        if (matches.size() > MAX_SCHEMATICS) {
            throw new IllegalArgumentException(matches.size() + " schematics casan con «" + input + "»; el máximo es "
                    + MAX_SCHEMATICS);
        }
        return matches;
    }

    private static Clipboard read(Path file, String formatName) {
        ClipboardFormat format = formatName == null
                ? ClipboardFormats.findByFile(file.toFile())
                : ClipboardFormats.findByAlias(formatName);
        if (format == null) {
            throw new IllegalArgumentException(formatName == null
                    ? "No se reconoce el formato de " + file.getFileName()
                    : "Formato de schematic desconocido: " + formatName);
        }
        try (InputStream stream = Files.newInputStream(file); ClipboardReader reader = format.getReader(stream)) {
            return reader.read();
        } catch (IOException exception) {
            throw new IllegalArgumentException("No se pudo leer " + file.getFileName() + ": " + exception.getMessage());
        }
    }

    /**
     * Paleta de ezEdits ({@code ##Magma}, {@code a,b,c}, {@code #guardada}…); cada entrada tiene que ser un bloque
     * concreto.
     */
    static List<BlockState> palette(WorldEditService.PlayerContext context, String input) {
        List<BlockState> states = new ArrayList<>();
        ParserContext parser = parser(context);
        for (String entry : SavedPalettes.parse(context.actor().getUniqueId(), input)) {
            try {
                BaseBlock block = WorldEdit.getInstance().getBlockFactory().parseFromInput(entry, parser);
                states.add(block.toImmutableState());
            } catch (InputParseException exception) {
                throw new IllegalArgumentException("«" + entry + "» no es un bloque: " + exception.getMessage());
            }
        }
        return List.copyOf(states);
    }

    private static ParserContext parser(WorldEditService.PlayerContext context) {
        ParserContext parser = new ParserContext();
        parser.setActor(context.actor());
        parser.setSession(context.session());
        parser.setWorld(context.world());
        parser.setRestricted(!context.actor().hasPermission("worldedit.anyblock"));
        return parser;
    }

    // ------------------------------------------------------------------ Regiones y máscaras

    /**
     * {@code -h}: {@code Active(UseOriginalPosition:true,Offset:(0,0,0))} (por defecto), {@code Box}, {@code Ellipsoid}
     * o {@code Cylinder} con {@code Dimensions:"40,40,40"} alrededor del jugador, o {@code Saved(Name:…)}.
     */
    static Region region(ServerPlayerEntity player, WorldEditService.PlayerContext context, String input,
                         BlockVector3 feet) throws IncompleteRegionException {
        ParamText.Call call = ParamText.call(input == null ? "Active" : input);
        String name = ParamText.normalize(call.name());
        String offsetText = call.get("offset", "o");
        BlockVector3 offset = offsetText == null ? BlockVector3.ZERO : vector(offsetText);
        BlockVector3 center = feet.add(offset);
        World world = context.world();
        switch (name) {
            case "box", "b", "caja", "ellipsoid", "e", "elipsoide", "cylinder", "c", "cilindro" -> {
                call.requireOnly("dimensions", "d", "dimensiones", "offset", "o");
                String dimensions = call.get("dimensions", "d", "dimensiones");
                Vec3 size = dimensions == null ? new Vec3(40, 40, 40) : PlacementSettings.dimensions(dimensions);
                int sx = (int) Math.round(size.x());
                int sy = (int) Math.round(size.y());
                int sz = (int) Math.round(size.z());
                BlockVector3 minimum = center.subtract(sx / 2, sy / 2, sz / 2);
                return switch (name) {
                    case "box", "b", "caja" -> new CuboidRegion(world, minimum, minimum.add(sx - 1, sy - 1, sz - 1));
                    case "ellipsoid", "e", "elipsoide" -> new EllipsoidRegion(world, center,
                            Vector3.at(sx / 2.0, sy / 2.0, sz / 2.0));
                    default -> new CylinderRegion(world, center, Vector2.at(sx / 2.0, sz / 2.0), minimum.y(),
                            minimum.y() + sy - 1);
                };
            }
            case "active", "a", "seleccion", "saved", "s", "guardada" -> {
                call.requireOnly("useoriginalposition", "p", "name", "n", "nombre", "offset", "o");
                boolean saved = name.equals("saved") || name.equals("s") || name.equals("guardada");
                Region region;
                if (saved) {
                    String selection = call.get("name", "n", "nombre");
                    if (selection == null) {
                        throw new IllegalArgumentException("Saved necesita el nombre: Saved(Name:mi_seleccion)");
                    }
                    try {
                        region = SelectionBridge.selector(SavedSelections.load(player.getUuid(), selection), world)
                                .getRegion();
                    } catch (IncompleteRegionException exception) {
                        throw new IllegalArgumentException("La selección guardada " + selection + " está incompleta");
                    }
                    region = region.clone();
                } else {
                    region = WorldEditService.selection(player);
                }
                String original = call.get("useoriginalposition", "p");
                boolean keep = original == null || ParamText.bool(original, "UseOriginalPosition");
                try {
                    if (keep) {
                        region.shift(offset);
                    } else {
                        BlockVector3 middle = region.getMinimumPoint().add(region.getMaximumPoint()).divide(2);
                        region.shift(center.subtract(middle));
                    }
                } catch (RegionOperationException exception) {
                    throw new IllegalArgumentException("No se puede mover esa región: " + exception.getMessage());
                }
                return region;
            }
            default -> throw new IllegalArgumentException("Región desconocida para -h: " + call.name()
                    + " (Active, Box, Ellipsoid, Cylinder o Saved)");
        }
    }

    private static BlockVector3 vector(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        String[] parts = trimmed.split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Offset espera (x,y,z)");
        }
        return BlockVector3.at(
                (int) Math.round(ParamText.number(parts[0], -30_000_000, 30_000_000, "Offset")),
                (int) Math.round(ParamText.number(parts[1], -4_096, 4_096, "Offset")),
                (int) Math.round(ParamText.number(parts[2], -30_000_000, 30_000_000, "Offset")));
    }

    /** Caja de la trayectoria con margen para buscar superficies con {@code -b}, dentro del límite de lectura. */
    private static Region pathBox(World world, PathSnapshot path) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockVector3 point : path.points()) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }
        int margin = snapRadius();
        while (margin > 4 && (long) (maxX - minX + 1 + 2 * margin) * (maxY - minY + 1 + 2 * margin)
                * (maxZ - minZ + 1 + 2 * margin) > RegionOperationService.MAX_CAPTURE_BLOCKS) {
            margin -= 4;
        }
        return new CuboidRegion(world, BlockVector3.at(minX - margin, minY - margin, minZ - margin),
                BlockVector3.at(maxX + margin, maxY + margin, maxZ + margin));
    }

    /** La máscara se parsea ahora y se comprueba después en el worker contra la foto, no contra el mundo. */
    private static Mask mask(WorldEditService.PlayerContext context, String input, SnapshotExtent extent) {
        ParserContext parser = parser(context);
        parser.setExtent(extent);
        try {
            return WorldEdit.getInstance().getMaskFactory().parseFromInput(input, parser);
        } catch (InputParseException exception) {
            throw new IllegalArgumentException("Máscara inválida: " + exception.getMessage());
        }
    }

    /** Extensión de WorldEdit que lee la foto: las máscaras se pueden evaluar fuera del hilo del servidor. */
    static final class SnapshotExtent extends NullExtent {
        private volatile RegionSnapshot snapshot;

        void bind(RegionSnapshot value) {
            snapshot = value;
        }

        @Override
        public BlockVector3 getMinimumPoint() {
            RegionSnapshot current = snapshot;
            return current == null ? BlockVector3.ZERO
                    : BlockVector3.at(current.minimumX(), current.minimumY(), current.minimumZ());
        }

        @Override
        public BlockVector3 getMaximumPoint() {
            RegionSnapshot current = snapshot;
            return current == null ? BlockVector3.ZERO
                    : BlockVector3.at(current.maximumX(), current.maximumY(), current.maximumZ());
        }

        @Override
        public BlockState getBlock(BlockVector3 position) {
            RegionSnapshot current = snapshot;
            BlockState state = current == null ? null : current.state(position.x(), position.y(), position.z());
            return state == null ? BlockTypes.AIR.getDefaultState() : state;
        }

        @Override
        public BaseBlock getFullBlock(BlockVector3 position) {
            return getBlock(position).toBaseBlock();
        }
    }

    private record BoundMask(Mask mask, SnapshotExtent extent) {
    }

    // ------------------------------------------------------------------ Cálculo

    private static final class Job {
        private final UUID playerId;
        private final MinecraftServer server;
        private final PlacementSettings.Kind kind;
        private final PlacementSettings settings;
        private final StructureSpec spec;
        private final Resolved resolved;
        private final Vec3 eye;
        private final DirectionExpr.Context view;
        private final BlockVector3 feet;
        private PathSnapshot path;
        private BrushTarget target;

        Job(UUID playerId, MinecraftServer server, PlacementSettings.Kind kind, PlacementSettings settings,
            StructureSpec spec, Resolved resolved, Vec3 eye, DirectionExpr.Context view, BlockVector3 feet) {
            this.playerId = playerId;
            this.server = server;
            this.kind = kind;
            this.settings = settings;
            this.spec = spec;
            this.resolved = resolved;
            this.eye = eye;
            this.view = view;
            this.feet = feet;
        }

        private String description() {
            return switch (kind) {
                case PLACE -> "Colocar";
                case SCATTER -> "Dispersar";
                case ARRAY -> "Array";
            };
        }

        void compute(RegionSnapshot snapshot, Region region, BoundMask mask) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                return;
            }
            Region clip = region == null ? null : region.clone();
            AsyncEngine.submit(
                    playerId,
                    () -> plan(snapshot, clip, mask),
                    output -> server.execute(() -> submit(output)),
                    throwable -> server.execute(() -> {
                        ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                        if (current != null && !(throwable instanceof CancellationException)) {
                            current.sendMessage(message(description() + ": " + readable(throwable), Formatting.RED),
                                    false);
                        }
                    })
            );
        }

        private Output plan(RegionSnapshot snapshot, Region region, BoundMask mask) {
            PlacementBuilder builder = new PlacementBuilder(settings, resolved.structures(), spec.offsets());
            List<Placement> placements = new ArrayList<>();
            LongArrayList surfaces = null;
            switch (kind) {
                case PLACE -> {
                    Vec3 anchor = new Vec3(feet.x() + 0.5, feet.y() + 0.5, feet.z() + 0.5);
                    Vec3 normal = snapshot == null ? target == null ? null : target.faceNormal()
                            : target == null ? groundNormal(snapshot)
                            : ScatterPlanner.normal(snapshot, target.surface().x(), target.surface().y(),
                            target.surface().z());
                    Alignment.Context context = new Alignment.Context(anchor, eye, view, normal, null, null,
                            new Vec3(0, 0, 0), new Vec3(0, 0, 0), new SplittableRandom(settings.seed()),
                            target == null ? null : target.viewDiff());
                    placements.add(builder.build(anchor, context, 1.0));
                }
                case SCATTER -> {
                    if (mask != null) {
                        mask.extent().bind(snapshot);
                    }
                    PlacementSettings.Scatter scatter = settings.scatter();
                    ScatterPlanner.Result result = ScatterPlanner.plan(snapshot,
                            new ScatterPlanner.Settings(scatter.directions(), scatter.threshold(), scatter.density(),
                                    scatter.iterations(), settings.seed()),
                            mask == null ? null : (x, y, z) -> mask.mask().test(BlockVector3.at(x, y, z)));
                    if (result.found() == 0) {
                        throw new IllegalArgumentException("La región no tiene superficies (bloques junto al aire); "
                                + "amplíala para que incluya aire");
                    }
                    surfaces = scatter.cover() == null ? null : result.surfaces();
                    BlockVector3 low = region.getMinimumPoint();
                    BlockVector3 high = region.getMaximumPoint();
                    Vec3 center = new Vec3((low.x() + high.x() + 1) * 0.5, (low.y() + high.y() + 1) * 0.5,
                            (low.z() + high.z() + 1) * 0.5);
                    Vec3 half = new Vec3(Math.max(0.5, (high.x() - low.x() + 1) * 0.5),
                            Math.max(0.5, (high.y() - low.y() + 1) * 0.5),
                            Math.max(0.5, (high.z() - low.z() + 1) * 0.5));
                    int index = 0;
                    for (ScatterPlanner.Point point : result.points()) {
                        Vec3 anchor = point.anchorCenter();
                        Vec3 local = anchor.subtract(center);
                        Alignment.Context context = new Alignment.Context(anchor, eye, view, point.normal(), null,
                                null, local, new Vec3(local.x() / half.x(), local.y() / half.y(), local.z() / half.z()),
                                new SplittableRandom(settings.seed() * GOLDEN + index++), null);
                        placements.add(builder.build(anchor, context, 1.0));
                    }
                }
                case ARRAY -> {
                    PlacementSettings.Array array = settings.array();
                    List<Vec3> controls = new ArrayList<>();
                    for (BlockVector3 point : path.points()) {
                        controls.add(new Vec3(point.x() + 0.5, point.y() + 0.5, point.z() + 0.5));
                    }
                    PlacementPath line = PlacementPath.create(controls, path.closed(), array.tension(), array.bias(),
                            array.continuity(), array.normalMode());
                    ArrayPlanner.SurfaceSnapper snapper = snapshot == null ? null
                            : ArrayPlanner.snapper(snapshot, snapRadius());
                    placements.addAll(ArrayPlanner.plan(line, builder, eye, view, snapper));
                }
            }
            if (placements.isEmpty()) {
                throw new IllegalArgumentException("No ha salido ninguna colocación: sube la densidad (-n) o "
                        + "relaja los filtros (-d, -e, -m)");
            }
            PlacementVoxelizer.Options options = PlacementVoxelizer.Options.defaults()
                    .withPlaceAir(settings.placeAir())
                    .withSmooth(settings.smoothMode(), settings.coverage());
            if (kind == PlacementSettings.Kind.SCATTER && settings.scatter().trim()) {
                options = options.withClip((x, y, z) -> region.contains(BlockVector3.at(x, y, z)));
            }
            PlacementVoxelizer.Result result = PlacementVoxelizer.voxelize(placements, options);
            return build(placements, result, snapshot, surfaces);
        }

        /** La normal del suelo bajo el jugador: el primer bloque que no es aire bajando desde los pies. */
        private Vec3 groundNormal(RegionSnapshot snapshot) {
            for (int y = feet.y(); y >= snapshot.minimumY(); y--) {
                if (!snapshot.isAir(feet.x(), y, feet.z())) {
                    return ScatterPlanner.normal(snapshot, feet.x(), y, feet.z());
                }
            }
            return Alignment.UP;
        }

        private Output build(List<Placement> placements, PlacementVoxelizer.Result result, RegionSnapshot snapshot,
                             LongArrayList surfaces) {
            long[] keys = new long[result.cells().size()];
            int count = 0;
            for (Long2LongMap.Entry entry : Long2LongMaps.fastIterable(result.cells())) {
                keys[count++] = entry.getLongKey();
            }
            // De abajo arriba: la arena, las antorchas y lo colgado encuentran su apoyo ya puesto.
            LongArrays.quickSort(keys, 0, count, (first, second) -> {
                int byY = Integer.compare(PackedBlockPlan.unpackY(first), PackedBlockPlan.unpackY(second));
                return byY != 0 ? byY : Long.compare(first, second);
            });
            PackedBlockPlan.Builder builder = PackedBlockPlan.builder(Math.max(16, count));
            BlockState air = BlockTypes.AIR.getDefaultState();
            Long2ObjectOpenHashMap<BlockState> rotated = new Long2ObjectOpenHashMap<>();
            AffineTransform[] transforms = new AffineTransform[placements.size()];
            boolean patterned = false;
            int written = 0;
            for (int index = 0; index < count; index++) {
                if ((index & 4_095) == 0 && Thread.currentThread().isInterrupted()) {
                    throw new CancellationException();
                }
                long key = keys[index];
                long code = result.cells().get(key);
                int placementIndex = PlacementVoxelizer.Result.placement(code);
                int paletteIndex = PlacementVoxelizer.Result.palette(code);
                int x = PackedBlockPlan.unpackX(key);
                int y = PackedBlockPlan.unpackY(key);
                int z = PackedBlockPlan.unpackZ(key);
                byte piece = result.pieces().get(key);
                Placement placement = placements.get(placementIndex);
                boolean known = snapshot != null && snapshot.contains(x, y, z);
                int expected = known ? snapshot.rawId(x, y, z) : PackedBlockPlan.NO_EXPECTED_STATE;
                if (paletteIndex >= 0 && placement.structure().usesPattern()) {
                    int entry = builder.size();
                    if (known) {
                        builder.addConditionalPattern(BlockVector3.at(x, y, z), expected);
                    } else {
                        builder.addPattern(x, y, z);
                    }
                    builder.setPiece(entry, piece);
                    patterned = true;
                    written++;
                    continue;
                }
                BlockState state;
                if (paletteIndex < 0) {
                    state = air;
                } else {
                    long cacheKey = ((long) placementIndex << 32) | paletteIndex;
                    state = rotated.get(cacheKey);
                    if (state == null) {
                        int structure = resolved.structures().indexOf(placement.structure());
                        BlockState base = resolved.palettes().get(structure).get(paletteIndex);
                        if (transforms[placementIndex] == null) {
                            Mat3 matrix = placement.rotation();
                            transforms[placementIndex] = new AffineTransform(
                                    matrix.m00(), matrix.m01(), matrix.m02(), 0,
                                    matrix.m10(), matrix.m11(), matrix.m12(), 0,
                                    matrix.m20(), matrix.m21(), matrix.m22(), 0);
                        }
                        state = BlockTransformExtent.transform(base, transforms[placementIndex]);
                        rotated.put(cacheKey, state);
                    }
                    if (piece != SmoothPiece.FULL) {
                        BlockState shaped = SmoothBlockStates.shape(state.toBaseBlock(), piece,
                                settings.smooth().materials());
                        if (shaped != null) {
                            state = shaped;
                        } else if (SmoothPiece.isAdded(piece)) {
                            continue;
                        }
                    }
                }
                if (known && state.equals(snapshot.state(x, y, z))) {
                    continue;
                }
                if (known) {
                    builder.addConditionalOverride(x, y, z, expected, state);
                } else {
                    builder.addOverride(x, y, z, state);
                }
                written++;
            }
            LongOpenHashSet cover = new LongOpenHashSet();
            if (surfaces != null) {
                for (int index = 0; index < surfaces.size(); index++) {
                    long key = surfaces.getLong(index);
                    if (result.cells().containsKey(key)) {
                        continue;
                    }
                    int x = PackedBlockPlan.unpackX(key);
                    int y = PackedBlockPlan.unpackY(key);
                    int z = PackedBlockPlan.unpackZ(key);
                    builder.addConditionalPattern(BlockVector3.at(x, y, z), snapshot.rawId(x, y, z));
                    cover.add(key);
                    patterned = true;
                }
            }
            return new Output(builder.build().withPieceMaterials(settings.smooth().materials()), cover,
                    placements.size(), written, patterned);
        }

        private void submit(Output output) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player == null) {
                return;
            }
            if (output.plan().size() == 0) {
                player.sendMessage(message(description() + ": no hay nada que cambiar", Formatting.YELLOW), false);
                return;
            }
            try {
                int queued;
                if (output.patterned()) {
                    WorldEditService.PlayerContext context = WorldEditService.context(player);
                    Pattern shape = resolved.pattern() == null ? null
                            : WorldEditService.parsePatternUnchecked(context, resolved.pattern());
                    Pattern cover = output.cover().isEmpty() ? null
                            : WorldEditService.parsePatternUnchecked(context, settings.scatter().cover());
                    LongOpenHashSet covered = output.cover();
                    queued = BatchEditService.submitStyled(player, output.plan(), position ->
                            covered.contains(PackedBlockPlan.pack(position.x(), position.y(), position.z()))
                                    ? cover : shape, description());
                } else {
                    queued = BatchEditService.submit(player, output.plan(), "air", description());
                }
                if (PlanPreviews.holds(playerId, output.plan())) {
                    return;
                }
                String covered = output.cover().isEmpty() ? "" : " · " + output.cover().size() + " superficies cubiertas";
                player.sendMessage(message(description() + ": " + output.placements() + " "
                        + (output.placements() == 1 ? "estructura" : "estructuras") + " · " + queued
                        + " bloques en cola" + covered + " · //undo para deshacer", Formatting.GREEN), false);
            } catch (IllegalArgumentException | IllegalStateException exception) {
                player.sendMessage(message(description() + ": " + readable(exception), Formatting.RED), false);
            }
        }
    }

    private record Output(PackedBlockPlan plan, LongOpenHashSet cover, int placements, int written,
                          boolean patterned) {
    }

    static Text message(String text, Formatting color) {
        return Text.literal("KomaEdit · ").formatted(Formatting.DARK_AQUA)
                .append(Text.literal(text).formatted(color));
    }

    static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && (cause.getMessage() == null || cause instanceof RuntimeException
                && cause.getCause() instanceof IllegalArgumentException)) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** Para la ayuda: los nombres de las formas de {@code Shape}. */
    public static List<String> shapeNames() {
        return ProceduralShapes.NAMES;
    }
}
