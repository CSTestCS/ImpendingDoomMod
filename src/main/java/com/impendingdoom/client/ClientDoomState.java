package com.impendingdoom.client;

import java.util.HashMap;
import java.util.Map;

/** Client copy of the synced state. */
public final class ClientDoomState {
    public static int thirst = 20;
    public static float quake;
    /** entity id -> mark flags (see EntityMarkPayload) */
    public static final Map<Integer, Integer> MARKS = new HashMap<>();

    private ClientDoomState() {}

    public static void reset() {
        thirst = 20;
        quake = 0;
        MARKS.clear();
    }
}
