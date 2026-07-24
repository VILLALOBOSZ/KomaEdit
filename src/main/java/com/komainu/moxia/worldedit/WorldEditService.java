package com.komainu.moxia.worldedit;

import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.path.PathSnapshot;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.FabricPlayer;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WorldEditService {
    private WorldEditService() {
    }

    public static PlayerContext context(ServerPlayerEntity player) {
        FabricPlayer actor = FabricAdapter.adaptPlayer(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        return new PlayerContext(actor, session, actor.getWorld());
    }

    public static boolean hasUsePermission(ServerPlayerEntity player) {
        FabricPlayer actor = context(player).actor();
        return actor.hasPermission("komaedit.use") || actor.hasPermission("moxia.use");
    }

    public static PathSnapshot resolvePath(ServerPlayerEntity player) throws IncompleteRegionException {
        return resolvePath(player, false);
    }

    public static PathSnapshot resolvePath(ServerPlayerEntity player, boolean allowSinglePoint)
            throws IncompleteRegionException {
        PathSnapshot saved = PathManager.saved(player.getUuid());
        int minimum = allowSinglePoint ? 1 : 2;
        if (saved.points().size() >= minimum) {
            return saved;
        }
        return selectionPath(player);
    }

    public static PathSnapshot selectionPath(ServerPlayerEntity player) throws IncompleteRegionException {
        PlayerContext context = context(player);
        Region region = context.session().getSelection(context.world());
        List<BlockVector3> points = switch (region) {
            case CuboidRegion cuboid -> List.of(cuboid.getPos1(), cuboid.getPos2());
            case ConvexPolyhedralRegion convex -> new ArrayList<>(convex.getVertices());
            case Polygonal2DRegion polygon -> polygonPoints(polygon);
            default -> List.of(region.getMinimumPoint(), region.getMaximumPoint());
        };
        Set<BlockVector3> unique = new LinkedHashSet<>(points);
        if (unique.size() < 2) {
            throw new IllegalArgumentException("La selección de WorldEdit necesita dos puntos distintos");
        }
        return new PathSnapshot(new ArrayList<>(unique), false, "selección de WorldEdit");
    }

    public static void validatePattern(ServerPlayerEntity player, String input) throws InputParseException {
        parsePattern(context(player), input);
    }

    public static void setCuboidPoint(
            ServerPlayerEntity player,
            boolean primary,
            BlockPos position,
            boolean resetSelection
    ) {
        PlayerContext context = context(player);
        var selector = context.session().getRegionSelector(context.world());
        BlockVector3 point = BlockVector3.at(position.getX(), position.getY(), position.getZ());
        if (resetSelection || !(selector instanceof CuboidRegionSelector)) {
            selector = new CuboidRegionSelector(context.world());
            context.session().setRegionSelector(context.world(), selector);
        }
        if (primary) {
            selector.selectPrimary(point, PermissiveSelectorLimits.getInstance());
        } else {
            selector.selectSecondary(point, PermissiveSelectorLimits.getInstance());
        }
        context.session().dispatchCUISelection(context.actor());
    }

    public static void setCuboid(ServerPlayerEntity player, BlockPos first, BlockPos second) {
        PlayerContext context = context(player);
        var selector = new CuboidRegionSelector(
                context.world(),
                BlockVector3.at(first.getX(), first.getY(), first.getZ()),
                BlockVector3.at(second.getX(), second.getY(), second.getZ())
        );
        context.session().setRegionSelector(context.world(), selector);
        context.session().dispatchCUISelection(context.actor());
    }

    public static boolean undo(ServerPlayerEntity player) {
        PlayerContext context = context(player);
        return context.session().undo(null, context.actor()) != null;
    }

    public static boolean redo(ServerPlayerEntity player) {
        PlayerContext context = context(player);
        return context.session().redo(null, context.actor()) != null;
    }

    public static Region selection(ServerPlayerEntity player) throws IncompleteRegionException {
        PlayerContext context = context(player);
        return context.session().getSelection(context.world()).clone();
    }

    public static long copySelection(ServerPlayerEntity player) throws IncompleteRegionException, WorldEditException {
        PlayerContext context = context(player);
        Region region = context.session().getSelection(context.world()).clone();
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        ForwardExtentCopy copy = new ForwardExtentCopy(
                context.world(),
                region,
                region.getMinimumPoint(),
                clipboard,
                region.getMinimumPoint()
        );
        copy.setCopyingEntities(false);
        copy.setCopyingBiomes(false);
        Operations.complete(copy);
        clipboard.setOrigin(region.getMinimumPoint());
        context.session().setClipboard(new ClipboardHolder(clipboard));
        return region.getVolume();
    }

    public static int pasteClipboard(ServerPlayerEntity player, BlockPos position) throws WorldEditException {
        PlayerContext context = context(player);
        ClipboardHolder clipboard = context.session().getClipboard();
        EditSession editSession = context.session().createEditSession(context.actor());
        try {
            Operations.complete(clipboard.createPaste(editSession)
                    .to(BlockVector3.at(position.getX(), position.getY(), position.getZ()))
                    .ignoreAirBlocks(false)
                    .copyEntities(false)
                    .copyBiomes(false)
                    .build());
        } finally {
            editSession.close();
            context.session().remember(editSession);
        }
        return editSession.size();
    }

    public static Clipboard clipboard(ServerPlayerEntity player) throws EmptyClipboardException {
        return context(player).session().getClipboard().getClipboard();
    }

    public static int applyBlocks(ServerPlayerEntity player, Iterable<BlockVector3> blocks, String patternInput)
            throws InputParseException, MaxChangedBlocksException {
        PlayerContext context = context(player);
        Pattern pattern = parsePattern(context, patternInput);
        EditSession editSession = context.session().createEditSession(context.actor());
        try {
            for (BlockVector3 block : blocks) {
                editSession.setBlock(block, pattern);
            }
        } finally {
            editSession.close();
            context.session().remember(editSession);
        }
        return editSession.size();
    }

    public static Pattern parsePatternUnchecked(PlayerContext context, String input) {
        try {
            return parsePattern(context, input);
        } catch (InputParseException exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private static Pattern parsePattern(PlayerContext context, String input) throws InputParseException {
        ParserContext parserContext = new ParserContext();
        parserContext.setActor(context.actor());
        parserContext.setSession(context.session());
        parserContext.setWorld(context.world());
        parserContext.setRestricted(!context.actor().hasPermission("worldedit.anyblock"));
        return WorldEdit.getInstance().getPatternFactory().parseFromInput(input, parserContext);
    }

    private static List<BlockVector3> polygonPoints(Polygonal2DRegion polygon) {
        int y = (polygon.getMinimumPoint().y() + polygon.getMaximumPoint().y()) / 2;
        List<BlockVector3> points = new ArrayList<>();
        for (BlockVector2 point : polygon.getPoints()) {
            points.add(BlockVector3.at(point.x(), y, point.z()));
        }
        return points;
    }

    public record PlayerContext(FabricPlayer actor, LocalSession session, World world) {
    }
}
