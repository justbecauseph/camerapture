package me.chrr.camerapture.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.net.clientbound.DownloadPartialPicturePacket;
import me.chrr.camerapture.net.serverbound.UploadPartialPicturePacket;
import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PacketCodecTest {

    @BeforeAll
    public static void setUp() {
        MinecraftTestBootstrap.init();
    }

    @Test
    public void testUploadPartialPicturePacketSuccess() {
        UUID id = UUID.randomUUID();
        byte[] bytes = new byte[Camerapture.CLIENT_SECTION_SIZE];
        bytes[0] = 42;
        bytes[bytes.length - 1] = 99;

        UploadPartialPicturePacket packet = new UploadPartialPicturePacket(id, bytes, 5000);
        ByteBuf buf = Unpooled.buffer();

        UploadPartialPicturePacket.STREAM_CODEC.encode(buf, packet);
        UploadPartialPicturePacket decoded = UploadPartialPicturePacket.STREAM_CODEC.decode(buf);

        assertEquals(id, decoded.uuid());
        assertArrayEquals(bytes, decoded.bytes());
        assertEquals(5000, decoded.bytesLeft());
    }

    @Test
    public void testUploadPartialPicturePacketExceedingLimitThrows() {
        UUID id = UUID.randomUUID();
        byte[] bytes = new byte[Camerapture.CLIENT_SECTION_SIZE + 1];

        UploadPartialPicturePacket packet = new UploadPartialPicturePacket(id, bytes, 0);
        ByteBuf buf = Unpooled.buffer();

        // Encoding oversized array throws EncoderException
        assertThrows(io.netty.handler.codec.EncoderException.class, () -> {
            UploadPartialPicturePacket.STREAM_CODEC.encode(buf, packet);
        });

        // Decoding buffer declaring array larger than CLIENT_SECTION_SIZE throws DecoderException
        ByteBuf malformedBuf = Unpooled.buffer();
        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(malformedBuf, id);
        net.minecraft.network.VarInt.write(malformedBuf, Camerapture.CLIENT_SECTION_SIZE + 1);
        malformedBuf.writeBytes(new byte[10]);

        assertThrows(DecoderException.class, () -> {
            UploadPartialPicturePacket.STREAM_CODEC.decode(malformedBuf);
        });
    }

    @Test
    public void testDownloadPartialPicturePacketSuccess() {
        UUID id = UUID.randomUUID();
        byte[] bytes = new byte[500_000];
        bytes[0] = 7;
        bytes[bytes.length - 1] = 88;

        DownloadPartialPicturePacket packet = new DownloadPartialPicturePacket(id, PictureQuality.THUMBNAIL, bytes, 0);
        ByteBuf buf = Unpooled.buffer();

        DownloadPartialPicturePacket.STREAM_CODEC.encode(buf, packet);
        DownloadPartialPicturePacket decoded = DownloadPartialPicturePacket.STREAM_CODEC.decode(buf);

        assertEquals(id, decoded.uuid());
        assertEquals(PictureQuality.THUMBNAIL, decoded.quality());
        assertArrayEquals(bytes, decoded.bytes());
        assertEquals(0, decoded.bytesLeft());
    }

    @Test
    public void testDownloadPartialPicturePacketExceedingLimitThrows() {
        UUID id = UUID.randomUUID();
        byte[] bytes = new byte[Camerapture.SERVER_SECTION_SIZE + 1];

        DownloadPartialPicturePacket packet = new DownloadPartialPicturePacket(id, PictureQuality.FULL, bytes, 0);
        ByteBuf buf = Unpooled.buffer();

        // Encoding oversized array throws EncoderException
        assertThrows(io.netty.handler.codec.EncoderException.class, () -> {
            DownloadPartialPicturePacket.STREAM_CODEC.encode(buf, packet);
        });

        // Decoding buffer declaring array larger than SERVER_SECTION_SIZE throws DecoderException
        ByteBuf malformedBuf = Unpooled.buffer();
        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(malformedBuf, id);
        PictureQuality.STREAM_CODEC.encode(malformedBuf, PictureQuality.FULL);
        net.minecraft.network.VarInt.write(malformedBuf, Camerapture.SERVER_SECTION_SIZE + 1);
        malformedBuf.writeBytes(new byte[10]);

        assertThrows(DecoderException.class, () -> {
            DownloadPartialPicturePacket.STREAM_CODEC.decode(malformedBuf);
        });
    }

    @Test
    public void testPictureQualityStreamCodec() {
        ByteBuf buf = Unpooled.buffer();

        PictureQuality.STREAM_CODEC.encode(buf, PictureQuality.THUMBNAIL);
        assertEquals(PictureQuality.THUMBNAIL, PictureQuality.STREAM_CODEC.decode(buf));

        buf.clear();
        PictureQuality.STREAM_CODEC.encode(buf, PictureQuality.FULL);
        assertEquals(PictureQuality.FULL, PictureQuality.STREAM_CODEC.decode(buf));
    }
}
