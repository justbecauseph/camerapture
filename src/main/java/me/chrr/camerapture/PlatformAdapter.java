package me.chrr.camerapture;

import java.nio.file.Path;
import me.chrr.camerapture.net.NetworkAdapter;

public interface PlatformAdapter {
   NetworkAdapter createNetworkAdapter();

   Path getConfigFolder();

   Path getGameFolder();

   boolean isClientSide();

   boolean isModLoaded(String var1);

   default boolean canTakePicture() {
      return true;
   }
}
