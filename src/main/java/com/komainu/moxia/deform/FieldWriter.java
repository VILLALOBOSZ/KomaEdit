package com.komainu.moxia.deform;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.shape.smooth.PieceFit;
import com.komainu.moxia.shape.smooth.PieceSolver;
import com.komainu.moxia.shape.smooth.SmoothMode;
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
 *     {@link DeformField#solid} y {@link PieceFit} decide si queda entero, vacío o qué pieza del perfil lleva. En los
 *     bloques mixtos se afina con más muestras por octante (más en altura con {@code Layers}, para elegir bien el
 *     número de capas). Las piezas que aparecen en un hueco toman el material de un vecino. {@link PieceSolver}
 *     elige las esquinas de las escaleras en los perfiles 3D y les pone la forma que calcularía Minecraft
 *     ({@link StairShapes}).</li>
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

    /** Recuento para el mensaje final; {@code stairs} cuenta también muros y capas. */
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
        PieceSolver solver = new PieceSolver(pieces ? profile.mode() : SmoothMode.NONE,
                pieces ? profile.coverage() : 0.0);
        boolean fineHeight = pieces && profile.mode() == SmoothMode.LAYERS;
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
                        measure(field, x, y, z, occupancy, fineHeight);
                        byte piece = solver.choose(occupancy, material != DeformField.AIR);
                        if (piece == SmoothPiece.EMPTY) {
                            material = DeformField.AIR;
                        } else if (piece != SmoothPiece.FULL) {
                            int pieceMaterial = material != DeformField.AIR ? material : neighbour(volume, field, x, y, z);
                            if (pieceMaterial != DeformField.AIR) {
                                long key = PackedBlockPlan.pack(x, y, z);
                                solver.put(key, piece, occupancy);
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
        if (!solver.isEmpty()) {
            Long2ByteOpenHashMap shaped = solver.finish(key -> solidAfter(volume, field,
                    PackedBlockPlan.unpackX(key), PackedBlockPlan.unpackY(key), PackedBlockPlan.unpackZ(key)));
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
            // Capas sin apoyo que eran bloque: vuelven a ser el bloque entero.
            ObjectIterator<it.unimi.dsi.fastutil.longs.Long2IntMap.Entry> materials =
                    shapedMaterial.long2IntEntrySet().fastIterator();
            while (materials.hasNext()) {
                var entry = materials.next();
                long key = entry.getLongKey();
                if (shaped.containsKey(key) || solver.dropped().contains(key)) {
                    continue;
                }
                int x = PackedBlockPlan.unpackX(key);
                int y = PackedBlockPlan.unpackY(key);
                int z = PackedBlockPlan.unpackZ(key);
                if (!unchanged(volume, x, y, z, entry.getIntValue())) {
                    sink.put(x, y, z, entry.getIntValue(), SmoothPiece.FULL);
                    blocks++;
                }
            }
        }
        return new Stats(blocks, slabs, stairs);
    }

    /** Si tras la escritura queda un bloque en la posición (sin contar las piezas). */
    private static boolean solidAfter(Volume volume, DeformField field, int x, int y, int z) {
        int material = field.material(x, y, z);
        if (material == DeformField.KEEP) {
            material = volume.materialAt(x, y, z);
        }
        return material != DeformField.AIR;
    }

    private static boolean unchanged(Volume volume, int x, int y, int z, int material) {
        return volume.materialAt(x, y, z) == material;
    }

    /**
     * Centro de cada octante y, si el bloque es mixto, una rejilla de 2×2×2 muestras por octante (2×4×2 con
     * {@code fineHeight}, cuartos de octante en altura para las capas).
     */
    static void measure(DeformField field, int x, int y, int z, double[] occupancy, boolean fineHeight) {
        int inside = 0;
        for (int octant = 0; octant < 8; octant++) {
            double px = x + 0.25 + 0.5 * (octant & 1);
            double py = y + 0.25 + 0.5 * (octant >> 1 & 1);
            double pz = z + 0.25 + 0.5 * (octant >> 2 & 1);
            boolean solid = field.solid(px, py, pz);
            occupancy[octant] = solid ? 1.0 : 0.0;
            if (solid) {
                inside++;
            }
        }
        // Un bloque lleno solo se afina para las capas y si es la cara de arriba (encima hay hueco).
        if (inside == 0 || inside == 8 && (!fineHeight || field.solid(x + 0.5, y + 1.25, z + 0.5))) {
            return;
        }
        int levels = fineHeight ? 4 : 2;
        for (int octant = 0; octant < 8; octant++) {
            double baseX = x + 0.5 * (octant & 1);
            double baseY = y + 0.5 * (octant >> 1 & 1);
            double baseZ = z + 0.5 * (octant >> 2 & 1);
            int hits = 0;
            for (int level = 0; level < levels; level++) {
                double py = baseY + (level + 0.5) * 0.5 / levels;
                for (int sub = 0; sub < 4; sub++) {
                    if (field.solid(baseX + 0.125 + 0.25 * (sub & 1), py, baseZ + 0.125 + 0.25 * (sub >> 1))) {
                        hits++;
                    }
                }
            }
            occupancy[octant] = hits / (4.0 * levels);
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
