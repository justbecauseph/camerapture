package me.chrr.camerapture.gui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureTexture;
import me.chrr.camerapture.picture.RemotePicture;
import me.chrr.camerapture.util.PictureDrawingUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class PictureScreen extends Screen {
   public static final int MAX_BAR_WIDTH = 360;
   public static final int BORDER_THICKNESS = 24;
   private List<ItemStack> pictures;
   private int index = 0;
   private RemotePicture picture;
   private Component pageNumber;
   private Component customName;
   private boolean ctrlHeld = false;

   public PictureScreen(List<ItemStack> pictures) {
      super(Component.translatable("item.camerapture.picture"));
      this.pictures = pictures;
      this.forceRefresh();
   }

   protected void init() {
      super.init();
      if (!this.isSinglePicture()) {
         int barWidth = Math.min(360, this.width - 48);
         int barX = this.width / 2 - barWidth / 2;
         int barY = this.height - 24 - 20;
         this.addRenderableWidget(Button.builder(Component.nullToEmpty("←"), button -> this.changeIndexBy(-1)).bounds(barX, barY, 20, 20).build());
         this.addRenderableWidget(
            Button.builder(Component.nullToEmpty("→"), button -> this.changeIndexBy(1)).bounds(barX + barWidth - 20, barY, 20, 20).build()
         );
      }
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
      super.render(graphics, mouseX, mouseY, delta);
      if (!this.isSinglePicture()) {
         int barY = this.height - 24 - 10;
         int pageNumberX = this.width / 2 - this.font.width(this.pageNumber) / 2;
         if (this.customName != null) {
            int nameX = this.width / 2 - this.font.width(this.customName) / 2;
            graphics.drawString(this.font, this.customName, nameX, barY - 1 - 9, -1, false);
            graphics.drawString(this.font, this.pageNumber, pageNumberX, barY + 1, -1, false);
         } else {
            graphics.drawString(this.font, this.pageNumber, pageNumberX, barY - 9 / 2, -1, false);
         }
      }

      if (this.picture != null) {
         if (this.ctrlHeld) {
            Component text = Component.translatable("text.camerapture.save_as").withStyle(ChatFormatting.WHITE);
            int tw = this.font.width(text);
            graphics.drawString(this.font, text, this.width / 2 - tw / 2, 24 - 9 - 2, -1, false);
         }

         int bottomOffset = this.isSinglePicture() ? 0 : 24;
         PictureDrawingUtil.drawPicture(graphics, this.font, this.picture, 24, 24, this.width - 48, this.height - 48 - bottomOffset);
      }
   }

   @Nullable
   public NativeImage getNativeImage() {
      if (this.minecraft != null && this.picture != null && this.picture.getFull().getStatus() == PictureTexture.Status.SUCCESS) {
         return this.minecraft.getTextureManager().getTexture(this.picture.getFull().getTextureIdentifier()) instanceof DynamicTexture backedTexture
            ? backedTexture.getPixels()
            : null;
      } else {
         return null;
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 341) {
         this.ctrlHeld = true;
      } else if (keyCode == 83 && (modifiers & 2) != 0) {
         NativeImage image = this.getNativeImage();
         if (image != null) {
            this.saveAs(image);
            return true;
         }
      } else {
         if (keyCode == 263) {
            this.changeIndexBy(-1);
            return true;
         }

         if (keyCode == 262) {
            this.changeIndexBy(1);
            return true;
         }
      }

      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 341) {
         this.ctrlHeld = false;
      }

      return super.keyReleased(keyCode, scanCode, modifiers);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.changeIndexBy((int)(-verticalAmount));
      return true;
   }

   public void changeIndexBy(int delta) {
      if (!this.pictures.isEmpty()) {
         this.index = Math.floorMod(this.index + delta, this.pictures.size());
         this.forceRefresh();
      }
   }

   public void setPictures(List<ItemStack> pictures) {
      this.pictures = pictures;
      this.index = 0;
      this.rebuildWidgets();
      this.forceRefresh();
   }

   private void forceRefresh() {
      this.pageNumber = Component.literal(this.index + 1 + " / " + this.pictures.size()).withStyle(ChatFormatting.GRAY);
      if (this.index >= this.pictures.size()) {
         this.picture = null;
         this.customName = null;
      } else {
         ItemStack stack = this.pictures.get(this.index);
         PictureItem.PictureData pictureData = PictureItem.getPictureData(stack);
         if (pictureData != null) {
            this.picture = ClientPictureStore.getInstance().ensureRemotePicture(pictureData.id());
            this.customName = (Component)stack.get(DataComponents.CUSTOM_NAME);
         }
      }
   }

   private boolean isSinglePicture() {
      return this.pictures.size() == 1;
   }

   private void saveAs(NativeImage image) {
      new Thread(() -> {
         MemoryStack stack = MemoryStack.stackPush();

         label44: {
            try {
               PointerBuffer filter = stack.mallocPointer(1);
               filter.put(stack.UTF8("png"));
               filter.flip();
               String path = TinyFileDialogs.tinyfd_saveFileDialog("Save Picture", "picture.png", filter, "*.png");
               if (path == null) {
                  break label44;
               }

               try {
                  image.writeToFile(Path.of(path));
               } catch (IOException e) {
                  Camerapture.LOGGER.error("failed to save picture to disk", e);
               }
            } catch (Throwable var7) {
               if (stack != null) {
                  try {
                     stack.close();
                  } catch (Throwable var5) {
                     var7.addSuppressed(var5);
                  }
               }

               throw var7;
            }

            if (stack != null) {
               stack.close();
            }

            return;
         }

         if (stack != null) {
            stack.close();
         }
      }, "Save prompter").start();
   }
}
