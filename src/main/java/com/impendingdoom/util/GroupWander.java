package com.impendingdoom.util;

import com.impendingdoom.ImpendingDoom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Groups of event mobs (hordes, invasion parties, spider packs) roam like pillager patrols: a leader walks from one
 * random destination to the next and the rest follow it. They don't know where players are; a member that sees a
 * player attacks, and members close by join in.
 */
@EventBusSubscriber(modid = ImpendingDoom.MODID)
public final class GroupWander {
    private static final String GROUP = "impendingdoom_group";
    private static final Map<String, State> GROUPS = new HashMap<>();

    private static final class State {
        UUID leader;
        BlockPos destination;
        long chosenAt;
    }

    private GroupWander() {}

    /** Puts the mob in a roaming group. */
    public static void join(Mob mob, String group) {
        mob.getPersistentData().putString(GROUP, group);
        mob.addTag(MobSight.EVENT_MOB);
        setup(mob);
    }

    public static String group(Entity entity) {
        return entity.getPersistentData().getStringOr(GROUP, "");
    }

    private static boolean usesBrain(Mob mob) {
        return mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED);
    }

    private static void setup(Mob mob) {
        // Goal based mobs get a goal that keeps their own random strolling from pulling them away from the group.
        if (!usesBrain(mob)) mob.goalSelector.addGoal(4, new RoamGoal(mob));
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.loadedFromDisk() && event.getEntity() instanceof Mob mob && !group(mob).isEmpty()) {
            setup(mob);
        }
    }

    /** Brain based mobs (piglins, hoglins...) are steered from here. */
    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.tickCount % 20 != 0 || mob.level().isClientSide()) return;
        if (group(mob).isEmpty() || !usesBrain(mob)) return;
        steer(mob);
    }

    /** One member spotted a player: members close by join the attack. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!(event.getNewAboutToBeSetTarget() instanceof Player player)) return;
        String group = group(mob);
        if (group.isEmpty()) return;
        EventUtil.defer(() -> {
            if (mob.getTarget() != player) return;
            for (Mob other : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16),
                    m -> m != mob && m.getTarget() == null && group.equals(group(m)))) {
                if (!MobSight.hiddenFrom(other, player)) other.setTarget(player);
            }
        });
    }

    /** Moves an idle member: the leader roams, the others keep close to the leader. */
    static boolean steer(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || mob.getTarget() != null) return false;
        // A mount is steered by its rider.
        if (mob.getControllingPassenger() instanceof Mob) return false;
        String id = group(mob);
        if (id.isEmpty()) return false;
        State state = GROUPS.computeIfAbsent(id, k -> new State());
        Entity leaderEntity = state.leader == null ? null : level.getEntity(state.leader);
        Mob leader = leaderEntity instanceof Mob m && m.isAlive() && id.equals(group(m)) ? m : null;
        if (leader == null) {
            state.leader = mob.getUUID();
            state.destination = null;
            leader = mob;
        }
        long now = level.getGameTime();
        if (leader == mob) {
            if (state.destination == null || mob.blockPosition().closerThan(state.destination, 4) || now - state.chosenAt > 1200) {
                state.destination = EventUtil.surfacePos(level, mob.blockPosition(), 16, 40, mob.getRandom());
                state.chosenAt = now;
            }
            if (state.destination == null) return false;
            moveTo(mob, Vec3.atBottomCenterOf(state.destination), 0.8);
            return true;
        }
        double dist = mob.distanceTo(leader);
        if (dist < 5) return false;
        // Each follower keeps its own spot around the leader.
        double angle = (mob.getUUID().getLeastSignificantBits() & 0xFFFF) / 65536.0 * Math.PI * 2;
        Vec3 spot = leader.position().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
        moveTo(mob, spot, dist > 16 ? 1.15 : 0.9);
        return true;
    }

    private static void moveTo(Mob mob, Vec3 pos, double speed) {
        Mob flyer = mob instanceof Ghast ? mob : mob.getVehicle() instanceof Ghast g ? g : null;
        if (flyer != null) {
            flyer.getMoveControl().setWantedPosition(pos.x, pos.y + 12, pos.z, 1.0);
        } else if (usesBrain(mob)) {
            mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(pos), (float) speed, 2));
        } else {
            mob.getNavigation().moveTo(pos.x, pos.y, pos.z, speed);
        }
    }

    /** Groups are forgotten when every member is gone. */
    public static void forget(String group) {
        GROUPS.remove(group);
    }

    private static final class RoamGoal extends Goal {
        private final Mob mob;
        private int cooldown;

        RoamGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() == null && !group(mob).isEmpty();
        }

        @Override
        public void start() {
            cooldown = 0;
        }

        @Override
        public void tick() {
            if (--cooldown > 0) return;
            cooldown = 20;
            steer(mob);
        }
    }

    /** Whether the entity is an attacker in a roaming group. */
    public static boolean inGroup(LivingEntity entity) {
        return !group(entity).isEmpty();
    }
}
