package me.chrr.camerapture.gui;

import me.chrr.camerapture.Camerapture;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AlbumLecternMenu extends AbstractContainerMenu {
   private final Container inventory;

   public AlbumLecternMenu(int containerId) {
      this(containerId, new SimpleContainer(1));
   }

   public AlbumLecternMenu(int containerId, Container inventory) {
      super(Camerapture.ALBUM_LECTERN_SCREEN_HANDLER, containerId);
      checkContainerSize(inventory, 1);
      this.inventory = inventory;
      this.addSlot(new Slot(inventory, 0, 0, 0) {
         public void setChanged() {
            super.setChanged();
            AlbumLecternMenu.this.slotsChanged(this.container);
         }
      });
   }

   public boolean clickMenuButton(Player player, int id) {
      if (!player.mayBuild()) {
         return false;
      }

      ItemStack itemStack = this.inventory.removeItemNoUpdate(0);
      this.inventory.setChanged();
      if (!player.getInventory().add(itemStack)) {
         player.drop(itemStack, false);
      }

      return true;
   }

   public ItemStack quickMoveStack(Player player, int slot) {
      return ItemStack.EMPTY;
   }

   public boolean stillValid(Player player) {
      return this.inventory.stillValid(player);
   }
}
