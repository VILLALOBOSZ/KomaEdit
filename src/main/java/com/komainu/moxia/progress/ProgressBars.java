package com.komainu.moxia.progress;

import com.komainu.moxia.edit.BatchEditService;
import com.komainu.moxia.performance.AsyncEngine;
import com.komainu.moxia.preview.PlanPreviews;
import com.komainu.moxia.region.RegionOperationService;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bossbar de progreso de cada jugador: lectura de la selección, cálculo, preparación de la vista previa, espera de
 * confirmación y colocación, con porcentaje, bloques por segundo y tiempo restante. No hay que avisarla: cada
 * pocos ticks pregunta a los servicios del motor ({@link RegionOperationService}, {@link AsyncEngine},
 * {@link PlanPreviews}, {@link BatchEditService}) qué está haciendo cada jugador, así que cualquier operación que
 * pase por ellos la tiene. Solo aparece cuando la operación dura más de un segundo (la vista previa, al momento).
 */
public final class ProgressBars {
    private static final int UPDATE_EVERY_TICKS = 5;
    private static final long SHOW_AFTER_NANOS = 1_000_000_000L;
    private static final Map<UUID, Bar> BARS = new HashMap<>();
    private static int ticks;

    private ProgressBars() {
    }

    private static final class Bar {
        private final ServerBossBar bossBar;
        private long activeSince;
        private boolean shown;

        private Bar() {
            bossBar = new ServerBossBar(Text.literal("KomaEdit"), BossBar.Color.BLUE, BossBar.Style.PROGRESS);
            bossBar.setVisible(false);
        }
    }

    /** Lo que hace ahora el jugador, por orden: colocar, vista previa, leer, calcular. */
    public static Optional<ProgressLine> current(UUID player) {
        Optional<BatchEditService.JobStatus> job = BatchEditService.status(player);
        if (job.isPresent()) {
            BatchEditService.JobStatus status = job.get();
            return Optional.of(new ProgressLine(status.description(), ProgressLine.Phase.PLACING,
                    status.processed(), status.total(), status.elapsedNanos(), "/komaedit cancel"));
        }
        Optional<ProgressLine> preview = PlanPreviews.progress(player);
        if (preview.isPresent()) {
            return preview;
        }
        Optional<ProgressLine> reading = RegionOperationService.progress(player);
        if (reading.isPresent()) {
            ProgressLine line = reading.get();
            return Optional.of(new ProgressLine(line.title(), line.phase(), line.done(), line.total(),
                    line.elapsedNanos(), "/komaedit cancel"));
        }
        return AsyncEngine.status(player).map(task -> new ProgressLine(task.label(), ProgressLine.Phase.CALCULATING,
                task.done(), task.total(), task.elapsedNanos(), "/komaedit cancel"));
    }

    public static void tick(MinecraftServer server) {
        if (++ticks % UPDATE_EVERY_TICKS != 0) {
            return;
        }
        long now = System.nanoTime();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            Optional<ProgressLine> line = current(id);
            Bar bar = BARS.get(id);
            if (line.isEmpty()) {
                if (bar != null) {
                    bar.bossBar.clearPlayers();
                    BARS.remove(id);
                }
                continue;
            }
            if (bar == null) {
                bar = new Bar();
                bar.activeSince = now;
                BARS.put(id, bar);
            }
            ProgressLine progress = line.get();
            boolean immediate = progress.phase() == ProgressLine.Phase.WAITING;
            if (!bar.shown && !immediate && now - bar.activeSince < SHOW_AFTER_NANOS) {
                continue;
            }
            update(bar.bossBar, progress, ticks);
            if (!bar.shown || !bar.bossBar.getPlayers().contains(player)) {
                bar.bossBar.clearPlayers();
                bar.bossBar.addPlayer(player);
                bar.bossBar.setVisible(true);
                bar.shown = true;
            }
        }
        BARS.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
    }

    private static void update(ServerBossBar bossBar, ProgressLine line, int ticks) {
        bossBar.setName(Text.literal("KomaEdit · " + line.text()));
        bossBar.setColor(switch (line.phase()) {
            case READING -> BossBar.Color.BLUE;
            case CALCULATING -> BossBar.Color.PURPLE;
            case PREPARING, CHECKING -> BossBar.Color.PINK;
            case WAITING -> BossBar.Color.YELLOW;
            case PLACING -> BossBar.Color.GREEN;
        });
        // Sin medida (un cálculo que no informa), la barra va y viene para que se vea que sigue vivo.
        float percent = line.measured() ? line.fraction() : (float) Math.abs(Math.sin(ticks / 12.0));
        bossBar.setPercent(Math.clamp(percent, 0.0f, 1.0f));
    }

    public static void remove(ServerPlayerEntity player) {
        Bar bar = BARS.remove(player.getUuid());
        if (bar != null) {
            bar.bossBar.clearPlayers();
        }
    }

    public static void clearAll() {
        BARS.values().forEach(bar -> bar.bossBar.clearPlayers());
        BARS.clear();
    }
}
