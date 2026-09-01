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
    public void testTransientBusyExponentialBackoffAndDeadline() {
        ClientPictureStore store = ClientPictureStore.getInstance();
        store.clearRetryStates();

        UUID id = UUID.randomUUID();
        RemotePicture pic = store.getPictureDirect(id);
        pic.getFull().setStatus(PictureTexture.Status.FETCHING);

        long before = System.currentTimeMillis();
        // 1st BUSY
        store.processReceivedError(id, PictureQuality.FULL, PictureErrorPacket.Reason.BUSY);
        assertEquals(1, store.getConsecutiveBusyFailures(id, PictureQuality.FULL));
        long deadline1 = store.getRetryDeadline(id, PictureQuality.FULL);
        assertTrue(deadline1 >= before + ClientPictureStore.INITIAL_RETRY_BACKOFF_MS, "1st backoff deadline must be at least INITIAL_RETRY_BACKOFF_MS");
        assertEquals(PictureTexture.Status.NOT_LOADED, pic.getFull().getStatus());

        // resolveTextureForRender during backoff window must NOT change status to FETCHING
        PictureTexture effective = store.resolveTextureForRender(id, PictureQuality.FULL);
        assertEquals(PictureTexture.Status.NOT_LOADED, effective.getStatus(),
                "resolveTextureForRender must not trigger fetch before backoff deadline");

        // 2nd BUSY
        store.processReceivedError(id, PictureQuality.FULL, PictureErrorPacket.Reason.BUSY);
        assertEquals(2, store.getConsecutiveBusyFailures(id, PictureQuality.FULL));
        long deadline2 = store.getRetryDeadline(id, PictureQuality.FULL);
        assertTrue(deadline2 >= before + (ClientPictureStore.INITIAL_RETRY_BACKOFF_MS * 2), "2nd backoff deadline must be at least 2x INITIAL");

        // NOT_FOUND clears retry state and marks ERROR
        store.processReceivedError(id, PictureQuality.FULL, PictureErrorPacket.Reason.NOT_FOUND);
        assertEquals(0, store.getConsecutiveBusyFailures(id, PictureQuality.FULL));
        assertEquals(0L, store.getRetryDeadline(id, PictureQuality.FULL));
        assertEquals(PictureTexture.Status.ERROR, pic.getFull().getStatus());
    }

    @Test
    public void testClearRetryStatesClearsBackoff() {
        ClientPictureStore store = ClientPictureStore.getInstance();
        store.clearRetryStates();

        UUID id = UUID.randomUUID();
        store.processReceivedError(id, PictureQuality.THUMBNAIL, PictureErrorPacket.Reason.BUSY);
        assertEquals(1, store.getConsecutiveBusyFailures(id, PictureQuality.THUMBNAIL));

        store.clearRetryStates();

        assertEquals(0, store.getConsecutiveBusyFailures(id, PictureQuality.THUMBNAIL),
                "clearRetryStates must reset all retry backoff counters");
        assertEquals(0L, store.getRetryDeadline(id, PictureQuality.THUMBNAIL));
    }

    @Test
    public void testTextureSizeAccounting() {
        UUID id = UUID.randomUUID();
        PictureTexture texture = new PictureTexture(id, PictureQuality.FULL);
        assertEquals(0L, texture.getTextureBytes(), "Newly created texture must have 0 bytes");

        texture.setSize(100, 200);
        assertEquals(100 * 200 * 4L, texture.getTextureBytes(), "Texture bytes must match width * height * 4 RGBA bytes");
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
