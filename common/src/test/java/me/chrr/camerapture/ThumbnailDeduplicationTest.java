package me.chrr.camerapture;

import me.chrr.camerapture.picture.StoredPicture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ThumbnailDeduplicationTest {

    @Test
    public void testConcurrentThumbnailRequestsCollapseToSingleGeneration() throws InterruptedException {
        UUID pictureId = UUID.randomUUID();
        ConcurrentHashMap<UUID, CompletableFuture<StoredPicture>> inFlight = new ConcurrentHashMap<>();
        AtomicInteger generationExecutionCount = new AtomicInteger(0);

        int requesterCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requesterCount);
        List<CompletableFuture<StoredPicture>> results = new ArrayList<>();

        for (int i = 0; i < requesterCount; i++) {
            CompletableFuture<StoredPicture> requesterFuture = new CompletableFuture<>();
            results.add(requesterFuture);

            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {
                }

                // Deduplicated in-flight generator pattern
                CompletableFuture<StoredPicture> future = inFlight.computeIfAbsent(pictureId, id -> {
                    generationExecutionCount.incrementAndGet();
                    CompletableFuture<StoredPicture> genFuture = new CompletableFuture<>();

                    // Simulate async generation task
                    CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                        }
                        genFuture.complete(new StoredPicture(new byte[]{1, 2, 3, 4}));
                    });

                    genFuture.whenComplete((res, err) -> inFlight.remove(id, genFuture));
                    return genFuture;
                });

                future.thenAccept(pic -> {
                    requesterFuture.complete(pic);
                    finishLatch.countDown();
                });
            });
            thread.start();
        }

        // Release all 50 threads simultaneously
        startLatch.countDown();

        assertTrue(finishLatch.await(5, TimeUnit.SECONDS));

        // Exactly 1 generation ran
        assertEquals(1, generationExecutionCount.get(), "50 concurrent requests must trigger exactly ONE thumbnail generation");

        // All 50 requesters received the generated thumbnail
        StoredPicture firstResult = results.get(0).join();
        assertNotNull(firstResult);
        assertArrayEquals(new byte[]{1, 2, 3, 4}, firstResult.bytes());

        for (int i = 1; i < requesterCount; i++) {
            assertSame(firstResult, results.get(i).join(), "All requesters must receive the same generated instance");
        }

        // Future was removed from in-flight map upon completion
        assertFalse(inFlight.containsKey(pictureId), "In-flight map must be cleaned up after completion");
    }
}
