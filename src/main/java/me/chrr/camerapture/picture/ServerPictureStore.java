package me.chrr.camerapture.picture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.ImageTaskExecutor;
import me.chrr.camerapture.util.ImageUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

public class ServerPictureStore {
   private static final Logger LOGGER = LogManager.getLogger("Camerapture/ServerPictureStore");
   private static final long MAX_CACHE_BYTES = 268435456L;
   private static final ServerPictureStore INSTANCE = new ServerPictureStore();
   private final Set<UUID> reservedIds = ConcurrentHashMap.newKeySet();
   private final LinkedHashMap<PictureKey, StoredPicture> pictureCache = new LinkedHashMap<>(512, 0.75F, true);
   private long cacheBytes = 0L;
   private final Map<PictureKey, Object> loadLocks = new ConcurrentHashMap<>();
   private final Executor ioExecutor;
   private final ImageTaskExecutor imageExecutor;
   private final int defaultThumbnailResolution;
   private final ConcurrentHashMap<UUID, CompletableFuture<ServerPictureStore.ThumbnailResult>> thumbnailGenerations = new ConcurrentHashMap<>();

   public ServerPictureStore() {
      this(null, null, 128);
   }

   public ServerPictureStore(Executor ioExecutor, ImageTaskExecutor imageExecutor, int thumbnailResolution) {
      this.ioExecutor = ioExecutor;
      this.imageExecutor = imageExecutor;
      this.defaultThumbnailResolution = thumbnailResolution;
   }

   private Executor getIoExecutor() {
      return this.ioExecutor != null ? this.ioExecutor : Camerapture.EXECUTOR;
   }

   private boolean trySubmitImageTask(Runnable task) {
      return this.imageExecutor != null ? this.imageExecutor.trySubmit(task) : Camerapture.trySubmitImageTask(task);
   }

   private int getThumbnailResolution() {
      if (this.imageExecutor != null) {
         return this.defaultThumbnailResolution;
      }

      try {
         return Camerapture.CONFIG_MANAGER.getConfig().server.thumbnailResolution;
      } catch (Throwable t) {
         return this.defaultThumbnailResolution;
      }
   }

   public UUID reserveId() {
      UUID id = UUID.randomUUID();
      this.reservedIds.add(id);
      return id;
   }

   public boolean unreserveId(UUID id) {
      return this.reservedIds.remove(id);
   }

   public boolean isReserved(UUID id) {
      return this.reservedIds.contains(id);
   }

   public ServerPictureStore.PreparedPicture prepare(UUID id, byte[] bytes) throws IOException {
      if (!this.unreserveId(id)) {
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

      if (size.width() <= maxImageResolution && size.height() <= maxImageResolution) {
         byte[] thumbBytes = null;

         try {
            int thumbRes = this.getThumbnailResolution();
            thumbBytes = ImageUtil.createThumbnail(bytes, thumbRes);
         } catch (Exception e) {
            LOGGER.error("failed to generate server thumbnail for {}", id, e);
         }

         return new ServerPictureStore.PreparedPicture(id, bytes, thumbBytes);
      } else {
         throw new IOException("image is " + size.width() + "x" + size.height() + ", larger than " + maxImageResolution + " in at least one dimension");
      }
   }

   public void save(MinecraftServer server, ServerPictureStore.PreparedPicture prepared) throws IOException {
      StoredPicture fullPicture = new StoredPicture(prepared.fullBytes());
      PictureKey fullKey = new PictureKey(prepared.id(), PictureQuality.FULL);
      this.cache(fullKey, fullPicture);
      Path fullPath = this.getFilePath(server, prepared.id(), PictureQuality.FULL);
      Files.createDirectories(fullPath.getParent());
      Files.write(fullPath, prepared.fullBytes());
      if (prepared.thumbBytes() != null) {
         StoredPicture thumbPicture = new StoredPicture(prepared.thumbBytes());
         PictureKey thumbKey = new PictureKey(prepared.id(), PictureQuality.THUMBNAIL);
         this.cache(thumbKey, thumbPicture);
         Path thumbPath = this.getFilePath(server, prepared.id(), PictureQuality.THUMBNAIL);
         Files.write(thumbPath, prepared.thumbBytes());
      }
   }

   public void saveThumbnail(MinecraftServer server, UUID id, StoredPicture thumbPicture) {
      Path dataFolder = server.getWorldPath(LevelResource.ROOT).resolve("camerapture");
      this.saveThumbnail(dataFolder, id, thumbPicture);
   }

   public void saveThumbnail(Path dataFolder, UUID id, StoredPicture thumbPicture) {
      this.getIoExecutor().execute(() -> {
         try {
            PictureKey thumbKey = new PictureKey(id, PictureQuality.THUMBNAIL);
            this.cache(thumbKey, thumbPicture);
            Path thumbPath = getFilePath(dataFolder, id, PictureQuality.THUMBNAIL);
            Files.createDirectories(thumbPath.getParent());
            Files.write(thumbPath, thumbPicture.bytes());
         } catch (Exception e) {
            LOGGER.error("failed to save lazy thumbnail for {}", id, e);
         }
      });
   }

   public CompletableFuture<ServerPictureStore.ThumbnailResult> getOrGenerateThumbnailAsync(MinecraftServer server, UUID id) {
      Path dataFolder = server.getWorldPath(LevelResource.ROOT).resolve("camerapture");
      return this.getOrGenerateThumbnailAsync(dataFolder, id);
   }

   public CompletableFuture<ServerPictureStore.ThumbnailResult> getOrGenerateThumbnailAsync(Path dataFolder, UUID id) {
      PictureKey key = new PictureKey(id, PictureQuality.THUMBNAIL);
      StoredPicture cached = this.getCached(key);
      if (cached != null) {
         return CompletableFuture.completedFuture(ServerPictureStore.ThumbnailResult.success(cached));
      }

      CompletableFuture<ServerPictureStore.ThumbnailResult> created = new CompletableFuture<>();
      CompletableFuture<ServerPictureStore.ThumbnailResult> existing = this.thumbnailGenerations.putIfAbsent(id, created);
      if (existing != null) {
         return existing;
      }

      created.whenComplete((result, error) -> this.thumbnailGenerations.remove(id, created));
      this.startThumbnailGeneration(dataFolder, id, created);
      return created;
   }

   private void startThumbnailGeneration(Path dataFolder, UUID pictureId, CompletableFuture<ServerPictureStore.ThumbnailResult> future) {
      PictureKey key = new PictureKey(pictureId, PictureQuality.THUMBNAIL);
      this.getIoExecutor().execute(() -> {
         try {
            StoredPicture memoryCached = this.getCached(key);
            if (memoryCached != null) {
               future.complete(ServerPictureStore.ThumbnailResult.success(memoryCached));
               return;
            }

            Path thumbPath = getFilePath(dataFolder, pictureId, PictureQuality.THUMBNAIL);
            if (Files.exists(thumbPath)) {
               StoredPicture thumb = new StoredPicture(Files.readAllBytes(thumbPath));
               this.cache(key, thumb);
               future.complete(ServerPictureStore.ThumbnailResult.success(thumb));
               return;
            }

            Path fullPath = getFilePath(dataFolder, pictureId, PictureQuality.FULL);
            if (!Files.exists(fullPath)) {
               future.complete(ServerPictureStore.ThumbnailResult.notFound());
               return;
            }

            byte[] fullBytes = Files.readAllBytes(fullPath);
            boolean submitted = this.trySubmitImageTask(() -> {
               try {
                  int thumbRes = this.getThumbnailResolution();
                  byte[] thumbBytes = ImageUtil.createThumbnail(fullBytes, thumbRes);
                  StoredPicture thumbPicture = new StoredPicture(thumbBytes);
                  this.cache(key, thumbPicture);
                  this.saveThumbnail(dataFolder, pictureId, thumbPicture);
                  future.complete(ServerPictureStore.ThumbnailResult.success(thumbPicture));
               } catch (Exception e) {
                  LOGGER.error("failed to generate lazy thumbnail for picture {}", pictureId, e);
                  future.complete(ServerPictureStore.ThumbnailResult.busy());
               }
            });
            if (!submitted) {
               LOGGER.warn("Image worker saturated, could not generate lazy thumbnail for {}", pictureId);
               future.complete(ServerPictureStore.ThumbnailResult.busy());
            }
         } catch (Exception e) {
            LOGGER.error("failed to read picture for lazy thumbnail generation {}", pictureId, e);
            future.complete(ServerPictureStore.ThumbnailResult.busy());
         }
      });
   }

   public void put(MinecraftServer server, UUID id, StoredPicture picture) throws IOException {
      ServerPictureStore.PreparedPicture prepared = this.prepare(id, picture.bytes());
      this.save(server, prepared);
   }

   @Nullable
   public StoredPicture get(MinecraftServer server, UUID id, PictureQuality quality) throws IOException {
      PictureKey key = new PictureKey(id, quality);
      StoredPicture cached = this.getCached(key);
      if (cached != null) {
         return cached;
      }

      Object loadLock = this.loadLocks.computeIfAbsent(key, k -> new Object());

      StoredPicture var10;
      try {
         synchronized (loadLock) {
            cached = this.getCached(key);
            if (cached != null) {
               return cached;
            }

            Path path = this.getFilePath(server, id, quality);
            if (!Files.exists(path)) {
               return null;
            }

            StoredPicture picture = new StoredPicture(Files.readAllBytes(path));
            this.cache(key, picture);
            var10 = picture;
         }
      } finally {
         this.loadLocks.remove(key, loadLock);
      }

      return var10;
   }

   @Nullable
   private StoredPicture getCached(PictureKey key) {
      synchronized (this.pictureCache) {
         return this.pictureCache.get(key);
      }
   }

   private void cache(PictureKey key, StoredPicture picture) {
      synchronized (this.pictureCache) {
         StoredPicture previous = this.pictureCache.put(key, picture);
         if (previous != null) {
            this.cacheBytes = this.cacheBytes - previous.bytes().length;
         }

         this.cacheBytes = this.cacheBytes + picture.bytes().length;
         Iterator<Entry<PictureKey, StoredPicture>> iterator = this.pictureCache.entrySet().iterator();

         while (this.cacheBytes > 268435456L && this.pictureCache.size() > 1 && iterator.hasNext()) {
            Entry<PictureKey, StoredPicture> eldest = iterator.next();
            this.cacheBytes = this.cacheBytes - eldest.getValue().bytes().length;
            iterator.remove();
         }
      }
   }

   public Path getFilePath(MinecraftServer server, UUID uuid, PictureQuality quality) {
      Path dataFolder = server.getWorldPath(LevelResource.ROOT).resolve("camerapture");
      return getFilePath(dataFolder, uuid, quality);
   }

   public static Path getFilePath(Path dataFolder, UUID uuid, PictureQuality quality) {
      return quality == PictureQuality.THUMBNAIL ? dataFolder.resolve(uuid + ".thumb.webp") : dataFolder.resolve(uuid + ".webp");
   }

   public static ServerPictureStore getInstance() {
      return INSTANCE;
   }

   public record PreparedPicture(UUID id, byte[] fullBytes, @Nullable byte[] thumbBytes) {
   }

   public record ThumbnailResult(ServerPictureStore.ThumbnailResultType type, @Nullable StoredPicture picture) {
      public static ServerPictureStore.ThumbnailResult success(StoredPicture picture) {
         return new ServerPictureStore.ThumbnailResult(ServerPictureStore.ThumbnailResultType.SUCCESS, picture);
      }

      public static ServerPictureStore.ThumbnailResult notFound() {
         return new ServerPictureStore.ThumbnailResult(ServerPictureStore.ThumbnailResultType.NOT_FOUND, null);
      }

      public static ServerPictureStore.ThumbnailResult busy() {
         return new ServerPictureStore.ThumbnailResult(ServerPictureStore.ThumbnailResultType.BUSY, null);
      }
   }

   public enum ThumbnailResultType {
      SUCCESS,
      NOT_FOUND,
      BUSY;
   }
}
