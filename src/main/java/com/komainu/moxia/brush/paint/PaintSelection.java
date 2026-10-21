package com.komainu.moxia.brush.paint;

import com.komainu.moxia.brush.ez.BrushMask;
import com.komainu.moxia.brush.ez.BrushWorld;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;

import java.util.List;

/**
 * Pintar la selección ({@code /komaedit pintar}) con los ajustes del pincel de pintura: un solo toque que la cubre
 * entera, y nada fuera de ella. La paleta, el modo, la fuerza, la semilla, las capas y las máscaras son las del
 * pincel. El radio, el espaciado y la semilla bloqueada no cuentan: la selección es el toque.
 *
 * <p>La forma de la huella solo cuenta con el modo Huella, que reparte la paleta del centro de la selección (primer
 * tono) a su borde (último): la huella se estira hasta llegar a la esquina más lejana. Con los demás modos el toque
 * es un cuadrado sin borde difuminado, así que pinta la selección entera con la misma fuerza.</p>
 *
 * <p>Va por el camino de los pinceles (captura repartida entre ticks, cálculo en un worker) y se aplica como una
 * orden ({@code SculptManager.runAsCommand}): con {@code -v} enseña la vista previa universal.</p>
 */
public record PaintSelection(PaintBrush brush, Region selection, PaintPainter.Dab dab, BrushMask mask,
                             PaintPainter.Settings settings, long salt, Cover cover) implements BrushOperation {

    /**
     * El toque que cubre una caja de columnas y lo que hay que leer, sin Minecraft.
     *
     * @param vertical   lo que el pintor mira por encima y por debajo del centro (cubre la caja aunque se vaya al
     *                   suelo de la columna del centro)
     * @param horizontal radio de la captura alrededor del centro: la caja y un margen para la pendiente
     * @param capture    alto de la captura por encima y por debajo del centro
     */
    public record Cover(PaintPainter.Dab dab, double hardness, int vertical, int horizontal, int capture) {
        /**
         * @param byFootprint el modo Huella: la huella elegida, estirada hasta la esquina más lejana
         * @param extent      hasta dónde llega esa huella, en radios ({@link PaintFootprint#extent})
         */
        public static Cover of(int lowX, int lowY, int lowZ, int highX, int highY, int highZ, boolean byFootprint,
                               double extent, double hardness, int layers) {
            double centerX = (lowX + highX + 1) / 2.0;
            double centerZ = (lowZ + highZ + 1) / 2.0;
            double halfX = (highX - lowX + 1) / 2.0;
            double halfZ = (highZ - lowZ + 1) / 2.0;
            double radius = byFootprint ? Math.sqrt(halfX * halfX + halfZ * halfZ) / extent
                    : Math.max(halfX, halfZ) + 0.5;
            int vertical = highY - lowY + 1 + layers + 4;
            PaintPainter.Dab dab = new PaintPainter.Dab(centerX, (lowY + highY) / 2.0 + 0.5, centerZ,
                    Math.max(0.5, radius), 0.0);
            // Lo que la huella alcance fuera de la caja no se pinta: basta con leer la caja y un margen.
            int horizontal = (int) Math.ceil(Math.max(halfX, halfZ)) + 3 + layers;
            return new Cover(dab, byFootprint ? hardness : 1.0, vertical, horizontal, vertical + layers + 4);
        }
    }

    /** El toque que cubre {@code selection}, con la máscara de la orden ya leída ({@code null} si no hay). */
    public static PaintSelection of(PaintBrush brush, Region selection, BrushMask mask) {
        Region region = selection.clone();
        BlockVector3 low = region.getMinimumPoint();
        BlockVector3 high = region.getMaximumPoint();
        boolean byFootprint = brush.config().gradient() == PaintGradient.INTENSITY;
        PaintFootprint footprint = byFootprint ? brush.footprint() : PaintFootprint.square();
        Cover cover = Cover.of(low.x(), low.y(), low.z(), high.x(), high.y(), high.z(), byFootprint,
                footprint.extent(), brush.config().hardness(), brush.config().layers());
        PaintPainter.Settings settings = brush.settings(brush.config().heightLow(), brush.config().heightHigh(),
                false, footprint, cover.hardness(), cover.vertical());
        return new PaintSelection(brush, region, cover.dab(), mask, settings, brush.stroke().salt(), cover);
    }

    @Override
    public String displayName() {
        return "Pintar la selección";
    }

    @Override
    public int previewColor() {
        return 0x9CCC65;
    }

    @Override
    public Capture capture() {
        return new Capture(BlockVector3.at((int) Math.floor(dab.x()), (int) Math.floor(dab.y()),
                (int) Math.floor(dab.z())), cover.horizontal(), cover.capture());
    }

    @Override
    public PackedBlockPlan plan(WorldSnapshot snapshot) {
        PaintBrush.Planned planned = brush.plan(snapshot, List.of(dab), new Inside(selection, mask), settings,
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

    /** Solo lo que queda dentro de la selección y cumple la máscara de la orden. */
    private record Inside(Region region, BrushMask mask) implements BrushMask {
        @Override
        public boolean matches(BrushWorld world, int x, int y, int z) {
            return region.contains(BlockVector3.at(x, y, z)) && (mask == null || mask.matches(world, x, y, z));
        }

        @Override
        public void bind(WorldSnapshot snapshot) {
            if (mask != null) {
                mask.bind(snapshot);
            }
        }
    }
}
