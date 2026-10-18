package com.komainu.moxia.image;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BuildEstimate;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.PendingBuilds;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.shape.smooth.SmoothMaterials;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

/**
 * {@code /komaedit image}: convierte una imagen en bloques sobre un plano.
 *
 * <p>En el hilo del servidor se comprueban los permisos y la URL, se resuelven la paleta y los estados de bloque y
 * se fija el plano (el que mira el jugador o la selección). La lectura del archivo o la descarga, la decodificación,
 * el reescalado y la elección de bloques van en un worker de {@link AsyncEngine}. El plan resultante entra en
 * {@link BatchEditService}, que lo coloca por ticks como una sola edición de //undo.</p>
 */
public final class ImageService {
    /** Lado largo por defecto si no se pide medida. */
    public static final int DEFAULT_LONGEST = 128;
    private static final double REACH = 128.0;

    private ImageService() {
    }

    public static void start(ServerPlayerEntity player, ImageRequest request) throws IncompleteRegionException {
        ImageSettings settings = ImageConfig.settings();
        if (request.isUrl()) {
            ImageUrlPolicy.checkAccess(settings, KomaPermissions.can(player, KomaPermissions.ADMIN_IMAGE_URL),
                    request.source());
        } else {
            ImageFiles.resolve(ImageConfig.directory(), request.source());
        }
        ResolvedPalette palette = resolvePalette(player, request.palette());
        if (!palette.missing().isEmpty()) {
            info(player, "Sin color conocido, no se usan: " + String.join(", ", palette.missing()));
        }
        BlockState transparent = switch (request.transparency()) {
            case AIR -> BlockTypes.AIR.getDefaultState();
            case KEEP -> null;
            case BLOCK -> state(player, request.transparentBlock());
        };
        Placement placement = placement(player, request, settings);
        if (placement.horizontal() && palette.hasPanes()) {
            throw new IllegalArgumentException("Los paneles solo valen para obras verticales (en el suelo o el "
                    + "techo quedarían como rejas sueltas); usa la paleta glass");
        }
        BlockState[] states = palette.states(placement.right());
        ServerWorld world = player.getServerWorld();
        int bottom = world.getBottomY();
        int top = world.getTopY() - 1;
        long maxPixels = settings.maxPixels();
        long maxBytes = settings.maxImageBytes();
        java.nio.file.Path directory = request.isUrl() ? null : ImageConfig.directory();
        MinecraftServer server = player.getServer();
        UUID playerId = player.getUuid();
        String name = request.displayName();
        info(player, "Imagen «" + name + "»: " + (request.isUrl() ? "descargando y calculando" : "calculando")
                + " fuera del hilo del servidor…");
        AsyncEngine.submit(
                playerId,
                () -> {
                    byte[] data = request.isUrl() ? download(request.source(), settings)
                            : ImageFiles.read(directory, request.source(), maxBytes);
                    ImageConverter.Converted converted = ImageConverter.convert(data, maxPixels, placement.sizing(),
                            request.brightness(), request.contrast(), palette.palette(), request.dithering(),
                            request.alphaThreshold(), () -> Thread.currentThread().isInterrupted());
                    return plan(converted, placement.layout().apply(converted.size()), palette, states, transparent,
                            bottom, top);
                },
                built -> server.execute(() -> queue(server, playerId, request, palette, placement, built)),
                throwable -> server.execute(() -> {
                    ServerPlayerEntity current = server.getPlayerManager().getPlayer(playerId);
                    if (current != null && !(throwable instanceof CancellationException)) {
                        fail(current, "Imagen «" + name + "»: " + readable(throwable));
                    }
                })
        );
    }

    private static byte[] download(String url, ImageSettings settings) {
        try {
            return ImageDownloader.of(settings).download(url);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException();
        }
    }

    /** El plan de bloques; los que caen fuera de la altura del mundo se cuentan y se dejan. */
    static Built plan(ImageConverter.Converted converted, ImageLayout layout, ResolvedPalette palette,
                      BlockState[] states, BlockState transparent, int bottom, int top) {
        ImageQuantizer.Result blocks = converted.blocks();
        int[] cells = ImageConverter.cells(blocks, palette.panes());
        int width = blocks.width();
        int height = blocks.height();
        PackedBlockPlan.Builder plan = PackedBlockPlan.builder(width * height);
        int outside = 0;
        for (int v = 0; v < height; v++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException();
            }
            for (int u = 0; u < width; u++) {
                int cell = cells[v * width + u];
                BlockState state = cell < 0 ? transparent : states[cell];
                if (state == null) {
                    continue;
                }
                int y = layout.worldY(u, v);
                if (y < bottom || y > top) {
                    outside++;
                    continue;
                }
                plan.addOverride(layout.worldX(u, v), y, layout.worldZ(u, v), state);
            }
        }
        return new Built(converted, layout, plan.build(), outside);
    }

    private static void queue(MinecraftServer server, UUID playerId, ImageRequest request, ResolvedPalette palette,
                              Placement placement, Built built) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            return;
        }
        String name = request.displayName();
        if (built.plan().size() == 0) {
            fail(player, "Imagen «" + name + "»: no queda ningún bloque dentro del mundo");
            return;
        }
        String description = "Imagen " + name;
        ImageQuantizer.Result blocks = built.converted().blocks();
        ImageDecoder.Header header = built.converted().header();
        String summary = String.format(Locale.ROOT, "%s %d×%d → %s bloques de %s, %s · %d bloques distintos · "
                        + "ΔE medio %.1f", header.format().displayName(), header.width(), header.height(),
                built.converted().size(), palette.displayName(), request.dithering().displayName(),
                blocks.distinct(), blocks.meanError())
                + (built.outside() > 0 ? " · " + built.outside() + " fuera de la altura del mundo" : "")
                + " · " + placement.where();
        Runnable submit = () -> {
            try {
                int queued = BatchEditService.submit(player, built.plan(), "air", description);
                if (PlanPreviews.holds(player.getUuid(), built.plan())) {
                    return;
                }
                success(player, "Imagen «" + name + "»: " + summary + " · en cola " + queued
                        + " bloques · //undo para deshacer");
            } catch (IllegalArgumentException | IllegalStateException exception) {
                fail(player, "Imagen «" + name + "»: " + readable(exception));
            }
        };
        PendingBuilds.submitOrHold(player, description, BuildEstimate.of(built.plan()), submit, true);
    }

    // ---------------------------------------------------------------- Paleta

    /** Resuelve la paleta en el hilo del servidor: de serie, o cualquier paleta de KomaEdit con colores de la tabla. */
    static ResolvedPalette resolvePalette(ServerPlayerEntity player, String spec) {
        ImagePalettes.Preset preset = ImagePalettes.Preset.find(spec);
        Map<String, BlockState> states = new LinkedHashMap<>();
        List<BlockPalette.Entry> entries = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<String, Integer> colors = SmoothMaterials.textureColors();
        if (preset != null) {
            for (BlockPalette.Entry entry : ImagePalettes.preset(preset).entries()) {
                BlockType type = BlockTypes.get(entry.key());
                if (type != null && states.putIfAbsent(entry.key(), type.getDefaultState()) == null) {
                    entries.add(entry);
                }
            }
        } else {
            WorldEditService.PlayerContext context = WorldEditService.context(player);
            for (String text : SavedPalettes.parse(player.getUuid(), spec)) {
                BlockState state = WorldEditService.parsePatternUnchecked(context, text)
                        .applyBlock(BlockVector3.ZERO).toImmutableState();
                Integer color = colors.get(state.getBlockType().id());
                if (color == null) {
                    missing.add(text);
                    continue;
                }
                String key = state.getAsString();
                if (states.putIfAbsent(key, state) == null) {
                    entries.add(new BlockPalette.Entry(key, color));
                }
            }
            if (entries.isEmpty()) {
                throw new IllegalArgumentException("Ningún bloque de la paleta tiene color conocido (la tabla es de "
                        + "bloques vanilla): " + String.join(", ", missing));
            }
        }
        BlockPalette palette = new BlockPalette(entries);
        List<BlockState> ordered = new ArrayList<>(palette.size());
        for (BlockPalette.Entry entry : palette.entries()) {
            ordered.add(states.get(entry.key()));
        }
        String displayName = preset != null ? preset.displayName() : "la paleta " + spec;
        return new ResolvedPalette(palette, ordered, displayName, missing);
    }

    private static BlockState state(ServerPlayerEntity player, String text) {
        Pattern pattern = WorldEditService.parsePatternUnchecked(WorldEditService.context(player), text);
        return pattern.applyBlock(BlockVector3.ZERO).toImmutableState();
    }

    /**
     * @param missing entradas de la paleta sin color en la tabla (bloques de mods), que no se usan
     */
    record ResolvedPalette(BlockPalette palette, List<BlockState> base, String displayName, List<String> missing) {
        boolean[] panes() {
            boolean[] panes = new boolean[palette.size()];
            for (int index = 0; index < panes.length; index++) {
                panes[index] = ImagePalettes.isPane(base.get(index).getBlockType().id());
            }
            return panes;
        }

        boolean hasPanes() {
            for (boolean pane : panes()) {
                if (pane) {
                    return true;
                }
            }
            return false;
        }

        /** Estado de cada celda ({@code índice × 4 + conexiones}); los paneles se unen a lo largo de {@code right}. */
        BlockState[] states(int[] right) {
            boolean[] panes = panes();
            String rightName = direction(right).asString();
            String leftName = direction(right).getOpposite().asString();
            BlockState[] states = new BlockState[palette.size() * 4];
            for (int index = 0; index < palette.size(); index++) {
                BlockState state = base.get(index);
                for (int mask = 0; mask < 4; mask++) {
                    states[index * 4 + mask] = panes[index]
                            ? connect(connect(state, rightName, (mask & ImageConverter.CONNECT_RIGHT) != 0), leftName,
                            (mask & ImageConverter.CONNECT_LEFT) != 0)
                            : state;
                }
            }
            return states;
        }

        @SuppressWarnings("unchecked")
        private static BlockState connect(BlockState state, String name, boolean value) {
            Property<Boolean> property = (Property<Boolean>) state.getBlockType().getPropertyMap().get(name);
            return property == null ? state : state.with(property, value);
        }
    }

    // ---------------------------------------------------------------- Plano

    /**
     * Dónde va la obra. {@code sizing} da la medida a partir de la cabecera de la imagen y {@code layout} la coloca.
     */
    record Placement(int[] right, int[] up, Function<ImageDecoder.Header, ImageSize> sizing,
                     Function<ImageSize, ImageLayout> layout, String where) {
        boolean horizontal() {
            return right[1] == 0 && up[1] == 0;
        }
    }

    private static Placement placement(ServerPlayerEntity player, ImageRequest request, ImageSettings settings)
            throws IncompleteRegionException {
        int maxSide = settings.maxBlocksPerSide();
        Direction facing = player.getHorizontalFacing();
        if (request.selection()) {
            Region region = WorldEditService.selection(player);
            int[] min = vector(region.getMinimumPoint());
            int[] max = vector(region.getMaximumPoint());
            int axis = ImageLayout.flatAxis(min, max);
            int[] right;
            int[] up;
            if (axis == 1) {
                right = vector(facing.rotateYClockwise());
                up = vector(facing);
            } else {
                double plane = min[axis] + 0.5;
                double eye = axis == 0 ? player.getX() : player.getZ();
                Direction toward = axis == 0 ? (eye < plane ? Direction.EAST : Direction.WEST)
                        : (eye < plane ? Direction.SOUTH : Direction.NORTH);
                right = vector(toward.rotateYClockwise());
                up = vector(Direction.UP);
            }
            int[] box = ImageLayout.boxSize(min, max, right, up);
            Anchor anchor = request.anchor() == null ? Anchor.CENTER : request.anchor();
            return new Placement(right, up,
                    header -> ImageSize.fit(header.width(), header.height(), box[0], box[1], request.stretch(),
                            maxSide),
                    size -> ImageLayout.inBox(min, max, right, up, size, anchor),
                    "ajustada a la selección " + box[0] + "×" + box[1]);
        }
        int longest = Math.min(DEFAULT_LONGEST, maxSide);
        Function<ImageDecoder.Header, ImageSize> sizing = header -> ImageSize.of(header.width(), header.height(),
                request.width(), request.height(), longest, maxSide);
        HitResult result = player.raycast(REACH, 1.0F, false);
        int[] target;
        int[] right;
        int[] up;
        Anchor fallback;
        String where;
        if (result instanceof BlockHitResult hit && result.getType() == HitResult.Type.BLOCK) {
            Direction side = hit.getSide();
            BlockPos position = request.surface() ? hit.getBlockPos() : hit.getBlockPos().offset(side);
            target = new int[]{position.getX(), position.getY(), position.getZ()};
            if (side.getAxis().isHorizontal()) {
                right = vector(side.getOpposite().rotateYClockwise());
                up = vector(Direction.UP);
                fallback = Anchor.BOTTOM;
                where = request.surface() ? "sobre la pared" : "delante de la pared";
            } else {
                right = vector(facing.rotateYClockwise());
                up = vector(facing);
                fallback = Anchor.CENTER;
                where = side == Direction.UP ? (request.surface() ? "en el suelo" : "sobre el suelo")
                        : (request.surface() ? "en el techo" : "bajo el techo");
            }
        } else {
            if (request.surface()) {
                throw new IllegalArgumentException("Con -r tienes que mirar un bloque (a menos de "
                        + (int) REACH + " bloques)");
            }
            float pitch = player.getPitch();
            if (pitch < -60.0F || pitch > 60.0F) {
                BlockPos position = BlockPos.ofFloored(player.getEyePos()).offset(pitch < 0 ? Direction.UP
                        : Direction.DOWN, 2);
                target = new int[]{position.getX(), position.getY(), position.getZ()};
                right = vector(facing.rotateYClockwise());
                up = vector(facing);
                fallback = Anchor.CENTER;
                where = "en horizontal, " + (pitch < 0 ? "sobre" : "bajo") + " ti";
            } else {
                BlockPos position = player.getBlockPos().offset(facing, 2);
                target = new int[]{position.getX(), position.getY(), position.getZ()};
                right = vector(facing.rotateYClockwise());
                up = vector(Direction.UP);
                fallback = Anchor.BOTTOM;
                where = "de pie delante de ti";
            }
        }
        Anchor anchor = request.anchor() == null ? fallback : request.anchor();
        int[] finalTarget = target;
        int[] finalRight = right;
        int[] finalUp = up;
        return new Placement(right, up, sizing,
                size -> ImageLayout.anchored(finalTarget, finalRight, finalUp, size, anchor),
                where + ", anclada " + anchor.id());
    }

    static Direction direction(int[] vector) {
        for (Direction direction : Direction.values()) {
            if (direction.getOffsetX() == vector[0] && direction.getOffsetY() == vector[1]
                    && direction.getOffsetZ() == vector[2]) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Dirección no unitaria");
    }

    private static int[] vector(Direction direction) {
        return new int[]{direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ()};
    }

    private static int[] vector(BlockVector3 vector) {
        return new int[]{vector.x(), vector.y(), vector.z()};
    }

    record Built(ImageConverter.Converted converted, ImageLayout layout, PackedBlockPlan plan, int outside) {
    }

    // ---------------------------------------------------------------- Mensajes

    static String readable(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getMessage() == null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static void info(ServerPlayerEntity player, String message) {
        player.sendMessage(prefix().append(Text.literal(message).formatted(Formatting.GRAY)), false);
    }

    private static void success(ServerPlayerEntity player, String message) {
        player.sendMessage(prefix().append(Text.literal(message).formatted(Formatting.GREEN)), false);
    }

    private static void fail(ServerPlayerEntity player, String message) {
        player.sendMessage(prefix().append(Text.literal(message).formatted(Formatting.RED)), false);
    }

    private static MutableText prefix() {
        return Text.literal("KomaEdit · ").formatted(Formatting.GOLD);
    }
}
