package com.komainu.moxia.shape;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.List;

public final class SampledPath {
    public static final double MAX_LENGTH = 4_096.0;
    private final List<Frame> frames;
    private final double length;
    private final boolean closed;

    private SampledPath(List<Frame> frames, double length, boolean closed) {
        this.frames = frames;
        this.length = length;
        this.closed = closed;
    }

    public static SampledPath create(List<BlockVector3> blockPoints, boolean closed) {
        int minimum = closed ? 3 : 2;
        if (blockPoints.size() < minimum) {
            throw new IllegalArgumentException("La trayectoria necesita al menos " + minimum + " puntos");
        }

        List<Vec3> controls = blockPoints.stream()
                .map(point -> new Vec3(point.x() + 0.5, point.y() + 0.5, point.z() + 0.5))
                .toList();
        List<Vec3> positions = samplePositions(controls, closed);
        if (positions.size() < 2) {
            throw new IllegalArgumentException("Los puntos de la trayectoria no pueden coincidir");
        }
        return createFrames(positions, closed);
    }

    public static SampledPath createSmooth(List<BlockVector3> blockPoints, boolean closed) {
        int minimum = closed ? 3 : 2;
        if (blockPoints.size() < minimum) {
            throw new IllegalArgumentException("La trayectoria necesita al menos " + minimum + " puntos");
        }

        List<Vec3> controls = blockPoints.stream()
                .map(point -> new Vec3(point.x() + 0.5, point.y() + 0.5, point.z() + 0.5))
                .toList();
        List<Vec3> positions = sampleSmoothPositions(controls, closed);
        if (positions.size() < 2) {
            throw new IllegalArgumentException("Los puntos de la trayectoria no pueden coincidir");
        }
        return createFrames(positions, closed);
    }

    public double length() {
        return length;
    }

    public boolean closed() {
        return closed;
    }

    public Frame at(double requestedDistance) {
        double distance = Math.clamp(requestedDistance, 0.0, length);
        if (closed) {
            distance = requestedDistance % length;
            if (distance < 0.0) {
                distance += length;
            }
        }
        if (!closed && distance >= length) {
            return frames.getLast();
        }

        int low = 0;
        int high = frames.size() - 1;
        while (low + 1 < high) {
            int middle = (low + high) >>> 1;
            if (frames.get(middle).distance <= distance) {
                low = middle;
            } else {
                high = middle;
            }
        }

        Frame first = frames.get(low);
        Frame second = frames.get(Math.min(low + 1, frames.size() - 1));
        double span = second.distance - first.distance;
        double amount = span < 1.0E-9 ? 0.0 : (distance - first.distance) / span;
        Vec3 tangent = first.tangent.lerp(second.tangent, amount).normalized();
        Vec3 normal = first.normal.lerp(second.normal, amount);
        normal = normal.subtract(tangent.multiply(normal.dot(tangent))).normalized();
        if (normal.length() < 1.0E-9) {
            normal = initialNormal(tangent);
        }
        Vec3 binormal = tangent.cross(normal).normalized();
        normal = binormal.cross(tangent).normalized();
        return new Frame(first.position.lerp(second.position, amount), tangent, normal, binormal, distance);
    }

    private static List<Vec3> samplePositions(List<Vec3> controls, boolean closed) {
        int segments = closed ? controls.size() : controls.size() - 1;
        List<Vec3> positions = new ArrayList<>();
        for (int segment = 0; segment < segments; segment++) {
            Vec3 first = control(controls, segment - 1, closed);
            Vec3 second = control(controls, segment, closed);
            Vec3 third = control(controls, segment + 1, closed);
            Vec3 fourth = control(controls, segment + 2, closed);
            int steps = Math.clamp((int) Math.ceil(second.distance(third) * 4.0), 4, 512);
            for (int step = 0; step < steps; step++) {
                Vec3 point = catmullRom(first, second, third, fourth, step / (double) steps);
                if (positions.isEmpty() || positions.getLast().distance(point) > 0.02) {
                    positions.add(point);
                }
            }
        }
        Vec3 end = closed ? controls.getFirst() : controls.getLast();
        if (positions.isEmpty() || positions.getLast().distance(end) > 0.02) {
            positions.add(end);
        }
        return positions;
    }

    private static List<Vec3> sampleSmoothPositions(List<Vec3> controls, boolean closed) {
        int segments = closed ? controls.size() : controls.size() - 1;
        List<Vec3> positions = new ArrayList<>();
        for (int segment = 0; segment < segments; segment++) {
            Vec3 first = smoothControl(controls, segment - 1, closed);
            Vec3 second = smoothControl(controls, segment, closed);
            Vec3 third = smoothControl(controls, segment + 1, closed);
            Vec3 fourth = smoothControl(controls, segment + 2, closed);
            int steps = Math.clamp((int) Math.ceil(second.distance(third) * 4.0), 6, 512);
            for (int step = 0; step < steps; step++) {
                Vec3 point = centripetalCatmullRom(
                        first,
                        second,
                        third,
                        fourth,
                        step / (double) steps
                );
                if (positions.isEmpty() || positions.getLast().distance(point) > 0.02) {
                    positions.add(point);
                }
            }
        }
        Vec3 end = closed ? controls.getFirst() : controls.getLast();
        if (positions.isEmpty() || positions.getLast().distance(end) > 0.02) {
            positions.add(end);
        }
        return positions;
    }

    private static Vec3 smoothControl(List<Vec3> controls, int index, boolean closed) {
        if (closed) {
            return controls.get(Math.floorMod(index, controls.size()));
        }
        if (index < 0) {
            return controls.getFirst().multiply(2.0).subtract(controls.get(1));
        }
        if (index >= controls.size()) {
            return controls.getLast().multiply(2.0).subtract(controls.get(controls.size() - 2));
        }
        return controls.get(index);
    }

    private static Vec3 centripetalCatmullRom(
            Vec3 first,
            Vec3 second,
            Vec3 third,
            Vec3 fourth,
            double amount
    ) {
        double firstTime = 0.0;
        double secondTime = nextCentripetalTime(firstTime, first, second);
        double thirdTime = nextCentripetalTime(secondTime, second, third);
        double fourthTime = nextCentripetalTime(thirdTime, third, fourth);
        double time = secondTime + (thirdTime - secondTime) * amount;

        Vec3 firstBlend = timedLerp(first, second, firstTime, secondTime, time);
        Vec3 secondBlend = timedLerp(second, third, secondTime, thirdTime, time);
        Vec3 thirdBlend = timedLerp(third, fourth, thirdTime, fourthTime, time);
        Vec3 firstCurve = timedLerp(firstBlend, secondBlend, firstTime, thirdTime, time);
        Vec3 secondCurve = timedLerp(secondBlend, thirdBlend, secondTime, fourthTime, time);
        return timedLerp(firstCurve, secondCurve, secondTime, thirdTime, time);
    }

    private static double nextCentripetalTime(double previous, Vec3 first, Vec3 second) {
        return previous + Math.sqrt(Math.max(1.0E-9, first.distance(second)));
    }

    private static Vec3 timedLerp(
            Vec3 first,
            Vec3 second,
            double firstTime,
            double secondTime,
            double time
    ) {
        double span = secondTime - firstTime;
        if (span < 1.0E-9) {
            return first;
        }
        return first.multiply((secondTime - time) / span)
                .add(second.multiply((time - firstTime) / span));
    }

    private static Vec3 control(List<Vec3> controls, int index, boolean closed) {
        if (closed) {
            return controls.get(Math.floorMod(index, controls.size()));
        }
        return controls.get(Math.clamp(index, 0, controls.size() - 1));
    }

    private static Vec3 catmullRom(Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, double amount) {
        double squared = amount * amount;
        double cubed = squared * amount;
        return second.multiply(2.0)
                .add(third.subtract(first).multiply(amount))
                .add(first.multiply(2.0).subtract(second.multiply(5.0))
                        .add(third.multiply(4.0)).subtract(fourth).multiply(squared))
                .add(first.multiply(-1.0).add(second.multiply(3.0))
                        .subtract(third.multiply(3.0)).add(fourth).multiply(cubed))
                .multiply(0.5);
    }

    private static SampledPath createFrames(List<Vec3> positions, boolean closed) {
        List<Frame> frames = new ArrayList<>(positions.size());
        double distance = 0.0;
        Vec3 previousNormal = null;
        for (int index = 0; index < positions.size(); index++) {
            Vec3 position = positions.get(index);
            if (index > 0) {
                distance += position.distance(positions.get(index - 1));
            }
            Vec3 before = positions.get(Math.max(0, index - 1));
            Vec3 after = positions.get(Math.min(positions.size() - 1, index + 1));
            Vec3 tangent = after.subtract(before).normalized();
            if (tangent.length() < 1.0E-9 && !frames.isEmpty()) {
                tangent = frames.getLast().tangent;
            }
            Vec3 normal = previousNormal == null
                    ? initialNormal(tangent)
                    : previousNormal.subtract(tangent.multiply(previousNormal.dot(tangent))).normalized();
            if (normal.length() < 1.0E-9) {
                normal = initialNormal(tangent);
            }
            Vec3 binormal = tangent.cross(normal).normalized();
            normal = binormal.cross(tangent).normalized();
            frames.add(new Frame(position, tangent, normal, binormal, distance));
            previousNormal = normal;
        }
        if (distance < 0.1) {
            throw new IllegalArgumentException("La trayectoria es demasiado corta");
        }
        if (distance > MAX_LENGTH) {
            throw new IllegalArgumentException("La trayectoria supera el límite de 4096 bloques de longitud");
        }
        return new SampledPath(List.copyOf(frames), distance, closed);
    }

    private static Vec3 initialNormal(Vec3 tangent) {
        Vec3 reference = Math.abs(tangent.y()) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 binormal = tangent.cross(reference).normalized();
        return binormal.cross(tangent).normalized();
    }

    public record Frame(Vec3 position, Vec3 tangent, Vec3 normal, Vec3 binormal, double distance) {
    }
}
