package me.chrr.camerapture.net.serverbound;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import me.chrr.camerapture.net.NetCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record UploadPartialPicturePacket(UUID uuid, byte[] bytes, int bytesLeft) {
   private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("camerapture", "upload_partial_picture");
   public static final StreamCodec<ByteBuf, UploadPartialPicturePacket> STREAM_CODEC = StreamCodec.of((buf, packet) -> {
      UUIDUtil.STREAM_CODEC.encode(buf, packet.uuid());
      ByteBufCodecs.byteArray(30000).encode(buf, packet.bytes());
      ByteBufCodecs.VAR_INT.encode(buf, packet.bytesLeft());
   }, buf -> {
      UUID uuid = (UUID)UUIDUtil.STREAM_CODEC.decode(buf);
      byte[] bytes = (byte[])ByteBufCodecs.byteArray(30000).decode(buf);
      int bytesLeft = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
      return new UploadPartialPicturePacket(uuid, bytes, bytesLeft);
   });
   public static final NetCodec<UploadPartialPicturePacket> NET_CODEC = new NetCodec<>(ID, STREAM_CODEC);
}
