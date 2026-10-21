package com.komainu.moxia.reference;

import com.komainu.moxia.shape.Vec3;

/**
 * Dónde está una imagen de referencia en el mundo: un plano con centro, giro y tamaño. El tamaño va en píxeles de la
 * imagen por bloque, la misma unidad que la rejilla ({@link ReferenceGrid}): con {@code pixelsPerBlock} igual a los
 * píxeles de una celda, cada celda ocupa un bloque.
 *
 * <p>Las coordenadas de la imagen van en píxeles desde su esquina de arriba a la izquierda, como al abrirla en un
 * programa de dibujo: {@code u} hacia la derecha y {@code v} hacia abajo.</p>
 *
 * @param depth con profundidad: los bloques que tenga delante la tapan; sin ella se ve siempre, por encima de todo
 */
public record WorldFrame(Vec3 center, ReferenceRotation rotation, double pixelsPerBlock, boolean depth) {
    public static final double MIN_PIXELS_PER_BLOCK = 1.0 / 64.0;
    public static final double MAX_PIXELS_PER_BLOCK = 4_096.0;
    /** Lo más grande que puede ser un lado, en bloques (más allá ni se ve ni sirve para calcar). */
    public static final double MAX_SIDE_BLOCKS = 2_048.0;
    /** Lo más pequeño, en bloques: un píxel de bloque. */
    public static final double MIN_SIDE_BLOCKS = 1.0 / 16.0;
    /** Lejos del mundo jugable no tiene sentido: se recorta. */
    private static final double MAX_COORDINATE = 30_000_000.0;

    public WorldFrame {
        center = center == null ? new Vec3(0.0, 64.0, 0.0) : clamp(center);
        rotation = rotation == null ? ReferenceRotation.IDENTITY : rotation;
        pixelsPerBlock = Double.isFinite(pixelsPerBlock)
                ? Math.clamp(pixelsPerBlock, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK) : 1.0;
    }

    /** Sin giro, con el tamaño de serie para una imagen de esas medidas ({@link #defaultPixelsPerBlock}). */
    public static WorldFrame at(Vec3 center, int pixelWidth, int pixelHeight) {
        return new WorldFrame(center, ReferenceRotation.IDENTITY, defaultPixelsPerBlock(pixelWidth, pixelHeight),
                false);
    }

    /**
     * Tamaño de serie: un píxel por bloque si parece pixel art (ningún lado pasa de 128); si no, unos 32 bloques por
     * su lado más largo, lo que ocupa una fachada grande.
     */
    public static double defaultPixelsPerBlock(int pixelWidth, int pixelHeight) {
        int longest = Math.max(1, Math.max(pixelWidth, pixelHeight));
        return longest <= 128 ? 1.0 : longest / 32.0;
    }

    public WorldFrame withCenter(Vec3 value) {
        return new WorldFrame(value, rotation, pixelsPerBlock, depth);
    }

    public WorldFrame moved(Vec3 delta) {
        return withCenter(center.add(delta));
    }

    public WorldFrame withRotation(ReferenceRotation value) {
        return new WorldFrame(center, value, pixelsPerBlock, depth);
    }

    public WorldFrame withPixelsPerBlock(double value) {
        return new WorldFrame(center, rotation, value, depth);
    }

    public WorldFrame withDepth(boolean value) {
        return new WorldFrame(center, rotation, pixelsPerBlock, value);
    }

    /**
     * El mismo plano con la anchura de la imagen en bloques; se respeta el tamaño mínimo y máximo de un lado (por eso
     * hacen falta las dos medidas).
     */
    public WorldFrame withWidthBlocks(double blocks, int pixelWidth, int pixelHeight) {
        int width = Math.max(1, pixelWidth);
        int height = Math.max(1, pixelHeight);
        double longest = Math.max(width, height);
        double shortest = Math.min(width, height);
        double scale = blocks / width;
        scale = Math.min(scale, MAX_SIDE_BLOCKS / longest);
        scale = Math.max(scale, MIN_SIDE_BLOCKS / shortest);
        return withPixelsPerBlock(1.0 / scale);
    }

    public double widthBlocks(int pixelWidth) {
        return Math.max(1, pixelWidth) / pixelsPerBlock;
    }

    public double heightBlocks(int pixelHeight) {
        return Math.max(1, pixelHeight) / pixelsPerBlock;
    }

    public Vec3 right() {
        return rotation.right();
    }

    public Vec3 up() {
        return rotation.up();
    }

    public Vec3 normal() {
        return rotation.normal();
    }

    /** El punto del mundo del píxel ({@code u}, {@code v}) de la imagen, medido desde arriba a la izquierda. */
    public Vec3 point(double u, double v, int pixelWidth, int pixelHeight) {
        double across = (u - Math.max(1, pixelWidth) * 0.5) / pixelsPerBlock;
        double rise = (Math.max(1, pixelHeight) * 0.5 - v) / pixelsPerBlock;
        return center.add(right().multiply(across)).add(up().multiply(rise));
    }

    /** Las cuatro esquinas: arriba a la izquierda, arriba a la derecha, abajo a la derecha y abajo a la izquierda. */
    public Vec3[] corners(int pixelWidth, int pixelHeight) {
        int width = Math.max(1, pixelWidth);
        int height = Math.max(1, pixelHeight);
        return new Vec3[]{point(0, 0, width, height), point(width, 0, width, height),
                point(width, height, width, height), point(0, height, width, height)};
    }

    /**
     * El píxel de la imagen bajo un punto del mundo ({@code {u, v}}, puede caer fuera de la imagen), proyectándolo
     * sobre el plano. Es la inversa de {@link #point}.
     */
    public double[] pixel(Vec3 point, int pixelWidth, int pixelHeight) {
        Vec3 offset = point.subtract(center);
        double u = offset.dot(right()) * pixelsPerBlock + Math.max(1, pixelWidth) * 0.5;
        double v = Math.max(1, pixelHeight) * 0.5 - offset.dot(up()) * pixelsPerBlock;
        return new double[]{u, v};
    }

    private static Vec3 clamp(Vec3 value) {
        return new Vec3(coordinate(value.x()), coordinate(value.y()), coordinate(value.z()));
    }

    private static double coordinate(double value) {
        return Double.isFinite(value) ? Math.clamp(value, -MAX_COORDINATE, MAX_COORDINATE) : 0.0;
    }
}
