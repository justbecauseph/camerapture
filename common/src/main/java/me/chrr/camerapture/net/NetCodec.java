package me.chrr.camerapture.net;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record NetCodec<T>(Identifier id, StreamCodec<ByteBuf, T> streamCodec) {
    public NetCodec(Identifier id, Codec<T> codec) {
        this(id, ByteBufCodecs.fromCodec(codec));
    }
}
