package me.chrr.camerapture.picture;

import org.jetbrains.annotations.NotNull;

/// Holds a resolved picture and the effective texture selected for rendering.
public record ResolvedPicture(@NotNull RemotePicture picture, @NotNull PictureTexture texture) {
    public PictureTexture.Status status() {
        return texture.getStatus();
    }
}
