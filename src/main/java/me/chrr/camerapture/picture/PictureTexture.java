package me.chrr.camerapture.picture;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

public class PictureTexture {
   private final UUID id;
   private final PictureQuality quality;
   private final ResourceLocation textureIdentifier;
   private PictureTexture.Status status = PictureTexture.Status.NOT_LOADED;
   private int width = 0;
   private int height = 0;
   private volatile long lastAccess = System.currentTimeMillis();

   public PictureTexture(UUID id, PictureQuality quality) {
      this.id = id;
      this.quality = quality;
      String prefix = quality == PictureQuality.THUMBNAIL ? "pictures/thumb/" : "pictures/";
      this.textureIdentifier = ResourceLocation.fromNamespaceAndPath("camerapture", prefix + id.toString());
   }

   public void touch() {
      this.lastAccess = System.currentTimeMillis();
   }

   public void touch(long now) {
      this.lastAccess = now;
   }

   public long getLastAccess() {
      return this.lastAccess;
   }

   public long getTextureBytes() {
      return (long)this.width * this.height * 4L;
   }

   public UUID getId() {
      return this.id;
   }

   public PictureQuality getQuality() {
      return this.quality;
   }

   public ResourceLocation getTextureIdentifier() {
      return this.textureIdentifier;
   }

   public PictureTexture.Status getStatus() {
      return this.status;
   }

   public void setStatus(PictureTexture.Status status) {
      this.status = status;
   }

   public int getWidth() {
      return this.width;
   }

   public int getHeight() {
      return this.height;
   }

   public void setSize(int width, int height) {
      this.width = width;
      this.height = height;
   }

   public enum Status {
      NOT_LOADED,
      FETCHING,
      SUCCESS,
      ERROR;
   }
}
