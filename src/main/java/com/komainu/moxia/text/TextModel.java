package com.komainu.moxia.text;

import com.komainu.moxia.deform.DeformField;
import com.komainu.moxia.deform.FieldWriter;
import com.komainu.moxia.deform.SmoothProfile;
import com.komainu.moxia.deform.Volume;
import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.region.RegionSnapshot;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

/**
 * El texto en 3D: letras y borde extruidos {@code depth} bloques sobre el plano de {@link TextPlacement}. Se ve
 * como un {@link DeformField} para que {@link FieldWriter} ponga las losas y escaleras de {@code -w} igual que en el
 * resto de comandos.
 *
 * <p>Cada bloque de letra recibe una <em>capa</em>: un índice de la paleta (según el degradado) o la del borde. El
 * texto nunca quita bloques: donde no hay letra se deja lo que hubiera, y las piezas de suavizado solo aparecen en
 * huecos (aire o líquido).</p>
 */
public final class TextModel {
    /** Los ids de capa empiezan aquí para no confundirse con los ids crudos de la foto. */
    public static final int LAYER_BASE = 1 << 24;

    private final TextRaster glyphs;
    private final TextRaster border;
    private final TextPlacement placement;
    private final int depth;
    private final int paletteSize;
    private final Gradient gradient;
    private final boolean borderOwnMaterial;
    private final CellFilter filter;
    private final int minU;
    private final int minV;
    private final int maxU;
    private final int maxV;

    /** Sentido del degradado de la paleta. */
    public enum Gradient {
        /** De abajo arriba del texto. */
        VERTICAL,
        /** De izquierda a derecha. */
        HORIZONTAL
    }

    /** Qué posiciones del mundo puede tocar el texto. */
    public interface CellFilter {
        CellFilter ALL = (x, y, z) -> true;

        /** La máscara: dónde se pueden poner letras. */
        boolean allowed(int x, int y, int z);
    }

    /**
     * @param border            anillo del borde o {@code null}
     * @param paletteSize       entradas de la paleta de las letras (1 para un solo material)
     * @param borderOwnMaterial si el borde tiene material propio (capa {@code paletteSize}); si no, usa la paleta
     */
    public TextModel(
            TextRaster glyphs,
            TextRaster border,
            TextPlacement placement,
            int depth,
            int paletteSize,
            Gradient gradient,
            boolean borderOwnMaterial,
            CellFilter filter
    ) {
        if (depth < 1 || depth > 64) {
            throw new IllegalArgumentException("El grosor va de 1 a 64 bloques");
        }
        if (paletteSize < 1) {
            throw new IllegalArgumentException("La paleta está vacía");
        }
        this.glyphs = glyphs;
        this.border = border;
        this.placement = placement;
        this.depth = depth;
        this.paletteSize = paletteSize;
        this.gradient = gradient == null ? Gradient.VERTICAL : gradient;
        this.borderOwnMaterial = borderOwnMaterial && border != null;
        this.filter = filter == null ? CellFilter.ALL : filter;
        TextRaster outer = border != null ? border : glyphs;
        this.minU = outer.minU();
        this.minV = outer.minV();
        this.maxU = outer.maxU();
        this.maxV = outer.maxV();
    }

    /** Capas que usa el texto: las de la paleta y, si lo tiene, la del borde. */
    public int layers() {
        return paletteSize + (borderOwnMaterial ? 1 : 0);
    }

    public int borderLayer() {
        return paletteSize;
    }

    /** Caja del mundo que ocupa el texto. */
    public int[] worldBox() {
        return placement.box(minU, minV, maxU, maxV, depth);
    }

    /** Capa de un bloque del plano del texto, o -1 si no lleva letra ni borde. */
    public int layerAt(int u, int v) {
        double letter = glyphs.coverage(u, v);
        double ring = border == null ? 0.0 : border.coverage(u, v);
        if (letter + ring < 0.5) {
            return -1;
        }
        if (ring > letter && borderOwnMaterial) {
            return borderLayer();
        }
        return paletteIndex(u, v);
    }

    private int paletteIndex(int u, int v) {
        if (paletteSize == 1) {
            return 0;
        }
        double t = gradient == Gradient.VERTICAL
                ? (v - minV) / (double) Math.max(1, maxV - minV)
                : (u - minU) / (double) Math.max(1, maxU - minU);
        return Math.min(paletteSize - 1, (int) Math.floor(t * paletteSize));
    }

    private boolean partial(int u, int v) {
        return glyphs.coverage(u, v) > 0.0 || border != null && border.coverage(u, v) > 0.0;
    }

    /** Si el punto del mundo queda dentro de una letra o del borde. */
    public boolean solid(double x, double y, double z) {
        double[] local = placement.local(x, y, z);
        if (local[2] < 0.0 || local[2] >= depth) {
            return false;
        }
        return glyphs.solid(local[0], local[1]) || border != null && border.solid(local[0], local[1]);
    }

    /** Resultado: bloques a colocar con su capa y su pieza, y el id leído para la escritura condicional. */
    public record Result(LongArrayList positions, IntArrayList layers, ByteArrayList pieces, IntArrayList expected,
                         int slabs, int stairs) {
        public int size() {
            return positions.size();
        }

        public int full() {
            return size() - slabs - stairs;
        }
    }

    /**
     * Calcula los bloques del texto sobre la foto de su caja. Las piezas de {@code profile} solo van en huecos y el
     * texto no quita nada.
     */
    public Result plan(RegionSnapshot snapshot, SmoothProfile profile, Runnable checkCancelled) {
        Volume volume = Volume.of(snapshot);
        DeformField bound = new DeformField() {
            @Override
            public int material(int x, int y, int z) {
                int[] local = placement.local(x, y, z);
                if (local[2] < 0 || local[2] >= depth) {
                    return DeformField.KEEP;
                }
                int layer = layerAt(local[0], local[1]);
                if (layer >= 0) {
                    return filter.allowed(x, y, z) ? LAYER_BASE + layer : DeformField.KEEP;
                }
                // Junto a la letra, en un hueco, puede aparecer una losa o una escalera.
                boolean empty = snapshot.isAir(x, y, z) || snapshot.isLiquid(x, y, z);
                return empty && partial(local[0], local[1]) && filter.allowed(x, y, z)
                        ? DeformField.AIR : DeformField.KEEP;
            }

            @Override
            public boolean solid(double x, double y, double z) {
                return TextModel.this.solid(x, y, z);
            }
        };
        LongArrayList positions = new LongArrayList();
        IntArrayList layers = new IntArrayList();
        ByteArrayList pieces = new ByteArrayList();
        IntArrayList expected = new IntArrayList();
        int[] counts = new int[2];
        FieldWriter.write(volume, bound, worldBox(), profile, (x, y, z, id, piece) -> {
            if (id < LAYER_BASE) {
                // Aire (el texto no borra) o el material de un vecino del mundo: no se toca.
                return;
            }
            positions.add(PackedBlockPlan.pack(x, y, z));
            layers.add(id - LAYER_BASE);
            pieces.add(piece);
            expected.add(snapshot.rawId(x, y, z));
            if (piece != SmoothPiece.FULL) {
                counts[SmoothPiece.isSlab(piece) ? 0 : 1]++;
            }
        }, checkCancelled);
        return new Result(positions, layers, pieces, expected, counts[0], counts[1]);
    }
}
