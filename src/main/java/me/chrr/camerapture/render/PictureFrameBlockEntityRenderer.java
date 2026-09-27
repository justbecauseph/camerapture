package me.chrr.camerapture.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.UUID;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.block.PictureFrameBlockEntity;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.PictureTexture;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.screens.LoadingDotsText;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class PictureFrameBlockEntityRenderer implements BlockEntityRenderer<PictureFrameBlockEntity> {
   public static final double DISTANCE_FROM_WALL = 0.01;

   public PictureFrameBlockEntityRenderer(Context context) {
   }

   public void render(PictureFrameBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
      CameraptureDebugStats.extractedFrames.incrementAndGet();
      RenderMetrics.FrameContext context = RenderMetrics.getFrameContext();
      Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
      PictureLod lod = classifyLod(blockEntity, context, cameraPos);
      blockEntity.lastLod = lod;
      if (lod == PictureLod.SKIP) {
         CameraptureDebugStats.subpixelRejected.incrementAndGet();
      } else {
         CameraptureDebugStats.submittedFrames.incrementAndGet();
         if (lod == PictureLod.FULL) {
            CameraptureDebugStats.fullLodFrames.incrementAndGet();
         } else {
            CameraptureDebugStats.thumbnailLodFrames.incrementAndGet();
         }

         Direction facing = blockEntity.getFacing();
         float frameWidth = blockEntity.getFrameWidth();
         float frameHeight = blockEntity.getFrameHeight();
         poseStack.pushPose();
         poseStack.translate(0.5, 0.5, 0.5);
         poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
         poseStack.translate(0.5 - frameWidth / 2.0, -0.5 + frameHeight / 2.0, 0.47875);
         if (context.renderBacking) {
            if (lod == PictureLod.FULL) {
               PictureFrameGeometry.renderFullBacking(poseStack, buffers, frameWidth, frameHeight, light);
            } else {
               PictureFrameGeometry.renderBackQuad(poseStack, buffers, frameWidth, frameHeight, light);
            }
         }

         UUID pictureId = getPictureId(blockEntity.getItemStack());
         if (pictureId == null) {
            CameraptureDebugStats.missingPictures.incrementAndGet();
            if (lod == PictureLod.FULL) {
               renderErrorText(poseStack, buffers, light);
            } else {
               PictureFrameGeometry.renderPlaceholder(poseStack, buffers, frameWidth, frameHeight, blockEntity.isPictureGlowing(), light);
            }

            poseStack.popPose();
         } else {
            PictureQuality requested = lod == PictureLod.FULL ? PictureQuality.FULL : PictureQuality.THUMBNAIL;
            PictureTexture texture = ClientPictureStore.getInstance().resolveTextureForRender(pictureId, requested);
            if (texture != null && texture.getStatus() == PictureTexture.Status.SUCCESS) {
               if (texture.getQuality() == PictureQuality.FULL) {
                  CameraptureDebugStats.fullTextureRenders.incrementAndGet();
               } else {
                  CameraptureDebugStats.thumbnailTextureRenders.incrementAndGet();
               }

               poseStack.pushPose();
               if (blockEntity.getRotation() != 0) {
                  poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F * blockEntity.getRotation()));
               }

               PictureFrameGeometry.renderPicture(
                  poseStack, buffers, texture, frameWidth, frameHeight, blockEntity.getRotation(), blockEntity.isPictureGlowing(), light
               );
               poseStack.popPose();
            } else if (lod != PictureLod.FULL
               || texture == null
               || texture.getStatus() != PictureTexture.Status.NOT_LOADED && texture.getStatus() != PictureTexture.Status.FETCHING) {
               if (lod == PictureLod.FULL && texture != null && texture.getStatus() == PictureTexture.Status.ERROR) {
                  renderErrorText(poseStack, buffers, light);
               } else {
                  CameraptureDebugStats.placeholderRenders.incrementAndGet();
                  PictureFrameGeometry.renderPlaceholder(poseStack, buffers, frameWidth, frameHeight, blockEntity.isPictureGlowing(), light);
               }
            } else {
               renderFetching(poseStack, buffers, light);
            }

            poseStack.popPose();
         }
      }
   }

   private static UUID getPictureId(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         PictureItem.PictureData data = PictureItem.getPictureData(stack);
         return data == null ? null : data.id();
      } else {
         return null;
      }
   }

   private static PictureLod classifyLod(PictureFrameBlockEntity blockEntity, RenderMetrics.FrameContext context, Vec3 cameraPos) {
      double distSq = blockEntity.getRenderBox().distanceToSqr(cameraPos);
      double worldSize = Math.max(blockEntity.getFrameWidth(), blockEntity.getFrameHeight());
      double worldSizeSq = worldSize * worldSize;
      PictureLod previous = blockEntity.lastLod;
      if (previous == PictureLod.FULL) {
         if (distSq > worldSizeSq * context.skipDistFactorSq) {
            return PictureLod.SKIP;
         } else {
            return distSq > worldSizeSq * context.fullLowDistFactorSq ? PictureLod.THUMBNAIL : PictureLod.FULL;
         }
      } else if (previous == PictureLod.THUMBNAIL) {
         if (distSq > worldSizeSq * context.skipDistFactorSq) {
            return PictureLod.SKIP;
         } else {
            return distSq <= worldSizeSq * context.fullHighDistFactorSq ? PictureLod.FULL : PictureLod.THUMBNAIL;
         }
      } else if (distSq <= worldSizeSq * context.fullHighDistFactorSq) {
         return PictureLod.FULL;
      } else {
         return distSq <= worldSizeSq * context.minHighDistFactorSq ? PictureLod.THUMBNAIL : PictureLod.SKIP;
      }
   }

   private static void renderFetching(PoseStack poseStack, MultiBufferSource buffers, int light) {
      Font font = Minecraft.getInstance().font;
      poseStack.pushPose();
      poseStack.translate(0.0F, 0.0F, -0.033249997F);
      poseStack.scale(-0.015625F, -0.015625F, 0.015625F);
      String loading = LoadingDotsText.get(System.currentTimeMillis());
      drawCentered(font, Component.translatable("text.camerapture.fetching_picture"), -9 - 0.5F, -1, poseStack, buffers, light);
      drawCentered(font, Component.literal(loading), 0.5F, -8355712, poseStack, buffers, light);
      poseStack.popPose();
   }

   private static void renderErrorText(PoseStack poseStack, MultiBufferSource buffers, int light) {
      Font font = Minecraft.getInstance().font;
      poseStack.pushPose();
      poseStack.translate(0.0F, 0.0F, -0.033249997F);
      poseStack.scale(-0.015625F, -0.015625F, 0.015625F);
      Component text = Component.translatable("text.camerapture.fetching_failed").withStyle(ChatFormatting.RED);
      drawCentered(font, text, -9 / 2.0F, -1, poseStack, buffers, light);
      poseStack.popPose();
   }

   private static void drawCentered(Font font, Component text, float y, int color, PoseStack poseStack, MultiBufferSource buffers, int light) {
      font.drawInBatch(text, -font.width(text) / 2.0F, y, color, false, poseStack.last().pose(), buffers, DisplayMode.NORMAL, 0, light);
   }

   public int getViewDistance() {
      return Integer.MAX_VALUE;
   }

   public boolean shouldRender(PictureFrameBlockEntity blockEntity, Vec3 cameraPos) {
      double distSq = blockEntity.getRenderBox().distanceToSqr(cameraPos);
      if (!Camerapture.CONFIG_MANAGER.getConfig().client.distantPictureRendering) {
         return distSq <= 9216.0;
      }

      RenderMetrics.FrameContext context = RenderMetrics.getFrameContext();
      double worldSize = Math.max(blockEntity.getFrameWidth(), blockEntity.getFrameHeight());
      double factorSq = blockEntity.lastLod == PictureLod.SKIP ? context.minHighDistFactorSq : context.skipDistFactorSq;
      return distSq <= worldSize * worldSize * factorSq;
   }

   public boolean shouldRenderOffScreen(PictureFrameBlockEntity blockEntity) {
      return true;
   }

   public static AABB getFrameRenderBox(PictureFrameBlockEntity blockEntity) {
      return blockEntity.getRenderBox();
   }
}
