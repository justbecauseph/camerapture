package me.chrr.camerapture.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public record LegacyPictureFrameData(BlockPos attachmentPos, Direction facing, int width, int height, boolean glowing, boolean fixed, int rotation) {
   public static LegacyPictureFrameData fromTag(CompoundTag tag) {
      BlockPos attachmentPos = new BlockPos(tag.getInt("TileX"), tag.getInt("TileY"), tag.getInt("TileZ"));
      Direction facing = Direction.from3DDataValue(tag.getByte("Facing"));
      int width = Math.clamp(tag.contains("Width") ? tag.getInt("Width") : 1L, 1, 16);
      int height = Math.clamp(tag.contains("Height") ? tag.getInt("Height") : 1L, 1, 16);
      int rotation = Math.floorMod(tag.getInt("PictureRotation"), 4);
      return new LegacyPictureFrameData(attachmentPos, facing, width, height, tag.getBoolean("PictureGlowing"), tag.getBoolean("Fixed"), rotation);
   }

   public boolean hasHorizontalFacing() {
      return this.facing.getAxis().isHorizontal();
   }

   public void writeTo(CompoundTag tag) {
      tag.putInt("TileX", this.attachmentPos.getX());
      tag.putInt("TileY", this.attachmentPos.getY());
      tag.putInt("TileZ", this.attachmentPos.getZ());
      tag.putByte("Facing", (byte)this.facing.get3DDataValue());
      tag.putInt("Width", this.width);
      tag.putInt("Height", this.height);
      tag.putBoolean("PictureGlowing", this.glowing);
      tag.putBoolean("Fixed", this.fixed);
      tag.putInt("PictureRotation", this.rotation);
   }
}
