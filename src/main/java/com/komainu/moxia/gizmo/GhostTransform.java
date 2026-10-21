package com.komainu.moxia.gizmo;

import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.edit.PackedBlockPlan;

/**
 * Dónde está y cómo está girado el objeto que se coloca: su orientación y la posición del mundo de su pivote. Un
 * bloque con posición local {@code l} (relativa al pivote) cae en {@code (x, y, z) + orientación · l}.
 *
 * <p>Girar no mueve el pivote: el objeto gira sobre sí mismo. Es lo único que el cliente manda al confirmar; el
 * servidor la aplica al plan que ya tenía calculado. Lógica pura.</p>
 */
public record GhostTransform(Orientation orientation, int x, int y, int z) {
    public GhostTransform {
        if (orientation == null) {
            throw new IllegalArgumentException("Falta la orientación");
        }
    }

    public static GhostTransform at(int x, int y, int z) {
        return new GhostTransform(Orientation.IDENTITY, x, y, z);
    }

    public int worldX(int localX, int localY, int localZ) {
        return x + orientation.x(localX, localY, localZ);
    }

    public int worldY(int localX, int localY, int localZ) {
        return y + orientation.y(localX, localY, localZ);
    }

    public int worldZ(int localX, int localY, int localZ) {
        return z + orientation.z(localX, localY, localZ);
    }

    /** Una posición local empaquetada ({@link PackedBlockPlan#pack}) llevada al mundo. */
    public long apply(long local) {
        int localX = PackedBlockPlan.unpackX(local);
        int localY = PackedBlockPlan.unpackY(local);
        int localZ = PackedBlockPlan.unpackZ(local);
        return PackedBlockPlan.pack(worldX(localX, localY, localZ), worldY(localX, localY, localZ),
                worldZ(localX, localY, localZ));
    }

    /** La posición local que cae en ({@code worldX}, {@code worldY}, {@code worldZ}). */
    public int[] local(int worldX, int worldY, int worldZ) {
        return orientation.inverse().apply(worldX - x, worldY - y, worldZ - z);
    }

    public GhostTransform moved(int dx, int dy, int dz) {
        return new GhostTransform(orientation, x + dx, y + dy, z + dz);
    }

    public GhostTransform movedTo(int newX, int newY, int newZ) {
        return new GhostTransform(orientation, newX, newY, newZ);
    }

    /** Gira {@code quarterTurns} cuartos de vuelta alrededor de un eje del mundo que pasa por el pivote. */
    public GhostTransform turned(int worldAxis, int quarterTurns) {
        return new GhostTransform(orientation.then(Orientation.turn(worldAxis, quarterTurns)), x, y, z);
    }

    /** Voltea en el eje {@code worldAxis} del mundo, sobre el pivote. */
    public GhostTransform mirrored(int worldAxis) {
        return new GhostTransform(orientation.then(Orientation.mirror(worldAxis)), x, y, z);
    }

    /** La caja local girada, aún relativa al pivote. */
    public BlockBounds oriented(BlockBounds local) {
        int[] first = orientation.apply(local.minimumX(), local.minimumY(), local.minimumZ());
        int[] second = orientation.apply(local.maximumX(), local.maximumY(), local.maximumZ());
        return new BlockBounds(Math.min(first[0], second[0]), Math.min(first[1], second[1]),
                Math.min(first[2], second[2]), Math.max(first[0], second[0]), Math.max(first[1], second[1]),
                Math.max(first[2], second[2]));
    }

    /** La caja del mundo que ocupa un objeto de caja local {@code local}. */
    public BlockBounds bounds(BlockBounds local) {
        BlockBounds turned = oriented(local);
        return new BlockBounds(x + turned.minimumX(), y + turned.minimumY(), z + turned.minimumZ(),
                x + turned.maximumX(), y + turned.maximumY(), z + turned.maximumZ());
    }
}
