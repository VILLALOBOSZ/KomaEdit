package com.komainu.moxia.brush.ez;

import com.komainu.moxia.brush.BrushInput;
import com.komainu.moxia.feedback.Feedback;
import com.komainu.moxia.sculpt.BrushOperation;
import com.komainu.moxia.sculpt.SculptManager;
import com.komainu.moxia.studio.net.StudioAim;
import com.sk89q.worldedit.math.BlockVector3;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pinceles de cada jugador, uno por herramienta. Los puntos del degradado y del trazo pueden ser de la herramienta
 * (PER_ITEM) o compartidos por todas las del jugador (GLOBAL), como en ezEdits; la tecla F cambia entre los dos.
 */
public final class EzBrushManager {
    public static final double REACH = 192.0;
    private static final Map<UUID, PlayerState> PLAYERS = new ConcurrentHashMap<>();

    private EzBrushManager() {
    }

    public static void bind(ServerPlayerEntity player, String toolId, EzBrush brush) {
        PlayerState state = PLAYERS.computeIfAbsent(player.getUuid(), id -> new PlayerState());
        state.tools.put(toolId, new ToolState(brush));
    }

    public static Optional<EzBrush> brush(UUID playerId, String toolId) {
        PlayerState state = PLAYERS.get(playerId);
        ToolState tool = state == null ? null : state.tools.get(toolId);
        return tool == null ? Optional.empty() : Optional.of(tool.brush);
    }

    public static boolean unbind(UUID playerId, String toolId) {
        PlayerState state = PLAYERS.get(playerId);
        return state != null && state.tools.remove(toolId) != null;
    }

    public static void remove(UUID playerId) {
        PLAYERS.remove(playerId);
    }

    public static void clearAll() {
        PLAYERS.clear();
    }

    /** Un clic con la herramienta {@code toolId}; los errores llegan al jugador como mensaje. */
    public static void handle(ServerPlayerEntity player, String toolId, BrushInput input) {
        PlayerState state = PLAYERS.get(player.getUuid());
        ToolState tool = state == null ? null : state.tools.get(toolId);
        if (tool == null) {
            player.sendMessage(message("Esta herramienta no tiene pincel; configúralo con /komaedit pincel",
                    Formatting.YELLOW), true);
            return;
        }
        try {
            tool.brush.handle(new Session(player, state, tool), input);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            player.sendMessage(message(tool.brush.type().displayName() + ": " + exception.getMessage(),
                    Formatting.RED), true);
        }
    }

    static Text message(String text, Formatting color) {
        return Feedback.prefix().append(Text.literal(text).formatted(color));
    }

    /** Puntos de un degradado. */
    public static final class Anchor {
        BlockVector3 start;
        BlockVector3 end;
    }

    private static final class PlayerState {
        private final Map<String, ToolState> tools = new HashMap<>();
        private final Anchor globalAnchor = new Anchor();
        private final List<BlockVector3> globalStroke = new ArrayList<>();
    }

    private static final class ToolState {
        private final EzBrush brush;
        private final Anchor anchor = new Anchor();
        private final List<BlockVector3> stroke = new ArrayList<>();
        private boolean global;

        private ToolState(EzBrush brush) {
            this.brush = brush;
        }
    }

    /** Lo que ve un pincel durante un clic. */
    public static final class Session {
        private final ServerPlayerEntity player;
        private final PlayerState state;
        private final ToolState tool;

        private Session(ServerPlayerEntity player, PlayerState state, ToolState tool) {
            this.player = player;
            this.state = state;
            this.tool = tool;
        }

        public ServerPlayerEntity player() {
            return player;
        }

        /** El bloque al que apunta el pincel: donde se hizo clic con el Studio abierto o, si no, el que se mira. */
        public BlockHitResult hit() {
            HitResult result = StudioAim.target(player, REACH);
            if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
                throw new IllegalArgumentException("Mira un bloque (hasta " + (int) REACH + " bloques)");
            }
            return hit;
        }

        public BlockVector3 target() {
            BlockHitResult hit = hit();
            return BlockVector3.at(hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ());
        }

        public BlockVector3 feet() {
            return BlockVector3.at(player.getBlockX(), player.getBlockY(), player.getBlockZ());
        }

        Anchor anchor() {
            return tool.global ? state.globalAnchor : tool.anchor;
        }

        List<BlockVector3> strokePoints() {
            return tool.global ? state.globalStroke : tool.stroke;
        }

        void toggleScope() {
            tool.global = !tool.global;
            info(tool.global ? "Puntos GLOBAL: compartidos por todas tus herramientas"
                    : "Puntos PER_ITEM: solo de esta herramienta");
        }

        void run(BrushOperation operation) {
            SculptManager.run(player, operation, tool.brush.preview());
        }

        void info(String text) {
            player.sendMessage(message(text, Formatting.GRAY), true);
        }
    }
}
