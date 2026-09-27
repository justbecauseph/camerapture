package me.chrr.camerapture.gui;

import java.util.List;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.AlbumItem;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;

public class AlbumLecternScreen extends PictureScreen implements MenuAccess<AlbumLecternMenu>, ContainerListener {
   private final AlbumLecternMenu menu;

   public AlbumLecternScreen(AlbumLecternMenu menu, Inventory playerInventory, Component title) {
      super(List.of());
      this.menu = menu;
   }

   @Override
   protected void init() {
      super.init();
      this.menu.addSlotListener(this);
      if (this.minecraft.player.mayBuild()) {
         this.addRenderableWidget(
            Button.builder(Component.translatable("lectern.take_book"), button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, 0))
               .bounds(this.width / 2 - 40, this.height - 24 + 2, 80, 16)
               .build()
         );
      }
   }

   public void slotChanged(AbstractContainerMenu menu, int slotId, ItemStack stack) {
      if (stack.is(Camerapture.ALBUM)) {
         this.setPictures(AlbumItem.getPictures(stack));
      }
   }

   public void dataChanged(AbstractContainerMenu menu, int property, int value) {
   }

   public AlbumLecternMenu getMenu() {
      return this.menu;
   }

   public void onClose() {
      this.minecraft.player.closeContainer();
      super.onClose();
   }

   public void removed() {
      this.menu.removeSlotListener(this);
      super.removed();
   }

   public boolean isPauseScreen() {
      return false;
   }
}
