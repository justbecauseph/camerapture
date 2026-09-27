package me.chrr.camerapture.net.clientbound;

import java.util.UUID;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.net.NetCodec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

public record RequestUploadPacket(UUID uuid) {
   private static final ResourceLocation ID = Camerapture.id("request_upload");
   public static final NetCodec<RequestUploadPacket> NET_CODEC = new NetCodec<>(ID, UUIDUtil.AUTHLIB_CODEC.xmap(RequestUploadPacket::new, p -> p.uuid));
}
