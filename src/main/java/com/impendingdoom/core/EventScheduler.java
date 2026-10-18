package com.impendingdoom.core;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.events.major.SummerEvent;
import com.impendingdoom.events.major.UnstableEvent;
import com.impendingdoom.events.major.WinterEvent;
import com.impendingdoom.network.DoomStatePayload;
import com.impendingdoom.registry.ModGameRules;
import com.impendingdoom.util.EventUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives the event cycle: every {@code days_between_events} Minecraft days an event begins. After
 * {@code minor_events_before_major} minor events, the next event is a major event. When an event begins the
 * following event is chosen at random; a HUD element shows every player what comes next and on which day.
 * <p>
 * Seasons are major events that persist on their own layer: a season stays active through other major events and
 * only ends when the next season begins.
 * <p>
 * Players are only ever told that an event has begun (title + one chat line). Events are never explained.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public final class EventScheduler {
    private static DoomSavedData cached;
    private static int errorCooldown;

    private EventScheduler() {}

    public static DoomSavedData data() {
        return cached;
    }

    public static boolean isActive(String id) {
        return ActiveEvents.serverActive(id);
    }

    /** The data tag of an active event, or an empty throwaway tag. */
    public static CompoundTag eventData(String id) {
        DoomSavedData d = cached;
        if (d == null) return new CompoundTag();
        if (id.equals(d.currentMinor)) return d.minorData;
        if (id.equals(d.currentSeason)) return d.seasonData;
        if (id.equals(d.currentMajor)) return d.majorData;
        return new CompoundTag();
    }

    private static void publish(DoomSavedData d) {
        ActiveEvents.setServer(d.currentMinor, d.currentMajor, d.currentSeason);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        cached = DoomSavedData.get(event.getServer());
        publish(cached);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        cached = null;
        ActiveEvents.setServer("", "", "");
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        DoomSavedData d = cached;
        if (d == null) return;
        ServerLevel level = server.overworld();
        EventUtil.runDeferred();

        long day = DayClock.day(level);
        int interval = ModGameRules.daysBetweenEvents(level);

        if (!d.initialized) {
            d.initialized = true;
            d.lastEventDay = day;
            DoomEvent first = FirstEventChoice.take();
            if (first != null && !d.disabled.contains(first.id())) {
                setNext(level, d, first);
                d.nextForced = true;
            } else {
                chooseNext(level, d);
            }
            d.setDirty();
        }
        // Time was set backwards with /time: restart the countdown rather than waiting for the old day.
        if (day < d.lastEventDay) {
            d.lastEventDay = day;
            d.setDirty();
        }
        if (day >= d.lastEventDay + interval) {
            triggerNext(level, d, day, null);
        }

        tickActive(level, d);

        if (server.getTickCount() % 20 == 0) {
            d.setDirty();
            syncAll(level, d);
        }
        if (errorCooldown > 0) errorCooldown--;
    }

    private static void tickActive(ServerLevel level, DoomSavedData d) {
        DoomEvent minor = EventRegistry.get(d.currentMinor);
        if (minor != null) {
            try {
                minor.onTick(level, d.minorData);
                if (minor.isComplete(level, d.minorData)) endMinor(level, d);
            } catch (RuntimeException e) {
                logError(minor, e);
            }
        }
        DoomEvent season = EventRegistry.get(d.currentSeason);
        if (season != null) {
            try {
                season.onTick(level, d.seasonData);
            } catch (RuntimeException e) {
                logError(season, e);
            }
        }
        DoomEvent major = EventRegistry.get(d.currentMajor);
        if (major != null && !major.isSeason()) {
            try {
                major.onTick(level, d.majorData);
            } catch (RuntimeException e) {
                logError(major, e);
            }
        }
    }

    private static void logError(DoomEvent event, RuntimeException e) {
        if (errorCooldown <= 0) {
            ImpendingDoom.LOGGER.error("Error while ticking event {}", event.id(), e);
            errorCooldown = 200;
        }
    }

    /** Starts the next event now. If {@code forced} is non-null that event is started instead of the foretold one. */
    public static void triggerNext(ServerLevel level, DoomSavedData d, long day, DoomEvent forced) {
        boolean wantMajor = d.minorsSinceMajor >= ModGameRules.minorEventsBeforeMajor(level);
        DoomEvent next = forced != null ? forced : EventRegistry.get(d.nextEvent);
        boolean chosen = forced != null || (d.nextForced && next != null);
        if (!chosen && (next == null || d.disabled.contains(next.id()) || next.isMajor() != wantMajor
                || (next.isSeason() && !next.id().equals(d.nextSeason())))) {
            next = pick(level, d, wantMajor);
        }
        d.nextForced = false;
        if (next == null) {
            // Every event is disabled: just restart the countdown.
            d.lastEventDay = day;
            d.setDirty();
            return;
        }

        if (!d.currentMinor.isEmpty()) endMinor(level, d);

        if (next.isMajor()) {
            // The previous non-season major ends; a season only ends when another season begins.
            DoomEvent previous = EventRegistry.get(d.currentMajor);
            if (previous != null && !previous.isSeason()) endMajor(level, d);
            d.currentMajor = next.id();
            d.minorsSinceMajor = 0;
            d.majorStartDay = day;
            if (next.isSeason()) {
                if (!d.currentSeason.isEmpty()) endSeason(level, d);
                d.majorData = new CompoundTag();
                d.currentSeason = next.id();
                d.seasonData = new CompoundTag();
                d.seasonIndex = (EventRegistry.SEASONS.indexOf(next.id()) + 1) % EventRegistry.SEASONS.size();
                publish(d);
                next.onStart(level, d.seasonData);
            } else {
                d.majorData = new CompoundTag();
                publish(d);
                next.onStart(level, d.majorData);
            }
        } else {
            d.currentMinor = next.id();
            d.minorData = new CompoundTag();
            d.minorsSinceMajor++;
            publish(d);
            next.onStart(level, d.minorData);
        }
        d.lastEventDay = day;
        d.eventCount++;
        d.setDirty();

        announce(level, next);
        chooseNext(level, d);
        syncAll(level, d);
    }

    public static void endMinor(ServerLevel level, DoomSavedData d) {
        DoomEvent minor = EventRegistry.get(d.currentMinor);
        String id = d.currentMinor;
        CompoundTag data = d.minorData;
        d.currentMinor = "";
        d.minorData = new CompoundTag();
        d.lastMinor = id;
        publish(d);
        if (minor != null) {
            try {
                minor.onEnd(level, data);
            } catch (RuntimeException e) {
                ImpendingDoom.LOGGER.error("Error ending event {}", id, e);
            }
        }
        d.setDirty();
        syncAll(level, d);
    }

    /** Ends the current non-season major event (if the current major is a season, ends the season). */
    public static void endMajor(ServerLevel level, DoomSavedData d) {
        DoomEvent major = EventRegistry.get(d.currentMajor);
        if (major != null && major.isSeason()) {
            endSeason(level, d);
            return;
        }
        String id = d.currentMajor;
        CompoundTag data = d.majorData;
        d.currentMajor = d.currentSeason;
        d.majorData = new CompoundTag();
        d.lastMajor = id;
        publish(d);
        if (major != null) {
            try {
                major.onEnd(level, data);
            } catch (RuntimeException e) {
                ImpendingDoom.LOGGER.error("Error ending event {}", id, e);
            }
        }
        d.setDirty();
    }

    public static void endSeason(ServerLevel level, DoomSavedData d) {
        DoomEvent season = EventRegistry.get(d.currentSeason);
        String id = d.currentSeason;
        CompoundTag data = d.seasonData;
        d.currentSeason = "";
        d.seasonData = new CompoundTag();
        if (id.equals(d.currentMajor)) d.currentMajor = "";
        publish(d);
        if (season != null) {
            try {
                season.onEnd(level, data);
            } catch (RuntimeException e) {
                ImpendingDoom.LOGGER.error("Error ending season {}", id, e);
            }
        }
        d.setDirty();
    }

    /** Picks a random event of the requested kind, honouring season order and avoiding immediate repeats. */
    public static DoomEvent pick(ServerLevel level, DoomSavedData d, boolean major) {
        List<DoomEvent> candidates = candidates(d, major);
        // If every event of the wanted kind is disabled, fall back to the other kind.
        if (candidates.isEmpty()) candidates = candidates(d, !major);
        if (candidates.isEmpty()) return null;
        if (candidates.size() > 1) {
            String avoid = major ? d.currentMajor : (d.currentMinor.isEmpty() ? d.lastMinor : d.currentMinor);
            candidates.removeIf(e -> e.id().equals(avoid));
        }
        return candidates.get(level.getRandom().nextInt(candidates.size()));
    }

    private static List<DoomEvent> candidates(DoomSavedData d, boolean major) {
        List<DoomEvent> candidates = new ArrayList<>();
        for (DoomEvent e : EventRegistry.all()) {
            if (e.isMajor() != major || d.disabled.contains(e.id())) continue;
            if (e.isSeason() && !e.id().equals(d.nextSeason())) continue;
            candidates.add(e);
        }
        return candidates;
    }

    /** Chooses the following event and hands out omens. */
    public static void chooseNext(ServerLevel level, DoomSavedData d) {
        boolean major = d.minorsSinceMajor >= ModGameRules.minorEventsBeforeMajor(level);
        DoomEvent next = pick(level, d, major);
        d.nextForced = false;
        if (next == null) {
            d.nextEvent = "";
            d.setDirty();
            return;
        }
        setNext(level, d, next);
    }

    /** Sets the next event. Callers choosing it on purpose (commands) also set {@code d.nextForced}. */
    public static void setNext(ServerLevel level, DoomSavedData d, DoomEvent next) {
        d.nextEvent = next.id();
        d.setDirty();
        syncAll(level, d);
    }

    /** Day-time ticks left until the next event begins (it begins at the start of its day). */
    public static long ticksUntilNext(ServerLevel level, DoomSavedData d) {
        return Math.max(0, nextEventDay(level, d) * DayClock.DAY_LENGTH - DayClock.dayTime(level));
    }

    /** "2d 5h", "5h 30m", "30m": Minecraft time, 1 day = 24 hours of 1000 ticks. */
    public static String formatTicks(long ticks) {
        long days = ticks / DayClock.DAY_LENGTH;
        long hours = ticks % DayClock.DAY_LENGTH / 1000;
        long minutes = ticks % 1000 * 60 / 1000;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    public static long nextEventDay(ServerLevel level, DoomSavedData d) {
        return d.lastEventDay + ModGameRules.daysBetweenEvents(level);
    }

    /** Estimated number of days until the next major event starts, used by events that ramp down at the end. */
    public static long daysUntilNextMajor(ServerLevel level, DoomSavedData d) {
        int interval = ModGameRules.daysBetweenEvents(level);
        int minorsLeft = Math.max(0, ModGameRules.minorEventsBeforeMajor(level) - d.minorsSinceMajor);
        return (d.lastEventDay + (long) interval * (minorsLeft + 1)) - DayClock.day(level);
    }

    /** The only notification players get: the event's name when it begins. */
    private static void announce(ServerLevel level, DoomEvent event) {
        Component title = event.displayName();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
            EventUtil.notifySound(player, event.isMajor() ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.WITHER_AMBIENT, SoundSource.MASTER, 0.8F, 0.7F);
        }
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.impendingdoom.event_started", event.displayName()), false);
    }

    public static void syncAll(ServerLevel level, DoomSavedData d) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            sync(player, d);
        }
    }

    public static void sync(ServerPlayer player, DoomSavedData d) {
        int flags = WinterEvent.blizzard() ? DoomStatePayload.BLIZZARD : 0;
        PacketDistributor.sendToPlayer(player, new DoomStatePayload(d.currentMinor, d.currentMajor, d.currentSeason,
                SummerEvent.thirst(player), UnstableEvent.intensity(), flags, d.nextEvent,
                d.nextEvent.isEmpty() ? -1 : ticksUntilNext(((ServerLevel) player.level()).getServer().overworld(), d)));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DoomSavedData d = cached;
        if (d == null) return;
        DoomEvent minor = EventRegistry.get(d.currentMinor);
        if (minor != null) minor.onPlayerJoin(player, d.minorData);
        DoomEvent season = EventRegistry.get(d.currentSeason);
        if (season != null) season.onPlayerJoin(player, d.seasonData);
        DoomEvent major = EventRegistry.get(d.currentMajor);
        if (major != null && !major.isSeason()) major.onPlayerJoin(player, d.majorData);
        sync(player, d);
    }
}
