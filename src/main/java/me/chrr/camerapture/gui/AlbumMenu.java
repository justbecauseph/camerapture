package me.chrr.camerapture.gui;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.item.AlbumItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AlbumMenu extends AbstractContainerMenu {
   private final Container inventory;

   public AlbumMenu(int containerId, Inventory playerInventory) {
      this(containerId, playerInventory, new SimpleContainer(AlbumItem.SLOTS));
   }

   public AlbumMenu(int containerId, Inventory playerInventory, Container inventory) {
      super(Camerapture.ALBUM_SCREEN_HANDLER, containerId);
      checkContainerSize(inventory, AlbumItem.SLOTS);
      this.inventory = inventory;
      inventory.startOpen(playerInventory.player);

      for (int page = 0; page < AlbumItem.PAGES; page++) {
         for (int side = 0; side < 2; side++) {
            for (int y = 0; y < 3; y++) {
               for (int x = 0; x < 2; x++) {
                  int i = page * 12 + side * 6 + y * 2 + x;
                  int dx = side * 131 + 22 + x * 56;
                  int dy = 27 + y * 32;
                  this.addSlot(new PictureSlot(inventory, i, dx, dy));
               }
            }
         }
      }

      for (int x = 0; x < 9; x++) {
         for (int y = 0; y < 3; y++) {
            this.addSlot(new Slot(playerInventory, 9 + y * 9 + x, 60 + x * 18, 155 + y * 18));
         }
      }

      for (int x = 0; x < 9; x++) {
         this.addSlot(new Slot(playerInventory, x, 60 + x * 18, 213));
      }
   }

   public boolean stillValid(Player player) {
      return this.inventory.stillValid(player);
   }

   public ItemStack quickMoveStack(Player player, int slotId) {
      ItemStack itemStack = ItemStack.EMPTY;
      Slot slot = (Slot)this.slots.get(slotId);
      if (slot.hasItem()) {
         ItemStack stack = slot.getItem();
         itemStack = stack.copy();
         if (slotId < AlbumItem.SLOTS) {
            if (!this.moveItemStackTo(stack, AlbumItem.SLOTS, AlbumItem.SLOTS + 36, true)) {
               return ItemStack.EMPTY;
            }
         } else if (!this.tryAddPicture(stack)) {
            return ItemStack.EMPTY;
         }

         if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
         } else {
            slot.setChanged();
         }

         if (stack.getCount() == itemStack.getCount()) {
            return ItemStack.EMPTY;
         }

         slot.onTake(player, stack);
      }

      return itemStack;
   }

   private boolean tryAddPicture(ItemStack stack) {
      if (!stack.is(Camerapture.PICTURE)) {
         return false;
      }

      for (int i = 0; i < AlbumItem.SLOTS; i++) {
         Slot slot = this.getSlot(i);
         if (!slot.hasItem()) {
            ItemStack picture = stack.split(1);
            slot.setByPlayer(picture);
            return true;
         }
      }

      return false;
   }

   public void removed(Player player) {
      super.removed(player);
      this.inventory.stopOpen(player);
   }
}
