package me.chrr.camerapture.picture;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public class RemotePicture {
   private final UUID id;
   private final PictureTexture thumbnail;
   private final PictureTexture full;

   public RemotePicture(UUID id) {
      this.id = id;
      this.thumbnail = new PictureTexture(id, PictureQuality.THUMBNAIL);
      this.full = new PictureTexture(id, PictureQuality.FULL);
   }

   public UUID getId() {
      return this.id;
   }

   public PictureTexture getThumbnail() {
      return this.thumbnail;
   }

   public PictureTexture getFull() {
      return this.full;
   }

   public PictureTexture getTexture(PictureQuality quality) {
      return quality == PictureQuality.THUMBNAIL ? this.thumbnail : this.full;
   }

   public PictureTexture getEffectiveTexture(PictureQuality requestedQuality) {
      if (requestedQuality == PictureQuality.FULL) {
         if (this.full.getStatus() == PictureTexture.Status.SUCCESS) {
            return this.full;
         } else {
            return this.thumbnail.getStatus() == PictureTexture.Status.SUCCESS ? this.thumbnail : this.full;
         }
      } else if (this.thumbnail.getStatus() == PictureTexture.Status.SUCCESS) {
         return this.thumbnail;
      } else {
         return this.full.getStatus() == PictureTexture.Status.SUCCESS ? this.full : this.thumbnail;
      }
   }

   public PictureTexture.Status getStatus() {
      return this.full.getStatus();
   }

   public int getWidth() {
      return this.full.getWidth() > 0 ? this.full.getWidth() : this.thumbnail.getWidth();
   }

   public int getHeight() {
      return this.full.getHeight() > 0 ? this.full.getHeight() : this.thumbnail.getHeight();
   }

   public ResourceLocation getTextureIdentifier() {
      return this.full.getTextureIdentifier();
   }
}
