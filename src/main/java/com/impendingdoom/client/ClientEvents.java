package com.impendingdoom.client;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.core.ActiveEvents;
import com.impendingdoom.core.DayClock;
import com.impendingdoom.events.major.HerobrineEvent;
import com.impendingdoom.events.major.SolarEclipseEvent;
import com.impendingdoom.events.major.WinterEvent;
import com.impendingdoom.events.minor.BloodMoonEvent;
import com.impendingdoom.events.minor.ParanoiaEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Client visuals: fog, sky tints, earthquake camera shake, shaking mobs, hallucinations. */
@EventBusSubscriber(modid = ImpendingDoom.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    private static boolean blizzard(ClientLevel level) {
        return ActiveEvents.clientActive(WinterEvent.ID) && level.getThunderLevel(1.0F) > 0.5F;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        float far = -1;
        if (ActiveEvents.clientActive(ParanoiaEvent.ID)) far = 22.0F;
        if (ActiveEvents.clientActive(HerobrineEvent.ID)) far = far < 0 ? 36.0F : Math.min(far, 36.0F);
        if (blizzard(level)) far = far < 0 ? 18.0F : Math.min(far, 18.0F);
        if (ActiveEvents.clientActive(SolarEclipseEvent.ID) && DayClock.isDay(level)) far = far < 0 ? 64.0F : Math.min(far, 64.0F);
        if (far < 0) return;
        event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.1F));
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), far));
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (ActiveEvents.clientActive(BloodMoonEvent.ID) && DayClock.isNight(level)) {
            // Blood red night sky
            event.setRed(Mth.lerp(0.75F, event.getRed(), 0.45F));
            event.setGreen(event.getGreen() * 0.2F);
            event.setBlue(event.getBlue() * 0.2F);
        }
        if (ActiveEvents.clientActive(SolarEclipseEvent.ID) && DayClock.isDay(level)) {
            event.setRed(event.getRed() * 0.08F);
            event.setGreen(event.getGreen() * 0.08F);
            event.setBlue(event.getBlue() * 0.12F);
        }
        if (blizzard(level) || ActiveEvents.clientActive(ParanoiaEvent.ID)) {
            float grey = ActiveEvents.clientActive(ParanoiaEvent.ID) ? 0.45F : 0.85F;
            event.setRed(Mth.lerp(0.7F, event.getRed(), grey));
            event.setGreen(Mth.lerp(0.7F, event.getGreen(), grey));
            event.setBlue(Mth.lerp(0.7F, event.getBlue(), grey + 0.03F));
        }
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float q = ClientDoomState.quake;
        if (q <= 0) return;
        float t = (float) (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() + event.getPartialTick());
        event.setPitch(event.getPitch() + Mth.sin(t * 2.1F) * 0.9F * q);
        event.setYaw(event.getYaw() + Mth.cos(t * 1.7F) * 0.7F * q);
        event.setRoll(event.getRoll() + Mth.sin(t * 2.9F) * 0.6F * q);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Hallucinations.tick();
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?, ?> event) {
        Boolean shaking = event.getRenderState().getRenderData(ArmorRenderData.SHAKING);
        if (shaking != null && shaking) {
            float t = event.getRenderState().ageInTicks;
            event.getPoseStack().translate(Mth.sin(t * 3.1F) * 0.04F, 0, Mth.cos(t * 2.7F) * 0.04F);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ActiveEvents.setClient("", "");
        ClientDoomState.reset();
    }
}
