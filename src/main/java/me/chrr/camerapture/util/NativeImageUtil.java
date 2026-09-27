package me.chrr.camerapture.util;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public enum NativeImageUtil {
   ;
   public static NativeImage toNativeImage(BufferedImage image) {
      int width = image.getWidth();
      int height = image.getHeight();
      int totalPixels = width * height;
      NativeImage nativeImage = new NativeImage(Format.RGBA, width, height, false);
      int[] srcPixels;
      if (!(image.getRaster().getDataBuffer() instanceof DataBufferInt dataBufferInt && (image.getType() == 2 || image.getType() == 1))) {
         srcPixels = new int[totalPixels];
         image.getRGB(0, 0, width, height, srcPixels, 0, width);
      } else {
         srcPixels = dataBufferInt.getData();
      }

      boolean isRgbType = image.getType() == 1;

      for (int i = 0; i < totalPixels; i++) {
         int argb = srcPixels[i];
         if (isRgbType) {
            argb |= -16777216;
         }

         int abgr = argb & -16711936 | (argb & 0xFF0000) >>> 16 | (argb & 0xFF) << 16;
         nativeImage.setPixelRGBA(i % width, i / width, abgr);
      }

      return nativeImage;
   }

   public static BufferedImage fromNativeImage(NativeImage image) {
      int width = image.getWidth();
      int height = image.getHeight();
      int[] pixels = image.getPixelsRGBA();

      for (int i = 0; i < pixels.length; i++) {
         int abgr = pixels[i];
         pixels[i] = abgr & -16711936 | (abgr & 0xFF0000) >>> 16 | (abgr & 0xFF) << 16;
      }

      BufferedImage bufferedImage = new BufferedImage(width, height, 2);
      if (bufferedImage.getRaster().getDataBuffer() instanceof DataBufferInt dataBufferInt) {
         System.arraycopy(pixels, 0, dataBufferInt.getData(), 0, pixels.length);
      } else {
         bufferedImage.setRGB(0, 0, width, height, pixels, 0, width);
      }

      return bufferedImage;
   }
}
