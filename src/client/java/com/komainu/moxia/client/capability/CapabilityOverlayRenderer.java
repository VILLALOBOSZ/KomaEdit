package com.komainu.moxia.client.capability;

import com.komainu.moxia.capability.BlockStates;
import com.komainu.moxia.capability.CapabilityStatePayload;
import com.komainu.moxia.capability.Cell;
import com.komainu.moxia.capability.ClickRouting;
import com.komainu.moxia.capability.LayerFinder;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Vista previa de las capacidades en el mundo: dónde caerá el bloque en el aire, la capa que se va a extruir y el
 * origen y el final de un arrastre. Solo líneas, sin bloques fantasma.
 */
public final class CapabilityOverlayRenderer {
    /** Por encima de estas celdas la capa se dibuja como una sola caja. */
    private static final int MAX_CELL_BOXES = 256;
    /** La vista previa de la capa no busca más allá de esto aunque el límite sea mayor. */
    private static final int MAX_PREVIEW_LAYER = 4096;

    private static volatile Preview preview = Preview.EMPTY;
    private static LayerKey lastLayerKey;
    private static List<Box> lastLayer = List.of();

    private record LayerKey(BlockPos pos, Direction face, BlockState state, int limit, boolean byState) {
    }

    private record Preview(Box angel, Box smearSource, Box smearTarget, List<Box> layer) {
        static final Preview EMPTY = new Preview(null, null, null, List.of());
    }

    private CapabilityOverlayRenderer() {
    }

    public static void clear() {
        preview = Preview.EMPTY;
        lastLayerKey = null;
        lastLayer = List.of();
    }

    static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || client.currentScreen != null
                || CapabilityClientState.get() == null) {
            preview = Preview.EMPTY;
            return;
        }
        ClickRouting.Context context = CapabilityControls.context(client);
        Box angel = null;
        if (ClickRouting.use(context) == ClickRouting.Intent.ANGEL) {
            BlockPos cell = CapabilityControls.angelCell(client, 1.0F);
            if (cell != null) {
                angel = new Box(cell).expand(0.01);
            }
        }
        Box source = null;
        Box target = null;
        CapabilityStatePayload state = CapabilityClientState.get();
        if (state.smearPending()) {
            source = span(state.smearFrom(), state.smearTo()).expand(0.03);
            BlockPos end = CapabilityControls.smearEnd(client, 1.0F);
            if (end != null) {
                BlockPos offset = end.subtract(state.smearAnchor());
                target = span(state.smearFrom(), state.smearTo())
                        .union(span(state.smearFrom().add(offset), state.smearTo().add(offset)))
                        .expand(0.05);
            }
        }
        List<Box> layer = List.of();
        if (ClickRouting.use(context) == ClickRouting.Intent.EXTRUDE
                && client.crosshairTarget instanceof BlockHitResult hit) {
            layer = layer(client.world, hit, state);
        }
        preview = new Preview(angel, source, target, layer);
    }

    public static void render(WorldRenderContext context) {
        Preview current = preview;
        MatrixStack matrices = context.matrixStack();
        if (current == Preview.EMPTY || matrices == null || context.consumers() == null) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
        if (current.angel() != null) {
            WorldRenderer.drawBox(matrices, lines, current.angel(), 1.0F, 1.0F, 1.0F, 0.9F);
        }
        if (current.smearSource() != null) {
            WorldRenderer.drawBox(matrices, lines, current.smearSource(), 1.0F, 0.82F, 0.2F, 1.0F);
        }
        if (current.smearTarget() != null) {
            WorldRenderer.drawBox(matrices, lines, current.smearTarget(), 0.3F, 0.9F, 1.0F, 1.0F);
        }
        for (Box box : current.layer()) {
            WorldRenderer.drawBox(matrices, lines, box, 0.35F, 1.0F, 0.5F, 0.85F);
        }
        matrices.pop();
    }

    /** Celdas donde caerá la copia de la capa, recalculadas solo si cambia el bloque, la cara o la opción. */
    private static List<Box> layer(ClientWorld world, BlockHitResult hit, CapabilityStatePayload state) {
        BlockPos pos = hit.getBlockPos();
        BlockState target = world.getBlockState(pos);
        int limit = Math.min(MAX_PREVIEW_LAYER, Math.min(state.extrudeLimit(), state.maxLayerBlocks()));
        LayerKey key = new LayerKey(pos.toImmutable(), hit.getSide(), target, limit, state.extrudeByState());
        if (key.equals(lastLayerKey)) {
            return lastLayer;
        }
        LayerFinder.Layer layer = LayerFinder.find(new LayerFinder.View() {
            @Override
            public boolean matches(int x, int y, int z) {
                BlockPos cell = new BlockPos(x, y, z);
                if (!world.isInBuildLimit(cell) || !BlockStates.loaded(world, cell)) {
                    return false;
                }
                BlockState other = world.getBlockState(cell);
                return state.extrudeByState() ? other.equals(target) : other.isOf(target.getBlock());
            }

            @Override
            public boolean free(int x, int y, int z) {
                BlockPos cell = new BlockPos(x, y, z);
                return world.isInBuildLimit(cell) && BlockStates.loaded(world, cell)
                        && world.getBlockState(cell).isReplaceable();
            }
        }, BlockStates.cell(pos), BlockStates.normal(hit.getSide()), limit);
        List<Box> boxes = new ArrayList<>();
        List<Cell> targets = LayerFinder.extruded(layer, BlockStates.normal(hit.getSide()));
        if (targets.size() <= MAX_CELL_BOXES) {
            for (Cell cell : targets) {
                boxes.add(new Box(BlockStates.pos(cell)).contract(0.04));
            }
        } else if (!targets.isEmpty()) {
            Box all = new Box(BlockStates.pos(targets.getFirst()));
            for (Cell cell : targets) {
                all = all.union(new Box(BlockStates.pos(cell)));
            }
            boxes.add(all.contract(0.04));
        }
        lastLayerKey = key;
        lastLayer = List.copyOf(boxes);
        return lastLayer;
    }

    private static Box span(BlockPos from, BlockPos to) {
        return new Box(
                Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()),
                Math.max(from.getX(), to.getX()) + 1.0, Math.max(from.getY(), to.getY()) + 1.0,
                Math.max(from.getZ(), to.getZ()) + 1.0
        );
    }
}
