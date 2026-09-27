package me.chrr.camerapture;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import me.chrr.camerapture.block.PictureFrameBlock;
import me.chrr.camerapture.block.PictureFrameBlockEntity;
import me.chrr.camerapture.config.ConfigManager;
import me.chrr.camerapture.entity.LegacyPictureFrameEntity;
import me.chrr.camerapture.gui.AlbumLecternMenu;
import me.chrr.camerapture.gui.AlbumMenu;
import me.chrr.camerapture.gui.PictureFrameMenu;
import me.chrr.camerapture.item.AlbumCloningRecipe;
import me.chrr.camerapture.item.AlbumItem;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.item.PictureCloningRecipe;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.net.NetworkAdapter;
import me.chrr.camerapture.net.clientbound.PictureErrorPacket;
import me.chrr.camerapture.net.clientbound.RequestUploadPacket;
import me.chrr.camerapture.net.serverbound.NewPicturePacket;
import me.chrr.camerapture.net.serverbound.RequestDownloadPacket;
import me.chrr.camerapture.net.serverbound.UploadPartialPicturePacket;
import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.ServerPictureStore;
import me.chrr.camerapture.picture.StoredPicture;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityType.Builder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Camerapture {
   public static final String MOD_ID = "camerapture";
   public static final Logger LOGGER = LogManager.getLogger("Camerapture");
   public static final Executor EXECUTOR = createIOExecutor();
   public static final ConfigManager CONFIG_MANAGER = new ConfigManager();
   public static final ImageTaskExecutor IMAGE_TASK_EXECUTOR = new ImageTaskExecutor(
      Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors() / 4)), 256, "camerapture-image"
   );
   public static final Executor IMAGE_EXECUTOR = IMAGE_TASK_EXECUTOR.getExecutor();
   public static final PlatformAdapter PLATFORM = ServiceLoader.load(PlatformAdapter.class).iterator().next();
   public static final NetworkAdapter NETWORK = PLATFORM.createNetworkAdapter();
   public static final int CLIENT_SECTION_SIZE = 30000;
   public static final int SERVER_SECTION_SIZE = 1000000;
   public static Item CAMERA = new CameraItem();
   public static final SoundEvent CAMERA_SHUTTER = SoundEvent.createVariableRangeEvent(id("camera_shutter"));
   public static final ResourceLocation PICTURES_TAKEN = id("pictures_taken");
   public static Item PICTURE = new PictureItem();
   public static final RecipeSerializer<PictureCloningRecipe> PICTURE_CLONING = new SimpleCraftingRecipeSerializer(PictureCloningRecipe::new);
   public static final Item ALBUM = new AlbumItem();
   public static final MenuType<AlbumMenu> ALBUM_SCREEN_HANDLER = new MenuType(AlbumMenu::new, FeatureFlagSet.of());
   public static final MenuType<AlbumLecternMenu> ALBUM_LECTERN_SCREEN_HANDLER = new MenuType(
      (containerId, playerInventory) -> new AlbumLecternMenu(containerId), FeatureFlagSet.of()
   );
   public static final RecipeSerializer<AlbumCloningRecipe> ALBUM_CLONING = new SimpleCraftingRecipeSerializer(AlbumCloningRecipe::new);
   public static final Block PICTURE_FRAME_BLOCK = new PictureFrameBlock();
   public static final BlockEntityType<PictureFrameBlockEntity> PICTURE_FRAME_BLOCK_ENTITY = Builder.of(
         PictureFrameBlockEntity::new, new Block[]{PICTURE_FRAME_BLOCK}
      )
      .build(null);
   public static final MenuType<PictureFrameMenu> PICTURE_FRAME_SCREEN_HANDLER = new MenuType(
      (containerId, pi) -> new PictureFrameMenu(containerId), FeatureFlagSet.of()
   );
   public static final EntityType<LegacyPictureFrameEntity> LEGACY_PICTURE_FRAME = net.minecraft.world.entity.EntityType.Builder.of(
         LegacyPictureFrameEntity::new, MobCategory.MISC
      )
      .sized(0.5F, 0.5F)
      .clientTrackingRange(10)
      .build("picture_frame");
   public static final DataComponentType<PictureItem.PictureData> PICTURE_DATA = DataComponentType.<PictureItem.PictureData>builder()
      .persistent(PictureItem.PictureData.CODEC)
      .networkSynchronized(PictureItem.PictureData.PACKET_CODEC)
      .build();
   public static final DataComponentType<Boolean> CAMERA_ACTIVE = DataComponentType.<Boolean>builder()
      .persistent(Codec.BOOL)
      .networkSynchronized(ByteBufCodecs.BOOL)
      .build();

   private static Executor createIOExecutor() {
      int threads = Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
      AtomicInteger counter = new AtomicInteger();
      ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
         Thread thread = new Thread(runnable, "camerapture-io-" + counter.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      });
      executor.allowCoreThreadTimeOut(true);
      return executor;
   }

   public static boolean trySubmitImageTask(Runnable task) {
      return IMAGE_TASK_EXECUTOR.trySubmit(task);
   }

   public static void registerPacketHandlers() {
      NETWORK.onReceiveFromClient(NewPicturePacket.class, (packet, player) -> {
         CameraItem.HeldCamera camera = CameraItem.find(player, false);
         if (camera != null) {
            if (player.isCreative() || ContainerHelper.clearOrCountMatchingItems(player.getInventory(), stack -> stack.is(Items.PAPER), 1, false) == 1) {
               if (CameraItem.isActive(camera.stack())) {
                  player.level().playSound(null, player, CAMERA_SHUTTER, SoundSource.PLAYERS, 1.0F, 1.0F);
               }

               CameraItem.setActive(camera.stack(), false);
               player.getCooldowns().addCooldown(camera.stack().getItem(), 60);
               player.swing(camera.hand(), true);
               player.awardStat(PICTURES_TAKEN);
               UUID id = ServerPictureStore.getInstance().reserveId();
               NETWORK.sendToClient(player, new RequestUploadPacket(id));
            }
         }
      });
      Map<UUID, ByteCollector> collectors = new ConcurrentHashMap<>();
      NETWORK.onReceiveFromClient(UploadPartialPicturePacket.class, (packet, player) -> {
         if (!ServerPictureStore.getInstance().isReserved(packet.uuid())) {
            LOGGER.error("{} tried to send a byte section for an unreserved UUID", player.getName().toString());
         } else if (packet.bytesLeft() >= 0 && packet.bytesLeft() <= CONFIG_MANAGER.getConfig().server.maxImageBytes) {
            ByteCollector collector;
            synchronized (collectors) {
               collector = collectors.computeIfAbsent(packet.uuid(), uuid -> new ByteCollector(bytes -> {
                  collectors.remove(uuid);
                  boolean submitted = trySubmitImageTask(() -> {
                     try {
                        ServerPictureStore.PreparedPicture prepared = ServerPictureStore.getInstance().prepare(uuid, bytes);
                        EXECUTOR.execute(() -> {
                           try {
                              MinecraftServer server = player.server;
                              ServerPictureStore.getInstance().save(server, prepared);
                              ItemStack picture = PictureItem.create(player.getName().getString(), uuid);
                              server.execute(() -> player.getInventory().placeItemBackInInventory(picture));
                           } catch (Exception ex) {
                              LOGGER.error("failed to save picture from {}", player.getName().getString(), ex);
                              player.sendSystemMessage(Component.translatable("text.camerapture.picture_failed").withStyle(ChatFormatting.RED), false);
                           }
                        });
                     } catch (Exception e) {
                        LOGGER.error("failed to process picture from {}", player.getName().getString(), e);
                        ServerPictureStore.getInstance().unreserveId(uuid);
                        player.sendSystemMessage(Component.translatable("text.camerapture.picture_failed").withStyle(ChatFormatting.RED), false);
                     }
                  });
                  if (!submitted) {
                     LOGGER.error("Image worker saturated, rejecting picture save for {}", uuid);
                     ServerPictureStore.getInstance().unreserveId(uuid);
                     player.sendSystemMessage(Component.translatable("text.camerapture.picture_failed").withStyle(ChatFormatting.RED), false);
                  }
               }));
            }

            synchronized (collector) {
               if (!collector.push(packet.bytes(), packet.bytesLeft())) {
                  LOGGER.error("{} sent a malformed byte section", player.getName().getString());
                  collectors.remove(packet.uuid());
                  ServerPictureStore.getInstance().unreserveId(packet.uuid());
               } else if (collector.getCurrentLength() > CONFIG_MANAGER.getConfig().server.maxImageBytes) {
                  LOGGER.error("{} sent a picture exceeding the size limit", player.getName().getString());
                  collectors.remove(packet.uuid());
                  ServerPictureStore.getInstance().unreserveId(packet.uuid());
               }
            }
         } else {
            LOGGER.error("{} sent a picture with invalid or oversized byte length ({} bytes left)", player.getName().getString(), packet.bytesLeft());
            collectors.remove(packet.uuid());
            ServerPictureStore.getInstance().unreserveId(packet.uuid());
         }
      });
      NETWORK.onReceiveFromClient(RequestDownloadPacket.class, (packet, player) -> {
         if (packet.quality() == PictureQuality.THUMBNAIL) {
            ServerPictureStore.getInstance().getOrGenerateThumbnailAsync(player.server, packet.uuid()).thenAccept(result -> {
               if (result.type() == ServerPictureStore.ThumbnailResultType.SUCCESS && result.picture() != null) {
                  DownloadQueue.getInstance().send(player, packet.uuid(), PictureQuality.THUMBNAIL, result.picture());
               } else if (result.type() == ServerPictureStore.ThumbnailResultType.BUSY) {
                  NETWORK.sendToClient(player, new PictureErrorPacket(packet.uuid(), PictureQuality.THUMBNAIL, PictureErrorPacket.Reason.BUSY));
               } else {
                  LOGGER.warn("{} requested a thumbnail with an unknown UUID: {}", player.getName().getString(), packet.uuid());
                  NETWORK.sendToClient(player, new PictureErrorPacket(packet.uuid(), PictureQuality.THUMBNAIL, PictureErrorPacket.Reason.NOT_FOUND));
               }
            });
         } else {
            EXECUTOR.execute(() -> {
               try {
                  StoredPicture picture = ServerPictureStore.getInstance().get(player.server, packet.uuid(), packet.quality());
                  if (picture != null) {
                     DownloadQueue.getInstance().send(player, packet.uuid(), packet.quality(), picture);
                     return;
                  }

                  LOGGER.warn("{} requested a picture with an unknown UUID: {} ({})", player.getName().getString(), packet.uuid(), packet.quality());
                  NETWORK.sendToClient(player, new PictureErrorPacket(packet.uuid(), packet.quality(), PictureErrorPacket.Reason.NOT_FOUND));
               } catch (Exception e) {
                  LOGGER.error("failed to load picture for {} ({}): {}", player.getName().getString(), packet.uuid(), packet.quality(), e);
                  NETWORK.sendToClient(player, new PictureErrorPacket(packet.uuid(), packet.quality(), PictureErrorPacket.Reason.BUSY));
               }
            });
         }
      });
   }

   public static ResourceLocation id(String path) {
      return ResourceLocation.fromNamespaceAndPath("camerapture", path);
   }
}
