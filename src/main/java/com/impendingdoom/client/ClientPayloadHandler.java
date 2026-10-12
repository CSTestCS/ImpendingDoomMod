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
        ActiveEvents.setClient(payload.minor(), payload.major(), payload.season());
        ClientDoomState.blizzard = (payload.flags() & DoomStatePayload.BLIZZARD) != 0;
        ClientDoomState.thirst = payload.thirst();
        ClientDoomState.quake = payload.quake();
        boolean isFall = ActiveEvents.clientActive(FallEvent.ID);
        if (wasFall != isFall) redrawNearbyChunks();
    }

    /** Leaf tints changed: re-mesh the sections around the player. */
    private static void redrawNearbyChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int r = mc.options.getEffectiveRenderDistance() + 1;
        int sx = mc.player.getBlockX() >> 4;
        int sz = mc.player.getBlockZ() >> 4;
        mc.level.setSectionRangeDirty(sx - r, mc.level.getMinSectionY(), sz - r, sx + r, mc.level.getMaxSectionY(), sz + r);
    }

    public static void handleMark(EntityMarkPayload payload) {
        if (payload.flags() == 0) ClientDoomState.MARKS.remove(payload.entityId());
        else ClientDoomState.MARKS.put(payload.entityId(), payload.flags());
    }

    public static void handleHallucination(HallucinationPayload payload) {
        Hallucinations.spawn(payload);
    }
}
