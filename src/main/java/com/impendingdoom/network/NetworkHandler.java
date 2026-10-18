package com.impendingdoom.network;

import com.impendingdoom.client.ClientPayloadHandler;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {
    private NetworkHandler() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // The lambdas keep the client-only handler class from loading on a dedicated server.
        registrar.playToClient(DoomStatePayload.TYPE, DoomStatePayload.CODEC, (payload, ctx) -> ClientPayloadHandler.handleState(payload));
        registrar.playToClient(EntityMarkPayload.TYPE, EntityMarkPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.handleMark(payload));
        registrar.playToClient(HudSettingsPayload.TYPE, HudSettingsPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.handleHud(payload));
        registrar.playToClient(HallucinationPayload.TYPE, HallucinationPayload.CODEC, (payload, ctx) -> ClientPayloadHandler.handleHallucination(payload));
    }
}
