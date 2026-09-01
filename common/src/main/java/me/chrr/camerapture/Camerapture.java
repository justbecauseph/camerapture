package me.chrr.camerapture;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import me.chrr.camerapture.config.ConfigManager;
import me.chrr.camerapture.gui.AlbumLecternMenu;
import me.chrr.camerapture.gui.AlbumMenu;
import me.chrr.camerapture.gui.PictureFrameMenu;
import me.chrr.camerapture.item.*;
import me.chrr.camerapture.net.NetworkAdapter;
import me.chrr.camerapture.net.clientbound.PictureErrorPacket;
import me.chrr.camerapture.net.clientbound.RequestUploadPacket;
import me.chrr.camerapture.net.serverbound.NewPicturePacket;
import me.chrr.camerapture.net.serverbound.RequestDownloadPacket;
import me.chrr.camerapture.net.serverbound.UploadPartialPicturePacket;
import me.chrr.camerapture.block.PictureFrameBlock;
import me.chrr.camerapture.block.PictureFrameBlockEntity;
import me.chrr.camerapture.picture.ServerPictureStore;
import me.chrr.camerapture.picture.StoredPicture;
import me.chrr.tapestry.base.Tapestry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Camerapture {
    public static final String MOD_ID = "camerapture";
    public static final Logger LOGGER = LogManager.getLogger("Camerapture");

    public static final Executor EXECUTOR = createIOExecutor();
    public static final Executor IMAGE_EXECUTOR = createImageExecutor();
    public static final ConfigManager CONFIG_MANAGER = new ConfigManager();

    /// General & I/O executor for disk reads/writes, network serialization, and lightweight tasks.
    private static Executor createIOExecutor() {
        int threads = Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors() / 2));

        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "camerapture-io-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
        );

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /// Dedicated CPU-bound executor for WebP image compression and decompression.
    /// Capped to 2-4 threads with a bounded work queue to prevent CPU starvation and GC spikes.
    private static Executor createImageExecutor() {
        int threads = Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors() / 4));

        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256),
                runnable -> {
                    Thread thread = new Thread(runnable, "camerapture-image-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy()
        );

        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    /// Safely submit a task to the image executor without running on the caller thread on saturation.
    public static boolean trySubmitImageTask(Runnable task) {
        try {
            IMAGE_EXECUTOR.execute(task);
            return true;
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return false;
        }
    }

    private static PlatformAdapter loadPlatformAdapter() {
        try {
            return Tapestry.implementation(id("platform_adapter"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static NetworkAdapter loadNetworkAdapter() {
        try {
            return Tapestry.implementation(id("network_adapter"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static final PlatformAdapter PLATFORM = loadPlatformAdapter();
    public static final NetworkAdapter NETWORK = loadNetworkAdapter();

    // Server-bound packets have a way lower limit on size.
    public static final int CLIENT_SECTION_SIZE = 30_000;
    public static final int SERVER_SECTION_SIZE = 1_000_000;

    private static <T> T safeInit(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // Camera
    public static Item CAMERA = safeInit(CameraItem::new);
    public static final SoundEvent CAMERA_SHUTTER = safeInit(() -> SoundEvent.createVariableRangeEvent(id("camera_shutter")));
    public static final Identifier PICTURES_TAKEN = id("pictures_taken");

    // Picture
    public static Item PICTURE = safeInit(PictureItem::new);
    public static final RecipeSerializer<PictureCloningRecipe> PICTURE_CLONING = safeInit(() -> new RecipeSerializer<>(
            MapCodec.unit(PictureCloningRecipe.INSTANCE), StreamCodec.unit(PictureCloningRecipe.INSTANCE)));

    // Album
    public static final Item ALBUM = safeInit(AlbumItem::new);
    public static final MenuType<AlbumMenu> ALBUM_SCREEN_HANDLER = safeInit(() -> new MenuType<>(AlbumMenu::new, FeatureFlagSet.of()));
    public static final MenuType<AlbumLecternMenu> ALBUM_LECTERN_SCREEN_HANDLER = safeInit(() ->
            new MenuType<>((containerId, playerInventory) -> new AlbumLecternMenu(containerId), FeatureFlagSet.of()));
    public static final RecipeSerializer<AlbumCloningRecipe> ALBUM_CLONING = safeInit(() -> new RecipeSerializer<>(
            MapCodec.unit(AlbumCloningRecipe.INSTANCE), StreamCodec.unit(AlbumCloningRecipe.INSTANCE)));

    // Picture Frame Block
    public static final Block PICTURE_FRAME_BLOCK = safeInit(PictureFrameBlock::new);
    public static final BlockEntityType<PictureFrameBlockEntity> PICTURE_FRAME_BLOCK_ENTITY = safeInit(() ->
            new BlockEntityType<>(PictureFrameBlockEntity::new, java.util.Set.of(PICTURE_FRAME_BLOCK)));
    public static final MenuType<PictureFrameMenu> PICTURE_FRAME_SCREEN_HANDLER = safeInit(() ->
            new MenuType<>((containerId, pi) -> new PictureFrameMenu(containerId), FeatureFlagSet.of()));

    // Data Components
    public static final DataComponentType<PictureItem.PictureData> PICTURE_DATA = safeInit(() -> DataComponentType.<PictureItem.PictureData>builder()
            .persistent(PictureItem.PictureData.CODEC).networkSynchronized(PictureItem.PictureData.PACKET_CODEC)
            .build());
    public static final DataComponentType<Boolean> CAMERA_ACTIVE = safeInit(() -> DataComponentType.<Boolean>builder()
            .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL)
            .build());

    public static void registerPacketHandlers() {
        // Client requests to take / upload a picture
        NETWORK.onReceiveFromClient(NewPicturePacket.class, (packet, player) -> {
            CameraItem.HeldCamera camera = CameraItem.find(player, false);
            if (camera == null) {
                return;
            }

            // If the player is in creative mode, skip taking any paper.
            if (!player.isCreative()) {
                if (ContainerHelper.clearOrCountMatchingItems(player.getInventory(), (stack) -> stack.is(Items.PAPER), 1, false) != 1) {
                    return;
                }
            }

            // We don't want to play the sound when the player is uploading a picture, only when it's being taken.
            if (CameraItem.isActive(camera.stack())) {
                //noinspection resource: we don't want to close the level.
                player.level().playSound(null, player, CAMERA_SHUTTER, SoundSource.PLAYERS, 1f, 1f);
            }

            CameraItem.setActive(camera.stack(), false);
            player.getCooldowns().addCooldown(camera.stack(), 20 * 3);
            player.swing(camera.hand(), true);

            player.awardStat(PICTURES_TAKEN);

            UUID id = ServerPictureStore.getInstance().reserveId();
            NETWORK.sendToClient(player, new RequestUploadPacket(id));
        });

        // Client sends back a picture following a take-picture request
        Map<UUID, ByteCollector> collectors = new ConcurrentHashMap<>();
        NETWORK.onReceiveFromClient(UploadPartialPicturePacket.class, (packet, player) -> {
            if (!ServerPictureStore.getInstance().isReserved(packet.uuid())) {
                LOGGER.error("{} tried to send a byte section for an unreserved UUID", player.getName().toString());
                return;
            }

            if (packet.bytesLeft() < 0 || packet.bytesLeft() > CONFIG_MANAGER.getConfig().server.maxImageBytes) {
                LOGGER.error("{} sent a picture with invalid or oversized byte length ({} bytes left)", player.getName().getString(), packet.bytesLeft());
                collectors.remove(packet.uuid());
                ServerPictureStore.getInstance().unreserveId(packet.uuid());
                return;
            }

            ByteCollector collector;
            synchronized (collectors) {
                collector = collectors.computeIfAbsent(packet.uuid(), (uuid) -> new ByteCollector((bytes) -> {
                    collectors.remove(uuid);
                    boolean submitted = trySubmitImageTask(() -> {
                        try {
                            ServerPictureStore.PreparedPicture prepared = ServerPictureStore.getInstance().prepare(uuid, bytes);
                            EXECUTOR.execute(() -> {
                                try {
                                    MinecraftServer server = player.server;
                                    ServerPictureStore.getInstance().save(server, prepared);
                                    ItemStack picture = PictureItem.create(player.getName().getString(), uuid);

                                    // We have to do this on a separate thread, because it might spawn an item entity.
                                    server.execute(() -> player.getInventory().placeItemBackInInventory(picture));
                                } catch (Exception e) {
                                    LOGGER.error("failed to save picture from {}", player.getName().getString(), e);
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
                    return;
                }

                if (collector.getCurrentLength() > CONFIG_MANAGER.getConfig().server.maxImageBytes) {
                    LOGGER.error("{} sent a picture exceeding the size limit", player.getName().getString());
                    collectors.remove(packet.uuid());
                    ServerPictureStore.getInstance().unreserveId(packet.uuid());
                    return;
                }
            }
        });

        // Client requests a picture with a certain UUID and quality
        NETWORK.onReceiveFromClient(RequestDownloadPacket.class, (packet, player) -> {
            // Packet handlers run on the server thread on both loaders, and a cache miss here reads the
            // picture off the disk. Do that on the executor instead, like uploads already do.
            EXECUTOR.execute(() -> {
                try {
                    StoredPicture picture = ServerPictureStore.getInstance().get(player.server, packet.uuid(), packet.quality());

                    if (picture != null) {
                        DownloadQueue.getInstance().send(player, packet.uuid(), packet.quality(), picture);
                        return;
                    }

                    // If thumbnail requested but missing, check if original exists and generate lazily on IMAGE_EXECUTOR
                    if (packet.quality() == me.chrr.camerapture.picture.PictureQuality.THUMBNAIL) {
                        StoredPicture fullPicture = ServerPictureStore.getInstance().get(player.server, packet.uuid(), me.chrr.camerapture.picture.PictureQuality.FULL);
                        if (fullPicture != null) {
                            boolean submitted = trySubmitImageTask(() -> {
                                try {
                                    int thumbRes = CONFIG_MANAGER.getConfig().server.thumbnailResolution;
                                    byte[] thumbBytes = me.chrr.camerapture.util.ImageUtil.createThumbnail(fullPicture.bytes(), thumbRes);
                                    StoredPicture thumbPicture = new StoredPicture(thumbBytes);
                                    ServerPictureStore.getInstance().saveThumbnail(player.server, packet.uuid(), thumbPicture);
                                    DownloadQueue.getInstance().send(player, packet.uuid(), me.chrr.camerapture.picture.PictureQuality.THUMBNAIL, thumbPicture);
                                } catch (Exception e) {
                                    LOGGER.error("failed to generate lazy thumbnail for {} ({})", packet.uuid(), packet.quality(), e);
                                    NETWORK.sendToClient(player, new PictureErrorPacket(packet.uuid(), packet.quality()));
                                }
                            });
                            if (submitted) {
                                return;
                            }
                        }
                    }

                    LOGGER.warn("{} requested a picture with an unknown UUID: {} ({})", player.getName().getString(), packet.uuid(), packet.quality());
                    NETWORK.sendToClient(player, new PictureErrorPacket(packet.uuid(), packet.quality()));
                } catch (Exception e) {
                    LOGGER.error("failed to load picture for {} ({}): {}", player.getName().getString(), packet.uuid(), packet.quality(), e);
                    NETWORK.sendToClient(player, new PictureErrorPacket(packet.uuid(), packet.quality()));
                }
            });
        });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("camerapture", path);
    }
}
