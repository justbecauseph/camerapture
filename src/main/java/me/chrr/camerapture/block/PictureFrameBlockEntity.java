package me.chrr.camerapture.block;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.gui.PictureFrameMenu;
import me.chrr.camerapture.render.PictureLod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PictureFrameBlockEntity extends BlockEntity implements MenuProvider {
   public static final ResourceKey<BlockEntityType<?>> KEY = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Camerapture.id("picture_frame"));
   private ItemStack itemStack = ItemStack.EMPTY;
   private boolean glowing = false;
   private boolean fixed = false;
   private int rotation = 0;
   private int frameWidth = 1;
   private int frameHeight = 1;
   public PictureLod lastLod = PictureLod.SKIP;
   @Nullable
   private volatile PictureFrameBlockEntity.Geometry geometry;

   public PictureFrameBlockEntity(BlockPos pos, BlockState state) {
      super(Camerapture.PICTURE_FRAME_BLOCK_ENTITY, pos, state);
   }

   public ItemStack getItemStack() {
      return this.itemStack;
   }

   public void setItemStack(ItemStack itemStack) {
      this.itemStack = itemStack;
      this.setChanged();
      this.syncToClient();
   }

   public boolean isPictureGlowing() {
      return this.glowing;
   }

   public void setPictureGlowing(boolean glowing) {
      this.glowing = glowing;
      this.setChanged();
      this.syncToClient();
   }

   public boolean isFixed() {
      return this.fixed;
   }

   public void setFixed(boolean fixed) {
      this.fixed = fixed;
      this.setChanged();
      this.syncToClient();
   }

   public int getRotation() {
      return this.rotation;
   }

   public void setRotation(int rotation) {
      this.rotation = rotation % 4;
      this.setChanged();
      this.syncToClient();
   }

   public int getFrameWidth() {
      return this.frameWidth;
   }

   public void setFrameWidth(int width) {
      this.frameWidth = Math.max(1, Math.min(16, width));
      this.setChanged();
      this.syncToClient();
   }

   public int getFrameHeight() {
      return this.frameHeight;
   }

   public void setFrameHeight(int height) {
      this.frameHeight = Math.max(1, Math.min(16, height));
      this.setChanged();
      this.syncToClient();
   }

   public void applyLegacyMigration(ItemStack picture, boolean glowing, boolean fixed, int rotation, int width, int height) {
      this.itemStack = picture.copyWithCount(1);
      this.glowing = glowing;
      this.fixed = fixed;
      this.rotation = Math.floorMod(rotation, 4);
      this.frameWidth = Math.clamp(width, 1, 16);
      this.frameHeight = Math.clamp(height, 1, 16);
      this.geometry = null;
      this.setChanged();
      this.syncToClient();
   }

   public Direction getFacing() {
      return (Direction)this.getBlockState().getValue(PictureFrameBlock.FACING);
   }

   private PictureFrameBlockEntity.Geometry geometry() {
      Direction facing = this.getFacing();
      int key = facing.ordinal() << 10 | this.frameWidth << 5 | this.frameHeight;
      PictureFrameBlockEntity.Geometry current = this.geometry;
      if (current != null && current.key() == key) {
         return current;
      }

      double thickness = 0.0625;
      double width = this.frameWidth;
      double height = this.frameHeight;
      double minX;
      double maxX;
      double minZ;
      double maxZ;
      switch (facing) {
         case SOUTH:
            minX = 0.0;
            maxX = width;
            minZ = 0.0;
            maxZ = thickness;
            break;
         case EAST:
            minX = 0.0;
            maxX = thickness;
            minZ = 1.0 - width;
            maxZ = 1.0;
            break;
         case WEST:
            minX = 1.0 - thickness;
            maxX = 1.0;
            minZ = 0.0;
            maxZ = width;
            break;
         default:
            minX = 1.0 - width;
            maxX = 1.0;
            minZ = 1.0 - thickness;
            maxZ = 1.0;
      }

      VoxelShape shape = Shapes.box(minX, 0.0, minZ, maxX, height, maxZ);
      AABB renderBox = new AABB(
            this.worldPosition.getX() + minX,
            this.worldPosition.getY(),
            this.worldPosition.getZ() + minZ,
            this.worldPosition.getX() + maxX,
            this.worldPosition.getY() + height,
            this.worldPosition.getZ() + maxZ
         )
         .inflate(0.5);
      PictureFrameBlockEntity.Geometry computed = new PictureFrameBlockEntity.Geometry(key, shape, renderBox);
      this.geometry = computed;
      return computed;
   }

   public VoxelShape getFrameShape() {
      return this.geometry().shape();
   }

   public AABB getRenderBox() {
      return this.geometry().renderBox();
   }

   public void resize(PictureFrameBlockEntity.ResizeDirection direction, boolean shrink) {
      int delta = shrink ? -1 : 1;
      switch (direction) {
         case UP:
         case DOWN:
            int h = this.getFrameHeight() + delta;
            if (h >= 1 && h <= 16) {
               this.setFrameHeight(h);
            }
            break;
         case LEFT:
         case RIGHT:
            int w = this.getFrameWidth() + delta;
            if (w >= 1 && w <= 16) {
               this.setFrameWidth(w);
            }
      }
   }

   public void dropItem(ServerLevel level) {
      if (!this.itemStack.isEmpty()) {
         Vec3 center = Vec3.atCenterOf(this.worldPosition);
         ItemEntity itemEntity = new ItemEntity(level, center.x, center.y, center.z, this.itemStack.copy());
         itemEntity.setDefaultPickUpDelay();
         level.addFreshEntity(itemEntity);
         this.itemStack = ItemStack.EMPTY;
         this.setChanged();
      }
   }

   private void syncToClient() {
      if (this.level != null && !this.level.isClientSide()) {
         this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
      }
   }

   public CompoundTag getUpdateTag(Provider registries) {
      return this.saveWithoutMetadata(registries);
   }

   public Packet<ClientGamePacketListener> getUpdatePacket() {
      return ClientboundBlockEntityDataPacket.create(this);
   }

   protected void saveAdditional(CompoundTag tag, Provider registries) {
      super.saveAdditional(tag, registries);
      tag.put("item", this.itemStack.saveOptional(registries));
      tag.putBoolean("picture_glowing", this.glowing);
      tag.putBoolean("fixed", this.fixed);
      tag.putInt("rotation", this.rotation);
      tag.putInt("width", this.frameWidth);
      tag.putInt("height", this.frameHeight);
   }

   protected void loadAdditional(CompoundTag tag, Provider registries) {
      super.loadAdditional(tag, registries);
      this.itemStack = ItemStack.parseOptional(registries, tag.getCompound("item"));
      this.glowing = tag.getBoolean("picture_glowing");
      this.fixed = tag.getBoolean("fixed");
      this.rotation = tag.getInt("rotation");
      this.frameWidth = Math.clamp(tag.contains("width") ? tag.getInt("width") : 1L, 1, 16);
      this.frameHeight = Math.clamp(tag.contains("height") ? tag.getInt("height") : 1L, 1, 16);
      this.geometry = null;
   }

   public Component getDisplayName() {
      return (Component)(this.itemStack.get(DataComponents.CUSTOM_NAME) != null
         ? this.itemStack.getHoverName()
         : Component.translatable("block.camerapture.picture_frame"));
   }

   @Nullable
   public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
      return new PictureFrameMenu(containerId, this, new ContainerData() {
         public int get(int id) {
            return switch (id) {
               case 0 -> PictureFrameBlockEntity.this.getFrameWidth();
               case 1 -> PictureFrameBlockEntity.this.getFrameHeight();
               case 2 -> PictureFrameBlockEntity.this.isPictureGlowing() ? 1 : 0;
               case 3 -> PictureFrameBlockEntity.this.isFixed() ? 1 : 0;
               default -> 0;
            };
         }

         public void set(int id, int value) {
         }

         public int getCount() {
            return 4;
         }
      });
   }

   private record Geometry(int key, VoxelShape shape, AABB renderBox) {
   }

   public enum ResizeDirection {
      UP,
      DOWN,
      LEFT,
      RIGHT;
   }
}
