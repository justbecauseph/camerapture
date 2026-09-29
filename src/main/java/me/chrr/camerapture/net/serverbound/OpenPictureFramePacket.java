package me.chrr.camerapture.net.serverbound;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.net.NetCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record OpenPictureFramePacket(BlockPos pos, Vec3 hitLocation) {
   private static final ResourceLocation ID = Camerapture.id("open_picture_frame");
   public static final NetCodec<OpenPictureFramePacket> NET_CODEC = new NetCodec<>(
      ID,
      RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(OpenPictureFramePacket::pos),
            Vec3.CODEC.fieldOf("hit_location").forGetter(OpenPictureFramePacket::hitLocation))
         .apply(instance, OpenPictureFramePacket::new))
   );
}
