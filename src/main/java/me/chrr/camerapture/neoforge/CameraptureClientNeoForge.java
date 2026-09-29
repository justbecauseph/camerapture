package me.chrr.camerapture.neoforge;

import java.util.Objects;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.CameraptureClient;
import me.chrr.camerapture.compat.ClothConfigScreenFactory;
import me.chrr.camerapture.config.SyncedConfig;
import me.chrr.camerapture.gui.AlbumLecternScreen;
import me.chrr.camerapture.gui.AlbumScreen;
import me.chrr.camerapture.gui.CameraViewFinder;
import me.chrr.camerapture.gui.PictureFrameMenu;
import me.chrr.camerapture.gui.PictureFrameScreen;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.net.serverbound.OpenPictureFramePacket;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureTaker;
import me.chrr.camerapture.render.PictureItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.neoforged.neoforge.client.event.InputEvent.MouseScrollingEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent.Post;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.NotNull;

@Mod(value = "camerapture", dist = Dist.CLIENT)
public class CameraptureClientNeoForge {
   public CameraptureClientNeoForge(ModContainer mod) {
      Objects.requireNonNull(mod.getEventBus()).register(this);
      NeoForge.EVENT_BUS.register(new CameraptureClientNeoForge.ClientEvents());
      if (ModList.get().isLoaded("cloth_config")) {
         mod.registerExtensionPoint(IConfigScreenFactory.class, (IConfigScreenFactory)(container, parent) -> ClothConfigScreenFactory.create(parent));
      }
   }

   @SubscribeEvent
   public void setup(FMLClientSetupEvent event) {
      CameraptureClient.init();
      ItemProperties.register(
         Camerapture.PICTURE, Camerapture.id("should_render_picture"), (stack, level, entity, seed) -> PictureItemRenderer.canRender(stack) ? 1.0F : 0.0F
      );
   }

   @SubscribeEvent
   public void registerHandledScreens(RegisterMenuScreensEvent event) {
      event.register(Camerapture.PICTURE_FRAME_SCREEN_HANDLER, PictureFrameScreen::new);
      event.register(Camerapture.ALBUM_SCREEN_HANDLER, AlbumScreen::new);
      event.register(Camerapture.ALBUM_LECTERN_SCREEN_HANDLER, AlbumLecternScreen::new);
   }

   @SubscribeEvent
   public void registerPackets(RegisterPayloadHandlersEvent event) {
      CameraptureClient.registerPacketHandlers();
   }

   @SubscribeEvent
   public void registerEntityRenderers(RegisterRenderers event) {
      event.registerBlockEntityRenderer(Camerapture.PICTURE_FRAME_BLOCK_ENTITY, NeoPictureFrameBlockEntityRenderer::new);
      event.registerEntityRenderer(Camerapture.LEGACY_PICTURE_FRAME, NoopRenderer::new);
   }

   @SubscribeEvent
   public void registerClientExtensions(RegisterClientExtensionsEvent event) {
      event.registerItem(new IClientItemExtensions() {
         public ArmPose getArmPose(@NotNull LivingEntity entity, @NotNull InteractionHand hand, @NotNull ItemStack stack) {
            return CameraItem.isActive(stack) ? ArmPose.BOW_AND_ARROW : null;
         }
      }, new Item[]{Camerapture.CAMERA});
   }

   private static class ClientEvents {
      private BlockPos pendingFrame;
      private int pendingAtTick;

      @SubscribeEvent
      public void onInteractionKey(InteractionKeyMappingTriggered event) {
         if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND) {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft.player;
            if (player != null
               && minecraft.options.keyShift.isDown()
               && player.getMainHandItem().isEmpty()
               && minecraft.hitResult instanceof BlockHitResult hit
               && minecraft.level.getBlockState(hit.getBlockPos()).is(Camerapture.PICTURE_FRAME_BLOCK)) {
               if (this.pendingFrame == null
                  || !this.pendingFrame.equals(hit.getBlockPos())
                  || player.tickCount - this.pendingAtTick >= 100) {
                  this.pendingFrame = hit.getBlockPos().immutable();
                  this.pendingAtTick = player.tickCount;
                  minecraft.getConnection().send(new ServerboundSetCarriedItemPacket(player.getInventory().selected));
                  Camerapture.NETWORK.sendToServer(new OpenPictureFramePacket(this.pendingFrame, hit.getLocation()));
               }

               event.setSwingHand(false);
               event.setCanceled(true);
               return;
            }
         }

         if (event.isAttack()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
               CameraItem.HeldCamera camera = CameraItem.find(player, true);
               if (camera != null) {
                  if (CameraItem.canTakePicture(player)) {
                     PictureTaker.getInstance().takePicture();
                  }

                  event.setSwingHand(false);
                  event.setCanceled(true);
               }
            }
         }
      }

      @SubscribeEvent
      public InteractionResult onUseItem(RightClickItem event) {
         if (event.getSide() != LogicalSide.CLIENT) {
            return InteractionResult.PASS;
         }

         ItemStack stack = event.getItemStack();
         Player player = event.getEntity();
         return CameraptureClient.onUseItem(player, stack);
      }

      @SubscribeEvent
      public void onRenderTickEnd(Post event) {
         PictureTaker.getInstance().renderTickEnd();
      }

      @SubscribeEvent
      public void onDisconnect(LoggingOut event) {
         this.pendingFrame = null;
         ClientPictureStore.getInstance().clear();
         CameraptureClient.syncedConfig = SyncedConfig.fromServerConfig(Camerapture.CONFIG_MANAGER.getConfig().server);
      }

      @SubscribeEvent
      public void onRenderHand(RenderHandEvent event) {
         CameraItem.HeldCamera camera = CameraItem.find(Minecraft.getInstance().player, true);
         if (camera != null) {
            event.setCanceled(true);
         }
      }

      @SubscribeEvent
      public void onRenderGui(Pre event) {
         CameraItem.HeldCamera camera = CameraItem.find(Minecraft.getInstance().player, true);
         if (camera != null) {
            event.setCanceled(true);
            if (event.getName() == VanillaGuiLayers.CROSSHAIR && !Minecraft.getInstance().options.hideGui) {
               CameraViewFinder.drawCameraViewFinder(event.getGuiGraphics(), Minecraft.getInstance().font);
            }
         } else {
            PictureTaker.getInstance().zoomLevel = 1.0F;
         }
      }

      @SubscribeEvent
      public void onScroll(MouseScrollingEvent event) {
         if (CameraItem.find(Minecraft.getInstance().player, true) != null) {
            PictureTaker.getInstance().zoom((float)(event.getScrollDeltaY() / 4.0));
            event.setCanceled(true);
         }
      }

      @SubscribeEvent(priority = EventPriority.LOW)
      public void onFovModifier(ComputeFovModifierEvent event) {
         if (CameraItem.find(Minecraft.getInstance().player, true) != null) {
            event.setNewFovModifier(PictureTaker.getInstance().getFovModifier());
         }
      }

      @SubscribeEvent
      public void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Pre event) {
         ClientPictureStore.getInstance().processQueue();

         Minecraft minecraft = Minecraft.getInstance();
         LocalPlayer player = minecraft.player;
         if (player == null) {
            this.pendingFrame = null;
            return;
         }
         if (minecraft.screen instanceof PictureFrameScreen) {
            this.pendingFrame = null;
         } else if (this.pendingFrame != null && player.tickCount - this.pendingAtTick >= 100) {
            Camerapture.LOGGER.warn("Picture frame editor did not open for {} within 5 seconds", this.pendingFrame);
            this.pendingFrame = null;
         }
         if (player.containerMenu instanceof PictureFrameMenu menu
            && (!(minecraft.screen instanceof MenuAccess<?> menuAccess) || menuAccess.getMenu() != menu)) {
            Camerapture.LOGGER.warn("Closing picture frame menu {} because its screen is missing", menu.containerId);
            player.closeContainer();
         }
      }
   }
}
