package me.chrr.camerapture.gui;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.picture.PictureTaker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public class UploadScreen extends Screen {
   private static final ResourceLocation TEXTURE = Camerapture.id("textures/gui/upload_picture.png");
   private static final int backgroundWidth = 256;
   private static final int backgroundHeight = 128;
   private PlainTextButton browseButton;

   public UploadScreen() {
      super(Component.translatable("text.camerapture.upload_picture.title").withStyle(ChatFormatting.BOLD));
   }

   protected void init() {
      super.init();
      Component text = Component.translatable("text.camerapture.upload_picture.browse").withStyle(ChatFormatting.UNDERLINE);
      int w = this.font.width(text);
      this.browseButton = (PlainTextButton)this.addRenderableWidget(
         new PlainTextButton(this.width / 2 - w / 2, this.height / 2 + 9 + 4, w, 9, text, button -> this.browseFile(), this.font)
      );
   }

   public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
      super.render(graphics, mouseX, mouseY, delta);
      Component description = Component.translatable("text.camerapture.upload_picture.description");
      graphics.blit(TEXTURE, this.width / 2 - 128, this.height / 2 - 64, 0.0F, 0.0F, 256, 128, 256, 256);
      graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 9 - 16, -1);
      boolean canTakePicture = this.minecraft.player != null && CameraItem.canTakePicture(this.minecraft.player);
      this.browseButton.visible = canTakePicture;
      if (!canTakePicture) {
         if (System.currentTimeMillis() % 1000L < 500L) {
            int y = this.height / 2 + 9 + 4;
            graphics.drawCenteredString(this.font, Component.translatable("text.camerapture.no_paper"), this.width / 2, y, -65536);
         }
      } else {
         graphics.drawCenteredString(this.font, description, this.width / 2, this.height / 2, -1);
      }
   }

   public void onFilesDrop(List<Path> paths) {
      for (Path path : paths) {
         if (this.tryUpload(path)) {
            this.onClose();
            return;
         }
      }
   }

   private void browseFile() {
      new Thread(() -> {
         MemoryStack stack = MemoryStack.stackPush();

         label46: {
            try {
               PointerBuffer filter = stack.mallocPointer(1);
               filter.put(stack.UTF8("*"));
               filter.flip();
               String path = TinyFileDialogs.tinyfd_openFileDialog("Open Image", "", filter, "Image File", false);
               if (path == null) {
                  break label46;
               }

               try {
                  if (this.tryUpload(Path.of(path))) {
                     Minecraft.getInstance().executeIfPossible(this::onClose);
                  }
               } catch (InvalidPathException e) {
                  Camerapture.LOGGER.error("tinyfd returned invalid path", e);
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
      }).start();
   }

   private boolean tryUpload(Path path) {
      boolean canTakePicture = this.minecraft.player != null && CameraItem.canTakePicture(this.minecraft.player);
      if (!canTakePicture) {
         return false;
      }

      PictureTaker.getInstance().tryUploadFile(path);
      return true;
   }
}
