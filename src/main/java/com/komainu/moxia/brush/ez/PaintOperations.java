package com.komainu.moxia.brush.ez;

import com.komainu.moxia.edit.PackedBlockPlan;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.WorldSnapshot;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.registry.state.Property;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;

import java.util.Objects;

/** Las pasadas de los pinceles de paleta y de follaje sobre la foto capturada. */
public final class PaintOperations {
    public static final int MAX_CHANGES = 1_000_000;

    private PaintOperations() {
    }

    /** Desplazar paleta en la esfera del pincel. */
    public record Shift(ResolvedPalette palette, PaletteShift shift, BlockVector3 center, int radius, int amount)
            implements BrushOperation {
        @Override
        public String displayName() {
            return "Desplazar paleta";
        }

        @Override
        public int previewColor() {
            return 0xC792EA;
        }

        @Override
        public Capture capture() {
            return new Capture(center, radius + 1, radius + 1);
        }

        @Override
        public PackedBlockPlan plan(WorldSnapshot snapshot) {
            SnapshotWorld world = new SnapshotWorld(snapshot);
            Collector collector = new Collector(radius);
            shift.paint(world, center.x(), center.y(), center.z(), radius, amount, (x, y, z, target) -> {
                BlockState existing = world.state(x, y, z);
                collector.put(x, y, z, existing, palette.place(target.index(), existing, target.kind()));
            });
            return collector.build();
        }
    }

    /** Degradado en la esfera del pincel, según el plano o la distancia al primer punto. */
    public record Gradient(ResolvedPalette palette, GradientPainter.Ramp ramp, GradientPainter.Settings settings,
                           BlockVector3 center, int radius) implements BrushOperation {
        @Override
        public String displayName() {
            return "Degradado";
        }

        @Override
        public int previewColor() {
            return 0xFFB74D;
        }

        @Override
        public Capture capture() {
            return new Capture(center, radius + 1, radius + 1);
        }

        @Override
        public PackedBlockPlan plan(WorldSnapshot snapshot) {
            SnapshotWorld world = new SnapshotWorld(snapshot);
            Collector collector = new Collector(radius);
            GradientPainter.paintSphere(world, center, radius, ramp, settings,
                    (x, y, z, index) -> collector.paint(world, palette, x, y, z, index));
            return collector.build();
        }
    }

    /** Degradado a lo largo de un trazo de puntos. */
    public record Stroke(ResolvedPalette palette, StrokePath path, GradientPainter.Settings settings, int radius,
                         boolean distanceToCenter) implements BrushOperation {
        @Override
        public String displayName() {
            return "Trazo de degradado";
        }

        @Override
        public int previewColor() {
            return 0xFFB74D;
        }

        @Override
        public Capture capture() {
            int centerX = Math.floorDiv(path.minimumX() + path.maximumX(), 2);
            int centerY = Math.floorDiv(path.minimumY() + path.maximumY(), 2);
            int centerZ = Math.floorDiv(path.minimumZ() + path.maximumZ(), 2);
            int horizontal = Math.max(path.maximumX() - centerX, path.maximumZ() - centerZ) + radius + 2;
            int vertical = path.maximumY() - centerY + radius + 2;
            return new Capture(BlockVector3.at(centerX, centerY, centerZ), horizontal, vertical);
        }

        @Override
        public PackedBlockPlan plan(WorldSnapshot snapshot) {
            SnapshotWorld world = new SnapshotWorld(snapshot);
            Collector collector = new Collector(radius);
            GradientPainter.paintStroke(world, path, radius, distanceToCenter, settings,
                    (x, y, z, index) -> collector.paint(world, palette, x, y, z, index));
            return collector.build();
        }
    }

    /** Pétalos, flores silvestres u hojarasca sobre el suelo de la máscara. */
    public record Petals(BlockType type, PetalPainter.Settings settings, BlockVector3 center, int radius,
                         boolean erase) implements BrushOperation {
        @Override
        public String displayName() {
            return erase ? "Quitar follaje" : "Pintor de pétalos";
        }

        @Override
        public int previewColor() {
            return 0xF48FB1;
        }

        @Override
        public Capture capture() {
            return new Capture(center, radius + 1, radius + 2);
        }

        @Override
        public PackedBlockPlan plan(WorldSnapshot snapshot) {
            SnapshotWorld world = new SnapshotWorld(snapshot);
            Collector collector = new Collector(radius);
            Property<Object> amount = amountProperty(type);
            Property<Object> facing = property(type, "facing");
            BlockState air = Objects.requireNonNull(BlockTypes.AIR).getDefaultState();
            settings.ground().bind(snapshot);
            PetalPainter.paint(world, center, radius, settings, erase, (x, y, z, pieces, direction) -> {
                BlockState existing = world.state(x, y, z);
                if (pieces == 0) {
                    collector.put(x, y, z, existing, air);
                    return;
                }
                BlockState state = existing.getBlockType().equals(type) ? existing : type.getDefaultState();
                state = with(state, amount, pieces);
                if (direction >= 0 && facing != null) {
                    state = with(state, facing, HORIZONTAL[direction]);
                }
                collector.put(x, y, z, existing, state);
            });
            return collector.build();
        }
    }

    private static final Direction[] HORIZONTAL = {Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST};

    /** Propiedad de piezas del bloque ({@code flower_amount} o {@code segment_amount}). */
    @SuppressWarnings("unchecked")
    public static Property<Object> amountProperty(BlockType type) {
        for (String name : new String[]{"flower_amount", "segment_amount"}) {
            Property<?> property = type.getPropertyMap().get(name);
            if (property != null) {
                return (Property<Object>) property;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Property<Object> property(BlockType type, String name) {
        return (Property<Object>) type.getPropertyMap().get(name);
    }

    private static BlockState with(BlockState state, Property<Object> property, Object value) {
        if (property == null || !property.getValues().contains(value)) {
            return state;
        }
        return state.with(property, value);
    }

    /** Junta los cambios en un plan, sin repetir los que ya están como deben. */
    static final class Collector {
        private final PackedBlockPlan.Builder builder;
        private int changes;

        Collector(int radius) {
            long side = radius * 2L + 1L;
            builder = PackedBlockPlan.builder((int) Math.min(65_536L, side * side * side / 4L + 16L));
        }

        void paint(SnapshotWorld world, ResolvedPalette palette, int x, int y, int z, int index) {
            BlockState existing = world.state(x, y, z);
            PaletteShift.Kind kind = world.cell(x, y, z) == BrushWorld.Cell.SHAPED
                    ? ResolvedPalette.kindOf(existing)
                    : PaletteShift.Kind.FULL;
            put(x, y, z, existing, palette.place(index, existing, kind));
        }

        void put(int x, int y, int z, BlockState existing, BlockState state) {
            if (state == null || state.equals(existing)) {
                return;
            }
            builder.addOverride(x, y, z, state);
            if (++changes > MAX_CHANGES) {
                throw new IllegalArgumentException("El pincel supera el límite de " + ProgressLine.group(MAX_CHANGES) + " bloques");
            }
        }

        PackedBlockPlan build() {
            return builder.build();
        }
    }
}
