package me.chrr.camerapture.mixin;

import me.chrr.camerapture.gui.SizedSlot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
   @Shadow
   @Nullable
   protected Slot hoveredSlot;

   @Shadow
   protected abstract boolean isHovering(int var1, int var2, int var3, int var4, double var5, double var7);

   @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
   private void isHovering(Slot slot, double xm, double ym, CallbackInfoReturnable<Boolean> cir) {
      if (slot instanceof SizedSlot sizedSlot) {
         cir.setReturnValue(this.isHovering(slot.x, slot.y, sizedSlot.getWidth(), sizedSlot.getHeight(), xm, ym));
         cir.cancel();
      }
   }
}
