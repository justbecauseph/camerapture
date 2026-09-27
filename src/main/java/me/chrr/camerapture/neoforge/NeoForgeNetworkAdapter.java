package me.chrr.camerapture.neoforge;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import me.chrr.camerapture.net.NetCodec;
import me.chrr.camerapture.net.NetworkAdapter;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NeoForgeNetworkAdapter implements NetworkAdapter {
   private final Map<Class<?>, NeoForgeNetworkAdapter.ServerPacketType<?>> serverPackets = new HashMap<>();
   private final Map<Class<?>, NeoForgeNetworkAdapter.ClientPacketType<?>> clientPackets = new HashMap<>();

   public <P> void registerServerBound(PayloadRegistrar registrar, Class<P> clazz, NetCodec<P> netCodec) {
      NeoForgeNetworkAdapter.ClientPacketType<P> type = new NeoForgeNetworkAdapter.ClientPacketType<>(netCodec, new ArrayList<>());
      this.clientPackets.put(clazz, type);
      StreamCodec<ByteBuf, P> codec = netCodec.streamCodec();
      StreamCodec<ByteBuf, NeoForgeNetworkAdapter.PacketPayload<P>> payloadCodec = StreamCodec.composite(
         codec,
         (NeoForgeNetworkAdapter.PacketPayload<P> payload) -> payload.packet(),
         (P packet) -> new NeoForgeNetworkAdapter.PacketPayload<P>(netCodec.id(), packet)
      );
      registrar.playToServer(
         new Type(netCodec.id()),
         payloadCodec,
         (payload, context) -> type.handlers().forEach(handler -> handler.accept(payload.packet, (ServerPlayer)context.player()))
      );
   }

   public <P> void registerClientBound(PayloadRegistrar registrar, Class<P> clazz, NetCodec<P> netCodec) {
      NeoForgeNetworkAdapter.ServerPacketType<P> type = new NeoForgeNetworkAdapter.ServerPacketType<>(netCodec, new ArrayList<>());
      this.serverPackets.put(clazz, type);
      StreamCodec<ByteBuf, P> codec = netCodec.streamCodec();
      StreamCodec<ByteBuf, NeoForgeNetworkAdapter.PacketPayload<P>> payloadCodec = StreamCodec.composite(
         codec,
         (NeoForgeNetworkAdapter.PacketPayload<P> payload) -> payload.packet(),
         (P packet) -> new NeoForgeNetworkAdapter.PacketPayload<P>(netCodec.id(), packet)
      );
      registrar.playToClient(
         new Type(netCodec.id()),
         payloadCodec,
         (payload, context) -> type.handlers().forEach(handler -> handler.accept(payload.packet))
      );
   }

   @Override
   public <P> void sendToClient(ServerPlayer player, P packet) {
      NeoForgeNetworkAdapter.ServerPacketType<P> type = this.getServerPacketType((Class<P>)packet.getClass());
      PacketDistributor.sendToPlayer(player, new NeoForgeNetworkAdapter.PacketPayload(type.netCodec().id(), packet), new CustomPacketPayload[0]);
   }

   @Override
   public <P> void onReceiveFromClient(Class<P> clazz, BiConsumer<P, ServerPlayer> handler) {
      this.getClientPacketType(clazz).handlers().add(handler);
   }

   @Override
   public <P> void sendToServer(P packet) {
      NeoForgeNetworkAdapter.ClientPacketType<P> type = this.getClientPacketType((Class<P>)packet.getClass());
      PacketDistributor.sendToServer(new NeoForgeNetworkAdapter.PacketPayload(type.netCodec().id(), packet), new CustomPacketPayload[0]);
   }

   @Override
   public <P> void onReceiveFromServer(Class<P> clazz, Consumer<P> handler) {
      this.getServerPacketType(clazz).handlers().add(handler);
   }

   private <P> NeoForgeNetworkAdapter.ServerPacketType<P> getServerPacketType(Class<P> clazz) {
      return (NeoForgeNetworkAdapter.ServerPacketType<P>)this.serverPackets.get(clazz);
   }

   private <P> NeoForgeNetworkAdapter.ClientPacketType<P> getClientPacketType(Class<P> clazz) {
      return (NeoForgeNetworkAdapter.ClientPacketType<P>)this.clientPackets.get(clazz);
   }

   private record ClientPacketType<P>(NetCodec<P> netCodec, List<BiConsumer<P, ServerPlayer>> handlers) {
   }

   private record PacketPayload<P>(ResourceLocation id, P packet) implements CustomPacketPayload {
      public Type<NeoForgeNetworkAdapter.PacketPayload<P>> type() {
         return new Type(this.id);
      }
   }

   private record ServerPacketType<P>(NetCodec<P> netCodec, List<Consumer<P>> handlers) {
   }
}
