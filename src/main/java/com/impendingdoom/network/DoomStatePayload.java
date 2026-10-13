package com.impendingdoom.network;

import com.impendingdoom.ImpendingDoom;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Event state the client needs: fog, sky tint, leaf colors, earthquake shake, thirst HUD and the next event HUD.
 * {@code hud} holds the player's next-event HUD settings (see {@link com.impendingdoom.core.NextEventHudSettings}).
 */
public record DoomStatePayload(String minor, String major, String season, int thirst, float quake, int flags,
                               String next, long nextDay, long hud) implements CustomPacketPayload {
    public static final int BLIZZARD = 1;
    public static final Type<DoomStatePayload> TYPE = new Type<>(ImpendingDoom.id("state"));
    public static final StreamCodec<ByteBuf, DoomStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DoomStatePayload::minor,
            ByteBufCodecs.STRING_UTF8, DoomStatePayload::major,
            ByteBufCodecs.STRING_UTF8, DoomStatePayload::season,
            ByteBufCodecs.VAR_INT, DoomStatePayload::thirst,
            ByteBufCodecs.FLOAT, DoomStatePayload::quake,
            ByteBufCodecs.VAR_INT, DoomStatePayload::flags,
            ByteBufCodecs.STRING_UTF8, DoomStatePayload::next,
            ByteBufCodecs.VAR_LONG, DoomStatePayload::nextDay,
            ByteBufCodecs.VAR_LONG, DoomStatePayload::hud,
            DoomStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
