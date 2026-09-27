package me.chrr.camerapture.picture;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.CameraptureClient;
import me.chrr.camerapture.net.clientbound.PictureErrorPacket;
import me.chrr.camerapture.net.serverbound.RequestDownloadPacket;
import me.chrr.camerapture.render.CameraptureDebugStats;
import me.chrr.camerapture.util.ImageUtil;
import me.chrr.camerapture.util.NativeImageUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ClientPictureStore {
   private static final Logger LOGGER = LogManager.getLogger("Camerapture/ClientPictureStore");
   private static final ClientPictureStore INSTANCE = new ClientPictureStore();
   private static final int ABSOLUTE_MAX_RESOLUTION = 8192;
   private static final int ABSOLUTE_MAX_THUMBNAIL_RESOLUTION = 512;
   public static final long INITIAL_RETRY_BACKOFF_MS = 250L;
   public static final long MAX_RETRY_BACKOFF_MS = 8000L;
   private final Queue<ClientPictureStore.QueuedBytes> byteQueue = new ConcurrentLinkedQueue<>();
   private final Map<UUID, RemotePicture> pictures = new ConcurrentHashMap<>();
   private final Set<PictureKey> inFlightNetworkRequests = ConcurrentHashMap.newKeySet();
   private final ConcurrentHashMap<PictureKey, ClientPictureStore.RetryState> retryStates = new ConcurrentHashMap<>();
   private final ClientPictureStore.TextureCache fullCache = new ClientPictureStore.TextureCache(PictureQuality.FULL);
   private final ClientPictureStore.TextureCache thumbnailCache = new ClientPictureStore.TextureCache(PictureQuality.THUMBNAIL);

   private ClientPictureStore() {
   }

   ClientPictureStore.TextureCache getCache(PictureQuality quality) {
      return quality == PictureQuality.THUMBNAIL ? this.thumbnailCache : this.fullCache;
   }

   public boolean isInFlight(UUID id, PictureQuality quality) {
      return this.inFlightNetworkRequests.contains(new PictureKey(id, quality));
   }

   public RemotePicture getPictureDirect(UUID id) {
      return this.pictures.computeIfAbsent(id, RemotePicture::new);
   }

   public long getRetryDeadline(UUID id, PictureQuality quality) {
      ClientPictureStore.RetryState state = this.retryStates.get(new PictureKey(id, quality));
      return state != null ? state.retryAfterDeadline() : 0L;
   }

   public int getConsecutiveBusyFailures(UUID id, PictureQuality quality) {
      ClientPictureStore.RetryState state = this.retryStates.get(new PictureKey(id, quality));
      return state != null ? state.consecutiveFailures() : 0;
   }

   public PictureTexture resolveTextureForRender(@NotNull UUID id, @NotNull PictureQuality preferred) {
      RemotePicture picture = this.pictures.computeIfAbsent(id, RemotePicture::new);
      PictureTexture preferredTexture = picture.getTexture(preferred);
      if (preferredTexture.getStatus() == PictureTexture.Status.NOT_LOADED) {
         long now = System.currentTimeMillis();
         ClientPictureStore.RetryState retry = this.retryStates.get(new PictureKey(id, preferred));
         if (retry == null || now >= retry.retryAfterDeadline()) {
            preferredTexture.setStatus(PictureTexture.Status.FETCHING);
            this.fetchPicture(id, preferred);
         }
      }

      PictureTexture effective = picture.getEffectiveTexture(preferred);
      if (effective.getStatus() == PictureTexture.Status.SUCCESS) {
         long now = System.currentTimeMillis();
         this.getCache(effective.getQuality()).touch(id, effective, now);
      }

      return effective;
   }

   public ResolvedPicture resolveForRender(@NotNull UUID id, @NotNull PictureQuality preferred) {
      RemotePicture picture = this.pictures.computeIfAbsent(id, RemotePicture::new);
      PictureTexture effective = this.resolveTextureForRender(id, preferred);
      return new ResolvedPicture(picture, effective);
   }

   public RemotePicture getPicture(@NotNull UUID id, @NotNull PictureQuality quality) {
      RemotePicture picture = this.pictures.computeIfAbsent(id, RemotePicture::new);
      PictureTexture texture = picture.getTexture(quality);
      if (texture.getStatus() == PictureTexture.Status.NOT_LOADED) {
         long now = System.currentTimeMillis();
         ClientPictureStore.RetryState retry = this.retryStates.get(new PictureKey(id, quality));
         if (retry == null || now >= retry.retryAfterDeadline()) {
            texture.setStatus(PictureTexture.Status.FETCHING);
            this.fetchPicture(id, quality);
         }
      } else if (texture.getStatus() == PictureTexture.Status.SUCCESS) {
         long now = System.currentTimeMillis();
         this.getCache(quality).touch(id, texture, now);
      }

      return picture;
   }

   public RemotePicture getServerPicture(@NotNull UUID id) {
      return this.getPicture(id, PictureQuality.FULL);
   }

   public RemotePicture ensureRemotePicture(@NotNull UUID id) {
      return this.getPicture(id, PictureQuality.FULL);
   }

   void fetchPicture(UUID id, PictureQuality quality) {
      Camerapture.EXECUTOR.execute(() -> {
         Path diskPath = this.getCacheFilePath(id, quality);
         File file = diskPath.toFile();
         if (!file.exists() && quality == PictureQuality.FULL) {
            File legacyFile = this.getLegacyCacheFilePath(id).toFile();
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
               } catch (Exception var9) {
               }

               this.requestFromServer(id, quality);
               return;
            }

            File fileRef = file;
            boolean submitted = Camerapture.trySubmitImageTask(() -> {
               try {
                  BufferedImage image = quality == PictureQuality.FULL ? decodeFullChecked(id, bytes) : decodeThumbnailChecked(id, bytes);
                  this.processReceivedImage(id, quality, image);
               } catch (Exception e) {
                  Camerapture.LOGGER.error("could not decode cached picture {} ({}), falling back to server", id, quality, e);

                  try {
                     Files.deleteIfExists(fileRef.toPath());
                  } catch (Exception var7x) {
                  }

                  this.requestFromServer(id, quality);
               }
            });
            if (!submitted) {
               RemotePicture picture = this.pictures.get(id);
               if (picture != null) {
                  picture.getTexture(quality).setStatus(PictureTexture.Status.NOT_LOADED);
               }
            }
         } else {
            this.requestFromServer(id, quality);
         }
      });
   }

   void requestFromServer(UUID id, PictureQuality quality) {
      PictureKey key = new PictureKey(id, quality);
      if (this.inFlightNetworkRequests.add(key)) {
         try {
            CameraptureDebugStats.recordRequest(quality);
            Camerapture.NETWORK.sendToServer(new RequestDownloadPacket(id, quality));
         } catch (Exception e) {
            this.inFlightNetworkRequests.remove(key);
            RemotePicture picture = this.pictures.get(id);
            if (picture != null) {
               picture.getTexture(quality).setStatus(PictureTexture.Status.NOT_LOADED);
            }

            LOGGER.error("failed to send request for picture {} ({})", id, quality, e);
         }
      }
   }

   public void processReceivedImage(UUID id, PictureQuality quality, BufferedImage image) {
      RemotePicture picture = this.pictures.computeIfAbsent(id, RemotePicture::new);
      PictureTexture texture = picture.getTexture(quality);
      texture.setSize(image.getWidth(), image.getHeight());
      NativeImage nativeImage = NativeImageUtil.toNativeImage(image);
      Minecraft.getInstance().executeIfPossible(() -> {
         DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
         Minecraft.getInstance().getTextureManager().register(texture.getTextureIdentifier(), dynamicTexture);
         texture.setStatus(PictureTexture.Status.SUCCESS);
         this.getCache(quality).put(id, texture);
         this.retryStates.remove(new PictureKey(id, quality));
         CameraptureDebugStats.textureUploads.incrementAndGet();
      });
   }

   public void processReceivedBytes(UUID id, PictureQuality quality, byte[] bytes) {
      PictureKey key = new PictureKey(id, quality);
      boolean submitted = Camerapture.trySubmitImageTask(() -> {
         try {
            BufferedImage image = quality == PictureQuality.FULL ? decodeFullChecked(id, bytes) : decodeThumbnailChecked(id, bytes);
            this.processReceivedImage(id, quality, image);
            Camerapture.EXECUTOR.execute(() -> this.cacheBytesToDisk(id, quality, bytes));
         } catch (Exception e) {
            LOGGER.error("failed to decode received image bytes for image {} ({})", id, quality, e);
            this.processReceivedError(id, quality);
         } finally {
            this.inFlightNetworkRequests.remove(key);
         }
      });
      if (!submitted) {
         this.inFlightNetworkRequests.remove(key);
         RemotePicture picture = this.pictures.get(id);
         if (picture != null) {
            picture.getTexture(quality).setStatus(PictureTexture.Status.NOT_LOADED);
         }

         LOGGER.warn("Image worker saturated, deferred decode for {} ({})", id, quality);
      }
   }

   public void processQueue() {
      ClientPictureStore.QueuedBytes item;
      while ((item = this.byteQueue.poll()) != null) {
         this.processReceivedBytes(item.id(), item.quality(), item.bytes());
      }
   }

   public void processReceivedError(UUID id, PictureQuality quality, PictureErrorPacket.Reason reason) {
      PictureKey key = new PictureKey(id, quality);
      this.inFlightNetworkRequests.remove(key);
      if (reason == PictureErrorPacket.Reason.BUSY) {
         ClientPictureStore.RetryState prevState = this.retryStates.get(key);
         int failures = prevState != null ? prevState.consecutiveFailures() + 1 : 1;
         long baseDelay = Math.min(8000L, 250L * (1L << Math.min(failures - 1, 10)));
         long jitter = (long)(Math.random() * (baseDelay * 0.25));
         long delay = Math.min(8000L, baseDelay + jitter);
         long deadline = System.currentTimeMillis() + delay;
         this.retryStates.put(key, new ClientPictureStore.RetryState(failures, deadline));
         RemotePicture picture = this.pictures.get(id);
         if (picture != null) {
            picture.getTexture(quality).setStatus(PictureTexture.Status.NOT_LOADED);
         }

         LOGGER.warn("server busy for picture {} ({}), backing off for {}ms (attempt {})", id, quality, delay, failures);
      } else {
         this.retryStates.remove(key);
         RemotePicture picture = this.pictures.get(id);
         if (picture != null) {
            picture.getTexture(quality).setStatus(PictureTexture.Status.ERROR);
         }

         CameraptureDebugStats.missingPictures.incrementAndGet();
         LOGGER.error("remote error for picture {} ({})", id, quality);
      }
   }

   public void processReceivedError(UUID id, PictureQuality quality) {
      this.processReceivedError(id, quality, PictureErrorPacket.Reason.NOT_FOUND);
   }

   public void cacheBytesToDisk(UUID id, PictureQuality quality, byte[] bytes) {
      if (shouldCacheToDisk()) {
         try {
            Path path = this.getCacheFilePath(id, quality);
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
         } catch (IOException e) {
            LOGGER.error("could not cache picture {} ({})", id, quality, e);
         }
      }
   }

   public void clearRetryStates() {
      this.retryStates.clear();
      this.inFlightNetworkRequests.clear();
      this.pictures.clear();
   }

   private static BufferedImage decodeFullChecked(UUID id, byte[] bytes) throws IOException {
      WebPHeader.Size size = WebPHeader.read(bytes);
      if (size == null) {
         throw new IOException("picture " + id + " is not a readable WebP");
      } else {
         int limit = Math.min(CameraptureClient.syncedConfig != null ? CameraptureClient.syncedConfig.maxImageResolution() : 8192, 8192);
         if (size.width() <= limit && size.height() <= limit) {
            return ImageUtil.decodeImageFromWebP(bytes);
         } else {
            throw new IOException("refusing to decode picture " + id + " at " + size.width() + "x" + size.height() + ", over the " + limit + " limit");
         }
      }
   }

   private static BufferedImage decodeThumbnailChecked(UUID id, byte[] bytes) throws IOException {
      WebPHeader.Size size = WebPHeader.read(bytes);
      if (size == null) {
         throw new IOException("thumbnail " + id + " is not a readable WebP");
      } else {
         int permittedResolution = CameraptureClient.syncedConfig != null ? CameraptureClient.syncedConfig.thumbnailResolution() * 2 : 256;
         int limit = Math.min(Math.max(256, permittedResolution), 512);
         if (size.width() <= limit && size.height() <= limit) {
            return ImageUtil.decodeImageFromWebP(bytes);
         } else {
            throw new IOException("refusing to decode thumbnail " + id + " at " + size.width() + "x" + size.height() + ", over the " + limit + " limit");
         }
      }
   }

   public void clear() {
      this.retryStates.clear();
      this.byteQueue.clear();
      Minecraft.getInstance().executeIfPossible(() -> {
         this.fullCache.clear();
         this.thumbnailCache.clear();
         this.pictures.clear();
         this.inFlightNetworkRequests.clear();
      });
   }

   public long getFullTextureBytes() {
      return this.fullCache.getTextureBytes();
   }

   public long getThumbnailTextureBytes() {
      return this.thumbnailCache.getTextureBytes();
   }

   private static boolean shouldCacheToDisk() {
      return CameraptureClient.replayModInstalled
         || Camerapture.CONFIG_MANAGER.getConfig().client.cachePictures && !Minecraft.getInstance().hasSingleplayerServer();
   }

   private Path getCacheFilePath(UUID uuid, PictureQuality quality) {
      String subfolder = quality == PictureQuality.THUMBNAIL ? "thumbnails" : "full";
      return Camerapture.PLATFORM.getGameFolder().resolve("camerapture").resolve("picture-cache").resolve(subfolder).resolve(uuid + ".webp");
   }

   private Path getLegacyCacheFilePath(UUID uuid) {
      return Camerapture.PLATFORM.getGameFolder().resolve("camerapture").resolve("picture-cache").resolve(uuid + ".webp");
   }

   public static ClientPictureStore getInstance() {
      return INSTANCE;
   }

   private record QueuedBytes(UUID id, PictureQuality quality, byte[] bytes) {
   }

   public record RetryState(int consecutiveFailures, long retryAfterDeadline) {
   }

   public static class TextureCache {
      private final PictureQuality quality;
      private final long customMaxBytes;
      private final long customGraceMs;
      private final Map<UUID, ClientPictureStore.TextureCache.CacheEntry> entries = new LinkedHashMap<>(16, 0.75F, true);
      private long textureBytes = 0L;

      public TextureCache(PictureQuality quality) {
         this(quality, -1L, -1L);
      }

      public TextureCache(PictureQuality quality, long maxBytes, long graceMs) {
         this.quality = quality;
         this.customMaxBytes = maxBytes;
         this.customGraceMs = graceMs;
      }

      private long getMaxBytes() {
         if (this.customMaxBytes > 0L) {
            return this.customMaxBytes;
         }

         long budgetMiB = this.quality == PictureQuality.THUMBNAIL
            ? Camerapture.CONFIG_MANAGER.getConfig().client.thumbnailTextureBudgetMiB
            : Camerapture.CONFIG_MANAGER.getConfig().client.fullTextureBudgetMiB;
         return Math.max(8L, budgetMiB) * 1024L * 1024L;
      }

      private long getInUseGraceMs() {
         if (this.customGraceMs >= 0L) {
            return this.customGraceMs;
         } else {
            return this.quality == PictureQuality.THUMBNAIL ? 30000L : 5000L;
         }
      }

      public synchronized long getTextureBytes() {
         return this.textureBytes;
      }

      public synchronized List<UUID> getOrderedKeys() {
         return new ArrayList<>(this.entries.keySet());
      }

      public void touch(UUID id, PictureTexture texture) {
         this.touch(id, texture, System.currentTimeMillis());
      }

      public void touch(UUID id, PictureTexture texture, long now) {
         if (now - texture.getLastAccess() >= 500L) {
            synchronized (this) {
               texture.touch(now);
               this.entries.get(id);
            }
         }
      }

      public void put(UUID id, PictureTexture texture) {
         List<Entry<UUID, PictureTexture>> evicted = null;
         synchronized (this) {
            long newBytes = texture.getTextureBytes();
            ClientPictureStore.TextureCache.CacheEntry prev = this.entries.put(id, new ClientPictureStore.TextureCache.CacheEntry(texture, newBytes));
            if (prev != null) {
               this.textureBytes = this.textureBytes - prev.accountedBytes();
            }

            this.textureBytes += newBytes;
            texture.touch();
            long now = System.currentTimeMillis();
            long maxBytes = this.getMaxBytes();
            long graceMs = this.getInUseGraceMs();
            Iterator<Entry<UUID, ClientPictureStore.TextureCache.CacheEntry>> iterator = this.entries.entrySet().iterator();

            while (this.textureBytes > maxBytes && iterator.hasNext()) {
               Entry<UUID, ClientPictureStore.TextureCache.CacheEntry> eldest = iterator.next();
               if (now - eldest.getValue().texture().getLastAccess() < graceMs) {
                  break;
               }

               iterator.remove();
               this.textureBytes = this.textureBytes - eldest.getValue().accountedBytes();
               if (evicted == null) {
                  evicted = new ArrayList<>();
               }

               evicted.add(Map.entry(eldest.getKey(), eldest.getValue().texture()));
            }
         }

         if (evicted != null) {
            for (Entry<UUID, PictureTexture> entry : evicted) {
               this.releaseTexture(entry.getKey(), entry.getValue());
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
                     if (this.entries.containsKey(id)) {
                        return;
                     }
                  }

                  Minecraft.getInstance().getTextureManager().release(texture.getTextureIdentifier());
               });
            }
         } catch (Throwable var4) {
         }
      }

      public synchronized void clear() {
         for (ClientPictureStore.TextureCache.CacheEntry entry : this.entries.values()) {
            entry.texture().setStatus(PictureTexture.Status.NOT_LOADED);

            try {
               if (Minecraft.getInstance() != null && Minecraft.getInstance().getTextureManager() != null) {
                  Minecraft.getInstance().getTextureManager().release(entry.texture().getTextureIdentifier());
               }
            } catch (Throwable var4) {
            }
         }

         this.entries.clear();
         this.textureBytes = 0L;
      }

      private record CacheEntry(PictureTexture texture, long accountedBytes) {
      }
   }
}
