package me.chrr.camerapture.item;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import java.util.Optional;
import me.chrr.camerapture.Camerapture;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class AlbumCloningRecipe extends CustomRecipe {
   public AlbumCloningRecipe(CraftingBookCategory category) {
      super(category);
   }

   public boolean matches(CraftingInput input, Level level) {
      return this.getRecipe(input.items()).isPresent();
   }

   public ItemStack assemble(CraftingInput input, Provider registries) {
      return this.getRecipe(input.items()).<ItemStack>map(Pair::getFirst).orElse(null);
   }

   public boolean canCraftInDimensions(int width, int height) {
      return width * height >= 2;
   }

   public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
      return this.getRecipe(input.items()).<NonNullList<ItemStack>>map(Pair::getSecond).orElse(null);
   }

   private Optional<Pair<ItemStack, NonNullList<ItemStack>>> getRecipe(List<ItemStack> items) {
      NonNullList<ItemStack> remainder = NonNullList.withSize(items.size(), ItemStack.EMPTY);
      ItemStack album = ItemStack.EMPTY;
      boolean book = false;

      for (int i = 0; i < items.size(); i++) {
         ItemStack stack = items.get(i);
         if (!stack.isEmpty()) {
            if (stack.is(Camerapture.ALBUM)) {
               if (!album.isEmpty()) {
                  return Optional.empty();
               }

               remainder.set(i, stack.copyWithCount(1));
               album = stack;
            } else {
               if (!stack.is(Items.WRITABLE_BOOK) || book) {
                  return Optional.empty();
               }

               book = true;
            }
         }
      }

      return !album.isEmpty() && book ? Optional.of(new Pair(album.copy(), remainder)) : Optional.empty();
   }

   public RecipeSerializer<AlbumCloningRecipe> getSerializer() {
      return Camerapture.ALBUM_CLONING;
   }
}
