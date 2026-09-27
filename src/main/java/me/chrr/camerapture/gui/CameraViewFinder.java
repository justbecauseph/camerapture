package me.chrr.camerapture.gui;

import java.text.SimpleDateFormat;
import java.util.Date;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.picture.PictureTaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public enum CameraViewFinder {
   ;
   private static final SimpleDateFormat SDF_DATE = new SimpleDateFormat("yyyy/MM/dd");

   public static void drawCameraViewFinder(GuiGraphics graphics, Font font) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         int width = graphics.guiWidth();
         int height = graphics.guiHeight();
         drawViewFinder(graphics, 10, 10, width - 10, height - 10, 2, 30);
         drawViewFinder(graphics, width / 2 - 20, height / 2 - 20, width / 2 + 20, height / 2 + 20, 1, 10);
         drawZoomBar(graphics, font, width - 10, height / 2 - height / 6, height / 3);
         int fh = 9;
         int textX = 25;
         int textY = height - 25;
         if (!Camerapture.CONFIG_MANAGER.getConfig().client.simpleCameraHud) {
            graphics.drawString(font, Component.translatable("text.camerapture.date", new Object[]{SDF_DATE.format(new Date())}), textX, textY - fh, -1, false);
         }

         if (!CameraItem.canTakePicture(player)) {
            if (System.currentTimeMillis() % 1000L < 500L) {
               int w = font.width(Component.translatable("text.camerapture.no_paper"));
               int x = width / 2 - w / 2;
               int y = height / 2 + 32;
               graphics.drawString(font, Component.translatable("text.camerapture.no_paper"), x, y, -65536, false);
            }
         } else if (!Camerapture.CONFIG_MANAGER.getConfig().client.simpleCameraHud) {
            int paper = CameraItem.getPaperInInventory(player);
            Component text = Component.translatable("text.camerapture.paper_available", new Object[]{paper});
            int w = font.width(text);
            int x = width - 25 - w;
            int y = height - 25 - fh;
            graphics.drawString(font, text, x, y, -1, false);
         }
      }
   }

   private static void drawViewFinder(GuiGraphics graphics, int x1, int y1, int x2, int y2, int thickness, int length) {
      graphics.fill(x1, y1, x1 + length, y1 + thickness, -1);
      graphics.fill(x1, y1, x1 + thickness, y1 + length, -1);
      graphics.fill(x2 - length, y1, x2, y1 + thickness, -1);
      graphics.fill(x2 - thickness, y1, x2, y1 + length, -1);
      graphics.fill(x1, y2 - thickness, x1 + length, y2, -1);
      graphics.fill(x1, y2 - length, x1 + thickness, y2, -1);
      graphics.fill(x2 - length, y2 - thickness, x2, y2, -1);
      graphics.fill(x2 - thickness, y2 - length, x2, y2, -1);
   }

   private static void drawZoomBar(GuiGraphics graphics, Font font, int x, int y, int height) {
      int ticks = height / 10;

      for (int i = 0; i < ticks; i++) {
         int ty = y + height * i / (ticks - 1);
         graphics.fill(x - 6, ty, x, ty + 1, -1342177281);
      }

      float zoomProgress = 1.0F - (PictureTaker.getInstance().zoomLevel - 1.0F) / 5.0F;
      int ty = y + (int)(height * zoomProgress);
      graphics.fill(x - 10, ty - 1, x, ty + 1, -1);
      String zoomLevel = String.format("%.1fx", PictureTaker.getInstance().zoomLevel);
      int textWidth = font.width(zoomLevel);
      graphics.drawString(font, zoomLevel, x - 12 - textWidth, ty - 4, -1, false);
   }
}
