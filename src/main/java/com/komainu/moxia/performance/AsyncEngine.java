package com.komainu.moxia.performance;

import com.komainu.moxia.KomaEditMod;

import java.util.Map;
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
    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();
    private static volatile ThreadPoolExecutor executor;

    private AsyncEngine() {
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
        start();
        cancel(owner);
        AtomicReference<FutureTask<Void>> reference = new AtomicReference<>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                T result = computation.get();
                if (!Thread.currentThread().isInterrupted()) {
                    success.accept(result);
                }
            } catch (Throwable throwable) {
                if (!(throwable instanceof InterruptedException) && !Thread.currentThread().isInterrupted()) {
                    failure.accept(throwable);
                }
            } finally {
                TASKS.remove(owner, reference.get());
            }
            return null;
        });
        reference.set(task);
        TASKS.put(owner, task);
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            TASKS.remove(owner, task);
            failure.accept(new IllegalStateException("El motor de cálculo está ocupado; inténtalo de nuevo", exception));
        }
    }

    public static boolean isRunning(UUID owner) {
        Future<?> task = TASKS.get(owner);
        return task != null && !task.isDone();
    }

    public static boolean cancel(UUID owner) {
        Future<?> task = TASKS.remove(owner);
        return task != null && task.cancel(true);
    }

    public static synchronized void shutdown() {
        TASKS.values().forEach(task -> task.cancel(true));
        TASKS.clear();
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
