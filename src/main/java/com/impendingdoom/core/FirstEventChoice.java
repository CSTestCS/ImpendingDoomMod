package com.impendingdoom.core;

import com.impendingdoom.config.DoomConfig;

/**
 * The first event of a new world. Chosen on the Create World screen (client) or with the "firstEvent" config option
 * (dedicated servers). Empty or "random" means a random event.
 */
public final class FirstEventChoice {
    /** Set by the Create World screen; consumed when the new world first ticks. */
    public static volatile String pending = "";

    private FirstEventChoice() {}

    public static DoomEvent take() {
        String id = pending;
        pending = "";
        if (id.isEmpty() || id.equals("random")) id = DoomConfig.FIRST_EVENT.get();
        return EventRegistry.get(id);
    }
}
