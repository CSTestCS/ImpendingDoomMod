package com.impendingdoom.core;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.events.major.SummerEvent;
import com.impendingdoom.events.major.UnstableEvent;
import com.impendingdoom.network.DoomStatePayload;
import com.impendingdoom.registry.ModGameRules;
import com.impendingdoom.registry.ModItems;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
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
 * following event is chosen at random and every player receives an Omen naming it.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public final class EventScheduler {
    private static final String OMEN_KEY = "impendingdoom_omen_serial";
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
        if (id.equals(d.currentMajor)) return d.majorData;
        return new CompoundTag();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        cached = DoomSavedData.get(event.getServer());
        ActiveEvents.setServer(cached.currentMinor, cached.currentMajor);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        cached = null;
        ActiveEvents.setServer("", "");
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
            chooseNext(level, d, false);
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
                if (minor.isComplete(level, d.minorData)) endMinor(level, d, true);
            } catch (RuntimeException e) {
                logError(minor, e);
            }
        }
        DoomEvent major = EventRegistry.get(d.currentMajor);
        if (major != null) {
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
        if (forced == null && (next == null || next.isMajor() != wantMajor || (next.isSeason() && !next.id().equals(d.nextSeason())))) {
            next = pick(level, d, wantMajor);
        }

        if (!d.currentMinor.isEmpty()) endMinor(level, d, false);

        if (next.isMajor()) {
            if (!d.currentMajor.isEmpty()) endMajor(level, d);
            d.currentMajor = next.id();
            d.majorData = new CompoundTag();
            d.majorStartDay = day;
            d.minorsSinceMajor = 0;
            if (next.isSeason()) {
                d.seasonIndex = (EventRegistry.SEASONS.indexOf(next.id()) + 1) % EventRegistry.SEASONS.size();
            }
            ActiveEvents.setServer(d.currentMinor, d.currentMajor);
            next.onStart(level, d.majorData);
        } else {
            d.currentMinor = next.id();
            d.minorData = new CompoundTag();
            d.minorsSinceMajor++;
            ActiveEvents.setServer(d.currentMinor, d.currentMajor);
            next.onStart(level, d.minorData);
        }
        d.lastEventDay = day;
        d.eventCount++;
        d.setDirty();

        announce(level, next.displayName(), Component.translatable(next.isMajor() ? "omen.impendingdoom.major" : "omen.impendingdoom.minor")
                .withStyle(ChatFormatting.GRAY), next.isMajor());
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable("message.impendingdoom.event_started",
                next.displayName()).append(Component.literal(" ")).append(next.description().withStyle(ChatFormatting.GRAY)), false);

        chooseNext(level, d, true);
        syncAll(level, d);
    }

    public static void endMinor(ServerLevel level, DoomSavedData d, boolean early) {
        DoomEvent minor = EventRegistry.get(d.currentMinor);
        String id = d.currentMinor;
        CompoundTag data = d.minorData;
        d.currentMinor = "";
        d.minorData = new CompoundTag();
        d.lastMinor = id;
        ActiveEvents.setServer(d.currentMinor, d.currentMajor);
        if (minor != null) {
            try {
                minor.onEnd(level, data);
            } catch (RuntimeException e) {
                ImpendingDoom.LOGGER.error("Error ending event {}", id, e);
            }
            if (early) {
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.impendingdoom.event_ended", minor.displayName()).withStyle(ChatFormatting.GREEN), false);
            }
        }
        d.setDirty();
        syncAll(level, d);
    }

    public static void endMajor(ServerLevel level, DoomSavedData d) {
        DoomEvent major = EventRegistry.get(d.currentMajor);
        String id = d.currentMajor;
        CompoundTag data = d.majorData;
        d.currentMajor = "";
        d.majorData = new CompoundTag();
        d.lastMajor = id;
        ActiveEvents.setServer(d.currentMinor, d.currentMajor);
        if (major != null) {
            try {
                major.onEnd(level, data);
            } catch (RuntimeException e) {
                ImpendingDoom.LOGGER.error("Error ending event {}", id, e);
            }
        }
        d.setDirty();
    }

    /** Picks a random event of the requested kind, honouring season order and avoiding immediate repeats. */
    public static DoomEvent pick(ServerLevel level, DoomSavedData d, boolean major) {
        List<DoomEvent> candidates = new ArrayList<>();
        for (DoomEvent e : EventRegistry.all()) {
            if (e.isMajor() != major) continue;
            if (e.isSeason() && !e.id().equals(d.nextSeason())) continue;
            candidates.add(e);
        }
        if (candidates.size() > 1) {
            String avoid = major ? d.currentMajor : (d.currentMinor.isEmpty() ? d.lastMinor : d.currentMinor);
            candidates.removeIf(e -> e.id().equals(avoid));
        }
        return candidates.get(level.getRandom().nextInt(candidates.size()));
    }

    /** Chooses the following event and hands out omens. */
    public static void chooseNext(ServerLevel level, DoomSavedData d, boolean announce) {
        boolean major = d.minorsSinceMajor >= ModGameRules.minorEventsBeforeMajor(level);
        DoomEvent next = pick(level, d, major);
        setNext(level, d, next, announce);
    }

    public static void setNext(ServerLevel level, DoomSavedData d, DoomEvent next, boolean announce) {
        d.nextEvent = next.id();
        d.omenSerial++;
        d.setDirty();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            giveOmen(player, d);
        }
        if (announce) {
            level.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.impendingdoom.omen_received").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC), false);
        }
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

    public static ItemStack createOmen(DoomEvent event, long arrivalDay) {
        ItemStack stack = new ItemStack(ModItems.DOOM_OMEN.get());
        ChatFormatting nameColor = event.isMajor() ? ChatFormatting.DARK_RED : ChatFormatting.GOLD;
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.impendingdoom.doom_omen.named", event.displayName())
                .withStyle(s -> s.withItalic(false).withColor(nameColor)));
        List<Component> lore = List.of(
                Component.translatable(event.isMajor() ? "omen.impendingdoom.major" : "omen.impendingdoom.minor")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)),
                event.description().withStyle(s -> s.withItalic(true).withColor(ChatFormatting.DARK_PURPLE)),
                Component.translatable("omen.impendingdoom.arrives", arrivalDay + 1)
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.YELLOW)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        CompoundTag tag = new CompoundTag();
        tag.putString("event", event.id());
        tag.putLong("day", arrivalDay);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static void giveOmen(ServerPlayer player, DoomSavedData d) {
        DoomEvent next = EventRegistry.get(d.nextEvent);
        if (next == null) return;
        ItemStack omen = createOmen(next, nextEventDay((ServerLevel) player.level(), d));
        if (!player.getInventory().add(omen)) player.drop(omen, false);
        player.getPersistentData().putInt(OMEN_KEY, d.omenSerial);
        player.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    private static void announce(ServerLevel level, Component title, Component subtitle, boolean major) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            player.playNotifySound(major ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.WITHER_AMBIENT, SoundSource.MASTER, 0.8F, 0.7F);
        }
    }

    public static void syncAll(ServerLevel level, DoomSavedData d) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            sync(player, d);
        }
    }

    public static void sync(ServerPlayer player, DoomSavedData d) {
        PacketDistributor.sendToPlayer(player, new DoomStatePayload(d.currentMinor, d.currentMajor,
                SummerEvent.thirst(player), UnstableEvent.intensity()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DoomSavedData d = cached;
        if (d == null) return;
        if (!d.nextEvent.isEmpty() && player.getPersistentData().getIntOr(OMEN_KEY, -1) != d.omenSerial) {
            giveOmen(player, d);
        }
        DoomEvent minor = EventRegistry.get(d.currentMinor);
        if (minor != null) minor.onPlayerJoin(player, d.minorData);
        DoomEvent major = EventRegistry.get(d.currentMajor);
        if (major != null) major.onPlayerJoin(player, d.majorData);
        sync(player, d);
    }
}
