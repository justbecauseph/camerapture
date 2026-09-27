package me.chrr.camerapture.compat;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.block.PictureFrameBlock;
import me.chrr.camerapture.block.PictureFrameBlockEntity;
import me.chrr.camerapture.item.PictureItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public class JadePlugin implements IWailaPlugin {
   public void registerClient(IWailaClientRegistration registration) {
      registration.registerBlockComponent(JadePlugin.PictureFrameBlockComponentProvider.INSTANCE, PictureFrameBlock.class);
   }

   private enum PictureFrameBlockComponentProvider implements IBlockComponentProvider {
      INSTANCE;

      public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
         if (accessor.getBlockEntity() instanceof PictureFrameBlockEntity blockEntity) {
            ItemStack itemStack = blockEntity.getItemStack();
            if (itemStack == null || itemStack.isEmpty()) {
               return;
            }

            PictureItem.getTooltip(tooltip::add, itemStack);
         }
      }

      public ResourceLocation getUid() {
         return Camerapture.id("picture_frame_block");
      }
   }
}
