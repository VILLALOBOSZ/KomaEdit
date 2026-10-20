package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;

import java.util.List;

/**
 * Un toque suelto del pincel de pintura por el camino común de los pinceles ({@code SculptManager.run}): lo usa la
 * vista previa en cada clic ({@code -p}), que enseña el holograma y espera a {@code /komaedit pincel aplicar}. Sin
 * vista previa, los clics van a {@link PaintStrokes}, que deja arrastrar.
 */
public record PaintOperation(PaintBrush brush, PaintPainter.Dab dab, BrushMask mask, PaintPainter.Settings settings,
                             long salt) implements BrushOperation {
    /** El toque del bloque pulsado, con la semilla y la cuenta del pincel. */
    public static PaintOperation single(PaintBrush brush, BlockHitResult hit, boolean invert,
                                        ServerPlayerEntity player) {
        PaintStroke stroke = brush.stroke();
        PaintPainter.Dab dab = stroke.add(hit.getBlockPos().getX() + 0.5, hit.getBlockPos().getY() + 0.5,
                hit.getBlockPos().getZ() + 0.5).getFirst();
        brush.advance(stroke);
        double low = brush.config().heightLow();
        double high = brush.config().heightHigh();
        if (brush.config().gradient() == PaintGradient.HEIGHT && brush.config().autoHeights()) {
            double[] range = PaintStrokes.survey(player.getServerWorld(), hit.getBlockPos().getX(),
                    hit.getBlockPos().getZ(), hit.getBlockPos().getY());
            low = range[0];
            high = range[1];
        }
        return new PaintOperation(brush, dab, brush.mask(player), brush.settings(low, high, invert), stroke.salt());
    }

    @Override
    public String displayName() {
        return "Pintar";
    }

    @Override
    public int previewColor() {
        return 0x9CCC65;
    }

    @Override
    public Capture capture() {
        int layers = brush.config().layers();
        return new Capture(BlockVector3.at((int) Math.floor(dab.x()), (int) Math.floor(dab.y()),
                (int) Math.floor(dab.z())),
                PaintPainter.reach(dab, brush.footprint()) + 3 + layers, brush.vertical() + layers + 2);
    }

    @Override
    public PackedBlockPlan plan(WorldSnapshot snapshot) {
        PaintBrush.Planned planned = brush.plan(snapshot, List.of(dab), mask, settings,
                new PaintPainter.Memory(salt), () -> {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Cálculo cancelado");
            }
        });
        PackedBlockPlan.Builder builder = PackedBlockPlan.builder(Math.max(16, planned.size()));
        for (int index = 0; index < planned.size(); index++) {
            long key = planned.positions()[index];
            builder.addOverride(PaintPainter.unpackX(key), PaintPainter.unpackY(key), PaintPainter.unpackZ(key),
                    planned.states()[index]);
        }
        return builder.build();
    }
}
