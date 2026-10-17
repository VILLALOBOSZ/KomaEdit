package com.komainu.moxia.recipe;

import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.ShapeParameters;
import com.komainu.moxia.shape.tube.TubeCurve;
import com.komainu.moxia.shape.tube.TubeFigures;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.Set;

public final class RecipeGeometryEngine {
    private RecipeGeometryEngine() {
    }

    public static GeometryDiagnostics.Result generate(PathSnapshot pathSnapshot, ProceduralRecipe recipe) {
        RecipeExtensions extensions = recipe.extensions();
        Set<BlockVector3> generated = extensions.needsContinuousModifier()
                ? ContinuousModifierEngine.generate(pathSnapshot, recipe)
                : generateExact(pathSnapshot, recipe.shapeParameters());
        GeometryDiagnostics.Result result = GeometryDiagnostics.process(generated, extensions);
        return withPieces(pathSnapshot, recipe, result);
    }

    /**
     * Las losas y escaleras se eligen sobre la geometría ya reparada, con la misma curva de modificadores con
     * la que se generó la figura, así encajan con lo que de verdad se va a construir.
     */
    private static GeometryDiagnostics.Result withPieces(
            PathSnapshot pathSnapshot,
            ProceduralRecipe recipe,
            GeometryDiagnostics.Result result
    ) {
        ShapeParameters parameters = recipe.shapeParameters();
        if (!TubeFigures.usesPieces(parameters)) {
            return result;
        }
        RecipeExtensions extensions = recipe.extensions();
        TubeCurve curve = extensions.needsContinuousModifier() ? extensions.tubeCurve() : TubeCurve.NONE;
        SampledPath path = SampledPath.createSmooth(pathSnapshot.points(), pathSnapshot.closed());
        TubeFigures.Smoothed smoothed = TubeFigures.smooth(path, parameters, curve, result.blocks(),
                ShapeGenerator.MAX_GENERATED_BLOCKS);
        return result.withPieces(smoothed.blocks(), smoothed.pieces());
    }

    private static Set<BlockVector3> generateExact(PathSnapshot pathSnapshot, ShapeParameters parameters) {
        boolean preserveScaleGeometry = parameters.shape() == PathShape.SCALES
                || parameters.shape() == PathShape.SPIKED_SCALES;
        SampledPath sampledPath = preserveScaleGeometry
                ? SampledPath.create(pathSnapshot.points(), pathSnapshot.closed())
                : SampledPath.createSmooth(pathSnapshot.points(), pathSnapshot.closed());
        return ShapeGenerator.generate(sampledPath, parameters);
    }

}
