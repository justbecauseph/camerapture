package me.chrr.camerapture.util;

import com.mojang.blaze3d.platform.NativeImage;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/// Utilities for converting between {@link BufferedImage} and Minecraft's {@link NativeImage}.
public enum NativeImageUtil {
    ;

    /// Convert a {@link BufferedImage} to a {@link NativeImage} via bulk pixel buffer transfer.
    public static NativeImage toNativeImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int totalPixels = width * height;

        NativeImage nativeImage = new NativeImage(NativeImage.Format.RGBA, width, height, false);

        int[] srcPixels;
        if (image.getRaster().getDataBuffer() instanceof DataBufferInt dataBufferInt
                && (image.getType() == BufferedImage.TYPE_INT_ARGB || image.getType() == BufferedImage.TYPE_INT_RGB)) {
            srcPixels = dataBufferInt.getData();
        } else {
            srcPixels = new int[totalPixels];
            image.getRGB(0, 0, width, height, srcPixels, 0, width);
        }

        boolean isRgbType = image.getType() == BufferedImage.TYPE_INT_RGB;
        IntBuffer intBuffer = nativeImage.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
        for (int i = 0; i < totalPixels; i++) {
            int argb = srcPixels[i];
            if (isRgbType) {
                argb |= 0xFF000000;
            }
            // Convert ARGB to ABGR (which corresponds to RGBA byte ordering on Little-Endian systems)
            int abgr = (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >>> 16) | ((argb & 0x000000FF) << 16);
            intBuffer.put(i, abgr);
        }

        return nativeImage;
    }

    /// Convert a {@link NativeImage} to a {@link BufferedImage}.
    public static BufferedImage fromNativeImage(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getPixels();

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        if (bufferedImage.getRaster().getDataBuffer() instanceof DataBufferInt dataBufferInt) {
            System.arraycopy(pixels, 0, dataBufferInt.getData(), 0, pixels.length);
        } else {
            bufferedImage.setRGB(0, 0, width, height, pixels, 0, width);
        }
        return bufferedImage;
    }
}
