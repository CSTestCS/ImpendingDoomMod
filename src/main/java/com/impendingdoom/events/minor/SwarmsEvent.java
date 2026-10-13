package com.impendingdoom.events.minor;

import com.impendingdoom.core.DoomEvent;
import com.impendingdoom.core.EventScheduler;
import com.impendingdoom.events.major.FallEvent;
import com.impendingdoom.util.EventUtil;
import com.impendingdoom.util.MobSight;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Swarms: large groups of small, fragile, hostile bees (bats during Fall) roam together. When a swarm spots a player
 * it circles them, then dives in all at once, regroups, and repeats until it loses sight of them.
 */
public class SwarmsEvent extends DoomEvent {
    public static final String ID = "swarms";
    private static final String TAG = "impendingdoom.swarm";
    private static final String SWARM = "impendingdoom_swarm";
    private static final String PREY = "impendingdoom_swarm_prey";
    private static final String BITE = "impendingdoom_swarm_bite";
    private static final String SEEN = "impendingdoom_swarm_seen";
    private static final String HOME = "impendingdoom_swarm_home";

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

    /** A swarm appears somewhere around a player and roams until it spots someone. */
    private static void spawnSwarm(ServerLevel level, ServerPlayer player, boolean bats) {
        BlockPos origin = EventUtil.surfacePos(level, player.blockPosition(), 24, 40, level.getRandom());
        if (origin == null) return;
        String id = UUID.randomUUID().toString();
        int size = (bats ? 14 : 10) + level.getRandom().nextInt(8);
        for (int i = 0; i < size; i++) {
            Mob mob = bats ? EntityTypes.BAT.create(level, EntitySpawnReason.EVENT) : EntityTypes.BEE.create(level, EntitySpawnReason.EVENT);
            if (mob == null) continue;
            mob.setPos(origin.getX() + level.getRandom().nextGaussian(), origin.getY() + 4 + level.getRandom().nextInt(3),
                    origin.getZ() + level.getRandom().nextGaussian());
            mob.addTag(TAG);
            mob.addTag(MobSight.EVENT_MOB);
            mob.getPersistentData().putString(SWARM, id);
            mob.getPersistentData().putLong("impendingdoom_born", level.getGameTime());
            mob.getPersistentData().putLong(HOME, origin.above(5).asLong());
            EventUtil.addModifier(mob, Attributes.FLYING_SPEED, "swarm_speed", 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            // One heart of health each; bees are half size.
            EventUtil.addModifier(mob, Attributes.MAX_HEALTH, "swarm_health", 2.0 - mob.getAttributeBaseValue(Attributes.MAX_HEALTH),
                    AttributeModifier.Operation.ADD_VALUE);
            mob.setHealth(2.0F);
            if (mob instanceof Bee) EventUtil.addModifier(mob, Attributes.SCALE, "swarm_scale", -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (mob instanceof Bat bat) bat.setResting(false);
            level.addFreshEntity(mob);
        }
    }

    private static void tickSwarms(ServerLevel level) {
        List<? extends Mob> all = level.getEntities(EntityTypeTest.forClass(Mob.class), m -> m.entityTags().contains(TAG));
        long time = level.getGameTime();
        Map<String, List<Mob>> swarms = new HashMap<>();
        for (Mob mob : all) {
            if (time - mob.getPersistentData().getLongOr("impendingdoom_born", time) > 6000) {
                mob.discard();
                continue;
            }
            swarms.computeIfAbsent(mob.getPersistentData().getStringOr(SWARM, ""), k -> new ArrayList<>()).add(mob);
        }
        for (Map.Entry<String, List<Mob>> entry : swarms.entrySet()) tickSwarm(level, entry.getKey(), entry.getValue(), time);
    }

    private static void tickSwarm(ServerLevel level, String id, List<Mob> members, long time) {
        Mob first = members.get(0);
        UUID preyId = EventUtil.uuid(first.getPersistentData(), PREY);
        Player prey = preyId == null ? null : level.getPlayerByUUID(preyId);
        if (time % 10 == 0) {
            if (prey != null) {
                // Lose interest once nobody in the swarm has seen the victim for 15 seconds.
                boolean seen = false;
                for (Mob m : members) if (MobSight.canSee(m, prey, 40)) seen = true;
                long lastSeen = first.getPersistentData().getLongOr(SEEN, time);
                if (seen) lastSeen = time;
                if (prey.isSpectator() || !prey.isAlive() || time - lastSeen > 300) prey = null;
                for (Mob m : members) {
                    if (prey == null) m.getPersistentData().remove(PREY);
                    m.getPersistentData().putLong(SEEN, lastSeen);
                }
                if (prey == null) for (Mob m : members) if (m instanceof Bee bee) calm(bee);
            } else {
                // Roaming: any member that sees a player sets the whole swarm on them.
                for (Mob m : members) {
                    Player seen = MobSight.nearestVisiblePlayer(level, m, 16, true);
                    if (seen == null) continue;
                    prey = seen;
                    for (Mob other : members) {
                        other.getPersistentData().putString(PREY, seen.getUUID().toString());
                        other.getPersistentData().putLong(SEEN, time);
                    }
                    break;
                }
            }
        }
        if (prey == null) {
            roam(level, members, time);
            return;
        }
        // All members of one swarm share the same 20 second rhythm: circle for 12s, then attack for 8s.
        long phase = (time + Math.abs(id.hashCode())) % 400;
        boolean circling = phase < 240;
        for (Mob mob : members) {
            if (mob instanceof Bat bat) {
                steerBat(level, bat, prey.position().add(0, prey.getBbHeight() * 0.6, 0), circling ? orbit(prey, time, bat.getId()) : null, prey, time);
            } else if (mob instanceof Bee bee && time % 5 == 0) {
                if (circling || MobSight.hiddenFrom(bee, prey)) {
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

    /** No victim: the swarm drifts around as a cloud, slowly moving from place to place. */
    private static void roam(ServerLevel level, List<Mob> members, long time) {
        Mob first = members.get(0);
        BlockPos home = BlockPos.of(first.getPersistentData().getLongOr(HOME, first.blockPosition().asLong()));
        if (time % 400 == 0 || first.getPersistentData().getLongOr(HOME, Long.MIN_VALUE) == Long.MIN_VALUE) {
            BlockPos next = EventUtil.surfacePos(level, home, 8, 24, level.getRandom());
            if (next != null) home = next.above(4 + level.getRandom().nextInt(4));
            for (Mob m : members) m.getPersistentData().putLong(HOME, home.asLong());
        }
        Vec3 center = Vec3.atCenterOf(home);
        for (Mob mob : members) {
            double a = time * 0.05 + mob.getId();
            Vec3 spot = center.add(Math.cos(a) * 3, Math.sin(a * 0.7), Math.sin(a) * 3);
            if (mob instanceof Bat bat) {
                steerBat(level, bat, spot, spot, null, time);
            } else if (mob instanceof Bee bee && time % 5 == 0) {
                bee.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 1.0);
            }
        }
    }

    private static void calm(Bee bee) {
        bee.setTarget(null);
        bee.stopBeingAngry();
    }

    private static Vec3 orbit(Player prey, long time, int id) {
        double a = time * 0.08 + id;
        return prey.position().add(Math.cos(a) * 6, 2.5 + Math.sin(a * 0.7), Math.sin(a) * 6);
    }

    /** Bats have no attack AI of their own: they are flown directly and bite on contact. */
    private static void steerBat(ServerLevel level, Bat bat, Vec3 attackPoint, Vec3 circlePoint, Player prey, long time) {
        bat.setResting(false);
        boolean circling = circlePoint != null;
        Vec3 goal = circling ? circlePoint : attackPoint;
        Vec3 dir = goal.subtract(bat.position());
        double dist = dir.length();
        if (dist > 0.01) {
            Vec3 v = dir.scale(Math.min(0.45, dist) / dist);
            bat.setDeltaMovement(bat.getDeltaMovement().scale(0.5).add(v.scale(0.6)));
            bat.setYRot((float) (Math.atan2(dir.z, dir.x) * 180.0 / Math.PI) - 90.0F);
        }
        if (prey != null && !circling && dist < 1.4 && !MobSight.hiddenFrom(bat, prey) && time - bat.getPersistentData().getLongOr(BITE, 0L) > 20) {
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
