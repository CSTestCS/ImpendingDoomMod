package com.impendingdoom.entity.ai;

import com.impendingdoom.util.EventUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Melee attack goal that works for any mob, including ones without an attack damage attribute
 * (passive animals turned hostile: alphas, possessed soul thieves, bloodlusted cows...).
 */
public class ForcedMeleeGoal extends Goal {
    private final Mob mob;
    private final double speed;
    private final float fallbackDamage;
    private int cooldown;
    private int repath;

    public ForcedMeleeGoal(Mob mob, double speed, float fallbackDamage) {
        this.mob = mob;
        this.speed = speed;
        this.fallbackDamage = fallbackDamage;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--repath <= 0) {
            repath = 10;
            mob.getNavigation().moveTo(target, speed);
        }
        if (cooldown > 0) cooldown--;
        double reach = mob.getBbWidth() * 1.4 + target.getBbWidth() * 0.5 + 0.8;
        if (cooldown <= 0 && mob.distanceToSqr(target) <= reach * reach && mob.level() instanceof ServerLevel level) {
            cooldown = 20;
            mob.swing(InteractionHand.MAIN_HAND);
            float damage = EventUtil.hasAttribute(mob, Attributes.ATTACK_DAMAGE)
                    ? (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) : fallbackDamage;
            damage = Math.max(damage, fallbackDamage);
            target.hurtServer(level, mob.damageSources().mobAttack(mob), damage);
        }
    }
}
