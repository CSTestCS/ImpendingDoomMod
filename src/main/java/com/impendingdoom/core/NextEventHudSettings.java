package com.impendingdoom.core;

import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player settings for the next-event HUD, kept in the player's persistent data and changed with
 * {@code /impendingdoom hud}. Packed into one long for syncing: bit 0 = hidden, bits 1-4 = anchor,
 * bits 8-27 = x offset, bits 28-47 = y offset (offsets are signed, stored with a bias).
 */
public final class NextEventHudSettings {
    public static final String[] ANCHORS = {"top_left", "top_center", "top_right", "middle_left", "middle_right", "bottom_left", "bottom_right"};
    private static final String HIDDEN = "impendingdoom_hud_hidden";
    private static final String ANCHOR = "impendingdoom_hud_anchor";
    private static final String X = "impendingdoom_hud_x";
    private static final String Y = "impendingdoom_hud_y";
    private static final int BIAS = 1 << 19;

    private NextEventHudSettings() {}

    public static boolean hidden(ServerPlayer player) {
        return player.getPersistentData().getBooleanOr(HIDDEN, false);
    }

    public static void setHidden(ServerPlayer player, boolean hidden) {
        player.getPersistentData().putBoolean(HIDDEN, hidden);
    }

    public static void setPosition(ServerPlayer player, int anchor, int x, int y) {
        player.getPersistentData().putInt(ANCHOR, anchor);
        player.getPersistentData().putInt(X, x);
        player.getPersistentData().putInt(Y, y);
    }

    public static void reset(ServerPlayer player) {
        player.getPersistentData().remove(HIDDEN);
        player.getPersistentData().remove(ANCHOR);
        player.getPersistentData().remove(X);
        player.getPersistentData().remove(Y);
    }

    public static int anchorIndex(String name) {
        for (int i = 0; i < ANCHORS.length; i++) if (ANCHORS[i].equals(name)) return i;
        return -1;
    }

    public static long packed(ServerPlayer player) {
        var tag = player.getPersistentData();
        long hidden = tag.getBooleanOr(HIDDEN, false) ? 1 : 0;
        long anchor = Math.floorMod(tag.getIntOr(ANCHOR, 0), ANCHORS.length);
        long x = clamp(tag.getIntOr(X, 4)) + BIAS;
        long y = clamp(tag.getIntOr(Y, 4)) + BIAS;
        return hidden | (anchor << 1) | (x << 8) | (y << 28);
    }

    public static boolean hidden(long packed) {
        return (packed & 1) != 0;
    }

    public static int anchor(long packed) {
        return (int) ((packed >> 1) & 15);
    }

    public static int x(long packed) {
        return (int) ((packed >> 8) & 0xFFFFF) - BIAS;
    }

    public static int y(long packed) {
        return (int) ((packed >> 28) & 0xFFFFF) - BIAS;
    }

    private static int clamp(int v) {
        return Math.max(-10000, Math.min(10000, v));
    }
}
