package me.chrr.camerapture.net;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import me.chrr.camerapture.TransportLimits;
import me.chrr.camerapture.net.clientbound.DownloadPartialPicturePacket;
import me.chrr.camerapture.net.serverbound.UploadPartialPicturePacket;
import me.chrr.camerapture.net.clientbound.PictureErrorPacket;
import me.chrr.camerapture.picture.PictureQuality;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PacketCodecTest {

    @Test
    public void testUploadPartialPicturePacketSuccess() {
        UUID id = UUID.randomUUID();
        byte[] bytes = new byte[TransportLimits.CLIENT_SECTION_SIZE];
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
        byte[] bytes = new byte[TransportLimits.CLIENT_SECTION_SIZE + 1];

        UploadPartialPicturePacket packet = new UploadPartialPicturePacket(id, bytes, 0);
        ByteBuf buf = Unpooled.buffer();

        // Encoding oversized array throws EncoderException
        assertThrows(io.netty.handler.codec.EncoderException.class, () -> {
            UploadPartialPicturePacket.STREAM_CODEC.encode(buf, packet);
        });

        // Decoding buffer declaring array larger than CLIENT_SECTION_SIZE throws DecoderException
        ByteBuf malformedBuf = Unpooled.buffer();
        net.minecraft.core.UUIDUtil.STREAM_CODEC.encode(malformedBuf, id);
        net.minecraft.network.VarInt.write(malformedBuf, TransportLimits.CLIENT_SECTION_SIZE + 1);
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
        byte[] bytes = new byte[TransportLimits.SERVER_SECTION_SIZE + 1];

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
        net.minecraft.network.VarInt.write(malformedBuf, TransportLimits.SERVER_SECTION_SIZE + 1);
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

    @Test
    public void testPictureErrorPacketStreamCodec() {
        UUID id = UUID.randomUUID();
        ByteBuf buf = Unpooled.buffer();

        PictureErrorPacket packetNotFound = new PictureErrorPacket(id, PictureQuality.FULL, PictureErrorPacket.Reason.NOT_FOUND);
        PictureErrorPacket.STREAM_CODEC.encode(buf, packetNotFound);
        PictureErrorPacket decodedNotFound = PictureErrorPacket.STREAM_CODEC.decode(buf);

        assertEquals(id, decodedNotFound.uuid());
        assertEquals(PictureQuality.FULL, decodedNotFound.quality());
        assertEquals(PictureErrorPacket.Reason.NOT_FOUND, decodedNotFound.reason());

        buf.clear();
        PictureErrorPacket packetBusy = new PictureErrorPacket(id, PictureQuality.THUMBNAIL, PictureErrorPacket.Reason.BUSY);
        PictureErrorPacket.STREAM_CODEC.encode(buf, packetBusy);
        PictureErrorPacket decodedBusy = PictureErrorPacket.STREAM_CODEC.decode(buf);

        assertEquals(id, decodedBusy.uuid());
        assertEquals(PictureQuality.THUMBNAIL, decodedBusy.quality());
        assertEquals(PictureErrorPacket.Reason.BUSY, decodedBusy.reason());

        // Test out-of-bounds ordinal safe fallback
        buf.clear();
        net.minecraft.network.codec.ByteBufCodecs.VAR_INT.encode(buf, 999);
        PictureErrorPacket.Reason fallbackReason = PictureErrorPacket.Reason.STREAM_CODEC.decode(buf);
        assertEquals(PictureErrorPacket.Reason.NOT_FOUND, fallbackReason, "Invalid ordinal must fall back safely to NOT_FOUND");
    }
}
