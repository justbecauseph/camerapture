package me.chrr.camerapture.util;

import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.PictureTexture;
import me.chrr.camerapture.picture.RemotePicture;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingDotsText;
import net.minecraft.network.chat.Component;

public enum PictureDrawingUtil {
   ;
   public static void drawPicture(GuiGraphics graphics, Font font, RemotePicture picture, int x, int y, int width, int height) {
      drawPicture(graphics, font, picture, x, y, width, height, PictureQuality.FULL);
   }

   public static void drawPicture(GuiGraphics graphics, Font font, RemotePicture picture, int x, int y, int width, int height, PictureQuality quality) {
      PictureTexture texture = ClientPictureStore.getInstance().resolveTextureForRender(picture.getId(), quality);
      drawPicture(graphics, font, texture, x, y, width, height);
   }

   public static void drawPicture(GuiGraphics graphics, Font font, PictureTexture texture, int x, int y, int width, int height) {
      switch (texture.getStatus()) {
         case NOT_LOADED:
         case FETCHING:
            String loading = LoadingDotsText.get(System.currentTimeMillis());
            Component fetching = Component.translatable("text.camerapture.fetching_picture");
            graphics.drawCenteredString(font, fetching, x + width / 2, y + height / 2 - 9, 16777215);
            graphics.drawCenteredString(font, loading, x + width / 2, y + height / 2, 8421504);
            break;
         case ERROR:
            Component error = Component.translatable("text.camerapture.fetching_failed");
            graphics.drawCenteredString(font, error, x + width / 2, y + height / 2 - 9 / 2, 16711680);
            break;
         case SUCCESS:
            float scaledWidth = (float)width / texture.getWidth();
            float scaleHeight = (float)height / texture.getHeight();
            float scale = Math.min(scaledWidth, scaleHeight);
            int newWidth = (int)(texture.getWidth() * scale);
            int newHeight = (int)(texture.getHeight() * scale);
            int dx = x + width / 2 - newWidth / 2;
            int dy = y + height / 2 - newHeight / 2;
            graphics.blit(texture.getTextureIdentifier(), dx, dy, 0.0F, 0.0F, newWidth, newHeight, newWidth, newHeight);
      }
   }
}
