package me.chrr.camerapture;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.chrr.camerapture.compat.FirstPersonModelCompat;
import me.chrr.camerapture.config.SyncedConfig;
import me.chrr.camerapture.gui.PictureScreen;
import me.chrr.camerapture.gui.UploadScreen;
import me.chrr.camerapture.item.AlbumItem;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.net.clientbound.DownloadPartialPicturePacket;
import me.chrr.camerapture.net.clientbound.PictureErrorPacket;
import me.chrr.camerapture.net.clientbound.RequestUploadPacket;
import me.chrr.camerapture.net.clientbound.SyncConfigPacket;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureKey;
import me.chrr.camerapture.picture.PictureTaker;
import me.chrr.camerapture.render.PictureItemRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class CameraptureClient {
   public static final float MIN_ZOOM = 1.0F;
   public static final float MAX_ZOOM = 6.0F;
   public static final PictureItemRenderer PICTURE_ITEM_RENDERER = new PictureItemRenderer();
   public static boolean replayModInstalled = false;
   public static SyncedConfig syncedConfig;

   public static void init() {
      ClientPictureStore.getInstance().clear();
      syncedConfig = SyncedConfig.fromServerConfig(Camerapture.CONFIG_MANAGER.getConfig().server);
      if (Camerapture.PLATFORM.isModLoaded("firstperson")) {
         FirstPersonModelCompat.register();
      }

      if (Camerapture.PLATFORM.isModLoaded("replay-mod")) {
         Camerapture.LOGGER.info("Replay Mod is detected, Camerapture will cache pictures, regardless of config.");
         replayModInstalled = true;
      }
   }

   public static void registerPacketHandlers() {
      Camerapture.NETWORK.onReceiveFromServer(RequestUploadPacket.class, packet -> {
         boolean submitted = Camerapture.trySubmitImageTask(() -> PictureTaker.getInstance().uploadStoredPicture(packet.uuid()));
         if (!submitted) {
            Camerapture.LOGGER.warn("Image worker saturated, failing upload for {}", packet.uuid());
            PictureTaker.getInstance().clearPendingPicture();
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
               player.sendSystemMessage(Component.translatable("text.camerapture.upload_failed").withStyle(ChatFormatting.RED));
            }
         }
      });
      Map<PictureKey, ByteCollector> collectors = new ConcurrentHashMap<>();
      Camerapture.NETWORK.onReceiveFromServer(DownloadPartialPicturePacket.class, packet -> {
         PictureKey key = new PictureKey(packet.uuid(), packet.quality());
         ByteCollector collector;
         synchronized (collectors) {
            collector = collectors.computeIfAbsent(key, k -> new ByteCollector(bytes -> {
               collectors.remove(key);
               ClientPictureStore.getInstance().processReceivedBytes(packet.uuid(), packet.quality(), bytes);
            }));
         }

         synchronized (collector) {
            if (!collector.push(packet.bytes(), packet.bytesLeft())) {
               Camerapture.LOGGER.error("received malformed byte section from server for {}", key);
               ClientPictureStore.getInstance().processReceivedError(packet.uuid(), packet.quality());
            }
         }
      });
      Camerapture.NETWORK.onReceiveFromServer(PictureErrorPacket.class, packet -> {
         ClientPictureStore.getInstance().processReceivedError(packet.uuid(), packet.quality(), packet.reason());
         collectors.remove(new PictureKey(packet.uuid(), packet.quality()));
      });
      Camerapture.NETWORK.onReceiveFromServer(SyncConfigPacket.class, packet -> {
         Camerapture.LOGGER.info("received synced config: {}", packet.syncedConfig());
         syncedConfig = packet.syncedConfig();
      });
   }

   public static InteractionResult onUseItem(Player player, ItemStack stack) {
      Minecraft client = Minecraft.getInstance();
      if (client.player != player) {
         return InteractionResult.PASS;
      }

      if (stack.is(Camerapture.PICTURE)) {
         if (PictureItem.getPictureData(stack) != null) {
            client.execute(() -> client.setScreen(new PictureScreen(List.of(stack))));
            return InteractionResult.SUCCESS;
         }
      } else if (stack.is(Camerapture.ALBUM) && !player.isShiftKeyDown()) {
         List<ItemStack> pictures = AlbumItem.getPictures(stack);
         if (!pictures.isEmpty()) {
            client.execute(() -> client.setScreen(new PictureScreen(pictures)));
            return InteractionResult.SUCCESS;
         }
      } else if (syncedConfig.permissionLevels().canUpload(player)
         && player.isShiftKeyDown()
         && stack.is(Camerapture.CAMERA)
         && !CameraItem.isActive(stack)
         && !player.getCooldowns().isOnCooldown(stack.getItem())) {
         client.execute(() -> client.setScreen(new UploadScreen()));
         return InteractionResult.SUCCESS;
      }

      return InteractionResult.PASS;
   }
}
