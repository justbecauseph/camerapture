package me.chrr.camerapture.gui;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.block.PictureFrameBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class PictureFrameMenu extends AbstractContainerMenu {
   @Nullable
   private final PictureFrameBlockEntity blockEntity;

   public PictureFrameMenu(int containerId) {
      this(containerId, null, new SimpleContainerData(4));
   }

   public PictureFrameMenu(int containerId, @Nullable PictureFrameBlockEntity blockEntity, ContainerData propertyDelegate) {
      super(Camerapture.PICTURE_FRAME_SCREEN_HANDLER, containerId);
      checkContainerDataCount(propertyDelegate, 4);
      this.addDataSlots(propertyDelegate);
      this.blockEntity = blockEntity;
   }

   public void setData(int id, int value) {
      super.setData(id, value);
      this.broadcastChanges();
   }

   public boolean clickMenuButton(Player player, int id) {
      if (this.blockEntity == null) {
         return false;
      }

      switch (id) {
         case 0:
         case 1:
            this.blockEntity.resize(PictureFrameBlockEntity.ResizeDirection.UP, id % 2 == 0);
            return true;
         case 2:
         case 3:
            this.blockEntity.resize(PictureFrameBlockEntity.ResizeDirection.RIGHT, id % 2 == 0);
            return true;
         case 4:
         case 5:
            this.blockEntity.resize(PictureFrameBlockEntity.ResizeDirection.DOWN, id % 2 == 0);
            return true;
         case 6:
         case 7:
            this.blockEntity.resize(PictureFrameBlockEntity.ResizeDirection.LEFT, id % 2 == 0);
            return true;
         case 8:
            this.blockEntity.setPictureGlowing(!this.blockEntity.isPictureGlowing());
            return true;
         case 9:
            this.blockEntity.setFixed(!this.blockEntity.isFixed());
            return true;
         default:
            return false;
      }
   }

   public ItemStack quickMoveStack(Player player, int slot) {
      return ItemStack.EMPTY;
   }

   public boolean stillValid(Player player) {
      if (this.blockEntity == null) {
         return true;
      }

      if (this.blockEntity.getLevel() != player.level()
         || player.level().getBlockEntity(this.blockEntity.getBlockPos()) != this.blockEntity) {
         return false;
      }

      AABB frameBounds = this.blockEntity.getFrameShape().bounds().move(this.blockEntity.getBlockPos());
      double reach = player.blockInteractionRange() + 4.0;
      return frameBounds.distanceToSqr(player.getEyePosition()) < reach * reach;
   }
}
