package me.chrr.camerapture.neoforge;

import me.chrr.camerapture.block.PictureFrameBlockEntity;
import me.chrr.camerapture.render.PictureFrameBlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.world.phys.AABB;

public class NeoPictureFrameBlockEntityRenderer extends PictureFrameBlockEntityRenderer {
   public NeoPictureFrameBlockEntityRenderer(Context context) {
      super(context);
   }

   public AABB getRenderBoundingBox(PictureFrameBlockEntity blockEntity) {
      return getFrameRenderBox(blockEntity);
   }
}
