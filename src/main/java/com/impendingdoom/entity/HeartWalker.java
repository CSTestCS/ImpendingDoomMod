package com.impendingdoom.entity;

import com.impendingdoom.util.CreakingCompanions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A creaking heart on legs. Accompanies every creaking the mod spawns. It runs from players and hides out of
 * their sight; while it lives its creaking cannot be hurt, and killing it kills the creaking.
 */
public class HeartWalker extends PathfinderMob {
    public HeartWalker(EntityType<? extends HeartWalker> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AvoidEntityGoal<>(this, Player.class, 14.0F, 1.1, 1.5));
        goalSelector.addGoal(2, new HideGoal(this));
        goalSelector.addGoal(3, new StayNearCreakingGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, getX(), getY() + 0.6, getZ(), 30, 0.3, 0.3, 0.3, 0.05);
            CreakingCompanions.onHeartDeath(level, this);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** Keeps the heart within reach of its creaking when no player is close. */
    static class StayNearCreakingGoal extends Goal {
        private final HeartWalker mob;
        private net.minecraft.world.entity.Entity creaking;

        StayNearCreakingGoal(HeartWalker mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!(mob.level() instanceof ServerLevel level) || mob.tickCount % 20 != 0) return false;
            java.util.UUID id = com.impendingdoom.util.EventUtil.uuid(mob.getPersistentData(), CreakingCompanions.CREAKING_KEY);
            creaking = id == null ? null : level.getEntity(id);
            if (creaking == null || !creaking.isAlive() || mob.distanceToSqr(creaking) < 10 * 10) return false;
            Player near = level.getNearestPlayer(mob, 10.0);
            return near == null || near.isSpectator() || near.isCreative();
        }

        @Override
        public void start() {
            mob.getNavigation().moveTo(creaking, 1.1);
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone() && creaking != null && creaking.isAlive() && mob.distanceToSqr(creaking) > 6 * 6;
        }
    }

    /** Moves to a spot out of the nearest player's line of sight. */
    static class HideGoal extends Goal {
        private final HeartWalker mob;
        private Player threat;

        HideGoal(HeartWalker mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            threat = mob.level().getNearestPlayer(mob, 32.0);
            return threat != null && !threat.isSpectator() && threat.hasLineOfSight(mob) && mob.getRandom().nextInt(10) == 0;
        }

        @Override
        public void start() {
            for (int i = 0; i < 10; i++) {
                Vec3 pos = DefaultRandomPos.getPosAway(mob, 16, 6, threat.position());
                if (pos == null) continue;
                Vec3 eye = threat.getEyePosition();
                var clip = mob.level().clip(new net.minecraft.world.level.ClipContext(eye, pos.add(0, 0.5, 0),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, threat));
                if (clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                    mob.getNavigation().moveTo(pos.x, pos.y, pos.z, 1.4);
                    return;
                }
            }
        }

        @Override
        public boolean canContinueToUse() {
            return !mob.getNavigation().isDone();
        }
    }
}
