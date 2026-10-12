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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
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
        BlockPos near = EventUtil.surfacePos(level, creaking.blockPosition(), 2, 4, level.getRandom());
        if (near != null && Math.abs(near.getY() - creaking.getY()) < 4) {
            heart.setPos(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        } else {
            heart.setPos(creaking.getX(), creaking.getY(), creaking.getZ());
        }
        heart.setPersistenceRequired();
        heart.getPersistentData().putString(CREAKING_KEY, creaking.getUUID().toString());
        creaking.getPersistentData().putString(HEART_KEY, heart.getUUID().toString());
        creaking.setPersistenceRequired();
        level.addFreshEntity(heart);
        level.sendParticles(ParticleTypes.CRIMSON_SPORE, heart.getX(), heart.getY() + 0.6, heart.getZ(), 30, 0.3, 0.4, 0.3, 0.05);
        level.playSound(null, heart.blockPosition(), SoundEvents.CREAKING_ACTIVATE, SoundSource.HOSTILE, 1.0F, 1.2F);
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
            showTrail(level, creaking, heart);
        }
    }

    /** Like a heart-bound vanilla creaking: hitting it sends trail particles to where its heart is. */
    public static void showTrail(ServerLevel level, Creaking creaking, HeartWalker heart) {
        if (creaking.tickCount - creaking.getPersistentData().getIntOr("impendingdoom_trail", -100) < 10) return;
        creaking.getPersistentData().putInt("impendingdoom_trail", creaking.tickCount);
        Vec3 target = heart.position().add(0, heart.getBbHeight() * 0.6, 0);
        RandomSource r = level.getRandom();
        int duration = Mth.clamp((int) (creaking.distanceTo(heart) * 2), 10, 60);
        for (int i = 0; i < 24; i++) {
            Vec3 from = new Vec3(creaking.getX() + (r.nextDouble() - 0.5) * creaking.getBbWidth(),
                    creaking.getY() + r.nextDouble() * creaking.getBbHeight(),
                    creaking.getZ() + (r.nextDouble() - 0.5) * creaking.getBbWidth());
            int color = r.nextBoolean() ? 0xFC7812 : 0x5F5F5F;
            level.sendParticles(new TrailParticleOption(target, color, duration + r.nextInt(10)), from.x, from.y, from.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, creaking.blockPosition(), SoundEvents.CREAKING_SWAY, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** A creaking drifts back toward its heart when it has nothing to chase. */
    @SubscribeEvent
    public static void onCreakingTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Creaking creaking) || creaking.tickCount % 20 != 0 || !(creaking.level() instanceof ServerLevel level)) return;
        if (!creaking.getPersistentData().contains(HEART_KEY) || creaking.getTarget() != null) return;
        HeartWalker heart = heartOf(level, creaking);
        if (heart != null && creaking.distanceToSqr(heart) > 18 * 18) {
            creaking.getNavigation().moveTo(heart, 1.0);
        }
    }

    @SubscribeEvent
    public static void onCreakingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Creaking creaking) || !(creaking.level() instanceof ServerLevel level)) return;
        HeartWalker heart = heartOf(level, creaking);
        if (heart != null) heart.discard();
    }
}
