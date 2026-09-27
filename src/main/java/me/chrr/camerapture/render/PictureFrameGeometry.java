package me.chrr.camerapture.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack.Pose;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.picture.PictureTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class PictureFrameGeometry {
   public static final float FRAME_DEPTH = 0.0625F;
   public static final float HALF_FRAME_DEPTH = 0.03125F;
   public static final float FRONT_Z = -0.03125F;
   public static final float BACK_Z = 0.03125F;
   public static final float PICTURE_Z = -0.03225F;
   public static final ResourceLocation PICTURE_BACK_TEXTURE = Camerapture.id("textures/block/picture_back.png");

   private PictureFrameGeometry() {
   }

   public static void renderBackQuad(PoseStack poseStack, MultiBufferSource buffers, float width, float height, int light) {
      VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(PICTURE_BACK_TEXTURE));
      Pose pose = poseStack.last();
      quad(pose, buffer, -width / 2.0F, -height / 2.0F, 0.03125F, width / 2.0F, height / 2.0F, 0.03125F, 0.0F, 0.0F, 1.0F, light);
   }

   public static void renderFullBacking(PoseStack poseStack, MultiBufferSource buffers, float width, float height, int light) {
      VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(PICTURE_BACK_TEXTURE));
      Pose pose = poseStack.last();
      float x1 = -width / 2.0F;
      float x2 = width / 2.0F;
      float y1 = -height / 2.0F;
      float y2 = height / 2.0F;
      quad(pose, buffer, x1, y1, 0.03125F, x2, y2, 0.03125F, 0.0F, 0.0F, 1.0F, light);
      face(pose, buffer, x1, y2, -0.03125F, x1, y2, 0.03125F, x2, y2, 0.03125F, x2, y2, -0.03125F, 0.0F, 1.0F, 0.0F, light);
      face(pose, buffer, x1, y1, 0.03125F, x1, y1, -0.03125F, x2, y1, -0.03125F, x2, y1, 0.03125F, 0.0F, -1.0F, 0.0F, light);
      face(pose, buffer, x1, y1, 0.03125F, x1, y2, 0.03125F, x1, y2, -0.03125F, x1, y1, -0.03125F, -1.0F, 0.0F, 0.0F, light);
      face(pose, buffer, x2, y1, -0.03125F, x2, y2, -0.03125F, x2, y2, 0.03125F, x2, y1, 0.03125F, 1.0F, 0.0F, 0.0F, light);
   }

   public static void renderPicture(
      PoseStack poseStack, MultiBufferSource buffers, PictureTexture texture, float frameWidth, float frameHeight, int rotation, boolean glowing, int light
   ) {
      float sourceWidth = Math.max(1, texture.getWidth());
      float sourceHeight = Math.max(1, texture.getHeight());
      if ((rotation & 1) != 0) {
         float swap = sourceWidth;
         sourceWidth = sourceHeight;
         sourceHeight = swap;
      }

      float scale = Math.min(frameWidth / sourceWidth, frameHeight / sourceHeight);
      float width = sourceWidth * scale;
      float height = sourceHeight * scale;
      RenderType type = glowing ? RenderType.text(texture.getTextureIdentifier()) : RenderType.entityCutoutNoCull(texture.getTextureIdentifier());
      VertexConsumer buffer = buffers.getBuffer(type);
      Pose pose = poseStack.last();
      int effectiveLight = glowing ? 15728880 : light;
      quad(pose, buffer, -width / 2.0F, -height / 2.0F, -0.03225F, width / 2.0F, height / 2.0F, -0.03225F, 0.0F, 0.0F, -1.0F, effectiveLight);
   }

   public static void renderPlaceholder(PoseStack poseStack, MultiBufferSource buffers, float width, float height, boolean glowing, int light) {
      VertexConsumer buffer = buffers.getBuffer(RenderType.textBackground());
      Pose pose = poseStack.last();
      int effectiveLight = glowing ? 15728880 : light;
      face(
         pose,
         buffer,
         -width / 2.0F,
         -height / 2.0F,
         -0.03225F,
         -width / 2.0F,
         height / 2.0F,
         -0.03225F,
         width / 2.0F,
         height / 2.0F,
         -0.03225F,
         width / 2.0F,
         -height / 2.0F,
         -0.03225F,
         0.0F,
         0.0F,
         -1.0F,
         effectiveLight,
         -13948117
      );
   }

   private static void quad(
      Pose pose, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, float nx, float ny, float nz, int light
   ) {
      face(pose, buffer, x1, y1, z1, x1, y2, z1, x2, y2, z2, x2, y1, z2, nx, ny, nz, light, -1);
   }

   private static void face(
      Pose pose,
      VertexConsumer buffer,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      float cx,
      float cy,
      float cz,
      float dx,
      float dy,
      float dz,
      float nx,
      float ny,
      float nz,
      int light
   ) {
      face(pose, buffer, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, nx, ny, nz, light, -1);
   }

   private static void face(
      Pose pose,
      VertexConsumer buffer,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      float cx,
      float cy,
      float cz,
      float dx,
      float dy,
      float dz,
      float nx,
      float ny,
      float nz,
      int light,
      int color
   ) {
      Matrix4f matrix = pose.pose();
      vertex(buffer, pose, matrix, ax, ay, az, 1.0F, 1.0F, nx, ny, nz, light, color);
      vertex(buffer, pose, matrix, bx, by, bz, 1.0F, 0.0F, nx, ny, nz, light, color);
      vertex(buffer, pose, matrix, cx, cy, cz, 0.0F, 0.0F, nx, ny, nz, light, color);
      vertex(buffer, pose, matrix, dx, dy, dz, 0.0F, 1.0F, nx, ny, nz, light, color);
   }

   private static void vertex(
      VertexConsumer buffer, Pose pose, Matrix4f matrix, float x, float y, float z, float u, float v, float nx, float ny, float nz, int light, int color
   ) {
      buffer.addVertex(matrix, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
   }
}
