package com.impendingdoom.network;

import com.impendingdoom.ImpendingDoom;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Marks an entity for client side effects. Bit 1 = shaking (bloodlust haze), bit 2 = charged (light blue glint). */
public record EntityMarkPayload(int entityId, int flags) implements CustomPacketPayload {
    public static final int SHAKING = 1;
    public static final int CHARGED = 2;
    public static final Type<EntityMarkPayload> TYPE = new Type<>(ImpendingDoom.id("mark"));
    public static final StreamCodec<ByteBuf, EntityMarkPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EntityMarkPayload::entityId,
            ByteBufCodecs.VAR_INT, EntityMarkPayload::flags,
            EntityMarkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
