package com.komainu.moxia.recipe;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

public final class KitbashSocketService {
    private static final int MAX_TEMPLATE_BLOCKS = 50_000;

    private KitbashSocketService() {
    }

    public static Template capture(ServerPlayerEntity player, RecipeExtensions extensions) {
        if (!extensions.socketsEnabled()) {
            return Template.EMPTY;
        }
        try {
            Clipboard clipboard = WorldEditService.clipboard(player);
            if (clipboard.getRegion().getVolume() > MAX_TEMPLATE_BLOCKS) {
                throw new IllegalArgumentException("El módulo kitbash supera 50 000 bloques");
            }
            BlockVector3 origin = clipboard.getOrigin();
            List<Voxel> voxels = new ArrayList<>((int) clipboard.getRegion().getVolume());
            for (BlockVector3 source : clipboard.getRegion()) {
                BlockState block = clipboard.getFullBlock(source).toImmutableState();
                if (block.getBlockType().getMaterial().isAir()) {
                    continue;
                }
                voxels.add(new Voxel(
                        source.x() - origin.x(),
                        source.y() - origin.y(),
                        source.z() - origin.z(),
                        block
                ));
            }
            if (voxels.isEmpty()) {
                throw new IllegalArgumentException("El portapapeles kitbash no contiene bloques sólidos");
            }
            return new Template(List.copyOf(voxels));
        } catch (EmptyClipboardException exception) {
            throw new IllegalArgumentException("Copia un módulo con WorldEdit antes de activar Kitbash", exception);
        }
    }

    public static int append(
            PackedBlockPlan.Builder builder,
            Template template,
            PathSnapshot snapshot,
            RecipeExtensions extensions
    ) {
        if (template.voxels().isEmpty()) {
            return 0;
        }
        List<Anchor> anchors = anchors(snapshot, extensions);
        int added = 0;
        for (Anchor anchor : anchors) {
            double angle = Math.atan2(anchor.tangent().x(), anchor.tangent().z());
            double cosine = Math.cos(angle);
            double sine = Math.sin(angle);
            for (Voxel voxel : template.voxels()) {
                int rotatedX = (int) Math.round(voxel.x() * cosine - voxel.z() * sine);
                int rotatedZ = (int) Math.round(voxel.x() * sine + voxel.z() * cosine);
                builder.addOverride(
                        anchor.position().x() + rotatedX,
                        anchor.position().y() + voxel.y(),
                        anchor.position().z() + rotatedZ,
                        voxel.block()
                );
                added++;
            }
        }
        return added;
    }

    private static List<Anchor> anchors(PathSnapshot snapshot, RecipeExtensions extensions) {
        List<BlockVector3> points = snapshot.points();
        return switch (extensions.socketPlacement()) {
            case ENDPOINTS -> List.of(
                    nodeAnchor(points, 0),
                    nodeAnchor(points, points.size() - 1)
            );
            case NODES -> {
                List<Anchor> anchors = new ArrayList<>(points.size());
                for (int index = 0; index < points.size(); index++) {
                    anchors.add(nodeAnchor(points, index));
                }
                yield anchors;
            }
            case INTERVAL -> intervalAnchors(snapshot, extensions.socketInterval());
        };
    }

    private static Anchor nodeAnchor(List<BlockVector3> points, int index) {
        BlockVector3 before = points.get(Math.max(0, index - 1));
        BlockVector3 after = points.get(Math.min(points.size() - 1, index + 1));
        Vec3 tangent = new Vec3(
                after.x() - before.x(),
                after.y() - before.y(),
                after.z() - before.z()
        ).normalized();
        return new Anchor(points.get(index), tangent);
    }

    private static List<Anchor> intervalAnchors(PathSnapshot snapshot, int interval) {
        SampledPath path = SampledPath.createSmooth(snapshot.points(), snapshot.closed());
        List<Anchor> anchors = new ArrayList<>();
        for (double distance = 0.0; distance <= path.length(); distance += interval) {
            SampledPath.Frame frame = path.at(distance);
            anchors.add(new Anchor(block(frame.position()), frame.tangent()));
        }
        return anchors;
    }

    private static BlockVector3 block(Vec3 point) {
        return BlockVector3.at(
                (int) Math.floor(point.x()),
                (int) Math.floor(point.y()),
                (int) Math.floor(point.z())
        );
    }

    public record Template(List<Voxel> voxels) {
        private static final Template EMPTY = new Template(List.of());
    }

    public record Voxel(int x, int y, int z, BlockState block) {
    }

    private record Anchor(BlockVector3 position, Vec3 tangent) {
    }
}
