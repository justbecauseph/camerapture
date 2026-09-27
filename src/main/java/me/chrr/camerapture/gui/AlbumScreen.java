package me.chrr.camerapture.gui;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.AlbumItem;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureQuality;
import me.chrr.camerapture.picture.PictureTexture;
import me.chrr.camerapture.util.PictureDrawingUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class AlbumScreen extends AbstractContainerScreen<AlbumMenu> {
   private static final ResourceLocation TEXTURE = Camerapture.id("textures/gui/edit_album.png");
   private int activePage = 0;
   private Component pageText = Component.empty();
   private PageButton previousButton;
   private PageButton nextButton;

   public AlbumScreen(AlbumMenu menu, Inventory inventory, Component title) {
      super(menu, inventory, title);
      this.imageWidth = 280;
      this.imageHeight = 237;
      this.inventoryLabelX = 60;
      this.inventoryLabelY = this.imageHeight - 94;
      this.titleLabelX = 19;
      this.titleLabelY = 15;
   }

   protected void init() {
      super.init();
      this.previousButton = (PageButton)this.addRenderableWidget(
         new PageButton(this.leftPos + 22, this.topPos + 121, false, button -> this.changePage(-1), true)
      );
      this.nextButton = (PageButton)this.addRenderableWidget(new PageButton(this.leftPos + 234, this.topPos + 121, true, button -> this.changePage(1), true));
      this.updatePage();
   }

   private void changePage(int delta) {
      this.activePage = Math.min(Math.max(this.activePage + delta, 0), AlbumItem.PAGES);
      this.updatePage();
   }

   private void updatePage() {
      for (int i = 0; i < AlbumItem.SLOTS; i++) {
         int page = i / AlbumItem.ITEMS_PER_PAGE;
         ((PictureSlot)((AlbumMenu)this.menu).slots.get(i)).setEnabled(page == this.activePage);
      }

      this.pageText = Component.translatable("book.pageIndicator", new Object[]{this.activePage + 1, AlbumItem.PAGES});
      this.previousButton.visible = this.activePage != 0;
      this.nextButton.visible = this.activePage != AlbumItem.PAGES - 1;
   }

   protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
      graphics.blit(TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 512, 512);
   }

   protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
      graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, -16777216, false);
      graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
      int textWidth = this.font.width(this.pageText);
      int pageX = this.imageWidth - this.titleLabelX - textWidth;
      graphics.drawString(this.font, this.pageText, pageX, this.titleLabelY, -16777216, false);
   }

   protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
      int xOffset = mouseY - top > 148.0 ? 52 : 0;
      return mouseX < left + xOffset || mouseY < top || mouseX >= left + this.imageWidth - xOffset || mouseY >= top + this.imageHeight;
   }

   protected void renderSlot(GuiGraphics graphics, Slot slot) {
      if (slot instanceof PictureSlot pictureSlot) {
         if (pictureSlot.hasItem()) {
            PictureItem.PictureData pictureData = PictureItem.getPictureData(slot.getItem());
            if (pictureData != null) {
               PictureTexture texture = ClientPictureStore.getInstance().resolveTextureForRender(pictureData.id(), PictureQuality.THUMBNAIL);
               PictureDrawingUtil.drawPicture(graphics, this.font, texture, slot.x, slot.y, pictureSlot.getWidth(), pictureSlot.getHeight());
            }
         } else {
            graphics.blit(TEXTURE, slot.x - 1, slot.y - 1, 280.0F, 0.0F, pictureSlot.getWidth() + 2, pictureSlot.getHeight() + 2, 512, 512);
         }
      } else {
         super.renderSlot(graphics, slot);
      }
   }
}
