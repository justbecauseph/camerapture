package me.chrr.camerapture.net.clientbound;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.config.SyncedConfig;
import me.chrr.camerapture.net.NetCodec;
import net.minecraft.resources.ResourceLocation;

public record SyncConfigPacket(SyncedConfig syncedConfig) {
   private static final ResourceLocation ID = Camerapture.id("sync_config");
   public static final NetCodec<SyncConfigPacket> NET_CODEC = new NetCodec<>(ID, SyncedConfig.CODEC.xmap(SyncConfigPacket::new, SyncConfigPacket::syncedConfig));
}
