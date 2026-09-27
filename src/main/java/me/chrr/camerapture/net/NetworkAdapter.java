package me.chrr.camerapture.net;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerPlayer;

public interface NetworkAdapter {
   <P> void sendToClient(ServerPlayer var1, P var2);

   <P> void onReceiveFromClient(Class<P> var1, BiConsumer<P, ServerPlayer> var2);

   <P> void sendToServer(P var1);

   <P> void onReceiveFromServer(Class<P> var1, Consumer<P> var2);
}
