package me.chrr.camerapture.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.player.Player;

public class Config {
   public static Config DEFAULT = new Config();
   public Config.Client client = new Config.Client();
   public Config.Server server = new Config.Server();

   public static class Client {
      public int version = 6;
      public boolean cachePictures = true;
      public boolean saveScreenshot = false;
      public boolean simpleCameraHud = false;
      public float zoomMouseSensitivity = 0.5F;
      public boolean distantPictureRendering = true;
      public boolean renderPictureFrameBacking = false;
      public int fullTextureBudgetMiB = 512;
      public int thumbnailTextureBudgetMiB = 64;
      public float fullLodPixels = 32.0F;
      public float minimumRenderPixels = 1.5F;

      public void upgrade() {
         if (this.version < 4) {
            this.cachePictures = Config.DEFAULT.client.cachePictures;
         }

         if (this.version < 5) {
            this.distantPictureRendering = Config.DEFAULT.client.distantPictureRendering;
            this.fullTextureBudgetMiB = Config.DEFAULT.client.fullTextureBudgetMiB;
            this.thumbnailTextureBudgetMiB = Config.DEFAULT.client.thumbnailTextureBudgetMiB;
            this.fullLodPixels = Config.DEFAULT.client.fullLodPixels;
            this.minimumRenderPixels = Config.DEFAULT.client.minimumRenderPixels;
         }

         if (this.version < 6) {
            this.renderPictureFrameBacking = Config.DEFAULT.client.renderPictureFrameBacking;
         }

         if (this.minimumRenderPixels < 0.5F) {
            this.minimumRenderPixels = Config.DEFAULT.client.minimumRenderPixels;
         }

         if (this.fullLodPixels <= this.minimumRenderPixels) {
            this.fullLodPixels = Config.DEFAULT.client.fullLodPixels;
         }

         if (this.fullTextureBudgetMiB < 32) {
            this.fullTextureBudgetMiB = Config.DEFAULT.client.fullTextureBudgetMiB;
         }

         if (this.thumbnailTextureBudgetMiB < 8) {
            this.thumbnailTextureBudgetMiB = Config.DEFAULT.client.thumbnailTextureBudgetMiB;
         }

         this.version = Config.DEFAULT.client.version;
      }
   }

   public static class Server {
      public int version = 6;
      public int maxImageBytes = 500000;
      public int maxImageResolution = 1920;
      public int thumbnailResolution = 128;
      public int msPerPicture = 20;
      public boolean canRotatePictures = true;
      public boolean checkFramePosition = false;
      public Config.Server.PermissionLevels permissionLevels = new Config.Server.PermissionLevels();
      @DeprecatedConfigOption
      private boolean allowUploading = true;

      public void upgrade() {
         if (this.version < 5) {
            this.permissionLevels.upload = this.allowUploading ? 0 : 4;
         }

         if (this.version < 6) {
            this.thumbnailResolution = Config.DEFAULT.server.thumbnailResolution;
         }

         if (this.maxImageBytes < 10000) {
            this.maxImageBytes = Config.DEFAULT.server.maxImageBytes;
         }

         if (this.maxImageResolution < 64) {
            this.maxImageResolution = Config.DEFAULT.server.maxImageResolution;
         }

         if (this.thumbnailResolution < 32 || this.thumbnailResolution > 512) {
            this.thumbnailResolution = Config.DEFAULT.server.thumbnailResolution;
         }

         this.version = Config.DEFAULT.server.version;
      }

      public static class PermissionLevels {
         public static Codec<Config.Server.PermissionLevels> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(Codec.INT.fieldOf("takePicture").forGetter(p -> p.takePicture), Codec.INT.fieldOf("upload").forGetter(p -> p.upload))
               .apply(instance, Config.Server.PermissionLevels::new)
         );
         public int takePicture = 0;
         public int upload = 0;

         public PermissionLevels() {
         }

         public PermissionLevels(int takePicture, int upload) {
            this.takePicture = takePicture;
            this.upload = upload;
         }

         public boolean canTakePicture(Player player) {
            return this.takePicture == 0 || player.hasPermissions(this.takePicture);
         }

         public boolean canUpload(Player player) {
            return this.upload == 0 || player.hasPermissions(this.upload);
         }

         @Override
         public String toString() {
            return "{takePicture=" + this.takePicture + ", upload=" + this.upload + "}";
         }
      }
   }
}
