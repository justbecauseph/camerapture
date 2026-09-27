package me.chrr.camerapture.item;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class CameraItem extends Item {
   public static final ResourceLocation ID = Camerapture.id("camera");
   public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);

   public CameraItem() {
      super(new Properties().stacksTo(1));
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      boolean active = isActive(stack);
      Config.Server config = Camerapture.CONFIG_MANAGER.getConfig().server;
      if (active || !player.isShiftKeyDown() && config.permissionLevels.canTakePicture(player)) {
         setActive(stack, !active);
         return InteractionResultHolder.consume(stack);
      }

      if (player.isShiftKeyDown()) {
         if (!config.permissionLevels.canUpload(player)) {
            player.sendSystemMessage(Component.translatable("text.camerapture.uploading_disabled").withStyle(ChatFormatting.RED));
            return InteractionResultHolder.fail(stack);
         } else {
            return InteractionResultHolder.success(stack);
         }
      } else {
         return InteractionResultHolder.pass(stack);
      }
   }

   public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
      if (!selected) {
         setActive(stack, false);
      }
   }

   public static void setActive(ItemStack stack, boolean active) {
      stack.set(Camerapture.CAMERA_ACTIVE, active);
   }

   public static boolean isActive(ItemStack stack) {
      return stack.get(Camerapture.CAMERA_ACTIVE) == Boolean.TRUE;
   }

   public static int getPaperInInventory(Player player) {
      return player.getInventory().countItem(Items.PAPER);
   }

   public static boolean canTakePicture(Player player) {
      return player.hasInfiniteMaterials() || getPaperInInventory(player) > 0;
   }

   @Nullable
   public static CameraItem.HeldCamera find(Player player, boolean shouldBeActive) {
      if (player == null) {
         return null;
      }

      for (InteractionHand hand : InteractionHand.values()) {
         ItemStack stack = player.getItemInHand(hand);
         if (stack != null && stack.is(Camerapture.CAMERA) && (!shouldBeActive || isActive(stack))) {
            return new CameraItem.HeldCamera(stack, hand);
         }
      }

      return null;
   }

   public record HeldCamera(ItemStack stack, InteractionHand hand) {
   }
}
