package me.chrr.camerapture.render;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.CameraItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

public final class RenderMetrics {
   private static int lastWindowHeight = -1;
   private static double lastFov = -1.0;
   private static double cachedFocalLengthPixels = 0.0;
   private static RenderMetrics.FrameContext currentFrameContext = null;
   private static long frameEpoch = 0L;

   private RenderMetrics() {
   }

   public static double getFocalLengthPixels() {
      Minecraft client = Minecraft.getInstance();
      int height = client.getWindow().getHeight();
      double fov = client.options != null ? ((Integer)client.options.fov().get()).intValue() : 70.0;
      if (height != lastWindowHeight || fov != lastFov) {
         lastWindowHeight = height;
         lastFov = fov;
         double fovRad = Math.toRadians(Math.max(5.0, Math.min(170.0, fov)));
         cachedFocalLengthPixels = height / 2.0 / Math.tan(fovRad / 2.0);
      }

      return cachedFocalLengthPixels;
   }

   public static void beginFrame() {
      frameEpoch++;
      currentFrameContext = new RenderMetrics.FrameContext(Minecraft.getInstance(), frameEpoch);
   }

   public static RenderMetrics.FrameContext getFrameContext() {
      if (currentFrameContext == null) {
         currentFrameContext = new RenderMetrics.FrameContext(Minecraft.getInstance(), frameEpoch);
      }

      return currentFrameContext;
   }

   public static long getFrameEpoch() {
      return frameEpoch;
   }

   public static class FrameContext {
      public final long epoch;
      public final double focalLengthPixels;
      public final float minPixels;
      public final float fullThreshold;
      public final boolean renderBacking;
      public final boolean cameraActive;
      public final boolean hudHidden;
      public final BlockPos targetedBlockPos;
      public final double skipDistFactorSq;
      public final double minHighDistFactorSq;
      public final double fullLowDistFactorSq;
      public final double fullHighDistFactorSq;

      public FrameContext(Minecraft client, long epoch) {
         this.epoch = epoch;
         this.focalLengthPixels = RenderMetrics.getFocalLengthPixels();
         this.minPixels = Math.max(0.5F, Camerapture.CONFIG_MANAGER.getConfig().client.minimumRenderPixels);
         this.fullThreshold = Math.max(this.minPixels + 1.0F, Camerapture.CONFIG_MANAGER.getConfig().client.fullLodPixels);
         this.renderBacking = Camerapture.CONFIG_MANAGER.getConfig().client.renderPictureFrameBacking;
         this.cameraActive = client.player != null && CameraItem.find(client.player, true) != null;
         this.hudHidden = client.options != null && client.options.hideGui;
         this.targetedBlockPos = client.hitResult instanceof BlockHitResult bhr ? bhr.getBlockPos() : null;
         double skipDiv = this.minPixels * 0.75;
         double minHighDiv = this.minPixels * 1.25;
         double fullLowDiv = this.fullThreshold * 0.85;
         double fullHighDiv = this.fullThreshold * 1.15;
         this.skipDistFactorSq = this.focalLengthPixels / skipDiv * (this.focalLengthPixels / skipDiv);
         this.minHighDistFactorSq = this.focalLengthPixels / minHighDiv * (this.focalLengthPixels / minHighDiv);
         this.fullLowDistFactorSq = this.focalLengthPixels / fullLowDiv * (this.focalLengthPixels / fullLowDiv);
         this.fullHighDistFactorSq = this.focalLengthPixels / fullHighDiv * (this.focalLengthPixels / fullHighDiv);
      }
   }
}
