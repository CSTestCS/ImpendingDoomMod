package com.impendingdoom.network;

import com.impendingdoom.ImpendingDoom;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Sent by /impendingdoom hud: the client applies the change to its own config file. */
public record HudSettingsPayload(int action, int anchor, int x, int y) implements CustomPacketPayload {
    public static final Type<HudSettingsPayload> TYPE = new Type<>(ImpendingDoom.id("hud_settings"));
    public static final StreamCodec<ByteBuf, HudSettingsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HudSettingsPayload::action,
            ByteBufCodecs.VAR_INT, HudSettingsPayload::anchor,
            ByteBufCodecs.VAR_INT, HudSettingsPayload::x,
            ByteBufCodecs.VAR_INT, HudSettingsPayload::y,
            HudSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
