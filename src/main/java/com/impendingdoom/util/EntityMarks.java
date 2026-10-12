package com.impendingdoom.util;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.events.minor.ElectricalStormEvent;
import com.impendingdoom.network.EntityMarkPayload;
import com.impendingdoom.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Syncs client-only visual markers (bloodlust shaking, charged glint) for entities. */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public final class EntityMarks {
    private EntityMarks() {}

    public static int flags(Entity entity) {
        int flags = 0;
        if (entity instanceof LivingEntity living && living.hasEffect(ModEffects.BLOODLUST_HAZE)) flags |= EntityMarkPayload.SHAKING;
        if (entity.entityTags().contains(ElectricalStormEvent.CHARGED)) flags |= EntityMarkPayload.CHARGED;
        return flags;
    }

    /** Re-sends the entity's markers to everyone tracking it. */
    public static void update(Entity entity) {
        if (entity.level().isClientSide()) return;
        PacketDistributor.sendToPlayersTrackingEntity(entity, new EntityMarkPayload(entity.getId(), flags(entity)));
    }

    /** Like update, but for use while the effect that decides the flag is still being removed. */
    public static void update(Entity entity, int clearFlags) {
        if (entity.level().isClientSide()) return;
        PacketDistributor.sendToPlayersTrackingEntity(entity, new EntityMarkPayload(entity.getId(), flags(entity) & ~clearFlags));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        int flags = flags(event.getTarget());
        if (flags != 0) PacketDistributor.sendToPlayer(player, new EntityMarkPayload(event.getTarget().getId(), flags));
    }
}
