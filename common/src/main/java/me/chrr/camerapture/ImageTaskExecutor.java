package me.chrr.camerapture;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/// A bounded, thread-isolated worker executor for CPU-heavy image compression and decompression.
/// Enforces AbortPolicy on queue saturation to guarantee tasks never execute on the caller thread.
public class ImageTaskExecutor {
    private final ThreadPoolExecutor executor;

    public ImageTaskExecutor(int threads, int queueCapacity, String threadPrefix) {
        AtomicInteger counter = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread thread = new Thread(runnable, threadPrefix + "-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.executor.allowCoreThreadTimeOut(true);
    }

    /// Safely attempts to submit a task to the image executor without throwing or running on caller thread.
    public boolean trySubmit(Runnable task) {
        try {
            executor.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }

    public Executor getExecutor() {
        return executor;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
