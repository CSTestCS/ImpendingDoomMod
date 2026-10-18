package com.impendingdoom.client;

import com.impendingdoom.core.ActiveEvents;
import com.impendingdoom.events.major.FallEvent;
import com.impendingdoom.config.DoomClientConfig;
import com.impendingdoom.core.NextEventHudSettings;
import com.impendingdoom.network.DoomStatePayload;
import com.impendingdoom.network.HudSettingsPayload;
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
        ClientDoomState.next = payload.next();
        ClientDoomState.ticksUntilNext = payload.ticksUntilNext();
        ClientDoomState.syncedAt = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
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

    /** /impendingdoom hud: change and save the client config. */
    public static void handleHud(HudSettingsPayload payload) {
        switch (payload.action()) {
            case NextEventHudSettings.SHOW -> DoomClientConfig.HUD_VISIBLE.set(true);
            case NextEventHudSettings.HIDE -> DoomClientConfig.HUD_VISIBLE.set(false);
            case NextEventHudSettings.TOGGLE -> DoomClientConfig.HUD_VISIBLE.set(!DoomClientConfig.HUD_VISIBLE.get());
            case NextEventHudSettings.MOVE -> {
                DoomClientConfig.HUD_VISIBLE.set(true);
                DoomClientConfig.HUD_ANCHOR.set(NextEventHudSettings.ANCHORS[Math.floorMod(payload.anchor(), NextEventHudSettings.ANCHORS.length)]);
                DoomClientConfig.HUD_X.set(payload.x());
                DoomClientConfig.HUD_Y.set(payload.y());
            }
            default -> {
                DoomClientConfig.HUD_VISIBLE.set(true);
                DoomClientConfig.HUD_ANCHOR.set("top_left");
                DoomClientConfig.HUD_X.set(4);
                DoomClientConfig.HUD_Y.set(4);
            }
        }
        DoomClientConfig.SPEC.save();
    }

    public static void handleMark(EntityMarkPayload payload) {
        if (payload.flags() == 0) ClientDoomState.MARKS.remove(payload.entityId());
        else ClientDoomState.MARKS.put(payload.entityId(), payload.flags());
    }

    public static void handleHallucination(HallucinationPayload payload) {
        Hallucinations.spawn(payload);
    }
}
