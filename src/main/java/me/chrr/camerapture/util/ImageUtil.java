package me.chrr.camerapture.util;

import dev.matrixlab.webp4j.WebPCodec;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.io.IOException;

public enum ImageUtil {
   ;
   public static BufferedImage clampSize(BufferedImage image, int maxDimension) {
      if (image.getWidth() <= maxDimension && image.getHeight() <= maxDimension) {
         return image;
      }

      int width;
      int height;
      if (image.getWidth() > image.getHeight()) {
         float scale = (float)image.getWidth() / maxDimension;
         width = maxDimension;
         height = Math.max(1, (int)(image.getHeight() / scale));
      } else {
         float scale = (float)image.getHeight() / maxDimension;
         width = Math.max(1, (int)(image.getWidth() / scale));
         height = maxDimension;
      }

      BufferedImage scaledImage = new BufferedImage(width, height, 2);
      Graphics2D g = scaledImage.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(image, 0, 0, width, height, null);
      g.dispose();
      return scaledImage;
   }

   public static BufferedImage normalize(BufferedImage image) {
      if (image.getColorModel() instanceof DirectColorModel) {
         return image;
      }

      BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), 2);
      Graphics2D g = newImage.createGraphics();
      g.drawImage(image, 0, 0, null);
      g.dispose();
      return newImage;
   }

   public static byte[] compressIntoWebP(BufferedImage image, float quality) throws IOException {
      return WebPCodec.encodeImage(image, quality * 100.0F, false);
   }

   public static BufferedImage decodeImageFromWebP(byte[] data) throws IOException {
      return WebPCodec.decodeImage(data);
   }

   public static byte[] createThumbnail(byte[] originalWebpBytes, int maxDimension) throws IOException {
      BufferedImage original = decodeImageFromWebP(originalWebpBytes);
      BufferedImage thumb = clampSize(original, maxDimension);
      return compressIntoWebP(thumb, 0.8F);
   }
}
