package me.chrr.camerapture.picture;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.CameraptureClient;
import me.chrr.camerapture.net.clientbound.PictureErrorPacket;
import me.chrr.camerapture.net.serverbound.RequestDownloadPacket;
import me.chrr.camerapture.render.CameraptureDebugStats;
import me.chrr.camerapture.util.ImageUtil;
import me.chrr.camerapture.util.NativeImageUtil;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/// The client-side picture store. It manages picture resources on the client side,
/// separating Full and Thumbnail texture caches with independent VRAM budgets, grace periods,
/// and LRU tracking.
public class ClientPictureStore {
    private static final Logger LOGGER = LogManager.getLogger("Camerapture/ClientPictureStore");
    private static final ClientPictureStore INSTANCE = new ClientPictureStore();

    /// An absolute ceiling on what we'll decode.
    private static final int ABSOLUTE_MAX_RESOLUTION = 8192;
    private static final int ABSOLUTE_MAX_THUMBNAIL_RESOLUTION = 512;

    private final Queue<QueuedBytes> byteQueue = new ConcurrentLinkedQueue<>();
    private final Map<UUID, RemotePicture> pictures = new ConcurrentHashMap<>();
    private final Set<PictureKey> inFlightNetworkRequests = ConcurrentHashMap.newKeySet();

    private final TextureCache fullCache = new TextureCache(PictureQuality.FULL);
    private final TextureCache thumbnailCache = new TextureCache(PictureQuality.THUMBNAIL);

    private ClientPictureStore() {
    }

    TextureCache getCache(PictureQuality quality) {
        return (quality == PictureQuality.THUMBNAIL) ? thumbnailCache : fullCache;
    }

    public boolean isInFlight(UUID id, PictureQuality quality) {
        return inFlightNetworkRequests.contains(new PictureKey(id, quality));
    }

    public RemotePicture getPictureDirect(UUID id) {
        return pictures.computeIfAbsent(id, RemotePicture::new);
    }

    /// Resolve the effective rendering texture for a picture, requesting download if not yet loaded
    /// and touching the active texture in LRU cache with epoch/time throttling.
    public PictureTexture resolveTextureForRender(@NotNull UUID id, @NotNull PictureQuality preferred) {
        RemotePicture picture = pictures.computeIfAbsent(id, RemotePicture::new);
        PictureTexture preferredTexture = picture.getTexture(preferred);

        if (preferredTexture.getStatus() == PictureTexture.Status.NOT_LOADED) {
            preferredTexture.setStatus(PictureTexture.Status.FETCHING);
            fetchPicture(id, preferred);
        }

        PictureTexture effective = picture.getEffectiveTexture(preferred);
        if (effective.getStatus() == PictureTexture.Status.SUCCESS) {
            long now = System.currentTimeMillis();
            getCache(effective.getQuality()).touch(id, effective, now);
        }

        return effective;
    }

    /// Resolve a RemotePicture and its effective rendering texture.
    public ResolvedPicture resolveForRender(@NotNull UUID id, @NotNull PictureQuality preferred) {
        RemotePicture picture = pictures.computeIfAbsent(id, RemotePicture::new);
        PictureTexture effective = resolveTextureForRender(id, preferred);
        return new ResolvedPicture(picture, effective);
    }

    /// Retrieve or create a RemotePicture entry and request the specified quality if not yet loaded.
    public RemotePicture getPicture(@NotNull UUID id, @NotNull PictureQuality quality) {
        RemotePicture picture = pictures.computeIfAbsent(id, RemotePicture::new);
        PictureTexture texture = picture.getTexture(quality);
        if (texture.getStatus() == PictureTexture.Status.NOT_LOADED) {
            texture.setStatus(PictureTexture.Status.FETCHING);
            fetchPicture(id, quality);
        } else if (texture.getStatus() == PictureTexture.Status.SUCCESS) {
            long now = System.currentTimeMillis();
            getCache(quality).touch(id, texture, now);
        }
        return picture;
    }

    /// Legacy compatibility helper: requests full quality picture.
    public RemotePicture getServerPicture(@NotNull UUID id) {
        return getPicture(id, PictureQuality.FULL);
    }

    /// Legacy compatibility helper: ensures full quality picture is requested.
    public RemotePicture ensureRemotePicture(@NotNull UUID id) {
        return getPicture(id, PictureQuality.FULL);
    }

    /// Request a picture with a specific quality from disk or fallback to the server.
    void fetchPicture(UUID id, PictureQuality quality) {
        Camerapture.EXECUTOR.execute(() -> {
            Path diskPath = getCacheFilePath(id, quality);
            File file = diskPath.toFile();

            // Also check legacy flat cache directory for FULL quality
            if (!file.exists() && quality == PictureQuality.FULL) {
                File legacyFile = getLegacyCacheFilePath(id).toFile();
                if (legacyFile.exists()) {
                    file = legacyFile;
                }
            }

            if (file.exists()) {
                byte[] bytes;
                try {
                    bytes = Files.readAllBytes(file.toPath());
                } catch (Exception e) {
                    Camerapture.LOGGER.error("could not read cached picture {} ({}), falling back to server", id, quality, e);
                    try {
                        Files.deleteIfExists(file.toPath());
                    } catch (Exception ignored) {
                    }
                    requestFromServer(id, quality);
                    return;
                }

                final File fileRef = file;
                boolean submitted = Camerapture.trySubmitImageTask(() -> {
                    try {
                        BufferedImage image = (quality == PictureQuality.FULL)
                                ? decodeFullChecked(id, bytes)
                                : decodeThumbnailChecked(id, bytes);
                        processReceivedImage(id, quality, image);
                    } catch (Exception e) {
                        Camerapture.LOGGER.error("could not decode cached picture {} ({}), falling back to server", id, quality, e);
                        try {
                            Files.deleteIfExists(fileRef.toPath());
                        } catch (Exception ignored) {
                        }
                        requestFromServer(id, quality);
                    }
                });

                if (!submitted) {
                    RemotePicture picture = pictures.get(id);
                    if (picture != null) {
                        picture.getTexture(quality).setStatus(PictureTexture.Status.NOT_LOADED);
                    }
                }
                return;
            }

            requestFromServer(id, quality);
        });
    }

    /// Send a request to the server for a picture download.
    void requestFromServer(UUID id, PictureQuality quality) {
        PictureKey key = new PictureKey(id, quality);
        if (!inFlightNetworkRequests.add(key)) {
            return;
        }

        try {
            CameraptureDebugStats.recordRequest(quality);
            Camerapture.NETWORK.sendToServer(new RequestDownloadPacket(id, quality));
        } catch (Exception e) {
            inFlightNetworkRequests.remove(key);
            RemotePicture picture = pictures.get(id);
            if (picture != null) {
                picture.getTexture(quality).setStatus(PictureTexture.Status.NOT_LOADED);
            }
            Camerapture.LOGGER.error("failed to send request for picture {} ({})", id, quality, e);
        }
    }

    /// Update the stored texture with the given BufferedImage and upload to GPU.
    /// Byte accounting is handled strictly by TextureCache#put when the texture is uploaded.
    public void processReceivedImage(UUID id, PictureQuality quality, BufferedImage image) {
        RemotePicture picture = pictures.computeIfAbsent(id, RemotePicture::new);
        PictureTexture texture = picture.getTexture(quality);

        texture.setSize(image.getWidth(), image.getHeight());

        @SuppressWarnings("resource") NativeImage nativeImage = NativeImageUtil.toNativeImage(image);

        Minecraft.getInstance().executeIfPossible(() -> {
            DynamicTexture dynamicTexture = new DynamicTexture(
                    () -> "camerapture/" + quality.getSerializedName() + "/" + id,
                    nativeImage
            );
            Minecraft.getInstance()
                    .getTextureManager()
                    .register(texture.getTextureIdentifier(), dynamicTexture);

            texture.setStatus(PictureTexture.Status.SUCCESS);
            getCache(quality).put(id, texture);
            CameraptureDebugStats.textureUploads.incrementAndGet();
        });
    }

    /// Process bytes received from the server directly via IMAGE_EXECUTOR without client-tick delay.
    public void processReceivedBytes(UUID id, PictureQuality quality, byte[] bytes) {
        PictureKey key = new PictureKey(id, quality);
        boolean submitted = Camerapture.trySubmitImageTask(() -> {
            try {
                BufferedImage image = (quality == PictureQuality.FULL)
                        ? decodeFullChecked(id, bytes)
                        : decodeThumbnailChecked(id, bytes);
                processReceivedImage(id, quality, image);
                Camerapture.EXECUTOR.execute(() -> cacheBytesToDisk(id, quality, bytes));
            } catch (Exception e) {
                Camerapture.LOGGER.error("failed to decode received image bytes for image {} ({})", id, quality, e);
                processReceivedError(id, quality);
            } finally {
                inFlightNetworkRequests.remove(key);
            }
        });

        if (!submitted) {
            inFlightNetworkRequests.remove(key);
            RemotePicture picture = pictures.get(id);
            if (picture != null) {
                picture.getTexture(quality).setStatus(PictureTexture.Status.NOT_LOADED);
            }
            Camerapture.LOGGER.warn("Image worker saturated, deferred decode for {} ({})", id, quality);
        }
    }

    /// Processes all images from the queue (retained for backwards compatibility).
    public void processQueue() {
        QueuedBytes item;
        while ((item = byteQueue.poll()) != null) {
            processReceivedBytes(item.id(), item.quality(), item.bytes());
        }
    }

    public void processReceivedError(UUID id, PictureQuality quality, PictureErrorPacket.Reason reason) {
        inFlightNetworkRequests.remove(new PictureKey(id, quality));
        RemotePicture picture = pictures.get(id);
        if (picture != null) {
            if (reason == PictureErrorPacket.Reason.BUSY) {
                // Transient server saturation: reset to NOT_LOADED so it can be retried on next render
                picture.getTexture(quality).setStatus(PictureTexture.Status.NOT_LOADED);
                LOGGER.warn("server busy for picture {} ({}), resetting to NOT_LOADED", id, quality);
            } else {
                picture.getTexture(quality).setStatus(PictureTexture.Status.ERROR);
                CameraptureDebugStats.missingPictures.incrementAndGet();
                LOGGER.error("remote error for picture {} ({})", id, quality);
            }
        }
    }

    public void processReceivedError(UUID id, PictureQuality quality) {
        processReceivedError(id, quality, PictureErrorPacket.Reason.NOT_FOUND);
    }

    /// Cache picture bytes to disk.
    public void cacheBytesToDisk(UUID id, PictureQuality quality, byte[] bytes) {
        if (!shouldCacheToDisk()) {
            return;
        }

        try {
            Path path = getCacheFilePath(id, quality);
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException e) {
            Camerapture.LOGGER.error("could not cache picture {} ({})", id, quality, e);
        }
    }

    /// Decode full WebP bytes with header safety checks.
    private static BufferedImage decodeFullChecked(UUID id, byte[] bytes) throws IOException {
        WebPHeader.Size size = WebPHeader.read(bytes);
        if (size == null) {
            throw new IOException("picture " + id + " is not a readable WebP");
        }

        int limit = Math.min(
                (CameraptureClient.syncedConfig != null) ? CameraptureClient.syncedConfig.maxImageResolution() : ABSOLUTE_MAX_RESOLUTION,
                ABSOLUTE_MAX_RESOLUTION
        );
        if (size.width() > limit || size.height() > limit) {
            throw new IOException("refusing to decode picture " + id + " at "
                    + size.width() + "x" + size.height() + ", over the " + limit + " limit");
        }

        return ImageUtil.decodeImageFromWebP(bytes);
    }

    /// Decode thumbnail WebP bytes with defensive bounds checks against server synced config.
    private static BufferedImage decodeThumbnailChecked(UUID id, byte[] bytes) throws IOException {
        WebPHeader.Size size = WebPHeader.read(bytes);
        if (size == null) {
            throw new IOException("thumbnail " + id + " is not a readable WebP");
        }

        int permittedResolution = (CameraptureClient.syncedConfig != null)
                ? CameraptureClient.syncedConfig.thumbnailResolution() * 2
                : 256;
        int limit = Math.min(Math.max(256, permittedResolution), ABSOLUTE_MAX_THUMBNAIL_RESOLUTION);
        if (size.width() > limit || size.height() > limit) {
            throw new IOException("refusing to decode thumbnail " + id + " at "
                    + size.width() + "x" + size.height() + ", over the " + limit + " limit");
        }

        return ImageUtil.decodeImageFromWebP(bytes);
    }

    /// Clear all pictures from the store and destroy all textures.
    public void clear() {
        Minecraft.getInstance().executeIfPossible(() -> {
            fullCache.clear();
            thumbnailCache.clear();
            pictures.clear();
            inFlightNetworkRequests.clear();
        });
    }

    public long getFullTextureBytes() {
        return fullCache.getTextureBytes();
    }

    public long getThumbnailTextureBytes() {
        return thumbnailCache.getTextureBytes();
    }

    private static boolean shouldCacheToDisk() {
        return CameraptureClient.replayModInstalled
                || (Camerapture.CONFIG_MANAGER.getConfig().client.cachePictures
                && !Minecraft.getInstance().hasSingleplayerServer());
    }

    private Path getCacheFilePath(UUID uuid, PictureQuality quality) {
        String subfolder = (quality == PictureQuality.THUMBNAIL) ? "thumbnails" : "full";
        return Camerapture.PLATFORM.getGameFolder()
                .resolve("camerapture")
                .resolve("picture-cache")
                .resolve(subfolder)
                .resolve(uuid + ".webp");
    }

    private Path getLegacyCacheFilePath(UUID uuid) {
        return Camerapture.PLATFORM.getGameFolder()
                .resolve("camerapture")
                .resolve("picture-cache")
                .resolve(uuid + ".webp");
    }

    public static ClientPictureStore getInstance() {
        return INSTANCE;
    }

    private record QueuedBytes(UUID id, PictureQuality quality, byte[] bytes) {
    }

    /// An LRU cache managing GPU texture memory for a specific quality level.
    public static class TextureCache {
        private final PictureQuality quality;
        private final long customMaxBytes;
        private final long customGraceMs;
        private final Map<UUID, CacheEntry> entries = new LinkedHashMap<>(16, 0.75f, true);
        private long textureBytes = 0L;

        public TextureCache(PictureQuality quality) {
            this(quality, -1L, -1L);
        }

        public TextureCache(PictureQuality quality, long maxBytes, long graceMs) {
            this.quality = quality;
            this.customMaxBytes = maxBytes;
            this.customGraceMs = graceMs;
        }

        private record CacheEntry(PictureTexture texture, long accountedBytes) {
        }

        private long getMaxBytes() {
            if (customMaxBytes > 0) {
                return customMaxBytes;
            }
            long budgetMiB = (quality == PictureQuality.THUMBNAIL)
                    ? Camerapture.CONFIG_MANAGER.getConfig().client.thumbnailTextureBudgetMiB
                    : Camerapture.CONFIG_MANAGER.getConfig().client.fullTextureBudgetMiB;
            return Math.max(8L, budgetMiB) * 1024L * 1024L;
        }

        private long getInUseGraceMs() {
            if (customGraceMs >= 0) {
                return customGraceMs;
            }
            // Thumbnails are small and useful across distant scenes, so they have a longer grace period.
            return (quality == PictureQuality.THUMBNAIL) ? 30_000L : 5_000L;
        }

        public synchronized long getTextureBytes() {
            return textureBytes;
        }

        public synchronized List<UUID> getOrderedKeys() {
            return new ArrayList<>(entries.keySet());
        }

        public void touch(UUID id, PictureTexture texture) {
            touch(id, texture, System.currentTimeMillis());
        }

        public void touch(UUID id, PictureTexture texture, long now) {
            // Throttle touches to 500ms to eliminate synchronized Map lock contention on rendered frames
            if (now - texture.getLastAccess() < 500L) {
                return;
            }
            synchronized (this) {
                texture.touch(now);
                entries.get(id); // Access in LinkedHashMap moves to MRU end
            }
        }

        public void put(UUID id, PictureTexture texture) {
            List<Map.Entry<UUID, PictureTexture>> evicted = null;

            synchronized (this) {
                long newBytes = texture.getTextureBytes();
                CacheEntry prev = entries.put(id, new CacheEntry(texture, newBytes));
                if (prev != null) {
                    textureBytes -= prev.accountedBytes();
                }
                textureBytes += newBytes;
                texture.touch();

                long now = System.currentTimeMillis();
                long maxBytes = getMaxBytes();
                long graceMs = getInUseGraceMs();

                Iterator<Map.Entry<UUID, CacheEntry>> iterator = entries.entrySet().iterator();
                while (textureBytes > maxBytes && iterator.hasNext()) {
                    Map.Entry<UUID, CacheEntry> eldest = iterator.next();
                    if (now - eldest.getValue().texture().getLastAccess() < graceMs) {
                        break;
                    }

                    iterator.remove();
                    textureBytes -= eldest.getValue().accountedBytes();

                    if (evicted == null) {
                        evicted = new ArrayList<>();
                    }
                    evicted.add(Map.entry(eldest.getKey(), eldest.getValue().texture()));
                }
            }

            if (evicted != null) {
                for (Map.Entry<UUID, PictureTexture> entry : evicted) {
                    releaseTexture(entry.getKey(), entry.getValue());
                }
            }
        }

        private void releaseTexture(UUID id, PictureTexture texture) {
            CameraptureDebugStats.textureEvictions.incrementAndGet();
            texture.setStatus(PictureTexture.Status.NOT_LOADED);

            try {
                if (Minecraft.getInstance() != null && Minecraft.getInstance().getTextureManager() != null) {
                    Minecraft.getInstance().executeIfPossible(() -> {
                        synchronized (this) {
                            if (entries.containsKey(id)) {
                                return;
                            }
                        }
                        Minecraft.getInstance().getTextureManager().release(texture.getTextureIdentifier());
                    });
                }
            } catch (Throwable ignored) {
            }
        }

        public synchronized void clear() {
            for (CacheEntry entry : entries.values()) {
                entry.texture().setStatus(PictureTexture.Status.NOT_LOADED);
                try {
                    if (Minecraft.getInstance() != null && Minecraft.getInstance().getTextureManager() != null) {
                        Minecraft.getInstance().getTextureManager().release(entry.texture().getTextureIdentifier());
                    }
                } catch (Throwable ignored) {
                }
            }
            entries.clear();
            textureBytes = 0L;
        }
    }
}