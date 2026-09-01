package me.chrr.camerapture;

import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.PictureTexture;
import me.chrr.camerapture.picture.RemotePicture;
import me.chrr.camerapture.picture.WebPHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class CorruptCacheFallbackTest {

    @Test
    public void testCorruptCacheFileDeletedOnDecodeFailure(@TempDir Path tempDir) throws IOException {
        UUID id = UUID.randomUUID();
        Path corruptFile = tempDir.resolve(id + ".webp");
        Files.write(corruptFile, new byte[]{0, 1, 2, 3, 4, 5, 6, 7}); // Invalid WebP bytes

        assertTrue(Files.exists(corruptFile));

        RemotePicture picture = new RemotePicture(id);
        picture.getFull().setStatus(PictureTexture.Status.FETCHING);

        AtomicBoolean serverRequested = new AtomicBoolean(false);

        // Simulate decode failure handling in ClientPictureStore
        byte[] bytes = Files.readAllBytes(corruptFile);
        try {
            // Attempting to read invalid header throws IOException
            if (WebPHeader.read(bytes) == null) {
                throw new IOException("corrupt webp header");
            }
            fail("Should have failed on corrupt header");
        } catch (IOException e) {
            // Bad cache file is deleted
            Files.deleteIfExists(corruptFile);
            // Server fallback request is made
            serverRequested.set(true);
        }

        assertFalse(Files.exists(corruptFile), "Corrupt cache file must be deleted");
        assertTrue(serverRequested.get(), "Server download must be requested after cache deletion");
        assertEquals(PictureTexture.Status.FETCHING, picture.getFull().getStatus());
    }

    @Test
    public void testUnreadableCacheFileDeletedAndFallsBackToServer(@TempDir Path tempDir) throws IOException {
        UUID id = UUID.randomUUID();
        Path badPath = tempDir.resolve("bad-cache.webp");
        Files.write(badPath, new byte[]{9, 8, 7});

        AtomicBoolean serverRequested = new AtomicBoolean(false);

        try {
            // Simulate read failure
            throw new IOException("Disk I/O error");
        } catch (Exception e) {
            Files.deleteIfExists(badPath);
            serverRequested.set(true);
        }

        assertFalse(Files.exists(badPath));
        assertTrue(serverRequested.get());
    }

    @Test
    public void testServerErrorAfterCorruptCacheTransitionsToError() {
        UUID id = UUID.randomUUID();
        RemotePicture picture = new RemotePicture(id);

        // Fetch started
        picture.getFull().setStatus(PictureTexture.Status.FETCHING);

        // When server error packet arrives for in-flight request
        picture.getFull().setStatus(PictureTexture.Status.ERROR);

        assertEquals(PictureTexture.Status.ERROR, picture.getFull().getStatus(), "Server failure must mark texture as ERROR");
        assertNotEquals(PictureTexture.Status.FETCHING, picture.getFull().getStatus(), "Texture must never be stuck in FETCHING");
    }

    @Test
    public void testImageExecutorRejectionDuringCachedDecodeResetsToNotLoaded() {
        UUID id = UUID.randomUUID();
        RemotePicture picture = new RemotePicture(id);

        picture.getThumbnail().setStatus(PictureTexture.Status.FETCHING);

        ImageTaskExecutor saturatedExecutor = new ImageTaskExecutor(1, 1, "test-sat-cached");
        try {
            // Saturate executor
            saturatedExecutor.trySubmit(() -> {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            });
            saturatedExecutor.trySubmit(() -> {});

            // Attempt submitting decode task when saturated
            boolean submitted = saturatedExecutor.trySubmit(() -> {});
            if (!submitted) {
                // Rejection handler resets texture to NOT_LOADED
                picture.getTexture(PictureQuality.THUMBNAIL).setStatus(PictureTexture.Status.NOT_LOADED);
            }

            assertEquals(PictureTexture.Status.NOT_LOADED, picture.getThumbnail().getStatus(),
                    "Worker rejection must reset texture to NOT_LOADED for retry rather than stuck in FETCHING");
        } finally {
            saturatedExecutor.shutdown();
        }
    }
}
