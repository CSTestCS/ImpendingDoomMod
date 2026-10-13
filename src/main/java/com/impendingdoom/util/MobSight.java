package com.impendingdoom.util;

import com.impendingdoom.ImpendingDoom;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * What mobs spawned by the mod can see. They only go after a player they can actually see: never an invisible
 * player, and never while they are blinded. Seekers (hunters, Kaiju, decaying creepers...) are exempt: they always
 * know where their prey is.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public final class MobSight {
    /** Every mob spawned by an event. */
    public static final String EVENT_MOB = "impendingdoom.event_mob";
    /** Mobs that are meant to seek players out no matter what. */
    public static final String SEEKER = "impendingdoom.seeker";

    private MobSight() {}

    /** Whether the player is hidden from the mob regardless of distance: invisible, or the mob is blind. */
    public static boolean hiddenFrom(Mob mob, LivingEntity target) {
        return target.isInvisible() || target.hasEffect(MobEffects.INVISIBILITY) || mob.hasEffect(MobEffects.BLINDNESS);
    }

    /** Whether the mob can see the target right now. */
    public static boolean canSee(Mob mob, LivingEntity target, double range) {
        if (target == null || !target.isAlive() || target.isSpectator()) return false;
        if (mob.distanceToSqr(target) > range * range || hiddenFrom(mob, target)) return false;
        return mob.hasLineOfSight(target);
    }

    /** The nearest survival/adventure player the mob can see, or null. */
    public static Player nearestVisiblePlayer(ServerLevel level, Mob mob, double range) {
        return nearestVisiblePlayer(level, mob, range, false);
    }

    /** The nearest player the mob can see, or null. */
    public static Player nearestVisiblePlayer(ServerLevel level, Mob mob, double range, boolean includeCreative) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : level.players()) {
            if (p.isCreative() && !includeCreative) continue;
            double d = p.distanceToSqr(mob);
            if (d < bestDist && canSee(mob, p, range)) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    private static boolean restricted(LivingEntity entity) {
        var tags = entity.entityTags();
        return tags.contains(EVENT_MOB) && !tags.contains(SEEKER);
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !restricted(mob)) return;
        if (event.getNewAboutToBeSetTarget() instanceof Player player && hiddenFrom(mob, player)) {
            event.setCanceled(true);
        }
    }

    /** Drops a player target the mob can no longer perceive (the player drank invisibility, the mob was blinded). */
    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.tickCount % 10 != 0 || mob.level().isClientSide() || !restricted(mob)) return;
        if (mob.getTarget() instanceof Player player && hiddenFrom(mob, player)) {
            mob.setTarget(null);
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }
}
