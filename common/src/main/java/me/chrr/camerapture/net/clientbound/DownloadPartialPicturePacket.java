package me.chrr.camerapture.net.clientbound;

import io.netty.buffer.ByteBuf;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.net.NetCodec;
import me.chrr.camerapture.picture.PictureQuality;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.UUID;

public record DownloadPartialPicturePacket(UUID uuid, PictureQuality quality, byte[] bytes, int bytesLeft) {
    private static final Identifier ID = Camerapture.id("download_partial_picture");

    public static final StreamCodec<ByteBuf, DownloadPartialPicturePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, packet.uuid());
                PictureQuality.STREAM_CODEC.encode(buf, packet.quality());
                ByteBufCodecs.byteArray(2_000_000).encode(buf, packet.bytes());
                ByteBufCodecs.VAR_INT.encode(buf, packet.bytesLeft());
            },
            buf -> {
                UUID uuid = UUIDUtil.STREAM_CODEC.decode(buf);
                PictureQuality quality = PictureQuality.STREAM_CODEC.decode(buf);
                byte[] bytes = ByteBufCodecs.byteArray(2_000_000).decode(buf);
                int bytesLeft = ByteBufCodecs.VAR_INT.decode(buf);
                return new DownloadPartialPicturePacket(uuid, quality, bytes, bytesLeft);
            }
    );

    public static final NetCodec<DownloadPartialPicturePacket> NET_CODEC = new NetCodec<>(ID, STREAM_CODEC);
}