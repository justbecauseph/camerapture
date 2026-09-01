package me.chrr.camerapture;

import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.ServerPictureStore;
import me.chrr.camerapture.util.ImageUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ThumbnailDeduplicationTest {

    @Test
    public void testServerPictureStoreDeduplicatesConcurrentRequests(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path dataFolder = tempDir.resolve("camerapture");
        Files.createDirectories(dataFolder);

        UUID pictureId = UUID.randomUUID();
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        byte[] fullWebpBytes = ImageUtil.compressIntoWebP(img, 0.8f);

        Path fullPath = ServerPictureStore.getFilePath(dataFolder, pictureId, PictureQuality.FULL);
        Files.write(fullPath, fullWebpBytes);

        ExecutorService ioExecutor = Executors.newFixedThreadPool(8);
        ImageTaskExecutor imageExecutor = new ImageTaskExecutor(2, 64, "test-img");
        ServerPictureStore store = new ServerPictureStore(ioExecutor, imageExecutor, 64);

        int requesterCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(requesterCount);
        List<CompletableFuture<ServerPictureStore.ThumbnailResult>> results = new ArrayList<>();

        for (int i = 0; i < requesterCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException ignored) {
                }

                CompletableFuture<ServerPictureStore.ThumbnailResult> future =
                        store.getOrGenerateThumbnailAsync(dataFolder, pictureId);
                synchronized (results) {
                    results.add(future);
                }

                future.thenAccept(res -> finishLatch.countDown());
            });
            thread.start();
        }

        // Release all 50 threads simultaneously
        startLatch.countDown();

        assertTrue(finishLatch.await(10, TimeUnit.SECONDS), "All 50 thumbnail requests must complete");

        assertEquals(50, results.size());
        ServerPictureStore.ThumbnailResult first = results.get(0).join();
        assertEquals(ServerPictureStore.ThumbnailResultType.SUCCESS, first.type());
        assertNotNull(first.picture());

        // All 50 requesters must receive the same generated StoredPicture instance
        for (int i = 1; i < requesterCount; i++) {
            ServerPictureStore.ThumbnailResult res = results.get(i).join();
            assertEquals(ServerPictureStore.ThumbnailResultType.SUCCESS, res.type());
            assertSame(first.picture(), res.picture(), "All concurrent requesters must receive the exact same generated instance");
        }

        // Persisted thumbnail must exist on disk
        Path thumbPath = ServerPictureStore.getFilePath(dataFolder, pictureId, PictureQuality.THUMBNAIL);
        Thread.sleep(150); // Allow async save to finish
        assertTrue(Files.exists(thumbPath), "Generated thumbnail must be persisted to disk");

        ioExecutor.shutdown();
        imageExecutor.shutdown();
    }

    @Test
    public void testServerPictureStoreReturnsNotFoundWhenMissing(@TempDir Path tempDir) {
        Path dataFolder = tempDir.resolve("camerapture");
        UUID missingId = UUID.randomUUID();

        ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
        ImageTaskExecutor imageExecutor = new ImageTaskExecutor(1, 10, "test-img-missing");
        ServerPictureStore store = new ServerPictureStore(ioExecutor, imageExecutor, 64);

        ServerPictureStore.ThumbnailResult result =
                store.getOrGenerateThumbnailAsync(dataFolder, missingId).join();

        assertEquals(ServerPictureStore.ThumbnailResultType.NOT_FOUND, result.type());
        assertNull(result.picture());

        ioExecutor.shutdown();
        imageExecutor.shutdown();
    }

    @Test
    public void testServerPictureStoreReturnsBusyWhenImageExecutorSaturated(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path dataFolder = tempDir.resolve("camerapture");
        Files.createDirectories(dataFolder);

        UUID pictureId = UUID.randomUUID();
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        byte[] fullWebpBytes = ImageUtil.compressIntoWebP(img, 0.8f);

        Path fullPath = ServerPictureStore.getFilePath(dataFolder, pictureId, PictureQuality.FULL);
        Files.write(fullPath, fullWebpBytes);

        ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
        // 1 worker, 1 queue slot -> saturated after 2 tasks
        ImageTaskExecutor saturatedExecutor = new ImageTaskExecutor(1, 1, "test-img-sat");
        ServerPictureStore store = new ServerPictureStore(ioExecutor, saturatedExecutor, 64);

        CountDownLatch blockerStarted = new CountDownLatch(1);
        CountDownLatch canUnblock = new CountDownLatch(1);

        try {
            // Task 1: blocks worker
            saturatedExecutor.trySubmit(() -> {
                blockerStarted.countDown();
                try {
                    canUnblock.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            });
            assertTrue(blockerStarted.await(3, TimeUnit.SECONDS));

            // Task 2: fills queue slot
            saturatedExecutor.trySubmit(() -> {});

            // Now request thumbnail generation while pool is saturated
            ServerPictureStore.ThumbnailResult result =
                    store.getOrGenerateThumbnailAsync(dataFolder, pictureId).join();

            // Must return BUSY, NOT NOT_FOUND
            assertEquals(ServerPictureStore.ThumbnailResultType.BUSY, result.type(),
                    "Worker saturation must return BUSY so client can retry rather than permanent NOT_FOUND");
            assertNull(result.picture());
        } finally {
            canUnblock.countDown();
            ioExecutor.shutdown();
            saturatedExecutor.shutdown();
        }
    }
}
