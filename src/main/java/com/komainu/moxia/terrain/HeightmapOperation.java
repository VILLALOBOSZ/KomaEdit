package com.komainu.moxia.terrain;

import com.komainu.moxia.brush.ez.BlockFilter;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.SculptManager;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.math.BlockVector3;

/**
 * Un clic del pincel de relieve sobre el camino de los pinceles ({@link SculptManager#run}): captura alrededor del
 * clic repartida entre ticks, sello en un worker y vista previa o edición por lotes con su //undo.
 */
public record HeightmapOperation(StampBrush.Settings settings, BlockVector3 center, SurfacePalette palette,
                                 BlockFilter mask, TerrainBlocks blocks) implements BrushOperation {
    @Override
    public String displayName() {
        return "Relieve " + settings.stamp().name();
    }

    @Override
    public int previewColor() {
        return 0x8BC34A;
    }

    @Override
    public Capture capture() {
        int horizontal = settings.reach() + 2;
        long side = horizontal * 2L + 1L;
        int wanted = (int) Math.ceil(settings.amplitude()) + settings.reach() + 6;
        // Lo que quepa en el límite de captura de los pinceles.
        int fits = (int) Math.max(8L, (SculptManager.MAX_OPERATION_CAPTURE / (side * side) - 1L) / 2L);
        return new Capture(center, horizontal, Math.clamp(wanted, 12, Math.min(160, fits)));
    }

    @Override
    public PackedBlockPlan plan(WorldSnapshot snapshot) {
        Capture capture = capture();
        BrushTerrainView view = new BrushTerrainView(snapshot, center, capture.horizontalRadius(), mask);
        TerrainBlocks.PlanSink sink = blocks.brush(snapshot, (int) Math.min(65_536L,
                (long) settings.reach() * settings.reach() * 4L));
        TerrainJobs.stamp(view, settings, center.x(), center.z(), palette, sink, TerrainService.CANCELLATION);
        return sink.build();
    }
}
