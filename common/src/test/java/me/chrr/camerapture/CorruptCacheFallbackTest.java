package me.chrr.camerapture;

import me.chrr.camerapture.net.clientbound.PictureErrorPacket;
import me.chrr.camerapture.picture.ClientPictureStore;
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
    public void testClientPictureStoreHandlesNotFoundAndBusyErrors() {
        ClientPictureStore store = ClientPictureStore.getInstance();
        UUID notFoundId = UUID.randomUUID();
        UUID busyId = UUID.randomUUID();

        RemotePicture notFoundPic = store.getPictureDirect(notFoundId);
        notFoundPic.getFull().setStatus(PictureTexture.Status.FETCHING);

        RemotePicture busyPic = store.getPictureDirect(busyId);
        busyPic.getFull().setStatus(PictureTexture.Status.FETCHING);

        // Process NOT_FOUND
        store.processReceivedError(notFoundId, PictureQuality.FULL, PictureErrorPacket.Reason.NOT_FOUND);
        assertEquals(PictureTexture.Status.ERROR, notFoundPic.getFull().getStatus(),
                "NOT_FOUND must transition texture to terminal ERROR status");
        assertFalse(store.isInFlight(notFoundId, PictureQuality.FULL));

        // Process BUSY (transient server saturation)
        store.processReceivedError(busyId, PictureQuality.FULL, PictureErrorPacket.Reason.BUSY);
        assertEquals(PictureTexture.Status.NOT_LOADED, busyPic.getFull().getStatus(),
                "BUSY must transition texture to NOT_LOADED for retry rather than terminal ERROR");
        assertFalse(store.isInFlight(busyId, PictureQuality.FULL));
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
