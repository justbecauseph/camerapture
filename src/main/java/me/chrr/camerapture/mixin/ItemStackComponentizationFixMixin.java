package me.chrr.camerapture.mixin;

import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import net.minecraft.util.datafix.fixes.ItemStackComponentizationFix;
import net.minecraft.util.datafix.fixes.ItemStackComponentizationFix.ItemStackData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackComponentizationFix.class)
public class ItemStackComponentizationFixMixin {
   @Inject(method = "fixItemStack", at = @At("TAIL"))
   private static void fixStack(ItemStackData itemStack, Dynamic<?> dynamic, CallbackInfo ci) {
      if (itemStack.is("camerapture:picture")) {
         camerapture$fixPicture(itemStack, dynamic);
      }

      if (itemStack.is("camerapture:album")) {
         camerapture$fixAlbum(itemStack, dynamic);
      }

      if (itemStack.is("camerapture:camera")) {
         itemStack.removeTag("active");
      }
   }

   @Unique
   private static void camerapture$fixPicture(ItemStackData data, Dynamic<?> dynamic) {
      Optional<? extends Dynamic<?>> uuid = data.removeTag("uuid").result();
      if (uuid.isPresent()) {
         String creator = data.removeTag("creator").asString("");
         long timestamp = data.removeTag("timestamp").asLong(0L);
         data.setComponent(
            "camerapture:picture_data",
            dynamic.emptyMap().set("id", uuid.get()).set("creator", dynamic.createString(creator)).set("timestamp", dynamic.createLong(timestamp))
         );
      }
   }

   @Unique
   private static void camerapture$fixAlbum(ItemStackData data, Dynamic<?> dynamic) {
      List<? extends Dynamic<?>> list = data.removeTag("Items")
         .asList(
            itemsDynamic -> itemsDynamic.emptyMap()
               .set("slot", itemsDynamic.createInt(itemsDynamic.get("Slot").asByte((byte)0)))
               .set("item", itemsDynamic.remove("Slot"))
         );
      if (!list.isEmpty()) {
         data.setComponent("minecraft:container", dynamic.createList(list.stream()));
      }
   }
}
