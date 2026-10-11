package com.impendingdoom.core;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Base class of every minor and major event. Lifecycle methods are called by {@link EventScheduler}
 * with the overworld and a per-event data tag that is saved with the world. Event specific behaviour that
 * reacts to game events lives in static NeoForge subscribers inside each event class, guarded by
 * {@link EventScheduler#isActive(String)}.
 */
public abstract class DoomEvent {
    private final String id;
    private final boolean major;
    private final ChatFormatting color;

    protected DoomEvent(String id, boolean major, ChatFormatting color) {
        this.id = id;
        this.major = major;
        this.color = color;
    }

    public final String id() {
        return id;
    }

    public final boolean isMajor() {
        return major;
    }

    public ChatFormatting color() {
        return color;
    }

    /** Seasons must always occur in order: summer, fall, winter, spring. */
    public boolean isSeason() {
        return false;
    }

    public MutableComponent displayName() {
        return Component.translatable("event.impendingdoom." + id).withStyle(color);
    }

    public MutableComponent description() {
        return Component.translatable("event.impendingdoom." + id + ".desc");
    }

    public void onStart(ServerLevel level, CompoundTag data) {}

    public void onTick(ServerLevel level, CompoundTag data) {}

    public void onEnd(ServerLevel level, CompoundTag data) {}

    /** Minor events may end before the next event by returning true. Ignored for major events. */
    public boolean isComplete(ServerLevel level, CompoundTag data) {
        return false;
    }

    /** Called when a player logs in while this event is running. */
    public void onPlayerJoin(ServerPlayer player, CompoundTag data) {}
}
