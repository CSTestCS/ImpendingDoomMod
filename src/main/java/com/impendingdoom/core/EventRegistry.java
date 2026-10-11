package com.impendingdoom.core;

import com.impendingdoom.events.major.FallEvent;
import com.impendingdoom.events.major.FloodingEvent;
import com.impendingdoom.events.major.HerobrineEvent;
import com.impendingdoom.events.major.KaijuEvent;
import com.impendingdoom.events.major.MeteorShowerEvent;
import com.impendingdoom.events.major.NetherInvasionEvent;
import com.impendingdoom.events.major.SolarEclipseEvent;
import com.impendingdoom.events.major.SpringEvent;
import com.impendingdoom.events.major.SummerEvent;
import com.impendingdoom.events.major.UnstableEvent;
import com.impendingdoom.events.major.WinterEvent;
import com.impendingdoom.events.minor.BlackoutEvent;
import com.impendingdoom.events.minor.BloodMoonEvent;
import com.impendingdoom.events.minor.BloodlustEvent;
import com.impendingdoom.events.minor.DistortionEvent;
import com.impendingdoom.events.minor.ElectricalStormEvent;
import com.impendingdoom.events.minor.FamineEvent;
import com.impendingdoom.events.minor.HordesEvent;
import com.impendingdoom.events.minor.HuntedEvent;
import com.impendingdoom.events.minor.IncomingRaidEvent;
import com.impendingdoom.events.minor.MonsterMutationsEvent;
import com.impendingdoom.events.minor.ParanoiaEvent;
import com.impendingdoom.events.minor.SoulThievesEvent;
import com.impendingdoom.events.minor.SwarmsEvent;
import com.impendingdoom.events.minor.ThievesEvent;
import com.impendingdoom.events.minor.TornadoEvent;
import com.impendingdoom.events.minor.WildfireEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EventRegistry {
    /** Seasons always progress in this order. */
    public static final List<String> SEASONS = List.of(SummerEvent.ID, FallEvent.ID, WinterEvent.ID, SpringEvent.ID);

    private static final Map<String, DoomEvent> EVENTS = new LinkedHashMap<>();

    static {
        // Minor events
        register(new IncomingRaidEvent());
        register(new MonsterMutationsEvent());
        register(new TornadoEvent());
        register(new ElectricalStormEvent());
        register(new DistortionEvent());
        register(new ThievesEvent());
        register(new BloodMoonEvent());
        register(new HuntedEvent());
        register(new FamineEvent());
        register(new HordesEvent());
        register(new BloodlustEvent());
        register(new BlackoutEvent());
        register(new ParanoiaEvent());
        register(new WildfireEvent());
        register(new SwarmsEvent());
        register(new SoulThievesEvent());
        // Major events
        register(new SummerEvent());
        register(new FallEvent());
        register(new WinterEvent());
        register(new SpringEvent());
        register(new HerobrineEvent());
        register(new KaijuEvent());
        register(new UnstableEvent());
        register(new MeteorShowerEvent());
        register(new FloodingEvent(FloodingEvent.WATER_ID, false));
        register(new FloodingEvent(FloodingEvent.LAVA_ID, true));
        register(new SolarEclipseEvent());
        register(new NetherInvasionEvent());
    }

    private EventRegistry() {}

    private static void register(DoomEvent event) {
        EVENTS.put(event.id(), event);
    }

    public static DoomEvent get(String id) {
        return id == null ? null : EVENTS.get(id);
    }

    public static Collection<DoomEvent> all() {
        return Collections.unmodifiableCollection(EVENTS.values());
    }

    public static Collection<String> ids() {
        return Collections.unmodifiableSet(EVENTS.keySet());
    }
}
