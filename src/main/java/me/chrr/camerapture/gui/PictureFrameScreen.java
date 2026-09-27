package me.chrr.camerapture.gui;

import java.util.function.Consumer;
import me.chrr.camerapture.Camerapture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;

public class PictureFrameScreen extends AbstractContainerScreen<PictureFrameMenu> implements ContainerListener {
   private static final ResourceLocation TEXTURE = Camerapture.id("textures/gui/edit_picture_frame.png");
   private int frameWidth = 0;
   private int frameHeight = 0;
   private boolean glowing = false;
   private boolean fixed = false;
   private Button upButton;
   private Button leftButton;
   private Button rightButton;
   private Button downButton;
   private PictureFrameScreen.SmallCheckboxWidget glowingCheckbox;
   private PictureFrameScreen.SmallCheckboxWidget fixedCheckbox;

   public PictureFrameScreen(PictureFrameMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
      this.imageWidth = 158;
      this.imageHeight = 52;
      menu.addSlotListener(this);
   }

   protected void init() {
      super.init();
      this.upButton = (Button)this.addRenderableWidget(Button.builder(Component.empty(), button -> {
         this.sendButtonPressPacket(Screen.hasShiftDown() ? 0 : 1);
         this.frameHeight = this.frameHeight + (Screen.hasShiftDown() ? -1 : 1);
      }).bounds(this.width / 2 - this.imageWidth / 2, this.height / 2 - this.imageHeight / 2 - 20 - 4, this.imageWidth, 20).build());
      this.rightButton = (Button)this.addRenderableWidget(Button.builder(Component.empty(), button -> {
         this.sendButtonPressPacket(Screen.hasShiftDown() ? 2 : 3);
         this.frameWidth = this.frameWidth + (Screen.hasShiftDown() ? -1 : 1);
      }).bounds(this.width / 2 + this.imageWidth / 2 + 4, this.height / 2 - this.imageHeight / 2, 20, this.imageHeight).build());
      this.downButton = (Button)this.addRenderableWidget(Button.builder(Component.empty(), button -> {
         this.sendButtonPressPacket(Screen.hasShiftDown() ? 4 : 5);
         this.frameHeight = this.frameHeight + (Screen.hasShiftDown() ? -1 : 1);
      }).bounds(this.width / 2 - this.imageWidth / 2, this.height / 2 + this.imageHeight / 2 + 4, this.imageWidth, 20).build());
      this.leftButton = (Button)this.addRenderableWidget(Button.builder(Component.empty(), button -> {
         this.sendButtonPressPacket(Screen.hasShiftDown() ? 6 : 7);
         this.frameWidth = this.frameWidth + (Screen.hasShiftDown() ? -1 : 1);
      }).bounds(this.width / 2 - this.imageWidth / 2 - 20 - 4, this.height / 2 - this.imageHeight / 2, 20, this.imageHeight).build());
      this.glowingCheckbox = (PictureFrameScreen.SmallCheckboxWidget)this.addRenderableWidget(
         new PictureFrameScreen.SmallCheckboxWidget(Component.translatable("text.camerapture.edit_picture_frame.glowing"), glowing -> {
            this.sendButtonPressPacket(8);
            this.glowing = glowing;
         }, this.width / 2 - this.imageWidth / 2 + 7, this.height / 2 - this.imageHeight / 2 + 34, false, this.glowing)
      );
      this.fixedCheckbox = (PictureFrameScreen.SmallCheckboxWidget)this.addRenderableWidget(
         new PictureFrameScreen.SmallCheckboxWidget(Component.translatable("text.camerapture.edit_picture_frame.fixed"), fixed -> {
            this.sendButtonPressPacket(9);
            this.fixed = fixed;
         }, this.width / 2 + this.imageWidth / 2 - 7 - 11, this.height / 2 - this.imageHeight / 2 + 34, true, this.fixed)
      );
      this.updateButtons();
   }

   private void sendButtonPressPacket(int id) {
      if (this.minecraft.gameMode != null) {
         this.minecraft.gameMode.handleInventoryButtonClick(((PictureFrameMenu)this.menu).containerId, id);
      }
   }

   public void slotChanged(AbstractContainerMenu menu, int slotId, ItemStack stack) {
   }

   public void dataChanged(AbstractContainerMenu menu, int property, int value) {
      switch (property) {
         case 0:
            this.frameWidth = value;
            break;
         case 1:
            this.frameHeight = value;
            break;
         case 2:
            this.glowing = value == 1;
            break;
         case 3:
            this.fixed = value == 1;
      }

      this.updateButtons();
   }

   @Override
   public void removed() {
      this.menu.removeSlotListener(this);
      super.removed();
   }

   protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
      graphics.blit(TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
   }

   protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
      graphics.drawCenteredString(
         this.font,
         Component.translatable("text.camerapture.edit_picture_frame.size", new Object[]{this.frameWidth, this.frameHeight}),
         this.imageWidth / 2,
         7,
         -1
      );
      graphics.drawCenteredString(
         this.font, Component.translatable("text.camerapture.edit_picture_frame.shrink_hint"), this.imageWidth / 2, 7 + 9 + 2, -8355712
      );
   }

   private void updateButtons() {
      if (this.upButton == null
         || this.leftButton == null
         || this.rightButton == null
         || this.downButton == null
         || this.glowingCheckbox == null
         || this.fixedCheckbox == null) {
         return;
      }

      if (Screen.hasShiftDown()) {
         this.upButton.setMessage(Component.nullToEmpty("↓"));
         this.leftButton.setMessage(Component.nullToEmpty("→"));
         this.rightButton.setMessage(Component.nullToEmpty("←"));
         this.downButton.setMessage(Component.nullToEmpty("↑"));
         this.upButton.active = this.frameHeight > 1;
         this.leftButton.active = this.frameWidth > 1;
         this.rightButton.active = this.frameWidth > 1;
         this.downButton.active = this.frameHeight > 1;
      } else {
         this.upButton.setMessage(Component.nullToEmpty("↑"));
         this.leftButton.setMessage(Component.nullToEmpty("←"));
         this.rightButton.setMessage(Component.nullToEmpty("→"));
         this.downButton.setMessage(Component.nullToEmpty("↓"));
         this.upButton.active = this.frameHeight < 16;
         this.leftButton.active = this.frameWidth < 16;
         this.rightButton.active = this.frameWidth < 16;
         this.downButton.active = this.frameHeight < 16;
      }

      this.glowingCheckbox.checked = this.glowing;
      this.fixedCheckbox.checked = this.fixed;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      this.updateButtons();
      return super.keyPressed(keyCode, scanCode, modifiers);
   }

   public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
      this.updateButtons();
      return super.keyReleased(keyCode, scanCode, modifiers);
   }

   public boolean isPauseScreen() {
      return false;
   }

   private static class SmallCheckboxWidget extends AbstractButton {
      private final boolean leftText;
      private boolean checked;
      private final Consumer<Boolean> onChange;

      public SmallCheckboxWidget(Component text, Consumer<Boolean> onChange, int x, int y, boolean leftText, boolean checked) {
         super(x, y, 11, 11, text);
         this.onChange = onChange;
         this.leftText = leftText;
         this.checked = checked;
      }

      public void onPress() {
         this.checked = !this.checked;
         this.onChange.accept(this.checked);
      }

      protected void updateWidgetNarration(NarrationElementOutput output) {
         output.add(NarratedElementType.TITLE, this.createNarrationMessage());
         if (this.active) {
            String action = this.checked ? "uncheck" : "check";
            if (this.isFocused()) {
               output.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.focused." + action));
            } else {
               output.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.hovered." + action));
            }
         }
      }

      protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
         Font font = Minecraft.getInstance().font;
         int textX = this.getX() + (this.leftText ? -4 - font.width(this.getMessage()) : 15);
         graphics.drawString(font, this.getMessage(), textX, this.getY() + 2, -2039584);
         graphics.blit(PictureFrameScreen.TEXTURE, this.getX(), this.getY(), this.isHoveredOrFocused() ? 11.0F : 0.0F, 52.0F, 11, 11, 256, 256);
         if (this.checked) {
            graphics.blit(PictureFrameScreen.TEXTURE, this.getX(), this.getY(), 22.0F, 52.0F, 11, 11, 256, 256);
         }
      }
   }
}
