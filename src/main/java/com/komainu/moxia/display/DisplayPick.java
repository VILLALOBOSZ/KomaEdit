package com.komainu.moxia.display;

import com.komainu.moxia.gizmo.GizmoMath;
import com.komainu.moxia.shape.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Elegir un display con un rayo aunque no tenga caja de colisión: el rayo se lleva al espacio del cubo unidad de su
 * volumen ({@link DisplayVolume}) con la inversa de su transformación y se corta con el cubo. La distancia que sale es
 * la del mundo, porque una transformación afín conserva el parámetro de la recta. Gana el más cercano.
 */
public final class DisplayPick {
    private static final double PARALLEL = 1.0E-12;

    private DisplayPick() {
    }

    /** Un display que se puede elegir: su id y lo que lleva el cubo unidad al mundo. */
    public record Candidate(int id, Affine unitToWorld) {
    }

    public record Hit(int id, double distance, Vec3 point) {
    }

    /**
     * El display más cercano bajo el rayo, hasta {@code maxDistance} bloques. Un display con escala 0 en algún eje
     * (sin inversa) no se puede elegir.
     */
    public static Optional<Hit> pick(GizmoMath.Ray ray, List<Candidate> candidates, double maxDistance) {
        Hit best = null;
        for (Candidate candidate : candidates) {
            double distance = distance(ray, candidate.unitToWorld());
            if (!Double.isNaN(distance) && distance <= maxDistance && (best == null || distance < best.distance())) {
                best = new Hit(candidate.id(), distance, ray.at(distance));
            }
        }
        return Optional.ofNullable(best);
    }

    /** Distancia a la que el rayo entra en el volumen, o {@code NaN} si no lo toca. */
    public static double distance(GizmoMath.Ray ray, Affine unitToWorld) {
        Affine inverse = unitToWorld.inverse();
        if (inverse == null) {
            return Double.NaN;
        }
        Vec3 origin = inverse.apply(ray.origin());
        Vec3 direction = inverse.applyVector(ray.direction());
        double[] o = {origin.x(), origin.y(), origin.z()};
        double[] d = {direction.x(), direction.y(), direction.z()};
        double near = 0.0;
        double far = Double.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(d[axis]) < PARALLEL) {
                if (o[axis] < 0.0 || o[axis] > 1.0) {
                    return Double.NaN;
                }
                continue;
            }
            double first = (0.0 - o[axis]) / d[axis];
            double second = (1.0 - o[axis]) / d[axis];
            near = Math.max(near, Math.min(first, second));
            far = Math.min(far, Math.max(first, second));
            if (near > far) {
                return Double.NaN;
            }
        }
        return near;
    }
}
