package me.chrr.camerapture;

import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.PictureTexture;
import me.chrr.camerapture.picture.RemotePicture;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class TextureCacheLruTest {

    @Test
    public void testThrottleTouchesUnder500ms() {
        ClientPictureStore.TextureCache cache = new ClientPictureStore.TextureCache(PictureQuality.FULL, 10_000_000L, 0L);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        PictureTexture tex1 = new PictureTexture(id1, PictureQuality.FULL);
        PictureTexture tex2 = new PictureTexture(id2, PictureQuality.FULL);
        PictureTexture tex3 = new PictureTexture(id3, PictureQuality.FULL);

        cache.put(id1, tex1);
        cache.put(id2, tex2);
        cache.put(id3, tex3);

        long t0 = System.currentTimeMillis();
        tex1.touch(t0);
        tex2.touch(t0);
        tex3.touch(t0);

        assertEquals(List.of(id1, id2, id3), cache.getOrderedKeys());

        // Touch ID1 at t0 + 600ms (> 500ms since t0) -> moves to MRU end
        cache.touch(id1, tex1, t0 + 600L);
        assertEquals(List.of(id2, id3, id1), cache.getOrderedKeys());
        assertEquals(t0 + 600L, tex1.getLastAccess());

        // Touch ID1 again at t0 + 800ms (< 500ms since tex1 last touch at t0 + 600ms)
        cache.touch(id1, tex1, t0 + 800L);
        // ID1 touch was throttled -> timestamp remains t0 + 600L
        assertEquals(t0 + 600L, tex1.getLastAccess());

        // Touch ID2 at t0 + 900ms (> 500ms since tex2 last touch at t0) -> moves ID2 to MRU end
        cache.touch(id2, tex2, t0 + 900L);
        assertEquals(List.of(id3, id1, id2), cache.getOrderedKeys());
        assertEquals(t0 + 900L, tex2.getLastAccess());
    }

    @Test
    public void testLruByteBudgetEviction() {
        // Budget = 100 bytes, grace = 0ms
        ClientPictureStore.TextureCache cache = new ClientPictureStore.TextureCache(PictureQuality.FULL, 100L, 0L);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        PictureTexture tex1 = new PictureTexture(id1, PictureQuality.FULL);
        tex1.setSize(4, 2); // 4 * 2 * 4 = 32 bytes
        tex1.setStatus(PictureTexture.Status.SUCCESS);

        PictureTexture tex2 = new PictureTexture(id2, PictureQuality.FULL);
        tex2.setSize(5, 2); // 5 * 2 * 4 = 40 bytes
        tex2.setStatus(PictureTexture.Status.SUCCESS);

        PictureTexture tex3 = new PictureTexture(id3, PictureQuality.FULL);
        tex3.setSize(6, 2); // 6 * 2 * 4 = 48 bytes
        tex3.setStatus(PictureTexture.Status.SUCCESS);

        cache.put(id1, tex1);
        assertEquals(32L, cache.getTextureBytes());

        cache.put(id2, tex2);
        assertEquals(72L, cache.getTextureBytes());

        // Adding tex3 brings total to 120 bytes > 100 bytes budget -> tex1 is evicted
        cache.put(id3, tex3);
        assertEquals(88L, cache.getTextureBytes());
        assertEquals(List.of(id2, id3), cache.getOrderedKeys());
        assertEquals(PictureTexture.Status.NOT_LOADED, tex1.getStatus());
    }

    @Test
    public void testFallbackResolutionTouchesThumbnailNotFull() {
        UUID id = UUID.randomUUID();
        RemotePicture picture = new RemotePicture(id);

        ClientPictureStore.TextureCache thumbCache = new ClientPictureStore.TextureCache(PictureQuality.THUMBNAIL, 10_000_000L, 0L);
        ClientPictureStore.TextureCache fullCache = new ClientPictureStore.TextureCache(PictureQuality.FULL, 10_000_000L, 0L);

        picture.getFull().setStatus(PictureTexture.Status.FETCHING);
        picture.getThumbnail().setStatus(PictureTexture.Status.SUCCESS);
        thumbCache.put(id, picture.getThumbnail());

        long t0 = System.currentTimeMillis();
        picture.getFull().touch(t0);
        picture.getThumbnail().touch(t0);

        // When FULL is requested, effective texture is THUMBNAIL
        PictureTexture effective = picture.getEffectiveTexture(PictureQuality.FULL);
        assertSame(picture.getThumbnail(), effective);

        // Touching effective texture touches thumbnail cache
        long t1 = t0 + 1000L;
        if (effective.getStatus() == PictureTexture.Status.SUCCESS) {
            thumbCache.touch(id, effective, t1);
        }

        // Thumbnail was touched
        assertEquals(t1, picture.getThumbnail().getLastAccess());
        // Full was NOT touched
        assertEquals(t0, picture.getFull().getLastAccess());
    }
}
