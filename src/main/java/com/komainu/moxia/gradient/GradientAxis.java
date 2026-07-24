package com.komainu.moxia.gradient;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.Locale;

public enum GradientAxis {
    X("x"),
    Y("y"),
    Z("z"),
    DIAGONAL("diagonal"),
    RADIAL("radial");

    private final String id;

    GradientAxis(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public double normalized(BlockVector3 point, BlockVector3 minimum, BlockVector3 maximum) {
        return switch (this) {
            case X -> ratio(point.x(), minimum.x(), maximum.x());
            case Y -> ratio(point.y(), minimum.y(), maximum.y());
            case Z -> ratio(point.z(), minimum.z(), maximum.z());
            case DIAGONAL -> (
                    ratio(point.x(), minimum.x(), maximum.x())
                            + ratio(point.y(), minimum.y(), maximum.y())
                            + ratio(point.z(), minimum.z(), maximum.z())
            ) / 3.0;
            case RADIAL -> radial(point, minimum, maximum);
        };
    }

    private static double radial(BlockVector3 point, BlockVector3 minimum, BlockVector3 maximum) {
        double centerX = (minimum.x() + maximum.x()) * 0.5;
        double centerY = (minimum.y() + maximum.y()) * 0.5;
        double centerZ = (minimum.z() + maximum.z()) * 0.5;
        double radiusX = Math.max(0.5, (maximum.x() - minimum.x()) * 0.5);
        double radiusY = Math.max(0.5, (maximum.y() - minimum.y()) * 0.5);
        double radiusZ = Math.max(0.5, (maximum.z() - minimum.z()) * 0.5);
        double x = (point.x() - centerX) / radiusX;
        double y = (point.y() - centerY) / radiusY;
        double z = (point.z() - centerZ) / radiusZ;
        return Math.clamp(Math.sqrt(x * x + y * y + z * z) / Math.sqrt(3.0), 0.0, 1.0);
    }

    private static double ratio(int value, int minimum, int maximum) {
        return maximum == minimum ? 0.0 : (value - minimum) / (double) (maximum - minimum);
    }

    public static GradientAxis byId(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        for (GradientAxis axis : values()) {
            if (axis.id.equals(normalized)) {
                return axis;
            }
        }
        throw new IllegalArgumentException("Eje desconocido: " + input + ". Usa x, y, z, diagonal o radial");
    }
}
