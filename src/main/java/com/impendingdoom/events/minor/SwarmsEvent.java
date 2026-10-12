package com.impendingdoom.events.minor;

import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.events.major.FallEvent;
import com.impendingdoom.util.EventUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Swarms: large groups of naturally hostile bees (hostile bats during Fall) spawn and attack together. A swarm
 * circles its victim first, then dives in all at once, regroups, and repeats.
 */
public class SwarmsEvent extends DoomEvent {
    public static final String ID = "swarms";
    private static final String TAG = "impendingdoom.swarm";
    private static final String SWARM = "impendingdoom_swarm";
    private static final String PREY = "impendingdoom_swarm_prey";
    private static final String BITE = "impendingdoom_swarm_bite";

    public SwarmsEvent() {
        super(ID, false, ChatFormatting.DARK_GREEN);
    }

    @Override
    public void onTick(ServerLevel level, CompoundTag data) {
        int cooldown = data.getIntOr("cooldown", 600) - 1;
        if (cooldown <= 0) {
            cooldown = 1800 + level.getRandom().nextInt(2400);
            ServerPlayer player = EventUtil.randomTarget(level);
            if (player != null) spawnSwarm(level, player, EventScheduler.isActive(FallEvent.ID));
        }
        data.putInt("cooldown", cooldown);
        tickSwarms(level);
    }

    private static void spawnSwarm(ServerLevel level, ServerPlayer player, boolean bats) {
        BlockPos origin = EventUtil.surfacePos(level, player.blockPosition(), 20, 28, level.getRandom());
        if (origin == null) return;
        String id = UUID.randomUUID().toString();
        int size = (bats ? 14 : 10) + level.getRandom().nextInt(8);
        for (int i = 0; i < size; i++) {
            Mob mob = bats ? EntityTypes.BAT.create(level, EntitySpawnReason.EVENT) : EntityTypes.BEE.create(level, EntitySpawnReason.EVENT);
            if (mob == null) continue;
            mob.setPos(origin.getX() + level.getRandom().nextGaussian(), origin.getY() + 4 + level.getRandom().nextInt(3),
                    origin.getZ() + level.getRandom().nextGaussian());
            mob.addTag(TAG);
            mob.getPersistentData().putString(SWARM, id);
            mob.getPersistentData().putString(PREY, player.getUUID().toString());
            mob.getPersistentData().putLong("impendingdoom_born", level.getGameTime());
            EventUtil.addModifier(mob, Attributes.FLYING_SPEED, "swarm_speed", 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (mob instanceof Bat bat) bat.setResting(false);
            level.addFreshEntity(mob);
        }
    }

    private static void tickSwarms(ServerLevel level) {
        List<? extends Mob> swarm = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG));
        long time = level.getGameTime();
        for (Mob mob : swarm) {
            if (time - mob.getPersistentData().getLongOr("impendingdoom_born", time) > 3600) {
                mob.discard();
                continue;
            }
            UUID preyId = EventUtil.uuid(mob.getPersistentData(), PREY);
            Player prey = preyId == null ? null : level.getPlayerByUUID(preyId);
            if (prey == null || prey.isCreative() || prey.isSpectator()) continue;
            // All members of one swarm share the same 20 second rhythm: circle for 12s, then attack for 8s.
            long phase = (time + Math.abs(mob.getPersistentData().getStringOr(SWARM, "").hashCode())) % 400;
            boolean circling = phase < 240;
            if (mob instanceof Bat bat) {
                steerBat(level, bat, prey, circling, time);
            } else if (mob instanceof Bee bee && time % 5 == 0) {
                if (circling) {
                    bee.setTarget(null);
                    Vec3 orbit = orbit(prey, time, bee.getId());
                    bee.getMoveControl().setWantedPosition(orbit.x, orbit.y, orbit.z, 1.2);
                } else if (bee.getTarget() != prey) {
                    bee.setTarget(prey);
                    bee.startPersistentAngerTimer();
                }
            }
        }
    }

    private static Vec3 orbit(Player prey, long time, int id) {
        double a = time * 0.08 + id;
        return prey.position().add(Math.cos(a) * 6, 2.5 + Math.sin(a * 0.7), Math.sin(a) * 6);
    }

    /** Bats have no attack AI of their own: they are flown directly and bite on contact. */
    private static void steerBat(ServerLevel level, Bat bat, Player prey, boolean circling, long time) {
        bat.setResting(false);
        Vec3 goal = circling ? orbit(prey, time, bat.getId()) : prey.position().add(0, prey.getBbHeight() * 0.6, 0);
        Vec3 dir = goal.subtract(bat.position());
        double dist = dir.length();
        if (dist > 0.01) {
            Vec3 v = dir.scale(Math.min(0.45, dist) / dist);
            bat.setDeltaMovement(bat.getDeltaMovement().scale(0.5).add(v.scale(0.6)));
            bat.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0 / Math.PI) - 90.0F);
        }
        if (!circling && dist < 1.4 && time - bat.getPersistentData().getLongOr(BITE, 0L) > 20) {
            bat.getPersistentData().putLong(BITE, time);
            prey.hurtServer(level, bat.damageSources().mobAttack(bat), 2.0F);
            level.playSound(null, bat.blockPosition(), SoundEvents.BAT_HURT, SoundSource.HOSTILE, 0.6F, 0.6F);
        }
    }

    @Override
    public void onEnd(ServerLevel level, CompoundTag data) {
        for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG))) mob.discard();
    }
}
