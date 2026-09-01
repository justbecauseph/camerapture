package me.chrr.camerapture.net.clientbound;

import io.netty.buffer.ByteBuf;
import me.chrr.camerapture.net.NetCodec;
import me.chrr.camerapture.picture.PictureQuality;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

import java.util.UUID;

public record PictureErrorPacket(UUID uuid, PictureQuality quality, Reason reason) {
    public enum Reason implements StringRepresentable {
        NOT_FOUND("not_found"),
        BUSY("busy");

        public static final StreamCodec<ByteBuf, Reason> STREAM_CODEC = StreamCodec.of(
                (buf, reason) -> ByteBufCodecs.VAR_INT.encode(buf, reason.ordinal()),
                buf -> {
                    int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
                    Reason[] values = values();
                    if (ordinal >= 0 && ordinal < values.length) {
                        return values[ordinal];
                    }
                    return NOT_FOUND;
                }
        );

        private final String name;

        Reason(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public PictureErrorPacket(UUID uuid, PictureQuality quality) {
        this(uuid, quality, Reason.NOT_FOUND);
    }

    private static final Identifier ID = Identifier.fromNamespaceAndPath("camerapture", "picture_error");

    public static final StreamCodec<ByteBuf, PictureErrorPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, packet.uuid());
                PictureQuality.STREAM_CODEC.encode(buf, packet.quality());
                Reason.STREAM_CODEC.encode(buf, packet.reason());
            },
            buf -> {
                UUID uuid = UUIDUtil.STREAM_CODEC.decode(buf);
                PictureQuality quality = PictureQuality.STREAM_CODEC.decode(buf);
                Reason reason = Reason.STREAM_CODEC.decode(buf);
                return new PictureErrorPacket(uuid, quality, reason);
            }
    );

    public static final NetCodec<PictureErrorPacket> NET_CODEC = new NetCodec<>(ID, STREAM_CODEC);
}