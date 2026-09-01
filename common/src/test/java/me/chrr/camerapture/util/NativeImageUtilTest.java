package me.chrr.camerapture.util;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

public class NativeImageUtilTest {

    @Test
    public void testTypeIntRgbAlphaIsOpaque() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x00FF0000); // Red
        image.setRGB(1, 0, 0x0000FF00); // Green
        image.setRGB(0, 1, 0x000000FF); // Blue
        image.setRGB(1, 1, 0x00FFFFFF); // White

        NativeImage nativeImage = NativeImageUtil.toNativeImage(image);
        try {
            assertEquals(2, nativeImage.getWidth());
            assertEquals(2, nativeImage.getHeight());

            IntBuffer buffer = nativeImage.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
            // ABGR on little endian: Alpha = 0xFF in high byte
            // Red: 0xFFFF0000 in ARGB -> 0xFF0000FF in ABGR
            assertEquals(0xFF0000FF, buffer.get(0));
            // Green: 0xFF00FF00 in ARGB -> 0xFF00FF00 in ABGR
            assertEquals(0xFF00FF00, buffer.get(1));
            // Blue: 0xFF0000FF in ARGB -> 0xFFFF0000 in ABGR
            assertEquals(0xFFFF0000, buffer.get(2));
            // White: 0xFFFFFFFF in ARGB -> 0xFFFFFFFF in ABGR
            assertEquals(0xFFFFFFFF, buffer.get(3));
        } finally {
            nativeImage.close();
        }
    }

    @Test
    public void testTypeIntArgbConversionExactness() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000); // Opaque Red
        image.setRGB(1, 0, 0x80123456); // Semi-transparent
        image.setRGB(0, 1, 0x00000000); // Fully transparent
        image.setRGB(1, 1, 0xFF000000); // Opaque Black

        NativeImage nativeImage = NativeImageUtil.toNativeImage(image);
        try {
            IntBuffer buffer = nativeImage.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
            // Red: 0xFFFF0000 -> 0xFF0000FF
            assertEquals(0xFF0000FF, buffer.get(0));
            // Semi-transparent 0x80123456: A=0x80, R=0x12, G=0x34, B=0x56 -> ABGR = 0x80563412
            assertEquals(0x80563412, buffer.get(1));
            // Transparent: 0x00000000 -> 0x00000000
            assertEquals(0x00000000, buffer.get(2));
            // Black: 0xFF000000 -> 0xFF000000
            assertEquals(0xFF000000, buffer.get(3));
        } finally {
            nativeImage.close();
        }
    }

    @Test
    public void testType3ByteBgrFallback() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 1, 1);
        g.setColor(Color.BLUE);
        g.fillRect(1, 0, 1, 1);
        g.dispose();

        NativeImage nativeImage = NativeImageUtil.toNativeImage(image);
        try {
            IntBuffer buffer = nativeImage.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer();
            // Red in ABGR: 0xFF0000FF
            assertEquals(0xFF0000FF, buffer.get(0));
            // Blue in ABGR: 0xFFFF0000
            assertEquals(0xFFFF0000, buffer.get(1));
        } finally {
            nativeImage.close();
        }
    }

    @Test
    public void testRoundtripConversion() {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000);
        image.setRGB(1, 1, 0xFF00FF00);
        image.setRGB(2, 2, 0xFF0000FF);
        image.setRGB(3, 3, 0xFFFFFFFF);

        NativeImage nativeImage = NativeImageUtil.toNativeImage(image);
        BufferedImage roundtripped = NativeImageUtil.fromNativeImage(nativeImage);
        nativeImage.close();

        assertEquals(image.getWidth(), roundtripped.getWidth());
        assertEquals(image.getHeight(), roundtripped.getHeight());
        assertEquals(0xFFFF0000, roundtripped.getRGB(0, 0));
        assertEquals(0xFF00FF00, roundtripped.getRGB(1, 1));
        assertEquals(0xFF0000FF, roundtripped.getRGB(2, 2));
        assertEquals(0xFFFFFFFF, roundtripped.getRGB(3, 3));
    }
}
