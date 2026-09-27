package me.chrr.camerapture;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.chrr.camerapture.net.clientbound.DownloadPartialPicturePacket;
import me.chrr.camerapture.picture.PictureKey;
import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.StoredPicture;
import net.minecraft.server.level.ServerPlayer;

public class DownloadQueue {
   private static final DownloadQueue INSTANCE = new DownloadQueue();
   private final Object lock = new Object();
   private final Map<UUID, DownloadQueue.PlayerQueue> byPlayer = new HashMap<>();
   private final Deque<DownloadQueue.PlayerQueue> rotation = new ArrayDeque<>();
   private ScheduledExecutorService scheduler;

   private DownloadQueue() {
   }

   public void send(ServerPlayer player, UUID id, PictureQuality quality, StoredPicture picture) {
      synchronized (this.lock) {
         DownloadQueue.PlayerQueue queue = this.byPlayer.get(player.getUUID());
         if (queue == null) {
            queue = new DownloadQueue.PlayerQueue(player);
            this.byPlayer.put(player.getUUID(), queue);
            this.rotation.add(queue);
         }

         PictureKey key = new PictureKey(id, quality);
         if (queue.pendingKeys.add(key)) {
            queue.pending.add(new DownloadQueue.QueuedPicture(id, quality, picture));
         }
      }
   }

   public void start(long intervalMs) {
      if (this.scheduler == null) {
         this.scheduler = Executors.newSingleThreadScheduledExecutor();
         this.scheduler.scheduleAtFixedRate(this::processQueue, 0L, intervalMs, TimeUnit.MILLISECONDS);
      }
   }

   public void stop() {
      if (this.scheduler != null) {
         synchronized (this.lock) {
            this.byPlayer.clear();
            this.rotation.clear();
         }

         this.scheduler.shutdown();
         this.scheduler = null;
      }
   }

   private void processQueue() {
      ServerPlayer recipient;
      DownloadQueue.QueuedPicture item;
      synchronized (this.lock) {
         while (true) {
            DownloadQueue.PlayerQueue queue = this.rotation.poll();
            if (queue == null) {
               return;
            }

            if (!queue.player.hasDisconnected()) {
               item = queue.pending.poll();
               if (item != null) {
                  queue.pendingKeys.remove(new PictureKey(item.id(), item.quality()));
                  recipient = queue.player;
                  if (queue.pending.isEmpty()) {
                     this.byPlayer.remove(queue.player.getUUID());
                  } else {
                     this.rotation.add(queue);
                  }
                  break;
               }

               this.byPlayer.remove(queue.player.getUUID());
            } else {
               this.byPlayer.remove(queue.player.getUUID());
            }
         }
      }

      ServerPlayer target = recipient;
      DownloadQueue.QueuedPicture sending = item;
      ByteCollector.split(
         sending.picture().bytes(),
         1000000,
         (section, bytesLeft) -> Camerapture.NETWORK
            .sendToClient(target, new DownloadPartialPicturePacket(sending.id(), sending.quality(), section, bytesLeft))
      );
   }

   public static DownloadQueue getInstance() {
      return INSTANCE;
   }

   private static class PlayerQueue {
      private final ServerPlayer player;
      private final Deque<DownloadQueue.QueuedPicture> pending = new ArrayDeque<>();
      private final Set<PictureKey> pendingKeys = new HashSet<>();

      private PlayerQueue(ServerPlayer player) {
         this.player = player;
      }
   }

   private record QueuedPicture(UUID id, PictureQuality quality, StoredPicture picture) {
   }
}
