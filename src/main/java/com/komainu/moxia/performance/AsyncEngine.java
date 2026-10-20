package com.komainu.moxia.performance;

import com.komainu.moxia.KomaEditMod;
import com.komainu.moxia.preview.EditRequest;
import com.komainu.moxia.preview.EditRequests;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AsyncEngine {
    private static final int WORKERS = Math.clamp(Runtime.getRuntime().availableProcessors() - 1, 1, 4);
    private static final Map<UUID, Future<?>> TASKS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, TaskInfo> INFO = new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<TaskInfo> WORKER = new ThreadLocal<>();
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static volatile ThreadPoolExecutor executor;

    private AsyncEngine() {
    }

    /** Qué calcula un jugador ahora mismo, para la barra de progreso. */
    public record TaskStatus(String label, long elapsedNanos, long done, long total) {
    }

    private static final class TaskInfo {
        private final String label;
        private final long startedAt = System.nanoTime();
        private volatile long done;
        private volatile long total;

        private TaskInfo(String label) {
            this.label = label;
        }
    }

    public static synchronized void start() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "KomaEdit-Worker-" + THREAD_NUMBER.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((ignored, throwable) ->
                    KomaEditMod.LOGGER.error("Unhandled asynchronous task failure", throwable));
            return thread;
        };
        executor = new ThreadPoolExecutor(
                WORKERS,
                WORKERS,
                30L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
    }

    public static <T> void submit(
            UUID owner,
            Supplier<T> computation,
            Consumer<T> success,
            Consumer<Throwable> failure
    ) {
        submit(owner, null, computation, success, failure);
    }

    /**
     * Calcula en un worker y entrega el resultado (o el fallo) desde ese worker; un cálculo nuevo del mismo jugador
     * cancela el anterior. La {@link EditRequest} activa al lanzar viaja hasta el resultado (ver
     * {@link EditRequests#handOff}), así que la vista previa de la orden que lo lanzó llega al plan.
     *
     * @param label nombre para la barra de progreso; {@code null} usa el de la orden
     */
    public static <T> void submit(
            UUID owner,
            String label,
            Supplier<T> computation,
            Consumer<T> success,
            Consumer<Throwable> failure
    ) {
        start();
        cancel(owner);
        EditRequest request = EditRequests.current();
        TaskInfo info = new TaskInfo(label != null ? label
                : request != null && request.label() != null ? request.label() : "Cálculo");
        AtomicReference<FutureTask<Void>> reference = new AtomicReference<>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            WORKER.set(info);
            try {
                T result = computation.get();
                if (!Thread.currentThread().isInterrupted()) {
                    EditRequests.handOff(owner, request);
                    success.accept(result);
                }
            } catch (Throwable throwable) {
                if (!(throwable instanceof InterruptedException) && !Thread.currentThread().isInterrupted()) {
                    failure.accept(throwable);
                }
            } finally {
                WORKER.remove();
                TASKS.remove(owner, reference.get());
                INFO.remove(owner, info);
            }
            return null;
        });
        reference.set(task);
        TASKS.put(owner, task);
        INFO.put(owner, info);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            TASKS.remove(owner, task);
            INFO.remove(owner, info);
            failure.accept(new IllegalStateException("El motor de cálculo está ocupado; inténtalo de nuevo", exception));
        }
    }

    /**
     * Desde dentro de un cálculo: cuánto lleva, para el porcentaje de la barra. Fuera de un worker no hace nada.
     * Es opcional; sin él la barra solo cuenta el tiempo.
     */
    public static void progress(long done, long total) {
        TaskInfo info = WORKER.get();
        if (info != null) {
            info.done = done;
            info.total = total;
        }
    }

    public static Optional<TaskStatus> status(UUID owner) {
        TaskInfo info = INFO.get(owner);
        if (info == null || !isRunning(owner)) {
            return Optional.empty();
        }
        return Optional.of(new TaskStatus(info.label, System.nanoTime() - info.startedAt, info.done, info.total));
    }

    public static boolean isRunning(UUID owner) {
        Future<?> task = TASKS.get(owner);
        return task != null && !task.isDone();
    }

    public static boolean cancel(UUID owner) {
        INFO.remove(owner);
        Future<?> task = TASKS.remove(owner);
        return task != null && task.cancel(true);
    }

    public static synchronized void shutdown() {
        TASKS.values().forEach(task -> task.cancel(true));
        TASKS.clear();
        INFO.clear();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
