package com.impendingdoom.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Winter cold. Builds up the vanilla frozen meter (frost overlay, slowness and freeze damage once fully frozen).
 * Higher amplifiers freeze faster. Vanilla thaws by 2 per tick outside powder snow, so the net gain is amplifier + 1.
 */
public class FreezingEffect extends MobEffect {
    public FreezingEffect() {
        super(MobEffectCategory.HARMFUL, 0xA6E3FF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity.canFreeze()) {
            int max = entity.getTicksRequiredToFreeze() + 20;
            entity.setTicksFrozen(Math.min(max, entity.getTicksFrozen() + 3 + amplifier));
        }
        return true;
    }
}
