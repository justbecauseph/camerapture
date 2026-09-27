package me.chrr.camerapture.entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.block.PictureFrameBlock;
import me.chrr.camerapture.block.PictureFrameBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class LegacyPictureFrameEntity extends Entity {
   private static final AtomicInteger MIGRATED_COUNT = new AtomicInteger();
   private static final Set<UUID> PENDING_ENTITIES = ConcurrentHashMap.newKeySet();
   private LegacyPictureFrameData frameData = new LegacyPictureFrameData(BlockPos.ZERO, Direction.SOUTH, 1, 1, false, false, 0);
   private ItemStack picture = ItemStack.EMPTY;
   private CompoundTag rawItemTag = new CompoundTag();
   private boolean hadItemTag;
   private boolean warnedPending;

   public LegacyPictureFrameEntity(EntityType<? extends LegacyPictureFrameEntity> type, Level level) {
      super(type, level);
      this.noPhysics = true;
   }

   protected void defineSynchedData(Builder builder) {
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
      this.frameData = LegacyPictureFrameData.fromTag(tag);
      this.hadItemTag = tag.contains("Item");
      this.rawItemTag = this.hadItemTag ? tag.getCompound("Item").copy() : new CompoundTag();
      this.picture = this.hadItemTag ? ItemStack.parseOptional(this.registryAccess(), this.rawItemTag) : ItemStack.EMPTY;
   }

   protected void addAdditionalSaveData(CompoundTag tag) {
      this.frameData.writeTo(tag);
      if (!this.picture.isEmpty()) {
         tag.put("Item", this.picture.save(this.registryAccess()));
      } else if (this.hadItemTag) {
         tag.put("Item", this.rawItemTag.copy());
      }
   }

   protected boolean repositionEntityAfterLoad() {
      return false;
   }

   public void tick() {
      super.tick();
      if (this.level() instanceof ServerLevel serverLevel) {
         this.migrate(serverLevel);
      }
   }

   private void migrate(ServerLevel level) {
      if (!this.frameData.hasHorizontalFacing()) {
         this.markPending("saved facing is not horizontal");
      } else if (this.hadItemTag && !this.picture.isEmpty() && this.picture.is(Camerapture.PICTURE)) {
         BlockPos pos = this.frameData.attachmentPos();
         if (level.hasChunkAt(pos)) {
            BlockState previousState = level.getBlockState(pos);
            boolean placedBlock = false;
            if (!previousState.is(Camerapture.PICTURE_FRAME_BLOCK)) {
               if (!previousState.canBeReplaced()) {
                  this.markPending("anchor position is occupied by " + previousState.getBlock());
                  return;
               }

               BlockState migratedState = (BlockState)Camerapture.PICTURE_FRAME_BLOCK
                  .defaultBlockState()
                  .setValue(PictureFrameBlock.FACING, this.frameData.facing());
               if (!level.setBlock(pos, migratedState, 3)) {
                  this.markPending("the picture-frame block could not be placed");
                  return;
               }

               placedBlock = true;
            }

            if (level.getBlockEntity(pos) instanceof PictureFrameBlockEntity blockEntity) {
               ItemStack existingPicture = blockEntity.getItemStack();
               if (!existingPicture.isEmpty() && !ItemStack.isSameItemSameComponents(existingPicture, this.picture)) {
                  this.markPending("a different migrated picture already occupies the anchor");
               } else {
                  blockEntity.applyLegacyMigration(
                     this.picture, this.frameData.glowing(), this.frameData.fixed(), this.frameData.rotation(), this.frameData.width(), this.frameData.height()
                  );
                  this.discard();
                  int migrated = MIGRATED_COUNT.incrementAndGet();
                  PENDING_ENTITIES.remove(this.getUUID());
                  if (migrated != 1 && migrated % 100 != 0) {
                     Camerapture.LOGGER.debug("Migrated legacy picture frame at {}", pos);
                  } else {
                     Camerapture.LOGGER.info("Migrated {} legacy picture frame(s); latest was at {}", migrated, pos);
                  }
               }
            } else {
               if (placedBlock) {
                  level.setBlock(pos, previousState, 3);
               }

               this.markPending("the migrated block entity was not created");
            }
         }
      } else {
         this.markPending("saved picture item is missing or unreadable");
      }
   }

   private void markPending(String reason) {
      if (!this.warnedPending) {
         this.warnedPending = true;
         PENDING_ENTITIES.add(this.getUUID());
         Camerapture.LOGGER
            .warn(
               "Could not migrate legacy picture frame {} at {}: {}. The legacy entity was retained for recovery.",
               this.getUUID(),
               this.frameData.attachmentPos(),
               reason
            );
      }
   }

   public static int getMigratedCount() {
      return MIGRATED_COUNT.get();
   }

   public static int getPendingCount() {
      return PENDING_ENTITIES.size();
   }

   public static void beginMigrationSession() {
      MIGRATED_COUNT.set(0);
      PENDING_ENTITIES.clear();
   }

   public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
      return new ClientboundAddEntityPacket(this, serverEntity);
   }
}
