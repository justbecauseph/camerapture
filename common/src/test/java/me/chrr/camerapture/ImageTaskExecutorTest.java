package me.chrr.camerapture;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ImageTaskExecutorTest {

    @Test
    public void testWorkerThreadExecution() throws InterruptedException {
        ImageTaskExecutor executor = new ImageTaskExecutor(2, 10, "test-image");
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> workerThread = new AtomicReference<>();

        try {
            boolean submitted = executor.trySubmit(() -> {
                workerThread.set(Thread.currentThread());
                latch.countDown();
            });

            assertTrue(submitted);
            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertNotEquals(Thread.currentThread(), workerThread.get(), "Task must run on worker thread");
            assertTrue(workerThread.get().getName().startsWith("test-image-"));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testQueueSaturationRejectsWithoutRunningOnCallerThread() throws InterruptedException {
        // 1 worker, 1 queue slot -> saturated after 2 tasks
        ImageTaskExecutor executor = new ImageTaskExecutor(1, 1, "test-sat");

        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch workerCanFinish = new CountDownLatch(1);
        AtomicBoolean task1Ran = new AtomicBoolean(false);
        AtomicBoolean task2Ran = new AtomicBoolean(false);
        AtomicBoolean task3Ran = new AtomicBoolean(false);
        AtomicReference<Thread> task3ExecutingThread = new AtomicReference<>();

        try {
            // Task 1: blocks worker thread
            boolean sub1 = executor.trySubmit(() -> {
                task1Ran.set(true);
                workerStarted.countDown();
                try {
                    workerCanFinish.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });
            assertTrue(sub1);
            assertTrue(workerStarted.await(3, TimeUnit.SECONDS));

            // Task 2: fills the 1 queue slot
            boolean sub2 = executor.trySubmit(() -> {
                task2Ran.set(true);
            });
            assertTrue(sub2);

            // Task 3: pool is saturated (1 busy worker + 1 queued task)
            boolean sub3 = executor.trySubmit(() -> {
                task3ExecutingThread.set(Thread.currentThread());
                task3Ran.set(true);
            });

            // Submission must return false immediately
            assertFalse(sub3, "Saturated executor must reject task submission");
            // Must NOT run synchronously on caller thread
            assertFalse(task3Ran.get(), "Rejected task must NOT execute on caller thread");
            assertNull(task3ExecutingThread.get());

            // Release worker so task 1 and task 2 can finish
            workerCanFinish.countDown();
            Thread.sleep(100);

            assertTrue(task1Ran.get());
            assertTrue(task2Ran.get());
            assertFalse(task3Ran.get(), "Rejected task must never execute");
        } finally {
            workerCanFinish.countDown();
            executor.shutdown();
        }
    }
}
