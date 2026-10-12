package com.impendingdoom.client;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventRegistry;
import com.impendingdoom.core.FirstEventChoice;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/** Adds a "First Doom Event" button to the Create World screen (default: random). */
@EventBusSubscriber(modid = ImpendingDoom.MODID, value = Dist.CLIENT)
public final class CreateWorldFirstEvent {
    private static Screen lastScreen;

    private CreateWorldFirstEvent() {}

    private static List<String> options() {
        List<String> ids = new ArrayList<>();
        ids.add("random");
        ids.addAll(EventRegistry.ids());
        return ids;
    }

    private static Component label() {
        String id = FirstEventChoice.pending.isEmpty() ? "random" : FirstEventChoice.pending;
        DoomEvent e = EventRegistry.get(id);
        Component value = e == null ? Component.translatable("gui.impendingdoom.first_event.random") : e.displayName();
        return Component.translatable("gui.impendingdoom.first_event", value);
    }

    @SubscribeEvent
    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen)) return;
        if (screen != lastScreen) {
            // A fresh Create World screen starts from "random".
            lastScreen = screen;
            FirstEventChoice.pending = "";
        }
        Button button = Button.builder(label(), b -> {
            List<String> ids = options();
            String current = FirstEventChoice.pending.isEmpty() ? "random" : FirstEventChoice.pending;
            int index = (ids.indexOf(current) + 1) % ids.size();
            FirstEventChoice.pending = ids.get(index).equals("random") ? "" : ids.get(index);
            b.setMessage(label());
        }).bounds(4, screen.height - 26, 170, 20).build();
        event.addListener(button);
    }
}
