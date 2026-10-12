package com.impendingdoom.core;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** World-wide schedule and event state, stored on the overworld. */
public final class DoomSavedData extends SavedData {
    public static final Codec<DoomSavedData> CODEC = CompoundTag.CODEC.xmap(DoomSavedData::load, DoomSavedData::save);
    public static final SavedDataType<DoomSavedData> TYPE = new SavedDataType<>(com.impendingdoom.ImpendingDoom.id("state"), DoomSavedData::new, CODEC);

    public boolean initialized;
    /** Day index on which the last event (or the schedule) started. */
    public long lastEventDay;
    public int minorsSinceMajor;
    /** Index into {@link EventRegistry#SEASONS} of the next season to occur. */
    public int seasonIndex;
    public long majorStartDay;
    public long eventCount;
    public int omenSerial;

    public String currentMinor = "";
    public String currentMajor = "";
    public String nextEvent = "";
    public String lastMinor = "";
    public String lastMajor = "";

    public CompoundTag minorData = new CompoundTag();
    public CompoundTag majorData = new CompoundTag();
    /** Persistent hunter groups for the Hunted event, keyed by player UUID. */
    public CompoundTag hunters = new CompoundTag();

    public DoomSavedData() {}

    public static DoomSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    private static DoomSavedData load(CompoundTag tag) {
        DoomSavedData d = new DoomSavedData();
        d.initialized = tag.getBooleanOr("initialized", false);
        d.lastEventDay = tag.getLongOr("lastEventDay", 0L);
        d.minorsSinceMajor = tag.getIntOr("minorsSinceMajor", 0);
        d.seasonIndex = tag.getIntOr("seasonIndex", 0);
        d.majorStartDay = tag.getLongOr("majorStartDay", 0L);
        d.eventCount = tag.getLongOr("eventCount", 0L);
        d.omenSerial = tag.getIntOr("omenSerial", 0);
        d.currentMinor = tag.getStringOr("currentMinor", "");
        d.currentMajor = tag.getStringOr("currentMajor", "");
        d.nextEvent = tag.getStringOr("nextEvent", "");
        d.lastMinor = tag.getStringOr("lastMinor", "");
        d.lastMajor = tag.getStringOr("lastMajor", "");
        d.minorData = tag.getCompoundOrEmpty("minorData").copy();
        d.majorData = tag.getCompoundOrEmpty("majorData").copy();
        d.hunters = tag.getCompoundOrEmpty("hunters").copy();
        return d;
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("initialized", initialized);
        tag.putLong("lastEventDay", lastEventDay);
        tag.putInt("minorsSinceMajor", minorsSinceMajor);
        tag.putInt("seasonIndex", seasonIndex);
        tag.putLong("majorStartDay", majorStartDay);
        tag.putLong("eventCount", eventCount);
        tag.putInt("omenSerial", omenSerial);
        tag.putString("currentMinor", currentMinor);
        tag.putString("currentMajor", currentMajor);
        tag.putString("nextEvent", nextEvent);
        tag.putString("lastMinor", lastMinor);
        tag.putString("lastMajor", lastMajor);
        tag.put("minorData", minorData.copy());
        tag.put("majorData", majorData.copy());
        tag.put("hunters", hunters.copy());
        return tag;
    }

    public String nextSeason() {
        return EventRegistry.SEASONS.get(Math.floorMod(seasonIndex, EventRegistry.SEASONS.size()));
    }
}
