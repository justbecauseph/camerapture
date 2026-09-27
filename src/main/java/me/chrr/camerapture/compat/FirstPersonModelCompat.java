package me.chrr.camerapture.compat;

import dev.tr7zw.firstperson.api.ActivationHandler;
import dev.tr7zw.firstperson.api.FirstPersonAPI;
import me.chrr.camerapture.item.CameraItem;
import net.minecraft.client.Minecraft;

public enum FirstPersonModelCompat {
   ;
   public static void register() {
      FirstPersonAPI.registerPlayerHandler((ActivationHandler)() -> CameraItem.find(Minecraft.getInstance().player, true) != null);
   }
}
