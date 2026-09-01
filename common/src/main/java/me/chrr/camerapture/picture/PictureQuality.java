package me.chrr.camerapture.picture;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

public enum PictureQuality implements StringRepresentable {
    THUMBNAIL("thumbnail"),
    FULL("full");

    public static final Codec<PictureQuality> CODEC = StringRepresentable.fromEnum(PictureQuality::values);
    public static final StreamCodec<ByteBuf, PictureQuality> STREAM_CODEC = ByteBufCodecs.idMapper(
            id -> (id >= 0 && id < values().length) ? values()[id] : THUMBNAIL,
            Enum::ordinal
    );

    private final String name;

    PictureQuality(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
