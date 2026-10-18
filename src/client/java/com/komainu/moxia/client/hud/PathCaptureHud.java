package com.komainu.moxia.client.hud;

import com.komainu.moxia.client.gui.ProceduralStudioScreen;
import com.komainu.moxia.path.PathHologramPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class PathCaptureHud {
    private static final int BACKGROUND = 0xFF14080B;
    private static final int BORDER = 0xFFE0445E;
    private static final int STATUS_BACKGROUND = 0xFF220B11;
    private static final int STATUS_ACTIVE = 0xFFFF6B82;
    private static final int[] POINT_COLORS = {
            0xFFFF5C78,
            0xFFFF8FA3,
            0xFFFFB3C1,
            0xFFE0445E,
            0xFFFF7A59,
            0xFFD66A8A,
            0xFFFFA36C,
            0xFFC94A63
    };
    private static volatile State state = State.clear();

    private PathCaptureHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(PathCaptureHud::render);
    }

    public static void update(PathHologramPayload payload) {
        if (!payload.active()) {
            clear();
            return;
        }
        List<HudLine> lines = new ArrayList<>(payload.points().size());
        for (int index = 0; index < payload.points().size(); index++) {
            BlockPos point = payload.points().get(index);
            String prefix = index == payload.points().size() - 1 ? "◆" : "◇";
            lines.add(new HudLine(
                    Text.literal(String.format(
                            "%s %02d   %d, %d, %d",
                            prefix,
                            index + 1,
                            point.getX(),
                            point.getY(),
                            point.getZ()
                    )),
                    POINT_COLORS[index % POINT_COLORS.length]
            ));
        }
        int count = payload.points().size();
        state = new State(
                true,
                Text.translatable(payload.closed() ? "hud.komaedit.path.status.closed" : "hud.komaedit.path.status",
                        count),
                Text.translatable(payload.closed() ? "hud.komaedit.path.closed" : "hud.komaedit.path.open"),
                Text.translatable("hud.komaedit.path.hint"),
                List.copyOf(lines),
                lines.size() > 16 ? 2 : 1
        );
    }

    public static void clear() {
        state = State.clear();
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        State current = state;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!current.active() || client.player == null || client.options.hudHidden) {
            return;
        }

        if (client.currentScreen instanceof ProceduralStudioScreen) {
            // El Studio enseña los nodos en su etapa Trayectoria: aquí solo taparía el mundo.
            return;
        }

        int windowWidth = client.getWindow().getScaledWidth();
        int windowHeight = client.getWindow().getScaledHeight();
        int statusWidth = client.textRenderer.getWidth(current.status()) + 20;
        int statusLeft = (windowWidth - statusWidth) / 2;
        int statusTop = windowHeight - 69;
        context.fill(statusLeft - 1, statusTop - 1, statusLeft + statusWidth + 1, statusTop + 14, BORDER);
        context.fill(statusLeft, statusTop, statusLeft + statusWidth, statusTop + 13, STATUS_BACKGROUND);
        context.drawTextWithShadow(client.textRenderer, current.status(), statusLeft + 10, statusTop + 3, STATUS_ACTIVE);

        int rows = Math.max(1, (current.lines().size() + current.columns() - 1) / current.columns());
        int cardWidth = current.columns() * 184 + 12;
        int cardHeight = 36 + rows * 11 + 17;
        context.fill(7, 7, 7 + cardWidth, 7 + cardHeight, BORDER);
        context.fill(8, 8, 6 + cardWidth, 6 + cardHeight, BACKGROUND);
        context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.komaedit.path.title"), 14, 13, 0xFFFF6B82);
        context.drawTextWithShadow(client.textRenderer, current.pathType(), 14, 25, 0xFFD3A1AA);

        for (int index = 0; index < current.lines().size(); index++) {
            int column = index / 16;
            int row = index % 16;
            HudLine line = current.lines().get(index);
            context.drawTextWithShadow(
                    client.textRenderer,
                    line.text(),
                    14 + column * 184,
                    42 + row * 11,
                    line.color()
            );
        }
        context.drawTextWithShadow(
                client.textRenderer,
                current.hint(),
                14,
                42 + rows * 11 + 3,
                0xFFB98992
        );
    }

    private record HudLine(Text text, int color) {
    }

    private record State(
            boolean active,
            Text status,
            Text pathType,
            Text hint,
            List<HudLine> lines,
            int columns
    ) {
        private static State clear() {
            return new State(false, Text.empty(), Text.empty(), Text.empty(), List.of(), 1);
        }
    }
}
