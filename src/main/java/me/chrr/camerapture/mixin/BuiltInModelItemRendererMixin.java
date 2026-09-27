package me.chrr.camerapture.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.UUID;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.CameraptureClient;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityWithoutLevelRenderer.class)
public class BuiltInModelItemRendererMixin {
   @Inject(method = "renderByItem", at = @At("HEAD"), cancellable = true)
   private void camerapture$renderPicture(
      ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay, CallbackInfo ci
   ) {
      if (stack.is(Camerapture.PICTURE)) {
         UUID data = CameraptureClient.PICTURE_ITEM_RENDERER.getData(stack);
         CameraptureClient.PICTURE_ITEM_RENDERER.render(data, poseStack, buffers, light, overlay);
         ci.cancel();
      }
   }
}
