package me.chrr.camerapture.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.block.PictureFrameBlock;
import me.chrr.camerapture.block.PictureFrameBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

public class PictureItem extends Item {
   public static final ResourceLocation ID = Camerapture.id("picture");
   public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);
   private static final SimpleDateFormat SDF = new SimpleDateFormat("MMM d, yyyy 'at' HH:mm");

   public PictureItem() {
      super(new Properties());
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      return !player.isShiftKeyDown() ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
   }

   public InteractionResult useOn(UseOnContext context) {
      Player player = context.getPlayer();
      if (player != null && player.isShiftKeyDown()) {
         Level level = context.getLevel();
         Direction facing = context.getClickedFace();
         BlockPos pos = context.getClickedPos().relative(facing);
         ItemStack itemStack = context.getItemInHand();
         if (facing.getAxis().isVertical() || !player.mayUseItemAt(pos, facing, itemStack)) {
            return InteractionResult.PASS;
         }

         if (!level.getBlockState(pos).canBeReplaced()) {
            return InteractionResult.PASS;
         }

         BlockState backingState = level.getBlockState(context.getClickedPos());
         if (!backingState.isSolid()) {
            return InteractionResult.PASS;
         }

         if (!level.isClientSide()) {
            BlockState frameState = (BlockState)Camerapture.PICTURE_FRAME_BLOCK.defaultBlockState().setValue(PictureFrameBlock.FACING, facing);
            level.setBlock(pos, frameState, 3);
            if (level.getBlockEntity(pos) instanceof PictureFrameBlockEntity blockEntity) {
               blockEntity.setItemStack(itemStack.copyWithCount(1));
            }

            level.playSound(null, pos, SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
         }

         itemStack.shrink(1);
         return InteractionResult.SUCCESS;
      } else {
         return InteractionResult.PASS;
      }
   }

   public static ItemStack create(String creator, UUID uuid) {
      ItemStack stack = new ItemStack(Camerapture.PICTURE, 1);
      stack.set(Camerapture.PICTURE_DATA, new PictureItem.PictureData(uuid, creator, System.currentTimeMillis()));
      return stack;
   }

   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
      getTooltip(tooltip::add, stack);
   }

   public static void getTooltip(Consumer<Component> textConsumer, ItemStack stack) {
      PictureItem.PictureData data = getPictureData(stack);
      if (data != null) {
         textConsumer.accept(
            Component.translatable("item.camerapture.picture.creator_tooltip", new Object[]{Component.literal(data.creator).withStyle(ChatFormatting.GRAY)})
               .withStyle(ChatFormatting.DARK_GRAY)
         );
         String timestamp = SDF.format(new Date(data.timestamp));
         textConsumer.accept(
            Component.translatable("item.camerapture.picture.timestamp_tooltip", new Object[]{Component.literal(timestamp).withStyle(ChatFormatting.GRAY)})
               .withStyle(ChatFormatting.DARK_GRAY)
         );
      }
   }

   @Nullable
   public static PictureItem.PictureData getPictureData(ItemStack stack) {
      return (PictureItem.PictureData)stack.get(Camerapture.PICTURE_DATA);
   }

   public record PictureData(UUID id, String creator, long timestamp) {
      public static Codec<PictureItem.PictureData> CODEC = RecordCodecBuilder.create(
         instance -> instance.group(
               UUIDUtil.AUTHLIB_CODEC.fieldOf("id").forGetter(component -> component.id),
               Codec.STRING.fieldOf("creator").forGetter(component -> component.creator),
               Codec.LONG.fieldOf("timestamp").forGetter(component -> component.timestamp)
            )
            .apply(instance, PictureItem.PictureData::new)
      );
      public static StreamCodec<ByteBuf, PictureItem.PictureData> PACKET_CODEC = ByteBufCodecs.fromCodec(CODEC);
   }
}
