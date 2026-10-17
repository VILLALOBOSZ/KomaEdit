package com.komainu.moxia.deform;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.SmoothPiece;
import com.komainu.moxia.shape.smooth.StairShapes;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

/**
 * Recorre una caja, pregunta al {@link DeformField} qué queda en cada bloque y lo entrega a un {@link Sink}.
 *
 * <ul>
 *     <li>No se entrega nada que no cambie: el mismo material, o aire donde ya había aire o un líquido.</li>
 *     <li>Con un perfil {@code -w}, en cada bloque se mide la ocupación de sus 8 octantes con
 *     {@link DeformField#solid} y {@link PieceFit} decide si queda entero, vacío, losa o escalera. Las piezas que
 *     aparecen en un hueco toman el material de un vecino. Las escaleras se eligen rectas y se les pone la forma que
 *     calcularía Minecraft ({@link StairShapes}).</li>
 * </ul>
 */
public final class FieldWriter {
    private static final int[][] NEIGHBOURS = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};

    private FieldWriter() {
    }

    /** Destino de los cambios: {@code piece} es {@link SmoothPiece#FULL} para un bloque entero. */
    @FunctionalInterface
    public interface Sink {
        void put(int x, int y, int z, int rawId, byte piece);
    }

    /** Recuento para el mensaje final. */
    public record Stats(int blocks, int slabs, int stairs) {
        public int total() {
            return blocks + slabs + stairs;
        }
    }

    /**
     * @param box             caja a recorrer ({@code minX, minY, minZ, maxX, maxY, maxZ}); se recorta a la captura
     * @param checkCancelled  se llama a menudo para poder cortar el trabajo
     */
    public static Stats write(
            Volume volume,
            DeformField field,
            int[] box,
            SmoothProfile profile,
            Sink sink,
            Runnable checkCancelled
    ) {
        int minimumX = Math.max(box[0], volume.minimumX);
        int minimumY = Math.max(box[1], volume.minimumY);
        int minimumZ = Math.max(box[2], volume.minimumZ);
        int maximumX = Math.min(box[3], volume.minimumX + volume.sizeX - 1);
        int maximumY = Math.min(box[4], volume.minimumY + volume.sizeY - 1);
        int maximumZ = Math.min(box[5], volume.minimumZ + volume.sizeZ - 1);
        boolean pieces = profile != null && profile.active();
        Long2ByteOpenHashMap shaped = new Long2ByteOpenHashMap();
        Long2IntOpenHashMap shapedMaterial = new Long2IntOpenHashMap();
        double[] occupancy = new double[8];
        int blocks = 0;
        for (int y = minimumY; y <= maximumY; y++) {
            for (int z = minimumZ; z <= maximumZ; z++) {
                for (int x = minimumX; x <= maximumX; x++) {
                    checkCancelled.run();
                    int material = field.material(x, y, z);
                    if (material == DeformField.KEEP) {
                        continue;
                    }
                    if (pieces) {
                        measure(field, x, y, z, occupancy);
                        byte piece = PieceFit.choose(occupancy, material != DeformField.AIR, profile.mode(),
                                profile.coverage());
                        if (piece == SmoothPiece.EMPTY) {
                            material = DeformField.AIR;
                        } else if (piece != SmoothPiece.FULL) {
                            int pieceMaterial = material != DeformField.AIR ? material : neighbour(volume, field, x, y, z);
                            if (pieceMaterial != DeformField.AIR) {
                                long key = PackedBlockPlan.pack(x, y, z);
                                shaped.put(key, piece);
                                shapedMaterial.put(key, pieceMaterial);
                                continue;
                            }
                        }
                    }
                    if (unchanged(volume, x, y, z, material)) {
                        continue;
                    }
                    sink.put(x, y, z, material, SmoothPiece.FULL);
                    blocks++;
                }
            }
        }
        int slabs = 0;
        int stairs = 0;
        if (!shaped.isEmpty()) {
            StairShapes.resolve(shaped);
            ObjectIterator<Long2ByteMap.Entry> entries = shaped.long2ByteEntrySet().fastIterator();
            while (entries.hasNext()) {
                Long2ByteMap.Entry entry = entries.next();
                long key = entry.getLongKey();
                byte piece = entry.getByteValue();
                sink.put(PackedBlockPlan.unpackX(key), PackedBlockPlan.unpackY(key), PackedBlockPlan.unpackZ(key),
                        shapedMaterial.get(key), piece);
                if (SmoothPiece.isSlab(piece)) {
                    slabs++;
                } else {
                    stairs++;
                }
            }
        }
        return new Stats(blocks, slabs, stairs);
    }

    private static boolean unchanged(Volume volume, int x, int y, int z, int material) {
        return volume.materialAt(x, y, z) == material;
    }

    private static void measure(DeformField field, int x, int y, int z, double[] occupancy) {
        for (int octant = 0; octant < 8; octant++) {
            double px = x + 0.25 + 0.5 * (octant & 1);
            double py = y + 0.25 + 0.5 * (octant >> 1 & 1);
            double pz = z + 0.25 + 0.5 * (octant >> 2 & 1);
            occupancy[octant] = field.solid(px, py, pz) ? 1.0 : 0.0;
        }
    }

    private static int neighbour(Volume volume, DeformField field, int x, int y, int z) {
        for (int[] offset : NEIGHBOURS) {
            int material = field.material(x + offset[0], y + offset[1], z + offset[2]);
            if (material > 0) {
                return material;
            }
        }
        for (int[] offset : NEIGHBOURS) {
            int material = volume.materialAt(x + offset[0], y + offset[1], z + offset[2]);
            if (material > 0) {
                return material;
            }
        }
        return DeformField.AIR;
    }
}
