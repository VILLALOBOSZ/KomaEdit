package com.komainu.moxia.shape;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ShapeGenerator {
    public static final int MAX_GENERATED_BLOCKS = 1_500_000;
    private static final double TRACE_STEP = 0.35;
    private static final double SCALE_INCLINATION_DEGREES = 18.0;

    private ShapeGenerator() {
    }

    public static Set<BlockVector3> generate(SampledPath path, ShapeParameters parameters) {
        VoxelCanvas canvas = new VoxelCanvas(MAX_GENERATED_BLOCKS);
        switch (parameters.shape()) {
            case CHAINLINK -> chainlink(canvas, path, parameters);
            case SCALES -> scales(canvas, path, parameters);
            case SPIKED_SCALES -> spikedScales(canvas, path, parameters);
            case BEADS -> beads(canvas, path, parameters);
            case COIL -> coil(canvas, path, parameters);
            case CROWNS -> crowns(canvas, path, parameters);
            case HONEYCOMB -> honeycomb(canvas, path, parameters);
            case OSCILLATE -> oscillate(canvas, path, parameters);
            case RIB_LOOPS -> ribLoops(canvas, path, parameters);
            case RINGS -> rings(canvas, path, parameters);
            case TILES -> tiles(canvas, path, parameters);
            case HEX_ARMOR -> hexArmor(canvas, path, parameters);
            case VORONOI -> voronoiCells(canvas, path, parameters);
            case KNOTS -> knots(canvas, path, parameters);
            case NOODLES -> noodles(canvas, path, parameters);
            case MANDALA -> mandala(canvas, path, parameters);
            case CLOUDS -> organicCloud(canvas, path, parameters);
            case WEB -> web(canvas, path, parameters);
            case PILLAR -> monumentalPillar(canvas, path, parameters);
            case PILLAR_ARCHES -> pillarArches(canvas, path, parameters);
            case TORII -> torii(canvas, path, parameters);
            case ARCH_BRIDGE -> archBridge(canvas, path, parameters);
        }
        return canvas.blocks();
    }

    public static Set<BlockVector3> generateStamp(
            Vec3 center,
            Vec3 surfaceNormal,
            Vec3 preferredUp,
            ShapeParameters parameters
    ) {
        throw new IllegalArgumentException("El catálogo contiene únicamente geometría 3D sobre trayectorias");
    }

    private static void beads(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double gap = Math.clamp(parameters.options().get("gap", 0.0), -0.5, 2.0);
        double pitch = parameters.radius() * scaledSpacing(parameters, 1.55, 4.60)
                + gap * parameters.radius();
        for (double distance : stations(path, pitch)) {
            canvas.sphereShell(path.at(distance).position(), parameters.radius(), parameters.thickness());
        }
    }

    private static void cubes(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double gap = Math.clamp(parameters.options().get("gap", 0.08), -0.75, 4.0);
        double pitch = Math.max(parameters.spacing(), parameters.radius() * 2.0 * (1.0 + gap));
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            Vec3[] axes = {frame.tangent(), frame.normal(), frame.binormal()};
            for (int normalAxis = 0; normalAxis < axes.length; normalAxis++) {
                Vec3 firstAxis = axes[(normalAxis + 1) % axes.length];
                Vec3 secondAxis = axes[(normalAxis + 2) % axes.length];
                for (int sign : new int[]{-1, 1}) {
                    canvas.rectangle(
                            frame.position().add(axes[normalAxis].multiply(sign * parameters.radius())),
                            firstAxis,
                            secondAxis,
                            parameters.radius(),
                            parameters.radius(),
                            parameters.thickness()
                    );
                }
            }
        }
    }

    private static void braids(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        List<Vec3> previous = new ArrayList<>(3);
        previous.add(null);
        previous.add(null);
        previous.add(null);
        int thickness = structuralThickness(parameters);
        for (double distance : traceDistances(path)) {
            SampledPath.Frame frame = path.at(distance);
            for (int strand = 0; strand < 3; strand++) {
                double angle = Math.PI * 2.0 * distance / parameters.spacing() + strand * Math.PI * 2.0 / 3.0;
                Vec3 point = radial(frame, angle, parameters.radius());
                if (previous.get(strand) != null) {
                    canvas.line(previous.get(strand), point, thickness);
                }
                previous.set(strand, point);
            }
        }
    }

    private static void helix(
            VoxelCanvas canvas,
            SampledPath path,
            ShapeParameters parameters,
            int strands
    ) {
        Vec3[] previous = new Vec3[strands];
        int strandThickness = Math.clamp(structuralThickness(parameters) + parameters.thickness() / 2, 1, 6);
        int rungThickness = Math.max(1, strandThickness - 1);
        double pitch = Math.max(parameters.spacing() * 1.5, parameters.radius() * 1.15);
        double roll = Math.toRadians(parameters.rollDegrees());
        for (double distance : traceDistances(path)) {
            SampledPath.Frame frame = path.at(distance);
            double rotation = roll + Math.PI * 2.0 * distance / pitch;
            for (int strand = 0; strand < strands; strand++) {
                double angle = rotation + strand * Math.PI * 2.0 / strands;
                Vec3 point = radial(frame, angle, parameters.radius());
                if (previous[strand] != null) {
                    canvas.line(previous[strand], point, strandThickness);
                }
                previous[strand] = point;
            }
        }

        double rungSpacing = Math.max(3.0, parameters.spacing());
        int station = 0;
        for (double distance : stations(path, rungSpacing)) {
            SampledPath.Frame frame = path.at(distance);
            double rotation = roll + Math.PI * 2.0 * distance / pitch;
            Vec3[] vertices = new Vec3[strands];
            for (int strand = 0; strand < strands; strand++) {
                double angle = rotation + strand * Math.PI * 2.0 / strands;
                vertices[strand] = radial(frame, angle, parameters.radius());
            }
            if (strands == 2) {
                canvas.line(vertices[0], vertices[1], rungThickness);
            } else {
                for (int strand = 0; strand < strands; strand++) {
                    canvas.line(vertices[strand], vertices[(strand + 1) % strands], rungThickness);
                }
                if ((station & 1) == 0) {
                    for (Vec3 vertex : vertices) {
                        canvas.line(frame.position(), vertex, Math.max(1, rungThickness - 1));
                    }
                }
            }
            station++;
        }
    }

    private static void web(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        List<Vec3> anchors = uniqueWebAnchors(path.controlPoints());
        if (anchors.size() == 2) {
            orbWeb(canvas, automaticWebAnchors(anchors.get(0), anchors.get(1), parameters), parameters);
            return;
        }
        orbWeb(canvas, sortedWebAnchors(anchors, parameters), parameters);
    }

    private static List<Vec3> uniqueWebAnchors(List<Vec3> controls) {
        List<Vec3> unique = new ArrayList<>(controls.size());
        for (Vec3 control : controls) {
            boolean duplicate = unique.stream().anyMatch(existing -> existing.distance(control) < 0.25);
            if (!duplicate) {
                unique.add(control);
            }
        }
        if (unique.size() < 2) {
            throw new IllegalArgumentException("La telaraña necesita al menos dos anclajes diferentes");
        }
        return unique;
    }

    private static List<Vec3> automaticWebAnchors(
            Vec3 first,
            Vec3 second,
            ShapeParameters parameters
    ) {
        Vec3 center = first.lerp(second, 0.5);
        Vec3 majorAxis = second.subtract(first).normalized();
        Vec3 reference = Math.abs(majorAxis.y()) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 verticalAxis = reference.subtract(majorAxis.multiply(reference.dot(majorAxis))).normalized();
        Vec3 depthAxis = majorAxis.cross(verticalAxis).normalized();
        double roll = Math.toRadians(parameters.rollDegrees());
        Vec3 minorAxis = verticalAxis.multiply(Math.cos(roll))
                .add(depthAxis.multiply(Math.sin(roll)))
                .normalized();
        double majorRadius = first.distance(second) * 0.5;
        double minorRadius = Math.min(majorRadius * 0.92, Math.max(3.0, parameters.radius()));
        int spokes = Math.clamp(14 - parameters.spacing(), 6, 12);
        if ((spokes & 1) != 0) {
            spokes++;
        }
        List<Vec3> anchors = new ArrayList<>(spokes);
        for (int spoke = 0; spoke < spokes; spoke++) {
            double angle = Math.PI * 2.0 * spoke / spokes;
            anchors.add(center
                    .add(majorAxis.multiply(Math.cos(angle) * majorRadius))
                    .add(minorAxis.multiply(Math.sin(angle) * minorRadius)));
        }
        return anchors;
    }

    private static List<Vec3> sortedWebAnchors(
            List<Vec3> anchors,
            ShapeParameters parameters
    ) {
        Vec3 center = average(anchors);
        Vec3 primaryAxis = anchors.stream()
                .map(anchor -> anchor.subtract(center))
                .max((first, second) -> Double.compare(first.length(), second.length()))
                .orElseThrow()
                .normalized();
        Vec3 planeNormal = anchors.stream()
                .map(anchor -> primaryAxis.cross(anchor.subtract(center)))
                .max((first, second) -> Double.compare(first.length(), second.length()))
                .orElseThrow();
        if (planeNormal.length() < 1.0E-4) {
            Vec3 first = anchors.getFirst();
            Vec3 second = anchors.stream()
                    .max((left, right) -> Double.compare(left.distance(first), right.distance(first)))
                    .orElseThrow();
            return automaticWebAnchors(first, second, parameters);
        }
        planeNormal = planeNormal.normalized();
        Vec3 secondaryAxis = planeNormal.cross(primaryAxis).normalized();
        Vec3 resolvedPrimary = primaryAxis;
        Vec3 resolvedSecondary = secondaryAxis;
        return anchors.stream()
                .sorted((first, second) -> Double.compare(
                        webAngle(first, center, resolvedPrimary, resolvedSecondary),
                        webAngle(second, center, resolvedPrimary, resolvedSecondary)
                ))
                .toList();
    }

    private static double webAngle(Vec3 point, Vec3 center, Vec3 primaryAxis, Vec3 secondaryAxis) {
        Vec3 offset = point.subtract(center);
        return Math.atan2(offset.dot(secondaryAxis), offset.dot(primaryAxis));
    }

    private static void orbWeb(
            VoxelCanvas canvas,
            List<Vec3> anchors,
            ShapeParameters parameters
    ) {
        Vec3 center = average(anchors);
        Vec3 normal = webNormal(anchors, center);
        double averageRadius = anchors.stream()
                .mapToDouble(anchor -> anchor.distance(center))
                .average()
                .orElse(1.0);
        double ringGap = scaledSpacing(parameters, 2.5, 8.0);
        double hubFraction = Math.clamp(ringGap / Math.max(averageRadius, ringGap) * 0.55, 0.06, 0.18);
        int ringCount = Math.clamp(
                (int) Math.ceil(averageRadius * (1.0 - hubFraction) / ringGap),
                2,
                64
        );
        int thickness = parameters.thickness();
        double twist = parameters.rollDegrees() / 360.0 * anchors.size() * 0.45;
        double sag = Math.min(averageRadius * 0.10, parameters.radius() * 0.18);

        List<List<Vec3>> rings = new ArrayList<>(ringCount);
        for (int ring = 1; ring <= ringCount; ring++) {
            double progress = hubFraction + (1.0 - hubFraction) * ring / ringCount;
            List<Vec3> points = new ArrayList<>(anchors.size());
            for (int spoke = 0; spoke < anchors.size(); spoke++) {
                points.add(webPoint(anchors, center, normal, spoke, progress, twist, sag));
            }
            rings.add(points);
            connectClosed(canvas, points, thickness);
        }

        for (int spoke = 0; spoke < anchors.size(); spoke++) {
            Vec3 previous = center;
            int radialSteps = Math.max(8, ringCount * 3);
            for (int step = 1; step <= radialSteps; step++) {
                double progress = step / (double) radialSteps;
                Vec3 point = webPoint(anchors, center, normal, spoke, progress, twist, sag);
                canvas.line(previous, point, thickness);
                previous = point;
            }
        }

        int hubThickness = Math.min(4, thickness + 1);
        for (Vec3 point : rings.getFirst()) {
            canvas.line(center, point, hubThickness);
        }
    }

    private static Vec3 webPoint(
            List<Vec3> anchors,
            Vec3 center,
            Vec3 normal,
            int spoke,
            double progress,
            double twist,
            double sag
    ) {
        double shifted = spoke + twist * (1.0 - progress);
        int firstIndex = Math.floorMod((int) Math.floor(shifted), anchors.size());
        int secondIndex = (firstIndex + 1) % anchors.size();
        double fraction = shifted - Math.floor(shifted);
        Vec3 boundary = anchors.get(firstIndex).lerp(anchors.get(secondIndex), fraction);
        double depth = Math.sin(Math.PI * progress) * sag;
        return center.lerp(boundary, progress).add(normal.multiply(depth));
    }

    private static Vec3 webNormal(List<Vec3> anchors, Vec3 center) {
        Vec3 strongest = new Vec3(0, 0, 0);
        for (int first = 0; first < anchors.size(); first++) {
            Vec3 firstOffset = anchors.get(first).subtract(center);
            for (int second = first + 1; second < anchors.size(); second++) {
                Vec3 candidate = firstOffset.cross(anchors.get(second).subtract(center));
                if (candidate.length() > strongest.length()) {
                    strongest = candidate;
                }
            }
        }
        if (strongest.length() >= 1.0E-4) {
            return strongest.normalized();
        }
        Vec3 axis = anchors.getLast().subtract(anchors.getFirst()).normalized();
        Vec3 reference = Math.abs(axis.y()) < 0.92 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return axis.cross(reference).normalized();
    }

    private static Vec3 average(List<Vec3> points) {
        Vec3 sum = new Vec3(0, 0, 0);
        for (Vec3 point : points) {
            sum = sum.add(point);
        }
        return sum.multiply(1.0 / points.size());
    }

    private static void connectClosed(VoxelCanvas canvas, List<Vec3> points, int thickness) {
        for (int index = 0; index < points.size(); index++) {
            canvas.line(points.get(index), points.get((index + 1) % points.size()), thickness);
        }
    }

    private static void coil(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        int thickness = Math.clamp(structuralThickness(parameters) + parameters.thickness() / 2, 1, 6);
        double pitch = parameters.radius() * scaledSpacing(parameters, 0.90, 3.40);
        double roll = Math.toRadians(parameters.rollDegrees());
        Vec3 previous = null;
        for (double distance : traceDistances(path)) {
            SampledPath.Frame frame = path.at(distance);
            double angle = roll + Math.PI * 2.0 * distance / pitch;
            Vec3 point = radial(frame, angle, parameters.radius());
            if (previous != null) {
                canvas.line(previous, point, thickness);
            }
            previous = point;
        }
    }

    private static void ribLoops(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        int thickness = structuralThickness(parameters);
        double roll = Math.toRadians(parameters.rollDegrees());
        double pitch = parameters.radius() * scaledSpacing(parameters, 0.55, 1.75);
        Vec3 previousApex = null;
        int station = 0;
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            Vec3 upAxis = frame.normal().multiply(Math.cos(roll))
                    .add(frame.binormal().multiply(Math.sin(roll))).normalized();
            Vec3 sideAxis = frame.binormal().multiply(Math.cos(roll))
                    .subtract(frame.normal().multiply(Math.sin(roll))).normalized();
            double lean = ((station & 1) == 0 ? -1.0 : 1.0) * parameters.radius() * 0.24;
            int steps = Math.max(24, (int) Math.ceil(Math.PI * parameters.radius() * 1.35));
            Vec3 previous = null;
            Vec3 apex = null;
            for (int step = 0; step <= steps; step++) {
                double angle = Math.PI * step / steps;
                double side = -Math.cos(angle) * parameters.radius();
                double lift = Math.pow(Math.sin(angle), 0.72) * parameters.radius() * 1.22;
                Vec3 point = frame.position()
                        .add(sideAxis.multiply(side))
                        .add(upAxis.multiply(lift))
                        .add(frame.tangent().multiply(lean * Math.sin(angle)));
                if (previous != null) {
                    canvas.line(previous, point, thickness);
                }
                if (step == steps / 2) {
                    apex = point;
                }
                previous = point;
            }
            if (previousApex != null && apex != null) {
                canvas.line(previousApex, apex, Math.max(1, thickness - 1));
            }
            previousApex = apex;
            station++;
        }
    }

    private static void infinityLoops(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        int thickness = structuralThickness(parameters);
        double pitch = Math.max(parameters.spacing(), parameters.radius() * 1.45);
        double roll = Math.toRadians(parameters.rollDegrees());
        int station = 0;
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            double orientation = roll + (station & 1) * Math.PI * 0.5;
            Vec3 firstAxis = frame.normal().multiply(Math.cos(orientation))
                    .add(frame.binormal().multiply(Math.sin(orientation))).normalized();
            Vec3 secondAxis = frame.binormal().multiply(Math.cos(orientation))
                    .subtract(frame.normal().multiply(Math.sin(orientation))).normalized();
            int steps = Math.max(48, (int) Math.ceil(Math.PI * parameters.radius() * 3.0));
            Vec3 previous = null;
            for (int step = 0; step <= steps; step++) {
                double angle = Math.PI * 2.0 * step / steps;
                double first = Math.sin(angle) * parameters.radius();
                double second = Math.sin(angle * 2.0) * parameters.radius() * 0.58;
                double depth = Math.cos(angle) * parameters.radius() * 0.16;
                Vec3 point = frame.position()
                        .add(firstAxis.multiply(first))
                        .add(secondAxis.multiply(second))
                        .add(frame.tangent().multiply(depth));
                if (previous != null) {
                    canvas.line(previous, point, thickness);
                }
                previous = point;
            }
            station++;
        }
    }

    private static void crowns(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        int thickness = structuralThickness(parameters);
        double pitch = parameters.radius() * scaledSpacing(parameters, 0.80, 2.30);
        double roll = Math.toRadians(parameters.rollDegrees());
        int station = 0;
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            double orientation = roll + (station & 1) * Math.PI / 5.0;
            Vec3 firstAxis = frame.normal().multiply(Math.cos(orientation))
                    .add(frame.binormal().multiply(Math.sin(orientation))).normalized();
            Vec3 secondAxis = frame.binormal().multiply(Math.cos(orientation))
                    .subtract(frame.normal().multiply(Math.sin(orientation))).normalized();
            int petals = 5 + station % 2;
            int steps = Math.max(60, (int) Math.ceil(Math.PI * parameters.radius() * 3.2));
            Vec3 previous = null;
            for (int step = 0; step <= steps; step++) {
                double angle = Math.PI * 2.0 * step / steps;
                double outline = parameters.radius() * (0.72 + 0.28 * Math.cos(angle * petals));
                Vec3 point = frame.position()
                        .add(firstAxis.multiply(Math.cos(angle) * outline))
                        .add(secondAxis.multiply(Math.sin(angle) * outline));
                if (previous != null) {
                    canvas.line(previous, point, thickness);
                }
                previous = point;
            }
            station++;
        }
    }

    private static void orbits(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        int thickness = structuralThickness(parameters);
        double pitch = Math.max(parameters.spacing(), parameters.radius() * 1.65);
        double roll = Math.toRadians(parameters.rollDegrees());
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            Vec3 firstRadial = frame.normal().multiply(Math.cos(roll))
                    .add(frame.binormal().multiply(Math.sin(roll))).normalized();
            Vec3 secondRadial = frame.binormal().multiply(Math.cos(roll))
                    .subtract(frame.normal().multiply(Math.sin(roll))).normalized();
            ellipse(canvas, frame.position(), firstRadial, secondRadial,
                    parameters.radius(), parameters.radius(), thickness);
            ellipse(canvas, frame.position(), firstRadial, frame.tangent(),
                    parameters.radius(), parameters.radius() * 0.78, thickness);
            ellipse(canvas, frame.position(), secondRadial, frame.tangent(),
                    parameters.radius(), parameters.radius() * 0.78, thickness);
        }
    }

    private static void oscillate(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        Vec3 previousFirst = null;
        Vec3 previousSecond = null;
        double wavelength = parameters.radius() * scaledSpacing(parameters, 1.10, 4.50);
        for (double distance : traceDistances(path)) {
            SampledPath.Frame frame = path.at(distance);
            double angle = Math.PI * 2.0 * distance / wavelength;
            Vec3 center = frame.position().add(
                    frame.normal().multiply(Math.sin(angle) * parameters.radius() * 0.42)
            );
            Vec3 widthAxis = frame.normal().multiply(Math.cos(angle))
                    .add(frame.binormal().multiply(Math.sin(angle)))
                    .normalized();
            Vec3 first = center.add(widthAxis.multiply(parameters.radius()));
            Vec3 second = center.subtract(widthAxis.multiply(parameters.radius()));
            canvas.line(first, second, parameters.thickness());
            if (previousFirst != null) {
                canvas.line(previousFirst, first, parameters.thickness());
                canvas.line(previousSecond, second, parameters.thickness());
            }
            previousFirst = first;
            previousSecond = second;
        }
    }

    private static void fishnet(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        int strands = Math.clamp(
                (int) Math.round(Math.PI * 2.0 * parameters.radius() / Math.max(2.0, parameters.spacing() * 0.45)),
                4,
                12
        );
        int thickness = structuralThickness(parameters);
        for (int direction : new int[]{-1, 1}) {
            Vec3[] previous = new Vec3[strands];
            for (double distance : traceDistances(path)) {
                SampledPath.Frame frame = path.at(distance);
                for (int strand = 0; strand < strands; strand++) {
                    double phase = strand * Math.PI * 2.0 / strands;
                    double angle = phase + direction * Math.PI * 2.0 * distance / (parameters.spacing() * 2.0);
                    Vec3 point = radial(frame, angle, parameters.radius());
                    if (previous[strand] != null) {
                        canvas.line(previous[strand], point, thickness);
                    }
                    previous[strand] = point;
                }
            }
        }
    }

    private static void honeycomb(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        Vec3[] previous = null;
        int station = 0;
        int thickness = structuralThickness(parameters);
        double pitch = parameters.radius() * scaledSpacing(parameters, 0.45, 1.50);
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            Vec3[] vertices = new Vec3[6];
            double offset = station % 2 == 0 ? 0.0 : Math.PI / 6.0;
            for (int vertex = 0; vertex < vertices.length; vertex++) {
                vertices[vertex] = radial(frame, offset + vertex * Math.PI / 3.0, parameters.radius());
            }
            for (int vertex = 0; vertex < vertices.length; vertex++) {
                canvas.line(vertices[vertex], vertices[(vertex + 1) % 6], thickness);
            }
            if (previous != null) {
                for (int vertex = station % 2; vertex < vertices.length; vertex += 2) {
                    canvas.line(previous[vertex], vertices[vertex], thickness);
                }
            }
            previous = vertices;
            station++;
        }
    }

    private static void chainlink(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        int station = 0;
        int thickness = ringThickness(parameters);
        double extrusion = Math.clamp(parameters.options().get("extrusion", 0.20), -0.40, 2.0);
        double stretch = Math.clamp(parameters.options().get("stretch", 1.0), 0.45, 3.0);
        double gap = Math.clamp(parameters.options().get("gap", 0.0), -0.75, 4.0);
        double exponent = Math.clamp(parameters.options().get("majorexponent", 2.0), 0.6, 20.0);
        double quality = Math.clamp(parameters.options().get("quality", 1.0), 0.35, 3.0);
        double majorRadius = parameters.radius() * (1.0 + extrusion) * stretch;
        double minorRadius = parameters.radius() * 0.68;
        double pitch = parameters.radius() * scaledSpacing(parameters, 1.10, 3.20)
                + gap * parameters.radius();
        double roll = Math.toRadians(
                parameters.rollDegrees() + parameters.options().get("angle", 0.0)
        );
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            double orientation = roll + (station & 1) * Math.PI * 0.5;
            Vec3 radialAxis = frame.normal().multiply(Math.cos(orientation))
                    .add(frame.binormal().multiply(Math.sin(orientation)))
                    .normalized();
            superellipseOutline(
                    canvas,
                    frame.position(),
                    frame.tangent(),
                    radialAxis,
                    majorRadius,
                    minorRadius,
                    exponent,
                    thickness,
                    quality
            );
            station++;
        }
    }

    private static void rings(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double radialThickness = parameters.options().has("thickness")
                ? parameters.radius() * Math.clamp(parameters.options().get("thickness", 0.15), 0.03, 0.90)
                : structuralThickness(parameters);
        radialThickness = Math.max(0.8, radialThickness);
        double extrusion = Math.clamp(parameters.options().get("extrusion", 0.20), -0.60, 3.0);
        double depth = Math.max(0.8, radialThickness * (1.0 + extrusion));
        double gap = Math.clamp(parameters.options().get("gap", 0.0), -0.75, 4.0);
        double majorExponent = Math.clamp(parameters.options().get("majorexponent", 2.0), 0.6, 20.0);
        double minorExponent = Math.clamp(parameters.options().get("minorexponent", 2.0), 0.6, 20.0);
        double quality = Math.clamp(parameters.options().get("quality", 1.0), 0.35, 2.0);
        double pitch = parameters.radius() * scaledSpacing(parameters, 0.32, 1.60)
                + gap * Math.max(depth, parameters.radius() * 0.35);
        double roll = Math.toRadians(
                parameters.rollDegrees() + parameters.options().get("angle", 0.0)
        );
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            Vec3 firstAxis = frame.normal().multiply(Math.cos(roll))
                    .add(frame.binormal().multiply(Math.sin(roll)))
                    .normalized();
            Vec3 secondAxis = frame.binormal().multiply(Math.cos(roll))
                    .subtract(frame.normal().multiply(Math.sin(roll)))
                    .normalized();
            superTorus(
                    canvas,
                    frame.position(),
                    firstAxis,
                    secondAxis,
                    frame.tangent(),
                    parameters.radius(),
                    radialThickness,
                    depth,
                    majorExponent,
                    minorExponent,
                    quality
            );
        }
    }

    private static void scales(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double radius = parameters.radius();
        int columns = Math.clamp(parameters.spacing(), 4, 48);
        double angularPitch = Math.PI * 2.0 / columns;
        double tileAngularWidth = angularPitch * 1.04;
        double tileWidth = radius * tileAngularWidth;
        double tileLength = Math.clamp(tileWidth * 1.05, 5.0, radius * 1.45);
        double rowPitch = tileLength * 0.62;
        double inclinedRise = Math.tan(Math.toRadians(SCALE_INCLINATION_DEGREES)) * tileLength;
        double relief = Math.clamp(
                Math.max(parameters.thickness() + 2.5, Math.max(tileWidth * 0.30, inclinedRise)),
                3.5,
                Math.max(4.0, radius * 0.32)
        );
        double baseRadius = Math.max(1.0, radius - relief);
        double roll = Math.toRadians(parameters.rollDegrees());

        tubeSolid(canvas, path, baseRadius);

        int row = 0;
        double maximum = path.closed() ? path.length() - 1.0E-6 : path.length() + tileLength * 0.35;
        for (double centerDistance = 0.0; centerDistance <= maximum; centerDistance += rowPitch) {
            checkInterrupted();
            double rowOffset = (row & 1) == 0 ? 0.0 : angularPitch * 0.5;
            for (int column = 0; column < columns; column++) {
                double centerAngle = roll + rowOffset + column * angularPitch;
                stampScaleTile(
                        canvas,
                        path,
                        centerDistance,
                        centerAngle,
                        baseRadius,
                        tileAngularWidth,
                        tileWidth,
                        tileLength,
                        relief,
                        parameters.thickness()
                );
            }
            row++;
        }

        if (!path.closed()) {
            SampledPath.Frame start = path.at(0.0);
            SampledPath.Frame end = path.at(path.length());
            canvas.filledDisc(
                    start.position(), start.normal(), start.binormal(), baseRadius, parameters.thickness()
            );
            canvas.filledDisc(
                    end.position(), end.normal(), end.binormal(), baseRadius, parameters.thickness()
            );
        }
    }

    private static void spikedScales(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double radius = parameters.radius();
        int columns = surfaceColumns(radius, 6, 30);
        double angularPitch = Math.PI * 2.0 / columns;
        double tileAngularWidth = angularPitch * 0.66;
        double tileWidth = radius * tileAngularWidth;
        double tileLength = Math.clamp(tileWidth * 1.38, 6.0, radius * 1.35);
        double rowPitch = tileLength * scaledSpacing(parameters, 0.58, 1.35);
        double relief = Math.clamp(
                Math.max(parameters.thickness() + 2.0, tileWidth * 0.24),
                3.0,
                Math.max(4.0, radius * 0.26)
        );
        double baseRadius = Math.max(1.0, radius - relief);
        double roll = Math.toRadians(parameters.rollDegrees());

        tubeSolid(canvas, path, baseRadius);
        int row = 0;
        double maximum = path.closed() ? path.length() - 1.0E-6 : path.length() + tileLength * 0.30;
        for (double centerDistance = 0.0; centerDistance <= maximum; centerDistance += rowPitch) {
            checkInterrupted();
            double rowOffset = (row & 1) == 0 ? 0.0 : angularPitch * 0.5;
            for (int column = 0; column < columns; column++) {
                stampDiamondScaleTile(
                        canvas,
                        path,
                        centerDistance,
                        roll + rowOffset + column * angularPitch,
                        baseRadius,
                        tileAngularWidth,
                        tileWidth,
                        tileLength,
                        relief,
                        parameters.thickness()
                );
            }
            row++;
        }
        capTube(canvas, path, baseRadius, parameters.thickness());
    }

    private static void hexArmor(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double radius = parameters.radius();
        int columns = surfaceColumns(radius, 6, 28);
        double angularPitch = Math.PI * 2.0 / columns;
        double panelWidth = radius * angularPitch * 0.74;
        double panelLength = Math.clamp(panelWidth * 0.88, 4.0, radius * 0.92);
        double rowPitch = panelLength * scaledSpacing(parameters, 0.55, 1.42);
        double relief = Math.clamp(parameters.thickness() + radius * 0.08, 2.0, 6.0);
        double baseRadius = Math.max(1.0, radius - relief);
        double roll = Math.toRadians(parameters.rollDegrees());
        tubeShell(canvas, path, baseRadius, Math.max(1, parameters.thickness()));

        int row = 0;
        for (double distance = 0.0; distance <= path.length() + 1.0E-6; distance += rowPitch) {
            checkInterrupted();
            double stagger = (row & 1) == 0 ? 0.0 : angularPitch * 0.5;
            for (int column = 0; column < columns; column++) {
                stampSurfacePanel(
                        canvas,
                        path,
                        Math.min(distance, path.length()),
                        roll + stagger + column * angularPitch,
                        baseRadius,
                        angularPitch * 0.72,
                        panelWidth,
                        panelLength,
                        relief,
                        parameters.thickness(),
                        true
                );
            }
            row++;
        }
        capTube(canvas, path, baseRadius, parameters.thickness());
    }

    private static void voronoiCells(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double radius = parameters.radius();
        double pitch = Math.max(3.0, radius * scaledSpacing(parameters, 0.34, 1.18));
        int columns = Math.clamp((int) Math.round(Math.PI * 2.0 * radius / pitch), 6, 22);
        int thickness = structuralThickness(parameters);
        double roll = Math.toRadians(parameters.rollDegrees());
        double baseRadius = Math.max(1.0, radius - Math.max(2.0, thickness + 1.0));
        tubeShell(canvas, path, baseRadius, 1);

        Vec3[] previous = null;
        int row = 0;
        for (double distance : stations(path, pitch)) {
            checkInterrupted();
            SampledPath.Frame frame = path.at(distance);
            Vec3[] vertices = new Vec3[columns];
            for (int column = 0; column < columns; column++) {
                double jitter = (hash01(row, column, 37) - 0.5) * 0.46;
                double angle = roll + (column + jitter + (row & 1) * 0.48) * Math.PI * 2.0 / columns;
                double radialJitter = (hash01(row, column, 91) - 0.5) * Math.min(2.5, radius * 0.16);
                vertices[column] = radial(frame, angle, radius + radialJitter);
                canvas.brush(vertices[column], thickness + (hash01(row, column, 13) > 0.72 ? 1 : 0));
            }
            for (int column = 0; column < columns; column++) {
                canvas.line(vertices[column], vertices[(column + 1) % columns], thickness);
                if (previous != null) {
                    int shift = (row & 1) == 0 ? 0 : 1;
                    canvas.line(previous[(column + shift) % columns], vertices[column], thickness);
                    if (hash01(row, column, 211) > 0.58) {
                        canvas.line(previous[(column + shift + 1) % columns], vertices[column], thickness);
                    }
                }
            }
            previous = vertices;
            row++;
        }
        capTube(canvas, path, baseRadius, 1);
    }

    private static void knots(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double pitch = parameters.radius() * scaledSpacing(parameters, 0.95, 2.70);
        int thickness = structuralThickness(parameters);
        double roll = Math.toRadians(parameters.rollDegrees());
        int station = 0;
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            Vec3 first = frame.normal().multiply(Math.cos(roll + station * 0.47))
                    .add(frame.binormal().multiply(Math.sin(roll + station * 0.47))).normalized();
            Vec3 second = frame.binormal().multiply(Math.cos(roll + station * 0.47))
                    .subtract(frame.normal().multiply(Math.sin(roll + station * 0.47))).normalized();
            int steps = Math.max(90, (int) Math.ceil(parameters.radius() * Math.PI * 9.0));
            Vec3 previous = null;
            for (int step = 0; step <= steps; step++) {
                double angle = Math.PI * 2.0 * step / steps;
                double majorAngle = angle * 2.0;
                double breathing = parameters.radius() * (0.70 + 0.22 * Math.cos(angle * 3.0));
                Vec3 point = frame.position()
                        .add(first.multiply(Math.cos(majorAngle) * breathing))
                        .add(second.multiply(Math.sin(majorAngle) * breathing))
                        .add(frame.tangent().multiply(Math.sin(angle * 3.0) * parameters.radius() * 0.34));
                if (previous != null) {
                    canvas.line(previous, point, thickness);
                }
                previous = point;
            }
            station++;
        }
    }

    private static void noodles(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        int strands = Math.clamp(4 + parameters.thickness(), 5, 8);
        int strandThickness = Math.clamp(structuralThickness(parameters) + 1, 2, 6);
        double wavelength = parameters.radius() * scaledSpacing(parameters, 1.35, 4.20);
        double roll = Math.toRadians(parameters.rollDegrees());
        Vec3[] previous = new Vec3[strands];
        int sample = 0;
        for (double distance : traceDistances(path)) {
            SampledPath.Frame frame = path.at(distance);
            for (int strand = 0; strand < strands; strand++) {
                double phase = roll + strand * Math.PI * 2.0 / strands;
                double primary = phase + distance * Math.PI * 2.0 / wavelength;
                double secondary = phase * 1.7 - distance * Math.PI * 2.0 / (wavelength * 0.61);
                double radius = parameters.radius() * (0.38 + 0.38 * (0.5 + 0.5 * Math.sin(secondary)));
                Vec3 point = frame.position()
                        .add(frame.normal().multiply(Math.cos(primary) * radius))
                        .add(frame.binormal().multiply(Math.sin(primary) * radius))
                        .add(frame.tangent().multiply(Math.sin(secondary * 0.72) * parameters.radius() * 0.13));
                if (previous[strand] != null) {
                    canvas.line(previous[strand], point, strandThickness);
                }
                if (sample % 31 == 0 && strand > 0 && hash01(sample, strand, 503) > 0.54) {
                    canvas.line(point, previous[strand - 1] == null ? frame.position() : previous[strand - 1],
                            Math.max(1, strandThickness - 1));
                }
                if (sample % 23 == 0 && hash01(sample, strand, 601) > 0.76) {
                    canvas.brush(point, strandThickness + 2);
                }
                previous[strand] = point;
            }
            sample++;
        }
    }

    private static void monumentalPillar(
            VoxelCanvas canvas,
            SampledPath path,
            ShapeParameters parameters
    ) {
        double radius = parameters.radius();
        double roll = Math.toRadians(parameters.rollDegrees());
        int fluteCount = Math.clamp(6 + parameters.spacing() * 2, 10, 22);
        int ribThickness = Math.clamp(structuralThickness(parameters) + 1, 2, 5);
        int samples = Math.max(2, (int) Math.ceil(path.length() * 1.20));
        Vec3[] previousRibs = new Vec3[fluteCount];
        for (int sample = 0; sample <= samples; sample++) {
            checkInterrupted();
            double progress = sample / (double) samples;
            SampledPath.Frame frame = path.at(path.length() * progress);
            double profile = pillarProfile(progress);
            double coreRadius = Math.max(1.0, radius * profile * 0.82);
            canvas.filledDisc(frame.position(), frame.normal(), frame.binormal(), coreRadius, 2);
            for (int flute = 0; flute < fluteCount; flute++) {
                double angle = roll + flute * Math.PI * 2.0 / fluteCount;
                Vec3 radial = radial(frame, angle);
                Vec3 point = frame.position().add(radial.multiply(radius * profile));
                if (previousRibs[flute] != null) {
                    canvas.line(previousRibs[flute], point, ribThickness);
                }
                previousRibs[flute] = point;
            }
        }

        SampledPath.Frame start = path.at(0.0);
        SampledPath.Frame end = path.at(path.length());
        Vec3 startFirst = rotatedNormal(start, roll);
        Vec3 startSecond = start.tangent().cross(startFirst).normalized();
        Vec3 endFirst = rotatedNormal(end, roll);
        Vec3 endSecond = end.tangent().cross(endFirst).normalized();
        canvas.rectangle(start.position(), startFirst, startSecond, radius * 1.42, radius * 1.42,
                Math.clamp(parameters.thickness() + 1, 2, 5));
        canvas.filledDisc(start.position(), start.normal(), start.binormal(), radius * 1.50,
                Math.clamp(parameters.thickness() + 2, 3, 6));
        canvas.rectangle(end.position(), endFirst, endSecond, radius * 1.38, radius * 1.38,
                Math.clamp(parameters.thickness() + 1, 2, 5));
        canvas.filledDisc(end.position(), end.normal(), end.binormal(), radius * 1.48,
                Math.clamp(parameters.thickness() + 2, 3, 6));

        pillarBand(canvas, path, path.length() * 0.045, radius * 1.48, radius * 0.085,
                parameters.thickness() + 1, roll);
        pillarBand(canvas, path, path.length() * 0.125, radius * 1.23, radius * 0.060,
                parameters.thickness(), roll);
        pillarBand(canvas, path, path.length() * 0.215, radius * 1.04, radius * 0.045,
                parameters.thickness(), roll);
        pillarBand(canvas, path, path.length() * 0.785, radius * 1.06, radius * 0.045,
                parameters.thickness(), roll);
        pillarBand(canvas, path, path.length() * 0.875, radius * 1.25, radius * 0.060,
                parameters.thickness(), roll);
        pillarBand(canvas, path, path.length() * 0.955, radius * 1.50, radius * 0.085,
                parameters.thickness() + 1, roll);

        pillarButtresses(canvas, path, parameters, roll, false);
        pillarButtresses(canvas, path, parameters, roll, true);
    }

    private static void organicCloud(
            VoxelCanvas canvas,
            SampledPath path,
            ShapeParameters parameters
    ) {
        double radius = parameters.radius();
        double organicity = spacingProgress(parameters);
        double density = (parameters.thickness() - 1.0) / 3.0;
        double bankGap = Math.clamp(parameters.options().get("gap", 12.0), 3.0, 128.0);
        List<Double> cloudStations = cloudStations(path, radius, organicity, density, bankGap);
        double roll = Math.toRadians(parameters.rollDegrees());

        for (int station = 0; station < cloudStations.size(); station++) {
            checkInterrupted();
            double distance = cloudStations.get(station);
            double progress = path.length() <= 1.0E-6 ? 0.5 : distance / path.length();
            double endEnvelope = path.closed()
                    ? 1.0
                    : 0.76 + 0.24 * Math.sin(Math.PI * Math.clamp(progress, 0.0, 1.0));
            SampledPath.Frame frame = path.at(distance);
            CloudBasis basis = cloudBasis(frame);
            double phase = roll + station * 2.399963229728653;
            double centerSide = (hash01(station, 11, 701) - 0.5) * radius * 0.28;
            double centerLift = radius * (0.08 + hash01(station, 13, 709) * 0.16);
            Vec3 center = frame.position()
                    .add(basis.side().multiply(centerSide))
                    .add(basis.up().multiply(centerLift));

            double longitudinal = radius * endEnvelope * (0.70 + hash01(station, 17, 719) * 0.25);
            double lateral = radius * endEnvelope * (0.72 + hash01(station, 19, 727) * 0.26);
            double vertical = radius * endEnvelope * (0.46 + hash01(station, 23, 733) * 0.18);
            fillCloudLobe(
                    canvas,
                    center,
                    basis.tangent(),
                    basis.side(),
                    basis.up(),
                    longitudinal,
                    lateral,
                    vertical,
                    organicity,
                    density,
                    station * 31 + 1
            );

            int satelliteCount = 3 + (int) Math.round(organicity * 3.0);
            for (int satellite = 0; satellite < satelliteCount; satellite++) {
                double angle = phase
                        + satellite * Math.PI * 2.0 / satelliteCount
                        + (hash01(station, satellite, 739) - 0.5) * 0.72;
                double lobeScale = radius * endEnvelope
                        * (0.30 + hash01(station, satellite, 743) * 0.25);
                double sideOffset = Math.cos(angle) * radius * (0.38 + lobeScale / radius * 0.30);
                double tangentOffset = (hash01(station, satellite, 751) - 0.5) * radius * 0.56;
                double lift = radius * (0.16 + Math.max(0.0, Math.sin(angle)) * 0.34)
                        + (hash01(station, satellite, 757) - 0.5) * radius * 0.18;
                Vec3 satelliteCenter = center
                        .add(basis.side().multiply(sideOffset))
                        .add(basis.tangent().multiply(tangentOffset))
                        .add(basis.up().multiply(lift));
                fillCloudLobe(
                        canvas,
                        satelliteCenter,
                        basis.tangent(),
                        basis.side(),
                        basis.up(),
                        lobeScale * (1.00 + hash01(station, satellite, 761) * 0.38),
                        lobeScale * (0.88 + hash01(station, satellite, 769) * 0.32),
                        lobeScale * (0.78 + hash01(station, satellite, 773) * 0.42),
                        organicity,
                        density,
                        station * 101 + satellite * 13 + 7
                );
            }

            if (station == 0 || station == cloudStations.size() - 1 || station % 2 == 1) {
                double direction = station == 0 ? -1.0 : 1.0;
                double sideDirection = hash01(station, 29, 787) < 0.5 ? -1.0 : 1.0;
                Vec3 wispCenter = center
                        .add(basis.tangent().multiply(direction * radius * 0.42))
                        .add(basis.side().multiply(sideDirection * radius * 0.46))
                        .subtract(basis.up().multiply(radius * 0.06));
                fillCloudLobe(
                        canvas,
                        wispCenter,
                        basis.tangent(),
                        basis.side(),
                        basis.up(),
                        radius * endEnvelope * 0.68,
                        radius * endEnvelope * 0.30,
                        radius * endEnvelope * 0.20,
                        organicity,
                        density,
                        station * 149 + 19
                );
            }
        }
    }

    private static void mandala(
            VoxelCanvas canvas,
            SampledPath path,
            ShapeParameters parameters
    ) {
        SampledPath.Frame anchor = path.at(0.0);
        Vec3 center = anchor.position();
        Vec3 depthAxis = anchor.tangent().normalized();
        double roll = Math.toRadians(parameters.rollDegrees());
        Vec3 firstAxis = rotatedNormal(anchor, roll);
        Vec3 secondAxis = depthAxis.cross(firstAxis).normalized();
        double radius = parameters.radius();
        double complexity = spacingProgress(parameters);
        int symmetry = 10 + parameters.spacing() * 2;
        int thickness = structuralThickness(parameters);
        double relief = Math.max(1.0, radius * (0.035 + parameters.thickness() * 0.018));

        canvas.filledDisc(
                center.add(depthAxis.multiply(relief * 0.20)),
                firstAxis,
                secondAxis,
                radius * 0.115,
                Math.max(2, thickness + 1)
        );
        canvas.brush(
                center.add(depthAxis.multiply(relief * 0.72)),
                Math.max(2, (int) Math.round(radius * 0.105) + parameters.thickness())
        );

        mandalaRing(canvas, center, firstAxis, secondAxis, depthAxis,
                radius * 0.18, symmetry * 3, thickness, relief * 0.58, symmetry, 0.0);
        mandalaRing(canvas, center, firstAxis, secondAxis, depthAxis,
                radius * 0.38, symmetry * 4, thickness, relief * 0.82, symmetry, Math.PI);
        mandalaRing(canvas, center, firstAxis, secondAxis, depthAxis,
                radius * 0.66, symmetry * 5, thickness, relief, symmetry * 2, 0.0);
        mandalaRing(canvas, center, firstAxis, secondAxis, depthAxis,
                radius * 0.90, symmetry * 6, thickness, relief * 0.62, symmetry, Math.PI);
        mandalaRing(canvas, center, firstAxis, secondAxis, depthAxis,
                radius, symmetry * 7, Math.max(1, thickness - 1), relief * 0.32, symmetry * 2, 0.0);

        double sector = Math.PI * 2.0 / symmetry;
        for (int petal = 0; petal < symmetry; petal++) {
            checkInterrupted();
            double angle = petal * sector;
            mandalaPetal(
                    canvas,
                    center,
                    firstAxis,
                    secondAxis,
                    depthAxis,
                    radius * 0.12,
                    radius * interpolate(0.54, 0.64, complexity),
                    angle,
                    sector * interpolate(0.72, 0.88, complexity),
                    relief * 1.12,
                    thickness,
                    true
            );
            mandalaPetal(
                    canvas,
                    center,
                    firstAxis,
                    secondAxis,
                    depthAxis,
                    radius * 0.34,
                    radius * interpolate(0.84, 0.94, complexity),
                    angle + sector * 0.5,
                    sector * interpolate(0.54, 0.70, complexity),
                    relief * 0.76,
                    Math.max(1, thickness - 1),
                    false
            );

            Vec3 inner = mandalaPoint(center, firstAxis, secondAxis, depthAxis,
                    radius * 0.19, angle, relief * 0.74);
            Vec3 middle = mandalaPoint(center, firstAxis, secondAxis, depthAxis,
                    radius * 0.69, angle, relief * 0.30);
            Vec3 crown = mandalaPoint(center, firstAxis, secondAxis, depthAxis,
                    radius * 1.04, angle, relief * 0.18);
            canvas.line(inner, middle, Math.max(1, thickness - 1));
            canvas.line(middle, crown, thickness);

            double crownHalfWidth = sector * 0.22;
            Vec3 crownLeft = mandalaPoint(center, firstAxis, secondAxis, depthAxis,
                    radius * 0.91, angle - crownHalfWidth, relief * 0.42);
            Vec3 crownRight = mandalaPoint(center, firstAxis, secondAxis, depthAxis,
                    radius * 0.91, angle + crownHalfWidth, relief * 0.42);
            canvas.line(crownLeft, crown, thickness);
            canvas.line(crown, crownRight, thickness);
            canvas.line(crownRight, crownLeft, Math.max(1, thickness - 1));
        }
    }

    private static void mandalaRing(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            Vec3 depthAxis,
            double radius,
            int segments,
            int thickness,
            double relief,
            int waves,
            double phase
    ) {
        Vec3 previous = null;
        for (int segment = 0; segment <= segments; segment++) {
            double angle = Math.PI * 2.0 * segment / segments;
            double depth = relief * (0.52 + 0.48 * Math.sin(angle * waves + phase));
            Vec3 point = mandalaPoint(center, firstAxis, secondAxis, depthAxis, radius, angle, depth);
            if (previous != null) {
                canvas.line(previous, point, thickness);
            }
            previous = point;
        }
    }

    private static void mandalaPetal(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            Vec3 depthAxis,
            double innerRadius,
            double outerRadius,
            double angle,
            double angularWidth,
            double relief,
            int thickness,
            boolean filled
    ) {
        int samples = Math.max(12, (int) Math.ceil((outerRadius - innerRadius) * 1.4));
        Vec3 previousLeft = null;
        Vec3 previousRight = null;
        for (int sample = 0; sample <= samples; sample++) {
            double progress = sample / (double) samples;
            double arch = Math.sin(Math.PI * progress);
            double radial = interpolate(innerRadius, outerRadius, progress)
                    + arch * (outerRadius - innerRadius) * 0.055;
            double angleOffset = angularWidth * 0.5 * arch;
            double depth = relief * Math.pow(arch, 1.35);
            Vec3 left = mandalaPoint(center, firstAxis, secondAxis, depthAxis,
                    radial, angle - angleOffset, depth);
            Vec3 right = mandalaPoint(center, firstAxis, secondAxis, depthAxis,
                    radial, angle + angleOffset, depth);
            if (previousLeft != null) {
                canvas.line(previousLeft, left, thickness);
                canvas.line(previousRight, right, thickness);
            }
            if (filled && sample % 2 == 0) {
                canvas.line(left, right, Math.max(1, thickness - 1));
            } else if (!filled && sample % 4 == 0) {
                Vec3 spine = mandalaPoint(center, firstAxis, secondAxis, depthAxis,
                        radial, angle, depth * 1.18);
                canvas.line(left, spine, 1);
                canvas.line(spine, right, 1);
            }
            previousLeft = left;
            previousRight = right;
        }
    }

    private static Vec3 mandalaPoint(
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            Vec3 depthAxis,
            double radius,
            double angle,
            double depth
    ) {
        return center
                .add(firstAxis.multiply(Math.cos(angle) * radius))
                .add(secondAxis.multiply(Math.sin(angle) * radius))
                .add(depthAxis.multiply(depth));
    }

    private static List<Double> cloudStations(
            SampledPath path,
            double radius,
            double organicity,
            double density,
            double bankGap
    ) {
        List<Double> distances = new ArrayList<>();
        double maximum = path.closed() ? path.length() - 1.0E-6 : path.length() + 1.0E-6;
        double distance = 0.0;
        int bank = 0;
        while (distance <= maximum) {
            int minimumBankSize = 4 + (int) Math.round(organicity * 2.0);
            int bankVariation = 2 + (int) Math.round(organicity * 2.0);
            int bankSize = minimumBankSize
                    + (int) Math.floor(hash01(bank, 41, 809) * (bankVariation + 1));
            for (int member = 0; member < bankSize && distance <= maximum; member++) {
                distances.add(Math.min(distance, path.length()));
                if (member + 1 < bankSize) {
                    double overlapStep = radius
                            * interpolate(0.84, 0.54, organicity)
                            * interpolate(1.04, 0.88, density)
                            * interpolate(0.84, 1.14, hash01(bank, member, 811));
                    distance += Math.max(3.0, overlapStep);
                }
            }
            double naturalGap = bankGap * interpolate(0.82, 1.22, hash01(bank, 43, 821));
            double clearBody = radius * interpolate(2.05, 2.35, hash01(bank, 47, 823));
            distance += clearBody + naturalGap;
            bank++;
        }
        return distances;
    }

    private static CloudBasis cloudBasis(SampledPath.Frame frame) {
        Vec3 tangent = frame.tangent().normalized();
        Vec3 worldUp = new Vec3(0.0, 1.0, 0.0);
        Vec3 side = tangent.cross(worldUp).normalized();
        if (side.length() < 0.25) {
            side = frame.binormal().normalized();
        }
        Vec3 up = side.cross(tangent).normalized();
        if (up.dot(worldUp) < 0.0) {
            up = up.multiply(-1.0);
            side = side.multiply(-1.0);
        }
        return new CloudBasis(tangent, side, up);
    }

    private static void fillCloudLobe(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 longitudinalAxis,
            Vec3 lateralAxis,
            Vec3 verticalAxis,
            double longitudinalRadius,
            double lateralRadius,
            double verticalRadius,
            double organicity,
            double density,
            int salt
    ) {
        longitudinalRadius = Math.max(1.5, longitudinalRadius);
        lateralRadius = Math.max(1.5, lateralRadius);
        verticalRadius = Math.max(1.25, verticalRadius);
        double roughness = interpolate(0.08, 0.24, organicity) * interpolate(1.08, 0.72, density);
        double densityExpansion = interpolate(-0.035, 0.055, density);
        double maximumThreshold = 1.0 + densityExpansion + roughness * 0.52;
        double extentScale = Math.sqrt(maximumThreshold) + 0.02;
        double xExtent = (Math.abs(longitudinalAxis.x()) * longitudinalRadius
                + Math.abs(lateralAxis.x()) * lateralRadius
                + Math.abs(verticalAxis.x()) * verticalRadius) * extentScale;
        double yExtent = (Math.abs(longitudinalAxis.y()) * longitudinalRadius
                + Math.abs(lateralAxis.y()) * lateralRadius
                + Math.abs(verticalAxis.y()) * verticalRadius) * extentScale;
        double zExtent = (Math.abs(longitudinalAxis.z()) * longitudinalRadius
                + Math.abs(lateralAxis.z()) * lateralRadius
                + Math.abs(verticalAxis.z()) * verticalRadius) * extentScale;
        int minimumX = (int) Math.floor(center.x() - xExtent - 0.75);
        int maximumX = (int) Math.ceil(center.x() + xExtent + 0.75);
        int minimumY = (int) Math.floor(center.y() - yExtent - 0.75);
        int maximumY = (int) Math.ceil(center.y() + yExtent + 0.75);
        int minimumZ = (int) Math.floor(center.z() - zExtent - 0.75);
        int maximumZ = (int) Math.ceil(center.z() + zExtent + 0.75);
        for (int x = minimumX; x <= maximumX; x++) {
            if ((x & 7) == 0) {
                checkInterrupted();
            }
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    Vec3 offset = new Vec3(
                            x + 0.5 - center.x(),
                            y + 0.5 - center.y(),
                            z + 0.5 - center.z()
                    );
                    double longitudinal = offset.dot(longitudinalAxis) / longitudinalRadius;
                    double lateral = offset.dot(lateralAxis) / lateralRadius;
                    double vertical = offset.dot(verticalAxis) / verticalRadius;
                    double distance = longitudinal * longitudinal + lateral * lateral + vertical * vertical;
                    if (distance > maximumThreshold) {
                        continue;
                    }
                    double surfaceNoise = cloudSurfaceNoise(x, y, z, salt);
                    double threshold = 1.0 + densityExpansion + (surfaceNoise - 0.5) * roughness;
                    if (distance <= threshold) {
                        canvas.add(new Vec3(x + 0.5, y + 0.5, z + 0.5));
                    }
                }
            }
        }
    }

    private static double cloudSurfaceNoise(int x, int y, int z, int salt) {
        double broad = hash01(
                Math.floorDiv(x, 7),
                Math.floorDiv(y, 7),
                Math.floorDiv(z, 7),
                salt
        );
        double detail = hash01(
                Math.floorDiv(x, 3),
                Math.floorDiv(y, 3),
                Math.floorDiv(z, 3),
                salt + 97
        );
        return broad * 0.68 + detail * 0.32;
    }

    private static double pillarProfile(double progress) {
        if (progress < 0.16) {
            return interpolate(1.38, 1.02, smoothStep(progress / 0.16));
        }
        if (progress > 0.80) {
            return interpolate(1.00, 1.42, smoothStep((progress - 0.80) / 0.20));
        }
        double shaft = (progress - 0.16) / 0.64;
        return 0.97 + Math.sin(shaft * Math.PI) * 0.045;
    }

    private static void pillarBand(
            VoxelCanvas canvas,
            SampledPath path,
            double distance,
            double radius,
            double halfWidth,
            int thickness,
            double roll
    ) {
        int layers = Math.max(1, (int) Math.ceil(halfWidth * 2.0));
        for (int layer = -layers; layer <= layers; layer++) {
            double offset = halfWidth * layer / layers;
            SampledPath.Frame frame = path.at(Math.clamp(distance + offset, 0.0, path.length()));
            pillarRing(canvas, frame, radius, Math.clamp(thickness, 1, 5), roll);
        }
    }

    private static void pillarRing(
            VoxelCanvas canvas,
            SampledPath.Frame frame,
            double radius,
            int thickness,
            double roll
    ) {
        int steps = Math.max(24, (int) Math.ceil(Math.PI * 2.0 * radius * 1.10));
        Vec3 previous = null;
        Vec3 first = null;
        for (int step = 0; step < steps; step++) {
            double angle = roll + step * Math.PI * 2.0 / steps;
            Vec3 point = frame.position().add(radial(frame, angle).multiply(radius));
            if (first == null) {
                first = point;
            }
            if (previous != null) {
                canvas.line(previous, point, thickness);
            }
            previous = point;
        }
        if (previous != null && first != null) {
            canvas.line(previous, first, thickness);
        }
    }

    private static void pillarButtresses(
            VoxelCanvas canvas,
            SampledPath path,
            ShapeParameters parameters,
            double roll,
            boolean capital
    ) {
        double radius = parameters.radius();
        int count = Math.clamp(parameters.spacing(), 4, 8);
        double reach = Math.min(path.length() * 0.22, radius * 1.75);
        SampledPath.Frame endpoint = path.at(capital ? path.length() : 0.0);
        SampledPath.Frame inner = path.at(capital
                ? Math.max(0.0, path.length() - reach)
                : Math.min(path.length(), reach));
        int thickness = Math.clamp(parameters.thickness() + 1, 2, 5);
        for (int buttress = 0; buttress < count; buttress++) {
            double angle = roll + buttress * Math.PI * 2.0 / count;
            Vec3 endpointRadial = radial(endpoint, angle);
            Vec3 innerRadial = radial(inner, angle);
            Vec3 outer = endpoint.position().add(endpointRadial.multiply(radius * 1.58));
            Vec3 shoulder = inner.position().add(innerRadial.multiply(radius * 0.72));
            Vec3 foot = endpoint.position().add(endpointRadial.multiply(radius * 0.92));
            canvas.line(outer, shoulder, thickness);
            canvas.line(foot, outer, thickness);
            canvas.brush(outer, Math.clamp(thickness + 1, 2, 6));
        }
    }

    private static Vec3 rotatedNormal(SampledPath.Frame frame, double roll) {
        return frame.normal().multiply(Math.cos(roll))
                .add(frame.binormal().multiply(Math.sin(roll)))
                .normalized();
    }

    private static Vec3 radial(SampledPath.Frame frame, double angle) {
        return frame.normal().multiply(Math.cos(angle))
                .add(frame.binormal().multiply(Math.sin(angle)))
                .normalized();
    }

    private static void pillarArches(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        Vec3 firstBase = path.at(0.0).position();
        Vec3 secondBase = path.at(path.length()).position();
        Vec3 horizontal = new Vec3(secondBase.x() - firstBase.x(), 0.0, secondBase.z() - firstBase.z());
        double horizontalSpan = horizontal.length();
        if (horizontalSpan < Math.max(8.0, parameters.radius() * 3.25)) {
            throw new IllegalArgumentException("Separa P1 y P2 al menos 3.25 veces el ancho de los pilares");
        }
        Vec3 forward = horizontal.normalized();
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 lateral = new Vec3(-forward.z(), 0.0, forward.x()).normalized();
        int height = Math.clamp((int) Math.round(parameters.options().get("height", 48.0)), 8, 128);

        monumentalPillar(canvas, verticalPath(firstBase, height), parameters);
        monumentalPillar(canvas, verticalPath(secondBase, height), parameters);

        double radius = parameters.radius();
        Vec3 firstSpring = firstBase.add(up.multiply(height * 0.56)).add(forward.multiply(radius * 0.82));
        Vec3 secondSpring = secondBase.add(up.multiply(height * 0.56)).subtract(forward.multiply(radius * 0.82));
        double innerSpan = firstSpring.distance(secondSpring);
        double rise = Math.min(height * 0.38, Math.max(radius * 0.90, innerSpan * 0.30));
        int steps = Math.max(24, (int) Math.ceil(innerSpan * 1.45));
        int archThickness = Math.clamp(parameters.thickness() + 1, 2, 5);
        int voussoirStride = Math.max(3, steps / Math.max(8, parameters.spacing() * 3));
        Vec3 previousFront = null;
        Vec3 previousBack = null;
        for (int step = 0; step <= steps; step++) {
            checkInterrupted();
            double amount = step / (double) steps;
            Vec3 center = archPoint(firstSpring, secondSpring, up, rise, amount);
            canvas.rectangle(center, lateral, up, radius * 0.58, archThickness * 0.72, 1);
            Vec3 front = center.add(lateral.multiply(radius * 0.68));
            Vec3 back = center.subtract(lateral.multiply(radius * 0.68));
            if (previousFront != null) {
                canvas.line(previousFront, front, archThickness);
                canvas.line(previousBack, back, archThickness);
            }
            if (step % voussoirStride == 0) {
                canvas.rectangle(center, lateral, up, radius * 0.70, archThickness * 1.25,
                        Math.max(1, parameters.thickness()));
            }
            previousFront = front;
            previousBack = back;
        }

        Vec3 keystone = archPoint(firstSpring, secondSpring, up, rise, 0.5);
        canvas.rectangle(keystone, lateral, up, radius * 0.78, radius * 0.62,
                Math.clamp(parameters.thickness() + 1, 2, 5));

        Vec3 firstBraceStart = firstBase.add(up.multiply(height * 0.34)).add(forward.multiply(radius * 0.68));
        Vec3 secondBraceStart = secondBase.add(up.multiply(height * 0.34)).subtract(forward.multiply(radius * 0.68));
        canvas.line(firstBraceStart, archPoint(firstSpring, secondSpring, up, rise, 0.22), archThickness);
        canvas.line(secondBraceStart, archPoint(firstSpring, secondSpring, up, rise, 0.78), archThickness);

        Vec3 firstTop = firstBase.add(up.multiply(height));
        Vec3 secondTop = secondBase.add(up.multiply(height));
        canvas.line(firstTop.add(lateral.multiply(radius * 0.76)),
                secondTop.add(lateral.multiply(radius * 0.76)), archThickness);
        canvas.line(firstTop.subtract(lateral.multiply(radius * 0.76)),
                secondTop.subtract(lateral.multiply(radius * 0.76)), archThickness);
    }

    private static void torii(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        Vec3 firstBase = path.at(0.0).position();
        Vec3 secondBase = path.at(path.length()).position();
        Vec3 horizontal = new Vec3(secondBase.x() - firstBase.x(), 0.0, secondBase.z() - firstBase.z());
        double span = horizontal.length();
        double radius = parameters.radius();
        if (span < Math.max(12.0, radius * 4.5)) {
            throw new IllegalArgumentException("Separa P1 y P2 al menos 4.5 veces el grosor de los pilares");
        }

        Vec3 forward = horizontal.normalized();
        Vec3 lateral = new Vec3(-forward.z(), 0.0, forward.x()).normalized();
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        int height = Math.clamp((int) Math.round(parameters.options().get("height", 96.0)), 16, 256);
        double inset = Math.min(radius * 0.58, span * 0.045);
        Vec3 firstTop = new Vec3(firstBase.x(), firstBase.y() + height, firstBase.z())
                .add(forward.multiply(inset));
        Vec3 secondTop = new Vec3(secondBase.x(), secondBase.y() + height, secondBase.z())
                .subtract(forward.multiply(inset));
        int detail = parameters.spacing();
        int beamThickness = Math.clamp(parameters.thickness() + (int) Math.ceil(radius / 10.0), 2, 7);
        double curveFactor = Math.clamp(1.0 + parameters.rollDegrees() / 180.0, 0.25, 2.0);

        toriiPillar(canvas, firstBase, firstTop, forward, lateral, radius, parameters);
        toriiPillar(canvas, secondBase, secondTop, forward, lateral, radius, parameters);

        Vec3 gateCenter = new Vec3(
                (firstBase.x() + secondBase.x()) * 0.5,
                0.0,
                (firstBase.z() + secondBase.z()) * 0.5
        );
        double halfSpan = span * 0.5;
        double firstLowerY = firstBase.y() + height * 0.66;
        double secondLowerY = secondBase.y() + height * 0.66;
        double firstShoulderY = firstBase.y() + height * 0.82;
        double secondShoulderY = secondBase.y() + height * 0.82;
        double firstCrownY = firstBase.y() + height * 0.925;
        double secondCrownY = secondBase.y() + height * 0.925;

        toriiBeam(
                canvas,
                gateCenter,
                forward,
                lateral,
                up,
                firstLowerY,
                secondLowerY,
                halfSpan,
                halfSpan + radius * 0.85,
                radius * 0.58,
                Math.max(radius * 0.20, beamThickness * 0.65),
                height * 0.006 * curveFactor,
                beamThickness
        );
        toriiBeam(
                canvas,
                gateCenter,
                forward,
                lateral,
                up,
                firstShoulderY,
                secondShoulderY,
                halfSpan,
                halfSpan + radius * 1.55,
                radius * 0.76,
                Math.max(radius * 0.27, beamThickness * 0.80),
                height * 0.020 * curveFactor,
                beamThickness
        );

        int crownLayers = 2 + detail / 2;
        for (int layer = 0; layer < crownLayers; layer++) {
            double layerProgress = crownLayers <= 1 ? 0.0 : layer / (double) (crownLayers - 1);
            toriiBeam(
                    canvas,
                    gateCenter,
                    forward,
                    lateral,
                    up,
                    firstCrownY + layer * Math.max(1.0, radius * 0.18),
                    secondCrownY + layer * Math.max(1.0, radius * 0.18),
                    halfSpan,
                    halfSpan + radius * interpolate(2.05, 3.10, layerProgress),
                    radius * interpolate(0.88, 1.10, layerProgress),
                    Math.max(radius * interpolate(0.25, 0.38, layerProgress), beamThickness),
                    height * interpolate(0.030, 0.065, layerProgress) * curveFactor,
                    beamThickness
            );
        }

        Vec3 firstShoulder = firstBase.lerp(firstTop, 0.74);
        Vec3 secondShoulder = secondBase.lerp(secondTop, 0.74);
        Vec3 firstBracket = new Vec3(gateCenter.x(), firstShoulderY, gateCenter.z())
                .subtract(forward.multiply(halfSpan + radius * 1.18));
        Vec3 secondBracket = new Vec3(gateCenter.x(), secondShoulderY, gateCenter.z())
                .add(forward.multiply(halfSpan + radius * 1.18));
        canvas.line(firstShoulder, firstBracket, beamThickness);
        canvas.line(secondShoulder, secondBracket, beamThickness);

        double bracketReach = Math.min(height * 0.12, radius * (2.4 + detail * 0.18));
        toriiBracketFan(canvas, firstTop, forward.multiply(-1.0), lateral, up, bracketReach, beamThickness, detail);
        toriiBracketFan(canvas, secondTop, forward, lateral, up, bracketReach, beamThickness, detail);

        double plaqueHalfWidth = Math.min(span * 0.115, radius * 1.75);
        double plaqueHalfHeight = Math.min(height * 0.075, radius * 2.40);
        double middleLowerY = toriiTerrainHeight(firstLowerY, secondLowerY, 0.5);
        double middleShoulderY = toriiTerrainHeight(firstShoulderY, secondShoulderY, 0.5);
        Vec3 plaqueCenter = new Vec3(
                gateCenter.x(),
                (middleLowerY + middleShoulderY) * 0.5,
                gateCenter.z()
        );
        canvas.rectangle(plaqueCenter, forward, up, plaqueHalfWidth, plaqueHalfHeight,
                Math.clamp(beamThickness, 2, 6));
        toriiPlaqueFrame(canvas, plaqueCenter, forward, lateral, up,
                plaqueHalfWidth, plaqueHalfHeight, Math.max(1, beamThickness - 1));
    }

    private static void toriiPillar(
            VoxelCanvas canvas,
            Vec3 base,
            Vec3 top,
            Vec3 forward,
            Vec3 lateral,
            double radius,
            ShapeParameters parameters
    ) {
        double pillarHeight = base.distance(top);
        int samples = Math.max(2, (int) Math.ceil(pillarHeight * 1.25));
        for (int sample = 0; sample <= samples; sample++) {
            if ((sample & 31) == 0) {
                checkInterrupted();
            }
            double progress = sample / (double) samples;
            Vec3 center = base.lerp(top, progress);
            double profile = interpolate(1.10, 0.82, smoothStep(progress));
            profile += Math.sin(progress * Math.PI) * 0.035;
            canvas.filledDisc(center, forward, lateral, radius * profile, 2);
        }

        int baseDepth = Math.clamp(parameters.thickness() + 2, 3, 7);
        canvas.rectangle(base, forward, lateral, radius * 1.58, radius * 1.58, baseDepth);
        canvas.rectangle(base.add(new Vec3(0.0, radius * 0.34, 0.0)),
                forward, lateral, radius * 1.34, radius * 1.34, baseDepth);
        toriiCollar(canvas, base.lerp(top, 0.10), forward, lateral, radius * 1.20, parameters.thickness() + 1);
        toriiCollar(canvas, base.lerp(top, 0.70), forward, lateral, radius * 1.04, parameters.thickness());
        toriiCollar(canvas, base.lerp(top, 0.86), forward, lateral, radius * 1.16, parameters.thickness() + 1);
        toriiCollar(canvas, base.lerp(top, 0.96), forward, lateral, radius * 1.38, parameters.thickness() + 2);
    }

    private static void toriiCollar(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 forward,
            Vec3 lateral,
            double radius,
            int layers
    ) {
        int resolvedLayers = Math.clamp(layers, 1, 7);
        for (int layer = -resolvedLayers; layer <= resolvedLayers; layer++) {
            canvas.filledDisc(
                    center.add(new Vec3(0.0, layer * 0.55, 0.0)),
                    forward,
                    lateral,
                    radius,
                    1
            );
        }
    }

    private static void toriiBeam(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 forward,
            Vec3 lateral,
            Vec3 up,
            double firstSupportY,
            double secondSupportY,
            double supportHalfSpan,
            double halfLength,
            double halfDepth,
            double halfHeight,
            double curveRise,
            int thickness
    ) {
        int samples = Math.max(4, (int) Math.ceil(halfLength * 2.2));
        for (int sample = -samples; sample <= samples; sample++) {
            if ((sample & 63) == 0) {
                checkInterrupted();
            }
            double normalized = sample / (double) samples;
            double longitudinal = normalized * halfLength;
            double supportProgress = Math.clamp(
                    (longitudinal + supportHalfSpan) / (supportHalfSpan * 2.0),
                    0.0,
                    1.0
            );
            double terrainHeight = toriiTerrainHeight(firstSupportY, secondSupportY, supportProgress);
            double edge = Math.max(0.0, (Math.abs(normalized) - 0.54) / 0.46);
            double curve = Math.pow(edge, 1.85) * curveRise;
            double flare = 1.0 + Math.pow(edge, 1.45) * 0.32;
            Vec3 point = new Vec3(center.x(), terrainHeight, center.z())
                    .add(forward.multiply(longitudinal))
                    .add(up.multiply(curve));
            canvas.rectangle(
                    point,
                    lateral,
                    up,
                    halfDepth * flare,
                    halfHeight * (1.0 + edge * 0.12),
                    Math.max(1, thickness / 2)
            );
        }
    }

    private static double toriiTerrainHeight(double firstHeight, double secondHeight, double progress) {
        double resolvedProgress = Math.clamp(progress, 0.0, 1.0);
        return interpolate(firstHeight, secondHeight, resolvedProgress);
    }

    private static void toriiBracketFan(
            VoxelCanvas canvas,
            Vec3 pillarTop,
            Vec3 outward,
            Vec3 lateral,
            Vec3 up,
            double reach,
            int thickness,
            int detail
    ) {
        int arms = Math.clamp(2 + detail / 2, 3, 6);
        for (int arm = 0; arm < arms; arm++) {
            double amount = arms <= 1 ? 0.0 : arm / (double) (arms - 1);
            Vec3 end = pillarTop
                    .add(outward.multiply(reach * interpolate(0.52, 1.0, amount)))
                    .add(lateral.multiply((amount - 0.5) * reach * 0.78))
                    .add(up.multiply(reach * interpolate(0.04, 0.24, amount)));
            canvas.line(pillarTop.subtract(up.multiply(reach * 0.22)), end, Math.max(1, thickness - 1));
        }
    }

    private static void toriiPlaqueFrame(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 horizontal,
            Vec3 depth,
            Vec3 vertical,
            double halfWidth,
            double halfHeight,
            int thickness
    ) {
        Vec3 topLeft = center.subtract(horizontal.multiply(halfWidth)).add(vertical.multiply(halfHeight));
        Vec3 topRight = center.add(horizontal.multiply(halfWidth)).add(vertical.multiply(halfHeight));
        Vec3 bottomLeft = center.subtract(horizontal.multiply(halfWidth)).subtract(vertical.multiply(halfHeight));
        Vec3 bottomRight = center.add(horizontal.multiply(halfWidth)).subtract(vertical.multiply(halfHeight));
        for (double offset : new double[]{-0.62, 0.62}) {
            Vec3 shift = depth.multiply(offset);
            canvas.line(topLeft.add(shift), topRight.add(shift), thickness);
            canvas.line(topRight.add(shift), bottomRight.add(shift), thickness);
            canvas.line(bottomRight.add(shift), bottomLeft.add(shift), thickness);
            canvas.line(bottomLeft.add(shift), topLeft.add(shift), thickness);
        }
    }

    private static Vec3 archPoint(Vec3 first, Vec3 second, Vec3 up, double rise, double amount) {
        double arch = Math.sqrt(Math.max(0.0, 1.0 - Math.pow(amount * 2.0 - 1.0, 2.0))) * rise;
        return first.lerp(second, amount).add(up.multiply(arch));
    }

    private static SampledPath verticalPath(Vec3 base, int height) {
        BlockVector3 bottom = blockPosition(base);
        BlockVector3 top = blockPosition(base.add(new Vec3(0.0, height, 0.0)));
        return SampledPath.create(List.of(bottom, top), false);
    }

    private static BlockVector3 blockPosition(Vec3 point) {
        return BlockVector3.at(
                (int) Math.floor(point.x()),
                (int) Math.floor(point.y()),
                (int) Math.floor(point.z())
        );
    }

    private static void archBridge(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double width = Math.max(3.0, parameters.radius());
        double halfWidth = width * 0.5;
        double height = Math.max(3.0, parameters.options().get("height", 18.0));
        int deckThickness = Math.clamp(parameters.thickness() + 1, 2, 5);
        int samples = Math.max(2, (int) Math.ceil(path.length() * 1.35));
        Vec3 previousCenter = null;
        Vec3 previousLeft = null;
        Vec3 previousRight = null;
        for (int sample = 0; sample <= samples; sample++) {
            checkInterrupted();
            double distance = path.length() * sample / samples;
            double progress = path.length() <= 1.0E-6 ? 0.0 : distance / path.length();
            SampledPath.Frame frame = path.at(distance);
            double arch = height * 4.0 * progress * (1.0 - progress);
            Vec3 tangent = new Vec3(frame.tangent().x(), 0.0, frame.tangent().z()).normalized();
            if (tangent.length() < 0.5) {
                tangent = frame.tangent();
            }
            Vec3 side = new Vec3(-tangent.z(), 0.0, tangent.x()).normalized();
            Vec3 center = frame.position().add(new Vec3(0.0, arch, 0.0));
            canvas.rectangle(center, side, new Vec3(0.0, 1.0, 0.0), halfWidth, deckThickness * 0.5, 1);
            Vec3 left = center.add(side.multiply(halfWidth));
            Vec3 right = center.subtract(side.multiply(halfWidth));
            if (previousCenter != null) {
                canvas.line(previousCenter, center, deckThickness);
                canvas.line(previousLeft, left, Math.max(2, deckThickness));
                canvas.line(previousRight, right, Math.max(2, deckThickness));
            }
            for (int railHeight = 1; railHeight <= 3; railHeight++) {
                canvas.add(left.add(new Vec3(0.0, railHeight, 0.0)));
                canvas.add(right.add(new Vec3(0.0, railHeight, 0.0)));
            }
            if (sample % Math.max(3, (int) Math.round(width * 0.55)) == 0) {
                canvas.line(left.add(new Vec3(0.0, 3.0, 0.0)), right.add(new Vec3(0.0, 3.0, 0.0)), 1);
                double ribDepth = Math.max(2.0, height * 0.22);
                canvas.line(left, left.add(new Vec3(0.0, -ribDepth, 0.0)), Math.max(2, deckThickness - 1));
                canvas.line(right, right.add(new Vec3(0.0, -ribDepth, 0.0)), Math.max(2, deckThickness - 1));
            }
            previousCenter = center;
            previousLeft = left;
            previousRight = right;
        }
    }

    private static void tiles(VoxelCanvas canvas, SampledPath path, ShapeParameters parameters) {
        double radius = parameters.radius();
        int columns = surfaceColumns(radius, 6, 32);
        double angularPitch = Math.PI * 2.0 / columns;
        double tileAngularWidth = angularPitch * 0.92;
        double tileWidth = radius * tileAngularWidth;
        double tileLength = Math.clamp(tileWidth * 0.96, 5.0, radius * 1.40);
        double rowPitch = tileLength * scaledSpacing(parameters, 0.58, 1.35);
        double inclinedRise = Math.tan(Math.toRadians(13.0)) * tileLength;
        double relief = Math.clamp(
                Math.max(parameters.thickness() + 1.5, inclinedRise),
                2.5,
                Math.max(3.0, radius * 0.24)
        );
        double baseRadius = Math.max(1.0, radius - relief);
        double roll = Math.toRadians(parameters.rollDegrees());

        tubeShell(canvas, path, baseRadius, Math.max(1, parameters.thickness()));

        int row = 0;
        double maximum = path.closed() ? path.length() - 1.0E-6 : path.length() + tileLength * 0.35;
        for (double centerDistance = 0.0; centerDistance <= maximum; centerDistance += rowPitch) {
            checkInterrupted();
            double rowOffset = (row & 1) == 0 ? 0.0 : angularPitch * 0.5;
            for (int column = 0; column < columns; column++) {
                stampSquareTile(
                        canvas,
                        path,
                        centerDistance,
                        roll + rowOffset + column * angularPitch,
                        baseRadius,
                        tileAngularWidth,
                        tileWidth,
                        tileLength,
                        relief,
                        parameters.thickness()
                );
            }
            row++;
        }

        if (!path.closed()) {
            SampledPath.Frame start = path.at(0.0);
            SampledPath.Frame end = path.at(path.length());
            canvas.filledDisc(
                    start.position(), start.normal(), start.binormal(), baseRadius, parameters.thickness()
            );
            canvas.filledDisc(
                    end.position(), end.normal(), end.binormal(), baseRadius, parameters.thickness()
            );
        }
    }

    private static void tubeShell(
            VoxelCanvas canvas,
            SampledPath path,
            double radius,
            int thickness
    ) {
        int longitudinalSteps = Math.max(1, (int) Math.ceil(path.length() * 1.15));
        int angularSteps = Math.max(24, (int) Math.ceil(Math.PI * 2.0 * radius * 1.15));
        for (int longitudinalStep = 0; longitudinalStep <= longitudinalSteps; longitudinalStep++) {
            checkInterrupted();
            double distance = path.length() * longitudinalStep / longitudinalSteps;
            SampledPath.Frame frame = path.at(distance);
            for (int angularStep = 0; angularStep < angularSteps; angularStep++) {
                double angle = Math.PI * 2.0 * angularStep / angularSteps;
                Vec3 radial = frame.normal().multiply(Math.cos(angle))
                        .add(frame.binormal().multiply(Math.sin(angle)))
                        .normalized();
                for (int depth = 0; depth < thickness; depth++) {
                    canvas.add(frame.position().add(radial.multiply(Math.max(0.5, radius - depth))));
                }
            }
        }
    }

    private static void tubeSolid(VoxelCanvas canvas, SampledPath path, double radius) {
        int longitudinalSteps = Math.max(1, (int) Math.ceil(path.length() * 1.35));
        for (int longitudinalStep = 0; longitudinalStep <= longitudinalSteps; longitudinalStep++) {
            checkInterrupted();
            double distance = path.length() * longitudinalStep / longitudinalSteps;
            SampledPath.Frame frame = path.at(distance);
            canvas.filledDisc(frame.position(), frame.normal(), frame.binormal(), radius, 3);
        }
    }

    private static void stampScaleTile(
            VoxelCanvas canvas,
            SampledPath path,
            double centerDistance,
            double centerAngle,
            double baseRadius,
            double angularWidth,
            double physicalWidth,
            double tileLength,
            double relief,
            int thickness
    ) {
        int lengthSteps = Math.max(8, (int) Math.ceil(tileLength * 0.85));
        int widthSteps = Math.max(8, (int) Math.ceil(physicalWidth * 0.55));
        for (int lengthStep = -lengthSteps; lengthStep <= lengthSteps; lengthStep++) {
            double longitudinal = lengthStep / (double) lengthSteps;
            double pathDistance = centerDistance + longitudinal * tileLength * 0.5;
            if (!path.closed() && (pathDistance < 0.0 || pathDistance > path.length())) {
                continue;
            }
            SampledPath.Frame frame = path.at(pathDistance);
            double halfWidth = scaleHalfWidth(longitudinal);
            for (int widthStep = -widthSteps; widthStep <= widthSteps; widthStep++) {
                double width = widthStep / (double) widthSteps;
                if (Math.abs(width) > halfWidth) {
                    continue;
                }
                double edge = halfWidth < 1.0E-6 ? 1.0 : Math.abs(width) / halfWidth;
                double incline = scaleIncline(longitudinal);
                double lateralCrown = 0.84 + 0.16 * Math.sqrt(Math.max(0.0, 1.0 - edge * edge));
                double surfaceRelief = relief * incline * lateralCrown;
                double slant = Math.toRadians(3.0) * longitudinal;
                double angle = centerAngle + width * angularWidth * 0.5 + slant;
                Vec3 radial = frame.normal().multiply(Math.cos(angle))
                        .add(frame.binormal().multiply(Math.sin(angle)))
                        .normalized();
                boolean sideWall = edge >= 0.90;
                boolean raisedLip = longitudinal >= 0.80;
                double slabDepth = Math.max(1.25, thickness + 0.35);
                double fillDepth = Math.max(slabDepth, surfaceRelief + 1.25);
                if (sideWall || raisedLip) {
                    fillDepth += 0.65;
                }
                int depthSteps = Math.max(1, (int) Math.ceil(fillDepth));
                for (int depthStep = 0; depthStep <= depthSteps; depthStep++) {
                    double depth = fillDepth * depthStep / depthSteps;
                    double radialDistance = Math.max(0.5, baseRadius + surfaceRelief - depth);
                    canvas.add(frame.position().add(radial.multiply(radialDistance)));
                }
            }
        }
    }

    private static void stampDiamondScaleTile(
            VoxelCanvas canvas,
            SampledPath path,
            double centerDistance,
            double centerAngle,
            double baseRadius,
            double angularWidth,
            double physicalWidth,
            double tileLength,
            double relief,
            int thickness
    ) {
        int lengthSteps = Math.max(8, (int) Math.ceil(tileLength * 0.90));
        int widthSteps = Math.max(8, (int) Math.ceil(physicalWidth * 0.70));
        for (int lengthStep = -lengthSteps; lengthStep <= lengthSteps; lengthStep++) {
            double longitudinal = lengthStep / (double) lengthSteps;
            double pathDistance = centerDistance + longitudinal * tileLength * 0.5;
            if (!path.closed() && (pathDistance < 0.0 || pathDistance > path.length())) {
                continue;
            }
            SampledPath.Frame frame = path.at(pathDistance);
            double halfWidth = Math.max(0.0, 1.0 - Math.abs(longitudinal));
            for (int widthStep = -widthSteps; widthStep <= widthSteps; widthStep++) {
                double width = widthStep / (double) widthSteps;
                if (Math.abs(width) > halfWidth) {
                    continue;
                }
                double edge = halfWidth < 1.0E-6 ? 1.0 : Math.abs(width) / halfWidth;
                double centerCrown = Math.pow(Math.max(0.0, 1.0 - Math.abs(longitudinal)), 0.58)
                        * Math.pow(Math.max(0.0, 1.0 - edge), 0.34);
                double lip = Math.max(0.0, longitudinal) * 0.32;
                double ridge = Math.pow(Math.max(0.0, 1.0 - edge), 3.4)
                        * Math.pow(Math.max(0.0, 1.0 - Math.abs(longitudinal)), 0.55);
                double tip = Math.pow(Math.max(0.0, longitudinal), 2.4)
                        * Math.pow(Math.max(0.0, 1.0 - edge), 5.0);
                double surfaceRelief = relief * (0.30 + centerCrown * 0.54 + lip + ridge * 0.68 + tip * 0.85);
                double angle = centerAngle + width * angularWidth * 0.5;
                Vec3 radial = frame.normal().multiply(Math.cos(angle))
                        .add(frame.binormal().multiply(Math.sin(angle))).normalized();
                double depth = Math.max(thickness + 0.5, surfaceRelief + 0.8);
                int depthSteps = Math.max(1, (int) Math.ceil(depth));
                for (int depthStep = 0; depthStep <= depthSteps; depthStep++) {
                    double radialDistance = Math.max(0.5,
                            baseRadius + surfaceRelief - depth * depthStep / depthSteps);
                    canvas.add(frame.position().add(radial.multiply(radialDistance)));
                }
            }
        }
    }

    private static void stampSurfacePanel(
            VoxelCanvas canvas,
            SampledPath path,
            double centerDistance,
            double centerAngle,
            double baseRadius,
            double angularWidth,
            double physicalWidth,
            double panelLength,
            double relief,
            int thickness,
            boolean hexagonal
    ) {
        int lengthSteps = Math.max(6, (int) Math.ceil(panelLength * 0.80));
        int widthSteps = Math.max(6, (int) Math.ceil(physicalWidth * 0.62));
        for (int lengthStep = -lengthSteps; lengthStep <= lengthSteps; lengthStep++) {
            double longitudinal = lengthStep / (double) lengthSteps;
            double pathDistance = centerDistance + longitudinal * panelLength * 0.5;
            if (!path.closed() && (pathDistance < 0.0 || pathDistance > path.length())) {
                continue;
            }
            double allowedWidth = hexagonal
                    ? Math.min(1.0, 1.48 - Math.abs(longitudinal) * 0.92)
                    : 1.0;
            SampledPath.Frame frame = path.at(pathDistance);
            for (int widthStep = -widthSteps; widthStep <= widthSteps; widthStep++) {
                double width = widthStep / (double) widthSteps;
                if (Math.abs(width) > allowedWidth) {
                    continue;
                }
                double edge = Math.max(Math.abs(longitudinal), Math.abs(width) / Math.max(0.01, allowedWidth));
                double bevel = relief * (0.62 + 0.38 * Math.clamp((1.0 - edge) * 4.0, 0.0, 1.0));
                double angle = centerAngle + width * angularWidth * 0.5;
                Vec3 radial = frame.normal().multiply(Math.cos(angle))
                        .add(frame.binormal().multiply(Math.sin(angle))).normalized();
                double fillDepth = Math.max(thickness + 0.5, bevel + 0.6);
                int depthSteps = Math.max(1, (int) Math.ceil(fillDepth));
                for (int depthStep = 0; depthStep <= depthSteps; depthStep++) {
                    double radialDistance = Math.max(0.5,
                            baseRadius + bevel - fillDepth * depthStep / depthSteps);
                    canvas.add(frame.position().add(radial.multiply(radialDistance)));
                }
            }
        }
    }

    private static void capTube(VoxelCanvas canvas, SampledPath path, double radius, int thickness) {
        if (path.closed()) {
            return;
        }
        SampledPath.Frame start = path.at(0.0);
        SampledPath.Frame end = path.at(path.length());
        canvas.filledDisc(start.position(), start.normal(), start.binormal(), radius, Math.max(1, thickness));
        canvas.filledDisc(end.position(), end.normal(), end.binormal(), radius, Math.max(1, thickness));
    }

    private static double hash01(int first, int second, int salt) {
        long value = first * 0x9E3779B97F4A7C15L
                ^ second * 0xC2B2AE3D27D4EB4FL
                ^ salt * 0x165667B19E3779F9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double hash01(int first, int second, int third, int salt) {
        long value = first * 0x9E3779B97F4A7C15L
                ^ second * 0xC2B2AE3D27D4EB4FL
                ^ third * 0x165667B19E3779F9L
                ^ salt * 0xD6E8FEB86659FD93L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static void stampSquareTile(
            VoxelCanvas canvas,
            SampledPath path,
            double centerDistance,
            double centerAngle,
            double baseRadius,
            double angularWidth,
            double physicalWidth,
            double tileLength,
            double relief,
            int thickness
    ) {
        int lengthSteps = Math.max(8, (int) Math.ceil(tileLength * 0.82));
        int widthSteps = Math.max(8, (int) Math.ceil(physicalWidth * 0.55));
        for (int lengthStep = -lengthSteps; lengthStep <= lengthSteps; lengthStep++) {
            double longitudinal = lengthStep / (double) lengthSteps;
            double pathDistance = centerDistance + longitudinal * tileLength * 0.5;
            if (!path.closed() && (pathDistance < 0.0 || pathDistance > path.length())) {
                continue;
            }
            SampledPath.Frame frame = path.at(pathDistance);
            double incline = squareTileIncline(longitudinal);
            for (int widthStep = -widthSteps; widthStep <= widthSteps; widthStep++) {
                double width = widthStep / (double) widthSteps;
                double lateralCrown = 0.92 + 0.08 * Math.sqrt(Math.max(0.0, 1.0 - width * width));
                double surfaceRelief = relief * incline * lateralCrown;
                double angle = centerAngle + width * angularWidth * 0.5;
                Vec3 radial = frame.normal().multiply(Math.cos(angle))
                        .add(frame.binormal().multiply(Math.sin(angle)))
                        .normalized();
                boolean sideWall = Math.abs(width) >= 0.90;
                boolean raisedLip = longitudinal >= 0.82;
                double slabDepth = Math.max(1.25, thickness + 0.35);
                double fillDepth = sideWall || raisedLip
                        ? Math.max(slabDepth, surfaceRelief + 0.8)
                        : slabDepth;
                int depthSteps = Math.max(1, (int) Math.ceil(fillDepth));
                for (int depthStep = 0; depthStep <= depthSteps; depthStep++) {
                    double depth = fillDepth * depthStep / depthSteps;
                    double radialDistance = Math.max(0.5, baseRadius + surfaceRelief - depth);
                    canvas.add(frame.position().add(radial.multiply(radialDistance)));
                }
            }
        }
    }

    private static double squareTileIncline(double longitudinal) {
        double progression = Math.clamp((longitudinal + 1.0) * 0.5, 0.0, 1.0);
        double rise = smoothStep(Math.clamp((progression - 0.03) / 0.82, 0.0, 1.0));
        return 0.10 + rise * 0.90;
    }

    private static double scaleHalfWidth(double longitudinal) {
        double progression = (longitudinal + 1.0) * 0.5;
        if (progression <= 0.18) {
            return 0.82 + progression;
        }
        if (progression <= 0.62) {
            return 1.0;
        }
        double taper = smoothStep((progression - 0.62) / 0.38);
        return 1.0 - taper * 0.72;
    }

    private static double scaleIncline(double longitudinal) {
        double progression = Math.clamp((longitudinal + 1.0) * 0.5, 0.0, 1.0);
        double rise = smoothStep(Math.clamp((progression - 0.04) / 0.78, 0.0, 1.0));
        double roundedLip = progression <= 0.82
                ? 1.0
                : 1.0 - smoothStep((progression - 0.82) / 0.18) * 0.10;
        return (0.08 + rise * 0.92) * roundedLip;
    }

    private static double smoothStep(double value) {
        double clamped = Math.clamp(value, 0.0, 1.0);
        return clamped * clamped * (3.0 - 2.0 * clamped);
    }

    private static double interpolate(double first, double second, double progress) {
        return first + (second - first) * progress;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Cálculo de geometría cancelado");
        }
    }

    private static void figureTrace(
            VoxelCanvas canvas,
            SampledPath path,
            ShapeParameters parameters,
            FigureKind figure
    ) {
        int stationIndex = 0;
        double roll = Math.toRadians(parameters.rollDegrees());
        double gap = Math.clamp(parameters.options().get("gap", 0.08), -0.70, 4.0);
        double pitch = Math.max(parameters.spacing(), parameters.radius() * 1.55 * (1.0 + gap));
        for (double distance : stations(path, pitch)) {
            SampledPath.Frame frame = path.at(distance);
            Vec3 widthAxis = frame.normal().multiply(Math.cos(roll))
                    .add(frame.binormal().multiply(Math.sin(roll)))
                    .normalized();
            Vec3 depthAxis = frame.tangent().cross(widthAxis).normalized();
            double layerOffset = 0.0;
            stampFigure(canvas, frame.position(), frame.tangent(), widthAxis, depthAxis,
                    parameters, figure, layerOffset);
            stationIndex++;
        }
    }

    private static void stampFigure(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 lengthAxis,
            Vec3 widthAxis,
            Vec3 depthAxis,
            ShapeParameters parameters,
            FigureKind figure,
            double layerOffset
    ) {
        int steps = Math.max(4, (int) Math.ceil(parameters.radius() * 1.25));
        for (int lengthStep = -steps; lengthStep <= steps; lengthStep++) {
            double normalizedLength = lengthStep / (double) steps;
            for (int widthStep = -steps; widthStep <= steps; widthStep++) {
                double normalizedWidth = widthStep / (double) steps;
                if (!insideFigure(figure, normalizedLength, normalizedWidth)) {
                    continue;
                }
                double centerRelief = Math.max(0.0,
                        1.0 - Math.sqrt(normalizedLength * normalizedLength + normalizedWidth * normalizedWidth));
                double bevel = parameters.radius() * 0.16 * centerRelief;
                double scaleLift = figure == FigureKind.DIAMOND
                        ? parameters.radius() * 0.10 * Math.max(0.0, normalizedLength)
                        : 0.0;
                Vec3 point = center
                        .add(lengthAxis.multiply(normalizedLength * parameters.radius()))
                        .add(widthAxis.multiply(normalizedWidth * parameters.radius()))
                        .add(depthAxis.multiply(layerOffset + bevel + scaleLift));
                canvas.brush(point, parameters.thickness());
            }
        }
    }

    private static boolean insideFigure(FigureKind figure, double length, double width) {
        double absoluteLength = Math.abs(length);
        double absoluteWidth = Math.abs(width);
        return switch (figure) {
            case DIAMOND -> absoluteLength + absoluteWidth <= 1.0;
            case SQUARE -> Math.max(absoluteLength, absoluteWidth) <= 1.0;
            case CIRCLE -> length * length + width * width <= 1.0;
            case TRIANGLE -> length >= -1.0 && length <= 1.0
                    && absoluteWidth <= (1.0 - length) * 0.5;
            case HEXAGON -> absoluteWidth <= Math.sqrt(3.0) * 0.5
                    && absoluteLength + absoluteWidth / Math.sqrt(3.0) <= 1.0;
        };
    }

    private static void ellipse(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            double firstRadius,
            double secondRadius,
            int thickness
    ) {
        int steps = Math.max(24, (int) Math.ceil(Math.PI * 2.0 * Math.max(firstRadius, secondRadius) * 1.25));
        Vec3 previous = null;
        for (int step = 0; step <= steps; step++) {
            double angle = step * Math.PI * 2.0 / steps;
            Vec3 point = center.add(firstAxis.multiply(Math.cos(angle) * firstRadius))
                    .add(secondAxis.multiply(Math.sin(angle) * secondRadius));
            if (previous != null) {
                canvas.line(previous, point, thickness);
            }
            previous = point;
        }
    }

    private static void superellipseOutline(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            double firstRadius,
            double secondRadius,
            double exponent,
            int thickness,
            double quality
    ) {
        int steps = Math.max(24, (int) Math.ceil(
                Math.PI * 2.0 * Math.max(firstRadius, secondRadius) * quality
        ));
        Vec3 previous = null;
        for (int step = 0; step <= steps; step++) {
            double angle = step * Math.PI * 2.0 / steps;
            double first = signedPower(Math.cos(angle), 2.0 / exponent) * firstRadius;
            double second = signedPower(Math.sin(angle), 2.0 / exponent) * secondRadius;
            Vec3 point = center.add(firstAxis.multiply(first)).add(secondAxis.multiply(second));
            if (previous != null) {
                canvas.line(previous, point, thickness);
            }
            previous = point;
        }
    }

    private static void superTorus(
            VoxelCanvas canvas,
            Vec3 center,
            Vec3 firstAxis,
            Vec3 secondAxis,
            Vec3 depthAxis,
            double outerRadius,
            double radialThickness,
            double depth,
            double majorExponent,
            double minorExponent,
            double quality
    ) {
        double majorRadius = Math.max(0.5, outerRadius - radialThickness);
        int radialSteps = Math.max(2, (int) Math.ceil(outerRadius * quality));
        int depthSteps = Math.max(1, (int) Math.ceil(depth * quality));
        for (int firstStep = -radialSteps; firstStep <= radialSteps; firstStep++) {
            double first = outerRadius * firstStep / radialSteps;
            for (int secondStep = -radialSteps; secondStep <= radialSteps; secondStep++) {
                double second = outerRadius * secondStep / radialSteps;
                double radialMetric = Math.pow(
                        Math.pow(Math.abs(first), majorExponent)
                                + Math.pow(Math.abs(second), majorExponent),
                        1.0 / majorExponent
                );
                double radialDistance = Math.abs(radialMetric - majorRadius) / radialThickness;
                if (radialDistance > 1.0) {
                    continue;
                }
                for (int depthStep = -depthSteps; depthStep <= depthSteps; depthStep++) {
                    double depthOffset = depth * depthStep / depthSteps;
                    double crossSection = Math.pow(radialDistance, minorExponent)
                            + Math.pow(Math.abs(depthOffset / depth), minorExponent);
                    if (crossSection > 1.0) {
                        continue;
                    }
                    canvas.add(center
                            .add(firstAxis.multiply(first))
                            .add(secondAxis.multiply(second))
                            .add(depthAxis.multiply(depthOffset)));
                }
            }
        }
    }

    private static double signedPower(double value, double exponent) {
        return Math.copySign(Math.pow(Math.abs(value), exponent), value);
    }

    private static int ringThickness(ShapeParameters parameters) {
        if (parameters.options().has("thickness")) {
            double relative = Math.clamp(parameters.options().get("thickness", 0.15), 0.03, 0.90);
            return Math.clamp((int) Math.round(parameters.radius() * relative), 1, 8);
        }
        return structuralThickness(parameters);
    }

    private static Vec3 radial(SampledPath.Frame frame, double angle, double radius) {
        return frame.position()
                .add(frame.normal().multiply(Math.cos(angle) * radius))
                .add(frame.binormal().multiply(Math.sin(angle) * radius));
    }

    private static int structuralThickness(ShapeParameters parameters) {
        int automatic = (int) Math.ceil(parameters.radius() / 8.0);
        return Math.clamp(automatic + parameters.thickness() - 1, 1, 4);
    }

    private static double spacingProgress(ShapeParameters parameters) {
        return (parameters.spacing() - 2.0) / 6.0;
    }

    private static double scaledSpacing(ShapeParameters parameters, double minimum, double maximum) {
        return minimum + (maximum - minimum) * spacingProgress(parameters);
    }

    private static int surfaceColumns(double radius, int minimum, int maximum) {
        return Math.clamp((int) Math.round(Math.PI * 2.0 * radius / 8.5), minimum, maximum);
    }

    private static List<Double> stations(SampledPath path, double spacing) {
        List<Double> distances = new ArrayList<>();
        double maximum = path.closed() ? path.length() - 1.0E-6 : path.length() + 1.0E-6;
        for (double distance = 0.0; distance <= maximum; distance += spacing) {
            distances.add(Math.min(distance, path.length()));
        }
        if (!path.closed() && path.length() - distances.getLast() > spacing * 0.35) {
            distances.add(path.length());
        }
        return distances;
    }

    private static List<Double> traceDistances(SampledPath path) {
        List<Double> distances = new ArrayList<>();
        for (double distance = 0.0; distance <= path.length() + 1.0E-6; distance += TRACE_STEP) {
            distances.add(Math.min(distance, path.length()));
        }
        if (!path.closed() && path.length() - distances.getLast() > 0.05) {
            distances.add(path.length());
        }
        return distances;
    }

    private record CloudBasis(Vec3 tangent, Vec3 side, Vec3 up) {
    }

    private enum FigureKind {
        DIAMOND,
        SQUARE,
        CIRCLE,
        TRIANGLE,
        HEXAGON
    }
}
