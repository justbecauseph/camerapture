package me.chrr.camerapture.block;

import com.mojang.serialization.MapCodec;
import me.chrr.camerapture.Camerapture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class PictureFrameBlock extends HorizontalDirectionalBlock implements EntityBlock {
   public static final ResourceLocation ID = Camerapture.id("picture_frame");
   public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, ID);
   public static final MapCodec<PictureFrameBlock> CODEC = simpleCodec(PictureFrameBlock::new);
   public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
   public static final double FRAME_THICKNESS = 0.0625;
   private static final VoxelShape NORTH_SHAPE = Block.box(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
   private static final VoxelShape SOUTH_SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
   private static final VoxelShape EAST_SHAPE = Block.box(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);
   private static final VoxelShape WEST_SHAPE = Block.box(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);

   public PictureFrameBlock(Properties properties) {
      super(properties);
      this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
   }

   public PictureFrameBlock() {
      this(Properties.of().mapColor(MapColor.NONE).noCollission().noOcclusion().instabreak().sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
   }

   protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
      return CODEC;
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      if (level.getBlockEntity(pos) instanceof PictureFrameBlockEntity blockEntity) {
         return blockEntity.getFrameShape();
      } else {
         return switch ((Direction)state.getValue(FACING)) {
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
         };
      }
   }

   @Nullable
   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new PictureFrameBlockEntity(pos, state);
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
      if (level.getBlockEntity(pos) instanceof PictureFrameBlockEntity blockEntity) {
         if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
               player.openMenu(blockEntity);
            }

            return InteractionResult.SUCCESS;
         }

         boolean canRotate = Camerapture.CONFIG_MANAGER.getConfig().server.canRotatePictures;
         if (canRotate && !blockEntity.isFixed()) {
            if (!level.isClientSide()) {
               blockEntity.setRotation(blockEntity.getRotation() + 1);
               level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
               level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            }

            return InteractionResult.SUCCESS;
         } else {
            return InteractionResult.PASS;
         }
      } else {
         return InteractionResult.PASS;
      }
   }

   public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
      if (level.getBlockEntity(pos) instanceof PictureFrameBlockEntity blockEntity) {
         if (blockEntity.isFixed() && !player.isCreative()) {
            return state;
         }

         if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            blockEntity.dropItem(serverLevel);
         }

         level.playSound(null, pos, SoundEvents.ITEM_FRAME_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
      }

      return super.playerWillDestroy(level, pos, state, player);
   }

   protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
      if (!state.is(newState.getBlock())
         && level instanceof ServerLevel serverLevel
         && level.getBlockEntity(pos) instanceof PictureFrameBlockEntity blockEntity) {
         blockEntity.dropItem(serverLevel);
      }

      super.onRemove(state, level, pos, newState, movedByPiston);
   }

   protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
      if (!level.isClientSide()
         && Camerapture.CONFIG_MANAGER.getConfig().server.checkFramePosition
         && level.getBlockEntity(pos) instanceof PictureFrameBlockEntity blockEntity) {
         if (blockEntity.isFixed()) {
            return;
         }

         if (!this.canSurvive(state, level, pos)) {
            if (level instanceof ServerLevel serverLevel) {
               blockEntity.dropItem(serverLevel);
            }

            level.removeBlock(pos, false);
         }
      }
   }

   protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
      Direction facing = (Direction)state.getValue(FACING);
      BlockPos backingPos = pos.relative(facing.getOpposite());
      BlockState backingState = level.getBlockState(backingPos);
      return backingState.isFaceSturdy(level, backingPos, facing) || backingState.isSolid();
   }
}
