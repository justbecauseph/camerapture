package me.chrr.camerapture.picture;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.ImageTaskExecutor;
import me.chrr.camerapture.util.ImageUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/// The server-side picture store. It manages picture storage, thumbnail generation,
/// and disk caching in the world folder. Pictures are identified by UUID and PictureQuality.
public class ServerPictureStore {
    private static final Logger LOGGER = LogManager.getLogger("Camerapture/ServerPictureStore");

    /// The cache is bounded by bytes rather than by entry count. Pictures vary hugely in size, so a
    /// fixed entry count either wastes memory on small ones or thrashes on large ones — and a world
    /// with a few thousand posters would hold only a small fraction of them, sending almost every
    /// request to the disk. At the 500KB default picture limit this holds at least ~500 pictures,
    /// and many more in practice since most are far smaller.
    private static final long MAX_CACHE_BYTES = 256L * 1024L * 1024L;

    private static final ServerPictureStore INSTANCE = new ServerPictureStore();

    private final Set<UUID> reservedIds = ConcurrentHashMap.newKeySet();

    /// Access-ordered LRU guarded by its own monitor, with `cacheBytes` tracking its total weight.
    private final LinkedHashMap<PictureKey, StoredPicture> pictureCache = new LinkedHashMap<>(512, 0.75f, true);
    private long cacheBytes = 0L;

    /// Per-resource load locks, so concurrent misses for the same picture key collapse into one disk read.
    private final Map<PictureKey, Object> loadLocks = new ConcurrentHashMap<>();

    private final Executor ioExecutor;
    private final ImageTaskExecutor imageExecutor;
    private final int defaultThumbnailResolution;

    public ServerPictureStore() {
        this(null, null, 128);
    }

    public ServerPictureStore(Executor ioExecutor, ImageTaskExecutor imageExecutor, int thumbnailResolution) {
        this.ioExecutor = ioExecutor;
        this.imageExecutor = imageExecutor;
        this.defaultThumbnailResolution = thumbnailResolution;
    }

    private Executor getIoExecutor() {
        return ioExecutor != null ? ioExecutor : Camerapture.EXECUTOR;
    }

    private boolean trySubmitImageTask(Runnable task) {
        if (imageExecutor != null) {
            return imageExecutor.trySubmit(task);
        }
        return Camerapture.trySubmitImageTask(task);
    }

    private int getThumbnailResolution() {
        if (imageExecutor != null) {
            return defaultThumbnailResolution;
        }
        try {
            return Camerapture.CONFIG_MANAGER.getConfig().server.thumbnailResolution;
        } catch (Throwable t) {
            return defaultThumbnailResolution;
        }
    }

    public UUID reserveId() {
        UUID id = UUID.randomUUID();
        reservedIds.add(id);
        return id;
    }

    public boolean unreserveId(UUID id) {
        return reservedIds.remove(id);
    }

    public boolean isReserved(UUID id) {
        return reservedIds.contains(id);
    }

    public record PreparedPicture(UUID id, byte[] fullBytes, @Nullable byte[] thumbBytes) {
    }

    /// Validates upload constraints and generates thumbnail on the calling CPU image thread.
    public PreparedPicture prepare(UUID id, byte[] bytes) throws IOException {
        if (!unreserveId(id)) {
            throw new IOException("UUID not reserved");
        }

        int maxImageBytes = Camerapture.CONFIG_MANAGER.getConfig().server.maxImageBytes;
        if (bytes.length > maxImageBytes) {
            throw new IOException("image larger than " + maxImageBytes + " bytes");
        }

        int maxImageResolution = Camerapture.CONFIG_MANAGER.getConfig().server.maxImageResolution;
        WebPHeader.Size size = WebPHeader.read(bytes);
        if (size == null) {
            throw new IOException("image is not a readable WebP");
        }

        if (size.width() > maxImageResolution || size.height() > maxImageResolution) {
            throw new IOException("image is " + size.width() + "x" + size.height()
                    + ", larger than " + maxImageResolution + " in at least one dimension");
        }

        byte[] thumbBytes = null;
        try {
            int thumbRes = getThumbnailResolution();
            thumbBytes = ImageUtil.createThumbnail(bytes, thumbRes);
        } catch (Exception e) {
            LOGGER.error("failed to generate server thumbnail for {}", id, e);
        }

        return new PreparedPicture(id, bytes, thumbBytes);
    }

    public enum ThumbnailResultType {
        SUCCESS,
        NOT_FOUND,
        BUSY
    }

    public record ThumbnailResult(ThumbnailResultType type, @Nullable StoredPicture picture) {
        public static ThumbnailResult success(StoredPicture picture) {
            return new ThumbnailResult(ThumbnailResultType.SUCCESS, picture);
        }

        public static ThumbnailResult notFound() {
            return new ThumbnailResult(ThumbnailResultType.NOT_FOUND, null);
        }

        public static ThumbnailResult busy() {
            return new ThumbnailResult(ThumbnailResultType.BUSY, null);
        }
    }

    private final ConcurrentHashMap<UUID, CompletableFuture<ThumbnailResult>> thumbnailGenerations = new ConcurrentHashMap<>();

    /// Persists prepared full picture and thumbnail to disk and memory cache (I/O thread).
    public void save(MinecraftServer server, PreparedPicture prepared) throws IOException {
        StoredPicture fullPicture = new StoredPicture(prepared.fullBytes());
        PictureKey fullKey = new PictureKey(prepared.id(), PictureQuality.FULL);
        cache(fullKey, fullPicture);

        Path fullPath = getFilePath(server, prepared.id(), PictureQuality.FULL);
        Files.createDirectories(fullPath.getParent());
        Files.write(fullPath, prepared.fullBytes());

        if (prepared.thumbBytes() != null) {
            StoredPicture thumbPicture = new StoredPicture(prepared.thumbBytes());
            PictureKey thumbKey = new PictureKey(prepared.id(), PictureQuality.THUMBNAIL);
            cache(thumbKey, thumbPicture);

            Path thumbPath = getFilePath(server, prepared.id(), PictureQuality.THUMBNAIL);
            Files.write(thumbPath, prepared.thumbBytes());
        }
    }

    /// Persist a lazily generated thumbnail to disk and cache (I/O thread).
    public void saveThumbnail(MinecraftServer server, UUID id, StoredPicture thumbPicture) {
        Path dataFolder = server.getWorldPath(LevelResource.ROOT).resolve("camerapture");
        saveThumbnail(dataFolder, id, thumbPicture);
    }

    public void saveThumbnail(Path dataFolder, UUID id, StoredPicture thumbPicture) {
        getIoExecutor().execute(() -> {
            try {
                PictureKey thumbKey = new PictureKey(id, PictureQuality.THUMBNAIL);
                cache(thumbKey, thumbPicture);

                Path thumbPath = getFilePath(dataFolder, id, PictureQuality.THUMBNAIL);
                Files.createDirectories(thumbPath.getParent());
                Files.write(thumbPath, thumbPicture.bytes());
            } catch (Exception e) {
                LOGGER.error("failed to save lazy thumbnail for {}", id, e);
            }
        });
    }

    /// Fetches a thumbnail or lazily generates and persists one if missing, collapsing concurrent
    /// requests for the same picture into a single I/O read + single WebP generation task.
    public CompletableFuture<ThumbnailResult> getOrGenerateThumbnailAsync(MinecraftServer server, UUID id) {
        Path dataFolder = server.getWorldPath(LevelResource.ROOT).resolve("camerapture");
        return getOrGenerateThumbnailAsync(dataFolder, id);
    }

    public CompletableFuture<ThumbnailResult> getOrGenerateThumbnailAsync(Path dataFolder, UUID id) {
        PictureKey key = new PictureKey(id, PictureQuality.THUMBNAIL);
        StoredPicture cached = getCached(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(ThumbnailResult.success(cached));
        }

        CompletableFuture<ThumbnailResult> created = new CompletableFuture<>();
        CompletableFuture<ThumbnailResult> existing = thumbnailGenerations.putIfAbsent(id, created);
        if (existing != null) {
            return existing;
        }

        created.whenComplete((result, error) -> thumbnailGenerations.remove(id, created));

        startThumbnailGeneration(dataFolder, id, created);
        return created;
    }

    private void startThumbnailGeneration(Path dataFolder, UUID pictureId, CompletableFuture<ThumbnailResult> future) {
        PictureKey key = new PictureKey(pictureId, PictureQuality.THUMBNAIL);
        getIoExecutor().execute(() -> {
            try {
                // 1. Check if already in memory cache
                StoredPicture memoryCached = getCached(key);
                if (memoryCached != null) {
                    future.complete(ThumbnailResult.success(memoryCached));
                    return;
                }

                // 2. Check if thumbnail exists on disk
                Path thumbPath = getFilePath(dataFolder, pictureId, PictureQuality.THUMBNAIL);
                if (Files.exists(thumbPath)) {
                    StoredPicture thumb = new StoredPicture(Files.readAllBytes(thumbPath));
                    cache(key, thumb);
                    future.complete(ThumbnailResult.success(thumb));
                    return;
                }

                // 3. Fallback to generating from full image (migration)
                Path fullPath = getFilePath(dataFolder, pictureId, PictureQuality.FULL);
                if (!Files.exists(fullPath)) {
                    future.complete(ThumbnailResult.notFound());
                    return;
                }

                byte[] fullBytes = Files.readAllBytes(fullPath);

                boolean submitted = trySubmitImageTask(() -> {
                    try {
                        int thumbRes = getThumbnailResolution();
                        byte[] thumbBytes = ImageUtil.createThumbnail(fullBytes, thumbRes);
                        StoredPicture thumbPicture = new StoredPicture(thumbBytes);

                        cache(key, thumbPicture);
                        saveThumbnail(dataFolder, pictureId, thumbPicture);
                        future.complete(ThumbnailResult.success(thumbPicture));
                    } catch (Exception e) {
                        LOGGER.error("failed to generate lazy thumbnail for picture {}", pictureId, e);
                        future.complete(ThumbnailResult.busy());
                    }
                });

                if (!submitted) {
                    LOGGER.warn("Image worker saturated, could not generate lazy thumbnail for {}", pictureId);
                    future.complete(ThumbnailResult.busy());
                }
            } catch (Exception e) {
                LOGGER.error("failed to read picture for lazy thumbnail generation {}", pictureId, e);
                future.complete(ThumbnailResult.busy());
            }
        });
    }

    public void put(MinecraftServer server, UUID id, StoredPicture picture) throws IOException {
        PreparedPicture prepared = prepare(id, picture.bytes());
        save(server, prepared);
    }

    @Nullable
    public StoredPicture get(MinecraftServer server, UUID id, PictureQuality quality) throws IOException {
        PictureKey key = new PictureKey(id, quality);
        StoredPicture cached = getCached(key);
        if (cached != null) {
            return cached;
        }

        // Collapse concurrent misses for the same picture key.
        Object loadLock = loadLocks.computeIfAbsent(key, k -> new Object());
        try {
            synchronized (loadLock) {
                cached = getCached(key);
                if (cached != null) {
                    return cached;
                }

                Path path = getFilePath(server, id, quality);
                if (Files.exists(path)) {
                    StoredPicture picture = new StoredPicture(Files.readAllBytes(path));
                    cache(key, picture);
                    return picture;
                }

                return null;
            }
        } finally {
            loadLocks.remove(key, loadLock);
        }
    }

    @Nullable
    private StoredPicture getCached(PictureKey key) {
        synchronized (pictureCache) {
            // Has to be a single lookup: containsKey/get is not atomic, so a concurrent eviction between
            // the two would report the picture as missing. It also keeps the LRU honest — the cache is
            // access-ordered, and containsKey does not count as an access.
            return pictureCache.get(key);
        }
    }

    /// Add a picture to the cache, evicting least-recently-used entries until it fits.
    private void cache(PictureKey key, StoredPicture picture) {
        synchronized (pictureCache) {
            StoredPicture previous = pictureCache.put(key, picture);
            if (previous != null) {
                cacheBytes -= previous.bytes().length;
            }
            cacheBytes += picture.bytes().length;

            // Access order puts the least recently used entry first. The entry we just inserted is the
            // most recent, so it's last and only evicted if it alone is over the cap.
            Iterator<Map.Entry<PictureKey, StoredPicture>> iterator = pictureCache.entrySet().iterator();
            while (cacheBytes > MAX_CACHE_BYTES && pictureCache.size() > 1 && iterator.hasNext()) {
                Map.Entry<PictureKey, StoredPicture> eldest = iterator.next();
                cacheBytes -= eldest.getValue().bytes().length;
                iterator.remove();
            }
        }
    }

    public Path getFilePath(MinecraftServer server, UUID uuid, PictureQuality quality) {
        Path dataFolder = server.getWorldPath(LevelResource.ROOT).resolve("camerapture");
        return getFilePath(dataFolder, uuid, quality);
    }

    public static Path getFilePath(Path dataFolder, UUID uuid, PictureQuality quality) {
        if (quality == PictureQuality.THUMBNAIL) {
            return dataFolder.resolve(uuid + ".thumb.webp");
        } else {
            return dataFolder.resolve(uuid + ".webp");
        }
    }

    public static ServerPictureStore getInstance() {
        return INSTANCE;
    }
}
