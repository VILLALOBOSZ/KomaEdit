package com.komainu.moxia.editor;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.edit.BlockBounds;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.path.PathEditing;
import com.komainu.moxia.path.PathHistory;
import com.komainu.moxia.path.PathHologramSync;
import com.komainu.moxia.path.PathManager;
import com.komainu.moxia.permission.KomaPermissions;
import com.komainu.moxia.progress.ProgressLine;
import com.komainu.moxia.worldedit.WorldEditService;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.regions.Region;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class EditorActionService {
    private EditorActionService() {
    }

    public static void handle(ServerPlayerEntity player, EditorActionPayload payload) {
        try {
            if (!KomaPermissions.canAccess(player)) {
                throw new IllegalArgumentException("No tienes permiso para usar el editor");
            }
            requireAction(player, payload.action());
            validatePosition(player.getServerWorld(), payload.position());
            switch (payload.action()) {
                case EditorActionPayload.PATH_ADD -> {
                    PathManager.add(player.getUuid(), payload.position());
                    PathHologramSync.sync(player);
                }
                case EditorActionPayload.PATH_MOVE -> {
                    PathManager.move(player.getUuid(), payload.index(), payload.position());
                    PathHologramSync.sync(player);
                }
                case EditorActionPayload.PATH_REMOVE -> {
                    PathManager.remove(player.getUuid(), payload.index());
                    PathHologramSync.sync(player);
                }
                case EditorActionPayload.PATH_INSERT -> {
                    PathManager.update(player.getUuid(), state -> new PathHistory.State(
                            PathEditing.insertMidpoint(state.points(), payload.index(), state.closed()),
                            state.closed()
                    ));
                    PathHologramSync.sync(player);
                    message(player, "Punto insertado tras el " + (payload.index() + 1));
                }
                case EditorActionPayload.BOX_PRIMARY -> WorldEditService.setCuboidPoint(
                        player, true, payload.position(), payload.index() < 0
                );
                case EditorActionPayload.BOX_SECONDARY -> WorldEditService.setCuboidPoint(
                        player, false, payload.position(), false
                );
                case EditorActionPayload.MAGIC_BLOCK -> {
                    WorldEditService.setCuboid(player, payload.position(), payload.position());
                    message(player, "Bloque seleccionado: " + coordinates(payload.position()));
                }
                case EditorActionPayload.MAGIC_AREA -> selectConnectedArea(player, payload.position());
                case EditorActionPayload.PAINT -> FastPaintService.dab(
                        player, payload.position(), payload.radius(), payload.pattern()
                );
                case EditorActionPayload.PAINT_BEGIN -> FastPaintService.begin(
                        player, payload.radius(), payload.pattern()
                );
                case EditorActionPayload.PAINT_END -> FastPaintService.end(player);
                case EditorActionPayload.UNDO -> message(player,
                        WorldEditService.undo(player) ? "Operación deshecha" : "No hay operaciones para deshacer");
                case EditorActionPayload.REDO -> message(player,
                        WorldEditService.redo(player) ? "Operación rehecha" : "No hay operaciones para rehacer");
                case EditorActionPayload.PAINT_SELECTION -> paintSelection(player, payload.pattern());
                case EditorActionPayload.SELECTION_REPLACE -> replaceSelection(player, payload.pattern());
                case EditorActionPayload.SELECTION_DELETE -> deleteSelection(player);
                case EditorActionPayload.CLIPBOARD_COPY -> copySelection(player);
                case EditorActionPayload.CLIPBOARD_CUT -> cutSelection(player);
                case EditorActionPayload.CLIPBOARD_PASTE -> {
                    int queued = BatchEditService.submitClipboard(
                            player,
                            WorldEditService.clipboard(player),
                            payload.position(),
                            "Pegado de portapapeles"
                    );
                    message(player, "Pegado en cola: " + queued + " posiciones");
                }
                default -> throw new IllegalArgumentException("Acción de editor desconocida");
            }
        } catch (Exception exception) {
            message(player, "Error: " + readable(exception));
        }
    }

    private static void selectConnectedArea(ServerPlayerEntity player, BlockPos origin) {
        ServerWorld world = player.getServerWorld();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        MagicArea.Result area = MagicArea.flood(origin.getX(), origin.getY(), origin.getZ(), new MagicArea.World() {
            @Override
            public boolean readable(int x, int y, int z) {
                cursor.set(x, y, z);
                return world.isInBuildLimit(cursor) && world.isChunkLoaded(cursor);
            }

            @Override
            public int state(int x, int y, int z) {
                return Block.getRawIdFromState(world.getBlockState(cursor.set(x, y, z)));
            }

            @Override
            public boolean isAir(int state) {
                return Block.getStateFromRawId(state).isAir();
            }
        });
        BlockBounds bounds = area.bounds();
        WorldEditService.setCuboid(player, new BlockPos(bounds.minimumX(), bounds.minimumY(), bounds.minimumZ()),
                new BlockPos(bounds.maximumX(), bounds.maximumY(), bounds.maximumZ()));
        message(player, "Área mágica: " + ProgressLine.group(area.blocks()) + " bloques conectados");
    }

    private static void requireAction(ServerPlayerEntity player, int action) {
        KomaPermissions.require(player, permission(action));
    }

    /** El permiso que pide cada acción del paquete. */
    static String permission(int action) {
        return switch (action) {
            case EditorActionPayload.PATH_ADD, EditorActionPayload.PATH_MOVE, EditorActionPayload.PATH_REMOVE,
                 EditorActionPayload.PATH_INSERT ->
                    KomaPermissions.PATH;
            case EditorActionPayload.PAINT, EditorActionPayload.PAINT_BEGIN, EditorActionPayload.PAINT_END ->
                    KomaPermissions.PAINT;
            case EditorActionPayload.CLIPBOARD_COPY, EditorActionPayload.CLIPBOARD_CUT,
                 EditorActionPayload.CLIPBOARD_PASTE -> KomaPermissions.CLIPBOARD;
            case EditorActionPayload.UNDO, EditorActionPayload.REDO -> KomaPermissions.HISTORY;
            // Cambian los bloques de toda la selección: construir, no seleccionar.
            case EditorActionPayload.SELECTION_REPLACE, EditorActionPayload.SELECTION_DELETE,
                 EditorActionPayload.PAINT_SELECTION -> KomaPermissions.BUILD;
            default -> KomaPermissions.SELECT;
        };
    }

    private static void paintSelection(ServerPlayerEntity player, String patternInput)
            throws IncompleteRegionException {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Region selection = context.session().getSelection(context.world()).clone();
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        int queued = BatchEditService.submitRegion(
                player,
                selection,
                block -> {
                    BlockPos position = new BlockPos(block.x(), block.y(), block.z());
                    BlockState existing = player.getServerWorld().getBlockState(position);
                    if (existing.isAir() || !existing.getFluidState().isEmpty()) {
                        return null;
                    }
                    return pattern;
                },
                "Pintura sólida de selección"
        );
        message(player, "Pintura sólida en cola: " + queued + " posiciones · Ctrl+Z al finalizar");
    }

    private static void replaceSelection(ServerPlayerEntity player, String patternInput)
            throws IncompleteRegionException {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Region selection = context.session().getSelection(context.world()).clone();
        Pattern pattern = WorldEditService.parsePatternUnchecked(context, patternInput);
        int queued = BatchEditService.submitRegion(player, selection, position -> pattern, "Reemplazo de selección");
        message(player, "Reemplazo en cola: " + queued + " posiciones");
    }

    private static void deleteSelection(ServerPlayerEntity player) throws IncompleteRegionException {
        WorldEditService.PlayerContext context = WorldEditService.context(player);
        Region selection = context.session().getSelection(context.world()).clone();
        Pattern air = WorldEditService.parsePatternUnchecked(context, "air");
        int queued = BatchEditService.submitRegion(player, selection, position -> air, "Borrado de selección");
        message(player, "Borrado en cola: " + queued + " posiciones");
    }

    private static void copySelection(ServerPlayerEntity player) throws Exception {
        long copied = WorldEditService.copySelection(player);
        message(player, "Copiado: " + ProgressLine.group(copied) + " bloques · Ctrl+V para posicionar");
    }

    private static void cutSelection(ServerPlayerEntity player) throws Exception {
        copySelection(player);
        deleteSelection(player);
    }

    private static void validatePosition(ServerWorld world, BlockPos position) {
        if (!world.isInBuildLimit(position)) {
            throw new IllegalArgumentException("El punto está fuera de los límites del mundo");
        }
    }

    private static String coordinates(BlockPos position) {
        return position.getX() + ", " + position.getY() + ", " + position.getZ();
    }

    private static void message(ServerPlayerEntity player, String message) {
        player.sendMessage(Feedback.line(Feedback.Level.INFO, message), true);
    }

    private static String readable(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
