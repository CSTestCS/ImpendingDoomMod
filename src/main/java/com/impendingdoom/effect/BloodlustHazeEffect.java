package com.impendingdoom.effect;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mortis's Bloodlust: a red haze floats around the mob and it shakes (client side, see ClientEvents).
 * When the effect runs out naturally the mob becomes bloodlusted (see BloodlustEvent).
 */
public class BloodlustHazeEffect extends MobEffect {
    private static final DustParticleOptions HAZE = new DustParticleOptions(0xB01010, 1.4F);

    public BloodlustHazeEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0303);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 5 == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        level.sendParticles(HAZE, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                4, entity.getBbWidth() * 0.6, entity.getBbHeight() * 0.4, entity.getBbWidth() * 0.6, 0.0);
        return true;
    }
}
