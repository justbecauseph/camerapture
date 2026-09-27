package me.chrr.camerapture.picture;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import me.chrr.camerapture.ByteCollector;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.CameraptureClient;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.net.serverbound.NewPicturePacket;
import me.chrr.camerapture.net.serverbound.UploadPartialPicturePacket;
import me.chrr.camerapture.util.ImageUtil;
import me.chrr.camerapture.util.NativeImageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public class PictureTaker {
   private static final PictureTaker INSTANCE = new PictureTaker();
   public float zoomLevel = 1.0F;
   private boolean hudWasHidden = false;
   private boolean takingPicture = false;
   private BufferedImage picture;

   private PictureTaker() {
   }

   public void takePicture() {
      if (!this.takingPicture) {
         if (Camerapture.PLATFORM.canTakePicture()) {
            this.takingPicture = true;
            Minecraft minecraft = Minecraft.getInstance();
            this.hudWasHidden = minecraft.options.hideGui;
            minecraft.options.hideGui = true;
         }
      }
   }

   public void tryUploadFile(Path filePath) {
      try {
         this.picture = ImageIO.read(filePath.toFile());
         Camerapture.NETWORK.sendToServer(new NewPicturePacket());
      } catch (Exception e) {
         Camerapture.LOGGER.error("failed to read picture from file", e);
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null) {
            player.sendSystemMessage(Component.translatable("text.camerapture.upload_failed").withStyle(ChatFormatting.RED));
         }
      }
   }

   public void renderTickEnd() {
      if (this.takingPicture) {
         Minecraft minecraft = Minecraft.getInstance();
         CameraItem.HeldCamera activeCamera = CameraItem.find(minecraft.player, true);
         if (activeCamera != null) {
            CameraItem.setActive(activeCamera.stack(), false);
         }

         if (Camerapture.CONFIG_MANAGER.getConfig().client.saveScreenshot) {
            Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), ignored -> {});
         }

         NativeImage nativeImage = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());

         try {
            this.picture = NativeImageUtil.fromNativeImage(nativeImage);
         } catch (Throwable var7) {
            if (nativeImage != null) {
               try {
                  nativeImage.close();
               } catch (Throwable var6) {
                  var7.addSuppressed(var6);
               }
            }

            throw var7;
         }

         if (nativeImage != null) {
            nativeImage.close();
         }

         Camerapture.NETWORK.sendToServer(new NewPicturePacket());
         this.takingPicture = false;
         minecraft.options.hideGui = this.hudWasHidden;
      }
   }

   public void uploadStoredPicture(UUID pictureId) {
      if (this.picture == null) {
         Camerapture.LOGGER.error("server requested a picture, but we don't have any stored");
      } else {
         try {
            BufferedImage picture = ImageUtil.clampSize(this.picture, CameraptureClient.syncedConfig.maxImageResolution());
            picture = ImageUtil.normalize(picture);
            float factor = 1.0F;

            byte[] bytes;
            for (bytes = ImageUtil.compressIntoWebP(picture, factor);
               bytes.length > CameraptureClient.syncedConfig.maxImageBytes();
               bytes = ImageUtil.compressIntoWebP(picture, factor)
            ) {
               if (factor < 0.1F) {
                  throw new IOException("image too big, even at 10% compression (" + bytes.length + " bytes)");
               }

               factor -= 0.05F;
            }

            Camerapture.LOGGER.debug("sending picture ({} bytes, {}%)", bytes.length, (int)(factor * 100.0F));
            ByteCollector.split(
               bytes, 30000, (section, bytesLeft) -> Camerapture.NETWORK.sendToServer(new UploadPartialPicturePacket(pictureId, section, bytesLeft))
            );
            ClientPictureStore.getInstance().processReceivedImage(pictureId, PictureQuality.FULL, picture);
            ClientPictureStore.getInstance().cacheBytesToDisk(pictureId, PictureQuality.FULL, bytes);

            try {
               int thumbRes = CameraptureClient.syncedConfig != null ? CameraptureClient.syncedConfig.thumbnailResolution() : 128;
               BufferedImage thumb = ImageUtil.clampSize(picture, thumbRes);
               byte[] thumbBytes = ImageUtil.compressIntoWebP(thumb, 0.8F);
               ClientPictureStore.getInstance().processReceivedImage(pictureId, PictureQuality.THUMBNAIL, thumb);
               ClientPictureStore.getInstance().cacheBytesToDisk(pictureId, PictureQuality.THUMBNAIL, thumbBytes);
            } catch (Exception e) {
               Camerapture.LOGGER.error("failed to generate local thumbnail for {}", pictureId, e);
            }

            this.picture = null;
         } catch (Exception e) {
            Camerapture.LOGGER.error("failed to send picture to server", e);
            this.picture = null;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
               player.sendSystemMessage(Component.translatable("text.camerapture.upload_failed").withStyle(ChatFormatting.RED));
            }
         }
      }
   }

   public void clearPendingPicture() {
      this.picture = null;
   }

   public void zoom(float delta) {
      this.zoomLevel += delta;
      this.zoomLevel = Math.max(1.0F, Math.min(6.0F, this.zoomLevel));
   }

   public float getFovModifier() {
      float zoomProgress = (this.zoomLevel - 1.0F) / 5.0F;
      return 0.1F + 0.9F * (float)Math.pow(1.0F - zoomProgress, 2.0);
   }

   public float getSensitivityModifier() {
      float zoomProgress = (this.zoomLevel - 1.0F) / 5.0F;
      float multiplier = 1.0F - Camerapture.CONFIG_MANAGER.getConfig().client.zoomMouseSensitivity;
      return 1.0F - zoomProgress * multiplier;
   }

   public static PictureTaker getInstance() {
      return INSTANCE;
   }
}
