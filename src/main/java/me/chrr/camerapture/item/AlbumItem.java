package me.chrr.camerapture.item;

import java.util.List;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.gui.AlbumMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

public class AlbumItem extends Item {
   public static final ResourceLocation ID = Camerapture.id("album");
   public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, ID);
   public static int PAGES = 3;
   public static int ITEMS_PER_PAGE = 12;
   public static int SLOTS = PAGES * ITEMS_PER_PAGE;

   public AlbumItem() {
      super(new Properties().stacksTo(1));
   }

   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!level.isClientSide() && (player.isShiftKeyDown() || getPictures(stack).isEmpty())) {
         player.openMenu(
            new SimpleMenuProvider(
               (containerId, playerInventory, playerEntity) -> new AlbumMenu(containerId, playerInventory, new AlbumItem.AlbumInventory(hand, stack)),
               stack.getHoverName()
            )
         );
      }

      return InteractionResultHolder.success(stack);
   }

   public static List<ItemStack> getPictures(ItemStack album) {
      ItemContainerContents container = (ItemContainerContents)album.get(DataComponents.CONTAINER);
      return container != null ? container.nonEmptyStream().<ItemStack>map(ItemStack::copy).toList() : List.of();
   }

   public static class AlbumInventory extends SimpleContainer {
      private final InteractionHand hand;

      private AlbumInventory(InteractionHand hand, ItemStack stack) {
         super(AlbumItem.SLOTS);
         this.hand = hand;
         ItemContainerContents container = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
         if (container != null) {
            container.copyInto(this.getItems());
         }
      }

      public boolean stillValid(Player player) {
         return this.getAlbumStack(player).is(Camerapture.ALBUM);
      }

      public void stopOpen(Player player) {
         ItemStack stack = this.getAlbumStack(player);
         stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
         super.stopOpen(player);
      }

      public int getMaxStackSize() {
         return 1;
      }

      private ItemStack getAlbumStack(Player player) {
         return player.getItemInHand(this.hand);
      }
   }
}
