package me.chrr.camerapture.net.clientbound;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import me.chrr.camerapture.net.NetCodec;
import me.chrr.camerapture.picture.PictureQuality;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

public record PictureErrorPacket(UUID uuid, PictureQuality quality, PictureErrorPacket.Reason reason) {
   private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("camerapture", "picture_error");
   public static final StreamCodec<ByteBuf, PictureErrorPacket> STREAM_CODEC = StreamCodec.of((buf, packet) -> {
      UUIDUtil.STREAM_CODEC.encode(buf, packet.uuid());
      PictureQuality.STREAM_CODEC.encode(buf, packet.quality());
      PictureErrorPacket.Reason.STREAM_CODEC.encode(buf, packet.reason());
   }, buf -> {
      UUID uuid = (UUID)UUIDUtil.STREAM_CODEC.decode(buf);
      PictureQuality quality = (PictureQuality)PictureQuality.STREAM_CODEC.decode(buf);
      PictureErrorPacket.Reason reason = (PictureErrorPacket.Reason)PictureErrorPacket.Reason.STREAM_CODEC.decode(buf);
      return new PictureErrorPacket(uuid, quality, reason);
   });
   public static final NetCodec<PictureErrorPacket> NET_CODEC = new NetCodec<>(ID, STREAM_CODEC);

   public PictureErrorPacket(UUID uuid, PictureQuality quality) {
      this(uuid, quality, PictureErrorPacket.Reason.NOT_FOUND);
   }

   public enum Reason implements StringRepresentable {
      NOT_FOUND("not_found"),
      BUSY("busy");

      public static final StreamCodec<ByteBuf, PictureErrorPacket.Reason> STREAM_CODEC = StreamCodec.of(
         (buf, reason) -> ByteBufCodecs.VAR_INT.encode(buf, reason.ordinal()), buf -> {
            int ordinal = (Integer)ByteBufCodecs.VAR_INT.decode(buf);
            PictureErrorPacket.Reason[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NOT_FOUND;
         }
      );
      private final String name;

      Reason(String name) {
         this.name = name;
      }

      public String getSerializedName() {
         return this.name;
      }
   }
}
