package me.chrr.camerapture.picture;

import org.jetbrains.annotations.NotNull;

public record ResolvedPicture(@NotNull RemotePicture picture, @NotNull PictureTexture texture) {
   public PictureTexture.Status status() {
      return this.texture.getStatus();
   }
}
