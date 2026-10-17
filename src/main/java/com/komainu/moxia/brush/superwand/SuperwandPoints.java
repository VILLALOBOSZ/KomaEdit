package com.komainu.moxia.brush.superwand;

import com.sk89q.worldedit.math.BlockVector3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lista ordenada de puntos de la supervarita. La selección de WorldEdit se reconstruye desde aquí: el primer punto
 * es el primario y los demás, secundarios. La capacidad depende del selector (dos en un cuboide o una esfera, sin
 * límite práctico en un poliedro convexo o un polígono).
 */
public final class SuperwandPoints {
    public static final int UNLIMITED = 256;

    private final List<BlockVector3> points = new ArrayList<>();

    public SuperwandPoints() {
    }

    public SuperwandPoints(List<BlockVector3> initial) {
        points.addAll(initial);
    }

    public List<BlockVector3> points() {
        return List.copyOf(points);
    }

    public int size() {
        return points.size();
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    public BlockVector3 last() {
        return points.isEmpty() ? null : points.getLast();
    }

    /** Añade un punto o, si la selección ya está llena, sustituye el último. */
    public void add(BlockVector3 point, int capacity) {
        int limit = Math.clamp(capacity, 1, UNLIMITED);
        while (points.size() > limit) {
            points.removeLast();
        }
        if (points.size() >= limit) {
            points.set(points.size() - 1, point);
        } else {
            points.add(point);
        }
    }

    public boolean removeLast() {
        if (points.isEmpty()) {
            return false;
        }
        points.removeLast();
        return true;
    }

    public void reverse() {
        Collections.reverse(points);
    }

    /** Desplaza todos los puntos una posición en la lista: el primero pasa al final. */
    public void rotate() {
        if (points.size() > 1) {
            Collections.rotate(points, -1);
        }
    }

    public void set(int index, BlockVector3 point) {
        points.set(index, point);
    }

    public BlockVector3 get(int index) {
        return points.get(index);
    }

    public void translate(int dx, int dy, int dz) {
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        for (int index = 0; index < points.size(); index++) {
            points.set(index, points.get(index).add(dx, dy, dz));
        }
    }

    public void clear() {
        points.clear();
    }

    /**
     * Punto más cercano al rayo de la mirada (sobre el centro de cada bloque), si pasa a menos de
     * {@code tolerance} bloques de él.
     *
     * @return el índice, o -1 si ninguno queda tan cerca
     */
    public int nearestToRay(double originX, double originY, double originZ, double dirX, double dirY, double dirZ,
                            double tolerance) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length < 1.0E-9) {
            return -1;
        }
        double ux = dirX / length;
        double uy = dirY / length;
        double uz = dirZ / length;
        int best = -1;
        double bestDistance = tolerance;
        double bestAlong = Double.POSITIVE_INFINITY;
        for (int index = 0; index < points.size(); index++) {
            BlockVector3 point = points.get(index);
            double px = point.x() + 0.5 - originX;
            double py = point.y() + 0.5 - originY;
            double pz = point.z() + 0.5 - originZ;
            double along = px * ux + py * uy + pz * uz;
            if (along < 0.0) {
                continue;
            }
            double cx = px - ux * along;
            double cy = py - uy * along;
            double cz = pz - uz * along;
            double distance = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (distance < bestDistance - 1.0E-9 || (Math.abs(distance - bestDistance) < 1.0E-9 && along < bestAlong)) {
                best = index;
                bestDistance = distance;
                bestAlong = along;
            }
        }
        return best;
    }

    /**
     * Punto del modo relativo: desde el último punto, {@code distance} bloques en la dirección de la mirada.
     */
    public static BlockVector3 relative(BlockVector3 last, double dirX, double dirY, double dirZ, double distance) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length < 1.0E-9) {
            return last;
        }
        double scale = distance / length;
        return BlockVector3.at(
                Math.round(last.x() + dirX * scale),
                Math.round(last.y() + dirY * scale),
                Math.round(last.z() + dirZ * scale)
        );
    }

    /** Punto que se lleva agarrado: a {@code distance} bloques del ojo en la dirección de la mirada. */
    public static BlockVector3 heldAt(double eyeX, double eyeY, double eyeZ, double dirX, double dirY, double dirZ,
                                      double distance) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length < 1.0E-9) {
            return BlockVector3.at(Math.floor(eyeX), Math.floor(eyeY), Math.floor(eyeZ));
        }
        double scale = distance / length;
        return BlockVector3.at(
                Math.floor(eyeX + dirX * scale),
                Math.floor(eyeY + dirY * scale),
                Math.floor(eyeZ + dirZ * scale)
        );
    }
}
