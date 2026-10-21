package com.komainu.moxia.ruler;

import com.komainu.moxia.feedback.WorldSketch;
import com.komainu.moxia.feedback.Words;
import com.komainu.moxia.shape.Vec3;
import com.komainu.moxia.studio.ui.StudioTheme;

import java.util.List;

/**
 * Lo que la regla dibuja en el mundo, igual en el juego y en los renders: los puntos numerados, los tramos con su
 * medida, las patas por ejes del último tramo, el círculo con su radio y su diámetro y la caja con sus tres lados.
 * Mientras se apunta, lo que mediría el siguiente clic sale a media luz.
 */
public final class RulerSketch {
    private static final int RULER = StudioTheme.WORLD_RULER;
    private static final int POINT = StudioTheme.WORLD_RULER_POINT;
    private static final int HOVER = StudioTheme.WORLD_RULER_HOVER;
    private static final int LABEL = StudioTheme.WORLD_LABEL;
    private static final int[] AXES = {StudioTheme.WORLD_PLANE_X, StudioTheme.WORLD_PLANE_Y, StudioTheme.WORLD_PLANE_Z};
    private static final int[] AXIS_LABELS = {StudioTheme.WORLD_LABEL_X, StudioTheme.WORLD_LABEL_Y,
            StudioTheme.WORLD_LABEL_Z};
    /** El número de cada punto, encima de su bloque. */
    private static final Vec3 NUMBER = new Vec3(0, 0.85, 0);
    private static final int CIRCLE_SEGMENTS = 96;

    private RulerSketch() {
    }

    /**
     * @param camera dónde está la cámara (las medidas de la caja van en sus aristas más cercanas)
     * @param hover  el punto al que se apunta ahora, o {@code null}
     */
    public static WorldSketch of(Ruler ruler, Vec3 camera, RulerPoint hover, Words words) {
        WorldSketch.Builder sketch = WorldSketch.builder();
        List<RulerPoint> points = ruler.points();
        RulerMeasure measure = ruler.measure();
        // El último punto de un recorrido de varios tramos lleva el total debajo de su número (line).
        boolean totalOnLast = measure instanceof RulerMeasure.Line line && line.segments().size() > 1;
        for (int index = 0; index < points.size(); index++) {
            RulerPoint point = points.get(index);
            marker(sketch, point, POINT);
            if (ruler.mode() == RulerMode.LINE && !(totalOnLast && index == points.size() - 1)) {
                sketch.label(point.center().add(NUMBER), Integer.toString(index + 1), LABEL);
            }
        }
        switch (measure) {
            case RulerMeasure.Line line -> line(sketch, line, words);
            case RulerMeasure.Circle circle -> circle(sketch, circle, words, RULER);
            case RulerMeasure.Box box -> box(sketch, box, camera, words, RULER);
            case RulerMeasure.Pending ignored -> {
            }
        }
        if (hover != null && !points.isEmpty() && !points.getLast().equals(hover)) {
            hover(sketch, ruler, hover, camera, words);
        } else if (hover != null) {
            marker(sketch, hover, HOVER);
        }
        return sketch.build();
    }

    /** Lo que mediría el siguiente clic. */
    private static void hover(WorldSketch.Builder sketch, Ruler ruler, RulerPoint hover, Vec3 camera,
                              Words words) {
        marker(sketch, hover, HOVER);
        List<RulerPoint> points = ruler.points();
        switch (ruler.mode()) {
            case LINE -> {
                if (points.size() >= Ruler.MAX_POINTS) {
                    return;
                }
                RulerPoint last = points.getLast();
                RulerMeasure.Delta delta = RulerMeasure.Delta.between(last.center(), hover.center());
                sketch.ghostLine(last.center(), hover.center(), HOVER);
                sketch.label(last.center().lerp(hover.center(), 0.5).add(new Vec3(0, 0.35, 0)),
                        RulerReport.number(delta.euclidean(), words), LABEL);
            }
            case CIRCLE -> {
                if (points.size() == 1) {
                    circle(sketch, RulerMeasure.Circle.of(points.getFirst(), hover), words, HOVER);
                }
            }
            case BOX -> {
                if (points.size() == 1) {
                    box(sketch, RulerMeasure.Box.of(points.getFirst(), hover), camera, words, HOVER);
                }
            }
        }
    }

    private static void marker(WorldSketch.Builder sketch, RulerPoint point, int argb) {
        if (point.isBlock()) {
            sketch.block(point.x(), point.y(), point.z(), argb);
            return;
        }
        Vec3 center = point.center();
        double size = 0.25;
        sketch.ghostLine(center.add(new Vec3(-size, 0, 0)), center.add(new Vec3(size, 0, 0)), argb);
        sketch.ghostLine(center.add(new Vec3(0, -size, 0)), center.add(new Vec3(0, size, 0)), argb);
        sketch.ghostLine(center.add(new Vec3(0, 0, -size)), center.add(new Vec3(0, 0, size)), argb);
    }

    private static void line(WorldSketch.Builder sketch, RulerMeasure.Line line, Words words) {
        for (RulerMeasure.Segment segment : line.segments()) {
            Vec3 from = segment.from().center();
            Vec3 to = segment.to().center();
            sketch.ghostLine(from, to, RULER);
            sketch.label(from.lerp(to, 0.5).add(new Vec3(0, 0.35, 0)),
                    RulerReport.number(segment.delta().euclidean(), words), LABEL);
        }
        // Las patas por ejes del último tramo: primero X, luego Z y al final la altura.
        RulerMeasure.Segment last = line.segments().getLast();
        Vec3 from = last.from().center();
        Vec3 to = last.to().center();
        Vec3[] corners = {from, new Vec3(to.x(), from.y(), from.z()), new Vec3(to.x(), from.y(), to.z()), to};
        int[] axes = {0, 2, 1};
        for (int leg = 0; leg < 3; leg++) {
            Vec3 start = corners[leg];
            Vec3 end = corners[leg + 1];
            double length = start.distance(end);
            if (length < 1.0E-9) {
                continue;
            }
            int axis = axes[leg];
            sketch.ghostLine(start, end, 0xB0000000 | AXES[axis]);
            sketch.label(start.lerp(end, 0.5).add(new Vec3(0, -0.3, 0)),
                    "XYZ".charAt(axis) + " " + RulerReport.number(length, words), AXIS_LABELS[axis]);
        }
        if (line.segments().size() > 1) {
            // El número del último punto y, debajo, el total: un solo rótulo, así no se tapan.
            String total = words.tr("gui.komaedit.ruler.world.total", RulerReport.number(line.length(), words),
                    RulerReport.number(line.manhattan(), words));
            sketch.label(new WorldSketch.Label(to.add(NUMBER), List.of(
                    new WorldSketch.Row(Integer.toString(line.points().size()), LABEL),
                    new WorldSketch.Row(total, LABEL)), null, 0, 1.0, 1.0, false));
        }
    }

    private static void circle(WorldSketch.Builder sketch, RulerMeasure.Circle circle, Words words,
                               int argb) {
        Vec3 center = circle.center().center();
        Vec3 edge = circle.edge().center();
        // El borde llevado al plano del círculo.
        Vec3 flat = switch (circle.axis()) {
            case 0 -> new Vec3(center.x(), edge.y(), edge.z());
            case 1 -> new Vec3(edge.x(), center.y(), edge.z());
            default -> new Vec3(edge.x(), edge.y(), center.z());
        };
        double radius = circle.radius();
        sketch.ghostLine(center, flat, argb);
        if (radius > 1.0E-9) {
            Vec3 u = flat.subtract(center).multiply(1.0 / radius);
            Vec3 normal = switch (circle.axis()) {
                case 0 -> new Vec3(1, 0, 0);
                case 1 -> new Vec3(0, 1, 0);
                default -> new Vec3(0, 0, 1);
            };
            Vec3 v = normal.cross(u).normalized();
            Vec3 previous = null;
            for (int segment = 0; segment <= CIRCLE_SEGMENTS; segment++) {
                double angle = 2.0 * Math.PI * segment / CIRCLE_SEGMENTS;
                Vec3 point = center.add(u.multiply(Math.cos(angle) * radius)).add(v.multiply(Math.sin(angle) * radius));
                if (previous != null) {
                    sketch.ghostLine(previous, point, argb);
                }
                previous = point;
            }
            // El diámetro, de lado a lado por el centro, más tenue.
            Vec3 opposite = center.subtract(u.multiply(radius));
            sketch.ghostLine(center, opposite, (argb & 0xFFFFFF) | 0x70000000);
            String diameter = circle.blocksAcross() > 0
                    ? words.tr("gui.komaedit.ruler.world.diameter_blocks", RulerReport.number(circle.diameter(), words),
                    RulerFormat.number(circle.blocksAcross(), words.decimal()))
                    : words.tr("gui.komaedit.ruler.world.diameter", RulerReport.number(circle.diameter(), words));
            sketch.label(center.add(v.multiply(radius)).add(new Vec3(0, 0.5, 0)), diameter, LABEL);
        }
        sketch.label(center.lerp(flat, 0.5).add(new Vec3(0, 0.35, 0)),
                words.tr("gui.komaedit.ruler.world.radius", RulerReport.number(radius, words)), LABEL);
        if (circle.offset() > 1.0E-9) {
            sketch.ghostLine(flat, edge, 0xB0000000 | AXES[circle.axis()]);
        }
    }

    private static void box(WorldSketch.Builder sketch, RulerMeasure.Box box, Vec3 camera, Words words,
                            int argb) {
        Vec3 min = box.min();
        Vec3 max = box.max();
        double grow = 0.01;
        sketch.box(min.subtract(new Vec3(grow, grow, grow)), max.add(new Vec3(grow, grow, grow)), argb);
        // Cada lado en la arista más cercana a la cámara, como las medidas de la selección.
        double nearX = Math.abs(camera.x() - min.x()) < Math.abs(camera.x() - max.x()) ? min.x() : max.x();
        double nearY = Math.abs(camera.y() - min.y()) < Math.abs(camera.y() - max.y()) ? min.y() : max.y();
        double nearZ = Math.abs(camera.z() - min.z()) < Math.abs(camera.z() - max.z()) ? min.z() : max.z();
        Vec3 middle = min.lerp(max, 0.5);
        sketch.label(new Vec3(middle.x(), nearY, nearZ), RulerReport.number(box.width(), words), AXIS_LABELS[0]);
        sketch.label(new Vec3(nearX, middle.y(), nearZ), RulerReport.number(box.height(), words), AXIS_LABELS[1]);
        sketch.label(new Vec3(nearX, nearY, middle.z()), RulerReport.number(box.length(), words), AXIS_LABELS[2]);
        String total = words.tr(box.blocks() ? "gui.komaedit.ruler.world.box_blocks" : "gui.komaedit.ruler.world.box",
                RulerReport.number(box.width(), words), RulerReport.number(box.height(), words),
                RulerReport.number(box.length(), words), RulerReport.number(box.volume(), words));
        sketch.label(new Vec3(middle.x(), max.y() + 0.7, middle.z()), total, LABEL);
    }
}
