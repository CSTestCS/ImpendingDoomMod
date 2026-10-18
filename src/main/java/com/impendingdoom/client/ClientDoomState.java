package com.impendingdoom.client;

import java.util.HashMap;
import java.util.Map;

/** Client copy of the synced state. */
public final class ClientDoomState {
    public static int thirst = 20;
    public static float quake;
    public static boolean blizzard;
    public static String next = "";
    /** Day-time ticks until the next event, as of {@link #syncedAt} (client game time). */
    public static long ticksUntilNext = -1;
    public static long syncedAt;
    /** entity id -> mark flags (see EntityMarkPayload) */
    public static final Map<Integer, Integer> MARKS = new HashMap<>();

    private ClientDoomState() {}

    public static void reset() {
        thirst = 20;
        quake = 0;
        blizzard = false;
        next = "";
        ticksUntilNext = -1;
        MARKS.clear();
    }
}
