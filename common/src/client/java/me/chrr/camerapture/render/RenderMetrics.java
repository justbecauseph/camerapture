package me.chrr.camerapture.render;

import net.minecraft.client.Minecraft;

/// Precomputes and caches global projection metrics across frames to avoid redundant trigonometric
/// and window calculations during block entity extraction. Uses the effective camera FOV for the frame.
public final class RenderMetrics {
    private static int lastWindowHeight = -1;
    private static double lastFov = -1.0;
    private static double cachedFocalLengthPixels = 0.0;

    private RenderMetrics() {
    }

    public static double getFocalLengthPixels() {
        Minecraft client = Minecraft.getInstance();
        int height = client.getWindow().getHeight();

        double fov = 70.0;
        if (client.gameRenderer != null && client.gameRenderer.mainCamera() != null) {
            fov = client.gameRenderer.mainCamera().getFov();
        }
        if (fov <= 0.0 && client.options != null) {
            fov = client.options.fov().get();
        }

        if (height != lastWindowHeight || fov != lastFov) {
            lastWindowHeight = height;
            lastFov = fov;
            double fovRad = Math.toRadians(Math.max(5.0, Math.min(170.0, fov)));
            cachedFocalLengthPixels = (height / 2.0) / Math.tan(fovRad / 2.0);
        }

        return cachedFocalLengthPixels;
    }

    private static FrameContext currentFrameContext = null;
    private static long lastContextFrameTime = -1L;

    public static FrameContext getFrameContext() {
        long now = System.currentTimeMillis();
        if (currentFrameContext == null || now - lastContextFrameTime > 16L) {
            currentFrameContext = new FrameContext(Minecraft.getInstance());
            lastContextFrameTime = now;
        }
        return currentFrameContext;
    }

    public static class FrameContext {
        public final double focalLengthPixels;
        public final float minPixels;
        public final float fullThreshold;
        public final boolean renderBacking;
        public final boolean cameraActive;
        public final boolean hudHidden;
        public final net.minecraft.core.BlockPos targetedBlockPos;

        // Precalculated squared ratio factors: (focal / threshold)^2
        public final double skipDistFactorSq;
        public final double minHighDistFactorSq;
        public final double fullLowDistFactorSq;
        public final double fullHighDistFactorSq;

        public FrameContext(Minecraft client) {
            this.focalLengthPixels = getFocalLengthPixels();
            this.minPixels = Math.max(0.5f, me.chrr.camerapture.Camerapture.CONFIG_MANAGER.getConfig().client.minimumRenderPixels);
            this.fullThreshold = Math.max(minPixels + 1.0f, me.chrr.camerapture.Camerapture.CONFIG_MANAGER.getConfig().client.fullLodPixels);
            this.renderBacking = me.chrr.camerapture.Camerapture.CONFIG_MANAGER.getConfig().client.renderPictureFrameBacking;
            this.cameraActive = client.player != null && me.chrr.camerapture.item.CameraItem.find(client.player, true) != null;
            this.hudHidden = client.gui.hud.isHidden();
            this.targetedBlockPos = (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr) ? bhr.getBlockPos() : null;

            double skipDiv = minPixels * 0.75;
            double minHighDiv = minPixels * 1.25;
            double fullLowDiv = fullThreshold * 0.85;
            double fullHighDiv = fullThreshold * 1.15;

            this.skipDistFactorSq = (focalLengthPixels / skipDiv) * (focalLengthPixels / skipDiv);
            this.minHighDistFactorSq = (focalLengthPixels / minHighDiv) * (focalLengthPixels / minHighDiv);
            this.fullLowDistFactorSq = (focalLengthPixels / fullLowDiv) * (focalLengthPixels / fullLowDiv);
            this.fullHighDistFactorSq = (focalLengthPixels / fullHighDiv) * (focalLengthPixels / fullHighDiv);
        }
    }
}
