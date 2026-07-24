package com.komainu.moxia.shape;

public record Vec3(double x, double y, double z) {
    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 subtract(Vec3 other) {
        return new Vec3(x - other.x, y - other.y, z - other.z);
    }

    public Vec3 multiply(double factor) {
        return new Vec3(x * factor, y * factor, z * factor);
    }

    public double dot(Vec3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public Vec3 cross(Vec3 other) {
        return new Vec3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.sqrt(dot(this));
    }

    public double distance(Vec3 other) {
        return subtract(other).length();
    }

    public Vec3 normalized() {
        double length = length();
        return length < 1.0E-9 ? new Vec3(0, 0, 0) : multiply(1.0 / length);
    }

    public Vec3 lerp(Vec3 other, double amount) {
        return multiply(1.0 - amount).add(other.multiply(amount));
    }
}
