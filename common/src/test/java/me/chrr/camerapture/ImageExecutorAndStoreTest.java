package me.chrr.camerapture;

import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.PictureTexture;
import me.chrr.camerapture.picture.ServerPictureStore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class ImageExecutorAndStoreTest {

    @BeforeAll
    public static void setUp() {
        MinecraftTestBootstrap.init();
    }

    @Test
    public void testImageExecutorRunsOnWorkerThread() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Thread> workerThread = new AtomicReference<>();

        boolean submitted = Camerapture.trySubmitImageTask(() -> {
            workerThread.set(Thread.currentThread());
            latch.countDown();
        });

        assertTrue(submitted);
        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotEquals(Thread.currentThread(), workerThread.get(), "Image task must run on worker thread, never caller thread");
        assertTrue(workerThread.get().getName().startsWith("camerapture-image-"));
    }

    @Test
    public void testServerPictureStoreReservationLifecycle() {
        ServerPictureStore store = ServerPictureStore.getInstance();
        UUID id = store.reserveId();

        assertTrue(store.isReserved(id));
        assertTrue(store.unreserveId(id));
        assertFalse(store.isReserved(id));
        assertFalse(store.unreserveId(id));
    }

    @Test
    public void testPictureTextureTouchAndProperties() {
        UUID id = UUID.randomUUID();
        PictureTexture texture = new PictureTexture(id, PictureQuality.THUMBNAIL);

        assertEquals(id, texture.getId());
        assertEquals(PictureQuality.THUMBNAIL, texture.getQuality());
        assertEquals(PictureTexture.Status.NOT_LOADED, texture.getStatus());

        long t1 = 123456789L;
        texture.touch(t1);
        assertEquals(t1, texture.getLastAccess());

        long t2 = 987654321L;
        texture.touch(t2);
        assertEquals(t2, texture.getLastAccess());

        texture.setSize(64, 64);
        assertEquals(64 * 64 * 4L, texture.getTextureBytes());
    }

    @Test
    public void testRemotePictureFallbackToThumbnail() {
        UUID id = UUID.randomUUID();
        me.chrr.camerapture.picture.RemotePicture picture = new me.chrr.camerapture.picture.RemotePicture(id);

        // Initially neither is loaded
        assertEquals(PictureTexture.Status.NOT_LOADED, picture.getFull().getStatus());
        assertEquals(PictureTexture.Status.NOT_LOADED, picture.getThumbnail().getStatus());
        assertSame(picture.getFull(), picture.getEffectiveTexture(PictureQuality.FULL));

        // Set thumbnail to SUCCESS, full still FETCHING
        picture.getThumbnail().setStatus(PictureTexture.Status.SUCCESS);
        picture.getFull().setStatus(PictureTexture.Status.FETCHING);

        // Effective texture for FULL request is the ready thumbnail
        PictureTexture effective = picture.getEffectiveTexture(PictureQuality.FULL);
        assertSame(picture.getThumbnail(), effective);
        assertEquals(PictureQuality.THUMBNAIL, effective.getQuality());

        // Once full is SUCCESS, effective texture becomes FULL
        picture.getFull().setStatus(PictureTexture.Status.SUCCESS);
        assertSame(picture.getFull(), picture.getEffectiveTexture(PictureQuality.FULL));
    }
}
