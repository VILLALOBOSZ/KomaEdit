package com.komainu.moxia.placement;

import com.komainu.moxia.gizmo.GhostStates;
import com.komainu.moxia.gizmo.Orientation;
import com.sk89q.worldedit.extent.transform.BlockTransformExtent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.world.block.BlockState;

/**
 * Gira el estado de un bloque con la estructura que se coloca ({@code place}, {@code scatter}, {@code array}, sus
 * pinceles y los planos). Si el giro es una de las 48 orientaciones exactas va por {@link GhostStates}, lo mismo que
 * el fantasma, la simetría y el portapapeles (raíles, bisagras, puertas boca abajo, palancas de techo…); el
 * {@code BlockTransformExtent} de WorldEdit no gira la forma de los raíles ni cambia la bisagra al voltear, así que
 * solo se usa en los giros libres.
 */
public final class PlacementStates {
    private PlacementStates() {
    }

    /** La orientación exacta de un giro de colocación, o {@code null} si es libre. */
    public static Orientation exact(Mat3 rotation) {
        return Orientation.ofMatrix(new double[]{
                rotation.m00(), rotation.m01(), rotation.m02(),
                rotation.m10(), rotation.m11(), rotation.m12(),
                rotation.m20(), rotation.m21(), rotation.m22()});
    }

    /** La orientación de una base entera por filas (la de {@code brush.place.Alignment}), o {@code null}. */
    public static Orientation exact(int[] basis) {
        double[] values = new double[9];
        for (int index = 0; index < 9; index++) {
            values[index] = basis[index];
        }
        return Orientation.ofMatrix(values);
    }

    /** El giro de WorldEdit de una matriz por filas, para los giros libres. */
    public static AffineTransform affine(Mat3 rotation) {
        return new AffineTransform(
                rotation.m00(), rotation.m01(), rotation.m02(), 0,
                rotation.m10(), rotation.m11(), rotation.m12(), 0,
                rotation.m20(), rotation.m21(), rotation.m22(), 0);
    }

    /**
     * @param exact     la orientación exacta ({@link #exact}), o {@code null} si el giro es libre
     * @param transform el giro libre (solo se usa sin orientación exacta)
     */
    public static BlockState turn(BlockState state, Orientation exact, AffineTransform transform) {
        if (exact != null) {
            if (exact == Orientation.IDENTITY) {
                return state;
            }
            return FabricAdapter.adapt(GhostStates.transform(FabricAdapter.adapt(state), exact));
        }
        return BlockTransformExtent.transform(state, transform);
    }
}
