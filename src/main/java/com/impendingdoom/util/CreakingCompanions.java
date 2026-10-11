package com.impendingdoom.util;

import com.impendingdoom.ImpendingDoom;
import com.impendingdoom.entity.HeartWalker;
import com.impendingdoom.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.UUID;

/**
 * Every creaking spawned by the mod is bound to a {@link HeartWalker}: a creaking heart on legs that flees
 * and hides from players. While the heart lives the creaking cannot be hurt; killing the heart kills it.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public final class CreakingCompanions {
    public static final String HEART_KEY = "impendingdoom_heart";
    public static final String CREAKING_KEY = "impendingdoom_creaking";

    private CreakingCompanions() {}

    public static HeartWalker attach(ServerLevel level, Creaking creaking) {
        HeartWalker heart = ModEntities.HEART_WALKER.get().create(level, EntitySpawnReason.EVENT);
        if (heart == null) return null;
        heart.setPos(creaking.getX(), creaking.getY(), creaking.getZ());
        heart.setPersistenceRequired();
        heart.getPersistentData().putString(CREAKING_KEY, creaking.getUUID().toString());
        creaking.getPersistentData().putString(HEART_KEY, heart.getUUID().toString());
        creaking.setPersistenceRequired();
        level.addFreshEntity(heart);
        return heart;
    }

    public static HeartWalker heartOf(ServerLevel level, Creaking creaking) {
        UUID id = EventUtil.uuid(creaking.getPersistentData(), HEART_KEY);
        if (id == null) return null;
        Entity e = level.getEntity(id);
        return e instanceof HeartWalker hw && hw.isAlive() ? hw : null;
    }

    public static void onHeartDeath(ServerLevel level, HeartWalker heart) {
        UUID id = EventUtil.uuid(heart.getPersistentData(), CREAKING_KEY);
        if (id == null) return;
        Entity e = level.getEntity(id);
        if (e instanceof Creaking creaking && creaking.isAlive()) {
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, creaking.getX(), creaking.getY() + 1.5, creaking.getZ(), 60, 0.4, 1.0, 0.4, 0.05);
            level.playSound(null, creaking.blockPosition(), SoundEvents.CREAKING_DEATH, SoundSource.HOSTILE, 1.0F, 1.0F);
            // The heart is already dead, so the damage is no longer blocked: a real death (drops, death events).
            creaking.kill(level);
        }
    }

    /** A bound creaking is invulnerable while its walking heart lives. */
    @SubscribeEvent
    public static void onCreakingHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Creaking creaking) || !(creaking.level() instanceof ServerLevel level)) return;
        if (!creaking.getPersistentData().contains(HEART_KEY)) return;
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        HeartWalker heart = heartOf(level, creaking);
        if (heart != null) {
            event.setCanceled(true);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, heart.getX(), heart.getY() + 0.8, heart.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
    }

    @SubscribeEvent
    public static void onCreakingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Creaking creaking) || !(creaking.level() instanceof ServerLevel level)) return;
        HeartWalker heart = heartOf(level, creaking);
        if (heart != null) heart.discard();
    }
}
