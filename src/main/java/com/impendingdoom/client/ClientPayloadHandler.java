package com.impendingdoom.client;

import com.impendingdoom.core.ActiveEvents;
import com.impendingdoom.events.major.FallEvent;
import com.impendingdoom.network.DoomStatePayload;
import com.impendingdoom.network.EntityMarkPayload;
import com.impendingdoom.network.HallucinationPayload;
import net.minecraft.client.Minecraft;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void handleState(DoomStatePayload payload) {
        boolean wasFall = ActiveEvents.clientActive(FallEvent.ID);
        ActiveEvents.setClient(payload.minor(), payload.major());
        ClientDoomState.thirst = payload.thirst();
        ClientDoomState.quake = payload.quake();
        boolean isFall = ActiveEvents.clientActive(FallEvent.ID);
        if (wasFall != isFall && Minecraft.getInstance().levelRenderer != null) {
            // Leaf tint changed: rebuild chunk meshes.
            Minecraft.getInstance().levelRenderer.resetLevelRenderData();
        }
    }

    public static void handleMark(EntityMarkPayload payload) {
        if (payload.flags() == 0) ClientDoomState.MARKS.remove(payload.entityId());
        else ClientDoomState.MARKS.put(payload.entityId(), payload.flags());
    }

    public static void handleHallucination(HallucinationPayload payload) {
        Hallucinations.spawn(payload);
    }
}
