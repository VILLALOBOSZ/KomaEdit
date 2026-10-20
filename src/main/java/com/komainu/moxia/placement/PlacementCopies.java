package com.komainu.moxia.placement;

import com.komainu.moxia.shape.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Copias de una colocación ({@code place -copias}), como «Modify copies» de Axiom: cada copia {@code i} parte de la
 * anterior desplazada {@code offset}, girada {@code revolve} grados alrededor de la vertical que pasa por el pivote
 * y girada {@code twist} grados sobre sí misma (alrededor de la dirección del desplazamiento, o de la vertical si
 * no hay). Combinadas salen escaleras de caracol, coronas de columnas o hélices.
 *
 * <p>Los giros van en el sentido de //rotate (positivo: de este a sur, visto desde arriba, cuando el eje es la
 * vertical). Todas las copias son la misma estructura con la misma semilla; el voxelizado y los estados de bloque
 * siguen el mismo camino que la colocación original.</p>
 *
 * @param count  copias además de la original (0 = ninguna)
 * @param pivot  centro del giro {@code revolve}, relativo al ancla de la colocación
 */
public record PlacementCopies(int count, Vec3 offset, double revolve, Vec3 pivot, double twist) {
    public static final int MAX_COPIES = 256;
    private static final Vec3 ZERO = new Vec3(0, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    public static final PlacementCopies NONE = new PlacementCopies(0, ZERO, 0.0, ZERO, 0.0);

    public PlacementCopies {
        if (count < 0 || count > MAX_COPIES) {
            throw new IllegalArgumentException("Las copias van de 1 a " + MAX_COPIES);
        }
    }

    public boolean active() {
        return count > 0;
    }

    /** La original y sus copias, en orden. */
    public List<Placement> expand(Placement base) {
        List<Placement> placements = new ArrayList<>(count + 1);
        placements.add(base);
        Vec3 twistAxis = offset.length() > 1.0E-9 ? offset.normalized() : UP;
        Vec3 center = base.anchor().add(pivot);
        for (int copy = 1; copy <= count; copy++) {
            // Mat3.rotation sigue la mano derecha; con el signo cambiado, el giro sobre la vertical va de este a sur.
            Mat3 around = Mat3.rotation(UP, -revolve * copy);
            Mat3 self = Mat3.rotation(twistAxis, -twist * copy);
            Vec3 moved = base.anchor().add(offset.multiply(copy));
            Vec3 anchor = center.add(around.apply(moved.subtract(center)));
            Mat3 rotation = around.multiply(self).multiply(base.rotation());
            placements.add(new Placement(base.structure(), anchor, rotation, base.scale(), base.seed()));
        }
        return placements;
    }

    /** Lee {@code x,y,z} (se admiten negativos y decimales). */
    static Vec3 vector(String value, String name) {
        String[] parts = ParamText.unquote(value).split(",");
        if (parts.length != 3) {
            throw new IllegalArgumentException(name + " espera x,y,z, por ejemplo 0,1,0");
        }
        return new Vec3(ParamText.number(parts[0], -4_096, 4_096, "la x de " + name),
                ParamText.number(parts[1], -4_096, 4_096, "la y de " + name),
                ParamText.number(parts[2], -4_096, 4_096, "la z de " + name));
    }

    /** Comprueba que las banderas de copias tengan sentido juntas. */
    static PlacementCopies of(int count, Vec3 offset, double revolve, Vec3 pivot, double twist, boolean anySet) {
        if (count == 0) {
            if (anySet) {
                throw new IllegalArgumentException("Falta -copias <n>: -desplazar, -revolver, -pivote y -torcer"
                        + " dicen cómo se colocan las copias");
            }
            return NONE;
        }
        if (offset.length() < 1.0E-9 && Math.abs(revolve) < 1.0E-9 && Math.abs(twist) < 1.0E-9) {
            throw new IllegalArgumentException("Las copias caerían en el mismo sitio: añade -desplazar x,y,z,"
                    + " -revolver <grados> o -torcer <grados>");
        }
        return new PlacementCopies(count, offset, revolve, pivot, twist);
    }
}
