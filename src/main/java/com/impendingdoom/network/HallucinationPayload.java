package com.impendingdoom.network;

import com.impendingdoom.ImpendingDoom;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Tells one client to see something that is not there. Kind 0 = random monster, 1 = Herobrine. */
public record HallucinationPayload(int kind, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<HallucinationPayload> TYPE = new Type<>(ImpendingDoom.id("hallucination"));
    public static final StreamCodec<ByteBuf, HallucinationPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HallucinationPayload::kind,
            ByteBufCodecs.DOUBLE, HallucinationPayload::x,
            ByteBufCodecs.DOUBLE, HallucinationPayload::y,
            ByteBufCodecs.DOUBLE, HallucinationPayload::z,
            HallucinationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
