package com.komainu.moxia.worldedit.ez;

import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector2;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.ConvexPolyhedralRegion;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.CylinderRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.RegionSelector;
import com.sk89q.worldedit.regions.selector.ConvexPolyhedralRegionSelector;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.CylinderRegionSelector;
import com.sk89q.worldedit.regions.selector.EllipsoidRegionSelector;
import com.sk89q.worldedit.regions.selector.ExtendingCuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import com.sk89q.worldedit.regions.selector.SphereRegionSelector;
import com.sk89q.worldedit.regions.selector.limit.PermissiveSelectorLimits;
import com.sk89q.worldedit.world.World;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;

/** Pasa la selección de WorldEdit a {@link SelectionShape} y al revés. Solo en el hilo del servidor. */
public final class SelectionBridge {
    private SelectionBridge() {
    }

    public static SelectionShape current(ServerPlayerEntity player) throws IncompleteRegionException {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        RegionSelector selector = context.session().getRegionSelector(context.world());
        return read(selector, context.world());
    }

    public static void apply(ServerPlayerEntity player, SelectionShape shape) {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        context.session().setRegionSelector(context.world(), selector(shape, context.world()));
        context.session().dispatchCUISelection(context.actor());
    }

    public static SelectionShape read(RegionSelector selector, World world) throws IncompleteRegionException {
        String worldName = world == null ? null : world.getName();
        if (selector instanceof CuboidRegionSelector) {
            CuboidRegion region = (CuboidRegion) selector.getRegion();
            SelectionShape.Kind kind = selector instanceof ExtendingCuboidRegionSelector
                    ? SelectionShape.Kind.EXTEND : SelectionShape.Kind.CUBOID;
            return new SelectionShape(kind, List.of(region.getPos1(), region.getPos2()), 0, 0, 0, 0, 0, worldName);
        }
        if (selector instanceof Polygonal2DRegionSelector) {
            Polygonal2DRegion region = (Polygonal2DRegion) selector.getRegion();
            List<BlockVector3> points = new ArrayList<>();
            for (BlockVector2 point : region.getPoints()) {
                points.add(point.toBlockVector3(region.getMinimumY()));
            }
            return new SelectionShape(SelectionShape.Kind.POLYGON, points, 0, 0, 0, region.getMinimumY(),
                    region.getMaximumY(), worldName);
        }
        if (selector instanceof ConvexPolyhedralRegionSelector) {
            ConvexPolyhedralRegion region = (ConvexPolyhedralRegion) selector.getRegion();
            return new SelectionShape(SelectionShape.Kind.CONVEX, new ArrayList<>(region.getVertices()), 0, 0, 0, 0,
                    0, worldName);
        }
        if (selector instanceof EllipsoidRegionSelector) {
            EllipsoidRegion region = (EllipsoidRegion) selector.getRegion();
            Vector3 radius = region.getRadius();
            SelectionShape.Kind kind = selector instanceof SphereRegionSelector
                    ? SelectionShape.Kind.SPHERE : SelectionShape.Kind.ELLIPSOID;
            return new SelectionShape(kind, List.of(region.getCenter().toBlockPoint()), radius.x(), radius.y(),
                    radius.z(), 0, 0, worldName);
        }
        if (selector instanceof CylinderRegionSelector) {
            CylinderRegion region = (CylinderRegion) selector.getRegion();
            Vector2 radius = region.getRadius();
            return new SelectionShape(SelectionShape.Kind.CYLINDER, List.of(region.getCenter().toBlockPoint()),
                    radius.x(), 0, radius.z(), region.getMinimumY(), region.getMaximumY(), worldName);
        }
        throw new IllegalArgumentException("Este tipo de selección (" + selector.getTypeName()
                + ") no se puede guardar ni mover");
    }

    public static RegionSelector selector(SelectionShape shape, World world) {
        List<BlockVector3> points = shape.points();
        return switch (shape.kind()) {
            case CUBOID -> new CuboidRegionSelector(world, points.getFirst(), points.getLast());
            case EXTEND -> new ExtendingCuboidRegionSelector(world, points.getFirst(), points.getLast());
            case POLYGON -> {
                List<BlockVector2> flat = new ArrayList<>(points.size());
                for (BlockVector3 point : points) {
                    flat.add(point.toBlockVector2());
                }
                yield new Polygonal2DRegionSelector(world, flat, shape.minY(), shape.maxY());
            }
            case CONVEX -> {
                ConvexPolyhedralRegionSelector convex = new ConvexPolyhedralRegionSelector(world);
                convex.selectPrimary(points.getFirst(), PermissiveSelectorLimits.getInstance());
                for (int index = 1; index < points.size(); index++) {
                    convex.selectSecondary(points.get(index), PermissiveSelectorLimits.getInstance());
                }
                yield convex;
            }
            case ELLIPSOID -> new EllipsoidRegionSelector(world, points.getFirst(),
                    Vector3.at(shape.radiusX(), shape.radiusY(), shape.radiusZ()));
            case SPHERE -> new SphereRegionSelector(world, points.getFirst(), (int) Math.ceil(shape.radiusX()));
            case CYLINDER -> new CylinderRegionSelector(world, points.getFirst().toBlockVector2(),
                    Vector2.at(shape.radiusX(), shape.radiusZ()), shape.minY(), shape.maxY());
        };
    }
}
