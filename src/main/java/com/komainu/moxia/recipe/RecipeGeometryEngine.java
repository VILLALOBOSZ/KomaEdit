package com.komainu.moxia.recipe;

import com.komainu.moxia.path.PathSnapshot;
import com.komainu.moxia.shape.PathShape;
import com.komainu.moxia.shape.NaturalAssetGenerator;
import com.komainu.moxia.shape.SampledPath;
import com.komainu.moxia.shape.ShapeGenerator;
import com.komainu.moxia.shape.ShapeParameters;
import com.sk89q.worldedit.math.BlockVector3;

import java.util.Set;

public final class RecipeGeometryEngine {
    private RecipeGeometryEngine() {
    }

    public static GeometryDiagnostics.Result generate(PathSnapshot pathSnapshot, ProceduralRecipe recipe) {
        RecipeExtensions extensions = recipe.extensions();
        if (recipe.shape().isAsset()) {
            var geometry = NaturalAssetGenerator.generate(pathSnapshot, recipe.shapeParameters());
            return GeometryDiagnostics.process(geometry.blocks(), geometry.accentBlocks(), extensions);
        }
        Set<BlockVector3> generated = extensions.needsContinuousModifier()
                ? ContinuousModifierEngine.generate(pathSnapshot, recipe)
                : generateExact(pathSnapshot, recipe.shapeParameters());
        return GeometryDiagnostics.process(generated, extensions);
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
