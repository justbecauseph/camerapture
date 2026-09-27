package me.chrr.camerapture.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import java.util.UUID;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.PictureTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class PictureItemRenderer {
   public static boolean canRender(ItemStack stack) {
      PictureItem.PictureData data = PictureItem.getPictureData(stack);
      if (data == null) {
         return false;
      }

      PictureTexture texture = ClientPictureStore.getInstance().resolveTextureForRender(data.id(), PictureQuality.THUMBNAIL);
      return texture.getStatus() == PictureTexture.Status.SUCCESS;
   }

   public void render(@Nullable UUID data, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
      if (data != null) {
         PictureTexture texture = ClientPictureStore.getInstance().resolveTextureForRender(data, PictureQuality.THUMBNAIL);
         if (texture.getStatus() == PictureTexture.Status.SUCCESS) {
            poseStack.pushPose();
            poseStack.translate(0.0625F, 0.0625F, 0.5F);
            poseStack.scale(0.875F, 0.875F, 0.875F);
            if (texture.getWidth() > texture.getHeight()) {
               float height = (float)texture.getHeight() / texture.getWidth();
               poseStack.translate(0.0F, (1.0F - height) / 2.0F, 0.0F);
               poseStack.scale(1.0F, height, 1.0F);
            } else {
               float width = (float)texture.getWidth() / texture.getHeight();
               poseStack.translate((1.0F - width) / 2.0F, 0.0F, 0.0F);
               poseStack.scale(width, 1.0F, 1.0F);
            }

            Pose pose = poseStack.last();
            Matrix4f matrix = pose.pose();
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(texture.getTextureIdentifier()));
            vertex(buffer, pose, matrix, 1.0F, 0.0F, 1.0F, 1.0F, light, overlay);
            vertex(buffer, pose, matrix, 1.0F, 1.0F, 1.0F, 0.0F, light, overlay);
            vertex(buffer, pose, matrix, 0.0F, 1.0F, 0.0F, 0.0F, light, overlay);
            vertex(buffer, pose, matrix, 0.0F, 0.0F, 0.0F, 1.0F, light, overlay);
            poseStack.popPose();
         }
      }
   }

   private static void vertex(VertexConsumer buffer, Pose pose, Matrix4f matrix, float x, float y, float u, float v, int light, int overlay) {
      buffer.addVertex(matrix, x, y, 0.0F)
         .setColor(-1)
         .setUv(u, v)
         .setOverlay(overlay == 0 ? OverlayTexture.NO_OVERLAY : overlay)
         .setLight(light)
         .setNormal(pose, 0.0F, 0.0F, 1.0F);
   }

   @Nullable
   public UUID getData(ItemStack stack) {
      PictureItem.PictureData data = PictureItem.getPictureData(stack);
      return data == null ? null : data.id();
   }
}
