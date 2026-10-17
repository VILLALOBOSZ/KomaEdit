package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

/**
 * Una estructura ya orientada en el mundo: {@code mundo = ancla + giro · escala · local}.
 *
 * @param anchor   punto del mundo donde cae el origen local (centro de bloque más los desplazamientos)
 * @param rotation orientación final (alineación, giros aleatorios, simetrías, orientación y giro de entrada);
 *                 sus columnas son los ejes locales en el mundo y puede incluir una simetría
 * @param scale    bloques del mundo por unidad local en cada eje local
 * @param seed     semilla de la colocación para las estructuras con azar
 */
public record Placement(Structure structure, Vec3 anchor, Mat3 rotation, Vec3 scale, long seed) {
    public Placement {
        if (!(scale.x() > 0) || !(scale.y() > 0) || !(scale.z() > 0)) {
            throw new IllegalArgumentException("La escala de una colocación debe ser positiva");
        }
    }

    public Vec3 toWorld(Vec3 local) {
        return anchor.add(rotation.apply(local.x() * scale.x(), local.y() * scale.y(), local.z() * scale.z()));
    }

    /** Inversa de {@link #toWorld}: el giro es ortogonal, así que basta con su traspuesta. */
    public Vec3 toLocal(double x, double y, double z) {
        Vec3 relative = new Vec3(x - anchor.x(), y - anchor.y(), z - anchor.z());
        Vec3 unrotated = rotation.transpose().apply(relative);
        return new Vec3(unrotated.x() / scale.x(), unrotated.y() / scale.y(), unrotated.z() / scale.z());
    }

    /** Caja del mundo (en bloques, inclusiva) que puede tocar la estructura. */
    public int[] worldBounds() {
        Vec3 min = structure.min();
        Vec3 max = structure.max();
        double lowX = Double.POSITIVE_INFINITY;
        double lowY = Double.POSITIVE_INFINITY;
        double lowZ = Double.POSITIVE_INFINITY;
        double highX = Double.NEGATIVE_INFINITY;
        double highY = Double.NEGATIVE_INFINITY;
        double highZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 world = toWorld(new Vec3(
                    (corner & 1) == 0 ? min.x() : max.x(),
                    (corner & 2) == 0 ? min.y() : max.y(),
                    (corner & 4) == 0 ? min.z() : max.z()
            ));
            lowX = Math.min(lowX, world.x());
            lowY = Math.min(lowY, world.y());
            lowZ = Math.min(lowZ, world.z());
            highX = Math.max(highX, world.x());
            highY = Math.max(highY, world.y());
            highZ = Math.max(highZ, world.z());
        }
        // Un bloque entra si su centro (b + 0,5) cae dentro de la caja.
        return new int[]{
                (int) Math.ceil(lowX - 0.5), (int) Math.ceil(lowY - 0.5), (int) Math.ceil(lowZ - 0.5),
                (int) Math.floor(highX - 0.5), (int) Math.floor(highY - 0.5), (int) Math.floor(highZ - 0.5)
        };
    }

    /** Extensión de la caja proyectada sobre una dirección: {@code [atrás, delante]} respecto al ancla. */
    public double[] extentAlong(Vec3 direction) {
        Vec3 min = structure.min();
        Vec3 max = structure.max();
        double low = Double.POSITIVE_INFINITY;
        double high = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 world = toWorld(new Vec3(
                    (corner & 1) == 0 ? min.x() : max.x(),
                    (corner & 2) == 0 ? min.y() : max.y(),
                    (corner & 4) == 0 ? min.z() : max.z()
            )).subtract(anchor);
            double along = world.dot(direction);
            low = Math.min(low, along);
            high = Math.max(high, along);
        }
        return new double[]{low, high};
    }
}
