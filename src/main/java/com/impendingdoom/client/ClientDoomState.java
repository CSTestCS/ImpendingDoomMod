package com.impendingdoom.client;

import java.util.HashMap;
import java.util.Map;

/** Client copy of the synced state. */
public final class ClientDoomState {
    public static int thirst = 20;
    public static float quake;
    public static boolean blizzard;
    public static String next = "";
    public static long nextDay = -1;
    /** Packed next-event HUD settings (see NextEventHudSettings). */
    public static long hud;
    /** entity id -> mark flags (see EntityMarkPayload) */
    public static final Map<Integer, Integer> MARKS = new HashMap<>();

    private ClientDoomState() {}

    public static void reset() {
        thirst = 20;
        quake = 0;
        blizzard = false;
        next = "";
        nextDay = -1;
        hud = 0;
        MARKS.clear();
    }
}
