package me.chrr.camerapture.net.serverbound;

import me.chrr.camerapture.net.NetCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

public record RequestPictureFrameEditorPacket(BlockPos pos, Vec3 hitLocation) {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("camerapture", "request_picture_frame_editor");
    public static final NetCodec<RequestPictureFrameEditorPacket> NET_CODEC = new NetCodec<>(ID,
            StreamCodec.composite(BlockPos.STREAM_CODEC, RequestPictureFrameEditorPacket::pos,
                    Vec3.STREAM_CODEC, RequestPictureFrameEditorPacket::hitLocation,
                    RequestPictureFrameEditorPacket::new));
}
