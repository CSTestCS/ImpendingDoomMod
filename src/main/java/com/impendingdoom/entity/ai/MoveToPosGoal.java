package com.impendingdoom.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.function.Supplier;

/** Walks towards a (changing) destination while the mob has no target. Used for migrations and raids. */
public class MoveToPosGoal extends Goal {
    private final Mob mob;
    private final Supplier<BlockPos> destination;
    private final double speed;
    private final double stopDistance;
    private int repath;

    public MoveToPosGoal(Mob mob, Supplier<BlockPos> destination, double speed, double stopDistance) {
        this.mob = mob;
        this.destination = destination;
        this.speed = speed;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.getTarget() != null) return false;
        BlockPos pos = destination.get();
        return pos != null && mob.blockPosition().distSqr(pos) > stopDistance * stopDistance;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void tick() {
        if (--repath > 0) return;
        repath = 40;
        BlockPos pos = destination.get();
        if (pos == null) return;
        // Long trips are walked in 32 block legs so the pathfinder stays inside its range.
        double dx = pos.getX() - mob.getX();
        double dz = pos.getZ() - mob.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 32) {
            double scale = 32 / dist;
            mob.getNavigation().moveTo(mob.getX() + dx * scale, pos.getY(), mob.getZ() + dz * scale, speed);
        } else {
            mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
        }
    }
}
