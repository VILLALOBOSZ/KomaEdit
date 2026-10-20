package com.komainu.moxia.text;

import com.komainu.moxia.config.KomaEditConfig;
import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.edit.SmoothBlockStates;
import com.komainu.moxia.gradient.SavedPalettes;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.persistence.QuietMode;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.region.RegionMasks;
import com.komainu.moxia.region.SnapshotJobs;
import com.komainu.moxia.studio.net.StudioAim;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Direction;

import java.awt.Font;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /komaedit text}: el contorno y la caja se calculan en el hilo del comando (es rápido), la caja se lee
 * repartida entre ticks, el relleno, el borde y las piezas se calculan en un worker y el resultado entra en una sola
 * edición de //undo con los patrones de WorldEdit aplicados en el hilo del servidor.
 */
public final class TextService {
    public static final double REACH = 128.0;

    private TextService() {
    }

    public static Path fontsDirectory() {
        return KomaEditConfig.directory().resolve("fonts");
    }

    /** @return resumen para el jugador */
    public static String start(ServerPlayerEntity player, TextRequest request) {
        KomaPermissions.requireBuild(player, "text");
        Font font = TextFonts.load(request.font(), fontsDirectory());
        TextRaster.Outline outline = TextRaster.outline(font, request.text(), request.rasterSettings());
        SmoothProfile profile = SmoothProfile.parse(request.smoothing());
        SmoothBlockStates.validate(profile);

        WorldEditService.PlayerContext context = WorldEditService.context(player);
        List<Pattern> patterns = new ArrayList<>();
        if (request.palette()) {
            for (String entry : SavedPalettes.parse(player.getUuid(), request.material())) {
                patterns.add(WorldEditService.parsePatternUnchecked(context, entry));
            }
        } else {
            patterns.add(WorldEditService.parsePatternUnchecked(context, request.material()));
        }
        int paletteSize = patterns.size();
        boolean ownBorder = request.borderMaterial() != null;
        if (ownBorder) {
            patterns.add(WorldEditService.parsePatternUnchecked(context, request.borderMaterial()));
        }
        RegionMasks.Bound mask = request.mask() == null ? null : RegionMasks.parse(player, request.mask());

        int[] bounds = outline.blockBounds();
        int margin = (int) Math.ceil(request.border());
        int minU = bounds[0] - margin;
        int minV = bounds[1] - margin;
        int maxU = bounds[2] + margin;
        int maxV = bounds[3] + margin;
        TextPlacement placement = placement(player, request, minU, minV, maxU, maxV);
        int[] box = placement.box(minU, minV, maxU, maxV, request.depth());
        var world = player.getServerWorld();
        if (box[1] < world.getBottomY() || box[4] >= world.getTopY()) {
            throw new IllegalArgumentException("El texto se sale de la altura del mundo (" + box[1] + " a " + box[4]
                    + "); bájalo, súbelo o hazlo más pequeño");
        }
        CuboidRegion region = new CuboidRegion(context.world(), BlockVector3.at(box[0], box[1], box[2]),
                BlockVector3.at(box[3], box[4], box[5]));
        String description = "Texto «" + shorten(request.text()) + "»";
        double border = request.border();
        TextModel.Gradient gradient = request.palette() ? request.gradient() : TextModel.Gradient.VERTICAL;
        int depth = request.depth();
        SnapshotJobs.run(player, region, 1, description, snapshot -> {
            TextRaster glyphs = TextRaster.fill(outline);
            TextRaster ring = border > 0.0 ? glyphs.border(border) : null;
            if (mask != null) {
                mask.bind(snapshot);
            }
            TextModel model = new TextModel(glyphs, ring, placement, depth, paletteSize, gradient, ownBorder,
                    mask == null ? TextModel.CellFilter.ALL : mask::test);
            return model.plan(snapshot, profile, () -> {
                if (Thread.currentThread().isInterrupted()) {
                    throw new java.util.concurrent.CancellationException();
                }
            });
        }, (current, result) -> apply(current, description, result, patterns, profile));
        return description + ": " + (box[3] - box[0] + 1) + "×" + (box[4] - box[1] + 1) + "×" + (box[5] - box[2] + 1)
                + " bloques con la fuente " + font.getFontName();
    }

    private static void apply(ServerPlayerEntity player, String description, TextModel.Result result,
                              List<Pattern> patterns, SmoothProfile profile) {
        if (result.size() == 0) {
            player.sendMessage(SnapshotJobs.message(description + ": no queda ningún bloque que poner (revisa la"
                    + " máscara o el tamaño)", Formatting.YELLOW), false);
            return;
        }
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(result.size());
        Long2IntOpenHashMap layers = new Long2IntOpenHashMap(result.size());
        for (int index = 0; index < result.size(); index++) {
            long packed = result.positions().getLong(index);
            BlockVector3 position = BlockVector3.at(PackedBlockPlan.unpackX(packed), PackedBlockPlan.unpackY(packed),
                    PackedBlockPlan.unpackZ(packed));
            builder.addConditionalPattern(position, result.expected().getInt(index));
            builder.setPiece(builder.size() - 1, result.pieces().getByte(index));
            layers.put(packed, result.layers().getInt(index));
        }
        PackedBlockPlan plan = builder.build().withPieceMaterials(profile.materials());
        List<Pattern> resolved = List.copyOf(patterns);
        int queued = BatchEditService.submitStyled(player, plan,
                position -> resolved.get(layers.get(PackedBlockPlan.pack(position.x(), position.y(), position.z()))),
                description);
        if (PlanPreviews.holds(player.getUuid(), plan)) {
            return;
        }
        String pieces = result.slabs() + result.stairs() == 0 ? ""
                : " (" + result.slabs() + " losas, " + result.stairs() + " escaleras)";
        QuietMode.status(player, SnapshotJobs.message(description + ": " + ProgressLine.group(queued) + " bloques en cola" + pieces
                + " · //undo para deshacer", Formatting.GREEN));
    }

    /**
     * Ancla y ejes según la mirada. Apoyado en una pared el texto mira hacia fuera de ella; de pie sobre el suelo
     * se apoya en él; tumbado o colgado del techo queda centrado en el punto mirado.
     */
    static TextPlacement placement(ServerPlayerEntity player, TextRequest request, int minU, int minV, int maxU,
                                   int maxV) {
        HitResult result = StudioAim.target(player, REACH);
        BlockHitResult hit = result instanceof BlockHitResult block && result.getType() == HitResult.Type.BLOCK
                ? block : null;
        int[] anchor;
        Direction side = null;
        if (hit != null) {
            side = hit.getSide();
            anchor = new int[]{hit.getBlockPos().getX() + side.getOffsetX(), hit.getBlockPos().getY()
                    + side.getOffsetY(), hit.getBlockPos().getZ() + side.getOffsetZ()};
        } else {
            anchor = new int[]{player.getBlockX(), player.getBlockY(), player.getBlockZ()};
        }
        float pitch = player.getPitch();
        TextPlacement.Mode mode = switch (request.orientation()) {
            case STANDING -> TextPlacement.Mode.WALL;
            case LYING -> pitch < 0 ? TextPlacement.Mode.CEILING : TextPlacement.Mode.FLOOR;
            case LOOK -> TextPlacement.modeForPitch(pitch);
        };
        Direction facing = player.getHorizontalFacing();
        if (mode == TextPlacement.Mode.WALL && side != null && side.getAxis().isHorizontal()) {
            // Mirando una pared: el texto queda pegado a ella, de frente.
            facing = side.getOpposite();
        }
        TextPlacement placement = TextPlacement.of(mode, anchor, new int[]{facing.getOffsetX(), 0,
                facing.getOffsetZ()});
        int centerU = Math.floorDiv(minU + maxU, 2);
        int centerV = Math.floorDiv(minV + maxV, 2);
        if (mode != TextPlacement.Mode.WALL || side != null && side.getAxis().isHorizontal()) {
            return placement.shifted(centerU, centerV);
        }
        // De pie sobre el suelo (o sin bloque mirado), con la base en el ancla; bajo un techo, colgando de él.
        return placement.shifted(centerU, side == Direction.DOWN ? maxV : minV);
    }

    private static String shorten(String text) {
        String single = text.replace("\\n", " ").replace('\n', ' ');
        return single.length() <= 24 ? single : single.substring(0, 23) + "…";
    }
}
