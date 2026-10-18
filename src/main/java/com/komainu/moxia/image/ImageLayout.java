package com.komainu.moxia.image;

import java.util.Arrays;

/**
 * Dónde cae cada píxel de la obra en el mundo. La columna {@code u} crece hacia {@code right} y la fila {@code v}
 * (0 arriba) baja en sentido contrario a {@code up}: la esquina inferior izquierda de la imagen está en el origen.
 * Los vectores son unitarios y van en ejes del mundo, así que la obra queda en un plano vertical u horizontal.
 */
public record ImageLayout(int x, int y, int z, int[] right, int[] up, int width, int height) {
    public ImageLayout {
        right = right.clone();
        up = up.clone();
    }

    public int worldX(int u, int v) {
        return x + right[0] * u + up[0] * (height - 1 - v);
    }

    public int worldY(int u, int v) {
        return y + right[1] * u + up[1] * (height - 1 - v);
    }

    public int worldZ(int u, int v) {
        return z + right[2] * u + up[2] * (height - 1 - v);
    }

    /** ¿Es horizontal (suelo o techo)? */
    public boolean horizontal() {
        return right[1] == 0 && up[1] == 0;
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ}. */
    public int[] bounds() {
        int[] first = {worldX(0, 0), worldY(0, 0), worldZ(0, 0)};
        int[] last = {worldX(width - 1, height - 1), worldY(width - 1, height - 1), worldZ(width - 1, height - 1)};
        return new int[]{Math.min(first[0], last[0]), Math.min(first[1], last[1]), Math.min(first[2], last[2]),
                Math.max(first[0], last[0]), Math.max(first[1], last[1]), Math.max(first[2], last[2])};
    }

    /** La obra con el punto {@code anchor} en {@code target}. */
    public static ImageLayout anchored(int[] target, int[] right, int[] up, ImageSize size, Anchor anchor) {
        int across = (int) Math.floor(anchor.horizontal() * (size.width() - 1));
        int along = (int) Math.floor(anchor.vertical() * (size.height() - 1));
        return new ImageLayout(
                target[0] - right[0] * across - up[0] * along,
                target[1] - right[1] * across - up[1] * along,
                target[2] - right[2] * across - up[2] * along,
                right, up, size.width(), size.height());
    }

    /**
     * La obra dentro de la caja {@code min..max} (inclusive), plana en el eje que no recorren {@code right} ni
     * {@code up}; si no la llena, se arrima según {@code anchor}.
     */
    public static ImageLayout inBox(int[] min, int[] max, int[] right, int[] up, ImageSize size, Anchor anchor) {
        int[] box = boxSize(min, max, right, up);
        if (size.width() > box[0] || size.height() > box[1]) {
            throw new IllegalArgumentException("La obra (" + size + ") no cabe en la selección (" + box[0] + "×"
                    + box[1] + ")");
        }
        int[] corner = new int[3];
        for (int axis = 0; axis < 3; axis++) {
            if (right[axis] != 0) {
                corner[axis] = right[axis] > 0 ? min[axis] : max[axis];
            } else if (up[axis] != 0) {
                corner[axis] = up[axis] > 0 ? min[axis] : max[axis];
            } else {
                corner[axis] = min[axis];
            }
        }
        int across = (int) Math.floor((box[0] - size.width()) * anchor.horizontal());
        int along = (int) Math.floor((box[1] - size.height()) * anchor.vertical());
        return new ImageLayout(
                corner[0] + right[0] * across + up[0] * along,
                corner[1] + right[1] * across + up[1] * along,
                corner[2] + right[2] * across + up[2] * along,
                right, up, size.width(), size.height());
    }

    /** Ancho (a lo largo de {@code right}) y alto (a lo largo de {@code up}) de la caja. */
    public static int[] boxSize(int[] min, int[] max, int[] right, int[] up) {
        return new int[]{extent(min, max, right), extent(min, max, up)};
    }

    private static int extent(int[] min, int[] max, int[] direction) {
        for (int axis = 0; axis < 3; axis++) {
            if (direction[axis] != 0) {
                return max[axis] - min[axis] + 1;
            }
        }
        throw new IllegalArgumentException("Dirección nula");
    }

    /** El eje en el que la selección tiene un bloque de grosor: 0 = X, 1 = Y, 2 = Z. Una línea no vale. */
    public static int flatAxis(int[] min, int[] max) {
        int[] size = {max[0] - min[0] + 1, max[1] - min[1] + 1, max[2] - min[2] + 1};
        int flat = -1;
        int count = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (size[axis] == 1) {
                flat = axis;
                count++;
            }
        }
        if (count == 0) {
            throw new IllegalArgumentException("La selección tiene que ser plana, de un bloque de grosor (ahora mide "
                    + size[0] + "×" + size[1] + "×" + size[2] + ")");
        }
        if (count > 1) {
            throw new IllegalArgumentException("La selección es una línea (" + size[0] + "×" + size[1] + "×"
                    + size[2] + "); tiene que ser un rectángulo de un bloque de grosor");
        }
        return flat;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ImageLayout layout && x == layout.x && y == layout.y && z == layout.z
                && width == layout.width && height == layout.height && Arrays.equals(right, layout.right)
                && Arrays.equals(up, layout.up);
    }

    @Override
    public int hashCode() {
        return ((31 * Integer.hashCode(x) + y) * 31 + z) * 31 + Arrays.hashCode(right) * 7 + Arrays.hashCode(up);
    }

    @Override
    public String toString() {
        return "ImageLayout[" + x + "," + y + "," + z + " right=" + Arrays.toString(right) + " up="
                + Arrays.toString(up) + " " + width + "×" + height + "]";
    }
}
