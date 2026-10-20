package com.komainu.moxia.nature;

import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Esqueleto de un árbol antes de convertirlo en bloques: tronco y ramas (cada una un tubo con su radio por punto),
 * raíces, grupos de hojas y celdas de frondas. El tronco va siempre el primero, así los bloques de los cruces conservan
 * su eje.
 */
final class TreeSkeleton {
    /** Un tubo con un radio en cada punto de control. */
    record Limb(List<Vec3> points, double[] radii) {
        Limb {
            points = List.copyOf(points);
            if (points.size() != radii.length) {
                throw new IllegalArgumentException("Un radio por punto");
            }
        }
    }

    /** Grupo de hojas: elipsoide con ruido alrededor de una punta de rama. */
    record Clump(Vec3 center, double radius, double flatten) {
    }

    final List<Limb> wood = new ArrayList<>();
    final List<Limb> roots = new ArrayList<>();
    final List<Clump> clumps = new ArrayList<>();
    /** Puntos de las frondas de la palmera, unidos en orden dentro de cada fronda. */
    final List<List<Vec3>> fronds = new ArrayList<>();
    /** Eje del tronco, para que las hojas colgantes salgan por fuera de la copa. */
    double axisX;
    double axisZ;
    double crownRadius;

    void limb(List<Vec3> points, double[] radii) {
        if (points.size() >= 2) {
            wood.add(new Limb(points, radii));
        }
    }
}
