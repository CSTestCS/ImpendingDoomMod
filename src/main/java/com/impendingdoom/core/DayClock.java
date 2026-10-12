package com.impendingdoom.core;

import net.minecraft.world.level.Level;

/**
 * Minecraft-day based clock. Days are counted from the world's day time, so sleeping through the night
 * advances the schedule exactly like it advances the calendar.
 */
public final class DayClock {
    public static final long DAY_LENGTH = 24000L;

    private DayClock() {}

    public static long dayTime(Level level) {
        return level.getOverworldClockTime();
    }

    public static long day(Level level) {
        return Math.floorDiv(dayTime(level), DAY_LENGTH);
    }

    public static long timeOfDay(Level level) {
        return Math.floorMod(dayTime(level), DAY_LENGTH);
    }

    public static boolean isNight(Level level) {
        long t = timeOfDay(level);
        return t >= 13000L && t < 23000L;
    }

    public static boolean isDay(Level level) {
        return !isNight(level);
    }

    /** Fractional day count, e.g. 3.5 = noon... well, 18:00 of day 3. */
    public static double preciseDay(Level level) {
        return dayTime(level) / (double) DAY_LENGTH;
    }
}
