package me.chrr.camerapture.mixin;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.net.serverbound.RequestPictureFrameEditorPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V"),
            cancellable = true)
    private void requestPictureFrameEditor(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                           CallbackInfoReturnable<InteractionResult> cir) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !Minecraft.getInstance().options.keyShift.isDown()
                || !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()
                || !player.level().getBlockState(hit.getBlockPos()).is(Camerapture.PICTURE_FRAME_BLOCK)) {
            return;
        }

        Camerapture.NETWORK.sendToServer(new RequestPictureFrameEditorPacket(hit.getBlockPos(), hit.getLocation()));
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
