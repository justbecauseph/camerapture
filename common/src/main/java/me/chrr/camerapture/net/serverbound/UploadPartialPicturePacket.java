package me.chrr.camerapture.net.serverbound;

import io.netty.buffer.ByteBuf;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.net.NetCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import java.util.UUID;

public record UploadPartialPicturePacket(UUID uuid, byte[] bytes, int bytesLeft) {
    private static final Identifier ID = Camerapture.id("upload_partial_picture");

    public static final StreamCodec<ByteBuf, UploadPartialPicturePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, packet.uuid());
                ByteBufCodecs.byteArray(Camerapture.CLIENT_SECTION_SIZE).encode(buf, packet.bytes());
                ByteBufCodecs.VAR_INT.encode(buf, packet.bytesLeft());
            },
            buf -> {
                UUID uuid = UUIDUtil.STREAM_CODEC.decode(buf);
                byte[] bytes = ByteBufCodecs.byteArray(Camerapture.CLIENT_SECTION_SIZE).decode(buf);
                int bytesLeft = ByteBufCodecs.VAR_INT.decode(buf);
                return new UploadPartialPicturePacket(uuid, bytes, bytesLeft);
            }
    );

    public static final NetCodec<UploadPartialPicturePacket> NET_CODEC = new NetCodec<>(ID, STREAM_CODEC);
}