package com.impendingdoom.core;

/** Anchors for the next-event HUD. The HUD settings themselves live in the client config (see DoomClientConfig). */
public final class NextEventHudSettings {
    public static final String[] ANCHORS = {"top_left", "top_center", "top_right", "middle_left", "middle_right", "bottom_left", "bottom_right"};
    /** Actions sent with HudSettingsPayload. */
    public static final int SHOW = 0, HIDE = 1, TOGGLE = 2, RESET = 3, MOVE = 4;

    private NextEventHudSettings() {}

    public static int anchorIndex(String name) {
        for (int i = 0; i < ANCHORS.length; i++) if (ANCHORS[i].equals(name)) return i;
        return -1;
    }
}
