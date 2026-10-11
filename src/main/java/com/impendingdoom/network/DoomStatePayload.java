package com.impendingdoom.network;

import com.impendingdoom.ImpendingDoom;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Event state the client needs for visuals: fog, sky tint, leaf colors, earthquake shake, thirst HUD. */
public record DoomStatePayload(String minor, String major, int thirst, float quake) implements CustomPacketPayload {
    public static final Type<DoomStatePayload> TYPE = new Type<>(ImpendingDoom.id("state"));
    public static final StreamCodec<ByteBuf, DoomStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DoomStatePayload::minor,
            ByteBufCodecs.STRING_UTF8, DoomStatePayload::major,
            ByteBufCodecs.VAR_INT, DoomStatePayload::thirst,
            ByteBufCodecs.FLOAT, DoomStatePayload::quake,
            DoomStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
